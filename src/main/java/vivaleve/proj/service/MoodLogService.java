package vivaleve.proj.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vivaleve.proj.dto.MoodLogDto;
import vivaleve.proj.dto.UserResponseDto;
import vivaleve.proj.exceptions.BadRequestExceptions;
import vivaleve.proj.exceptions.NotFoundException;
import vivaleve.proj.model.MoodLogEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.MoodLogRepository;
import vivaleve.proj.repository.UserRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MoodLogService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_LOGS_PER_DAY = 3;

    private final MoodLogRepository moodLogRepository;
    private final UserRepository userRepository;
    // private final FileStorageService fileStorageService;  // upload da imagem

    @Transactional
    public MoodLogDto.MoodLogResponse create(Long userId, MoodLogDto.MoodLogRequest req, MultipartFile image) {
        LocalDate today = LocalDate.now(ZONE);

        if (moodLogRepository.countByUserIdAndLogDate(userId, today) >= MAX_LOGS_PER_DAY) {
            throw new BadRequestExceptions(
                    "Você já atingiu o limite de " + MAX_LOGS_PER_DAY + " registros de humor hoje");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        MoodLogEntity log = MoodLogEntity.builder()
                .user(user)
                .mood(req.mood())
                .note(req.note())
                .logDate(today)
                .imageUrl(saveImage(image))
                .build();

        return toResponse(moodLogRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<MoodLogDto.MoodLogResponse> getToday(Long userId) {

        LocalDate today = LocalDate.now(ZONE);

        return moodLogRepository
                .findAllByUserIdAndLogDateOrderByCreatedAtDesc(userId, today)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MoodLogDto.MoodLogResponse> listAll(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Usuário não encontrado");
        }
        return moodLogRepository.findByUserIdOrderByLogDateDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MoodLogDto.MoodLogResponse> history(Long userId, Pageable pageable) {
        return moodLogRepository.findByUserIdOrderByLogDateDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public MoodLogDto.MoodLogResponse update(
            Long userId,
            Long id,
            MoodLogDto.MoodLogRequest req,
            MultipartFile image) {

        MoodLogEntity log = findOwned(userId, id);

        log.setMood(req.mood());
        log.setNote(req.note());

        if (image != null && !image.isEmpty()) {
            log.setImageUrl(saveImage(image));
        }

        return toResponse(moodLogRepository.save(log));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        moodLogRepository.delete(findOwned(userId, id));
    }

    // ---- helpers ----
    private MoodLogEntity findOwned(Long userId, Long id) {
        MoodLogEntity log = moodLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Registro não encontrado"));

        if (!log.getUser().getId().equals(userId)) {
            throw new NotFoundException("Registro não encontrado"); // não revela que existe
        }
        return log;
    }

    private String saveImage(MultipartFile image) {

        if (image == null || image.isEmpty()) return null;
        String type = image.getContentType();

        if (type == null || !type.startsWith("image/")) {
            throw new BadRequestExceptions("O arquivo precisa ser uma imagem");
        }
        // salvar em disco/S3/Cloudinary e retornar a URL
        return null; // TODO
    }

    private MoodLogDto.MoodLogResponse toResponse(MoodLogEntity m) {
        UserEntity u = m.getUser();
        return new MoodLogDto.MoodLogResponse(
                m.getId(), m.getMood(), m.getNote(),
                m.getImageUrl(), m.getLogDate(), m.getCreatedAt(),
                new UserResponseDto(u.getId(), u.getEmail(), u.getName(), u.getPictureUrl()));

    }
}