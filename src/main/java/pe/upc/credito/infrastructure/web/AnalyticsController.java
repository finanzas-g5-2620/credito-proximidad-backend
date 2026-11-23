package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.AuditMovementResponseDto;
import pe.upc.credito.application.dto.DashboardKpisResponseDto;
import pe.upc.credito.application.dto.MonthlyFlowResponseDto;
import pe.upc.credito.application.service.AnalyticsService;
import pe.upc.credito.application.support.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analitica", description = "Indicadores ejecutivos y auditoria de cartera")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TenantContext tenant;

    public AnalyticsController(AnalyticsService analyticsService, TenantContext tenant) {
        this.analyticsService = analyticsService;
        this.tenant = tenant;
    }

    @GetMapping("/kpis")
    @Operation(summary = "Indicadores ejecutivos",
            description = "Ingresos por ventas, cartera vencida, cuentas activas y tasa de morosidad.")
    public DashboardKpisResponseDto kpis() {
        return analyticsService.kpis(tenant.currentStoreId());
    }

    @GetMapping("/monthly-flows")
    @Operation(summary = "Flujo mensual de 12 meses",
            description = "Ventas emitidas y cobros recibidos por mes, ordenados cronologicamente.")
    public List<MonthlyFlowResponseDto> monthlyFlows() {
        return analyticsService.monthlyFlows(tenant.currentStoreId());
    }

    @GetMapping("/movements")
    @Operation(summary = "Bitacora de movimientos",
            description = "Ventas, cortes y pagos recientes ordenados del mas nuevo al mas antiguo (maximo 100).")
    public List<AuditMovementResponseDto> movements() {
        return analyticsService.movements(tenant.currentStoreId());
    }
}
