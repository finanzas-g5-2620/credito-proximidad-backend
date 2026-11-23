package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.upc.credito.domain.enums.RateType;

@Schema(name = "SimulateScheduleRequest", description = "Datos de entrada del simulador de cronograma frances")
public record SimulateScheduleRequestDto(

        @Schema(description = "Capital inicial en soles", example = "1500.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @DecimalMin(value = "0.01", message = "El capital inicial debe ser mayor a cero")
        String initial_capital,

        @Schema(description = "Tasa anual en fraccion (0.24 = 24%)", example = "0.24", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @DecimalMin(value = "0.0000001", message = "La tasa debe ser mayor a cero")
        String rate_value,

        @Schema(description = "NOMINAL (TNA) o EFECTIVA (TEA)", example = "NOMINAL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        RateType rate_type,

        @Schema(description = "Dias de capitalizacion m cuando la tasa es NOMINAL", example = "360", defaultValue = "360")
        @Min(value = 1, message = "Los dias de capitalizacion deben ser mayores a cero")
        @Max(value = 365, message = "Los dias de capitalizacion no pueden exceder 365")
        Integer compounding_days,

        @Schema(description = "Numero de cuotas n", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(value = 1, message = "Debe existir al menos una cuota")
        @Max(value = 120, message = "El plazo maximo es 120 cuotas")
        Integer total_installments,

        @Schema(description = "Dias de gracia tg (periodo de gracia total)", example = "15", defaultValue = "0")
        @Min(value = 0, message = "Los dias de gracia no pueden ser negativos")
        @Max(value = 365, message = "Los dias de gracia no pueden exceder 365")
        Integer grace_days,

        @Schema(description = "Fecha base del cronograma (yyyy-MM-dd); la cuota k vence k meses despues. Por defecto, la fecha actual", example = "2026-12-05")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Formato de fecha invalido (yyyy-MM-dd)")
        String first_due_date
) {
}
