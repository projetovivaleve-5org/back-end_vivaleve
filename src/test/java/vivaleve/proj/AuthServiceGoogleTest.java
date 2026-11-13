package vivaleve.proj;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.AuthDtos.AuthResponse;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.UserRepository;
import vivaleve.proj.service.AuthService;
import vivaleve.proj.service.GoogleTokenVerifier;
import vivaleve.proj.service.GoogleTokenVerifier.GoogleUser;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testa as regras do login com Google no AuthService (sem banco e sem chamar o Google):
 * o GoogleTokenVerifier e o repositório são mocks; o JwtEncoder é real para conferir o token gerado.
 */
class AuthServiceGoogleTest {

    private static final SecretKey KEY = new SecretKeySpec(
            "segredo-de-teste-com-pelo-menos-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final UserRepository repo = mock(UserRepository.class);
    private final GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
    private final AuthService service = new AuthService(repo, verifier,
            new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), new BCryptPasswordEncoder());

    private final GoogleUser google = new GoogleUser("google-sub-123", "Ana@Gmail.com ", "Ana", "https://foto");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expirationSeconds", 3600L);
        when(verifier.verify("id-token")).thenReturn(google);
        // simula o banco gerando o id no save
        when(repo.save(any())).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            if (u.getId() == null) ReflectionTestUtils.setField(u, "id", 10L);
            return u;
        });
    }

    @Test
    void primeiroLoginCriaUsuario() {
        when(repo.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());

        AuthResponse resp = service.loginWithGoogle("id-token");

        verify(repo).save(argThat(u -> "google-sub-123".equals(u.getGoogleSub())
                && "ana@gmail.com".equals(u.getEmail())
                && "Ana".equals(u.getName())));
        assertThat(resp.user().getId()).isEqualTo(10L);
        assertThat(resp.user().getPictureUrl()).isEqualTo("https://foto");

        // o token devolvido é o JWT da API, com o id interno no sub
        Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(resp.token());
        assertThat(jwt.getSubject()).isEqualTo("10");
    }

    @Test
    void loginDeUsuarioExistenteAtualizaPerfil() {
        UserEntity existente = UserEntity.builder().googleSub("google-sub-123").email("ana@gmail.com").name("Nome Antigo").build();
        ReflectionTestUtils.setField(existente, "id", 7L);
        when(repo.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(existente));

        AuthResponse resp = service.loginWithGoogle("id-token");

        assertThat(resp.user().getId()).isEqualTo(7L);
        assertThat(existente.getName()).isEqualTo("Ana");
        verify(repo, never()).findByEmail(any());
    }

    @Test
    void contaDeEmailESenhaEhVinculadaPeloEmailEPerdeASenha() {
        UserEntity antiga = UserEntity.builder().email("ana@gmail.com").password("$2a$10$hash-qualquer").build(); // sem googleSub
        ReflectionTestUtils.setField(antiga, "id", 3L);
        when(repo.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.of(antiga));

        AuthResponse resp = service.loginWithGoogle("id-token");

        assertThat(resp.user().getId()).isEqualTo(3L); // mesmo usuário → mantém os registros
        assertThat(antiga.getGoogleSub()).isEqualTo("google-sub-123");
        // quem cadastrou a senha pode não ser o dono do email: ela deixa de valer
        assertThat(antiga.getPassword()).isNull();
    }

    @Test
    void emailJaVinculadoAOutraContaGoogleRetorna409() {
        UserEntity outra = UserEntity.builder().email("ana@gmail.com").googleSub("OUTRO-sub").build();
        when(repo.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(repo.findByEmail("ana@gmail.com")).thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> service.loginWithGoogle("id-token"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repo, never()).save(any());
    }

    @Test
    void tokenGoogleInvalidoNaoTocaNoBanco() {
        when(verifier.verify("falso")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.loginWithGoogle("falso")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repo);
    }
}
