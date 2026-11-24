package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.upc.credito.application.dto.SimulateScheduleRequestDto;
import pe.upc.credito.application.dto.SimulateScheduleResponseDto;
import pe.upc.credito.application.dto.SimulatedInstallmentDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.exception.ResourceNotFoundException;
import pe.upc.credito.application.support.Amounts;
import pe.upc.credito.application.support.BillingCalendar;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.application.support.TenantContext;
import pe.upc.credito.domain.enums.RateType;
import pe.upc.credito.domain.model.SimulatedSchedule;
import pe.upc.credito.domain.service.FinancialMathEngine;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Caso de uso: simular un cronograma francés (POST /finance/simulate). */
@Service
public class FinanceService {

    private final FinancialMathEngine engine;
    private final SimulatedClock clock;
    private final CustomerRepository customers;
    private final TenantContext tenant;

    public FinanceService(FinancialMathEngine engine,
                          SimulatedClock clock,
                          CustomerRepository customers,
                          TenantContext tenant) {
        this.engine = engine;
        this.clock = clock;
        this.customers = customers;
        this.tenant = tenant;
    }

    public SimulateScheduleResponseDto simulate(SimulateScheduleRequestDto request) {
        BigDecimal capital = Amounts.money(request.initial_capital());
        BigDecimal rate = request.rate_value() == null ? null : new BigDecimal(request.rate_value());
        RateType type = request.rate_type();
        int ratePeriodDays = request.rate_period_days() == null
                ? FinancialMathEngine.COMMERCIAL_YEAR_DAYS
                : request.rate_period_days();
        Integer compoundingDays = request.compounding_days();

        LocalDate firstDueDate;
        Integer paymentDay = null;
        LocalDate contractualCutoff = null;
        int graceDays = request.grace_days() == null ? 0 : request.grace_days();
        if (request.customer_id() != null) {
            CustomerEntity customer = customers
                    .findByCustomerIdAndStoreId(request.customer_id(), tenant.currentStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found: " + request.customer_id()));
            if ("CUSTOMER".equals(tenant.currentRole())
                    && !request.customer_id().equals(tenant.currentCustomerId())) {
                throw new BusinessException("CUSTOMER_SCOPE_FORBIDDEN",
                        "The customer does not belong to the authenticated account", HttpStatus.FORBIDDEN);
            }
            type = parseRateType(customer.getCompensatoryRateType());
            rate = customer.getCompensatoryRateValue();
            ratePeriodDays = customer.getCompensatoryRatePeriodDays();
            compoundingDays = customer.getCompensatoryCapitalizationDays();
            if (request.total_installments() > customer.getMaxCreditMonths()) {
                throw new BusinessException("INSTALLMENTS_OUT_OF_RANGE",
                        "The installment count exceeds the customer's credit term",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            LocalDate purchaseDate = clock.today();
            LocalDate cutoff = BillingCalendar.cutoffForPurchase(purchaseDate, customer.getCutoffDay());
            contractualCutoff = cutoff;
            graceDays = engine.days30E360(purchaseDate, cutoff);
            paymentDay = customer.getPaymentDueDay();
            firstDueDate = BillingCalendar.paymentDueAfter(cutoff, paymentDay);
        } else if (request.first_due_date() == null || request.first_due_date().isBlank()) {
            firstDueDate = clock.today();
        } else {
            try {
                firstDueDate = LocalDate.parse(request.first_due_date());
            } catch (DateTimeParseException ex) {
                throw new BusinessException("INVALID_DATE",
                        "Invalid first due date: " + request.first_due_date(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        if (rate == null || type == null) {
            throw new BusinessException("RATE_REQUIRED",
                    "A standalone simulation requires a rate type and value", HttpStatus.BAD_REQUEST);
        }

        SimulatedSchedule schedule;
        try {
            schedule = engine.simulate(
                    capital,
                    rate,
                    type,
                    ratePeriodDays,
                    compoundingDays,
                    request.total_installments(),
                    graceDays,
                    firstDueDate,
                    paymentDay == null ? firstDueDate.getDayOfMonth() : paymentDay);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_FINANCIAL_INPUT", ex.getMessage(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        LocalDate graceEnd = contractualCutoff;
        return new SimulateScheduleResponseDto(
                Amounts.money(schedule.initialCapital()),
                Amounts.money(schedule.capitalizedCapital()),
                schedule.graceDays(),
                schedule.totalInstallments(),
                Amounts.rate(schedule.rates().tea()),
                Amounts.rate(schedule.rates().ted()),
                Amounts.rate(schedule.rates().tep30()),
                Amounts.money(schedule.regularInstallment()),
                Amounts.money(schedule.totalInterest()),
                Amounts.money(schedule.totalPayment()),
                schedule.installments().stream()
                        .map(i -> new SimulatedInstallmentDto(
                                i.installmentNumber(),
                                (i.gracePeriod() && graceEnd != null ? graceEnd : i.dueDate()).toString(),
                                Amounts.money(i.paymentAmount()),
                                Amounts.money(i.principalAmount()),
                                Amounts.money(i.interestAmount()),
                                Amounts.money(i.balanceAmount()),
                                i.gracePeriod()))
                        .toList());
    }

    private RateType parseRateType(String type) {
        try {
            return RateType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("INVALID_RATE_TYPE", "Invalid interest rate type",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
