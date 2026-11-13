package vivaleve.proj.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Usuário da aplicação.
 *
 * <p>Pode entrar de duas formas: email + senha ({@code password}) e/ou conta Google
 * ({@code googleSub}). Uma conta pode ter só uma delas ou as duas.</p>
 */
@Table(name = "usuarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class UserEntity extends BaseEntity {

    /**
     * Email do usuário (sempre em minúsculas). Único: é o login do email + senha e também
     * é usado para vincular uma conta de email + senha no primeiro login Google.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Hash BCrypt da senha (NUNCA a senha em texto). Nulo em contas que só entram pelo Google.
     *
     * <p>É apagado quando a conta é vinculada a um login Google: a API não confirma o email no
     * cadastro, então quem cadastrou a senha pode não ser o dono do email (ver AuthService).</p>
     */
    @Column(name = "password", length = 100)
    private String password;

    /**
     * Identificador permanente da conta Google (claim {@code sub} do ID Token).
     *
     * <p>No login Google é ele que identifica o usuário, e não o email: o email de uma conta Google
     * pode mudar, o {@code sub} nunca muda. Nulo em contas que só entram com email + senha.</p>
     */
    @Column(name = "google_sub", unique = true, length = 255)
    private String googleSub;

    /** Nome do usuário: informado no cadastro ou o da conta Google (atualizado a cada login Google). */
    @Column(length = 100)
    private String name;

    /** URL da foto de perfil do Google (claim {@code picture}). Atualizada a cada login Google. */
    @Column(name = "picture_url", length = 512)
    private String pictureUrl;
}
