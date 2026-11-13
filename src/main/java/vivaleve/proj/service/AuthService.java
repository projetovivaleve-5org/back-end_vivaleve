package vivaleve.proj.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.AuthDtos.AuthResponse;
import vivaleve.proj.dto.AuthDtos.LoginRequest;
import vivaleve.proj.dto.AuthDtos.RegisterRequest;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.UserRepository;
import vivaleve.proj.service.GoogleTokenVerifier.GoogleUser;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

/**
 * Regras de negócio de autenticação.
 *
 * <p>Dois jeitos de entrar, que devolvem o mesmo JWT da API:</p>
 * <ul>
 *   <li><b>Email + senha</b>: {@link #register(RegisterRequest)} e {@link #login(LoginRequest)}.
 *       A senha é guardada só como hash BCrypt.</li>
 *   <li><b>Login/cadastro com Google</b>: se o usuário ainda não existir, cria a conta na hora —
 *       o primeiro login É o cadastro.</li>
 *   <li><b>Vínculo de contas</b>: quem tem conta com email + senha entra com o Google do mesmo
 *       email e mantém todos os seus registros. A senha é apagada nesse momento (ver
 *       {@link #linkOrCreate(String, String)}).</li>
 *   <li><b>Emissão de JWT</b> (sem mudança): o token da API carrega o id interno do usuário no
 *       {@code sub} e o email em uma claim; expira após {@code jwt.expiration-seconds}.
 *       O token do Google só é usado uma vez, aqui no login — nas demais rotas vale o JWT da API.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Identifica quem emitiu o token (claim "iss"). */
    private static final String ISSUER = "vivaleve-api";
    private static final String TOKEN_TYPE = "Bearer";

    /** O BCrypt só aceita senhas de até 72 bytes (acentos ocupam 2 bytes em UTF-8). */
    private static final int BCRYPT_MAX_BYTES = 72;

    /** Mesma mensagem para email inexistente e senha errada: não revela quais emails têm conta. */
    private static final String INVALID_LOGIN = "Email ou senha inválidos";
    private static final String EMAIL_IN_USE = "Este email já está cadastrado";

    private final UserRepository userRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtEncoder jwtEncoder;
    private final PasswordEncoder passwordEncoder;

    /** Tempo de vida do token em segundos (padrão: 24h). */
    @Value("${jwt.expiration-seconds:86400}")
    private long expirationSeconds;

    /** Hash usado quando o email não existe, para o login demorar o mesmo tempo nos dois casos. */
    private volatile String dummyHash;

    /**
     * Cadastro com email + senha ({@code POST /v1/auth/register}). Já devolve o JWT (entra direto).
     *
     * @throws ResponseStatusException 409 se o email já tiver conta (com senha ou com Google);
     *                                 400 se a senha passar do limite do BCrypt
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (exceedsBcryptLimit(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha é longa demais");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_IN_USE);
        }

        UserEntity user = UserEntity.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .name(blankToNull(request.name()))
                .build();
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Dois cadastros ao mesmo tempo com o mesmo email: o índice único do banco barra o segundo.
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_IN_USE);
        }
        return buildAuthResponse(user);
    }

    /**
     * Login com email + senha ({@code POST /v1/auth/login}).
     *
     * @throws ResponseStatusException 401 se o email não existir, a senha estiver errada ou a
     *                                 conta só entrar pelo Google (sempre a mesma mensagem)
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user == null ? null : user.getPassword();

        if (!passwordMatches(request.password(), hash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        return buildAuthResponse(user);
    }

    private boolean passwordMatches(String rawPassword, String hash) {
        if (exceedsBcryptLimit(rawPassword)) {
            return false; // o BCrypt lançaria erro (500); nenhuma senha cadastrada passa desse limite
        }
        if (hash == null) {
            // Confere contra um hash qualquer só para gastar o mesmo tempo de uma senha errada.
            passwordEncoder.matches(rawPassword, dummyHash());
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("senha-que-nao-pertence-a-ninguem");
        }
        return dummyHash;
    }

    private static boolean exceedsBcryptLimit(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Login com Google ({@code POST /v1/auth/google}, usado pelo botão do Google no front).
     *
     * <p>Valida o ID Token com o {@link GoogleTokenVerifier} (assinatura, aud, iss, exp,
     * email verificado) e segue para o login/cadastro comum em {@link #loginWithGoogle(GoogleUser)}.</p>
     *
     * @param idToken o {@code credential} entregue pelo botão "Entrar com Google"
     * @throws ResponseStatusException 401 se o token do Google for inválido;
     *                                 409 se o email já pertencer a OUTRA conta Google
     */
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        return loginWithGoogle(googleTokenVerifier.verify(idToken));
    }

    /**
     * NOVO — Login e cadastro a partir de uma conta Google.
     *
     * <p>Os dados da conta já chegam validados pelo {@link GoogleTokenVerifier}, então aqui só
     * existe regra de negócio:</p>
     * <ol>
     *   <li>Procura o usuário pelo {@code sub} do Google → login normal.</li>
     *   <li>Se não achar, procura pelo email → conta antiga (email + senha): vincula o {@code sub} a ela.</li>
     *   <li>Se também não achar → cadastro: cria um usuário novo.</li>
     *   <li>Atualiza nome/foto (podem ter mudado no Google) e devolve o JWT da API.</li>
     * </ol>
     *
     * @param google dados de uma conta Google JÁ VALIDADA (nunca montar a partir de dados do cliente)
     * @throws ResponseStatusException 409 se o email já pertencer a OUTRA conta Google
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleUser google) {
        String email = normalizeEmail(google.email());

        UserEntity user = userRepository.findByGoogleSub(google.sub())
                .orElseGet(() -> linkOrCreate(google.sub(), email));

        // Mantém o perfil sincronizado com o Google a cada login.
        user.setEmail(email);
        user.setName(google.name());
        user.setPictureUrl(google.pictureUrl());
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    /**
     * Feature: vínculo de conta antiga ou criação de conta nova.
     *
     * <p>Só chega aqui quando nenhum usuário tem esse {@code sub}. Se existir um usuário com o
     * mesmo email e SEM {@code sub}, é uma conta de email + senha — o Google garante que
     * o email é verificado, então é seguro vinculá-la. Se o usuário com esse email já tiver
     * OUTRO {@code sub}, é outra conta Google (o email trocou de dono) e o acesso é negado.</p>
     *
     * <p><b>Segurança:</b> ao vincular, a senha é apagada. O cadastro com senha não confirma o
     * email, então qualquer pessoa pode ter cadastrado o email de outra. Sem isso, quem criou a
     * senha continuaria entrando na conta depois que o verdadeiro dono entrasse pelo Google.</p>
     */
    private UserEntity linkOrCreate(String googleSub, String email) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    if (existing.getGoogleSub() != null) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Este email já está vinculado a outra conta Google");
                    }
                    existing.setGoogleSub(googleSub);
                    existing.setPassword(null);
                    return existing;
                })
                .orElseGet(() -> UserEntity.builder()
                        .googleSub(googleSub)
                        .email(email)
                        .build());
    }

    /** Gera o JWT da API e monta o JSON de resposta. */
    private AuthResponse buildAuthResponse(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId())) // quem é o dono do token (id interno, não o sub do Google)
                .claim("email", user.getEmail())
                .build();

        // O header precisa declarar HS256, pois o padrão do encoder seria RS256 (chave assimétrica).
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new AuthResponse(token, TOKEN_TYPE, expiresAt, UserService.toDto(user));
    }

    /** Emails são comparados sem diferenciar maiúsculas/minúsculas nem espaços nas pontas. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
