package pe.upc.credito.domain.model;

import java.math.BigDecimal;

/**
 * Desglose del saldo exigible de una liquidacion a una fecha de calculo.
 *
 * @param daysOverdue           dias transcurridos desde el vencimiento
 * @param overduePrincipal      capital pendiente de amortizar
 * @param compensatoryInterest  interes compensatorio pendiente
 * @param moratoriumInterest    mora pendiente (penalidad almacenada + mora devengada)
 */
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
