package pe.upc.credito.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.support.AuthPrincipal;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

@Service
public class TokenSessionService {

    private final JwtTokenService tokens;
    private final UserRepository users;
    private final StoreRepository stores;
    private final CustomerRepository customers;

    public TokenSessionService(JwtTokenService tokens,
                               UserRepository users,
                               StoreRepository stores,
                               CustomerRepository customers) {
        this.tokens = tokens;
        this.users = users;
        this.stores = stores;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public AuthPrincipal authenticate(String token) {
        AuthPrincipal identity = tokens.parse(token);
        UserEntity user = users.findById(identity.userId())
                .orElseThrow(() -> invalidToken("The account no longer exists."));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("ACCOUNT_INACTIVE",
                    "The account is inactive or blocked.", HttpStatus.UNAUTHORIZED);
        }
        if (!identity.username().equals(user.getUsername())
                || !identity.role().equals(user.getUserType())
                || !same(identity.storeId(), user.getStoreId())) {
            throw invalidToken("The account permissions have changed. Sign in again.");
        }

        if ("SUPER_ADMIN".equals(user.getUserType())) {
            if (user.getStoreId() != null || identity.customerId() != null) {
                throw invalidToken("The account scope is invalid.");
            }
            return identity;
        }

        if (user.getStoreId() == null) {
            throw invalidToken("The account has no store scope.");
        }
        var store = stores.findById(user.getStoreId())
                .orElseThrow(() -> invalidToken("The store no longer exists."));
        if (!"ACTIVE".equals(store.getStatus())) {
            throw new BusinessException("STORE_INACTIVE",
                    "The store is inactive or suspended.", HttpStatus.FORBIDDEN);
        }

        if ("CUSTOMER".equals(user.getUserType())) {
            var customer = customers.findByCustomerIdAndStoreId(identity.customerId(), user.getStoreId())
                    .orElseThrow(() -> invalidToken("The customer profile no longer exists."));
            if (!customer.getUserId().equals(user.getUserId())) {
                throw invalidToken("The customer account scope is invalid.");
            }
        } else if (identity.customerId() != null) {
            throw invalidToken("The account scope is invalid.");
        }
        return identity;
    }

    private boolean same(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    private BusinessException invalidToken(String message) {
        return new BusinessException("INVALID_TOKEN", message, HttpStatus.UNAUTHORIZED);
    }
}
