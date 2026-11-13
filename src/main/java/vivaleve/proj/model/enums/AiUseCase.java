package vivaleve.proj.model.enums;

/**
 * Casos de uso de IA no Pluma (PL-22).
 * Detalhes de cada um em docs/ia-casos-de-uso.md.
 */
public enum AiUseCase {

    /** Conversa de apoio emocional na tela chat.html. Multi-turno, com histórico. */
    CHAT_APOIO("prompts/chat-apoio.txt"),

    /** Leitura acolhedora das estatísticas de humor (7 ou 30 dias) para a tela historico.html. */
    INSIGHT_HUMOR("prompts/insight-humor.txt"),

    /** Comentário curto e empático sobre um registro de humor/diário específico. */
    REFLEXAO_REGISTRO("prompts/reflexao-registro.txt");

    private final String promptPath;

    AiUseCase(String promptPath) {
        this.promptPath = promptPath;
    }

    /** Caminho (no classpath) do template com as instruções específicas do caso de uso. */
    public String getPromptPath() {
        return promptPath;
    }
}
