package pe.upc.credito.application.exception;

import org.springframework.http.HttpStatus;

/** Recurso inexistente dentro del tenant autenticado. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
