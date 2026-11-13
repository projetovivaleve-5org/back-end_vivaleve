package vivaleve.proj.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vivaleve.proj.config.CurrentUser;
import vivaleve.proj.dto.EntryDtos.EntryRequest;
import vivaleve.proj.dto.EntryDtos.EntryResponse;
import vivaleve.proj.service.EntryService;

import java.util.List;

/**
 * CRUD dos registros de humor do usuário logado.
 *
 * <p>ALTERADO: todas as rotas exigem {@code Authorization: Bearer <token>}.
 * O dono dos registros vem do token ({@code @AuthenticationPrincipal Jwt}) e não mais de
 * {@code ?userId=} ou do corpo. Assim cada usuário só enxerga e altera os próprios registros.</p>
 */
@RestController
@RequestMapping("/v1/entries")
@RequiredArgsConstructor
@Validated
public class EntryController {

    private final EntryService entryService;

    /** POST /v1/entries — cria um registro para o usuário do token e devolve 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse criar(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody EntryRequest request) {
        return entryService.criar(CurrentUser.id(jwt), request);
    }

    /** GET /v1/entries — lista os registros do usuário do token (antes: ?userId=). */
    @GetMapping
    public List<EntryResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        return entryService.listarPorUsuario(CurrentUser.id(jwt));
    }

    /** GET /v1/entries/{id} — busca um registro; 404 se não existir ou for de outro usuário. */
    @GetMapping("/{id}")
    public EntryResponse buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return entryService.buscarPorId(CurrentUser.id(jwt), id);
    }

    /** PUT /v1/entries/{id} — atualiza um registro do próprio usuário (404 se for de outro). */
    @PutMapping("/{id}")
    public EntryResponse atualizar(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable Long id,
                                   @Valid @RequestBody EntryRequest request) {
        return entryService.atualizar(CurrentUser.id(jwt), id, request);
    }

    /** DELETE /v1/entries/{id} — apaga um registro do próprio usuário e devolve 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        entryService.deletar(CurrentUser.id(jwt), id);
    }
}
