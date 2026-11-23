package pe.upc.credito.domain.enums;

/**
 * Tipo de tasa anual. Coincide con el CHECK de la columna
 * customers.compensatory_rate_type / penalty_rate_type (cap. 8.3).
 */
public enum RateType {
    NOMINAL,
    EFECTIVA
}
