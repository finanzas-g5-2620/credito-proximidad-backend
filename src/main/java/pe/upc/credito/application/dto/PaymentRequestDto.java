package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.upc.credito.domain.enums.PaymentMethod;

@Schema(name = "PaymentRequest", description = "Full settlement payment in PEN")
public record PaymentRequestDto(

        @Schema(description = "Tender in PEN must cover the full balance. "
                + "If it exceeds the current balance, the excess is added to the customer's favour balance.",
                example = "533.05", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        String amount,

        @Schema(example = "CASH", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PaymentMethod payment_method,

        @Schema(description = "Transaction reference for YAPE or bank transfer", example = "YAPE-889231")
        @Size(max = 50, message = "Reference cannot exceed 50 characters")
        String reference_number
) {
}
