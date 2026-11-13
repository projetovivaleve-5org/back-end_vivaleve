package vivaleve.proj.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import vivaleve.proj.dto.DiaryDtos.DiaryRequest;
import vivaleve.proj.dto.DiaryDtos.DiaryResponse;
import vivaleve.proj.model.enums.MoodType;
import vivaleve.proj.service.DiaryService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato HTTP do diário (/v1/diary) com a segurança real: o dono vem sempre do JWT. */
@WebMvcTest(DiaryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes!!")
class DiaryControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoBean
    DiaryService diaryService;

    /** Token válido da API para o usuário 7. */
    private String auth() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("7").issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        return "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static DiaryResponse entrada(Long id) {
        LocalDateTime quando = LocalDateTime.of(2026, 11, 13, 20, 30);
        return new DiaryResponse(id, "Dia corrido", "Hoje foi puxado, mas consegui descansar.",
                MoodType.MAIS_OU_MENOS, quando, quando);
    }

    @Test
    void criarDevolve201ComAEntrada() throws Exception {
        when(diaryService.criar(eq(7L), any())).thenReturn(entrada(5L));

        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Dia corrido", "text": "Hoje foi puxado, mas consegui descansar.", "emotion": "MAIS_OU_MENOS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Dia corrido"))
                .andExpect(jsonPath("$.emotion").value("MAIS_OU_MENOS"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.user").doesNotExist());

        verify(diaryService).criar(7L, new DiaryRequest("Dia corrido",
                "Hoje foi puxado, mas consegui descansar.", MoodType.MAIS_OU_MENOS));
    }

    @Test
    void emocaoEhOpcional() throws Exception {
        when(diaryService.criar(eq(7L), any())).thenReturn(entrada(6L));

        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Sem emoção\", \"text\": \"Só um pensamento.\"}"))
                .andExpect(status().isCreated());

        verify(diaryService).criar(7L, new DiaryRequest("Sem emoção", "Só um pensamento.", null));
    }

    @Test
    void semTokenDa401EmTodasAsRotas() throws Exception {
        mvc.perform(get("/v1/diary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/diary/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/diary").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"a\", \"text\": \"b\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/v1/diary/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"a\", \"text\": \"b\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/v1/diary/1")).andExpect(status().isUnauthorized());

        verifyNoInteractions(diaryService);
    }

    @Test
    void tituloOuTextoEmBrancoDa400() throws Exception {
        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"   \", \"text\": \"algo\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Título\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(diaryService);
    }

    @Test
    void textosGrandesDemaisDao400() throws Exception {
        String tituloLongo = "a".repeat(101);
        String textoLongo = "b".repeat(5001);

        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + tituloLongo + "\", \"text\": \"ok\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"ok\", \"text\": \"" + textoLongo + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(diaryService);
    }

    @Test
    void emocaoInvalidaDa400() throws Exception {
        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"t\", \"text\": \"x\", \"emotion\": \"FELIZ_DEMAIS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("400"));
        mvc.perform(post("/v1/diary")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(diaryService);
    }

    @Test
    void idQueNaoEhNumeroDa400() throws Exception {
        mvc.perform(get("/v1/diary/abc").header("Authorization", auth()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(diaryService);
    }

    @Test
    void listarRepassaABuscaEUsaODonoDoToken() throws Exception {
        when(diaryService.listar(7L, "descansar")).thenReturn(List.of(entrada(5L)));

        mvc.perform(get("/v1/diary").param("q", "descansar").param("userId", "99")
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));

        verify(diaryService).listar(7L, "descansar");
    }

    @Test
    void listarSemBuscaPassaNull() throws Exception {
        when(diaryService.listar(eq(7L), isNull())).thenReturn(List.of());

        mvc.perform(get("/v1/diary").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void entradaDeOutroUsuarioDa404() throws Exception {
        when(diaryService.buscarPorId(7L, 50L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada do diário não encontrada"));

        mvc.perform(get("/v1/diary/50").header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizarEApagarUsamODonoDoToken() throws Exception {
        when(diaryService.atualizar(eq(7L), eq(5L), any())).thenReturn(entrada(5L));

        mvc.perform(put("/v1/diary/5")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Novo\", \"text\": \"Texto novo\", \"emotion\": \"BEM\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/v1/diary/5").header("Authorization", auth()))
                .andExpect(status().isNoContent());

        verify(diaryService).atualizar(7L, 5L, new DiaryRequest("Novo", "Texto novo", MoodType.BEM));
        verify(diaryService).deletar(7L, 5L);
    }
}
