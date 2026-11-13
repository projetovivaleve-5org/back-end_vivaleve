package vivaleve.proj.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vivaleve.proj.config.CurrentUser;
import vivaleve.proj.dto.UserResponseDto;
import vivaleve.proj.service.UserService;

/**
 * Dados do usuário logado.
 *
 * <p>ALTERADO — rotas removidas:</p>
 * <ul>
 *   <li>{@code POST /v1/users}: salvava a senha em texto puro e duplicava o cadastro.
 *       O cadastro agora acontece no primeiro login com Google ({@code POST /v1/auth/google}).</li>
 *   <li>{@code GET /v1/users}: devolvia TODOS os usuários com a entidade inteira,
 *       incluindo o hash da senha.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /v1/users/me — devolve id e email do dono do token (nunca a senha). */
    @GetMapping("/me")
    public UserResponseDto me(@AuthenticationPrincipal Jwt jwt) {
        return userService.buscarPerfil(CurrentUser.id(jwt));
    }
}
