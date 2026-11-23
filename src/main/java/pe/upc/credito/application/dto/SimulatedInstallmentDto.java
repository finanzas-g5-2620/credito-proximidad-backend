package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SimulatedInstallment", description = "Cuota simulada del cronograma frances")
public record SimulatedInstallmentDto(

        @Schema(description = "Numero de cuota; 0 = periodo de gracia", example = "1")
        int installment_number,

        @Schema(description = "Fecha de vencimiento", example = "2026-12-05")
        String due_date,

        @Schema(description = "Cuota fija R", example = "525.52")
        String payment_amount,

        @Schema(description = "Amortizacion de capital A", example = "494.92")
        String principal_amount,

        @Schema(description = "Interes compensatorio I", example = "30.60")
        String interest_amount,

        @Schema(description = "Saldo de capital S", example = "1020.15")
        String balance_amount,

        @Schema(description = "true si la fila corresponde al periodo de gracia", example = "false")
        boolean is_grace_period
) {
}
