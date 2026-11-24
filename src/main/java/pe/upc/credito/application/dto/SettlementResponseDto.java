package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Settlement", description = "Monthly customer statement")
public record SettlementResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "L-000001") String code,
        @Schema(example = "1") Integer customer_id,
        @Schema(example = "Jane Doe") String customer_name,
        @Schema(example = "2026-11-25") String cutoff_date,
        @Schema(example = "2026-12-05") String due_date,
        @Schema(description = "Billed principal", example = "500.00") String principal_amount,
        @Schema(description = "Billed compensatory interest", example = "30.60") String compensatory_interest,
        @Schema(description = "Late interest as of the consultation date", example = "0.00") String penalty_amount,
        @Schema(description = "Total amount due as of the consultation date", example = "530.60") String total_due,
        @Schema(description = "PENDING | OVERDUE | PAID", example = "PENDING") String status,
        @Schema(description = "Last payment date for a paid statement", example = "2026-12-04")
        String paid_date
) {
}
