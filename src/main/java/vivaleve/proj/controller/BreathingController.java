package vivaleve.proj.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vivaleve.proj.config.CurrentUser;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionRequest;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionResponse;
import vivaleve.proj.dto.BreathingDtos.BreathingStatsResponse;
import vivaleve.proj.service.BreathingService;

import java.util.List;

/**
 * Exercício de respiração (tela respirar.html). Todas as rotas exigem
 * {@code Authorization: Bearer <token>}; o dono vem do token.
 */
@RestController
@RequestMapping("/v1/breathing")
@RequiredArgsConstructor
@Validated
public class BreathingController {

    private final BreathingService breathingService;

    /** POST /v1/breathing/sessions — registra uma sessão concluída (201). */
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public BreathingSessionResponse registrar(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody BreathingSessionRequest request) {
        return breathingService.registrar(CurrentUser.id(jwt), request);
    }

    /** GET /v1/breathing/sessions — histórico, da mais recente para a mais antiga. */
    @GetMapping("/sessions")
    public List<BreathingSessionResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        return breathingService.listar(CurrentUser.id(jwt));
    }

    /** GET /v1/breathing/stats — { todaySessions, totalSessions, totalMinutes }. */
    @GetMapping("/stats")
    public BreathingStatsResponse estatisticas(@AuthenticationPrincipal Jwt jwt) {
        return breathingService.estatisticas(CurrentUser.id(jwt));
    }

    /** DELETE /v1/breathing/sessions — apaga o histórico (botão "Limpar"), 204. */
    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void limpar(@AuthenticationPrincipal Jwt jwt) {
        breathingService.limpar(CurrentUser.id(jwt));
    }
}
