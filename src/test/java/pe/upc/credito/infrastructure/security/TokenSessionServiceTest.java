package pe.upc.credito.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.support.AuthPrincipal;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenSessionServiceTest {

    @Mock JwtTokenService tokens;
    @Mock UserRepository users;
    @Mock StoreRepository stores;
    @Mock CustomerRepository customers;

    @Test
    void everyRequestRechecksUserAndStoreStatus() {
        AuthPrincipal principal = new AuthPrincipal(2, "owner", "STORE_ADMIN", 7, null);
        UserEntity user = new UserEntity();
        user.setUserId(2);
        user.setUsername("owner");
        user.setUserType("STORE_ADMIN");
        user.setStoreId(7);
        user.setStatus("ACTIVE");
        StoreEntity store = new StoreEntity();
        store.setStatus("SUSPENDED");
        when(tokens.parse("token")).thenReturn(principal);
        when(users.findById(2)).thenReturn(Optional.of(user));
        when(stores.findById(7)).thenReturn(Optional.of(store));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().authenticate("token"));

        assertEquals("STORE_INACTIVE", error.getCode());
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void roleChangeRevokesPreviouslyIssuedToken() {
        AuthPrincipal principal = new AuthPrincipal(2, "owner", "STORE_ADMIN", 7, null);
        UserEntity user = new UserEntity();
        user.setUserId(2);
        user.setUsername("owner");
        user.setUserType("CUSTOMER");
        user.setStoreId(7);
        user.setStatus("ACTIVE");
        when(tokens.parse("token")).thenReturn(principal);
        when(users.findById(2)).thenReturn(Optional.of(user));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().authenticate("token"));

        assertEquals("INVALID_TOKEN", error.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
    }

    private TokenSessionService service() {
        return new TokenSessionService(tokens, users, stores, customers);
    }
}
