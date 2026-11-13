package vivaleve.proj.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vivaleve.proj.dto.StatsDtos.StatsSummary;
import vivaleve.proj.service.StatsService;

@RestController
@RequestMapping("/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** GET /v1/stats?userId=1&days=7 — devolve as estatísticas de humor do usuário no período (7 ou 30 dias). */
    @GetMapping
    public StatsSummary resumo(@RequestParam Long userId,
                               @RequestParam(defaultValue = "7") int days) {
        return statsService.gerarResumo(userId, days);
    }
}
