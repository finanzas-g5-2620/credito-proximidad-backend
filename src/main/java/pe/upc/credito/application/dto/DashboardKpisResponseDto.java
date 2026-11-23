package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DashboardKpis", description = "Indicadores ejecutivos del dashboard")
public record DashboardKpisResponseDto(

        @Schema(description = "Suma de ventas registradas", example = "14850.00") String total_revenue,
        @Schema(description = "Saldo pendiente de liquidaciones vencidas", example = "828.69") String overdue_portfolio,
        @Schema(description = "Cuentas corrientes del tenant", example = "42") long active_accounts,
        @Schema(description = "Cartera vencida / cartera total, en porcentaje con 7 decimales", example = "5.5800000")
        String default_rate
) {
}
