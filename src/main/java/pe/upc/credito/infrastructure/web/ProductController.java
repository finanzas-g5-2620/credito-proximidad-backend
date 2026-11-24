package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.ProductCreateRequestDto;
import pe.upc.credito.application.dto.ProductPatchRequestDto;
import pe.upc.credito.application.dto.ProductResponseDto;
import pe.upc.credito.application.dto.ProductUpdateRequestDto;
import pe.upc.credito.application.service.ProductService;
import pe.upc.credito.application.support.TenantContext;

import java.util.List;

@RestController
@RequestMapping("/products")
@Tag(name = "Catalog", description = "Catalog and inventory in the authenticated store")
public class ProductController {

    private final ProductService products;
    private final TenantContext tenant;

    public ProductController(ProductService products, TenantContext tenant) {
        this.products = products;
        this.tenant = tenant;
    }

    @GetMapping
    @Operation(summary = "List store inventory",
            description = "Returns the catalog and inventory, including products excluded from sales.")
    public List<ProductResponseDto> list() {
        return products.list(tenant.currentStoreId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a product",
            description = "Returns the inventory record for a product in the authenticated store.")
    public ProductResponseDto get(@PathVariable Integer id) {
        return products.get(tenant.currentStoreId(), id);
    }

    @PostMapping
    @Operation(summary = "Create a product",
            description = "Adds a product with cash and credit prices, stock and installment eligibility.")
    public ProductResponseDto create(@Valid @RequestBody ProductCreateRequestDto request) {
        return products.create(tenant.currentStoreId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product",
            description = "Replaces the editable catalog and inventory fields.")
    public ProductResponseDto update(@PathVariable Integer id,
                                     @Valid @RequestBody ProductUpdateRequestDto request) {
        return products.update(tenant.currentStoreId(), id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change stock or product status",
            description = "Updates stock or availability and supports discontinuing a product without removing its history.")
    public ProductResponseDto patch(@PathVariable Integer id,
                                    @Valid @RequestBody ProductPatchRequestDto request) {
        return products.patch(tenant.currentStoreId(), id, request);
    }
}
