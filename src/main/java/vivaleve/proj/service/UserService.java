package vivaleve.proj.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vivaleve.proj.dto.UserResponseDto;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.UserRepository;

/**
 * Regras de negócio do perfil do usuário.
 *
 * <p>ALTERADO: {@code criarUser} (senha sem hash) e {@code listarUsers} (expunha todos os
 * usuários) foram removidos. O cadastro agora é responsabilidade do {@link AuthService},
 * feito automaticamente no primeiro login com Google.</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Devolve os dados públicos do usuário autenticado.
     * 404 se a conta foi apagada mas o token ainda não expirou.
     */
    @Transactional(readOnly = true)
    public UserResponseDto buscarPerfil(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        return toDto(user);
    }

    /**
     * NOVO — Conversão única entidade → DTO, usada aqui e no {@link AuthService}
     * (antes cada um montava o DTO do seu jeito). Inclui nome e foto do Google.
     */
    static UserResponseDto toDto(UserEntity user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .pictureUrl(user.getPictureUrl())
                .build();
    }
}
