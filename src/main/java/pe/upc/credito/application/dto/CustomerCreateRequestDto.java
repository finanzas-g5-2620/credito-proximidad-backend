package pe.upc.credito.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.upc.credito.domain.enums.RateType;

import java.math.BigDecimal;

/** Atomically creates a customer login and its credit profile for the current store. */
public record CustomerCreateRequestDto(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^\\s*[A-Za-z0-9._-]{3,50}\\s*$", message = "Username must be 3-50 letters, digits, dots, underscores or hyphens")
        @Size(max = 50) String username,
        @NotBlank(message = "Password is required") @Size(min = 6, max = 72) String password,
        @NotBlank(message = "First name is required") @Size(max = 100) String first_name,
        @NotBlank(message = "Last name is required") @Size(max = 100) String last_name,
        @NotBlank(message = "Email is required") @Email @Size(max = 100) String email,
        @NotBlank(message = "Home address is required") @Size(max = 150) String home_address,
        @NotBlank(message = "Phone number is required") @Size(max = 20) String phone_number,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal credit_limit,
        @NotNull @Min(1) @Max(36) Integer max_credit_months,
        @NotNull @Min(1) @Max(30) Integer cutoff_day,
        @NotNull @Min(1) @Max(30) Integer payment_due_day,
        @NotNull RateType compensatory_rate_type,
        @NotNull @Min(1) @Max(360) Integer compensatory_rate_period_days,
        @DecimalMin("0.0000000") @Digits(integer = 5, fraction = 7) @NotNull BigDecimal compensatory_rate_value,
        Integer compensatory_capitalization_days,
        @NotNull RateType penalty_rate_type,
        @NotNull @Min(1) @Max(360) Integer penalty_rate_period_days,
        @DecimalMin("0.0000000") @Digits(integer = 5, fraction = 7) @NotNull BigDecimal penalty_rate_value,
        Integer penalty_capitalization_days,
        @NotBlank @Pattern(regexp = "^PEN$", message = "Only PEN currency is supported") String credit_currency) {
}
