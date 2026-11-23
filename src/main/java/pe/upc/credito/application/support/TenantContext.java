package pe.upc.credito.application.support;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.upc.credito.application.exception.BusinessException;

/**
 * Resolucion del tenant (Ley N. 29733: aislamiento logico por store_id).
 *
 * <p>Mientras la autenticacion JWT no este implementada, el tenant se toma
 * del header {@code X-Store-Id}; si no viene, se usa la tienda 1 (la misma
 * que el frontend fija en {@code cart.store.ts}). Cuando exista sesion, este
 * componente se reemplazara por el store_id del token.</p>
 */
@Component
public class TenantContext {

    public static final String STORE_HEADER = "X-Store-Id";
    public static final int DEFAULT_STORE_ID = 1;

    public Integer currentStoreId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            String raw = servlet.getRequest().getHeader(STORE_HEADER);
            if (raw != null && !raw.isBlank()) {
                try {
                    return Integer.parseInt(raw.trim());
                } catch (NumberFormatException ex) {
                    throw new BusinessException("INVALID_STORE_HEADER",
                            "El header X-Store-Id debe ser numerico", HttpStatus.BAD_REQUEST);
                }
            }
        }
        return DEFAULT_STORE_ID;
    }
}
