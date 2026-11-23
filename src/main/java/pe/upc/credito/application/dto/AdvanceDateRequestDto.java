package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AdvanceDateRequest", description = "Adelanta el reloj simulado del sistema")
public record AdvanceDateRequestDto(

        @Schema(description = "Dias a adelantar", example = "35", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(value = 1, message = "Debe adelantar al menos un dia")
        Integer days
) {
}
