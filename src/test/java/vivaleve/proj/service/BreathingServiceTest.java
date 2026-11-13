package vivaleve.proj.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionRequest;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionResponse;
import vivaleve.proj.dto.BreathingDtos.BreathingStatsResponse;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.BreathingSessionRepository;
import vivaleve.proj.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BreathingServiceTest {

    private final BreathingSessionRepository repo = mock(BreathingSessionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    /** 14/11 01:00 em UTC = 13/11 22:00 no Brasil. */
    private final Clock noite = Clock.fixed(Instant.parse("2026-11-14T01:00:00Z"), ZoneOffset.UTC);
    private final BreathingService service = new BreathingService(repo, userRepository, noite);

    @Test
    void sessaoAsDezDaNoiteNoBrasilContaNoDiaDoBrasilMesmoComServidorEmUtc() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(new UserEntity()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BreathingSessionResponse resp = service.registrar(7L, new BreathingSessionRequest(" 3-4-5 ", 5, 60));

        assertThat(resp.sessionDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(resp.technique()).isEqualTo("3-4-5");
    }

    @Test
    void statsContaHojeNoFusoDoBrasilEArredondaOsMinutos() {
        when(repo.countByUser_IdAndSessionDate(7L, LocalDate.of(2026, 11, 13))).thenReturn(2L);
        when(repo.countByUser_Id(7L)).thenReturn(5L);
        when(repo.sumDurationSecondsByUserId(7L)).thenReturn(330L); // 5,5 min

        BreathingStatsResponse stats = service.estatisticas(7L);

        assertThat(stats).isEqualTo(new BreathingStatsResponse(2, 5, 6));
    }

    @Test
    void semSessoesTudoZero() {
        assertThat(service.estatisticas(7L)).isEqualTo(new BreathingStatsResponse(0, 0, 0));
    }

    @Test
    void usuarioInexistenteDa404ENaoSalva() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registrar(7L, new BreathingSessionRequest("3-4-5", 5, 60)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(repo, never()).save(any());
    }

    @Test
    void limparApagaSoODoUsuario() {
        service.limpar(7L);
        verify(repo).deleteAllByUserId(7L);
    }
}
