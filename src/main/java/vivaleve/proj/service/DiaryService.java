package vivaleve.proj.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.DiaryDtos.DiaryRequest;
import vivaleve.proj.dto.DiaryDtos.DiaryResponse;
import vivaleve.proj.model.DiaryEntryEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.DiaryEntryRepository;
import vivaleve.proj.repository.UserRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Regras do diário emocional.
 *
 * <p>Todo método recebe o id do usuário autenticado (do JWT) e só mexe nas entradas dele.
 * Entrada de outra pessoa é tratada como inexistente (404), igual ao {@link EntryService}.</p>
 */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryEntryRepository diaryRepository;
    private final UserRepository userRepository;

    /** Cria uma entrada para o usuário autenticado (404 se a conta foi apagada). */
    @Transactional
    public DiaryResponse criar(Long userId, DiaryRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        DiaryEntryEntity entry = new DiaryEntryEntity();
        entry.setUser(user);
        aplicarDados(entry, request);
        return DiaryResponse.from(diaryRepository.save(entry));
    }

    /**
     * Entradas do usuário, da mais recente para a mais antiga.
     *
     * @param busca texto opcional: filtra pelo título ou pelo texto, sem diferenciar
     *              maiúsculas nem acentos (igual à busca da tela do diário)
     */
    @Transactional(readOnly = true)
    public List<DiaryResponse> listar(Long userId, String busca) {
        String termo = normalizar(busca);
        return diaryRepository.findByUser_IdOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(e -> termo.isEmpty()
                        || normalizar(e.getTitle()).contains(termo)
                        || normalizar(e.getText()).contains(termo))
                .map(DiaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DiaryResponse buscarPorId(Long userId, Long id) {
        return DiaryResponse.from(buscarEntidadeDoUsuario(userId, id));
    }

    /** Atualiza título, texto e emoção (o dono e a data de criação não mudam). */
    @Transactional
    public DiaryResponse atualizar(Long userId, Long id, DiaryRequest request) {
        DiaryEntryEntity entry = buscarEntidadeDoUsuario(userId, id);
        aplicarDados(entry, request);
        return DiaryResponse.from(diaryRepository.save(entry));
    }

    @Transactional
    public void deletar(Long userId, Long id) {
        diaryRepository.delete(buscarEntidadeDoUsuario(userId, id));
    }

    private DiaryEntryEntity buscarEntidadeDoUsuario(Long userId, Long id) {
        return diaryRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada do diário não encontrada"));
    }

    private void aplicarDados(DiaryEntryEntity entry, DiaryRequest request) {
        entry.setTitle(request.title().trim());
        entry.setText(request.text().trim());
        entry.setEmotion(request.emotion());
    }

    /** minúsculas e sem acentos: "Ansiedade" e "ANSIÉDADE" viram "ansiedade". */
    static String normalizar(String s) {
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).trim();
    }
}
