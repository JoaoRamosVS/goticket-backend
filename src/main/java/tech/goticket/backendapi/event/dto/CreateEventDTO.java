package tech.goticket.backendapi.event.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Criação de evento. Criado como PRIVATE; status inicial PENDING_APPROVAL (organizador) ou APPROVED (admin).")
public record CreateEventDTO(
        @Schema(description = "Título exibido na vitrine.", example = "Festival de Inverno 2026")
        @NotBlank(message = "O título do evento é um campo obrigatório.")
        String title,

        @Schema(description = "Descrição completa do evento.",
                example = "Três dias de shows ao ar livre com artistas nacionais.")
        @NotBlank(message = "A descrição do evento é um campo obrigatório.")
        String description,

        @Schema(description = "ID da categoria (ver GET /event-categories).", example = "3")
        @NotNull(message = "O ID da categoria é um campo obrigatório.")
        Long categoryId,

        @Schema(description = "ID do local (venue) onde o evento acontece.", example = "5")
        @NotNull(message = "O ID do espaço é um campo obrigatório.")
        Long venueId,

        @Schema(description = "Idade mínima para entrada, em anos.", example = "18", minimum = "0")
        @NotNull(message = "A restrição de idade é um campo obrigatório.")
        Integer ageRestriction,

        @Schema(description = "Início das vendas. Opcional.", example = "2026-10-01T10:00:00", nullable = true)
        LocalDateTime salesStartDate,

        @ArraySchema(arraySchema = @Schema(description = "Datas (sessões) do evento."), minItems = 1)
        @NotEmpty(message = "Pelo menos uma data deve ser informada para o evento.")
        @Valid
        List<EventDateInputDTO> eventDates,

        @Schema(description = "Organizador dono do evento. Obrigatório quando quem cria é ADMIN; "
                + "ignorado para ORGANIZER (usa o usuário autenticado).",
                example = "3f2b8c1e-9a4d-4e7b-8c21-5d6f7a8b9c0d", nullable = true)
        UUID organizerId
    ) {}
