package vivaleve.proj.service.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vivaleve.proj.config.AiProperties;
import vivaleve.proj.dto.StatsDtos.MoodDistributionItem;
import vivaleve.proj.dto.StatsDtos.StatsSummary;
import vivaleve.proj.model.AiMessageEntity;
import vivaleve.proj.model.EntryEntity;
import vivaleve.proj.model.enums.Activity;
import vivaleve.proj.model.enums.AiMessageRole;
import vivaleve.proj.model.enums.AiUseCase;
import vivaleve.proj.model.enums.Mood;
import vivaleve.proj.service.ai.AiClient.AiChatMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Monta os prompts de cada caso de uso e padroniza as respostas (PL-24).
 * Os textos ficam em src/main/resources/prompts/*.txt para poderem ser revisados sem mexer no Java.
 */
@Component
public class PromptFactory {

    private static final Map<AiUseCase, Integer> MAX_PALAVRAS = Map.of(
            AiUseCase.CHAT_APOIO, 120,
            AiUseCase.INSIGHT_HUMOR, 150,
            AiUseCase.REFLEXAO_REGISTRO, 80
    );

    private static final Map<Mood, String> HUMOR_PT = Map.of(
            Mood.GREAT, "ótimo", Mood.GOOD, "bem", Mood.NEUTRAL, "neutro",
            Mood.BAD, "mal", Mood.AWFUL, "muito mal"
    );

    private static final Map<Activity, String> ATIVIDADE_PT = Map.ofEntries(
            Map.entry(Activity.EXERCISE, "exercício"), Map.entry(Activity.WORK, "trabalho"),
            Map.entry(Activity.STUDY, "estudo"), Map.entry(Activity.READING, "leitura"),
            Map.entry(Activity.FAMILY, "família"), Map.entry(Activity.FRIENDS, "amigos"),
            Map.entry(Activity.HOBBY, "hobby"), Map.entry(Activity.REST, "descanso"),
            Map.entry(Activity.SLEEP_WELL, "dormiu bem"), Map.entry(Activity.MEDITATION, "meditação")
    );

    private final AiProperties props;
    private final String sistema;
    private final Map<AiUseCase, String> instrucoes = new EnumMap<>(AiUseCase.class);
    private final String respostaCrise;
    private final String respostaFallback;

    public PromptFactory(AiProperties props) {
        this.props = props;
        this.sistema = load("prompts/sistema.txt");
        for (AiUseCase useCase : AiUseCase.values()) {
            instrucoes.put(useCase, load(useCase.getPromptPath()));
        }
        this.respostaCrise = load("prompts/resposta-crise.txt");
        this.respostaFallback = load("prompts/resposta-fallback.txt");
    }

    // ---------------------------------------------------------------- montagem dos prompts

