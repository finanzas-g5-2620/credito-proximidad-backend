package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MonthlyFlow", description = "Monthly sales and collections")
public record MonthlyFlowResponseDto(

        @Schema(description = "Period in YYYY-MM format", example = "2026-11") String month,
        @Schema(example = "350.00") String sales_amount,
        @Schema(example = "181.60") String collected_amount
) {
}
