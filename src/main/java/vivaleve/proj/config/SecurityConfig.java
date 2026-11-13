package vivaleve.proj.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vivaleve.proj.handler.JwtAuthenticationEntryPoint;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Configuração de segurança da API.
 *
 * <p>Login: com email + senha ({@code POST /v1/auth/register} e {@code POST /v1/auth/login})
 * ou com o botão "Entrar com Google" ({@code POST /v1/auth/google}). Os três devolvem o JWT
 * da API. A partir daí, toda requisição se autentica com
 * {@code Authorization: Bearer <JWT da API>} — sem sessão e sem cookie.</p>
 *
 * <p>Os tokens da API são assinados com HMAC-SHA256 usando a chave secreta {@code jwt.secret}.
 * A mesma chave assina e valida, então ela nunca deve ir para o repositório.</p>
 */
@Configuration
@EnableWebSecurity // liga o Spring Security explicitamente (também necessário nos testes de slice)
public class SecurityConfig {

    /** HS256 exige uma chave de no mínimo 256 bits (32 bytes). */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    /** Origens (domínios do front) autorizadas a chamar a API pelo navegador. */
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${jwt.secret}") String secret,
            @Value("${cors.allowed-origins:http://localhost:5500,http://127.0.0.1:5500}") String[] allowedOrigins) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            // Falha logo na inicialização em vez de gerar tokens inseguros.
            throw new IllegalStateException(
                    "jwt.secret (JWT_SECRET) deve ter pelo menos " + MIN_SECRET_BYTES + " bytes");
        }
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
        this.allowedOrigins = Arrays.stream(allowedOrigins).map(String::trim).filter(o -> !o.isEmpty()).toList();
    }

    /**
     * Regras de acesso da API REST.
     *
     * <ul>
     *   <li>CORS ligado: usa o {@link #corsConfigurationSource()} abaixo.</li>
     *   <li>CSRF desligado: a API não usa cookies de sessão, só token no header.</li>
     *   <li>Sessão STATELESS: cada requisição se autentica sozinha pelo JWT.</li>
     *   <li>{@code /v1/auth/**} é sempre público (cadastro, login com senha e login com Google).</li>
     *   <li>{@code /error} é público: quando um controller lança erro (ex.: 409 no login),
     *       o Spring encaminha internamente para {@code /error} para montar o JSON de erro.
     *       Se essa rota exigisse token, um 409/400 numa rota pública viraria 401.</li>
     *   <li><b>Qualquer outra rota exige um JWT válido da API</b> ({@code authenticated()}).
     *       Sem token, com token adulterado ou expirado → 401 (nunca redireciona para o Google).</li>
     *   <li>{@code oauth2ResourceServer().jwt()}: lê o header {@code Authorization: Bearer ...}
     *       e valida o token com o {@link JwtDecoder} abaixo.</li>
     *   <li>Sem {@code formLogin}, {@code httpBasic} ou {@code oauth2Login}: não existe login por
     *       sessão nem por redirect. O login com senha é feito no AuthService, que devolve o JWT.</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/auth/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Feature: CORS.
     *
     * <p>O front (Vercel) e a API (Render) ficam em domínios diferentes; sem isso o navegador
     * bloqueia as chamadas, inclusive o {@code POST /v1/auth/google}. As origens vêm da
     * propriedade {@code cors.allowed-origins} (variável {@code CORS_ALLOWED_ORIGINS}, separada
     * por vírgula). {@code allowCredentials} fica desligado porque o token vai no header, não em cookie.</p>
     *
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Decide de onde ler o Bearer token de cada requisição.
     *
     * <p>Nas rotas {@code /v1/auth/**} o header {@code Authorization} é ignorado: quem está
     * fazendo login ainda não tem token válido. Sem isso, um token expirado,
     * gerado com outro {@code JWT_SECRET} ou um placeholder vazio (ex.: {@code {{token}}}
     * no Postman) faria o login responder 401 antes mesmo de chegar no controller.</p>
     */
    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith(request.getContextPath() + "/v1/auth/")
                ? null
                : defaultResolver.resolve(request);
    }

    /** Gera e confere os hashes BCrypt das senhas (cadastro e login com email + senha). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Assina os JWTs emitidos pelo AuthService. */
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    /**
     * Valida assinatura e expiração dos JWTs da API recebidos nas requisições.
     * É o ÚNICO bean {@code JwtDecoder} da aplicação: o ID Token do Google é validado pelo
     * {@code GoogleTokenVerifier}, que tem o seu próprio decoder (não é bean).
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
