package vivaleve.proj.model.enums;

/** Situação de cada mensagem guardada (PL-25) e de cada resposta devolvida ao front (PL-24). */
public enum AiInteractionStatus {
    /** Mensagem do usuário recebida. */
    RECEIVED,
    /** Resposta gerada pelo modelo normalmente. */
    SUCCESS,
    /** O modelo falhou (timeout, chave ausente, erro HTTP, resposta vazia) e foi usada a resposta padrão. */
    FALLBACK,
    /** Risco detectado na mensagem: resposta fixa de segurança com o CVV, sem chamar o modelo. */
    CRISIS
}
