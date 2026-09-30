package tech.goticket.backendapi.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.goticket.backendapi.ticket.dto.TicketResponse;
import tech.goticket.backendapi.ticket.service.TicketService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Ingressos", description = "Consulta dos ingressos do cliente")
public class TicketController {
    private final TicketService ticketService;

    @GetMapping("/{ticketId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_CLIENT', 'SCOPE_ADMIN')")
    @Operation(summary = "Detalha um ingresso pelo ID")
    public ResponseEntity<TicketResponse> getById(@PathVariable UUID ticketId,
                                                  Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ticketService.findByIdForUser(ticketId, requesterId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Lista os ingressos do cliente autenticado")
    public ResponseEntity<List<TicketResponse>> getMine(Authentication authentication) {
        UUID buyerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ticketService.findMyTickets(buyerId));
    }
}
