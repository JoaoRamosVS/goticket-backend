package tech.goticket.backendapi.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.event.EventSector;
import tech.goticket.backendapi.event.dto.CreateEventSectorDTO;
import tech.goticket.backendapi.event.dto.EventSectorDTO;
import tech.goticket.backendapi.event.dto.UpdateEventSectorDTO;
import tech.goticket.backendapi.event.service.EventSectorService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/sectors")
@RequiredArgsConstructor
@Tag(name = "Setores do evento", description = "Setores de um evento (montagem da estrutura)")
public class EventSectorController {

    private final EventSectorService eventSectorService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Adiciona um setor ao evento")
    public ResponseEntity<EventSectorDTO> createEventSector(@PathVariable Long eventId,
                                                            @Valid @RequestBody CreateEventSectorDTO dto,
                                                            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        EventSector created = eventSectorService.createEventSector(
                eventId,
                dto.name(),
                dto.description(),
                dto.hasNumberedSeats(),
                dto.venueSectorId(),
                userId
        );

        return ResponseEntity
                .created(URI.create("/events/" + eventId + "/sectors/" + created.getSectorId()))
                .body(new EventSectorDTO(created));
    }

    @PatchMapping("/{sectorId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Atualiza um setor do evento")
    public ResponseEntity<EventSectorDTO> updateEventSector(@PathVariable Long eventId,
                                                            @PathVariable Long sectorId,
                                                            @RequestBody UpdateEventSectorDTO dto,
                                                            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        EventSector updated = eventSectorService.updateEventSector(
                eventId,
                sectorId,
                dto.name(),
                dto.description(),
                dto.hasNumberedSeats(),
                userId
        );

        return ResponseEntity.ok(new EventSectorDTO(updated));
    }

    @DeleteMapping("/{sectorId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Remove um setor do evento")
    public ResponseEntity<Void> deleteEventSector(@PathVariable Long eventId,
                                                  @PathVariable Long sectorId,
                                                  Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        eventSectorService.deleteEventSector(eventId, sectorId, userId);

        return ResponseEntity.noContent().build();
    }
}
