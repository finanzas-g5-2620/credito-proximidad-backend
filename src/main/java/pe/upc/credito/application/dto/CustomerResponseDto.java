package pe.upc.credito.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CustomerCreditInfo", description = "Customer credit line and current debt")
public record CustomerResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "Jane Doe") String full_name,
        @Schema(description = "Synthetic display document number derived from the customer ID", example = "00000001")
        String document_number,
        @Schema(example = "1500.00") String credit_limit,
        @Schema(description = "Outstanding capital in unpaid settlements and unbilled purchases", example = "0.00")
        String current_debt,
        @Schema(description = "False when the customer has an overdue unpaid settlement")
        boolean allows_monthly_payments,
        @Schema(description = "Customer credit balance from payments above the amount due",
                example = "0.00") String favour_balance,
        Integer max_credit_months,
        String compensatory_rate_type,
        String compensatory_rate_value,
        Integer compensatory_rate_period_days,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer compensatory_capitalization_days
) {
}
