package vivaleve.proj.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Monta a resposta 401 quando o Spring Security barra a requisição ANTES do controller
 * (sem token, token adulterado ou expirado). O GlobalExceptionHandler não pega esses casos,
 * porque eles acontecem no filtro de segurança, fora dos controllers.
 *
 * <p>Devolve o mesmo formato do ErrorResponse: {"message": "...", "status": "401"}.</p>
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {

        String message = (ex instanceof InvalidBearerTokenException)
                ? "Token inválido ou expirado"
                : "Token de acesso não informado";

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"message\":\"" + message + "\",\"status\":\"401\"}");
    }
}
