package vivaleve.proj;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.EntryDtos.EntryRequest;
import vivaleve.proj.model.EntryEntity;
import vivaleve.proj.model.enums.Mood;
import vivaleve.proj.repository.EntryRepository;
import vivaleve.proj.repository.UserRepository;
import vivaleve.proj.service.EntryService;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Garante que o EntryService nunca mexe em registro de outro usuário. */
class EntryServiceOwnershipTest {

    private final EntryRepository entryRepository = mock(EntryRepository.class);
    private final EntryService service = new EntryService(entryRepository, mock(UserRepository.class));

    @Test
    void registroDeOutroUsuarioEhTratadoComo404EmTodasAsOperacoes() {
        // registro 50 pertence a outra pessoa: a busca filtrada por dono (usuário 7) não acha nada
        when(entryRepository.findByIdAndUser_Id(50L, 7L)).thenReturn(Optional.empty());
        EntryRequest req = new EntryRequest(Mood.values()[0], null, null, LocalDate.now());

        assertNotFound(() -> service.buscarPorId(7L, 50L));
        assertNotFound(() -> service.atualizar(7L, 50L, req));
        assertNotFound(() -> service.deletar(7L, 50L));

        verify(entryRepository, never()).save(any());
        verify(entryRepository, never()).delete(any());
    }

    @Test
    void donoConsegueApagarOProprioRegistro() {
        EntryEntity entry = new EntryEntity();
        when(entryRepository.findByIdAndUser_Id(50L, 7L)).thenReturn(Optional.of(entry));

        service.deletar(7L, 50L);

        verify(entryRepository).delete(entry);
    }

    private void assertNotFound(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
