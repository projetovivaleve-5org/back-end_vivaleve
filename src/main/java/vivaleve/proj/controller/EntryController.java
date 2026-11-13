package vivaleve.proj.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vivaleve.proj.dto.EntryDtos.EntryRequest;
import vivaleve.proj.dto.EntryDtos.EntryResponse;
import vivaleve.proj.service.EntryService;

import java.util.List;

@RestController
@RequestMapping("/v1/entries")
@RequiredArgsConstructor
@Validated
public class EntryController {

    private final EntryService entryService;

    /** POST /v1/entries — cria um novo registro de humor e devolve 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse criar(@Valid @RequestBody EntryRequest request) {
        return entryService.criar(request);
    }

    /** GET /v1/entries?userId=1 — lista os registros do usuário, do mais recente para o mais antigo. */
    @GetMapping
    public List<EntryResponse> listar(@RequestParam Long userId) {
        return entryService.listarPorUsuario(userId);
    }

    /** GET /v1/entries/{id} — busca um registro pelo id (404 se não existir). */
    @GetMapping("/{id}")
    public EntryResponse buscar(@PathVariable Long id) {
        return entryService.buscarPorId(id);
    }

    /** PUT /v1/entries/{id} — atualiza humor, atividades, nota e data de um registro. */
    @PutMapping("/{id}")
    public EntryResponse atualizar(@PathVariable Long id, @Valid @RequestBody EntryRequest request) {
        return entryService.atualizar(id, request);
    }

    /** DELETE /v1/entries/{id} — apaga um registro e devolve 204. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable Long id) {
        entryService.deletar(id);
    }
}
