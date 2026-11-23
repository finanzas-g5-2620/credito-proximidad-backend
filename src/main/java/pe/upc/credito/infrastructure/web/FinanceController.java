package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.SimulateScheduleRequestDto;
import pe.upc.credito.application.dto.SimulateScheduleResponseDto;
import pe.upc.credito.application.service.FinanceService;

@RestController
@RequestMapping("/finance")
@Tag(name = "Finanzas", description = "Simulacion del cronograma de amortizacion frances")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simular cronograma frances",
            description = "Calcula la cuota uniforme, la capitalizacion del periodo de gracia "
                    + "(Art. 1250 C.C.) y el cronograma completo con ajuste de centavo terminal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cronograma calculado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos"),
            @ApiResponse(responseCode = "422", description = "Regla financiera incumplida")
    })
    public SimulateScheduleResponseDto simulate(@Valid @RequestBody SimulateScheduleRequestDto request) {
        return financeService.simulate(request);
    }
}
