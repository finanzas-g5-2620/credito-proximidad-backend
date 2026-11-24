package pe.upc.credito.application.support;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.upc.credito.application.exception.BusinessException;

/** Provides trusted identity and tenant scope from the verified bearer token. */
@Component
public class TenantContext {

    public Integer currentStoreId() {
        Integer storeId = principal().storeId();
        if (storeId == null) {
            throw new BusinessException("STORE_SCOPE_REQUIRED",
                    "A store-scoped account is required for this operation.", HttpStatus.FORBIDDEN);
        }
        return storeId;
    }

    public Integer currentUserId() {
        return principal().userId();
    }

    public AuthPrincipal currentIdentity() {
        return principal();
    }

    public Integer currentCustomerId() {
        AuthPrincipal identity = principal();
        if (!"CUSTOMER".equals(identity.role()) || identity.customerId() == null) {
            throw new BusinessException("CUSTOMER_SCOPE_REQUIRED",
                    "A customer account is required for this operation.", HttpStatus.FORBIDDEN);
        }
        return identity.customerId();
    }

    public String currentRole() {
        return principal().role();
    }

    public boolean isStoreAdmin() {
        return "STORE_ADMIN".equals(currentRole());
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(currentRole());
    }

    public void requireCustomerOwner(Integer customerId) {
        if (isCustomer() && !currentCustomerId().equals(customerId)) {
            throw new BusinessException("CUSTOMER_SCOPE_VIOLATION",
                    "The requested customer record is outside your account.", HttpStatus.FORBIDDEN);
        }
    }

    private AuthPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthPrincipal identity) {
            return identity;
        }
        throw new BusinessException("UNAUTHENTICATED",
                "Authentication is required.", HttpStatus.UNAUTHORIZED);
    }
}
