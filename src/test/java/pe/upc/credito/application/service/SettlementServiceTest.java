package pe.upc.credito.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.upc.credito.application.dto.PaymentRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.application.support.TenantContext;
import pe.upc.credito.domain.enums.PaymentMethod;
import pe.upc.credito.domain.enums.PaymentMode;
import pe.upc.credito.domain.service.FinancialMathEngine;
import pe.upc.credito.infrastructure.persistence.entity.AmortizationScheduleEntity;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.MonthlySettlementEntity;
import pe.upc.credito.infrastructure.persistence.entity.PaymentEntity;
import pe.upc.credito.infrastructure.persistence.entity.SaleEntity;
import pe.upc.credito.infrastructure.persistence.repository.AmortizationScheduleRepository;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.MonthlySettlementRepository;
import pe.upc.credito.infrastructure.persistence.repository.PaymentRepository;
import pe.upc.credito.infrastructure.persistence.repository.SaleRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock MonthlySettlementRepository settlements;
    @Mock CustomerRepository customers;
    @Mock UserRepository users;
    @Mock AmortizationScheduleRepository schedules;
    @Mock PaymentRepository payments;
    @Mock SaleRepository sales;
    @Mock SimulatedClock clock;
    @Mock TenantContext tenant;

    @Test
    void overpaymentSettlesInFullAndCreditsOnlyTheExcessToFavour() {
        MonthlySettlementEntity settlement = settlement();
        CustomerEntity customer = customer();
        when(tenant.currentRole()).thenReturn("STORE_ADMIN");
        when(tenant.currentStoreId()).thenReturn(6);
        when(settlements.findByIdAndStoreForUpdate(1, 6)).thenReturn(Optional.of(settlement));
        when(payments.findBySettlementIdAndIdempotencyKey(1, "pay-1")).thenReturn(Optional.empty());
        when(customers.findByCustomerIdAndStoreIdForUpdate(5, 6)).thenReturn(Optional.of(customer));
        when(clock.today()).thenReturn(LocalDate.of(2026, 11, 26));
        when(clock.now()).thenReturn(Instant.parse("2026-11-26T12:00:00Z"));
        when(payments.findBySettlementIdOrderByPaymentDatetimeAsc(1)).thenReturn(List.of());
        when(payments.saveAndFlush(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customers.save(any(CustomerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlements.save(any(MonthlySettlementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlements.findById(1)).thenReturn(Optional.of(settlement));
        when(schedules.findBySettlementIdOrderByInstallmentNumberAsc(1)).thenReturn(List.of());
        when(sales.findBySettlementIdAndPaymentMode(1, PaymentMode.MONTH_END.name())).thenReturn(List.of());

        var response = service().pay(1,
                new PaymentRequestDto("130.00", PaymentMethod.CASH, null), "pay-1");

        assertThat(response.amount()).isEqualTo("110.00");
        assertThat(response.received_amount()).isEqualTo("130.00");
        assertThat(response.favour_balance()).isEqualTo("25.50");
        assertThat(customer.getFavourBalance()).isEqualByComparingTo("25.50");
        assertThat(settlement.getPendingBalance()).isEqualByComparingTo("0.00");
        assertThat(settlement.getSettlementStatus()).isEqualTo("PAID");

        ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(payments).saveAndFlush(paymentCaptor.capture());
        PaymentEntity recorded = paymentCaptor.getValue();
        assertThat(recorded.getAllocatedToInterest()).isEqualByComparingTo("10.00");
        assertThat(recorded.getAllocatedToPrincipal()).isEqualByComparingTo("100.00");
        assertThat(recorded.getReceivedAmount()).isEqualByComparingTo("130.00");
        assertThat(recorded.getFavourBalanceAfter()).isEqualByComparingTo("25.50");
    }

    @Test
    void partialPaymentIsRejectedBeforeCustomerOrPaymentIsChanged() {
        when(tenant.currentRole()).thenReturn("STORE_ADMIN");
        when(tenant.currentStoreId()).thenReturn(6);
        when(settlements.findByIdAndStoreForUpdate(1, 6)).thenReturn(Optional.of(settlement()));
        when(payments.findBySettlementIdAndIdempotencyKey(1, "pay-2")).thenReturn(Optional.empty());
        when(customers.findByCustomerIdAndStoreIdForUpdate(5, 6)).thenReturn(Optional.of(customer()));
        when(clock.today()).thenReturn(LocalDate.of(2026, 11, 26));
        when(payments.findBySettlementIdOrderByPaymentDatetimeAsc(1)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().pay(1, new PaymentRequestDto("109.99", PaymentMethod.BANK_TRANSFER, null), "pay-2"));

        assertThat(error.getCode()).isEqualTo("PARTIAL_PAYMENT_REJECTED");
        verify(payments, never()).saveAndFlush(any(PaymentEntity.class));
        verify(customers, never()).save(any(CustomerEntity.class));
    }

    @Test
    void payoffPenaltyAccruesOnPrincipalAndCompensatoryInterestDue() {
        MonthlySettlementEntity settlement = settlement();
        settlement.setCutoffDate(LocalDate.of(2026, 11, 1));
        settlement.setPaymentDueDate(LocalDate.of(2026, 11, 10));
        settlement.setMonthPurchasesAmount(new BigDecimal("100.00"));
        settlement.setMonthInstallmentsAmount(new BigDecimal("0.00"));
        settlement.setCompensatoryInterestAmount(new BigDecimal("50.00"));
        CustomerEntity customer = customer();
        SaleEntity purchase = new SaleEntity();
        purchase.setSaleId(12);
        purchase.setTotalAmount(new BigDecimal("100.00"));
        purchase.setPenaltyTea(new BigDecimal("0.3600000"));
        when(tenant.currentRole()).thenReturn("CUSTOMER");
        when(tenant.currentStoreId()).thenReturn(6);
        when(tenant.currentCustomerId()).thenReturn(5);
        when(settlements.findByIdAndCustomerAndStore(1, 5, 6)).thenReturn(Optional.of(settlement));
        when(customers.findByCustomerIdAndStoreId(5, 6)).thenReturn(Optional.of(customer));
        when(clock.today()).thenReturn(LocalDate.of(2026, 12, 10));
        when(payments.findBySettlementIdOrderByPaymentDatetimeAsc(1)).thenReturn(List.of());
        when(schedules.findBySettlementIdOrderByInstallmentNumberAsc(1)).thenReturn(List.of());
        when(sales.findBySettlementIdAndPaymentMode(1, PaymentMode.MONTH_END.name())).thenReturn(List.of(purchase));
        when(sales.findById(12)).thenReturn(Optional.of(purchase));

        var quote = service().payoffQuote(1, null);

        BigDecimal payoffPenalty = new FinancialMathEngine().overduePenalty(
                new BigDecimal("150.00"), new BigDecimal("0.3600000"), 30);
        assertThat(quote.days_overdue()).isEqualTo(30);
        assertThat(quote.overdue_principal()).isEqualTo("100.00");
        assertThat(quote.compensatory_interest()).isEqualTo("50.00");
        assertThat(quote.moratorium_interest()).isEqualTo(payoffPenalty.setScale(2).toPlainString());
        assertThat(quote.total_payoff_amount()).isEqualTo(
                new BigDecimal("150.00").add(payoffPenalty).setScale(2).toPlainString());
    }

    private SettlementService service() {
        return new SettlementService(settlements, customers, users, schedules, payments, sales,
                new FinancialMathEngine(), clock, tenant);
    }

    private MonthlySettlementEntity settlement() {
        MonthlySettlementEntity settlement = new MonthlySettlementEntity();
        settlement.setSettlementId(1);
        settlement.setCustomerId(5);
        settlement.setPeriodMonth(11);
        settlement.setPeriodYear(2026);
        settlement.setCutoffDate(LocalDate.of(2026, 11, 25));
        settlement.setPaymentDueDate(LocalDate.of(2026, 11, 30));
        settlement.setMonthPurchasesAmount(new BigDecimal("80.00"));
        settlement.setMonthInstallmentsAmount(new BigDecimal("20.00"));
        settlement.setCompensatoryInterestAmount(new BigDecimal("10.00"));
        settlement.setPenaltyInterestAmount(new BigDecimal("0.00"));
        settlement.setTotalAmountDue(new BigDecimal("110.00"));
        settlement.setPaidAmount(new BigDecimal("0.00"));
        settlement.setPendingBalance(new BigDecimal("110.00"));
        settlement.setSettlementStatus("PENDING");
        return settlement;
    }

    private CustomerEntity customer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerId(5);
        customer.setStoreId(6);
        customer.setFavourBalance(new BigDecimal("5.50"));
        return customer;
    }
}
