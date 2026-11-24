package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.ProductCreateRequestDto;
import pe.upc.credito.application.dto.ProductPatchRequestDto;
import pe.upc.credito.application.dto.ProductResponseDto;
import pe.upc.credito.application.dto.ProductUpdateRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.exception.ResourceNotFoundException;
import pe.upc.credito.infrastructure.persistence.entity.ProductEntity;
import pe.upc.credito.infrastructure.persistence.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private static final List<String> UNITS = List.of("KG", "LITER", "UNIT", "PACK", "BAG");
    private static final List<String> STATUSES = List.of("AVAILABLE", "OUT_OF_STOCK", "DISCONTINUED");

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> list(Integer storeId) {
        return products.findByStoreIdOrderByProductIdAsc(storeId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponseDto get(Integer storeId, Integer id) {
        return toDto(requireProduct(storeId, id));
    }

    @Transactional
    public ProductResponseDto create(Integer storeId, ProductCreateRequestDto request) {
        validateProduct(request.name(), request.unit_of_measure(), request.cash_price(),
                request.credit_list_price(), request.current_stock(), request.min_stock());
        String status = normalizedStatus(request.status(), request.current_stock());
        ProductEntity product = new ProductEntity();
        product.setStoreId(storeId);
        product.setName(request.name().trim());
        product.setDescription(normalizeNullable(request.description()));
        product.setBrand(normalizeNullable(request.brand()));
        product.setSupplier(normalizeNullable(request.supplier()));
        product.setUnitOfMeasure(request.unit_of_measure());
        product.setImageUrl(normalizeNullable(request.image_url()));
        product.setCashPrice(request.cash_price());
        product.setCreditListPrice(request.credit_list_price());
        product.setAllowsInstallments(request.allows_installments());
        product.setCurrentStock(request.current_stock());
        product.setMinStock(request.min_stock());
        product.setStatus(status);
        return toDto(products.saveAndFlush(product));
    }

    @Transactional
    public ProductResponseDto update(Integer storeId, Integer id, ProductUpdateRequestDto request) {
        ProductEntity product = requireProductForUpdate(storeId, id);
        validateProduct(request.name(), request.unit_of_measure(), request.cash_price(),
                request.credit_list_price(), request.current_stock(), request.min_stock());
        product.setName(request.name().trim());
        product.setDescription(normalizeNullable(request.description()));
        product.setBrand(normalizeNullable(request.brand()));
        product.setSupplier(normalizeNullable(request.supplier()));
        product.setUnitOfMeasure(request.unit_of_measure());
        product.setImageUrl(normalizeNullable(request.image_url()));
        product.setCashPrice(request.cash_price());
        product.setCreditListPrice(request.credit_list_price());
        product.setAllowsInstallments(request.allows_installments());
        product.setCurrentStock(request.current_stock());
        product.setMinStock(request.min_stock());
        product.setStatus(normalizedStatus(request.status(), request.current_stock()));
        return toDto(products.save(product));
    }

    @Transactional
    public ProductResponseDto patch(Integer storeId, Integer id, ProductPatchRequestDto request) {
        ProductEntity product = requireProductForUpdate(storeId, id);
        if (request.name() == null && request.description() == null && request.brand() == null
                && request.supplier() == null && request.unit_of_measure() == null
                && request.image_url() == null && request.cash_price() == null
                && request.credit_list_price() == null && request.allows_installments() == null
                && request.current_stock() == null && request.min_stock() == null && request.status() == null) {
            throw new BusinessException("EMPTY_PATCH", "At least one product field must be provided.", HttpStatus.BAD_REQUEST);
        }
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BusinessException("INVALID_NAME", "Product name cannot be blank.", HttpStatus.BAD_REQUEST);
            }
            product.setName(request.name().trim());
        }
        if (request.description() != null) product.setDescription(normalizeNullable(request.description()));
        if (request.brand() != null) product.setBrand(normalizeNullable(request.brand()));
        if (request.supplier() != null) product.setSupplier(normalizeNullable(request.supplier()));
        if (request.unit_of_measure() != null) product.setUnitOfMeasure(request.unit_of_measure());
        if (request.image_url() != null) product.setImageUrl(normalizeNullable(request.image_url()));
        if (request.cash_price() != null) product.setCashPrice(request.cash_price());
        if (request.credit_list_price() != null) product.setCreditListPrice(request.credit_list_price());
        if (request.allows_installments() != null) product.setAllowsInstallments(request.allows_installments());
        if (request.current_stock() != null) product.setCurrentStock(request.current_stock());
        if (request.min_stock() != null) product.setMinStock(request.min_stock());
        if (request.status() != null) product.setStatus(request.status());
        validateProduct(product.getName(), product.getUnitOfMeasure(), product.getCashPrice(),
                product.getCreditListPrice(), product.getCurrentStock(), product.getMinStock());
        product.setStatus(normalizedStatus(product.getStatus(), product.getCurrentStock()));
        return toDto(products.save(product));
    }

    private ProductEntity requireProduct(Integer storeId, Integer id) {
        return products.findByProductIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private ProductEntity requireProductForUpdate(Integer storeId, Integer id) {
        return products.findByProductIdAndStoreIdForUpdate(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private void validateProduct(String name, String unit, BigDecimal cash, BigDecimal credit,
                                 Integer stock, Integer minStock) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("INVALID_NAME", "Product name is required.", HttpStatus.BAD_REQUEST);
        }
        if (!UNITS.contains(unit)) {
            throw new BusinessException("INVALID_UNIT", "Unit of measure must be KG, LITER, UNIT, PACK or BAG.", HttpStatus.BAD_REQUEST);
        }
        if (cash == null || cash.signum() <= 0 || credit == null || credit.compareTo(cash) < 0) {
            throw new BusinessException("INVALID_PRODUCT_PRICES",
                    "Credit list price must be greater than or equal to a positive cash price.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (stock == null || stock < 0 || minStock == null || minStock < 0) {
            throw new BusinessException("INVALID_STOCK", "Stock values cannot be negative.", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizedStatus(String requested, Integer stock) {
        if (requested != null && !STATUSES.contains(requested)) {
            throw new BusinessException("INVALID_PRODUCT_STATUS", "Product status is invalid.", HttpStatus.BAD_REQUEST);
        }
        if ("DISCONTINUED".equals(requested)) {
            return requested;
        }
        return stock == 0 ? "OUT_OF_STOCK" : "AVAILABLE";
    }

    private ProductResponseDto toDto(ProductEntity product) {
        return new ProductResponseDto(product.getProductId(), product.getStoreId(), product.getName(),
                product.getDescription(), product.getBrand(), product.getSupplier(), product.getUnitOfMeasure(),
                product.getImageUrl(), product.getCashPrice(), product.getCreditListPrice(),
                product.getAllowsInstallments(), product.getCurrentStock(), product.getMinStock(), product.getStatus());
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
