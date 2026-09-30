package tech.goticket.backendapi.ticket.controller;

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
import tech.goticket.backendapi.event.dto.EventFullDTO;
import tech.goticket.backendapi.ticket.TicketBatch;
import tech.goticket.backendapi.ticket.dto.CreateTicketBatchDTO;
import tech.goticket.backendapi.ticket.service.TicketBatchService;
import tech.goticket.backendapi.shared.config.ApiError;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Lotes de ingressos", description = "Lotes e preços por setor/data do evento")
public class TicketBatchController {

    private final TicketBatchService ticketBatchService;

    @PostMapping("/events/{eventId}/date-sectors/{eventDateSectorId}/batches")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Cria um lote de ingressos para um setor/data")
    public ResponseEntity<EventFullDTO.TicketBatchFullDTO> createBatch(
            @PathVariable Long eventId,
            @PathVariable Long eventDateSectorId,
            @Valid @RequestBody CreateTicketBatchDTO dto,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        TicketBatch created = ticketBatchService.createBatch(eventId, eventDateSectorId, dto, userId);
        return ResponseEntity
                .created(URI.create("/events/" + eventId + "/batches/" + created.getBatchId()))
                .body(new EventFullDTO.TicketBatchFullDTO(created));
    }

    @DeleteMapping("/events/{eventId}/batches/{batchId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Remove um lote de ingressos")
    @ApiResponse(responseCode = "204", description = "Lote removido.")
    @ApiResponse(responseCode = "409", description = "O lote possui ingressos vendidos.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Não é possível remover um lote com ingressos vendidos."]}""")))
    public ResponseEntity<Void> deleteBatch(
            @PathVariable Long eventId,
            @PathVariable Long batchId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        ticketBatchService.deleteBatch(eventId, batchId, userId);
        return ResponseEntity.noContent().build();
    }
}
