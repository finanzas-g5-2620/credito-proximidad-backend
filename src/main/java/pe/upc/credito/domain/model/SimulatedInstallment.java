package pe.upc.credito.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuota simulada del cronograma frances.
 * Los importes ya vienen redondeados a 2 decimales (HALF_UP).
 */
public record SimulatedInstallment(
        int installmentNumber,
        LocalDate dueDate,
        BigDecimal paymentAmount,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal balanceAmount,
        boolean gracePeriod
) {
}
