package vivaleve.proj.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTOs (formato do JSON) usados pelos endpoints de autenticação.
 *
 * <p>Ficam agrupados como {@code record}s dentro de uma única classe, no mesmo
 * padrão de {@link EntryDtos}. Records são imutáveis e já geram construtor,
 * getters, equals/hashCode e toString.</p>
 *
 * <p>Login com email + senha ({@link RegisterRequest}, {@link LoginRequest}) e com Google
 * ({@link GoogleLoginRequest}). Os três devolvem o mesmo {@link AuthResponse}.</p>
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Corpo de POST /v1/auth/register — cadastro com email + senha. */
    public record RegisterRequest(
            @NotBlank(message = "O email é obrigatório")
            @Email(message = "Informe um email válido")
            @Size(max = 100, message = "O email deve ter no máximo 100 caracteres")
            String email,

            @NotBlank(message = "A senha é obrigatória")
            @Size(min = 8, max = 64, message = "A senha deve ter entre 8 e 64 caracteres")
            String password,

            @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
            String name
    ) {
    }

    /** Corpo de POST /v1/auth/login — login com email + senha. */
    public record LoginRequest(
            @NotBlank(message = "O email é obrigatório")
            String email,

            @NotBlank(message = "A senha é obrigatória")
            String password
    ) {
    }

    /**
     * NOVO — Corpo de POST /v1/auth/google.
     *
     * <p>{@code credential} é o ID Token (um JWT) que o botão "Entrar com Google"
     * (Google Identity Services) entrega para o front no callback. O front só repassa
     * esse valor; quem decide se ele é válido é o back-end ({@code GoogleTokenVerifier}).</p>
     *
     * <p>Propositalmente não há campos de email/nome aqui: esses dados são lidos de dentro
     * do token assinado pelo Google, nunca de algo que o cliente possa digitar.</p>
     */
    public record GoogleLoginRequest(
            @NotBlank(message = "O credential do Google é obrigatório")
            String credential
    ) {
    }

    /**
     * Resposta do login.
     *
     * @param token     JWT da própria API (não é o token do Google) que o front deve enviar
     *                  no header {@code Authorization: Bearer <token>}
     * @param tokenType sempre "Bearer"
     * @param expiresAt instante em que o token deixa de ser válido
     * @param user      dados públicos do usuário autenticado
     */
    public record AuthResponse(
            String token,
            String tokenType,
            Instant expiresAt,
            UserResponseDto user
    ) {
    }
}
