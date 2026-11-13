package vivaleve.proj.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vivaleve.proj.config.CurrentUser;
import vivaleve.proj.dto.AiDtos.*;
import vivaleve.proj.service.ai.AiService;

import java.util.List;

/**
 * Rotas de IA. Todas exigem o JWT da API: o usuário vem SEMPRE do token
 * ({@link CurrentUser}), nunca de um userId enviado pelo cliente — assim ninguém lê ou
 * apaga as conversas de outra pessoa.
 */
@RestController
@RequestMapping("/v1/ai")
@RequiredArgsConstructor
@Validated
public class AiController {

    private final AiService aiService;

    /** POST /v1/ai/chat — envia uma mensagem para a Pluma (sem conversationId abre conversa nova). */
    @PostMapping("/chat")
    public AiResponse chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request) {
        return aiService.chat(CurrentUser.id(jwt), request);
    }

    /** POST /v1/ai/insights/mood — leitura gentil das estatísticas de humor (7 ou 30 dias). */
    @PostMapping("/insights/mood")
    public AiResponse moodInsight(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MoodInsightRequest request) {
        return aiService.moodInsight(CurrentUser.id(jwt), request);
    }

    /** POST /v1/ai/entries/{entryId}/reflection — comentário acolhedor sobre um registro do usuário. */
    @PostMapping("/entries/{entryId}/reflection")
    public AiResponse entryReflection(@AuthenticationPrincipal Jwt jwt, @PathVariable Long entryId,
                                      @Valid @RequestBody EntryReflectionRequest request) {
        return aiService.entryReflection(CurrentUser.id(jwt), entryId, request);
    }

    /** GET /v1/ai/conversations — conversas do usuário, da mais recente para a mais antiga. */
    @GetMapping("/conversations")
    public List<ConversationSummary> listConversations(@AuthenticationPrincipal Jwt jwt) {
        return aiService.listConversations(CurrentUser.id(jwt));
    }

    /** GET /v1/ai/conversations/{id}/messages — mensagens da conversa em ordem cronológica. */
    @GetMapping("/conversations/{id}/messages")
    public List<MessageResponse> listMessages(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return aiService.listMessages(id, CurrentUser.id(jwt));
    }

    /** DELETE /v1/ai/conversations/{id} — apaga a conversa e as mensagens (204). */
    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        aiService.deleteConversation(id, CurrentUser.id(jwt));
    }
}
