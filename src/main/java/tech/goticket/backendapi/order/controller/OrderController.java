package tech.goticket.backendapi.order.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.order.Order;
import tech.goticket.backendapi.order.dto.*;
import tech.goticket.backendapi.order.service.OrderService;
import tech.goticket.backendapi.shared.config.ApiError;
import tech.goticket.backendapi.shared.config.OpenApiConfig;
import tech.goticket.backendapi.ticket.dto.TicketResponse;
import tech.goticket.backendapi.ticket.service.TicketService;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Reserva, pagamento e acompanhamento de pedidos")
public class OrderController {
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final TicketService ticketService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Cria um pedido e reserva os ingressos (exige Idempotency-Key; X-Queue-Token em alta demanda)",
            description = """
                    Ordem das verificações: (1) fila — se o evento está em alta demanda, exige `X-Queue-Token` válido \
                    (obtido em `GET /events/{eventId}/queue/position` quando `state = ADMITTED`); \
                    (2) idempotência — a mesma `Idempotency-Key` com o mesmo body devolve o pedido já criado (replay), \
                    sem nova reserva; (3) reserva com lock otimista e retentativa com backoff; \
                    (4) criação do PaymentIntent no Stripe.""")
    @ApiResponse(responseCode = "201", description = "Pedido criado (ou replay idempotente do pedido já existente), com client secret do PaymentIntent.")
    @ApiResponse(responseCode = "403", description = "Evento em alta demanda sem X-Queue-Token válido, ou usuário sem escopo CLIENT.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "Fila obrigatória", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 403, "status": "FORBIDDEN",
                             "errors": ["Este evento está com alta demanda. Entre na fila para comprar."]}"""),
                    @ExampleObject(name = "Sem escopo CLIENT", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 403, "status": "FORBIDDEN",
                             "errors": ["Acesso negado: você não tem permissão para acessar este recurso."]}""")
            }))
    @ApiResponse(responseCode = "404", description = "EventDate, lote (allotment) ou tipo de ingresso inexistente.",
            ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", description = """
            Conflito de concorrência ou de idempotência: estoque insuficiente (proteção contra overselling), \
            reserva não concluída após as retentativas, ou Idempotency-Key em uso de forma incompatível.""",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "Estoque insuficiente (overselling)", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Estoque insuficiente no lote/tipo selecionado. Disponível: 1, solicitado: 2"]}"""),
                    @ExampleObject(name = "Contenção na reserva", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Não foi possível reservar os ingressos após 6. Tente de novo em alguns segundos."]}"""),
                    @ExampleObject(name = "Idempotency-Key com body diferente", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Idempotency-Key reutilizada com body diferente. Gere uma nova chave para uma compra distinta."]}"""),
                    @ExampleObject(name = "Requisição em processamento", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Já existe uma requisição em processamento para esta Idempotency-Key. Tente novamente em instantes."]}"""),
                    @ExampleObject(name = "Idempotency-Key de outro usuário", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 409, "status": "CONFLICT",
                             "errors": ["Idempotency-Key já foi utilizada por outro usuário."]}""")
            }))
    @ApiResponse(responseCode = "502", description = "Falha na comunicação com o gateway de pagamento (Stripe).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 502, "status": "BAD_GATEWAY",
                             "errors": ["Falha ao comunicar com gateway de pagamento. Tente novamente."]}""")))
    public ResponseEntity<PlaceOrderResponse> placeOrder(
            @Parameter(description = "Chave única por intenção de compra (ex: UUID). Reenvio com o mesmo body devolve o mesmo pedido.")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Parameter(description = "Token de admissão da fila. Obrigatório apenas quando o evento está em alta demanda.")
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @Valid @RequestBody PlaceOrderRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) throws IOException {
        UUID buyerId = UUID.fromString(authentication.getName());
        String rawBody = objectMapper.writeValueAsString(request);
        PlaceOrderResponse response = orderService.placeOrder(request, buyerId, idempotencyKey, rawBody, queueToken);

        return ResponseEntity.created(URI.create("/orders/" + response.orderId()))
                .body(response);
    }

    @PostMapping("/quote")
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Calcula o valor do pedido (subtotal, taxas e total) sem reservar")
    @ApiResponse(responseCode = "200", description = "Cotação calculada.")
    @ApiResponse(responseCode = "400", description = "Body inválido, ou lote/tipo de ingresso incompatíveis com a EventDate informada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 400, "status": "BAD_REQUEST",
                             "errors": ["TicketType Meia não corresponde ao allotment 12"]}""")))
    @ApiResponse(responseCode = "404", description = "EventDate, lote (allotment) ou tipo de ingresso inexistente.",
            ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<QuoteResponse> quoteOrder(@Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.ok(orderService.quote(request));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_CLIENT', 'SCOPE_ADMIN')")
    @Operation(summary = "Detalha um pedido pelo ID")
    @ApiResponse(responseCode = "200", description = "Pedido encontrado.")
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId,
            Authentication authentication) {

        UUID requesterId = UUID.fromString(authentication.getName());
        Order order = orderService.getById(orderId, requesterId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}/tickets")
    @PreAuthorize("hasAnyAuthority('SCOPE_CLIENT', 'SCOPE_ADMIN')")
    @Operation(summary = "Lista os ingressos gerados de um pedido")
    @ApiResponse(responseCode = "200", description = "Ingressos do pedido.")
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<List<TicketResponse>> getTicketsByOrder(
            @PathVariable Long orderId,
            Authentication authentication) {

        UUID requesterId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(ticketService.findByOrderIdForUser(orderId, requesterId));
    }

    @GetMapping("/{orderId}/status")
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Retorna o status do pedido (para polling do pagamento)")
    @ApiResponse(responseCode = "200", description = "Status atual do pedido.")
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<OrderStatusDTO> getOrderStatus(@PathVariable Long orderId,
                                                         Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        OrderStatusDTO dto = orderService.getStatus(orderId, requesterId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dto);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_CLIENT', 'SCOPE_ADMIN')")
    @Operation(summary = "Lista os pedidos do cliente autenticado (ou todos, se admin)")
    public ResponseEntity<MyOrderListDTO> listOrders(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_ADMIN"));

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "placedAt"));

        MyOrderListDTO orders = isAdmin
                ? orderService.listAllOrders(pageable)
                : orderService.listMyOrders(UUID.fromString(authentication.getName()), pageable);

        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}/summary")
    @PreAuthorize("hasAnyAuthority('SCOPE_CLIENT', 'SCOPE_ADMIN')")
    @Operation(summary = "Retorna o resumo do pedido (evento, itens, valores e QR)")
    @ApiResponse(responseCode = "200", description = "Resumo do pedido.")
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<OrderSummaryResponse> getOrderSummary(@PathVariable Long orderId,
                                                                Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(orderService.getSummary(orderId, requesterId));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Cancela um pedido pendente e libera a reserva")
    @ApiResponse(responseCode = "200", description = "Pedido cancelado e reserva liberada.")
    @ApiResponse(responseCode = "403", description = "Pedido em status que não permite cancelamento, ou usuário sem escopo CLIENT.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 403, "status": "FORBIDDEN",
                             "errors": ["Order com status: PAID não pode ser cancelada."]}""")))
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId,
                                                     @Valid @RequestBody(required = false) CancelOrderRequest body,
                                                     Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        String reason = (body == null || body.reason() == null) ? "Cancelado pelo cliente" : body.reason();
        Order order = orderService.cancelByBuyer(orderId, requesterId, reason);
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
