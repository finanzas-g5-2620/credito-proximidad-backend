package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.upc.credito.domain.enums.PaymentMode;

import java.util.List;

@Schema(name = "CreateSaleRequest", description = "Registro de una venta con uno o mas productos")
public record CreateSaleRequestDto(

        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Integer customer_id,

        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Integer store_id,

        @Schema(example = "FINANCIADO_CUOTAS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PaymentMode payment_mode,

        @Schema(description = "Cuotas n (obligatorio para FINANCIADO_CUOTAS)", example = "3")
        @Min(value = 1, message = "Debe existir al menos una cuota")
        Integer installments_count,

        @Schema(description = "Dias de gracia tg", example = "0", defaultValue = "0")
        @Min(value = 0, message = "Los dias de gracia no pueden ser negativos")
        Integer grace_days,

        @Schema(description = "TEA efectiva pactada en fraccion (0.18 = 18%)", example = "0.1800000")
        @DecimalMin(value = "0.0000001", message = "La TEA debe ser mayor a cero")
        String tea,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "La venta debe contener al menos un detalle")
        List<@Valid CreateSaleDetailRequestDto> details
) {
}
