package pe.upc.credito.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequestDto(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        String description,
        @Size(max = 50) String brand,
        @Size(max = 100) String supplier,
        @NotBlank @Pattern(regexp = "^(KG|LITER|UNIT|PACK|BAG)$") String unit_of_measure,
        @Size(max = 255) String image_url,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal cash_price,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal credit_list_price,
        @NotNull Boolean allows_installments,
        @NotNull @Min(0) Integer current_stock,
        @NotNull @Min(0) Integer min_stock,
        @NotBlank @Pattern(regexp = "^(AVAILABLE|OUT_OF_STOCK|DISCONTINUED)$") String status) {
}
