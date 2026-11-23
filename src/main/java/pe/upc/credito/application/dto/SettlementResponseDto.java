package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Settlement", description = "Estado de cuenta mensual de un cliente")
public record SettlementResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "L-000001") String code,
        @Schema(example = "1") Integer customer_id,
        @Schema(example = "Maria Quispe Rojas") String customer_name,
        @Schema(example = "2026-11-25") String cutoff_date,
        @Schema(example = "2026-12-05") String due_date,
        @Schema(description = "Capital facturado", example = "500.00") String principal_amount,
        @Schema(description = "Interes compensatorio facturado", example = "30.60") String compensatory_interest,
        @Schema(description = "Mora acumulada a la fecha de consulta", example = "0.00") String penalty_amount,
        @Schema(description = "Total exigible a la fecha de consulta", example = "530.60") String total_due,
        @Schema(description = "PENDING | OVERDUE | PAID", example = "PENDING") String status,
        @Schema(description = "Fecha del ultimo pago, si la liquidacion esta cancelada", example = "2026-12-04")
        String paid_date
) {
}
