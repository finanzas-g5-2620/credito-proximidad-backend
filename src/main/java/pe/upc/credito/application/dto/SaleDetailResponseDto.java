package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SaleDetail", description = "Persisted sale line item")
public record SaleDetailResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "1") Integer product_id,
        @Schema(example = "Saco de Arroz Extra 50kg") String product_name,
        @Schema(example = "SKU-0001") String sku,
        @Schema(example = "175.00") String unit_price,
        @Schema(example = "2") Integer quantity,
        @Schema(example = "350.00") String subtotal
) {
}
