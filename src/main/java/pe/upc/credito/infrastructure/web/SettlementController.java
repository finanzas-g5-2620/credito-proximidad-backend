package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.PaymentRequestDto;
import pe.upc.credito.application.dto.PaymentResponseDto;
import pe.upc.credito.application.dto.PayoffQuoteResponseDto;
import pe.upc.credito.application.dto.SettlementResponseDto;
import pe.upc.credito.application.service.SettlementService;
import pe.upc.credito.application.support.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/settlements")
@Tag(name = "Liquidaciones", description = "Estados de cuenta mensuales, cotizacion de cancelacion y cobros")
public class SettlementController {

    private final SettlementService settlementService;
    private final TenantContext tenant;

    public SettlementController(SettlementService settlementService, TenantContext tenant) {
        this.settlementService = settlementService;
        this.tenant = tenant;
    }

    @GetMapping
    @Operation(summary = "Listar liquidaciones de la tienda",
            description = "Estados de cuenta ordenados por fecha de corte descendente. El estado "
                    + "se calcula a la fecha actual del sistema (PENDING, OVERDUE o PAID).")
    public List<SettlementResponseDto> list() {
        return settlementService.list(tenant.currentStoreId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una liquidacion por identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liquidacion encontrada"),
            @ApiResponse(responseCode = "404", description = "La liquidacion no existe en la tienda")
    })
    public SettlementResponseDto get(@PathVariable Integer id) {
        return settlementService.get(id, tenant.currentStoreId());
    }

    @GetMapping("/{id}/payoff-quote")
    @Operation(summary = "Cotizar la cancelacion total",
            description = "Calcula capital pendiente, interes compensatorio y mora a la fecha de "
                    + "calculo. Si no se envia target_date se usa la fecha actual del sistema.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cotizacion calculada"),
            @ApiResponse(responseCode = "404", description = "La liquidacion no existe en la tienda"),
            @ApiResponse(responseCode = "409", description = "La liquidacion ya fue cancelada")
    })
    public PayoffQuoteResponseDto payoffQuote(
            @PathVariable Integer id,
            @Parameter(description = "Fecha de calculo en formato yyyy-MM-dd", example = "2026-12-20")
            @RequestParam(name = "target_date", required = false) String targetDate) {
        return settlementService.payoffQuote(id, tenant.currentStoreId(), targetDate);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Registrar un pago",
            description = "Exige la cancelacion integral de la liquidacion (no admite pagos parciales, "
                    + "cap. 7.5). Imputa el importe en el orden legal del Art. 1257 C.C.: mora/penalidad, "
                    + "interes compensatorio y capital. Si el importe recibido excede la deuda, el "
                    + "excedente se acumula en el saldo a favor del cliente (customers.favour_balance).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago registrado"),
            @ApiResponse(responseCode = "400", description = "Monto invalido"),
            @ApiResponse(responseCode = "404", description = "La liquidacion no existe en la tienda"),
            @ApiResponse(responseCode = "409", description = "Liquidacion ya cancelada"),
            @ApiResponse(responseCode = "422", description = "Pago parcial rechazado")
    })
    public PaymentResponseDto pay(@PathVariable Integer id,
                                  @Valid @RequestBody PaymentRequestDto request) {
        return settlementService.pay(id, tenant.currentStoreId(), request);
    }
}
