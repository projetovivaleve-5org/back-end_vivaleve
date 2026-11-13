package vivaleve.proj.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.DiaryDtos.DiaryRequest;
import vivaleve.proj.dto.DiaryDtos.DiaryResponse;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.model.enums.MoodType;
import vivaleve.proj.service.DiaryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.temporal.ChronoUnit;

/** Diário de ponta a ponta com banco H2: gravar, listar, buscar, editar, isolar usuários e apagar. */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class DiaryIntegrationTest {

    @Autowired DiaryService diaryService;
    @Autowired UserRepository userRepository;
    @Autowired DiaryEntryRepository diaryRepository;

    private UserEntity novoUsuario(String email) {
        return userRepository.save(UserEntity.builder().email(email).password("12345678").build());
    }

    @Test
    void cicloCompletoComIsolamentoEntreUsuarios() {
        UserEntity ana = novoUsuario("diario-ana@pluma.com");
        UserEntity bia = novoUsuario("diario-bia@pluma.com");

        DiaryResponse primeira = diaryService.criar(ana.getId(), new DiaryRequest("Primeiro dia", "Comecei o diário.", MoodType.BEM));
        DiaryResponse segunda = diaryService.criar(ana.getId(), new DiaryRequest("Prova", "Fiquei ansiosa antes da prova.", MoodType.ANSIOSO));
        diaryService.criar(bia.getId(), new DiaryRequest("Da Bia", "Entrada de outra pessoa.", null));

        assertThat(primeira.id()).isNotNull();
        assertThat(primeira.createdAt()).isNotNull();

        // mais recente primeiro, só as da Ana
        assertThat(diaryService.listar(ana.getId(), null)).extracting(DiaryResponse::id)
                .containsExactly(segunda.id(), primeira.id());
        assertThat(diaryService.listar(ana.getId(), "ANSIOSA")).extracting(DiaryResponse::id)
                .containsExactly(segunda.id());

        // a Bia não enxerga nem altera as entradas da Ana
        assertThatThrownBy(() -> diaryService.buscarPorId(bia.getId(), primeira.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> diaryService.deletar(bia.getId(), primeira.id()))
                .isInstanceOf(ResponseStatusException.class);

        DiaryResponse editada = diaryService.atualizar(ana.getId(), primeira.id(),
                new DiaryRequest("Primeiro dia (editado)", "Comecei o diário e gostei.", null));
        assertThat(editada.title()).isEqualTo("Primeiro dia (editado)");
        assertThat(editada.emotion()).isNull();
        // editar não muda a data de criação (tolerância: o banco pode guardar menos casas decimais)
        assertThat(editada.createdAt()).isCloseTo(primeira.createdAt(), within(1, ChronoUnit.SECONDS));

        diaryService.deletar(ana.getId(), primeira.id());
        assertThat(diaryService.listar(ana.getId(), null)).extracting(DiaryResponse::id)
                .containsExactly(segunda.id());
        assertThat(diaryService.listar(bia.getId(), null)).hasSize(1);
    }

    @Test
    void textoDe5000CaracteresCabeNoBanco() {
        UserEntity user = novoUsuario("diario-longo@pluma.com");
        String texto = "á".repeat(5000);

        DiaryResponse salva = diaryService.criar(user.getId(), new DiaryRequest("t".repeat(100), texto, null));

        assertThat(diaryRepository.findById(salva.id())).get()
                .satisfies(e -> assertThat(e.getText()).hasSize(5000));
    }
}
