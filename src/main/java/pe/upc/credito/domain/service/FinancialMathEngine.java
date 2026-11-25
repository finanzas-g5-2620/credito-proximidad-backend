package pe.upc.credito.domain.service;

import org.springframework.stereotype.Service;
import pe.upc.credito.domain.enums.RateType;
import pe.upc.credito.domain.model.NormalizedRates;
import pe.upc.credito.domain.model.SimulatedInstallment;
import pe.upc.credito.domain.model.SimulatedSchedule;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates commercial interest equivalences, French amortization and late interest.
 * Intermediate operations use 50-digit precision. The final installment absorbs
 * the cent adjustment so that the remaining balance reaches zero.
 */
@Service
public class FinancialMathEngine {

    public static final int COMMERCIAL_YEAR_DAYS = 360;
    public static final int COMMERCIAL_MONTH_DAYS = 30;
    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 7;

    /** Precision for intermediate financial calculations. */
    private static final MathContext MC = new MathContext(50, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE;

    // ------------------------------------------------------------------
    // Rate normalization
    // ------------------------------------------------------------------

    /**
     * Returns annual, daily and 30-day effective rates.
     *
     * Converts a rate quoted for its reference period into an effective annual
     * rate. Nominal rates accrue at the specified number of commercial days,
     * including weekly compounding where the annual exponent is 360 / 7.
     */
    public NormalizedRates normalize(BigDecimal rateValue, RateType rateType, Integer compoundingDays) {
        return normalize(rateValue, rateType, COMMERCIAL_YEAR_DAYS, compoundingDays);
    }

    public NormalizedRates normalize(BigDecimal rateValue,
                                     RateType rateType,
                                     Integer ratePeriodDays,
                                     Integer compoundingDays) {
        if (rateValue == null || rateValue.signum() < 0) {
            throw new IllegalArgumentException("The rate cannot be negative");
        }
        if (rateType == null) {
            throw new IllegalArgumentException("The rate type is required");
        }
        if (ratePeriodDays == null || !isAllowedRatePeriod(ratePeriodDays)) {
            throw new IllegalArgumentException("Invalid rate reference period");
        }

        BigDecimal tea;
        if (rateType == RateType.NOMINAL) {
            if (compoundingDays == null || !isAllowedCapitalizationPeriod(compoundingDays)) {
                throw new IllegalArgumentException("Invalid capitalization period");
            }
            BigDecimal nominalPeriodRate = rateValue
                    .multiply(BigDecimal.valueOf(compoundingDays), MC)
                    .divide(BigDecimal.valueOf(ratePeriodDays), MC);
            BigDecimal periodFactor = ONE.add(nominalPeriodRate, MC);
            tea = powRational(periodFactor, COMMERCIAL_YEAR_DAYS, compoundingDays).subtract(ONE, MC);
        } else {
            if (compoundingDays != null) {
                throw new IllegalArgumentException("An effective rate does not use nominal capitalization");
            }
            tea = powRational(ONE.add(rateValue, MC), COMMERCIAL_YEAR_DAYS, ratePeriodDays)
                    .subtract(ONE, MC);
        }

        BigDecimal onePlusTea = ONE.add(tea, MC);
        BigDecimal ted = nthRoot(onePlusTea, COMMERCIAL_YEAR_DAYS).subtract(ONE, MC);
        BigDecimal tep30 = nthRoot(onePlusTea, COMMERCIAL_YEAR_DAYS / COMMERCIAL_MONTH_DAYS)
                .subtract(ONE, MC);

        return new NormalizedRates(tea, ted, tep30);
    }

    // ------------------------------------------------------------------
    // French amortization
    // ------------------------------------------------------------------

    /**
     * Calculates the French amortization schedule.
     *
     * @param initialCapital    opening principal
     * @param rateValue         rate fraction for the reference period
     * @param rateType          NOMINAL or EFFECTIVE
     * @param ratePeriodDays    number of commercial days in the reference period
     * @param compoundingDays   days between capitalization events for a nominal rate
     * @param totalInstallments n &gt;= 1
     * @param graceDays         tg &gt;= 0
     * @param firstDueDate      first installment due date
     */
    public SimulatedSchedule simulate(BigDecimal initialCapital,
                                      BigDecimal rateValue,
                                      RateType rateType,
                                      Integer compoundingDays,
                                      int totalInstallments,
                                      int graceDays,
                                      LocalDate firstDueDate) {
        return simulate(initialCapital, rateValue, rateType,
                COMMERCIAL_YEAR_DAYS,
                rateType == RateType.NOMINAL ? compoundingDays : null,
                totalInstallments, graceDays, firstDueDate,
                firstDueDate == null ? 0 : firstDueDate.getDayOfMonth());
    }

    public SimulatedSchedule simulate(BigDecimal initialCapital,
                                      BigDecimal rateValue,
                                      RateType rateType,
                                      Integer ratePeriodDays,
                                      Integer compoundingDays,
                                      int totalInstallments,
                                      int graceDays,
                                      LocalDate firstDueDate) {
        return simulate(initialCapital, rateValue, rateType, ratePeriodDays, compoundingDays,
                totalInstallments, graceDays, firstDueDate,
                firstDueDate == null ? 0 : firstDueDate.getDayOfMonth());
    }

    /**
     * Simulates a schedule whose regular due dates follow a contractual day of
     * month. The anchor matters when the first date was clamped in February:
     * a payment day of 30 resumes on the 30th in March.
     */
    public SimulatedSchedule simulate(BigDecimal initialCapital,
                                      BigDecimal rateValue,
                                      RateType rateType,
                                      Integer ratePeriodDays,
                                      Integer compoundingDays,
                                      int totalInstallments,
                                      int graceDays,
                                      LocalDate firstDueDate,
                                      int paymentDayOfMonth) {
        if (initialCapital == null || initialCapital.signum() <= 0) {
            throw new IllegalArgumentException("Initial principal must be greater than zero");
        }
        if (totalInstallments < 1) {
            throw new IllegalArgumentException("At least one installment is required");
        }
        if (graceDays < 0) {
            throw new IllegalArgumentException("Grace days cannot be negative");
        }
        if (firstDueDate == null) {
            throw new IllegalArgumentException("The first installment due date is required");
        }
        if (paymentDayOfMonth < 1 || paymentDayOfMonth > 31) {
            throw new IllegalArgumentException("The payment day must be between 1 and 31");
        }

        BigDecimal p = initialCapital.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        NormalizedRates rates = normalize(rateValue, rateType, ratePeriodDays, compoundingDays);
        BigDecimal tep30 = rates.tep30();

        // Capitalize interest accrued during the grace period.
        BigDecimal pPrime = p;
        if (graceDays > 0) {
            pPrime = p.multiply(ONE.add(rates.ted(), MC).pow(graceDays, MC), MC)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        // Capital recovery factor:
        // FRC = [TEP30 * (1 + TEP30)^n] / [(1 + TEP30)^n - 1]
        BigDecimal onePlusTepPowN = ONE.add(tep30, MC).pow(totalInstallments, MC);
        BigDecimal denominator = onePlusTepPowN.subtract(ONE, MC);
        BigDecimal frc = tep30.signum() == 0
                ? ONE.divide(BigDecimal.valueOf(totalInstallments), MC)
                : tep30.multiply(onePlusTepPowN, MC).divide(denominator, MC);

        // Regular installment R = round(P' * FRC, 2)
        BigDecimal regularInstallment = pPrime.multiply(frc, MC).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<SimulatedInstallment> installments = new ArrayList<>(totalInstallments + 1);
        BigDecimal balance = pPrime;
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal totalPayment = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal zero = BigDecimal.ZERO.setScale(MONEY_SCALE);

        // Row zero records interest capitalized during the grace period.
        if (graceDays > 0) {
            BigDecimal capitalizedInterest = pPrime.subtract(p, MC).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            LocalDate graceDate = java.time.YearMonth.from(firstDueDate).minusMonths(1)
                    .atDay(Math.min(paymentDayOfMonth,
                            java.time.YearMonth.from(firstDueDate).minusMonths(1).lengthOfMonth()));
            installments.add(new SimulatedInstallment(
                    0, graceDate, zero, zero, capitalizedInterest, pPrime, true));
            totalInterest = totalInterest.add(capitalizedInterest);
        }

        for (int k = 1; k <= totalInstallments; k++) {
            java.time.YearMonth dueMonth = java.time.YearMonth.from(firstDueDate).plusMonths(k - 1L);
            LocalDate dueDate = dueMonth.atDay(Math.min(paymentDayOfMonth, dueMonth.lengthOfMonth()));

            // I_k = round(S_{k-1} * TEP30, 2)
            BigDecimal interest = balance.multiply(tep30, MC).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            BigDecimal principal;
            BigDecimal payment;

            if (k == totalInstallments) {
                // Terminal cent adjustment
                principal = balance.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                payment = principal.add(interest).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                balance = zero;
            } else {
                principal = regularInstallment.subtract(interest, MC).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                payment = regularInstallment;
                balance = balance.subtract(principal, MC).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }

            totalInterest = totalInterest.add(interest);
            totalPayment = totalPayment.add(payment);

            installments.add(new SimulatedInstallment(
                    k, dueDate, payment, principal, interest, balance, false));
        }

        return new SimulatedSchedule(
                p,
                pPrime,
                graceDays,
                totalInstallments,
                rates,
                regularInstallment,
                totalInterest.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                totalPayment.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                List.copyOf(installments));
    }

    // ------------------------------------------------------------------
    // Late interest
    // ------------------------------------------------------------------

    /**
     * Late interest on the overdue payment, including billed compensatory interest.
     *
     * <pre>interest = overdue_payment * ((1 + annual_penalty_rate)^(d/360) - 1)</pre>
     *
     *
     */
    public BigDecimal overduePenalty(BigDecimal overduePayment, BigDecimal penaltyTea, int daysOverdue) {
        return accruedInterest(overduePayment, penaltyTea, daysOverdue);
    }

    public BigDecimal accruedInterest(BigDecimal amount, BigDecimal annualEffectiveRate, int days) {
        if (days <= 0 || amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        if (annualEffectiveRate == null || annualEffectiveRate.signum() < 0) {
            throw new IllegalArgumentException("The annual effective rate cannot be negative");
        }
        BigDecimal factor = powRational(ONE.add(annualEffectiveRate, MC), days,
                COMMERCIAL_YEAR_DAYS).subtract(ONE, MC);
        return amount.multiply(factor, MC).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** European 30/360 day count. Dates on day 31 are treated as day 30. */
    public int days30E360(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both dates are required for 30E/360");
        }
        int startDay = Math.min(from.getDayOfMonth(), COMMERCIAL_MONTH_DAYS);
        int endDay = Math.min(to.getDayOfMonth(), COMMERCIAL_MONTH_DAYS);
        return 360 * (to.getYear() - from.getYear())
                + COMMERCIAL_MONTH_DAYS * (to.getMonthValue() - from.getMonthValue())
                + endDay - startDay;
    }

    private BigDecimal powRational(BigDecimal base, int numerator, int denominator) {
        if (base == null || base.signum() <= 0 || numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("Invalid exponent for a financial calculation");
        }
        if (numerator == 0) {
            return ONE;
        }
        int gcd = gcd(numerator, denominator);
        int reducedNumerator = numerator / gcd;
        int reducedDenominator = denominator / gcd;
        int whole = reducedNumerator / reducedDenominator;
        int remainder = reducedNumerator % reducedDenominator;
        BigDecimal result = base.pow(whole, MC);
        if (remainder == 0) {
            return result;
        }
        BigDecimal fractionalBase = base.pow(remainder, MC);
        return result.multiply(nthRoot(fractionalBase, reducedDenominator), MC);
    }

    private int gcd(int left, int right) {
        int a = Math.abs(left);
        int b = Math.abs(right);
        while (b != 0) {
            int next = a % b;
            a = b;
            b = next;
        }
        return a;
    }

    private boolean isAllowedRatePeriod(int days) {
        return days == 1 || days == 15 || days == 30 || days == 360;
    }

    private boolean isAllowedCapitalizationPeriod(int days) {
        return days == 1 || days == 7 || days == 15 || days == 30
                || days == 60 || days == 90 || days == 180 || days == 360;
    }

    // ------------------------------------------------------------------
    // Numerical helpers
    // ------------------------------------------------------------------

    /**
     * Computes the nth root using Newton-Raphson at the precision of {@link #MC}.
     * x_{k+1} = ((n - 1) * x_k + A / x_k^(n-1)) / n
     */
    BigDecimal nthRoot(BigDecimal value, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("The root degree must be positive");
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (n == 1) {
            return value;
        }

        BigDecimal nBd = BigDecimal.valueOf(n);
        BigDecimal nMinusOne = nBd.subtract(ONE);
        // Relative tolerance at working precision (50 digitos -> 1E-45)
        BigDecimal tolerance = ONE.scaleByPowerOfTen(-(MC.getPrecision() - 5));

        // Initialize Newton iteration with a double-precision estimate.
        BigDecimal x = BigDecimal.valueOf(Math.pow(value.doubleValue(), 1.0 / n));
        if (x.signum() == 0) {
            x = ONE;
        }

        for (int i = 0; i < 200; i++) {
            BigDecimal xPow = x.pow(n - 1, MC);
            BigDecimal next = x.multiply(nMinusOne, MC)
                    .add(value.divide(xPow, MC), MC)
                    .divide(nBd, MC);
            if (next.subtract(x).abs().compareTo(tolerance) <= 0) {
                return next;
            }
            x = next;
        }
        return x;
    }
}
