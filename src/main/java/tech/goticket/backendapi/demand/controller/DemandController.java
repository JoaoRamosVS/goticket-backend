package tech.goticket.backendapi.demand.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.demand.dto.SetDemandTierRequest;
import tech.goticket.backendapi.demand.service.DemandOverrideService;

import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/demand-tier")
@RequiredArgsConstructor
@Tag(name = "Demanda", description = "Controle do tier de demanda do evento (ativação manual da fila)")
public class DemandController {

    private final DemandOverrideService demandOverrideService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Força manualmente o tier de demanda do evento (HIGH/NORMAL)")
    public ResponseEntity<Void> setTier(@PathVariable Long eventId,
                                        @Valid @RequestBody SetDemandTierRequest body,
                                        Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        demandOverrideService.override(eventId, body.tier(), body.validForMinutes(), requesterId);
        return ResponseEntity.noContent().build();
    }
}
