package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.upc.credito.domain.enums.PaymentMethod;

@Schema(name = "Payment", description = "Issued payment receipt")
public record PaymentResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "P-000001") String code,
        @Schema(example = "1") Integer settlement_id,
        @Schema(example = "1") Integer customer_id,
        @Schema(description = "Amount allocated to the debt and stored in payments.amount_paid",
                example = "533.05")
        String amount,
        @Schema(description = "Payment timestamp in ISO 8601", example = "2026-12-20T15:30:00Z")
        String payment_date,
        @Schema(example = "CASH") PaymentMethod payment_method,
        @Schema(example = "YAPE-889231") String reference_number,
        @Schema(example = "R-000001") String receipt_number,
        @Schema(description = "Tender received, including any excess over the amount due",
                example = "600.00")
        String received_amount,
        @Schema(description = "Customer credit balance after the payment",
                example = "66.95")
        String favour_balance,
        @Schema(example = "3.69") String allocated_to_penalty,
        @Schema(example = "25.00") String allocated_to_interest,
        @Schema(example = "800.00") String allocated_to_principal
) {
}
