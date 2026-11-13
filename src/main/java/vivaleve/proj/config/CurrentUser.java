package vivaleve.proj.config;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utilitário para descobrir QUEM está fazendo a requisição a partir do JWT.
 *
 * <p>O {@code AuthService} grava o id do usuário no claim {@code sub} (subject) do token.
 * Como o token é assinado com o {@code JWT_SECRET}, esse id não pode ser forjado:
 * se alguém alterar o {@code sub}, a assinatura deixa de bater e o Spring Security
 * rejeita a requisição com 401 antes de chegar no controller.</p>
 *
 * <p>Por isso os controllers usam este id em vez de receber {@code userId} pela URL
 * ou pelo corpo — que o cliente poderia trocar à vontade.</p>
 *
 * <p>Uso no controller:</p>
 * <pre>
 * public X metodo(@AuthenticationPrincipal Jwt jwt) {
 *     Long userId = CurrentUser.id(jwt);
 * }
 * </pre>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Devolve o id do usuário autenticado (claim "sub" do token). */
    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
