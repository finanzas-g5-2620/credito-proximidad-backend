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

/** Tabla 7 - amortization_schedules (cronograma frances, base 360). */
@Entity
@Table(name = "amortization_schedules")
@Getter
@Setter
@NoArgsConstructor
public class AmortizationScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Integer scheduleId;

    @Column(name = "sale_id", nullable = false)
    private Integer saleId;

    @Column(name = "settlement_id")
    private Integer settlementId;

    /** 0 = periodo de gracia. */
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "initial_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal initialBalance;

    @Column(name = "principal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "compensatory_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal compensatoryInterest;

    @Column(name = "fixed_installment", nullable = false, precision = 12, scale = 2)
    private BigDecimal fixedInstallment;

    @Column(name = "ending_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal endingBalance;

    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays;

    @Column(name = "installment_status", nullable = false, length = 20)
    private String installmentStatus;
}
