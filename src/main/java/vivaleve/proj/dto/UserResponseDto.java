package vivaleve.proj.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Dados públicos do usuário que saem no JSON (login e GET /v1/users/me).
 *
 * <p>ALTERADO (OAuth2 Google): passou a incluir nome e foto vindos da conta Google.</p>
 */
@Getter
@Builder
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String email;

    /** NOVO — Nome da conta Google. */
    private String name;

    /** NOVO — URL da foto de perfil da conta Google (pode ser null). */
    private String pictureUrl;
}
