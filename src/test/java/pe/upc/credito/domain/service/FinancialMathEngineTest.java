package pe.upc.credito.domain.service;

import org.junit.jupiter.api.Test;
import pe.upc.credito.domain.enums.RateType;
import pe.upc.credito.domain.model.SimulatedSchedule;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialMathEngineTest {

    private final FinancialMathEngine engine = new FinancialMathEngine();

    @Test
    void weeklyNominalCapitalizationUsesFractionalAnnualExponent() {
        BigDecimal nominal = new BigDecimal("0.24");
        var weekly = engine.normalize(nominal, RateType.NOMINAL, 360, 7);
        double expected = Math.pow(1.0 + 0.24 * 7.0 / 360.0, 360.0 / 7.0) - 1.0;

        assertThat(weekly.tea().doubleValue()).isCloseTo(expected,
                org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(weekly.tea()).isGreaterThan(nominal);
    }

    @Test
    void installmentDatesResumeContractualDayAfterFebruaryWasClamped() {
        SimulatedSchedule schedule = engine.simulate(
                new BigDecimal("1000.00"),
                new BigDecimal("0.18"),
                RateType.EFFECTIVE,
                360,
                null,
                3,
                0,
                LocalDate.of(2027, 2, 28),
                30);

        assertThat(schedule.installments()).extracting("dueDate")
                .containsExactly(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 30),
                        LocalDate.of(2027, 4, 30));
    }

    @Test
    void zeroRateScheduleHasNoInterestAndExtinguishesBalance() {
        SimulatedSchedule schedule = engine.simulate(
                new BigDecimal("90.00"),
                BigDecimal.ZERO,
                RateType.EFFECTIVE,
                360,
                null,
                3,
                0,
                LocalDate.of(2027, 1, 10));

        assertThat(schedule.regularInstallment()).isEqualByComparingTo("30.00");
        assertThat(schedule.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(schedule.totalPayment()).isEqualByComparingTo("90.00");
        assertThat(schedule.installments().get(2).balanceAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void dayCountUsesEuropeanThirtyEThreeSixtyConvention() {
        assertThat(engine.days30E360(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28)))
                .isEqualTo(28);
        assertThat(engine.days30E360(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)))
                .isEqualTo(32);
    }

    @Test
    void finalInstallmentExtinguishesRoundingResidual() {
        SimulatedSchedule schedule = engine.simulate(
                new BigDecimal("1000.00"),
                new BigDecimal("0.18"),
                RateType.EFFECTIVE,
                360,
                null,
                6,
                0,
                LocalDate.of(2026, 1, 15));

        assertThat(schedule.installments()).hasSize(6);
        assertThat(schedule.installments().get(5).balanceAmount()).isEqualByComparingTo("0.00");
        assertThat(schedule.totalPayment())
                .isEqualByComparingTo(schedule.capitalizedCapital().add(schedule.totalInterest()));
    }
}
