package vivaleve.proj.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vivaleve.proj.config.AiProperties;
import vivaleve.proj.service.ai.AiClient.AiChatMessage;
import vivaleve.proj.service.ai.AiClient.AiCompletion;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Testa a integração HTTP com o Groq sem rede, simulando o servidor. */
class GroqClientTest {

    private static final List<AiChatMessage> PROMPT =
            List.of(AiChatMessage.system("regras"), AiChatMessage.user("oi"));

    private MockRestServiceServer server;

    private GroqClient client(String key, String model) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://groq.test/openai/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties props = new AiProperties("https://groq.test/openai/v1", key, model, null,
                0.7, 600, null, null, null);
        return new GroqClient(builder.build(), props);
    }

    @Test
    void enviaNoFormatoOpenAiELeResposta() {
        GroqClient client = client("gsk-test", "openai/gpt-oss-20b");
        server.expect(requestTo("https://groq.test/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gsk-test"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("oi"))
                .andExpect(jsonPath("$.temperature").value(0.7))
                .andExpect(jsonPath("$.max_completion_tokens").value(600))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-1",
                          "object": "chat.completion",
                          "model": "openai/gpt-oss-20b",
                          "choices": [{"index": 0, "finish_reason": "stop",
                                       "message": {"role": "assistant", "content": "Olá! Como você está?", "reasoning": "..."}}],
                          "usage": {"prompt_tokens": 40, "completion_tokens": 12, "total_tokens": 52}
                        }
                        """, MediaType.APPLICATION_JSON));

        AiCompletion completion = client.complete(PROMPT);

        server.verify();
        assertThat(completion.content()).isEqualTo("Olá! Como você está?");
        assertThat(completion.model()).isEqualTo("openai/gpt-oss-20b");
        assertThat(completion.promptTokens()).isEqualTo(40);
        assertThat(completion.responseTokens()).isEqualTo(12);
    }

    @Test
    void modeloSemRaciocinioNaoRecebeReasoningEffort() {
        GroqClient client = client("gsk-test", "llama-3.1-8b-instant");
        server.expect(requestTo("https://groq.test/openai/v1/chat/completions"))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andRespond(withSuccess("""
                        {"model":"llama-3.1-8b-instant","choices":[{"message":{"role":"assistant","content":"oi"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete(PROMPT).content()).isEqualTo("oi");
        server.verify();
    }

    @Test
    void limiteDoPlanoGratuitoViraAiClientException() {
        GroqClient client = client("gsk-test", "openai/gpt-oss-20b");
        server.expect(requestTo("https://groq.test/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.complete(PROMPT)).isInstanceOf(AiClientException.class);
    }

    @Test
    void respostaSemChoicesViraAiClientException() {
        GroqClient client = client("gsk-test", "openai/gpt-oss-20b");
        server.expect(requestTo("https://groq.test/openai/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(PROMPT)).hasMessageContaining("vazia");
    }

    @Test
    void semChaveNaoFazChamadaHttp() {
        GroqClient client = client("", "openai/gpt-oss-20b");

        assertThatThrownBy(() -> client.complete(PROMPT)).hasMessageContaining("GROQ_API_KEY");
        server.verify();
    }
}
