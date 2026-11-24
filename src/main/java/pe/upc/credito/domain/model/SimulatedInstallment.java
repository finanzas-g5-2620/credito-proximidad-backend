package pe.upc.credito.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Scheduled payment row or grace-period accrual. */
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
