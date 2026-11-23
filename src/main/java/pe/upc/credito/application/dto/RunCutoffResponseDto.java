package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RunCutoffResponse", description = "Resultado de la ejecucion del corte mensual")
public record RunCutoffResponseDto(

        @Schema(example = "Corte ejecutado para el periodo 2026-12: 3 liquidaciones generadas")
        String message,

        @Schema(example = "3") int generatedCount
) {
}
