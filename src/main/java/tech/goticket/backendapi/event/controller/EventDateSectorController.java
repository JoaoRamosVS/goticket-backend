package tech.goticket.backendapi.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.event.EventDateSector;
import tech.goticket.backendapi.event.dto.CreateEventDateSectorDTO;
import tech.goticket.backendapi.event.dto.EventFullDTO;
import tech.goticket.backendapi.event.service.EventDateSectorService;
import tech.goticket.backendapi.shared.config.ApiError;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/dates/{eventDateId}/sectors")
@RequiredArgsConstructor
@Tag(name = "Setores por data", description = "Vínculo entre datas e setores do evento")
public class EventDateSectorController {

    private final EventDateSectorService eventDateSectorService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Vincula um setor a uma data do evento")
    @ApiResponse(responseCode = "201", description = "Setor vinculado à data.")
    @ApiResponse(responseCode = "409", description = "O setor já está vinculado a esta data.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Este setor já está vinculado a esta data do evento."]}""")))
    public ResponseEntity<EventFullDTO.EventDateSectorFullDTO> linkSector(
            @PathVariable Long eventId,
            @PathVariable Long eventDateId,
            @Valid @RequestBody CreateEventDateSectorDTO dto,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        EventDateSector created = eventDateSectorService.link(eventId, eventDateId, dto.eventSectorId(), userId);
        return ResponseEntity
                .created(URI.create("/events/" + eventId + "/dates/" + eventDateId
                        + "/sectors/" + created.getEventDateSectorId()))
                .body(new EventFullDTO.EventDateSectorFullDTO(created));
    }

    @DeleteMapping("/{eventDateSectorId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Remove o vínculo entre setor e data do evento")
    @ApiResponse(responseCode = "204", description = "Vínculo removido.")
    @ApiResponse(responseCode = "409", description = "O setor possui ingressos vendidos nesta data.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Não é possível remover um setor com ingressos vendidos."]}""")))
    public ResponseEntity<Void> unlinkSector(
            @PathVariable Long eventId,
            @PathVariable Long eventDateId,
            @PathVariable Long eventDateSectorId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        eventDateSectorService.unlink(eventId, eventDateId, eventDateSectorId, userId);
        return ResponseEntity.noContent().build();
    }
}
