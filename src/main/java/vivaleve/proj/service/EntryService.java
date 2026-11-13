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

@Service
@RequiredArgsConstructor
public class EntryService {

    private final EntryRepository entryRepository;
    private final UserRepository userRepository;

    /** Cria um registro de humor ligado ao usuário informado (404 se o usuário não existir). */
    @Transactional
    public EntryResponse criar(EntryRequest request) {
        UserEntity user = userRepository.findById(request.userId())
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

    /** Busca um registro pelo id e devolve no formato de resposta da API. */
    @Transactional(readOnly = true)
    public EntryResponse buscarPorId(Long id) {
        return EntryResponse.from(buscarEntidade(id));
    }

    /** Atualiza os dados de um registro existente (o usuário dono não muda). */
    @Transactional
    public EntryResponse atualizar(Long id, EntryRequest request) {
        EntryEntity entry = buscarEntidade(id);
        aplicarDados(entry, request);
        return EntryResponse.from(entryRepository.save(entry));
    }

    /** Apaga um registro pelo id (404 se não existir). */
    @Transactional
    public void deletar(Long id) {
        entryRepository.delete(buscarEntidade(id));
    }

    /** Busca a entidade no banco ou lança 404 se não encontrar. */
    private EntryEntity buscarEntidade(Long id) {
        return entryRepository.findById(id)
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
