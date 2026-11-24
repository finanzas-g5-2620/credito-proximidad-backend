package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SimulateScheduleResponse", description = "Calculated French amortization schedule")
public record SimulateScheduleResponseDto(

        @Schema(example = "1500.00") String initial_capital,
        @Schema(example = "1515.07") String capitalized_capital,
        @Schema(example = "15") int grace_days,
        @Schema(example = "3") int total_installments,
        @Schema(description = "Annual effective rate as a fraction", example = "0.2711475") String tea,
        @Schema(description = "Daily effective rate as a fraction", example = "0.0006667") String ted,
        @Schema(description = "Effective rate for a 30-day commercial month as a fraction", example = "0.0201945") String tep_30,
        @Schema(example = "525.56") String regular_installment,
        @Schema(example = "76.67") String total_interest,
        @Schema(example = "1576.67") String total_payment,
        List<SimulatedInstallmentDto> installments
) {
}
