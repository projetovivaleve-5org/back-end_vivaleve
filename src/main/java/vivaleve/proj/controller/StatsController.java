package vivaleve.proj.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vivaleve.proj.config.CurrentUser;
import vivaleve.proj.dto.StatsDtos.StatsSummary;
import vivaleve.proj.service.StatsService;

@RestController
@RequestMapping("/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * GET /v1/stats?days=7 — estatísticas de humor do usuário logado (7 ou 30 dias).
     * ALTERADO: o parâmetro {@code userId} foi removido; o usuário vem do JWT.
     */
    @GetMapping
    public StatsSummary resumo(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam(defaultValue = "7") int days) {
        return statsService.gerarResumo(CurrentUser.id(jwt), days);
    }
}
