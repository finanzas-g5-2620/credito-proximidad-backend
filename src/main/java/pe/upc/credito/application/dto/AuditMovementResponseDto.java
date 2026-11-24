package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.upc.credito.domain.enums.MovementEventType;

@Schema(name = "AuditMovement", description = "Event derived from a persisted transaction")
public record AuditMovementResponseDto(

        @Schema(example = "1") long id,
        @Schema(description = "Timestamp in ISO 8601", example = "2026-11-21T15:04:05Z") String timestamp,
        @Schema(example = "SALE_CREATED") MovementEventType event_type,
        @Schema(example = "Jane Doe") String customer_name,
        @Schema(example = "350.00") String amount,
        @Schema(example = "Sale V-000001 (INSTALLMENTS)") String description,
        @Schema(example = "V-000001") String reference_code
) {
}
