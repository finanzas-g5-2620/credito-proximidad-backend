package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "Error uniforme consumido por el interceptor del frontend")
public record ApiErrorResponseDto(

        @Schema(example = "CREDIT_LIMIT_EXCEEDED") String code,
        @Schema(example = "El importe de la venta excede la linea de credito disponible") String message,
        @Schema(description = "Detalle opcional (errores de validacion por campo)") Object details
) {
}
