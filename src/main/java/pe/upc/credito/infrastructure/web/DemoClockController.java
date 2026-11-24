package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.AdvanceDateRequestDto;
import pe.upc.credito.application.dto.AdvanceDateResponseDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.application.support.TenantContext;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/__test/clock")
@ConditionalOnProperty(prefix = "app.demo-clock", name = "enabled", havingValue = "true")
@Tag(name = "Demo Clock", description = "Test-only business date controls enabled for demo runs")
public class DemoClockController {

    private final SimulatedClock clock;
    private final TenantContext tenant;

    public DemoClockController(SimulatedClock clock, TenantContext tenant) {
        this.clock = clock;
        this.tenant = tenant;
    }

    @PostMapping("/advance")
    @Operation(summary = "Advance the demo business date",
            description = "STORE_ADMIN only. This route exists only when app.demo-clock.enabled=true. "
                    + "Advances the configured demo date by 1 to 365 days in memory.")
    public AdvanceDateResponseDto advance(
            @Parameter(description = "Number of days to advance (1-365)")
            @Valid @RequestBody AdvanceDateRequestDto request) {
        if (!"STORE_ADMIN".equals(tenant.currentRole())) {
            throw new BusinessException("FORBIDDEN", "Only store administrators can advance the demo clock",
                    HttpStatus.FORBIDDEN);
        }
        return new AdvanceDateResponseDto(clock.advance(request.days()).toString());
    }
}
