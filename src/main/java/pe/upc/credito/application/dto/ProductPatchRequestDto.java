package pe.upc.credito.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductPatchRequestDto(
        @Size(max = 100) String name,
        String description,
        @Size(max = 50) String brand,
        @Size(max = 100) String supplier,
        @Pattern(regexp = "^(KG|LITER|UNIT|PACK|BAG)$") String unit_of_measure,
        @Size(max = 255) String image_url,
        @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal cash_price,
        @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal credit_list_price,
        Boolean allows_installments,
        @Min(0) Integer current_stock,
        @Min(0) Integer min_stock,
        @Pattern(regexp = "^(AVAILABLE|OUT_OF_STOCK|DISCONTINUED)$") String status) {
}
