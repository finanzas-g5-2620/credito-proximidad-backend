package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.upc.credito.domain.enums.MovementEventType;

@Schema(name = "AuditMovement", description = "Evento de la bitacora de auditoria")
public record AuditMovementResponseDto(

        @Schema(example = "1") long id,
        @Schema(description = "Instante ISO-8601", example = "2026-11-21T15:04:05Z") String timestamp,
        @Schema(example = "SALE_CREATED") MovementEventType event_type,
        @Schema(example = "Maria Quispe Rojas") String customer_name,
        @Schema(example = "350.00") String amount,
        @Schema(example = "Venta V-000001 (FINANCIADO_CUOTAS)") String description
) {
}
