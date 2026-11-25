package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.PaymentRequestDto;
import pe.upc.credito.application.dto.PaymentResponseDto;
import pe.upc.credito.application.dto.PayoffQuoteResponseDto;
import pe.upc.credito.application.dto.RunCutoffResponseDto;
import pe.upc.credito.application.dto.SettlementResponseDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.exception.ResourceNotFoundException;
import pe.upc.credito.application.support.Amounts;
import pe.upc.credito.application.support.ApiCodes;
import pe.upc.credito.application.support.BillingCalendar;
import pe.upc.credito.application.support.Names;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.application.support.TenantContext;
import pe.upc.credito.domain.enums.InstallmentStatus;
import pe.upc.credito.domain.enums.PaymentMode;
import pe.upc.credito.domain.enums.SaleStatus;
import pe.upc.credito.domain.enums.SettlementStatus;
import pe.upc.credito.domain.service.FinancialMathEngine;
import pe.upc.credito.infrastructure.persistence.entity.AmortizationScheduleEntity;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.MonthlySettlementEntity;
import pe.upc.credito.infrastructure.persistence.entity.PaymentEntity;
import pe.upc.credito.infrastructure.persistence.entity.SaleEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.AmortizationScheduleRepository;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.MonthlySettlementRepository;
import pe.upc.credito.infrastructure.persistence.repository.PaymentRepository;
import pe.upc.credito.infrastructure.persistence.repository.SaleRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Billing, payoff quotes and full payment with statutory imputation. */
@Service
public class SettlementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
    private static final String STORE_ADMIN = "STORE_ADMIN";
    private static final String CUSTOMER = "CUSTOMER";

    private final MonthlySettlementRepository settlements;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final AmortizationScheduleRepository schedules;
    private final PaymentRepository payments;
    private final SaleRepository sales;
    private final FinancialMathEngine engine;
    private final SimulatedClock clock;
    private final TenantContext tenant;

    public SettlementService(MonthlySettlementRepository settlements,
                             CustomerRepository customers,
                             UserRepository users,
                             AmortizationScheduleRepository schedules,
                             PaymentRepository payments,
                             SaleRepository sales,
                             FinancialMathEngine engine,
                             SimulatedClock clock,
                             TenantContext tenant) {
        this.settlements = settlements;
        this.customers = customers;
        this.users = users;
        this.schedules = schedules;
        this.payments = payments;
        this.sales = sales;
        this.engine = engine;
        this.clock = clock;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public List<SettlementResponseDto> list() {
        Integer storeId = tenant.currentStoreId();
        List<MonthlySettlementEntity> rows = CUSTOMER.equals(tenant.currentRole())
                ? settlements.findByCustomerIdAndStoreIdOrderByCutoffDateDesc(
                        tenant.currentCustomerId(), storeId)
                : settlements.findByStore(storeId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = rows.stream().map(MonthlySettlementEntity::getSettlementId).toList();
        Map<Integer, List<PaymentEntity>> history = payments.findBySettlementIdInOrderByPaymentDatetimeAsc(ids)
                .stream().collect(Collectors.groupingBy(PaymentEntity::getSettlementId));
        Map<Integer, CustomerEntity> customerMap = customers.findAllById(
                        rows.stream().map(MonthlySettlementEntity::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(CustomerEntity::getCustomerId, Function.identity()));
        Map<Integer, UserEntity> userMap = users.findAllById(customerMap.values().stream()
                        .map(CustomerEntity::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        return rows.stream().map(row -> {
            CustomerEntity customer = customerMap.get(row.getCustomerId());
            return toDto(row, customer,
                    customer == null ? null : userMap.get(customer.getUserId()),
                    history.getOrDefault(row.getSettlementId(), List.of()), null);
        }).toList();
    }

    @Transactional(readOnly = true)
    public SettlementResponseDto get(Integer settlementId) {
        MonthlySettlementEntity settlement = requireSettlement(settlementId, false);
        CustomerEntity customer = customers.findById(settlement.getCustomerId()).orElse(null);
        UserEntity user = customer == null ? null : users.findById(customer.getUserId()).orElse(null);
        return toDto(settlement, customer, user,
                payments.findBySettlementIdOrderByPaymentDatetimeAsc(settlementId),
                sales.findBySettlementIdAndPaymentMode(settlementId, PaymentMode.MONTH_END.name()).stream()
                        .sorted(Comparator.comparing(SaleEntity::getSaleDatetime).thenComparing(SaleEntity::getSaleId))
                        .map(this::toPurchase)
                        .toList());
    }

    @Transactional(readOnly = true)
    public PayoffQuoteResponseDto payoffQuote(Integer settlementId, String targetDate) {
        MonthlySettlementEntity settlement = requireSettlement(settlementId, false);
        if (isPaid(settlement)) {
            throw new BusinessException("SETTLEMENT_ALREADY_PAID", "The settlement is already paid",
                    HttpStatus.CONFLICT);
        }
        CustomerEntity customer = customers.findByCustomerIdAndStoreId(
                        settlement.getCustomerId(), tenant.currentStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        LocalDate calculationDate = parseDate(targetDate, clock.today());
        List<PaymentEntity> history = payments.findBySettlementIdOrderByPaymentDatetimeAsc(settlementId);
        Quote quote = quote(settlement, customer, calculationDate, history);
        return new PayoffQuoteResponseDto(settlementId, calculationDate.toString(), quote.daysOverdue(),
                Amounts.money(quote.principal()), Amounts.money(quote.interest()),
                Amounts.money(quote.moratorium()), Amounts.money(quote.total()));
    }

    @Transactional
    public PaymentResponseDto pay(Integer settlementId, PaymentRequestDto request, String rawIdempotencyKey) {
        requireStoreAdmin();
        Integer storeId = tenant.currentStoreId();
        String idempotencyKey = normalizeKey(rawIdempotencyKey);
        if (idempotencyKey == null) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required for payment registration", HttpStatus.BAD_REQUEST);
        }

        MonthlySettlementEntity settlement = settlements.findByIdAndStoreForUpdate(settlementId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found: " + settlementId));
        BigDecimal amountReceived = Amounts.money(request.amount());
        String reference = normalizeReference(request.reference_number());
        var previous = payments.findBySettlementIdAndIdempotencyKey(settlementId, idempotencyKey);
        if (previous.isPresent()) {
            PaymentEntity payment = previous.get();
            if (payment.getReceivedAmount().compareTo(amountReceived) != 0
                    || !payment.getPaymentMethod().equals(request.payment_method().name())
                    || !Objects.equals(payment.getReferenceNumber(), reference)) {
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used with a different payment", HttpStatus.CONFLICT);
            }
            return toPaymentDto(payment);
        }
        if (isPaid(settlement)) {
            throw new BusinessException("SETTLEMENT_ALREADY_PAID", "The settlement is already paid",
                    HttpStatus.CONFLICT);
        }
        if (amountReceived.signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "The amount must be greater than zero",
                    HttpStatus.BAD_REQUEST);
        }

        CustomerEntity customer = customers.findByCustomerIdAndStoreIdForUpdate(
                        settlement.getCustomerId(), storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        LocalDate today = clock.today();
        List<PaymentEntity> history = payments.findBySettlementIdOrderByPaymentDatetimeAsc(settlementId);
        Quote quote = quote(settlement, customer, today, history);
        BigDecimal totalDue = quote.total();
        if (totalDue.signum() <= 0) {
            throw new BusinessException("SETTLEMENT_ALREADY_PAID", "The settlement has no balance due",
                    HttpStatus.CONFLICT);
        }
        if (amountReceived.compareTo(totalDue) < 0) {
            throw new BusinessException("PARTIAL_PAYMENT_REJECTED",
                    "The received amount must cover the full amount due: PEN " + Amounts.money(totalDue),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        BigDecimal applied = totalDue;
        BigDecimal surplus = amountReceived.subtract(applied).setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal available = applied;
        BigDecimal toPenalty = available.min(quote.moratorium());
        available = available.subtract(toPenalty);
        BigDecimal toInterest = available.min(quote.interest());
        available = available.subtract(toInterest);
        BigDecimal toPrincipal = available.min(quote.principal());
        available = available.subtract(toPrincipal);
        if (available.signum() != 0) {
            throw new BusinessException("INVALID_IMPUTATION", "Payment could not be fully allocated",
                    HttpStatus.CONFLICT);
        }

        BigDecimal favour = safe(customer.getFavourBalance()).add(surplus)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        PaymentEntity payment = new PaymentEntity();
        payment.setSettlementId(settlementId);
        payment.setPaymentDatetime(clock.now());
        payment.setAmountPaid(applied);
        payment.setReceivedAmount(amountReceived);
        payment.setFavourBalanceAfter(favour);
        payment.setCalculatedOverdueDays(quote.daysOverdue());
        payment.setAllocatedToPenalty(toPenalty);
        payment.setAllocatedToInterest(toInterest);
        payment.setAllocatedToPrincipal(toPrincipal);
        payment.setPaymentMethod(request.payment_method().name());
        payment.setReferenceNumber(reference);
        payment.setIdempotencyKey(idempotencyKey);
        payments.saveAndFlush(payment);

        customer.setFavourBalance(favour);
        customers.save(customer);

        BigDecimal previousPaid = safe(settlement.getPaidAmount());
        BigDecimal paidPenaltyBefore = sumPayments(history, PaymentEntity::getAllocatedToPenalty);
        settlement.setPenaltyInterestAmount(quote.moratorium().add(paidPenaltyBefore)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
        settlement.setPaidAmount(previousPaid.add(applied).setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
        settlement.setTotalAmountDue(previousPaid.add(totalDue).setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
        settlement.setPendingBalance(ZERO);
        settlement.setSettlementStatus(SettlementStatus.PAID.name());
        settlements.save(settlement);

        List<AmortizationScheduleEntity> billedRows =
                schedules.findBySettlementIdOrderByInstallmentNumberAsc(settlementId);
        billedRows.forEach(row -> row.setInstallmentStatus(InstallmentStatus.PAID.name()));
        schedules.saveAll(billedRows);
        completeSalesIfPaid(billedRows, settlementId);
        return toPaymentDto(payment);
    }

    @Transactional
    public RunCutoffResponseDto runCutoff() {
        requireStoreAdmin();
        return runCutoffForStore(tenant.currentStoreId());
    }

    /** Internal scheduler entry point; web callers must use the authorized wrapper above. */
    @Transactional
    public RunCutoffResponseDto runCutoffForStore(Integer storeId) {
        if (storeId == null || storeId <= 0) {
            throw new BusinessException("STORE_CONTEXT_REQUIRED", "A valid store is required for cutoff",
                    HttpStatus.BAD_REQUEST);
        }
        LocalDate today = clock.today();
        LocalDate closedThroughDate = today.minusDays(1);
        List<AmortizationScheduleEntity> candidateRows =
                schedules.findUnbilledByStore(storeId, closedThroughDate);
        List<SaleEntity> candidatePurchases =
                sales.findUnbilledMonthEndByStore(storeId, closedThroughDate);

        Map<Integer, SaleEntity> salesById = sales.findAllById(candidateRows.stream()
                        .map(AmortizationScheduleEntity::getSaleId).distinct().toList())
                .stream().collect(Collectors.toMap(SaleEntity::getSaleId, Function.identity()));
        Set<CutoffKey> keys = new java.util.HashSet<>();
        for (AmortizationScheduleEntity row : candidateRows) {
            SaleEntity sale = salesById.get(row.getSaleId());
            if (sale != null && row.getBillingCutoffDate() != null) {
                keys.add(new CutoffKey(sale.getCustomerId(), row.getBillingCutoffDate()));
            }
        }
        candidatePurchases.stream()
                .filter(sale -> sale.getBillingCutoffDate() != null)
                .forEach(sale -> keys.add(new CutoffKey(sale.getCustomerId(), sale.getBillingCutoffDate())));
        List<CutoffKey> orderedKeys = keys.stream()
                .sorted(Comparator.comparing(CutoffKey::cutoffDate).thenComparing(CutoffKey::customerId))
                .toList();

        int generated = 0;
        for (CutoffKey key : orderedKeys) {
            CustomerEntity customer = customers.findByCustomerIdAndStoreIdForUpdate(key.customerId(), storeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + key.customerId()));
            YearMonth period = YearMonth.from(key.cutoffDate());
            if (settlements.findByCustomerIdAndPeriodYearAndPeriodMonth(
                    customer.getCustomerId(), period.getYear(), period.getMonthValue()).isPresent()) {
                continue;
            }
            // Re-read after taking the customer lock. A sale committed while this
            // cutoff waited for the lock must be included in the same run.
            List<AmortizationScheduleEntity> rows = schedules.findUnbilledByCustomerAndStore(
                            customer.getCustomerId(), storeId, closedThroughDate).stream()
                    .filter(row -> key.cutoffDate().equals(row.getBillingCutoffDate()))
                    .toList();
            List<SaleEntity> purchases = sales.findUnbilledMonthEndByCustomerAndStore(
                            customer.getCustomerId(), storeId, closedThroughDate).stream()
                    .filter(sale -> key.cutoffDate().equals(sale.getBillingCutoffDate()))
                    .toList();
            if (rows.isEmpty() && purchases.isEmpty()) {
                continue;
            }

            BigDecimal installmentPrincipal = sumRows(rows, AmortizationScheduleEntity::getPrincipalAmount);
            BigDecimal purchasePrincipal = purchases.stream().map(SaleEntity::getTotalAmount)
                    .reduce(ZERO, BigDecimal::add).setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal interest = sumRows(rows, AmortizationScheduleEntity::getCompensatoryInterest)
                    .add(purchases.stream().map(sale -> safe(sale.getMonthEndInterest()))
                            .reduce(ZERO, BigDecimal::add));
            BigDecimal principal = purchasePrincipal.add(installmentPrincipal)
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
            LocalDate dueDate = BillingCalendar.paymentDueAfter(key.cutoffDate(), customer.getCutoffDay(), customer.getPaymentDueDay());

            // Mora remains dynamic until payoff. This avoids storing an accrual
            // date that could be mistaken for the contractual statement cutoff.
            BigDecimal total = principal.add(interest).setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
            boolean overdue = today.isAfter(dueDate);
            MonthlySettlementEntity settlement = new MonthlySettlementEntity();
            settlement.setCustomerId(customer.getCustomerId());
            settlement.setPeriodMonth(period.getMonthValue());
            settlement.setPeriodYear(period.getYear());
            settlement.setCutoffDate(key.cutoffDate());
            settlement.setPaymentDueDate(dueDate);
            settlement.setMonthPurchasesAmount(purchasePrincipal);
            settlement.setMonthInstallmentsAmount(installmentPrincipal);
            settlement.setCompensatoryInterestAmount(interest);
            settlement.setPenaltyInterestAmount(ZERO);
            settlement.setTotalAmountDue(total);
            settlement.setPaidAmount(ZERO);
            settlement.setPendingBalance(total);
            settlement.setSettlementStatus(overdue
                    ? SettlementStatus.OVERDUE.name() : SettlementStatus.PENDING.name());
            settlements.saveAndFlush(settlement);

            for (AmortizationScheduleEntity row : rows) {
                row.setSettlementId(settlement.getSettlementId());
                row.setInstallmentStatus(overdue
                        ? InstallmentStatus.OVERDUE.name() : InstallmentStatus.BILLED.name());
            }
            schedules.saveAll(rows);
            for (SaleEntity purchase : purchases) {
                purchase.setSettlementId(settlement.getSettlementId());
                sales.save(purchase);
            }
            generated++;
        }
        return new RunCutoffResponseDto(
                "Billing cutoff completed for " + closedThroughDate + ": " + generated
                        + " settlements generated",
                generated);
    }

    private record CutoffKey(Integer customerId, LocalDate cutoffDate) { }

    private record Quote(int daysOverdue,
                         BigDecimal principal,
                         BigDecimal interest,
                         BigDecimal moratorium,
                         LocalDate calculationDate) {
        BigDecimal total() {
            return principal.add(interest).add(moratorium)
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }

    private Quote quote(MonthlySettlementEntity settlement,
                        CustomerEntity customer,
                        LocalDate calculationDate,
                        List<PaymentEntity> history) {
        BigDecimal paidPrincipal = sumPayments(history, PaymentEntity::getAllocatedToPrincipal);
        BigDecimal paidInterest = sumPayments(history, PaymentEntity::getAllocatedToInterest);
        BigDecimal paidPenalty = sumPayments(history, PaymentEntity::getAllocatedToPenalty);
        BigDecimal principal = nonNegative(safe(settlement.getMonthPurchasesAmount())
                .add(safe(settlement.getMonthInstallmentsAmount())).subtract(paidPrincipal));
        BigDecimal interest = nonNegative(safe(settlement.getCompensatoryInterestAmount()).subtract(paidInterest));
        BigDecimal storedPenalty = nonNegative(safe(settlement.getPenaltyInterestAmount()).subtract(paidPenalty));

        LocalDate dueDate = settlement.getPaymentDueDate();
        int daysOverdue = calculationDate.isAfter(dueDate) ? Math.max(0, engine.days30E360(dueDate, calculationDate)) : 0;
        LocalDate lastAccrual = history.stream()
                .map(PaymentEntity::getPaymentDatetime).filter(Objects::nonNull)
                .max(Instant::compareTo)
                .map(instant -> instant.atZone(ZoneId.of("America/Lima")).toLocalDate())
                .orElse(settlement.getCutoffDate());
        LocalDate accrualFrom = lastAccrual.isAfter(dueDate) ? lastAccrual : dueDate;
        int additionalDays = calculationDate.isAfter(accrualFrom)
                ? Math.max(0, engine.days30E360(accrualFrom, calculationDate)) : 0;
        BigDecimal additionalPenalty = additionalPenalty(
                settlement, principal.add(interest), paidPrincipal.add(paidInterest), customer, additionalDays);
        BigDecimal moratorium = storedPenalty.add(additionalPenalty)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        return new Quote(daysOverdue, principal, interest, moratorium, calculationDate);
    }

    private BigDecimal additionalPenalty(MonthlySettlementEntity settlement,
                                        BigDecimal overdueAmount,
                                        BigDecimal paidAmount,
                                        CustomerEntity customer,
                                        int days) {
        if (days <= 0 || overdueAmount.signum() <= 0) {
            return ZERO;
        }
        List<AmortizationScheduleEntity> rows =
                schedules.findBySettlementIdOrderByInstallmentNumberAsc(settlement.getSettlementId());
        List<SaleEntity> monthEnd = sales.findBySettlementIdAndPaymentMode(
                settlement.getSettlementId(), PaymentMode.MONTH_END.name());
        Map<Integer, BigDecimal> amountBySale = new LinkedHashMap<>();
        rows.stream().filter(row -> row.getInstallmentNumber() != null && row.getInstallmentNumber() > 0)
                .forEach(row -> amountBySale.merge(row.getSaleId(),
                        safe(row.getPrincipalAmount()).add(safe(row.getCompensatoryInterest())), BigDecimal::add));
        monthEnd.forEach(sale -> amountBySale.merge(
                sale.getSaleId(), safe(sale.getTotalAmount()).add(safe(sale.getMonthEndInterest())), BigDecimal::add));

        if (amountBySale.isEmpty()) {
            BigDecimal fallbackRate = normalizeCustomerPenaltyRate(customer);
            return engine.overduePenalty(overdueAmount, fallbackRate, days);
        }
        if (amountBySale.size() == 1) {
            amountBySale.replaceAll((saleId, amount) -> overdueAmount.add(paidAmount));
        }
        BigDecimal paidRemaining = paidAmount;
        BigDecimal unallocated = overdueAmount;
        BigDecimal totalPenalty = ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : amountBySale.entrySet()) {
            BigDecimal saleDue = entry.getValue();
            BigDecimal salePaid = saleDue.min(paidRemaining);
            paidRemaining = paidRemaining.subtract(salePaid).max(ZERO);
            BigDecimal remaining = saleDue.subtract(salePaid).max(ZERO).min(unallocated);
            unallocated = unallocated.subtract(remaining);
            SaleEntity sale = sales.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + entry.getKey()));
            BigDecimal rate = sale.getPenaltyTea();
            if (rate == null) {
                throw new BusinessException("MISSING_RATE_SNAPSHOT",
                        "The sale has no contractual penalty rate snapshot", HttpStatus.CONFLICT);
            }
            totalPenalty = totalPenalty.add(engine.overduePenalty(remaining, rate, days));
        }
        if (unallocated.signum() > 0) {
            totalPenalty = totalPenalty.add(engine.overduePenalty(
                    unallocated, normalizeCustomerPenaltyRate(customer), days));
        }
        return totalPenalty.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeCustomerPenaltyRate(CustomerEntity customer) {
        try {
            return engine.normalize(customer.getPenaltyRateValue(),
                    pe.upc.credito.domain.enums.RateType.valueOf(customer.getPenaltyRateType()),
                    customer.getPenaltyRatePeriodDays(),
                    "NOMINAL".equals(customer.getPenaltyRateType())
                            ? customer.getPenaltyCapitalizationDays() : null).tea();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("INVALID_CUSTOMER_RATE", "The customer's penalty terms are invalid",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private SettlementResponseDto.Purchase toPurchase(SaleEntity sale) {
        BigDecimal principal = safe(sale.getTotalAmount());
        BigDecimal interest = safe(sale.getMonthEndInterest());
        return new SettlementResponseDto.Purchase(sale.getSaleId(), ApiCodes.sale(sale.getSaleId()),
                sale.getSaleDatetime().atZone(ZoneId.of("America/Lima")).toLocalDate().toString(),
                Amounts.money(principal), Amounts.money(interest), Amounts.money(principal.add(interest)));
    }

    private SettlementResponseDto toDto(MonthlySettlementEntity settlement,
                                        CustomerEntity customer,
                                        UserEntity user,
                                        List<PaymentEntity> history,
                                        List<SettlementResponseDto.Purchase> purchases) {
        boolean paid = isPaid(settlement);
        Quote quote = paid ? null : quote(settlement, customer, clock.today(), history);
        String status = paid ? SettlementStatus.PAID.name()
                : (clock.today().isAfter(settlement.getPaymentDueDate())
                    ? SettlementStatus.OVERDUE.name() : SettlementStatus.PENDING.name());
        BigDecimal penalty = paid ? safe(settlement.getPenaltyInterestAmount()) : quote.moratorium();
        BigDecimal totalDue = paid ? safe(settlement.getTotalAmountDue())
                : safe(settlement.getPaidAmount()).add(quote.total()).setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal principal = safe(settlement.getMonthPurchasesAmount())
                .add(safe(settlement.getMonthInstallmentsAmount()));
        return new SettlementResponseDto(
                settlement.getSettlementId(), ApiCodes.settlement(settlement.getSettlementId()),
                settlement.getCustomerId(), Names.fullName(user, settlement.getCustomerId()),
                settlement.getCutoffDate().toString(), settlement.getPaymentDueDate().toString(),
                Amounts.money(principal), Amounts.money(settlement.getCompensatoryInterestAmount()),
                Amounts.money(penalty), Amounts.money(totalDue), status,
                paid ? lastPaymentDate(history) : null, purchases);
    }

    private MonthlySettlementEntity requireSettlement(Integer settlementId, boolean lock) {
        Integer storeId = tenant.currentStoreId();
        if (CUSTOMER.equals(tenant.currentRole())) {
            Integer customerId = tenant.currentCustomerId();
            return settlements.findByIdAndCustomerAndStore(settlementId, customerId, storeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Settlement not found: " + settlementId));
        }
        return settlements.findByIdAndStore(settlementId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found: " + settlementId));
    }

    private boolean isPaid(MonthlySettlementEntity settlement) {
        return SettlementStatus.PAID.name().equals(settlement.getSettlementStatus())
                || settlement.getPendingBalance() == null
                || settlement.getPendingBalance().signum() == 0;
    }

    private void requireStoreAdmin() {
        if (!STORE_ADMIN.equals(tenant.currentRole())) {
            throw new BusinessException("FORBIDDEN", "Only store administrators can run this operation",
                    HttpStatus.FORBIDDEN);
        }
    }

    private BigDecimal sumRows(List<AmortizationScheduleEntity> rows,
                               Function<AmortizationScheduleEntity, BigDecimal> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add).setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPayments(List<PaymentEntity> history,
                                  Function<PaymentEntity, BigDecimal> getter) {
        return history.stream().map(getter).filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return ZERO;
        }
        return value.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            LocalDate parsed = LocalDate.parse(raw.trim());
            if (parsed.isBefore(fallback)) {
                throw new BusinessException("INVALID_DATE", "Calculation date cannot precede today",
                        HttpStatus.BAD_REQUEST);
            }
            return parsed;
        } catch (DateTimeParseException ex) {
            throw new BusinessException("INVALID_DATE", "The calculation date must use yyyy-MM-dd",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 100) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key cannot exceed 100 characters",
                    HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeReference(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private PaymentResponseDto toPaymentDto(PaymentEntity payment) {
        BigDecimal received = payment.getReceivedAmount() == null
                ? payment.getAmountPaid() : payment.getReceivedAmount();
        BigDecimal favourAfter = payment.getFavourBalanceAfter() == null ? ZERO : payment.getFavourBalanceAfter();
        MonthlySettlementEntity settlement = settlements.findById(payment.getSettlementId()).orElse(null);
        Integer customerId = settlement == null ? null : settlement.getCustomerId();
        return new PaymentResponseDto(
                payment.getPaymentId(), ApiCodes.payment(payment.getPaymentId()), payment.getSettlementId(),
                customerId, Amounts.money(payment.getAmountPaid()), payment.getPaymentDatetime().toString(),
                pe.upc.credito.domain.enums.PaymentMethod.valueOf(payment.getPaymentMethod()),
                payment.getReferenceNumber(), ApiCodes.receipt(payment.getPaymentId()),
                Amounts.money(received), Amounts.money(favourAfter),
                Amounts.money(payment.getAllocatedToPenalty()),
                Amounts.money(payment.getAllocatedToInterest()),
                Amounts.money(payment.getAllocatedToPrincipal()));
    }

    private boolean isPaidStatus(String status) {
        return InstallmentStatus.PAID.name().equals(status);
    }

    private String lastPaymentDate(List<PaymentEntity> history) {
        return history.stream().map(PaymentEntity::getPaymentDatetime).filter(Objects::nonNull)
                .max(Instant::compareTo)
                .map(instant -> instant.atZone(ZoneId.of("America/Lima")).toLocalDate().toString()).orElse(null);
    }

    private void completeSalesIfPaid(List<AmortizationScheduleEntity> paidRows, Integer settlementId) {
        Set<Integer> saleIds = paidRows.stream().map(AmortizationScheduleEntity::getSaleId).collect(Collectors.toSet());
        for (Integer saleId : saleIds) {
            List<AmortizationScheduleEntity> saleRows = schedules.findBySaleIdOrderByInstallmentNumberAsc(saleId);
            boolean allPaid = saleRows.stream()
                    .filter(row -> row.getInstallmentNumber() != null && row.getInstallmentNumber() > 0)
                    .allMatch(row -> isPaidStatus(row.getInstallmentStatus()));
            if (allPaid) {
                sales.findById(saleId).ifPresent(sale -> {
                    sale.setSaleStatus(SaleStatus.PAID.name());
                    sales.save(sale);
                });
            }
        }
        for (SaleEntity purchase : sales.findBySettlementIdAndPaymentMode(
                settlementId, PaymentMode.MONTH_END.name())) {
            purchase.setSaleStatus(SaleStatus.PAID.name());
            sales.save(purchase);
        }
    }
}