    /** Chat: system + últimas mensagens da conversa (em ordem cronológica) + nova mensagem. */
    public List<AiChatMessage> buildChat(String userName, List<AiMessageEntity> history, String userMessage) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.system(systemPrompt(AiUseCase.CHAT_APOIO, userName)));
        for (AiMessageEntity m : history) {
            if (m.getRole() != AiMessageRole.SYSTEM) {
                messages.add(new AiChatMessage(m.getRole().getApiValue(), m.getContent()));
            }
        }
        messages.add(AiChatMessage.user(userMessage));
        return messages;
    }

    /** Insight: system + resumo das estatísticas em texto. */
    public List<AiChatMessage> buildMoodInsight(String userName, StatsSummary stats) {
        return List.of(
                AiChatMessage.system(systemPrompt(AiUseCase.INSIGHT_HUMOR, userName)),
                AiChatMessage.user(describeStats(stats))
        );
    }

    /** Reflexão: system + dados do registro em texto. */
    public List<AiChatMessage> buildEntryReflection(String userName, EntryEntity entry) {
        return List.of(
                AiChatMessage.system(systemPrompt(AiUseCase.REFLEXAO_REGISTRO, userName)),
                AiChatMessage.user(describeEntry(entry))
        );
    }

    String systemPrompt(AiUseCase useCase, String userName) {
        String nome = (userName == null || userName.isBlank()) ? "não informado" : userName.trim();
        return sistema
                .replace("{{nomeUsuario}}", nome)
                .replace("{{maxPalavras}}", String.valueOf(MAX_PALAVRAS.get(useCase)))
                + "\n\n" + instrucoes.get(useCase);
    }

    /** Texto enviado ao modelo no insight (também é o que fica salvo como mensagem do usuário). */
    public String describeStats(StatsSummary s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Resumo dos últimos ").append(s.days()).append(" dias (")
                .append(s.startDate()).append(" a ").append(s.endDate()).append("):\n");
        sb.append("- Total de registros: ").append(s.totalEntries()).append('\n');
        sb.append("- Humor predominante: ")
                .append(s.predominantMood() == null ? "sem registros" : HUMOR_PT.get(s.predominantMood())).append('\n');
        sb.append("- Sequência atual de dias registrando: ").append(s.currentStreak()).append('\n');
        sb.append("- Maior sequência no período: ").append(s.longestStreak()).append('\n');
        if (s.distribution() != null && s.totalEntries() > 0) {
            String dist = s.distribution().stream()
                    .filter(d -> d.count() > 0)
                    .map(this::describeDistribution)
                    .collect(Collectors.joining(", "));
            sb.append("- Distribuição: ").append(dist).append('\n');
        }
        if (s.evolution() != null && !s.evolution().isEmpty()) {
            String evo = s.evolution().stream()
                    .map(p -> p.date() + "=" + p.averageScore())
                    .collect(Collectors.joining(", "));
            sb.append("- Média diária (5=ótimo, 1=muito mal): ").append(evo).append('\n');
        }
        return sb.toString().trim();
    }

    /** Texto enviado ao modelo na reflexão de um registro. */
    public String describeEntry(EntryEntity e) {
        String atividades = (e.getActivities() == null || e.getActivities().isEmpty())
                ? "nenhuma informada"
                : e.getActivities().stream().map(ATIVIDADE_PT::get).sorted().collect(Collectors.joining(", "));
        String nota = (e.getNote() == null || e.getNote().isBlank()) ? "sem nota" : "\"" + e.getNote().trim() + "\"";
        return "Registro do dia " + e.getDate() + ":\n"
                + "- Humor: " + HUMOR_PT.get(e.getMood()) + '\n'
                + "- Atividades: " + atividades + '\n'
                + "- Nota: " + nota;
    }

    private String describeDistribution(MoodDistributionItem d) {
        return HUMOR_PT.get(d.mood()) + " " + d.count() + "x (" + d.percentage() + "%)";
    }

    // ---------------------------------------------------------------- respostas padronizadas

    public String crisisReply() {
        return respostaCrise;
    }

    public String fallbackReply() {
        return respostaFallback;
    }

    /**
     * Padroniza o texto do modelo antes de salvar/devolver:
     * remove blocos de raciocínio (&lt;think&gt;), markdown pesado, espaços sobrando e corta no limite.
     * Devolve null se não sobrar nada (o serviço então usa o fallback).
     */
    public String sanitizeReply(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw
                .replaceAll("(?is)<think>.*?</think>", "")
                .replaceAll("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*", "")
                .replace("**", "")
                .replace("__", "")
                .replaceAll("(?m)^[ \\t]*[*•][ \\t]+", "- ")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (text.isEmpty()) {
            return null;
        }
        int max = props.maxResponseChars();
        if (text.length() > max) {
            int cut = text.lastIndexOf(' ', max - 1);
            text = text.substring(0, cut > max / 2 ? cut : max - 1).trim() + "…";
        }
        return text;
    }

    /** Título curto para a lista de conversas (primeiras palavras da mensagem). */
    public String titleFrom(String message) {
        String t = message.strip().replaceAll("\\s+", " ");
        return t.length() <= 60 ? t : t.substring(0, 57).trim() + "...";
    }

    private static String load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Template de prompt não encontrado: " + path, e);
        }
    }
}
