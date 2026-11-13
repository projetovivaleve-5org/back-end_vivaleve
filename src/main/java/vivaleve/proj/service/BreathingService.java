package vivaleve.proj.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionRequest;
import vivaleve.proj.dto.BreathingDtos.BreathingSessionResponse;
import vivaleve.proj.dto.BreathingDtos.BreathingStatsResponse;
import vivaleve.proj.model.BreathingSessionEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.BreathingSessionRepository;
import vivaleve.proj.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Sessões do exercício de respiração do usuário autenticado (id vem do JWT).
 * "Hoje" é calculado no fuso do Brasil, como no MoodLog.
 */
@Service
public class BreathingService {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final BreathingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public BreathingService(BreathingSessionRepository sessionRepository, UserRepository userRepository) {
        this(sessionRepository, userRepository, Clock.system(ZONE));
    }

    /** Usado nos testes para fixar o dia. */
    BreathingService(BreathingSessionRepository sessionRepository, UserRepository userRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Registra uma sessão concluída. */
    @Transactional
    public BreathingSessionResponse registrar(Long userId, BreathingSessionRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        BreathingSessionEntity s = new BreathingSessionEntity();
        s.setUser(user);
        s.setTechnique(request.technique().trim());
        s.setCycles(request.cycles());
        s.setDurationSeconds(request.durationSeconds());
        s.setSessionDate(hoje());
        return BreathingSessionResponse.from(sessionRepository.save(s));
    }

    @Transactional(readOnly = true)
    public List<BreathingSessionResponse> listar(Long userId) {
        return sessionRepository.findByUser_IdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(BreathingSessionResponse::from)
                .toList();
    }

    /** Sessões hoje, total e minutos (segundos somados, arredondados para o minuto mais próximo). */
    @Transactional(readOnly = true)
    public BreathingStatsResponse estatisticas(Long userId) {
        long segundos = sessionRepository.sumDurationSecondsByUserId(userId);
        return new BreathingStatsResponse(
                sessionRepository.countByUser_IdAndSessionDate(userId, hoje()),
                sessionRepository.countByUser_Id(userId),
                Math.round(segundos / 60.0));
    }

    /** Apaga todo o histórico de respiração do usuário. */
    @Transactional
    public void limpar(Long userId) {
        sessionRepository.deleteAllByUserId(userId);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
