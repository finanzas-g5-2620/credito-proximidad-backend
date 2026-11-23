package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CatalogProduct", description = "Producto disponible para la venta")
public record CatalogProductResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "Saco de Arroz Extra 50kg") String name,
        @Schema(description = "SKU derivado del identificador", example = "SKU-0001") String sku,
        @Schema(description = "Categoria comercial derivada de la unidad de medida", example = "Abarrotes")
        String category,
        @Schema(description = "Precio de lista a credito", example = "175.00") String unit_price,
        @Schema(example = "25") Integer stock
) {
}
