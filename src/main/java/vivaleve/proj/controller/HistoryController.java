package vivaleve.proj.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import vivaleve.proj.dto.HistoryDto;
import vivaleve.proj.service.HistoryService;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService service;

    @GetMapping("/summary")
    public HistoryDto.SummaryResponse summary(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        return service.summary(userId);
    }

    @GetMapping("/evolution")
    public List<HistoryDto.EvolutionPoint> evolution(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "7") int days) {

        Long userId = Long.valueOf(jwt.getSubject());

        return service.evolution(userId, days);
    }

    @GetMapping("/distribution")
    public HistoryDto.DistributionResponse distribution(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30") int days) {

        Long userId = Long.valueOf(jwt.getSubject());

        return service.distribution(userId, days);
    }
}

