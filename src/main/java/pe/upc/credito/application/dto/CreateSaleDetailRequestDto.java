package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreateSaleDetailRequest", description = "Linea de detalle de una venta")
public record CreateSaleDetailRequestDto(

        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Integer product_id,

        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(value = 1, message = "La cantidad debe ser mayor a cero")
        Integer quantity,

        @Schema(description = "Precio unitario de lista a credito", example = "175.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a cero")
        String unit_price
) {
}
