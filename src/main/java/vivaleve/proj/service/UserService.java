package vivaleve.proj.service;

import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vivaleve.proj.dto.UserDto;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void criarUser(UserDto userDto) {
        UserEntity user = userRepository.findByEmail(userDto.getEmail())
                .orElse(null);

        if (user != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Usuário já cadastrado com este email"
            );
        }

        userRepository.save(UserEntity.builder()
                .email(userDto.getEmail())
                .password(userDto.getPassword())
                .build());
    }

    public List<UserEntity> listarUsers() {
        return userRepository.findAll();
    }
}