package pe.upc.credito.application.exception;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con codigo estable para el interceptor del frontend.
 * El cuerpo de respuesta siempre es {code, message, details}.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
