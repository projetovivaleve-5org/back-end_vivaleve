package vivaleve.proj.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.DiaryDtos.DiaryRequest;
import vivaleve.proj.dto.DiaryDtos.DiaryResponse;
import vivaleve.proj.model.DiaryEntryEntity;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.model.enums.MoodType;
import vivaleve.proj.repository.DiaryEntryRepository;
import vivaleve.proj.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regras do DiaryService: dono, limpeza dos campos e busca. */
class DiaryServiceTest {

    private final DiaryEntryRepository diaryRepository = mock(DiaryEntryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DiaryService service = new DiaryService(diaryRepository, userRepository);

    private static DiaryEntryEntity entrada(String titulo, String texto) {
        DiaryEntryEntity e = new DiaryEntryEntity();
        e.setTitle(titulo);
        e.setText(texto);
        return e;
    }

    @Test
    void criarLigaAoUsuarioETiraEspacosDasPontas() {
        UserEntity user = new UserEntity();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(diaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiaryResponse resp = service.criar(7L, new DiaryRequest("  Meu dia  ", "\n Foi bom. \n", MoodType.BEM));

        assertThat(resp.title()).isEqualTo("Meu dia");
        assertThat(resp.text()).isEqualTo("Foi bom.");
        assertThat(resp.emotion()).isEqualTo(MoodType.BEM);
        verify(diaryRepository).save(argThat(e -> e.getUser() == user));
    }

    @Test
    void criarParaUsuarioInexistenteDa404() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertNotFound(() -> service.criar(7L, new DiaryRequest("t", "x", null)));
        verify(diaryRepository, never()).save(any());
    }

    @Test
    void entradaDeOutroUsuarioEhTratadaComo404EmTodasAsOperacoes() {
        when(diaryRepository.findByIdAndUser_Id(50L, 7L)).thenReturn(Optional.empty());
        DiaryRequest req = new DiaryRequest("t", "x", null);

        assertNotFound(() -> service.buscarPorId(7L, 50L));
        assertNotFound(() -> service.atualizar(7L, 50L, req));
        assertNotFound(() -> service.deletar(7L, 50L));

        verify(diaryRepository, never()).save(any());
        verify(diaryRepository, never()).delete(any());
    }

    @Test
    void atualizarTrocaOsCamposDaEntradaDoDono() {
        DiaryEntryEntity e = entrada("Antigo", "Texto antigo");
        e.setEmotion(MoodType.MAL);
        when(diaryRepository.findByIdAndUser_Id(5L, 7L)).thenReturn(Optional.of(e));
        when(diaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiaryResponse resp = service.atualizar(7L, 5L, new DiaryRequest("Novo", "Texto novo", null));

        assertThat(resp.title()).isEqualTo("Novo");
        assertThat(resp.text()).isEqualTo("Texto novo");
        assertThat(resp.emotion()).isNull();
    }

    @Test
    void donoConsegueApagar() {
        DiaryEntryEntity e = entrada("t", "x");
        when(diaryRepository.findByIdAndUser_Id(5L, 7L)).thenReturn(Optional.of(e));

        service.deletar(7L, 5L);

        verify(diaryRepository).delete(e);
    }

    @Test
    void buscaIgnoraMaiusculasEAcentosNoTituloENoTexto() {
        when(diaryRepository.findByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(
                entrada("Ansiedade antes da prova", "Respirei fundo."),
                entrada("Dia tranquilo", "Fiz uma caminhada e me senti ÓTIMA."),
                entrada("Trabalho", "Reunião longa.")));

        assertThat(service.listar(7L, "ANSIEDADE")).extracting(DiaryResponse::title)
                .containsExactly("Ansiedade antes da prova");
        assertThat(service.listar(7L, "otima")).extracting(DiaryResponse::title)
                .containsExactly("Dia tranquilo");
        assertThat(service.listar(7L, "reuniao")).extracting(DiaryResponse::title)
                .containsExactly("Trabalho");
        assertThat(service.listar(7L, "nada a ver")).isEmpty();
    }

    @Test
    void semBuscaDevolveTudoNaOrdemDoBanco() {
        when(diaryRepository.findByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(
                entrada("B", "x"), entrada("A", "y")));

        assertThat(service.listar(7L, null)).extracting(DiaryResponse::title).containsExactly("B", "A");
        assertThat(service.listar(7L, "   ")).hasSize(2);
    }

    private void assertNotFound(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
