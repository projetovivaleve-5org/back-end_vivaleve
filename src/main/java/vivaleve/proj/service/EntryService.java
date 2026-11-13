package vivaleve.proj.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.EntryDtos.EntryRequest;
import vivaleve.proj.dto.EntryDtos.EntryResponse;
import vivaleve.proj.model.EntryEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.EntryRepository;
import vivaleve.proj.repository.UserRepository;

import java.util.HashSet;
import java.util.List;

/**
 * Regras de negócio dos registros de humor.
 *
 * <p>ALTERADO: todo método recebe o {@code userId} do usuário autenticado (vindo do JWT)
 * e só opera sobre registros desse usuário. Um registro de outra pessoa é tratado como
 * inexistente (404) — responder 403 revelaria que aquele id existe.</p>
 */
@Service
@RequiredArgsConstructor
public class EntryService {

    private final EntryRepository entryRepository;
    private final UserRepository userRepository;

    /** Cria um registro de humor para o usuário autenticado (404 se a conta foi apagada). */
    @Transactional
    public EntryResponse criar(Long userId, EntryRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        EntryEntity entry = new EntryEntity();
        entry.setUser(user);
        aplicarDados(entry, request);

        return EntryResponse.from(entryRepository.save(entry));
    }

    /** Lista todos os registros do usuário, do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public List<EntryResponse> listarPorUsuario(Long userId) {
        return entryRepository.findByUser_IdOrderByDateDesc(userId).stream()
                .map(EntryResponse::from)
                .toList();
    }

    /** Busca um registro do usuário pelo id. */
    @Transactional(readOnly = true)
    public EntryResponse buscarPorId(Long userId, Long id) {
        return EntryResponse.from(buscarEntidadeDoUsuario(userId, id));
    }

    /** Atualiza os dados de um registro do usuário (o dono não muda). */
    @Transactional
    public EntryResponse atualizar(Long userId, Long id, EntryRequest request) {
        EntryEntity entry = buscarEntidadeDoUsuario(userId, id);
        aplicarDados(entry, request);
        return EntryResponse.from(entryRepository.save(entry));
    }

    /** Apaga um registro do usuário. */
    @Transactional
    public void deletar(Long userId, Long id) {
        entryRepository.delete(buscarEntidadeDoUsuario(userId, id));
    }

    /**
     * ALTERADO: antes buscava só pelo id ({@code findById}), então qualquer pessoa acessava
     * qualquer registro. Agora a consulta filtra por id E dono; se não achar → 404.
     */
    private EntryEntity buscarEntidadeDoUsuario(Long userId, Long id) {
        return entryRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
    }

    /** Copia os dados da requisição para a entidade (usado no criar e no atualizar). */
    private void aplicarDados(EntryEntity entry, EntryRequest request) {
        entry.setMood(request.mood());
        entry.setActivities(request.activities() == null ? new HashSet<>() : new HashSet<>(request.activities()));
        entry.setNote(request.note());
        entry.setDate(request.date());
    }
}
