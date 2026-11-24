package pe.upc.credito.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.upc.credito.application.dto.StoreAdminRequestDto;
import pe.upc.credito.application.dto.StoreCreateRequestDto;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock StoreRepository stores;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void storeAndAdministratorAreCreatedWithScopedHashedAccount() {
        when(stores.existsByTaxId("20512345678")).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("pepe.admin")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("pepe@example.com")).thenReturn(false);
        when(stores.saveAndFlush(any(StoreEntity.class))).thenAnswer(invocation -> {
            StoreEntity store = invocation.getArgument(0);
            store.setStoreId(10);
            return store;
        });
        when(passwordEncoder.encode("secret123")).thenReturn("bcrypt-hash");

        var result = service().create(new StoreCreateRequestDto(
                "Bodega Don Pepe", "20512345678", "Av. Lima 10", null,
                new StoreAdminRequestDto("Jose", "Perez", "pepe@example.com", "pepe.admin", "secret123")));

        assertEquals(10, result.id());
        assertEquals("ACTIVE", result.status());
        ArgumentCaptor<UserEntity> account = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(account.capture());
        assertEquals(10, account.getValue().getStoreId());
        assertEquals("STORE_ADMIN", account.getValue().getUserType());
        assertEquals("bcrypt-hash", account.getValue().getPasswordHash());
    }

    private StoreService service() {
        return new StoreService(stores, users, passwordEncoder);
    }
}
