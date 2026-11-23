package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SimulateScheduleResponse", description = "Cronograma frances calculado")
public record SimulateScheduleResponseDto(

        @Schema(example = "1500.00") String initial_capital,
        @Schema(example = "1515.07") String capitalized_capital,
        @Schema(example = "15") int grace_days,
        @Schema(example = "3") int total_installments,
        @Schema(description = "TEA con 7 decimales", example = "0.2711545") String tea,
        @Schema(description = "TED con 7 decimales", example = "0.0006667") String ted,
        @Schema(description = "TEP30 con 7 decimales", example = "0.0201993") String tep_30,
        @Schema(example = "525.52") String regular_installment,
        @Schema(example = "76.69") String total_interest,
        @Schema(example = "1576.69") String total_payment,
        List<SimulatedInstallmentDto> installments
) {
}
