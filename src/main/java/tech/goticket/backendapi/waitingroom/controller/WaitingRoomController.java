package tech.goticket.backendapi.waitingroom.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.waitingroom.dto.QueueStatusResponse;
import tech.goticket.backendapi.waitingroom.service.WaitingRoomService;

import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/queue")
@RequiredArgsConstructor
@Tag(name = "Fila de espera", description = "Sala de espera virtual para eventos de alta demanda")
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Entra na fila de espera do evento")
    public ResponseEntity<QueueStatusResponse> enterQueue(@PathVariable Long eventId,
                                                          Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(waitingRoomService.enqueue(eventId, userId));
    }

    @GetMapping("/position")
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Consulta a posição na fila (polling até ser admitido)")
    public ResponseEntity<QueueStatusResponse> position(
            @PathVariable Long eventId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        QueueStatusResponse status = waitingRoomService.getStatus(eventId, userId);

        if (status == null) {
            status = waitingRoomService.enqueue(eventId, userId);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(status);
    }
}
