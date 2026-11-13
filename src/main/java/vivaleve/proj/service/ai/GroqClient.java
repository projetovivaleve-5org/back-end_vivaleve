package vivaleve.proj.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vivaleve.proj.config.AiProperties;

import java.util.List;

/**
 * Integração com o Groq (PL-23), API compatível com OpenAI. Plano gratuito com limite de requisições.
 * POST {baseUrl}/chat/completions com "Authorization: Bearer GROQ_API_KEY".
 * Docs: https://console.groq.com/docs/openai
 */
@Slf4j
@Component
public class GroqClient implements AiClient {

    private final RestClient restClient;
    private final AiProperties props;

    public GroqClient(@Qualifier("groqRestClient") RestClient restClient, AiProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    @Override
    public AiCompletion complete(List<AiChatMessage> messages) {
        if (!props.hasApiKey()) {
            throw new AiClientException("GROQ_API_KEY não configurada");
        }

        GroqChatRequest body = new GroqChatRequest(
                props.model(),
                messages,
                props.temperature(),
                props.maxTokens(),
                reasoningEffort(props)
        );

        long start = System.nanoTime();
        GroqChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                    .body(body)
                    .retrieve()
                    .body(GroqChatResponse.class);
        } catch (RestClientException e) {
            // 429 = limite do plano gratuito atingido. Não loga o corpo: pode conter desabafos do usuário.
            log.warn("Falha ao chamar Groq (modelo {}): {}", props.model(), e.getMessage());
            throw new AiClientException("Falha ao chamar o Groq", e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        String content = response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                ? null : response.choices().getFirst().message().content();
        if (content == null || content.isBlank()) {
            throw new AiClientException("Groq devolveu resposta vazia");
        }

        Usage usage = response.usage();
        return new AiCompletion(
                content,
                response.model() != null ? response.model() : props.model(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                latencyMs
        );
    }

    /**
     * Os modelos gpt-oss "pensam" antes de responder e esse raciocínio consome o limite de tokens.
     * Sem configuração explícita, usamos "low" neles para sobrar espaço para a resposta.
     * Outros modelos não recebem o campo (alguns rejeitam).
     */
    static String reasoningEffort(AiProperties props) {
        if (props.reasoningEffort() != null) {
            return props.reasoningEffort();
        }
        return props.model().startsWith("openai/gpt-oss") ? "low" : null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GroqChatRequest(
            String model,
            List<AiChatMessage> messages,
            Double temperature,
            @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
            @JsonProperty("reasoning_effort") String reasoningEffort
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqChatResponse(String model, List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(GroqMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens
    ) {
    }
}
