package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "Standard error response for API clients")
public record ApiErrorResponseDto(

        @Schema(example = "CREDIT_LIMIT_EXCEEDED") String code,
        @Schema(example = "The sale exceeds the available credit limit") String message,
        @Schema(description = "Optional field validation details") Object details
) {
}
