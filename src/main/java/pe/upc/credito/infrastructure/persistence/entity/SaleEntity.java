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
import java.time.LocalDate;

/** Tabla 5 - sales (encabezado transaccional). */
@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class SaleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    private Integer saleId;

    @Column(name = "store_id", nullable = false)
    private Integer storeId;

    @Column(name = "customer_id", nullable = false)
    private Integer customerId;

    @Column(name = "sale_datetime", nullable = false)
    private Instant saleDatetime;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "month_end_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthEndInterest = BigDecimal.ZERO;

    @Column(name = "payment_mode", nullable = false, length = 25)
    private String paymentMode;

    @Column(name = "number_of_months", nullable = false)
    private Integer numberOfMonths;

    @Column(name = "sale_status", nullable = false, length = 20)
    private String saleStatus;

    /** V2: TEA efectiva pactada (NULL en ventas al contado). */
    @Column(name = "tea", precision = 12, scale = 7)
    private BigDecimal tea;

    @Column(name = "penalty_tea", precision = 12, scale = 7)
    private BigDecimal penaltyTea;

    @Column(name = "settlement_id")
    private Integer settlementId;

    @Column(name = "billing_cutoff_date")
    private LocalDate billingCutoffDate;

    @Column(name = "first_due_date")
    private LocalDate firstDueDate;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;
}
