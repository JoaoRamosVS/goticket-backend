package tech.goticket.backendapi.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Uma data/sessão do evento")
public record EventDateInputDTO(
        @Schema(description = "Início da sessão. Deve ser futura e anterior a endDate.", example = "2026-11-20T20:00:00")
        @NotNull(message = "A data de início é obrigatória.")
        @Future(message = "A data de início deve ser uma data futura.")
        LocalDateTime startDate,

        @Schema(description = "Término da sessão. Deve ser futura.", example = "2026-11-20T23:30:00")
        @NotNull(message = "A data de término é obrigatória.")
        @Future(message = "A data de término deve ser uma data futura.")
        LocalDateTime endDate
) {}
