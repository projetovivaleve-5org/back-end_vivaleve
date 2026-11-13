package vivaleve.proj;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.service.GoogleTokenVerifier;
import vivaleve.proj.service.GoogleTokenVerifier.GoogleUser;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa as validações do ID Token do Google sem acessar a internet:
 * uma chave RSA local faz o papel da chave do Google.
 */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "meu-client-id.apps.googleusercontent.com";

    private final KeyPair keys = generateRsa();
    private final NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
            new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey((RSAPrivateKey) keys.getPrivate()).build())));
    private final GoogleTokenVerifier verifier = new GoogleTokenVerifier(CLIENT_ID,
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());

    /** Monta um ID Token "do Google" válido; cada teste pode alterar uma claim. */
    private String idToken(Consumer<JwtClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject("google-sub-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", "ana@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Ana")
                .claim("picture", "https://foto");
        customize.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    private void assert401(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenValidoDevolveDadosDaConta() {
        GoogleUser user = verifier.verify(idToken(c -> { }));
        assertThat(user.sub()).isEqualTo("google-sub-123");
        assertThat(user.email()).isEqualTo("ana@gmail.com");
        assertThat(user.name()).isEqualTo("Ana");
        assertThat(user.pictureUrl()).isEqualTo("https://foto");
    }

    @Test
    void aceitaIssuerSemHttps() {
        assertThat(verifier.verify(idToken(c -> c.issuer("accounts.google.com"))).sub()).isEqualTo("google-sub-123");
    }

    @Test
    void tokenEmitidoParaOutroAppRetorna401() {
        assert401(idToken(c -> c.audience(List.of("client-id-de-outro-site"))));
    }

    @Test
    void issuerQueNaoEhGoogleRetorna401() {
        assert401(idToken(c -> c.issuer("https://site-falso.com")));
    }

    @Test
    void tokenExpiradoRetorna401() {
        assert401(idToken(c -> c.issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600))));
    }

    @Test
    void emailNaoVerificadoRetorna401() {
        assert401(idToken(c -> c.claim("email_verified", false)));
    }

    @Test
    void tokenAssinadoComOutraChaveRetorna401() {
        KeyPair outra = generateRsa();
        NimbusJwtEncoder falsario = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
                new RSAKey.Builder((RSAPublicKey) outra.getPublic()).privateKey((RSAPrivateKey) outra.getPrivate()).build())));
        Instant now = Instant.now();
        String falso = falsario.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().issuer("https://accounts.google.com").audience(List.of(CLIENT_ID))
                        .subject("x").issuedAt(now).expiresAt(now.plusSeconds(3600))
                        .claim("email", "a@a.com").claim("email_verified", true).build())).getTokenValue();
        assert401(falso);
    }

    @Test
    void lixoNoLugarDoTokenRetorna401() {
        assert401("isso-nao-e-um-jwt");
    }

    private static KeyPair generateRsa() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
