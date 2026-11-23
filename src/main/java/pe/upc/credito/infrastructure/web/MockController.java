package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.AdvanceDateRequestDto;
import pe.upc.credito.application.dto.AdvanceDateResponseDto;
import pe.upc.credito.application.dto.RunCutoffResponseDto;
import pe.upc.credito.application.service.MockService;
import pe.upc.credito.application.support.TenantContext;

@RestController
@RequestMapping("/__mock")
@Tag(name = "Simulacion", description = "Reloj simulado y corte mensual para la demostracion")
public class MockController {

    private final MockService mockService;
    private final TenantContext tenant;

    public MockController(MockService mockService, TenantContext tenant) {
        this.mockService = mockService;
        this.tenant = tenant;
    }

    @PostMapping("/run-cutoff")
    @Operation(summary = "Ejecutar el corte mensual",
            description = "Emite un estado de cuenta por cliente con las cuotas que vencen el mes "
                    + "siguiente. Es idempotente por periodo: volver a ejecutarlo no duplica liquidaciones.")
    public RunCutoffResponseDto runCutoff() {
        return mockService.runCutoff(tenant.currentStoreId());
    }

    @PostMapping("/advance-date")
    @Operation(summary = "Adelantar la fecha del sistema",
            description = "Desplaza el reloj simulado en memoria para provocar vencimientos y mora.")
    public AdvanceDateResponseDto advanceDate(@Valid @RequestBody AdvanceDateRequestDto request) {
        return mockService.advanceDate(request.days());
    }
}
