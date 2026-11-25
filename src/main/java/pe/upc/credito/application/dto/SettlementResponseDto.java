package pe.upc.credito.application.dto;

import java.util.List;

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
        String paid_date,
        @Schema(description = "Chronological month-end purchases, available on statement detail")
        List<Purchase> purchases
) {
    public record Purchase(
            Integer sale_id,
            @Schema(example = "V-000042") String sale_code,
            @Schema(description = "Purchase date in America/Lima", example = "2026-11-25") String purchase_date,
            @Schema(example = "175.00") String principal_amount,
            @Schema(example = "0.81") String compensatory_interest,
            @Schema(description = "Principal plus compensatory interest", example = "175.81") String total_due
    ) {
    }
}
