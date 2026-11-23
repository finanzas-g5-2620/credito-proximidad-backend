package pe.upc.credito.domain.service;

import org.junit.jupiter.api.Test;
import pe.upc.credito.domain.enums.RateType;
import pe.upc.credito.domain.model.SimulatedInstallment;
import pe.upc.credito.domain.model.SimulatedSchedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el algoritmo general (sin ramas de calibracion) reproduce los
 * valores del "Caso de Prueba 1" del frontend Angular.
 */
class FinancialMathEngineTest {

    private final FinancialMathEngine engine = new FinancialMathEngine();

    @Test
    void reproduceElCasoDePrueba1DelFrontend() {
        SimulatedSchedule schedule = engine.simulate(
                new BigDecimal("1500"),
                new BigDecimal("0.24"),
                RateType.NOMINAL,
                360,
                3,
                15,
                LocalDate.of(2026, 12, 5));

        assertThat(schedule.initialCapital()).isEqualByComparingTo("1500.00");
        assertThat(schedule.capitalizedCapital()).isEqualByComparingTo("1515.07");
        assertThat(schedule.regularInstallment()).isEqualByComparingTo("525.52");
        assertThat(schedule.totalInterest()).isEqualByComparingTo("76.69");
        assertThat(schedule.totalPayment()).isEqualByComparingTo("1576.69");

        assertThat(schedule.rates().tea().setScale(7, RoundingMode.HALF_UP))
                .isEqualByComparingTo("0.2711545");
        assertThat(schedule.rates().ted().setScale(7, RoundingMode.HALF_UP))
                .isEqualByComparingTo("0.0006667");
        assertThat(schedule.rates().tep30().setScale(7, RoundingMode.HALF_UP))
                .isEqualByComparingTo("0.0201993");

        assertThat(schedule.installments()).hasSize(4);
        assertInstallment(schedule.installments().get(0), 0,
                "2026-12-05", "0.00", "0.00", "15.07", "1515.07");
        assertInstallment(schedule.installments().get(1), 1,
                "2027-01-05", "525.52", "494.92", "30.60", "1020.15");
        assertInstallment(schedule.installments().get(2), 2,
                "2027-02-05", "525.52", "504.91", "20.61", "515.24");
        assertInstallment(schedule.installments().get(3), 3,
                "2027-03-05", "525.65", "515.24", "10.41", "0.00");
    }

    @Test
    void laUltimaCuotaExtingueElSaldoYAbsorbeElCentavoTerminal() {
        SimulatedSchedule schedule = engine.simulate(
                new BigDecimal("1000"),
                new BigDecimal("0.18"),
                RateType.EFECTIVA,
                360,
                6,
                0,
                LocalDate.of(2026, 1, 15));

        assertThat(schedule.installments()).hasSize(6);
        assertThat(schedule.installments().get(5).balanceAmount()).isEqualByComparingTo("0.00");
        assertThat(schedule.totalPayment())
                .isEqualByComparingTo(schedule.capitalizedCapital().add(schedule.totalInterest()));
    }

    private void assertInstallment(SimulatedInstallment installment,
                                   int number,
                                   String dueDate,
                                   String payment,
                                   String principal,
                                   String interest,
                                   String balance) {
        assertThat(installment.installmentNumber()).isEqualTo(number);
        assertThat(installment.dueDate()).isEqualTo(LocalDate.parse(dueDate));
        assertThat(installment.paymentAmount()).isEqualByComparingTo(payment);
        assertThat(installment.principalAmount()).isEqualByComparingTo(principal);
        assertThat(installment.interestAmount()).isEqualByComparingTo(interest);
        assertThat(installment.balanceAmount()).isEqualByComparingTo(balance);
    }
}
