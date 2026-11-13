package vivaleve.proj.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vivaleve.proj.config.CurrentUser;
import vivaleve.proj.dto.DiaryDtos.DiaryRequest;
import vivaleve.proj.dto.DiaryDtos.DiaryResponse;
import vivaleve.proj.service.DiaryService;

import java.util.List;

/**
 * Diário emocional do usuário logado (tela diario.html).
 *
 * <p>Todas as rotas exigem {@code Authorization: Bearer <token>}. O dono vem do token,
 * nunca de um userId enviado pelo cliente.</p>
 */
@RestController
@RequestMapping("/v1/diary")
@RequiredArgsConstructor
@Validated
public class DiaryController {

    private final DiaryService diaryService;

    /** POST /v1/diary — cria uma entrada e devolve 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryResponse criar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DiaryRequest request) {
        return diaryService.criar(CurrentUser.id(jwt), request);
    }

    /** GET /v1/diary?q=texto — entradas do usuário (mais recente primeiro); q é opcional. */
    @GetMapping
    public List<DiaryResponse> listar(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(name = "q", required = false) @Size(max = 100) String busca) {
        return diaryService.listar(CurrentUser.id(jwt), busca);
    }

    /** GET /v1/diary/{id} — 404 se não existir ou for de outro usuário. */
    @GetMapping("/{id}")
    public DiaryResponse buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return diaryService.buscarPorId(CurrentUser.id(jwt), id);
    }

    /** PUT /v1/diary/{id} — atualiza título, texto e emoção. */
    @PutMapping("/{id}")
    public DiaryResponse atualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody DiaryRequest request) {
        return diaryService.atualizar(CurrentUser.id(jwt), id, request);
    }

    /** DELETE /v1/diary/{id} — apaga a entrada e devolve 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        diaryService.deletar(CurrentUser.id(jwt), id);
    }
}
