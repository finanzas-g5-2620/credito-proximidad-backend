package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CatalogProduct", description = "Product available for sale")
public record CatalogProductResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "50 kg rice bag") String name,
        @Schema(description = "SKU derived from the product identifier", example = "SKU-0001") String sku,
        @Schema(description = "Product category derived from its unit of measure", example = "GROCERIES")
        String category,
        @Schema(description = "Cash price", example = "160.00") String unit_price,
        @Schema(description = "Credit list price", example = "175.00") String credit_unit_price,
        boolean allows_installments,
        @Schema(example = "25") Integer stock
) {
}
