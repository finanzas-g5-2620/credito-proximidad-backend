package pe.upc.credito.application.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BillingCalendarTest {

    @Test
    void billsTheFirstInstallmentInTheCycleAfterTheGracePaymentDate() {
        LocalDate purchase = LocalDate.of(2026, 9, 15);
        LocalDate cutoff = BillingCalendar.cutoffForPurchase(purchase, 20);
        assertThat(BillingCalendar.paymentDueAfter(cutoff, 20, 26)).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(BillingCalendar.installmentCutoff(cutoff, 20, 1)).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(BillingCalendar.firstInstallmentDue(cutoff, 20, 26)).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void includesPurchasesOnTheCutoffDayAndMovesLaterPurchasesToTheNextCycle() {
        assertThat(BillingCalendar.cutoffForPurchase(LocalDate.of(2026, 11, 25), 25))
                .isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(BillingCalendar.cutoffForPurchase(LocalDate.of(2026, 11, 26), 25))
                .isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void accountsForShortMonthsAndEqualDates() {
        LocalDate cutoff = BillingCalendar.cutoffForPurchase(LocalDate.of(2027, 2, 28), 29);
        assertThat(cutoff).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(BillingCalendar.paymentDueAfter(cutoff, 29, 30))
                .isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void choosesTheNextValidPaymentDateAcrossYears() {
        assertThat(BillingCalendar.paymentDueAfter(LocalDate.of(2026, 12, 25), 25, 30))
                .isEqualTo(LocalDate.of(2026, 12, 30));
        assertThat(BillingCalendar.paymentDueAfter(LocalDate.of(2026, 12, 25), 25, 25))
                .isEqualTo(LocalDate.of(2027, 1, 25));
    }
}
