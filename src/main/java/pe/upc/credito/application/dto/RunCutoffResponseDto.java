package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RunCutoffResponse", description = "Result of processing completed billing cycles")
public record RunCutoffResponseDto(

        @Schema(example = "Billing cutoff generated three statements")
        String message,

        @Schema(example = "3") int generatedCount
) {
}
