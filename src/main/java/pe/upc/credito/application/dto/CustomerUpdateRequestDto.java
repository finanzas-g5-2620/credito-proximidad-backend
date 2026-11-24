package pe.upc.credito.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.upc.credito.domain.enums.RateType;

import java.math.BigDecimal;

/** Mutable customer contact and credit terms. Tenant, linked user and favour balance are server-owned. */
public record CustomerUpdateRequestDto(
        @NotBlank(message = "Home address is required") @Size(max = 150) String home_address,
        @NotBlank(message = "Phone number is required") @Size(max = 20) String phone_number,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal credit_limit,
        @NotNull Integer max_credit_months,
        @NotNull Integer cutoff_day,
        @NotNull Integer payment_due_day,
        @NotNull RateType compensatory_rate_type,
        @NotNull Integer compensatory_rate_period_days,
        @NotNull @DecimalMin("0.0000000") @Digits(integer = 5, fraction = 7) BigDecimal compensatory_rate_value,
        Integer compensatory_capitalization_days,
        @NotNull RateType penalty_rate_type,
        @NotNull Integer penalty_rate_period_days,
        @NotNull @DecimalMin("0.0000000") @Digits(integer = 5, fraction = 7) BigDecimal penalty_rate_value,
        Integer penalty_capitalization_days,
        @NotBlank String credit_currency) {
}
