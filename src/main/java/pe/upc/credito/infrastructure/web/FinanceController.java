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
@Tag(name = "Finance", description = "Calculate installment schedules and normalize interest rates")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate a French amortization schedule",
            description = "Returns the regular installment, grace interest and the schedule with "
                    + "a final installment that absorbs any rounding difference.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schedule calculated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "The requested terms violate a financial rule")
    })
    public SimulateScheduleResponseDto simulate(@Valid @RequestBody SimulateScheduleRequestDto request) {
        return financeService.simulate(request);
    }
}
