package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreateSaleDetailRequest", description = "Requested sale line item")
public record CreateSaleDetailRequestDto(

        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Integer product_id,

        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(value = 1, message = "Quantity must be greater than zero")
        Integer quantity
) {
}
