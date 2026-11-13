package vivaleve.proj;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.AuthDtos.AuthResponse;
import vivaleve.proj.dto.AuthDtos.LoginRequest;
import vivaleve.proj.dto.AuthDtos.RegisterRequest;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.UserRepository;
import vivaleve.proj.service.AuthService;
import vivaleve.proj.service.GoogleTokenVerifier;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testa o cadastro e o login com email + senha no AuthService (sem banco):
 * o repositório é mock; o BCrypt e o JwtEncoder são reais.
 */
class AuthServicePasswordTest {

    private static final SecretKey KEY = new SecretKeySpec(
            "segredo-de-teste-com-pelo-menos-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final UserRepository repo = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthService service = new AuthService(repo, mock(GoogleTokenVerifier.class),
            new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), encoder);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expirationSeconds", 3600L);
        // simula o banco gerando o id no save
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            if (u.getId() == null) ReflectionTestUtils.setField(u, "id", 10L);
            return u;
        });
    }

    private UserEntity contaComSenha(String senha) {
        UserEntity u = UserEntity.builder().email("ana@gmail.com").password(encoder.encode(senha)).build();
        ReflectionTestUtils.setField(u, "id", 7L);
        return u;
    }

    private static HttpStatusCode statusDe(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode();
    }

    @Test
    void cadastroSalvaHashENaoASenhaEDevolveToken() {
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());

        AuthResponse resp = service.register(new RegisterRequest(" Ana@Gmail.com ", "senha12345", " Ana "));

        verify(repo).saveAndFlush(argThat(u -> "ana@gmail.com".equals(u.getEmail())
                && "Ana".equals(u.getName())
                && u.getGoogleSub() == null
                && !"senha12345".equals(u.getPassword())
                && encoder.matches("senha12345", u.getPassword())));
        Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(resp.token());
        assertThat(jwt.getSubject()).isEqualTo("10");
    }

    @Test
    void cadastroComEmailJaUsadoRetorna409() {
        // vale também para conta que só entra pelo Google: não dá para "adicionar" senha nela pelo cadastro
        when(repo.findByEmail("ana@gmail.com"))
                .thenReturn(Optional.of(UserEntity.builder().email("ana@gmail.com").googleSub("sub").build()));

        assertThatThrownBy(() -> service.register(new RegisterRequest("ana@gmail.com", "senha12345", null)))
                .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.CONFLICT));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void cadastroSimultaneoComMesmoEmailRetorna409() {
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("email duplicado")).when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.register(new RegisterRequest("ana@gmail.com", "senha12345", null)))
                .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cadastroComSenhaAcimaDoLimiteDoBcryptRetorna400() {
        String senha = "ç".repeat(40); // 40 caracteres, mas 80 bytes em UTF-8 (> 72)

        assertThatThrownBy(() -> service.register(new RegisterRequest("ana@gmail.com", senha, null)))
                .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(repo);
    }

    @Test
    void loginComSenhaCertaDevolveToken() {
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.of(contaComSenha("senha12345")));

        AuthResponse resp = service.login(new LoginRequest("ANA@gmail.com", "senha12345"));

        Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(resp.token());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(resp.user().getEmail()).isEqualTo("ana@gmail.com");
    }

    @Test
    void loginComSenhaErradaRetorna401() {
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.of(contaComSenha("senha12345")));

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@gmail.com", "outra-senha")))
                .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("Email ou senha inválidos");
    }

    @Test
    void loginComEmailInexistenteRetornaOMesmo401() {
        when(repo.findByEmail("ninguem@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ninguem@gmail.com", "senha12345")))
                .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("Email ou senha inválidos");
    }

    @Test
    void contaSoDoGoogleNaoEntraComSenha() {
        UserEntity soGoogle = UserEntity.builder().email("ana@gmail.com").googleSub("sub").build(); // sem senha
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.of(soGoogle));

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@gmail.com", "qualquer-coisa")))
                .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginComSenhaGiganteRetorna401EmVezDeErro500() {
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.of(contaComSenha("senha12345")));

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@gmail.com", "x".repeat(500))))
                .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
