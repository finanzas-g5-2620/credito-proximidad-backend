package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.AuthMeResponseDto;
import pe.upc.credito.application.dto.AuthResponseDto;
import pe.upc.credito.application.dto.LoginRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;
import pe.upc.credito.infrastructure.security.JwtTokenService;

@Service
public class AuthService {

    private final UserRepository users;
    private final StoreRepository stores;
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;

    public AuthService(UserRepository users,
                       StoreRepository stores,
                       CustomerRepository customers,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService tokens) {
        this.users = users;
        this.stores = stores;
        this.customers = customers;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        String identifier = request.identifier().trim();
        UserEntity user = users.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("ACCOUNT_INACTIVE",
                    "The account is inactive or blocked.", HttpStatus.FORBIDDEN);
        }

        Integer customerId = null;
        if ("SUPER_ADMIN".equals(user.getUserType())) {
            if (user.getStoreId() != null) {
                throw new BusinessException("ACCOUNT_SCOPE_INVALID",
                        "The system administrator account has an invalid store scope.", HttpStatus.FORBIDDEN);
            }
        } else {
            if (user.getStoreId() == null) {
                throw new BusinessException("ACCOUNT_SCOPE_INVALID",
                        "The account has no store assigned.", HttpStatus.FORBIDDEN);
            }
            var store = stores.findById(user.getStoreId()).orElseThrow(() ->
                    new BusinessException("STORE_NOT_FOUND", "The account store does not exist.", HttpStatus.FORBIDDEN));
            if (!"ACTIVE".equals(store.getStatus())) {
                throw new BusinessException("STORE_INACTIVE",
                        "The store is inactive or suspended.", HttpStatus.FORBIDDEN);
            }
            if ("CUSTOMER".equals(user.getUserType())) {
                var customer = customers.findByUserId(user.getUserId()).orElseThrow(() ->
                        new BusinessException("CUSTOMER_PROFILE_REQUIRED",
                                "The customer account has no credit profile.", HttpStatus.FORBIDDEN));
                if (!customer.getStoreId().equals(user.getStoreId())) {
                    throw new BusinessException("ACCOUNT_SCOPE_INVALID",
                            "The customer profile belongs to a different store.", HttpStatus.FORBIDDEN);
                }
                customerId = customer.getCustomerId();
            } else if (!"STORE_ADMIN".equals(user.getUserType())) {
                throw new BusinessException("ACCOUNT_ROLE_INVALID",
                        "The account role is not supported.", HttpStatus.FORBIDDEN);
            }
        }

        return new AuthResponseDto(tokens.issue(user, customerId), user.getUserId(),
                user.getUsername(), user.getEmail(), user.getUserType(), user.getStoreId());
    }

    @Transactional(readOnly = true)
    public AuthMeResponseDto currentIdentity(Integer userId) {
        UserEntity user = users.findById(userId).orElseThrow(() ->
                new BusinessException("ACCOUNT_NOT_FOUND", "The authenticated account no longer exists.",
                        HttpStatus.UNAUTHORIZED));
        return new AuthMeResponseDto(user.getUserId(), user.getUsername(), user.getEmail(),
                user.getUserType(), user.getStoreId());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS",
                "The username/email or password is incorrect.", HttpStatus.UNAUTHORIZED);
    }
}
