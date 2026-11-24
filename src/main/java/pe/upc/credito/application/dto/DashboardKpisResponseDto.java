package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DashboardKpis", description = "Key indicators for the authenticated store")
public record DashboardKpisResponseDto(

        @Schema(description = "Total registered sales", example = "14850.00") String total_revenue,
        @Schema(description = "Persisted outstanding balance of overdue settlements", example = "828.69") String overdue_portfolio,
        @Schema(description = "Customer accounts for the store", example = "42") long active_accounts,
        @Schema(description = "Overdue principal divided by total outstanding principal, expressed as a fraction", example = "0.0558000")
        String default_rate
) {
}
