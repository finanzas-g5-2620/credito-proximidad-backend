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

/** Tabla 3 - customers (cuenta corriente y expediente de riesgo). */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "store_id", nullable = false)
    private Integer storeId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @Column(name = "home_address", nullable = false, length = 150)
    private String homeAddress;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "max_credit_months", nullable = false)
    private Integer maxCreditMonths;

    @Column(name = "cutoff_day", nullable = false)
    private Integer cutoffDay;

    @Column(name = "payment_due_day", nullable = false)
    private Integer paymentDueDay;

    @Column(name = "compensatory_rate_period_days", nullable = false)
    private Integer compensatoryRatePeriodDays;

    @Column(name = "compensatory_capitalization_days")
    private Integer compensatoryCapitalizationDays;

    @Column(name = "compensatory_rate_type", nullable = false, length = 10)
    private String compensatoryRateType;

    @Column(name = "compensatory_rate_value", nullable = false, precision = 12, scale = 7)
    private BigDecimal compensatoryRateValue;

    @Column(name = "penalty_rate_period_days", nullable = false)
    private Integer penaltyRatePeriodDays;

    @Column(name = "penalty_capitalization_days")
    private Integer penaltyCapitalizationDays;

    @Column(name = "penalty_rate_type", nullable = false, length = 10)
    private String penaltyRateType;

    @Column(name = "penalty_rate_value", nullable = false, precision = 12, scale = 7)
    private BigDecimal penaltyRateValue;

    @Column(name = "credit_currency", nullable = false, length = 3)
    private String creditCurrency;

    @Column(name = "favour_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal favourBalance;
}
