package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PayoffQuote", description = "Cotizacion de cancelacion total a una fecha de calculo")
public record PayoffQuoteResponseDto(

        @Schema(example = "1") Integer settlement_id,
        @Schema(example = "2026-12-20") String calculation_date,
        @Schema(example = "15") int days_overdue,
        @Schema(description = "Capital pendiente de amortizar", example = "500.00") String overdue_principal,
        @Schema(description = "Interes compensatorio pendiente", example = "30.60") String compensatory_interest,
        @Schema(description = "Mora pendiente: penalidad almacenada + mora devengada", example = "2.45")
        String moratorium_interest,
        @Schema(example = "533.05") String total_payoff_amount
) {
}
