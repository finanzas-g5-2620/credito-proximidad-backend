package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.CatalogProductResponseDto;
import pe.upc.credito.application.service.CatalogService;
import pe.upc.credito.application.support.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/catalog")
@Tag(name = "Catalogo", description = "Productos disponibles para la venta")
public class CatalogController {

    private final CatalogService catalogService;
    private final TenantContext tenant;

    public CatalogController(CatalogService catalogService, TenantContext tenant) {
        this.catalogService = catalogService;
        this.tenant = tenant;
    }

    @GetMapping("/products")
    @Operation(summary = "Listar productos vendibles",
            description = "Devuelve el catalogo activo de la tienda con precio de lista a credito y stock.")
    public List<CatalogProductResponseDto> listProducts() {
        return catalogService.list(tenant.currentStoreId());
    }
}
