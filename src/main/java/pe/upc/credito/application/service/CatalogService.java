package pe.upc.credito.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.CatalogProductResponseDto;
import pe.upc.credito.application.support.Amounts;
import pe.upc.credito.application.support.ApiCodes;
import pe.upc.credito.infrastructure.persistence.repository.ProductRepository;

import java.util.List;

/** Projects the authenticated store catalog for checkout. */
@Service
public class CatalogService {

    private final ProductRepository products;

    public CatalogService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CatalogProductResponseDto> list(Integer storeId) {
        return products.findByStoreIdOrderByProductIdAsc(storeId).stream()
                .filter(p -> !"DISCONTINUED".equals(p.getStatus()))
                .map(p -> new CatalogProductResponseDto(
                        p.getProductId(),
                        p.getName(),
                        ApiCodes.sku(p.getProductId()),
                        ApiCodes.category(p.getUnitOfMeasure()),
                        Amounts.money(p.getCashPrice()),
                        Amounts.money(p.getCreditListPrice()),
                        Boolean.TRUE.equals(p.getAllowsInstallments()),
                        p.getCurrentStock()))
                .toList();
    }
}
