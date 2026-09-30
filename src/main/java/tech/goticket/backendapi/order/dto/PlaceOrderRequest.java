package tech.goticket.backendapi.order.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Pedido de compra: uma data do evento e os ingressos desejados (um item por ingresso/titular)")
public record PlaceOrderRequest(
        @Schema(description = "ID da data (sessão) do evento. Todos os itens devem pertencer a esta data.", example = "7")
        @NotNull Long eventDateId,

        @ArraySchema(arraySchema = @Schema(description = "Ingressos do pedido. Cada item gera um ingresso nominal."),
                maxItems = 10)
        @NotEmpty @Size(max = 10) @Valid List<PlaceOrderItemRequest> items
) { }
