package pe.upc.credito.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Tabla 9 - payments (cobros en ventanilla, prelacion Art. 1257 C.C.). */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "settlement_id", nullable = false)
    private Integer settlementId;

    @Column(name = "payment_datetime", nullable = false)
    private Instant paymentDatetime;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "calculated_overdue_days", nullable = false)
    private Integer calculatedOverdueDays;

    @Column(name = "allocated_to_penalty", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedToPenalty;

    @Column(name = "allocated_to_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedToInterest;

    @Column(name = "allocated_to_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedToPrincipal;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    /** V2: referencia externa de YAPE / transferencia. */
    @Column(name = "reference_number", length = 50)
    private String referenceNumber;
}
