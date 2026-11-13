package vivaleve.proj;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.config.SecurityConfig;
import vivaleve.proj.controller.AuthController;
import vivaleve.proj.controller.EntryController;
import vivaleve.proj.controller.StatsController;
import vivaleve.proj.controller.UserController;
import vivaleve.proj.dto.AuthDtos.LoginRequest;
import vivaleve.proj.dto.AuthDtos.RegisterRequest;
import vivaleve.proj.dto.UserResponseDto;
import vivaleve.proj.service.AuthService;
import vivaleve.proj.service.EntryService;
import vivaleve.proj.service.StatsService;
import vivaleve.proj.service.UserService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa as regras do SecurityConfig de ponta a ponta na camada web (sem banco):
 * tokens são gerados com o mesmo JwtEncoder da aplicação e os services são mocks.
 */
@WebMvcTest(controllers = {AuthController.class, EntryController.class, StatsController.class, UserController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes!!"
})
class SecurityRulesTest {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder jwtEncoder;

    @MockitoBean AuthService authService;
    @MockitoBean EntryService entryService;
    @MockitoBean StatsService statsService;
    @MockitoBean UserService userService;

    private String token(long userId, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String validToken(long userId) {
        return token(userId, Instant.now().plusSeconds(3600));
    }

    @Test
    void rotasProtegidasSemTokenRetornam401() throws Exception {
        mvc.perform(get("/v1/entries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/entries/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/v1/entries/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAdulteradoRetorna401() throws Exception {
        String t = validToken(1);
        // troca um caractere no meio do payload → assinatura deixa de bater
        int i = t.indexOf('.') + 5;
        String adulterado = t.substring(0, i) + (t.charAt(i) == 'A' ? 'B' : 'A') + t.substring(i + 1);
        mvc.perform(get("/v1/entries").header("Authorization", "Bearer " + adulterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        mvc.perform(get("/v1/entries").header("Authorization", "Bearer " + token(1, Instant.now().minusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void semTokenDevolveJsonDe401() throws Exception {
        mvc.perform(get("/v1/entries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("401"))
                .andExpect(jsonPath("$.message").value("Token de acesso não informado"));
    }

    @Test
    void tokenInvalidoDevolveJsonDe401() throws Exception {
        mvc.perform(get("/v1/entries").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("401"))
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"));
    }

    @Test
    void tokenValidoUsaOIdDoTokenEIgnoraUserIdDaUrl() throws Exception {
        when(entryService.listarPorUsuario(7L)).thenReturn(List.of());

        // mesmo mandando ?userId=99, quem vale é o sub=7 do token
        mvc.perform(get("/v1/entries?userId=99").header("Authorization", "Bearer " + validToken(7)))
                .andExpect(status().isOk());
        verify(entryService).listarPorUsuario(7L);
    }

    @Test
    void entryDeOutroUsuarioRetorna404() throws Exception {
        when(entryService.buscarPorId(7L, 50L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));

        mvc.perform(get("/v1/entries/50").header("Authorization", "Bearer " + validToken(7)))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarEntryUsaDonoDoToken() throws Exception {
        mvc.perform(post("/v1/entries")
                        .header("Authorization", "Bearer " + validToken(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mood\":\"GOOD\",\"date\":\"2026-11-01\"}"))
                .andExpect(status().isCreated());
        verify(entryService).criar(eq(7L), any());
    }

    @Test
    void statsUsaDonoDoToken() throws Exception {
        mvc.perform(get("/v1/stats?days=30").header("Authorization", "Bearer " + validToken(7)))
                .andExpect(status().isOk());
        verify(statsService).gerarResumo(7L, 30);
    }

    @Test
    void meDevolveOUsuarioDoToken() throws Exception {
        when(userService.buscarPerfil(7L)).thenReturn(UserResponseDto.builder().id(7L).email("a@a.com").build());

        mvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + validToken(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("a@a.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void rotasAntigasDeUsuarioNaoExistemMais() throws Exception {
        String auth = "Bearer " + validToken(7);
        mvc.perform(get("/v1/users").header("Authorization", auth))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 404 && s != 405) throw new AssertionError("esperado 404/405, veio " + s);
                });
        mvc.perform(post("/v1/users").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 404 && s != 405) throw new AssertionError("esperado 404/405, veio " + s);
                });
    }

    @Test
    void loginGoogleContinuaPublicoMesmoComTokenInvalido() throws Exception {
        mvc.perform(post("/v1/auth/google")
                        .header("Authorization", "Bearer {{token}}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"id-token-do-google\"}"))
                .andExpect(status().isOk());
        verify(authService).loginWithGoogle("id-token-do-google");
    }

    @Test
    void loginGoogleSemCredentialRetorna400() throws Exception {
        mvc.perform(post("/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginGoogleComTokenGoogleInvalidoRetorna401() throws Exception {
        when(authService.loginWithGoogle("falso"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google inválido"));

        mvc.perform(post("/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"falso\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cadastroComSenhaEhPublicoERetorna201() throws Exception {
        mvc.perform(post("/v1/auth/register")
                        .header("Authorization", "Bearer {{token}}") // token inválido é ignorado em /v1/auth/**
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@a.com\",\"password\":\"senha12345\",\"name\":\"Ana\"}"))
                .andExpect(status().isCreated());
        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void cadastroComSenhaCurtaOuEmailInvalidoRetorna400() throws Exception {
        mvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@a.com\",\"password\":\"1234567\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"password\":\"senha12345\"}"))
                .andExpect(status().isBadRequest());
        verify(authService, never()).register(any());
    }

    @Test
    void cadastroComEmailRepetidoRetorna409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Este email já está cadastrado"));

        mvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@a.com\",\"password\":\"senha12345\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginComSenhaEhPublico() throws Exception {
        mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@a.com\",\"password\":\"senha12345\"}"))
                .andExpect(status().isOk());
        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou senha inválidos"));

        mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@a.com\",\"password\":\"errada123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPermiteOFrontConfigurado() throws Exception {
        mvc.perform(options("/v1/auth/google")
                        .header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }

    @Test
    void fluxoDeRedirectFoiRemovido() throws Exception {
        // O login por redirect (oauth2Login) saiu: essas URLs agora são rotas comuns da API,
        // protegidas como qualquer outra → 401 sem token, nunca um redirect para o Google.
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isUnauthorized());
        mvc.perform(get("/login/oauth2/code/google?code=qualquer&state=qualquer"))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).loginWithGoogle(any(String.class));
    }

    @Test
    void corsBloqueiaOrigemDesconhecida() throws Exception {
        mvc.perform(options("/v1/auth/google")
                        .header("Origin", "https://site-malicioso.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
