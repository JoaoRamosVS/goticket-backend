package tech.goticket.backendapi.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.event.EventDate;
import tech.goticket.backendapi.event.dto.CreateEventDateDTO;
import tech.goticket.backendapi.event.dto.EventDateDTO;
import tech.goticket.backendapi.event.dto.UpdateEventDateDTO;
import tech.goticket.backendapi.event.service.EventDateService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/dates")
@RequiredArgsConstructor
@Tag(name = "Datas do evento", description = "Datas/sessões de um evento")
public class EventDateController {

    private final EventDateService eventDateService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Adiciona uma data ao evento")
    public ResponseEntity<Void> createEventDate(@PathVariable Long eventId,
                                                @Valid @RequestBody CreateEventDateDTO dto,
                                                Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        EventDate created = eventDateService.createEventDate(
                eventId,
                dto.startDate(),
                dto.endDate(),
                userId
        );

        return ResponseEntity
                .created(URI.create("/events/" + eventId + "/dates/" + created.getEventDateId()))
                .build();
    }

    @PatchMapping("/{eventDateId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Atualiza uma data do evento")
    public ResponseEntity<EventDateDTO> updateEventDate(@PathVariable Long eventId,
                                                        @PathVariable Long eventDateId,
                                                        @Valid @RequestBody UpdateEventDateDTO dto,
                                                        Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        EventDate updated = eventDateService.updateEventDate(
                eventId,
                eventDateId,
                dto.startDate(),
                dto.endDate(),
                userId
        );

        return ResponseEntity.ok(new EventDateDTO(updated));
    }

    @DeleteMapping("/{eventDateId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Remove uma data do evento")
    public ResponseEntity<Void> deleteEventDate(@PathVariable Long eventId,
                                                @PathVariable Long eventDateId,
                                                Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        eventDateService.deleteEventDate(eventId, eventDateId, userId);

        return ResponseEntity.noContent().build();
    }
}
