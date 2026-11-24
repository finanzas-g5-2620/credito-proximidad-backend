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
@Tag(name = "Analytics", description = "Store indicators and recent account activity")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TenantContext tenant;

    public AnalyticsController(AnalyticsService analyticsService, TenantContext tenant) {
        this.analyticsService = analyticsService;
        this.tenant = tenant;
    }

    @GetMapping("/kpis")
    @Operation(summary = "Read key indicators",
            description = "Returns sales, balances past due, accounts and the delinquency ratio.")
    public DashboardKpisResponseDto kpis() {
        return analyticsService.kpis(tenant.currentStoreId());
    }

    @GetMapping("/monthly-flows")
    @Operation(summary = "Read 12 months of sales and collections",
            description = "Lists monthly totals in chronological order using the Lima business calendar.")
    public List<MonthlyFlowResponseDto> monthlyFlows() {
        return analyticsService.monthlyFlows(tenant.currentStoreId());
    }

    @GetMapping("/movements")
    @Operation(summary = "List recent movements",
            description = "Returns up to 100 sales, billing and payment movements, newest first.")
    public List<AuditMovementResponseDto> movements() {
        return analyticsService.movements(tenant.currentStoreId());
    }
}
