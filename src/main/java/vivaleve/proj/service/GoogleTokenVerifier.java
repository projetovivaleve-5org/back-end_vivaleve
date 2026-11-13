package vivaleve.proj.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Set;

/**
 * NOVO — Valida o ID Token emitido pelo Google (o {@code credential} que o front recebe
 * do botão "Entrar com Google") e extrai dele os dados do usuário.
 *
 * <p>O ID Token é um JWT assinado pelo Google com chaves RSA privadas. Para conferir a
 * assinatura usamos as chaves públicas que o Google publica em {@value #GOOGLE_JWKS_URI}
 * (o Nimbus baixa e guarda em cache, e rebaixa quando o Google troca as chaves).</p>
 *
 * <p>Validações feitas em todo token:</p>
 * <ul>
 *   <li><b>Assinatura</b> — prova que o token foi emitido pelo Google e não foi alterado.</li>
 *   <li><b>exp / iat</b> — token expirado é rejeitado ({@link JwtValidators#createDefault()}).</li>
 *   <li><b>iss</b> — precisa ser o Google ({@code accounts.google.com}, com ou sem https).</li>
 *   <li><b>aud</b> — precisa ser o NOSSO Client ID. É a validação mais importante: sem ela,
 *       um token Google emitido para QUALQUER outro site seria aceito aqui.</li>
 *   <li><b>email_verified</b> — só aceitamos contas com email confirmado pelo Google.</li>
 * </ul>
 *
 * <p>Importante: este decoder NÃO é registrado como bean {@code JwtDecoder}. O único
 * {@code JwtDecoder} do contexto é o do {@code SecurityConfig}, que valida o JWT da própria API
 * nas rotas protegidas. Se este também fosse um bean, o Spring não saberia qual usar.</p>
 */
@Component
public class GoogleTokenVerifier {

    /** Endereço das chaves públicas do Google (formato JWK Set). */
    static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /** O Google emite o "iss" nos dois formatos, então aceitamos ambos. */
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private static final String INVALID_TOKEN = "Token do Google inválido";

    private final NimbusJwtDecoder decoder;

    /** Construtor usado pelo Spring: busca as chaves públicas no endereço oficial do Google. */
    @Autowired
    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        // RS256 é o padrão do withJwkSetUri e é o algoritmo que o Google usa.
        this(clientId, NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build());
    }

    /**
     * Construtor que recebe o decoder pronto — usado nos testes, com uma chave RSA local
     * no lugar das chaves do Google. As validações (iss, aud, exp) são aplicadas do mesmo jeito.
     */
    public GoogleTokenVerifier(String clientId, NimbusJwtDecoder decoder) {
        if (clientId == null || clientId.isBlank()) {
            // Falha logo na inicialização: sem Client ID não dá para validar o "aud" com segurança.
            throw new IllegalStateException("google.client-id (GOOGLE_CLIENT_ID) não foi configurado");
        }
        this.decoder = decoder;
        this.decoder.setJwtValidator(buildValidator(clientId));
    }

    /**
     * Valida o ID Token e devolve os dados da conta Google.
     *
     * @throws ResponseStatusException 401 se o token for inválido, expirado, de outro app
     *                                 ou de uma conta com email não verificado;
     *                                 503 se não for possível falar com o Google para buscar as chaves
     */
    public GoogleUser verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException e) {
            // Token malformado, assinatura inválida, expirado, aud/iss errados...
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN);
        } catch (JwtException e) {
            // Erro que não é culpa do token (ex.: não conseguiu baixar as chaves do Google).
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível validar o login com o Google agora, tente novamente");
        }

        // Feature: só contas com email verificado pelo Google podem entrar/se cadastrar.
        if (!isTrue(jwt.getClaim("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "O email da conta Google não está verificado");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN);
        }

        return new GoogleUser(
                jwt.getSubject(),
                email,
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"));
    }

    /** Junta as validações padrão (exp/iat) com as específicas do Google (iss e aud). */
    private static OAuth2TokenValidator<Jwt> buildValidator(String clientId) {
        // Feature: o token tem que ter sido emitido pelo Google.
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<Object>("iss",
                iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString()));

        // Feature: o token tem que ter sido emitido PARA o nosso app (aud == nosso Client ID).
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Object>("aud",
                aud -> aud instanceof Collection<?> list ? list.contains(clientId) : clientId.equals(aud));

        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience);
    }

    /** O Google manda {@code email_verified} como boolean, mas aceitamos "true" em texto por segurança. */
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    /**
     * Dados confiáveis extraídos de um ID Token já validado.
     *
     * @param sub        id permanente da conta Google (é o que identifica o usuário)
     * @param email      email verificado da conta
     * @param name       nome exibido (pode ser null)
     * @param pictureUrl foto de perfil (pode ser null)
     */
    public record GoogleUser(String sub, String email, String name, String pictureUrl) {
    }
}
