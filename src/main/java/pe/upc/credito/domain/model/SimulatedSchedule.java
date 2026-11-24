package pe.upc.credito.domain.model;

import java.math.BigDecimal;
import java.util.List;

/** Calculated repayment schedule and totals. */
public record SimulatedSchedule(
        BigDecimal initialCapital,
        BigDecimal capitalizedCapital,
        int graceDays,
        int totalInstallments,
        NormalizedRates rates,
        BigDecimal regularInstallment,
        BigDecimal totalInterest,
        BigDecimal totalPayment,
        List<SimulatedInstallment> installments
) {
}
