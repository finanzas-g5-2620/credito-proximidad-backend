package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.CreateSaleRequestDto;
import pe.upc.credito.application.dto.SaleResponseDto;
import pe.upc.credito.application.service.SaleService;
import pe.upc.credito.application.support.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/sales")
@Tag(name = "Ventas", description = "Registro y consulta de ventas al contado y a credito")
public class SaleController {

    private final SaleService saleService;
    private final TenantContext tenant;

    public SaleController(SaleService saleService, TenantContext tenant) {
        this.saleService = saleService;
        this.tenant = tenant;
    }

    @GetMapping
    @Operation(summary = "Listar ventas de la tienda",
            description = "Historial de ventas ordenado de la mas reciente a la mas antigua.")
    public List<SaleResponseDto> list() {
        return saleService.list(tenant.currentStoreId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una venta por identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Venta encontrada"),
            @ApiResponse(responseCode = "404", description = "La venta no existe en la tienda")
    })
    public SaleResponseDto get(@PathVariable Integer id) {
        return saleService.get(id, tenant.currentStoreId());
    }

    @PostMapping
    @Operation(summary = "Registrar una venta",
            description = "Valida stock y linea de credito, descuenta inventario y, si la venta "
                    + "es financiada, genera el cronograma frances completo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Venta registrada"),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida"),
            @ApiResponse(responseCode = "404", description = "Cliente o producto inexistente"),
            @ApiResponse(responseCode = "422", description = "Stock insuficiente o linea de credito excedida")
    })
    public SaleResponseDto create(@Valid @RequestBody CreateSaleRequestDto request) {
        return saleService.create(request);
    }
}
