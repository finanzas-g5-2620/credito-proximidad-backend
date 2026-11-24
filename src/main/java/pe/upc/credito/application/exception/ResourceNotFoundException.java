package pe.upc.credito.application.exception;

import org.springframework.http.HttpStatus;

/** Indicates a missing resource within the authenticated scope. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
