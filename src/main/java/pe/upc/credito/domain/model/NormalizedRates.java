package pe.upc.credito.domain.model;

import java.math.BigDecimal;

/**
 * Tasas normalizadas a la base comercial 360/30.
 *
 * @param tea   tasa efectiva anual
 * @param ted   tasa efectiva diaria: (1 + TEA)^(1/360) - 1
 * @param tep30 tasa efectiva del periodo de 30 dias: (1 + TEA)^(30/360) - 1
 */
public record NormalizedRates(BigDecimal tea, BigDecimal ted, BigDecimal tep30) {
}
