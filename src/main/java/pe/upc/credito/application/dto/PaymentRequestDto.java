package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.upc.credito.domain.enums.PaymentMethod;

@Schema(name = "PaymentRequest", description = "Registro de un cobro en ventanilla")
public record PaymentRequestDto(

        @Schema(description = "Monto recibido en soles. Debe cubrir la totalidad del saldo exigible; "
                + "si lo excede, el excedente pasa al saldo a favor del cliente",
                example = "533.05", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        String amount,

        @Schema(example = "EFECTIVO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PaymentMethod payment_method,

        @Schema(description = "Nro. de operacion (YAPE / transferencia)", example = "YAPE-889231")
        @Size(max = 50, message = "La referencia no puede exceder 50 caracteres")
        String reference_number
) {
}
