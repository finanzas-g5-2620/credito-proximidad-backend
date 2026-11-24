package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SimulatedInstallment", description = "A projected installment or grace row")
public record SimulatedInstallmentDto(

        @Schema(description = "Installment number. Zero denotes the grace period.", example = "1")
        int installment_number,

        @Schema(description = "Due date", example = "2026-12-05")
        String due_date,

        @Schema(description = "Payment amount", example = "525.56")
        String payment_amount,

        @Schema(description = "Principal repaid", example = "494.96")
        String principal_amount,

        @Schema(description = "Compensatory interest", example = "30.60")
        String interest_amount,

        @Schema(description = "Remaining principal", example = "1020.11")
        String balance_amount,

        @Schema(description = "Whether this row represents the grace period", example = "false")
        boolean is_grace_period
) {
}
