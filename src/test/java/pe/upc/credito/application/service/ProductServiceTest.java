package pe.upc.credito.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.upc.credito.application.dto.ProductCreateRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.exception.ResourceNotFoundException;
import pe.upc.credito.infrastructure.persistence.entity.ProductEntity;
import pe.upc.credito.infrastructure.persistence.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository products;

    @Test
    void createUsesTokenStoreAndNormalizesEmptyStockStatus() {
        when(products.saveAndFlush(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            product.setProductId(21);
            return product;
        });

        var result = service().create(5, request("160.00", "175.00", 0));

        assertEquals(21, result.id());
        assertEquals(5, result.store_id());
        assertEquals("BAG", result.unit_of_measure());
        assertEquals("OUT_OF_STOCK", result.status());
        verify(products).saveAndFlush(any(ProductEntity.class));
    }

    @Test
    void creditPriceCannotBeLowerThanCashPrice() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service().create(5, request("175.00", "160.00", 1)));

        assertEquals("INVALID_PRODUCT_PRICES", error.getCode());
    }

    @Test
    void getCannotCrossStoreBoundary() {
        when(products.findByProductIdAndStoreId(21, 6)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service().get(6, 21));
    }

    private ProductService service() {
        return new ProductService(products);
    }

    private ProductCreateRequestDto request(String cash, String credit, int stock) {
        return new ProductCreateRequestDto("Rice", null, null, null, "BAG", null,
                new BigDecimal(cash), new BigDecimal(credit), true, stock, 2, null);
    }
}
