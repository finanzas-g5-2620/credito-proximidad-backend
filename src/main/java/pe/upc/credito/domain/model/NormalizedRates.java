package pe.upc.credito.domain.model;

import java.math.BigDecimal;

/** Equivalent annual, daily and 30-day rates carried at working precision. */
public record NormalizedRates(BigDecimal tea, BigDecimal ted, BigDecimal tep30) {
}
