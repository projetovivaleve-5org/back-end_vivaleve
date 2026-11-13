package vivaleve.proj.service.ai;

import java.util.List;

/**
 * Contrato de qualquer provedor de LLM (PL-23).
 * Hoje a implementação é o {@link GroqClient}; trocar de provedor é só criar outra implementação.
 */
public interface AiClient {

    /**
     * Envia as mensagens (system + histórico + usuário) e devolve a resposta do modelo.
     *
     * @throws AiClientException se o provedor falhar, demorar demais ou devolver resposta vazia
     */
    AiCompletion complete(List<AiChatMessage> messages);

    /** Uma mensagem no formato neutro (role = "system" | "user" | "assistant"). */
    record AiChatMessage(String role, String content) {
        public static AiChatMessage system(String content) {
            return new AiChatMessage("system", content);
        }

        public static AiChatMessage user(String content) {
            return new AiChatMessage("user", content);
        }

        public static AiChatMessage assistant(String content) {
            return new AiChatMessage("assistant", content);
        }
    }

    /** Resposta do modelo + metadados guardados no banco. */
    record AiCompletion(String content, String model, Integer promptTokens, Integer responseTokens, long latencyMs) {
    }
}
