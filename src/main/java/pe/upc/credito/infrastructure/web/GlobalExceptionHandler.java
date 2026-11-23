package pe.upc.credito.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.upc.credito.application.dto.ApiErrorResponseDto;
import pe.upc.credito.application.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce excepciones al contrato uniforme {@code {code, message, details}}
 * que consume el interceptor HTTP del frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponseDto> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponseDto(exception.getCode(), exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiErrorResponseDto(
                "VALIDATION_ERROR",
                "La solicitud contiene campos invalidos",
                details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponseDto> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponseDto(
                "INVALID_REQUEST_BODY",
                "El cuerpo de la solicitud no tiene el formato esperado",
                null));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponseDto> handleBadParameter(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponseDto(
                "INVALID_PARAMETER",
                "Parametro de solicitud invalido: " + exception.getMessage(),
                null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleIntegrity(DataIntegrityViolationException exception) {
        log.warn("Violacion de integridad de datos", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponseDto(
                "DATA_INTEGRITY_VIOLATION",
                "La operacion viola una restriccion de la base de datos",
                null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponseDto(
                "NOT_FOUND",
                "Recurso no encontrado: " + exception.getResourcePath(),
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleUnexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponseDto(
                "INTERNAL_ERROR",
                "Ocurrio un error interno al procesar la solicitud",
                null));
    }
}
