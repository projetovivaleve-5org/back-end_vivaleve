package vivaleve.proj.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vivaleve.proj.config.CurrentUser;
import vivaleve.proj.dto.MoodLogDto;
import vivaleve.proj.model.enums.MoodType;
import vivaleve.proj.service.MoodLogService;

import java.util.List;

@RestController
@RequestMapping("/v1/mood-logs")
@RequiredArgsConstructor
public class MoodLogController {

    private final MoodLogService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MoodLogDto.MoodLogResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @NotNull MoodType mood,
            @RequestParam(required = false) @Size(max = 500) String note,
            @RequestParam(required = false) MultipartFile image) {

        var response = service.create(CurrentUser.id(jwt) , new MoodLogDto.MoodLogRequest(mood, note), image);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/today")
    public List<MoodLogDto.MoodLogResponse> today(
            @AuthenticationPrincipal Jwt jwt) {

        return service.getToday(CurrentUser.id(jwt));
    }

    @GetMapping
    public Page<MoodLogDto.MoodLogResponse> history(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return service.history(CurrentUser.id(jwt), pageable);
    }

    @GetMapping("/all")
    public List<MoodLogDto.MoodLogResponse> listAll(@AuthenticationPrincipal Jwt jwt) {
        return service.listAll(CurrentUser.id(jwt));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MoodLogDto.MoodLogResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam @NotNull MoodType mood,
            @RequestParam(required = false) @Size(max = 500) String note,
            @RequestParam(required = false) MultipartFile image) {

        var response = service.update(
                CurrentUser.id(jwt),
                id,
                new MoodLogDto.MoodLogRequest(mood, note),
                image
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }
}

