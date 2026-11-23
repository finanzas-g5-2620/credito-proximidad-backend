package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdvanceDateResponse", description = "Nueva fecha del sistema")
public record AdvanceDateResponseDto(

        @Schema(example = "2026-12-26") String newDate
) {
}
