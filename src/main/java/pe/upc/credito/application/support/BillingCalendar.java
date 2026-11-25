package pe.upc.credito.application.support;

import java.time.LocalDate;
import java.time.YearMonth;

/** Resolves contractual billing dates against the customer's local business calendar. */
public final class BillingCalendar {

    private BillingCalendar() {
    }

    public static LocalDate cutoffForPurchase(LocalDate purchaseDate, int cutoffDay) {
        YearMonth month = YearMonth.from(purchaseDate);
        LocalDate currentCutoff = dayOfMonth(month, cutoffDay);
        return purchaseDate.isAfter(currentCutoff)
                ? dayOfMonth(month.plusMonths(1), cutoffDay) : currentCutoff;
    }

    public static LocalDate dayOfMonth(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    public static LocalDate paymentDueAfter(LocalDate cutoff, int cutoffDay, int paymentDay) {
        YearMonth month = YearMonth.from(cutoff);
        return dayOfMonth(paymentDay > cutoffDay ? month : month.plusMonths(1), paymentDay);
    }

    public static LocalDate installmentCutoff(LocalDate purchaseCutoff, int cutoffDay, int installmentNumber) {
        return dayOfMonth(YearMonth.from(purchaseCutoff).plusMonths(installmentNumber), cutoffDay);
    }

    public static LocalDate firstInstallmentDue(LocalDate purchaseCutoff, int cutoffDay, int paymentDay) {
        return paymentDueAfter(installmentCutoff(purchaseCutoff, cutoffDay, 1), cutoffDay, paymentDay);
    }
}
