package pe.upc.credito.domain.model;

import java.math.BigDecimal;

/** Principal and interest in a payoff quote on a specified business date. */
public record PayoffBreakdown(
        int daysOverdue,
        BigDecimal overduePrincipal,
        BigDecimal compensatoryInterest,
        BigDecimal moratoriumInterest
) {
    public BigDecimal total() {
        return overduePrincipal.add(compensatoryInterest).add(moratoriumInterest);
    }
}
