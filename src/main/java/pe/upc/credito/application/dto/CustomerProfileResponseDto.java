package pe.upc.credito.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Full customer account profile used by store administration screens. */
public record CustomerProfileResponseDto(
        Integer id,
        Integer store_id,
        Integer user_id,
        String username,
        String email,
        String full_name,
        String home_address,
        String phone_number,
        BigDecimal credit_limit,
        Integer max_credit_months,
        Integer cutoff_day,
        Integer payment_due_day,
        String compensatory_rate_type,
        Integer compensatory_rate_period_days,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer compensatory_capitalization_days,
        BigDecimal compensatory_rate_value,
        String penalty_rate_type,
        Integer penalty_rate_period_days,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer penalty_capitalization_days,
        BigDecimal penalty_rate_value,
        String credit_currency,
        BigDecimal favour_balance) {
}
