package vivaleve.proj.service.ai;

import org.junit.jupiter.api.Test;
import vivaleve.proj.config.AiProperties;
import vivaleve.proj.dto.StatsDtos.MoodDistributionItem;
import vivaleve.proj.dto.StatsDtos.MoodEvolutionPoint;
import vivaleve.proj.dto.StatsDtos.StatsSummary;
import vivaleve.proj.model.AiMessageEntity;
import vivaleve.proj.model.EntryEntity;
import vivaleve.proj.model.enums.*;
import vivaleve.proj.service.ai.AiClient.AiChatMessage;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PromptFactoryTest {

    private final PromptFactory factory = new PromptFactory(
            new AiProperties(null, null, null, null, null, null, null, null, 100));

    @Test
    void systemPromptTemRegrasDeSegurancaEPlaceholdersPreenchidos() {
        String prompt = factory.systemPrompt(AiUseCase.CHAT_APOIO, "Ana");

        assertThat(prompt)
                .contains("Pluma", "Ana", "CVV", "188", "120 palavras", "CASO DE USO: CHAT DE APOIO")
                .doesNotContain("{{");
    }

    @Test
    void todosOsCasosDeUsoTemTemplate() {
        for (AiUseCase useCase : AiUseCase.values()) {
            assertThat(factory.systemPrompt(useCase, null)).contains("CASO DE USO").contains("não informado");
        }
    }

    @Test
    void chatMontaSystemHistoricoENovaMensagemEmOrdem() {
        List<AiChatMessage> msgs = factory.buildChat("Ana", List.of(
                msg(AiMessageRole.USER, "oi"),
                msg(AiMessageRole.ASSISTANT, "Olá! Como você está?")
        ), "estou ansiosa");

        assertThat(msgs).extracting(AiChatMessage::role).containsExactly("system", "user", "assistant", "user");
        assertThat(msgs.getLast().content()).isEqualTo("estou ansiosa");
    }

    @Test
    void descreveEstatisticasSemInventarDados() {
        StatsSummary stats = new StatsSummary(7, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10),
                3, Mood.GOOD, 2, 3,
                List.of(new MoodEvolutionPoint(LocalDate.of(2026, 11, 10), 4.0)),
                List.of(new MoodDistributionItem(Mood.GOOD, 2, 66.67),
                        new MoodDistributionItem(Mood.BAD, 1, 33.33),
                        new MoodDistributionItem(Mood.AWFUL, 0, 0.0)));

        String texto = factory.describeStats(stats);

        assertThat(texto).contains("últimos 7 dias", "Total de registros: 3", "Humor predominante: bem",
                "bem 2x (66.67%)", "mal 1x (33.33%)", "2026-11-10=4.0");
        assertThat(texto).doesNotContain("muito mal 0x");
    }

    @Test
    void descreveRegistroEmPortugues() {
        EntryEntity e = new EntryEntity();
        e.setMood(Mood.NEUTRAL);
        e.setActivities(Set.of(Activity.EXERCISE, Activity.SLEEP_WELL));
        e.setNote("dia corrido");
        e.setDate(LocalDate.of(2026, 11, 9));

        assertThat(factory.describeEntry(e))
                .contains("2026-11-09", "Humor: neutro", "dormiu bem, exercício", "\"dia corrido\"");
    }

    @Test
    void sanitizeRemoveRaciocinioEMarkdown() {
        String raw = "<think>vou pensar</think>\n## Título\n**Respire** fundo.\n* passo um\n\n\n\nFim.  ";

        assertThat(factory.sanitizeReply(raw)).isEqualTo("Título\nRespire fundo.\n- passo um\n\nFim.");
    }

    @Test
    void sanitizeCortaNoLimiteDeCaracteres() {
        String longo = "palavra ".repeat(50);

        String result = factory.sanitizeReply(longo);

        assertThat(result).hasSizeLessThanOrEqualTo(101).endsWith("…");
    }

    @Test
    void sanitizeDevolveNullQuandoNaoSobraTexto() {
        assertThat(factory.sanitizeReply("<think>só raciocínio</think>  ")).isNull();
        assertThat(factory.sanitizeReply(null)).isNull();
    }

    @Test
    void respostasFixasCarregadas() {
        assertThat(factory.crisisReply()).contains("188", "192");
        assertThat(factory.fallbackReply()).isNotBlank();
    }

    @Test
    void tituloCurto() {
        assertThat(factory.titleFrom("  oi   tudo bem? ")).isEqualTo("oi tudo bem?");
        assertThat(factory.titleFrom("a".repeat(100))).hasSize(60).endsWith("...");
    }

    private static AiMessageEntity msg(AiMessageRole role, String content) {
        AiMessageEntity m = new AiMessageEntity();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
