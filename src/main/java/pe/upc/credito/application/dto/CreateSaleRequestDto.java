package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.upc.credito.domain.enums.PaymentMode;

import java.util.List;

@Schema(name = "CreateSaleRequest", description = "Registers a sale for one or more items")
public record CreateSaleRequestDto(

        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Integer customer_id,

        @Schema(example = "INSTALLMENTS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PaymentMode payment_mode,

        @Schema(description = "Number of installments (required for INSTALLMENTS)", example = "3")
        @Min(value = 1, message = "At least one installment is required")
        Integer installments_count,

        @Schema(description = "Grace days for an installment plan", example = "0", defaultValue = "0")
        @Min(value = 0, message = "Grace days cannot be negative")
        Integer grace_days,

        @Schema(description = "Optional quote TEA. The backend still applies the customer's current contract.", example = "0.1800000")
        String tea,

        @Schema(description = "Total shown in the quote; registration fails if the catalog price changed", example = "350.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "expected_total is required")
        String expected_total,

        @Schema(description = "Quoted first installment due date; required for INSTALLMENTS", example = "2026-12-10")
        String first_due_date,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "At least one sale item is required")
        List<@Valid CreateSaleDetailRequestDto> details
) {
}
