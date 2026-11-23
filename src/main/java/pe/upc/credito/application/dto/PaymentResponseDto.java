package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.upc.credito.domain.enums.PaymentMethod;

@Schema(name = "Payment", description = "Recibo de pago emitido")
public record PaymentResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "P-000001") String code,
        @Schema(example = "1") Integer settlement_id,
        @Schema(example = "1") Integer customer_id,
        @Schema(description = "Importe imputado a la deuda (amount_paid en la tabla payments)",
                example = "533.05")
        String amount,
        @Schema(description = "Fecha y hora del pago ISO-8601", example = "2026-12-20T15:30:00Z")
        String payment_date,
        @Schema(example = "EFECTIVO") PaymentMethod payment_method,
        @Schema(example = "YAPE-889231") String reference_number,
        @Schema(example = "R-000001") String receipt_number,
        @Schema(description = "Importe recibido en ventanilla; puede superar la deuda exigible",
                example = "600.00")
        String received_amount,
        @Schema(description = "Saldo a favor acumulado del cliente despues del pago",
                example = "66.95")
        String favour_balance
) {
}
