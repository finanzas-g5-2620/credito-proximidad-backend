package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AdvanceDateRequest", description = "Advances the demo business date")
public record AdvanceDateRequestDto(

        @Schema(description = "Days to advance, from 1 to 365", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(value = 1, message = "Advance at least one day")
        @Max(value = 365, message = "Advance no more than 365 days per request")
        Integer days
) {
}
