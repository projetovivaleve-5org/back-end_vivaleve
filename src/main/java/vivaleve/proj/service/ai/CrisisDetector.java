package vivaleve.proj.service.ai;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;

/**
 * Detecção determinística de risco (PL-22, regra de segurança).
 * Roda ANTES de chamar a IA: se bater, o usuário recebe na hora a resposta fixa com o CVV,
 * sem depender do modelo (que pode falhar, demorar ou responder mal).
 * É uma rede de segurança simples por palavras-chave, não um classificador clínico.
 */
@Component
public class CrisisDetector {

    /** Expressões já normalizadas (minúsculas, sem acento). */
    private static final List<String> EXPRESSOES = List.of(
            "suicid",
            "me matar",
            "matar a mim",
            "tirar minha vida",
            "tirar a minha vida",
            "acabar com minha vida",
            "acabar com a minha vida",
            "acabar com tudo",
            "nao quero mais viver",
            "nao quero viver",
            "quero morrer",
            "queria morrer",
            "vontade de morrer",
            "melhor se eu morresse",
            "melhor sem mim",
            "nao aguento mais viver",
            "sem motivo para viver",
            "sem razao para viver",
            "me cortar",
            "me machucar",
            "automutil",
            "auto mutil",
            "me ferir"
    );

    public boolean isCrisis(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalize(message);
        return EXPRESSOES.stream().anyMatch(normalized::contains);
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }
}
