package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.upc.credito.domain.enums.PaymentMode;

import java.util.List;

@Schema(name = "Sale", description = "Confirmed sale")
public record SaleResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(description = "Sale reference derived from its identifier", example = "V-000001") String code,
        @Schema(example = "1") Integer customer_id,
        @Schema(example = "Jane Doe") String customer_name,
        @Schema(example = "1") Integer store_id,
        @Schema(example = "INSTALLMENTS") PaymentMode payment_mode,
        @Schema(example = "350.00") String total_amount,
        @Schema(description = "COMPLETED | PENDING | CANCELLED | VOIDED", example = "PENDING") String status,
        @Schema(example = "3") Integer installments_count,
        @Schema(example = "0") Integer grace_days,
        @Schema(description = "TEA pactada con 7 decimales", example = "0.1800000") String tea,
        @Schema(description = "Issue timestamp in ISO 8601", example = "2026-11-21T15:04:05Z") String issue_date,
        List<SaleDetailResponseDto> details
) {
}
