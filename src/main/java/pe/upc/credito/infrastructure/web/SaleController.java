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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.CreateSaleRequestDto;
import pe.upc.credito.application.dto.SaleResponseDto;
import pe.upc.credito.application.service.SaleService;

import java.util.List;

@RestController
@RequestMapping("/sales")
@Tag(name = "Sales", description = "Cash, month-end, and installment sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    @Operation(summary = "List sales", description = "Lists sales in the authenticated store, or the authenticated customer's own sales.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sales listed"),
            @ApiResponse(responseCode = "401", description = "Bearer token is missing or invalid")
    })
    public List<SaleResponseDto> list() {
        return saleService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a sale", description = "Returns a sale within the authenticated tenant and customer scope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sale found"),
            @ApiResponse(responseCode = "404", description = "Sale was not found in the authorized scope")
    })
    public SaleResponseDto get(@PathVariable Integer id) {
        return saleService.get(id);
    }

    @PostMapping
    @Operation(summary = "Register a sale", description = "Reprices items from the catalog, checks stock and credit, and creates a month-end obligation or an amortization schedule. `Idempotency-Key` is required and must be reused only to retry this exact request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sale registered"),
            @ApiResponse(responseCode = "400", description = "Sale request is invalid"),
            @ApiResponse(responseCode = "409", description = "Stock, quote, rate terms, or idempotency key conflict"),
            @ApiResponse(responseCode = "422", description = "Credit or product payment rules are not satisfied")
    })
    public SaleResponseDto create(
            @Valid @RequestBody CreateSaleRequestDto request,
            @Parameter(description = "Required stable key for safely retrying this exact sale request",
                    required = true, example = "sale-9c3c82a1")
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {
        return saleService.create(request, idempotencyKey);
    }
}
