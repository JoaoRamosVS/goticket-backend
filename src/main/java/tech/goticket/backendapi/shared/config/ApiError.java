package tech.goticket.backendapi.shared.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Schema(description = "Payload padrão de erro da API")
public record ApiError(
        @JsonFormat(pattern = "dd-MM-yyyy HH:mm:ss")
        @Schema(type = "string", pattern = "dd-MM-yyyy HH:mm:ss", example = "24-09-2026 14:30:00")
        LocalDateTime timestamp,

        @Schema(description = "Código HTTP", example = "409")
        Integer code,

        @Schema(description = "Nome do status HTTP", example = "CONFLICT")
        String status,

        @Schema(description = "Mensagens de erro legíveis")
        List<String> errors
) {
}
