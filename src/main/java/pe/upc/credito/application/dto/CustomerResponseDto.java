package pe.upc.credito.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CustomerCreditInfo", description = "Cliente con su linea de credito y deuda vigente")
public record CustomerResponseDto(

        @Schema(example = "1") Integer id,
        @Schema(example = "Maria Quispe Rojas") String full_name,
        @Schema(description = "DNI derivado del identificador (el cap. 8 no almacena documento)", example = "00000001")
        String document_number,
        @Schema(example = "1500.00") String credit_limit,
        @Schema(description = "Capital pendiente: liquidaciones impagas + cuotas no facturadas", example = "0.00")
        String current_debt,
        @Schema(description = "false si el cliente tiene una liquidacion vencida impaga")
        boolean allows_monthly_payments,
        @Schema(description = "Saldo a favor por pagos que excedieron la deuda exigible (cap. 7.5)",
                example = "0.00")
        String favour_balance
) {
}
