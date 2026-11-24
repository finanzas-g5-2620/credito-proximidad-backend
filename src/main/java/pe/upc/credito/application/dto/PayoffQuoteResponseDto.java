package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PayoffQuote", description = "Quote for paying a settlement in full on a specified date")
public record PayoffQuoteResponseDto(

        @Schema(example = "1") Integer settlement_id,
        @Schema(example = "2026-12-20") String calculation_date,
        @Schema(example = "15") int days_overdue,
        @Schema(description = "Overdue outstanding principal", example = "500.00") String overdue_principal,
        @Schema(description = "Outstanding compensatory interest", example = "30.60") String compensatory_interest,
        @Schema(description = "Late interest due as of the quote date", example = "2.45")
        String moratorium_interest,
        @Schema(example = "533.05") String total_payoff_amount
) {
}
