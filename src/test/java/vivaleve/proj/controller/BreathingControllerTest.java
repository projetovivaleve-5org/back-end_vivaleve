package vivaleve.proj.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vivaleve.proj.config.SecurityConfig;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionRequest;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionResponse;
import vivaleve.proj.dto.BreathingDtos.BreathingStatsResponse;
import vivaleve.proj.service.BreathingService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato HTTP da respiração (/v1/breathing) com a segurança real: o dono vem sempre do JWT. */
@WebMvcTest(BreathingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=segredo-de-teste-com-pelo-menos-32-bytes!!")
class BreathingControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoBean
    BreathingService breathingService;

    private String auth() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("7").issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        return "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static BreathingSessionResponse sessao() {
        return new BreathingSessionResponse(3L, "3-4-5", 5, 60, LocalDate.of(2026, 11, 13),
                LocalDateTime.of(2026, 11, 13, 21, 0));
    }

    @Test
    void registrarDevolve201() throws Exception {
        when(breathingService.registrar(eq(7L), any())).thenReturn(sessao());

        mvc.perform(post("/v1/breathing/sessions")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technique\": \"3-4-5\", \"cycles\": 5, \"durationSeconds\": 60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.technique").value("3-4-5"))
                .andExpect(jsonPath("$.sessionDate").value("2026-11-13"))
                .andExpect(jsonPath("$.user").doesNotExist());

        verify(breathingService).registrar(7L, new BreathingSessionRequest("3-4-5", 5, 60));
    }

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(get("/v1/breathing/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/breathing/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/v1/breathing/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/breathing/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"technique\": \"3-4-5\", \"cycles\": 5, \"durationSeconds\": 60}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(breathingService);
    }

    @Test
    void valoresInvalidosDao400() throws Exception {
        String[] corpos = {
                "{\"technique\": \"\", \"cycles\": 5, \"durationSeconds\": 60}",
                "{\"technique\": \"3-4-5\", \"cycles\": 0, \"durationSeconds\": 60}",
                "{\"technique\": \"3-4-5\", \"cycles\": 5, \"durationSeconds\": 0}",
                "{\"technique\": \"3-4-5\", \"cycles\": 5, \"durationSeconds\": 7201}",
                "{\"technique\": \"3-4-5\", \"cycles\": 101, \"durationSeconds\": 60}",
                "{\"technique\": \"3-4-5\"}",
                "{\"technique\": \"3-4-5\", \"cycles\": \"muitos\", \"durationSeconds\": 60}"
        };
        for (String corpo : corpos) {
            mvc.perform(post("/v1/breathing/sessions")
                            .header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(breathingService);
    }

    @Test
    void statsDevolveOsTresNumerosDaTela() throws Exception {
        when(breathingService.estatisticas(7L)).thenReturn(new BreathingStatsResponse(2, 9, 9));

        mvc.perform(get("/v1/breathing/stats").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaySessions").value(2))
                .andExpect(jsonPath("$.totalSessions").value(9))
                .andExpect(jsonPath("$.totalMinutes").value(9));
    }

    @Test
    void listarELimparUsamODonoDoToken() throws Exception {
        when(breathingService.listar(7L)).thenReturn(List.of(sessao()));

        mvc.perform(get("/v1/breathing/sessions").param("userId", "99").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cycles").value(5));
        mvc.perform(delete("/v1/breathing/sessions").header("Authorization", auth()))
                .andExpect(status().isNoContent());

        verify(breathingService).listar(7L);
        verify(breathingService).limpar(7L);
    }
}
