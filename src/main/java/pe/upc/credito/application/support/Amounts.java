package pe.upc.credito.application.support;

import org.springframework.http.HttpStatus;
import pe.upc.credito.application.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion y formato de importes y tasas segun el contrato del frontend:
 * dinero = 2 decimales; tasas = 7 decimales; siempre como string JSON.
 */
public final class Amounts {

    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 7;

    private Amounts() {
    }

    public static BigDecimal money(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("INVALID_AMOUNT", "El importe es obligatorio", HttpStatus.BAD_REQUEST);
        }
        try {
            return new BigDecimal(raw.trim()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_AMOUNT",
                    "Importe invalido: \"" + raw + "\"", HttpStatus.BAD_REQUEST);
        }
    }

    public static BigDecimal rate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("INVALID_RATE", "La tasa es obligatoria", HttpStatus.BAD_REQUEST);
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() < 0) {
                throw new BusinessException("INVALID_RATE", "La tasa no puede ser negativa", HttpStatus.BAD_REQUEST);
            }
            return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_RATE",
                    "Tasa invalida: \"" + raw + "\"", HttpStatus.BAD_REQUEST);
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
