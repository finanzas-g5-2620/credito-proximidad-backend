package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.StoreAdminRequestDto;
import pe.upc.credito.application.dto.StoreCreateRequestDto;
import pe.upc.credito.application.dto.StorePatchRequestDto;
import pe.upc.credito.application.dto.StoreResponseDto;
import pe.upc.credito.application.dto.StoreUpdateRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.exception.ResourceNotFoundException;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.time.Instant;
import java.util.List;

@Service
public class StoreService {

    private final StoreRepository stores;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public StoreService(StoreRepository stores, UserRepository users, PasswordEncoder passwordEncoder) {
        this.stores = stores;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StoreResponseDto> list() {
        return stores.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public StoreResponseDto get(Integer id) {
        return toDto(requireStore(id));
    }

    @Transactional
    public StoreResponseDto create(StoreCreateRequestDto request) {
        validateUniqueStoreAndAdmin(request.tax_id(), request.admin().username(), request.admin().email());
        StoreEntity store = newStore(request.business_name(), request.tax_id(), request.address(), request.phone_number());
        StoreEntity saved = stores.saveAndFlush(store);
        createUser(saved.getStoreId(), request.admin(), "STORE_ADMIN");
        return toDto(saved);
    }

    @Transactional
    public StoreResponseDto update(Integer id, StoreUpdateRequestDto request) {
        StoreEntity store = requireStore(id);
        ensureTaxIdAvailable(request.tax_id(), id);
        store.setBusinessName(request.business_name().trim());
        store.setTaxId(request.tax_id().trim());
        store.setAddress(request.address().trim());
        store.setPhoneNumber(normalizeNullable(request.phone_number()));
        return toDto(stores.save(store));
    }

    @Transactional
    public StoreResponseDto patch(Integer id, StorePatchRequestDto request) {
        StoreEntity store = requireStore(id);
        if (request.business_name() == null && request.tax_id() == null
                && request.address() == null && request.phone_number() == null && request.status() == null) {
            throw new BusinessException("EMPTY_PATCH", "At least one store field must be provided.", HttpStatus.BAD_REQUEST);
        }
        if (request.business_name() != null) {
            if (request.business_name().isBlank()) {
                throw new BusinessException("INVALID_BUSINESS_NAME", "Business name cannot be blank.", HttpStatus.BAD_REQUEST);
            }
            store.setBusinessName(request.business_name().trim());
        }
        if (request.tax_id() != null) {
            ensureTaxIdAvailable(request.tax_id(), id);
            store.setTaxId(request.tax_id().trim());
        }
        if (request.address() != null) {
            if (request.address().isBlank()) {
                throw new BusinessException("INVALID_ADDRESS", "Address cannot be blank.", HttpStatus.BAD_REQUEST);
            }
            store.setAddress(request.address().trim());
        }
        if (request.phone_number() != null) {
            store.setPhoneNumber(normalizeNullable(request.phone_number()));
        }
        if (request.status() != null) {
            store.setStatus(request.status());
        }
        return toDto(stores.save(store));
    }

    private StoreEntity newStore(String businessName, String taxId, String address, String phoneNumber) {
        StoreEntity store = new StoreEntity();
        store.setBusinessName(businessName.trim());
        store.setTaxId(taxId.trim());
        store.setAddress(address.trim());
        store.setPhoneNumber(normalizeNullable(phoneNumber));
        store.setStatus("ACTIVE");
        return store;
    }

    private void validateUniqueStoreAndAdmin(String taxId, String username, String email) {
        if (stores.existsByTaxId(taxId.trim())) {
            throw new BusinessException("TAX_ID_ALREADY_EXISTS", "A store with this tax ID already exists.", HttpStatus.CONFLICT);
        }
        ensureAccountAvailable(username, email);
    }

    private void ensureTaxIdAvailable(String taxId, Integer storeId) {
        if (stores.existsByTaxIdAndStoreIdNot(taxId.trim(), storeId)) {
            throw new BusinessException("TAX_ID_ALREADY_EXISTS", "A store with this tax ID already exists.", HttpStatus.CONFLICT);
        }
    }

    private void ensureAccountAvailable(String username, String email) {
        if (users.existsByUsernameIgnoreCase(username.trim())) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "A user with this username already exists.", HttpStatus.CONFLICT);
        }
        if (users.existsByEmailIgnoreCase(email.trim())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "A user with this email already exists.", HttpStatus.CONFLICT);
        }
    }

    private void createUser(Integer storeId, StoreAdminRequestDto request, String role) {
        createUser(storeId, request.first_name(), request.last_name(), request.email(),
                request.username(), request.password(), role);
    }

    private void createUser(Integer storeId,
                            String firstName,
                            String lastName,
                            String email,
                            String username,
                            String password,
                            String role) {
        UserEntity user = new UserEntity();
        user.setStoreId(storeId);
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setEmail(email.trim());
        user.setUserType(role);
        user.setStatus("ACTIVE");
        users.save(user);
    }

    private StoreEntity requireStore(Integer id) {
        return stores.findById(id).orElseThrow(() -> new ResourceNotFoundException("Store not found: " + id));
    }

    private StoreResponseDto toDto(StoreEntity store) {
        Instant createdAt = store.getCreatedAt() == null ? Instant.now() : store.getCreatedAt();
        return new StoreResponseDto(store.getStoreId(), store.getBusinessName(), store.getTaxId(),
                store.getAddress(), store.getPhoneNumber(), createdAt.toString(), store.getStatus());
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
