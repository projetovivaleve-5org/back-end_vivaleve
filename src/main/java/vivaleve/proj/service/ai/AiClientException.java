package vivaleve.proj.service.ai;

import java.io.Serial;

/** Falha ao falar com o provedor de IA. O AiService trata e devolve a resposta de fallback. */
public class AiClientException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AiClientException(String message) {
        super(message);
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
