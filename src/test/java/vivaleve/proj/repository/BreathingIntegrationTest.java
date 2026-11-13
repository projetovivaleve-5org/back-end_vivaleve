package vivaleve.proj.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionRequest;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionResponse;
import vivaleve.proj.dto.BreathingDtos.BreathingStatsResponse;
import vivaleve.proj.model.BreathingSessionEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.service.BreathingService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Respiração de ponta a ponta com H2: registrar, estatísticas, histórico, limpar e isolamento. */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class BreathingIntegrationTest {

    @Autowired BreathingService breathingService;
    @Autowired BreathingSessionRepository repo;
    @Autowired UserRepository userRepository;

    private UserEntity novoUsuario(String email) {
        return userRepository.save(UserEntity.builder().email(email).password("12345678").build());
    }

    @Test
    void cicloCompleto() {
        UserEntity ana = novoUsuario("resp-ana@pluma.com");
        UserEntity bia = novoUsuario("resp-bia@pluma.com");

        BreathingSessionRequest padrao = new BreathingSessionRequest("3-4-5", 5, 60);
        BreathingSessionResponse s1 = breathingService.registrar(ana.getId(), padrao);
        BreathingSessionResponse s2 = breathingService.registrar(ana.getId(), padrao);
        breathingService.registrar(bia.getId(), padrao);

        // uma sessão antiga (ontem) da Ana: conta no total, não em "hoje"
        BreathingSessionEntity antiga = new BreathingSessionEntity();
        antiga.setUser(ana);
        antiga.setTechnique("3-4-5");
        antiga.setCycles(10);
        antiga.setDurationSeconds(150);
        antiga.setSessionDate(s1.sessionDate().minusDays(1));
        repo.save(antiga);

        assertThat(breathingService.estatisticas(ana.getId()))
                .isEqualTo(new BreathingStatsResponse(2, 3, 5)); // 60+60+150 = 270s = 4,5 → 5 min
        assertThat(breathingService.listar(ana.getId())).hasSize(3);
        assertThat(breathingService.listar(ana.getId()).get(0).id()).isIn(antiga.getId(), s2.id());

        breathingService.limpar(ana.getId());

        assertThat(breathingService.estatisticas(ana.getId())).isEqualTo(new BreathingStatsResponse(0, 0, 0));
        assertThat(breathingService.estatisticas(bia.getId())).isEqualTo(new BreathingStatsResponse(1, 1, 1));
        assertThat(s1.sessionDate()).isNotNull().isBeforeOrEqualTo(LocalDate.now().plusDays(1));
    }
}
