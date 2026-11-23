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
import java.time.LocalDate;

/** Tabla 8 - monthly_settlements (estado de cuenta mensual). */
@Entity
@Table(name = "monthly_settlements")
@Getter
@Setter
@NoArgsConstructor
public class MonthlySettlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Integer settlementId;

    @Column(name = "customer_id", nullable = false)
    private Integer customerId;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "cutoff_date", nullable = false)
    private LocalDate cutoffDate;

    @Column(name = "payment_due_date", nullable = false)
    private LocalDate paymentDueDate;

    @Column(name = "month_purchases_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthPurchasesAmount;

    @Column(name = "month_installments_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthInstallmentsAmount;

    @Column(name = "compensatory_interest_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal compensatoryInterestAmount;

    @Column(name = "penalty_interest_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal penaltyInterestAmount;

    @Column(name = "total_amount_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmountDue;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "pending_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal pendingBalance;

    @Column(name = "settlement_status", nullable = false, length = 20)
    private String settlementStatus;
}
