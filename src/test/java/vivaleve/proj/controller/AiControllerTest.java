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
import vivaleve.proj.dto.AiDtos.AiResponse;
import vivaleve.proj.model.enums.AiInteractionStatus;
import vivaleve.proj.model.enums.AiUseCase;
import vivaleve.proj.service.ai.AiService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contrato HTTP da camada de IA (rotas, validação e formato do JSON), com a segurança real:
 * o usuário vem sempre do JWT, nunca de um userId enviado pelo cliente.
 */
@WebMvcTest(AiController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes!!")
class AiControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoBean
    AiService aiService;

    /** Token válido da API para o usuário 7. */
    private String auth() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("7").issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        return "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static AiResponse resposta(AiUseCase useCase) {
        return new AiResponse(10L, 21L, useCase, AiInteractionStatus.SUCCESS, "Estou aqui com você.",
                false, List.of(), "openai/gpt-oss-20b", LocalDateTime.of(2026, 11, 10, 12, 0));
    }

    @Test
    void chatDevolveEnvelopePadrao() throws Exception {
        when(aiService.chat(eq(7L), any())).thenReturn(resposta(AiUseCase.CHAT_APOIO));

        mvc.perform(post("/v1/ai/chat")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Estou triste hoje", "userName": "Ana"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(10))
                .andExpect(jsonPath("$.messageId").value(21))
                .andExpect(jsonPath("$.useCase").value("CHAT_APOIO"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.reply").value("Estou aqui com você."))
                .andExpect(jsonPath("$.crisisDetected").value(false))
                .andExpect(jsonPath("$.helpResources").isArray());
    }

    @Test
    void chatSemTokenDa401() throws Exception {
        mvc.perform(post("/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"oi\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(aiService);
    }

    @Test
    void userIdEnviadoPeloClienteEhIgnorado() throws Exception {
        // mesmo mandando userId 99 no corpo, quem vale é o dono do token (7)
        mvc.perform(post("/v1/ai/chat")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 99, \"message\": \"oi\"}"))
                .andExpect(status().isOk());
        verify(aiService).chat(eq(7L), any());

        mvc.perform(get("/v1/ai/conversations").param("userId", "99").header("Authorization", auth()))
                .andExpect(status().isOk());
        verify(aiService).listConversations(7L);
    }

    @Test
    void chatSemMensagemDa400() throws Exception {
        mvc.perform(post("/v1/ai/chat")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(aiService);
    }

    @Test
    void mensagemGrandeDemaisDa400() throws Exception {
        String grande = "a".repeat(2001);
        mvc.perform(post("/v1/ai/chat")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"" + grande + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversaInexistenteOuDeOutroUsuarioDa404() throws Exception {
        when(aiService.listMessages(99L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada"));

        mvc.perform(get("/v1/ai/conversations/99/messages").header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void apagarConversaDa204() throws Exception {
        mvc.perform(delete("/v1/ai/conversations/5").header("Authorization", auth()))
                .andExpect(status().isNoContent());
        verify(aiService).deleteConversation(5L, 7L);
    }

    @Test
    void reflexaoUsaIdDoPathEDonoDoToken() throws Exception {
        when(aiService.entryReflection(eq(7L), eq(3L), any())).thenReturn(resposta(AiUseCase.REFLEXAO_REGISTRO));

        mvc.perform(post("/v1/ai/entries/3/reflection")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useCase").value("REFLEXAO_REGISTRO"));
    }
}
