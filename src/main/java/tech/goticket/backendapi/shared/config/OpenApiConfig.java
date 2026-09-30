package tech.goticket.backendapi.shared.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import java.util.*;

@Configuration
public class OpenApiConfig {

    // Respostas de erro reutilizáveis — referenciar via @ApiResponse(responseCode = "...", ref = OpenApiConfig.X)
    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String UNAUTHORIZED = "#/components/responses/Unauthorized";
    public static final String FORBIDDEN = "#/components/responses/Forbidden";
    public static final String NOT_FOUND = "#/components/responses/NotFound";

    private static final String API_ERROR_SCHEMA = "#/components/schemas/ApiError";

    private static final List<String> TAG_ORDER = List.of(
            "Autenticação e usuários",
            "Clientes",
            "Organizadores",
            "Administradores",
            "Eventos",
            "Categorias de evento",
            "Setores do evento",
            "Datas do evento",
            "Setores por data",
            "Lotes de ingressos",
            "Tipos de elegibilidade",
            "Locais",
            "Fila de espera",
            "Demanda",
            "Pedidos",
            "Ingressos"
    );

    @Bean
    OpenApiCustomizer orderedTags() {
        return openApi -> {
            if (openApi.getTags() == null) return;

            // Deduplica por nome, preferindo a versão com descrição (vinda do @Tag do controller)
            Map<String, Tag> byName = new LinkedHashMap<>();
            openApi.getTags().forEach(tag -> byName.merge(tag.getName(), tag,
                    (current, other) -> current.getDescription() != null ? current : other));

            List<Tag> ordered = new ArrayList<>();
            TAG_ORDER.forEach(name -> Optional.ofNullable(byName.remove(name)).ifPresent(ordered::add));
            ordered.addAll(byName.values()); // tag nova, não listada em TAG_ORDER, vai para o fim

            openApi.setTags(ordered);
        };
    }

    @Bean
    OpenAPI goTicketOpenAPI() {
        Components components = new Components()
                .addSecuritySchemes("bearer-jwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                .addResponses("BadRequest", errorResponse(HttpStatus.BAD_REQUEST,
                        "Requisição inválida: falha na validação do body ou em regra de negócio.",
                        "O campo 'email' é obrigatório."))
                .addResponses("Unauthorized", errorResponse(HttpStatus.UNAUTHORIZED,
                        "JWT ausente, inválido ou expirado.",
                        "Token de acesso inválido ou expirado."))
                .addResponses("Forbidden", errorResponse(HttpStatus.FORBIDDEN,
                        "Autenticado, mas sem o escopo exigido ou sem permissão sobre o recurso.",
                        "Acesso negado: você não tem permissão para acessar este recurso."))
                .addResponses("NotFound", errorResponse(HttpStatus.NOT_FOUND,
                        "Recurso não encontrado (ou não pertence ao usuário autenticado).",
                        "Order não encontrada: 42"));
        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info().title("GoTicket API").version("v1")
                        .description("Venda de ingressos com fila de espera e controle de concorrência"))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .components(components);
    }

    /**
     * Adiciona os erros transversais que o GlobalExceptionHandler/Spring Security produzem em qualquer endpoint,
     * sem repetir anotação em cada controller:
     * - 401/403 em métodos com @PreAuthorize;
     * - 400 em métodos com @Valid @RequestBody.
     * Respostas declaradas explicitamente com @ApiResponse têm precedência (putIfAbsent).
     */
    @Bean
    OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();

            if (isSecured(handlerMethod)) {
                responses.putIfAbsent("401", new ApiResponse().$ref(UNAUTHORIZED));
                responses.putIfAbsent("403", new ApiResponse().$ref(FORBIDDEN));
            }
            else {
                operation.setSecurity(List.of());
            }

            if (hasValidatedBody(handlerMethod)) {
                responses.putIfAbsent("400", new ApiResponse().$ref(BAD_REQUEST));
            }

            ApiResponses sorted = new ApiResponses();
            responses.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sorted.addApiResponse(e.getKey(), e.getValue()));
            sorted.setExtensions(responses.getExtensions());
            operation.setResponses(sorted);
            return operation;
        };
    }

    private static boolean isSecured(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
    }

    private static boolean hasValidatedBody(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(p -> p.hasParameterAnnotation(Valid.class) && p.hasParameterAnnotation(RequestBody.class));
    }

    private static ApiResponse errorResponse(HttpStatus status, String description, String message) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("timestamp", "24-09-2026 14:30:00");
        example.put("code", status.value());
        example.put("status", status.name());
        example.put("errors", List.of(message));

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(API_ERROR_SCHEMA)).example(example)));
    }
}
