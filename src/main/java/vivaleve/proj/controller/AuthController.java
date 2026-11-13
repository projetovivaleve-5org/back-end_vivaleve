package vivaleve.proj.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vivaleve.proj.dto.AuthDtos.AuthResponse;
import vivaleve.proj.dto.AuthDtos.GoogleLoginRequest;
import vivaleve.proj.dto.AuthDtos.LoginRequest;
import vivaleve.proj.dto.AuthDtos.RegisterRequest;
import vivaleve.proj.service.AuthService;

/**
 * Endpoints públicos de autenticação (liberados no {@code SecurityConfig}).
 *
 * <p>Email + senha ({@code /register}, {@code /login}) ou conta Google ({@code /google}).
 * Todos devolvem um {@link AuthResponse} com o JWT da API que o front deve mandar
 * nas próximas requisições:</p>
 *
 * <pre>Authorization: Bearer &lt;token&gt;</pre>
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    /**
     * POST /v1/auth/register — cadastro com email + senha. Já devolve o JWT (o usuário entra direto).
     *
     * <p>Corpo: {@code { "email": "...", "password": "...", "name": "..." }} (name é opcional)</p>
     * <p>Respostas: 201 (ok), 400 (email inválido ou senha fora de 8 a 64 caracteres),
     * 409 (email já cadastrado).</p>
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * POST /v1/auth/login — login com email + senha.
     *
     * <p>Corpo: {@code { "email": "...", "password": "..." }}</p>
     * <p>Respostas: 200 (ok), 400 (campo vazio), 401 (email ou senha inválidos — também para
     * contas que só entram pelo Google).</p>
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * POST /v1/auth/google — troca o ID Token do Google por um JWT da API.
     *
     * <p>Serve tanto para login quanto para cadastro: se for o primeiro acesso dessa conta
     * Google, o usuário é criado automaticamente.</p>
     *
     * <p>Corpo: {@code { "credential": "<ID Token recebido do botão Entrar com Google>" }}</p>
     * <p>Respostas: 200 (ok), 400 (credential vazio), 401 (token do Google inválido ou email
     * não verificado), 409 (email já vinculado a outra conta Google), 503 (Google indisponível).</p>
     */
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.credential());
    }
}
