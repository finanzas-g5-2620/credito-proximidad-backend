package pe.upc.credito.domain.enums;

/** Estados persistidos de una cuota (CHECK de amortization_schedules). */
public enum InstallmentStatus {
    PENDIENTE,
    FACTURADA,
    PAGADA,
    EN_MORA
}
