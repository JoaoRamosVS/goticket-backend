package tech.goticket.backendapi.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Um ingresso do pedido, vinculado a um titular")
public record PlaceOrderItemRequest(
        @Schema(description = "ID do allotment (lote × tipo de ingresso) de onde o estoque será reservado. "
                + "Deve pertencer à eventDateId do pedido.", example = "12")
        @NotNull Long batchAllotmentId,

        @Schema(description = "Tipo do ingresso: 1 = FULL (inteira), 2 = HALF (meia), 3 = SOLIDARY (solidário). "
                + "Deve corresponder ao tipo do allotment.", example = "2")
        @NotNull Long ticketTypeId,

        @Schema(description = "Nome do titular impresso no ingresso.", example = "Maria da Silva")
        @NotBlank @Size(max = 200) String holderName,

        @Schema(description = "Documento do titular (ex: CPF, somente dígitos).", example = "\"12345678909\"")
        @NotBlank @Size(min = 11, max = 20) String holderDocument,

        @Schema(description = "Obrigatório para meia-entrada (ticketTypeId = 2). Valores em GET /eligibility-types: "
                + "1 = STUDENT, 2 = ELDERLY, 3 = DISABILITY, 4 = LOW_INCOME_YOUTH, 5 = TEACHER.",
                example = "1", nullable = true)
        Long eligibilityTypeId,

        @Schema(description = "Número do documento que comprova a meia-entrada. Obrigatório quando eligibilityTypeId = 1 (STUDENT).",
                example = "\"2026123456\"", nullable = true)
        @Size(max = 50) String eligibilityDocumentNumber
) { }
