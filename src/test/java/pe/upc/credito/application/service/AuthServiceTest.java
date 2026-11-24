package pe.upc.credito.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.upc.credito.application.dto.LoginRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;
import pe.upc.credito.infrastructure.security.JwtTokenService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock StoreRepository stores;
    @Mock CustomerRepository customers;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenService tokens;

    @Test
    void loginByEmailReturnsTokenAndStoreAdminIdentity() {
        UserEntity user = user(8, 3, "STORE_ADMIN", "ACTIVE");
        StoreEntity store = new StoreEntity();
        store.setStatus("ACTIVE");
        when(users.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin@example.com", "admin@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(true);
        when(stores.findById(3)).thenReturn(Optional.of(store));
        when(tokens.issue(user, null)).thenReturn("signed-token");

        var result = service().login(new LoginRequestDto(" admin@example.com ", "secret"));

        assertEquals("signed-token", result.token());
        assertEquals(8, result.userId());
        assertEquals("admin@example.com", result.email());
        assertEquals("STORE_ADMIN", result.userType());
        assertEquals(3, result.storeId());
        verify(customers, never()).findByUserId(any());
    }

    @Test
    void wrongPasswordUsesGenericUnauthorizedResponse() {
        UserEntity user = user(8, 3, "STORE_ADMIN", "ACTIVE");
        when(users.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().login(new LoginRequestDto("admin", "wrong")));

        assertEquals("INVALID_CREDENTIALS", error.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verify(stores, never()).findById(any());
    }

    private AuthService service() {
        return new AuthService(users, stores, customers, passwordEncoder, tokens);
    }

    private UserEntity user(Integer id, Integer storeId, String role, String status) {
        UserEntity user = new UserEntity();
        user.setUserId(id);
        user.setStoreId(storeId);
        user.setUsername("admin");
        user.setEmail("admin@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setUserType(role);
        user.setStatus(status);
        return user;
    }
}
