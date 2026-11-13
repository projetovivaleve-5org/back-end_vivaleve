package vivaleve.proj.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CrisisDetectorTest {

    private final CrisisDetector detector = new CrisisDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "Não quero mais viver",
            "às vezes penso em SUICÍDIO",
            "eu queria morrer hoje",
            "tenho vontade de me cortar",
            "acho que seria melhor sem mim",
            "pensei em tirar   minha vida"
    })
    void detectaFrasesDeRisco(String mensagem) {
        assertThat(detector.isCrisis(mensagem)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Estou me sentindo ansioso",
            "Estou triste hoje",
            "morrendo de rir com meus amigos",
            "Preciso de ajuda para dormir melhor"
    })
    void naoDisparaEmMensagensComuns(String mensagem) {
        assertThat(detector.isCrisis(mensagem)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void mensagemVaziaNaoEhCrise(String mensagem) {
        assertThat(detector.isCrisis(mensagem)).isFalse();
    }

    @Test
    void normalizaAcentosECaixa() {
        assertThat(CrisisDetector.normalize("NÃO  Aguento")).isEqualTo("nao aguento");
    }
}
