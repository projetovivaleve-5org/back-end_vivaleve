package vivaleve.proj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configurações da IA (Groq), lidas de "pluma.ai.*" no application.yml.
 * A chave vem SEMPRE do .env / variável de ambiente GROQ_API_KEY, nunca do código.
 */
@ConfigurationProperties(prefix = "pluma.ai")
public record AiProperties(
        String baseUrl,
        String apiKey,
        String model,
        String reasoningEffort,
        Double temperature,
        Integer maxTokens,
        Duration timeout,
        Integer historyLimit,
        Integer maxResponseChars
) {
    public AiProperties {
        baseUrl = isBlank(baseUrl) ? "https://api.groq.com/openai/v1" : baseUrl;
        model = isBlank(model) ? "openai/gpt-oss-20b" : model;
        reasoningEffort = isBlank(reasoningEffort) ? null : reasoningEffort.trim();
        temperature = temperature == null ? 0.7 : temperature;
        maxTokens = maxTokens == null ? 600 : maxTokens;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        historyLimit = historyLimit == null ? 10 : historyLimit;
        maxResponseChars = maxResponseChars == null ? 2000 : maxResponseChars;
    }

    public boolean hasApiKey() {
        return !isBlank(apiKey);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
