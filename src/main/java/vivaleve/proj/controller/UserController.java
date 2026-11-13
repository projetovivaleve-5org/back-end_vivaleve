package vivaleve.proj.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vivaleve.proj.dto.UserDto;
import vivaleve.proj.model.UserEntity;
import vivaleve.proj.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void criarUser(@Valid @RequestBody UserDto userDto) {
        userService.criarUser(userDto);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<List<UserEntity>> listarUsuarios() {
        List<UserEntity> usuarios = userService.listarUsers();
        return ResponseEntity.ok(usuarios);
    }

}
