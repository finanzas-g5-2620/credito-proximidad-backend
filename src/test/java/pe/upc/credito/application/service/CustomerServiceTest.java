package pe.upc.credito.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.upc.credito.application.dto.CustomerCreateRequestDto;
import pe.upc.credito.application.dto.CustomerUpdateRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.domain.enums.RateType;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.AmortizationScheduleRepository;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.MonthlySettlementRepository;
import pe.upc.credito.infrastructure.persistence.repository.SaleRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;
import pe.upc.credito.application.support.SimulatedClock;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customers;
    @Mock UserRepository users;
    @Mock MonthlySettlementRepository settlements;
    @Mock AmortizationScheduleRepository schedules;
    @Mock SaleRepository sales;
    @Mock SimulatedClock clock;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void createAtomicallyLinksCustomerAndBcryptAccountToTokenStore() {
        when(users.existsByUsernameIgnoreCase("maria")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("maria@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setUserId(44);
            return user;
        });
        when(customers.saveAndFlush(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity customer = invocation.getArgument(0);
            customer.setCustomerId(52);
            return customer;
        });
        when(users.findById(44)).thenAnswer(invocation -> Optional.of(user(44, 6)));

        var result = service().create(6, createRequest());

        assertEquals(52, result.id());
        assertEquals(6, result.store_id());
        assertEquals(44, result.user_id());
        assertEquals("PEN", result.credit_currency());
        assertEquals("EFFECTIVE", result.compensatory_rate_type());
        verify(users).saveAndFlush(any(UserEntity.class));
        verify(customers).saveAndFlush(any(CustomerEntity.class));
    }

    @Test
    void currentDebtIncludesSettlementsInstallmentsAndUnbilledMonthEndSales() {
        when(settlements.sumPendingBalanceByCustomer(52)).thenReturn(new BigDecimal("100.00"));
        when(schedules.sumUnbilledPrincipal(52)).thenReturn(new BigDecimal("50.00"));
        when(sales.sumUnbilledMonthEndByCustomer(52)).thenReturn(new BigDecimal("25.00"));

        assertEquals(new BigDecimal("175.00"), service().currentDebt(52));
    }

    @Test
    void activeDebtBlocksRateOrCycleChangeButKeepsContactEditable() {
        CustomerEntity customer = customer(52, 6, 44);
        when(customers.findByCustomerIdAndStoreIdForUpdate(52, 6)).thenReturn(Optional.of(customer));
        when(settlements.sumPendingBalanceByCustomer(52)).thenReturn(new BigDecimal("30.00"));
        when(schedules.sumUnbilledPrincipal(52)).thenReturn(new BigDecimal("20.00"));
        when(sales.sumUnbilledMonthEndByCustomer(52)).thenReturn(BigDecimal.ZERO);
        when(customers.save(any(CustomerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findById(44)).thenReturn(Optional.of(user(44, 6)));

        CustomerUpdateRequestDto contactOnly = updateRequest("New home", "999111222", "0.1800000");
        var updated = service().update(6, 52, contactOnly);

        assertEquals("New home", updated.home_address());
        assertEquals("999111222", updated.phone_number());
        assertEquals(new BigDecimal("0.1800000"), updated.compensatory_rate_value());
        assertEquals(new BigDecimal("250.00"), updated.credit_limit());
        verify(customers).save(customer);
    }

    @Test
    void activeDebtRejectsChangingInterestTerms() {
        CustomerEntity customer = customer(52, 6, 44);
        when(customers.findByCustomerIdAndStoreIdForUpdate(52, 6)).thenReturn(Optional.of(customer));
        when(settlements.sumPendingBalanceByCustomer(52)).thenReturn(BigDecimal.ZERO);
        when(schedules.sumUnbilledPrincipal(52)).thenReturn(new BigDecimal("50.00"));
        when(sales.sumUnbilledMonthEndByCustomer(52)).thenReturn(BigDecimal.ZERO);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().update(6, 52, updateRequest("Home", "900000000", "0.2000000")));

        assertEquals("ACTIVE_DEBT_TERMS_LOCKED", error.getCode());
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(customers, never()).save(any(CustomerEntity.class));
    }

    private CustomerService service() {
        return new CustomerService(customers, users, settlements, schedules, sales, clock, passwordEncoder);
    }

    private CustomerCreateRequestDto createRequest() {
        return new CustomerCreateRequestDto("maria", "secret123", "Maria", "Quispe",
                "maria@example.com", "Street 1", "999111222", new BigDecimal("250.00"), 6, 25, 5,
                RateType.EFFECTIVE, 30, new BigDecimal("0.1800000"), null,
                RateType.EFFECTIVE, 30, new BigDecimal("0.1200000"), null, "PEN");
    }

    private CustomerUpdateRequestDto updateRequest(String address, String phone, String compensatoryRate) {
        return new CustomerUpdateRequestDto(address, phone, new BigDecimal("250.00"), 6, 25, 5,
                RateType.EFFECTIVE, 30, new BigDecimal(compensatoryRate), null,
                RateType.EFFECTIVE, 30, new BigDecimal("0.1200000"), null, "PEN");
    }

    private CustomerEntity customer(Integer id, Integer storeId, Integer userId) {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(id);
        customer.setStoreId(storeId);
        customer.setUserId(userId);
        customer.setHomeAddress("Old home");
        customer.setPhoneNumber("999000000");
        customer.setCreditLimit(new BigDecimal("250.00"));
        customer.setMaxCreditMonths(6);
        customer.setCutoffDay(25);
        customer.setPaymentDueDay(5);
        customer.setCompensatoryRateType("EFFECTIVE");
        customer.setCompensatoryRatePeriodDays(30);
        customer.setCompensatoryCapitalizationDays(null);
        customer.setCompensatoryRateValue(new BigDecimal("0.1800000"));
        customer.setPenaltyRateType("EFFECTIVE");
        customer.setPenaltyRatePeriodDays(30);
        customer.setPenaltyCapitalizationDays(null);
        customer.setPenaltyRateValue(new BigDecimal("0.1200000"));
        customer.setCreditCurrency("PEN");
        customer.setFavourBalance(new BigDecimal("0.00"));
        return customer;
    }

    private UserEntity user(Integer id, Integer storeId) {
        UserEntity user = new UserEntity();
        user.setUserId(id);
        user.setStoreId(storeId);
        user.setUsername("maria");
        user.setEmail("maria@example.com");
        user.setFirstName("Maria");
        user.setLastName("Quispe");
        return user;
    }
}
