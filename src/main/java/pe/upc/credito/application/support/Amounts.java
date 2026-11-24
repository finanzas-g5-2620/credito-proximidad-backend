package pe.upc.credito.application.support;

import org.springframework.http.HttpStatus;
import pe.upc.credito.application.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Parses and formats the amounts and rates used by the HTTP contract.
 * Money uses two decimal places. Displayed rates use seven decimal places.
 */
public final class Amounts {

    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 7;

    private Amounts() {
    }

    public static BigDecimal money(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("INVALID_AMOUNT", "The amount is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return new BigDecimal(raw.trim()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_AMOUNT",
                    "Invalid amount: \"" + raw + "\"", HttpStatus.BAD_REQUEST);
        }
    }

    public static BigDecimal rate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("INVALID_RATE", "The rate is required", HttpStatus.BAD_REQUEST);
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() < 0) {
                throw new BusinessException("INVALID_RATE", "The rate cannot be negative", HttpStatus.BAD_REQUEST);
            }
            return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_RATE",
                    "Invalid rate: \"" + raw + "\"", HttpStatus.BAD_REQUEST);
        }
    }

    public static String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    public static String rate(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(RATE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    public static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
