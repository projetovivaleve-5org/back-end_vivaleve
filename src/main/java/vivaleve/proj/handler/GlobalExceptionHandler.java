package vivaleve.proj.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vivaleve.proj.exceptions.BadRequestExceptions;
import vivaleve.proj.exceptions.ErrorResponse;
import vivaleve.proj.exceptions.NotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestExceptions.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestExceptions ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // Tratamento das validações do DTO
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        // Erros do próprio Spring que já têm status (ResponseStatusException 404/401,
        // rota inexistente 404, método não permitido 405...) mantêm o status original.
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            String detail = springError.getBody().getDetail();
            return build(springError.getStatusCode(), detail != null ? detail : ex.getMessage());
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatusCode status, String message) {
        ErrorResponse response = ErrorResponse.builder()
                .message(message)
                .status(String.valueOf(status.value()))
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
