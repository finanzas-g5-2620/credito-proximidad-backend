package pe.upc.credito.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado completo de una simulacion de cronograma frances.
 *
 * @param initialCapital      P redondeado a 2 decimales
 * @param capitalizedCapital  P' = P * (1 + TED)^tg (Art. 1250 C.C.)
 * @param graceDays           tg
 * @param totalInstallments   n
 * @param rates               TEA / TED / TEP30 sin redondear
 * @param regularInstallment  cuota uniforme R
 * @param totalInterest       suma de intereses (incluye capitalizacion de gracia)
 * @param totalPayment        suma de cuotas pagadas
 * @param installments        fila 0 (gracia, si tg &gt; 0) + filas 1..n
 */
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
