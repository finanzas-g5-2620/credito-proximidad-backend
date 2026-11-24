package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.upc.credito.domain.enums.RateType;

@Schema(name = "SimulateScheduleRequest", description = "French amortization schedule simulation request")
public record SimulateScheduleRequestDto(

        @Schema(description = "Initial principal in PEN", example = "1500.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @DecimalMin(value = "0.01", message = "Initial principal must be greater than zero")
        String initial_capital,

        @Schema(description = "Standalone rate as a fraction of the reference period", example = "0.24")
        @DecimalMin(value = "0.0", message = "The rate cannot be negative")
        String rate_value,

        @Schema(description = "Standalone rate type", example = "NOMINAL")
        RateType rate_type,

        @Schema(description = "Days between capitalization events for a nominal rate. Seven means weekly.", example = "7")
        @Min(value = 1, message = "Capitalization days must be positive")
        @Max(value = 360, message = "Capitalization days cannot exceed 360")
        Integer compounding_days,

        @Schema(description = "Commercial days in the rate reference period", example = "360", defaultValue = "360")
        @Min(value = 1, message = "The reference period must be positive")
        @Max(value = 360, message = "The reference period cannot exceed 360 days")
        Integer rate_period_days,

        @Schema(description = "Derive the rate and billing cycle from the customer agreement", example = "1")
        @Min(value = 1, message = "Customer ID must be positive")
        Integer customer_id,

        @Schema(description = "Number of installments", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(value = 1, message = "At least one installment is required")
        @Max(value = 120, message = "At most 120 installments are allowed")
        Integer total_installments,

        @Schema(description = "Commercial grace days", example = "15", defaultValue = "0")
        @Min(value = 0, message = "Grace days cannot be negative")
        @Max(value = 365, message = "Grace days cannot exceed 365")
        Integer grace_days,

        @Schema(description = "First installment date for standalone simulations, ISO 8601. Defaults to the current business date.", example = "2026-12-05")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "The date must have format YYYY-MM-DD")
        String first_due_date
) {
}
