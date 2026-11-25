package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.CreateSaleDetailRequestDto;
import pe.upc.credito.application.dto.CreateSaleRequestDto;
import pe.upc.credito.application.dto.SaleDetailResponseDto;
import pe.upc.credito.application.dto.SaleResponseDto;
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
import pe.upc.credito.domain.enums.RateType;
import pe.upc.credito.domain.enums.SaleStatus;
import pe.upc.credito.domain.model.SimulatedInstallment;
import pe.upc.credito.domain.model.SimulatedSchedule;
import pe.upc.credito.domain.service.FinancialMathEngine;
import pe.upc.credito.infrastructure.persistence.entity.AmortizationScheduleEntity;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.ProductEntity;
import pe.upc.credito.infrastructure.persistence.entity.SaleDetailEntity;
import pe.upc.credito.infrastructure.persistence.entity.SaleEntity;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.AmortizationScheduleRepository;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.MonthlySettlementRepository;
import pe.upc.credito.infrastructure.persistence.repository.ProductRepository;
import pe.upc.credito.infrastructure.persistence.repository.SaleDetailRepository;
import pe.upc.credito.infrastructure.persistence.repository.SaleRepository;
import pe.upc.credito.infrastructure.persistence.repository.StoreRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Sale registration and read operations, scoped to the authenticated tenant. */
@Service
public class SaleService {

    private static final String STORE_ADMIN = "STORE_ADMIN";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String CUSTOMER = "CUSTOMER";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Amounts.MONEY_SCALE);

    private final SaleRepository sales;
    private final SaleDetailRepository saleDetails;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final StoreRepository stores;
    private final AmortizationScheduleRepository schedules;
    private final MonthlySettlementRepository settlements;
    private final FinancialMathEngine engine;
    private final SimulatedClock clock;
    private final TenantContext tenant;

    public SaleService(SaleRepository sales,
                       SaleDetailRepository saleDetails,
                       ProductRepository products,
                       CustomerRepository customers,
                       UserRepository users,
                       StoreRepository stores,
                       AmortizationScheduleRepository schedules,
                       MonthlySettlementRepository settlements,
                       FinancialMathEngine engine,
                       SimulatedClock clock,
                       TenantContext tenant) {
        this.sales = sales;
        this.saleDetails = saleDetails;
        this.products = products;
        this.customers = customers;
        this.users = users;
        this.stores = stores;
        this.schedules = schedules;
        this.settlements = settlements;
        this.engine = engine;
        this.clock = clock;
        this.tenant = tenant;
    }

    @Transactional
    public SaleResponseDto create(CreateSaleRequestDto request, String rawIdempotencyKey) {
        requireStoreAdmin();
        Integer storeId = requireStoreId();
        validateRequest(request);

        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        String fingerprint = fingerprint(request);
        SaleResponseDto replay = findIdempotentReplay(storeId, idempotencyKey, fingerprint);
        if (replay != null) {
            return replay;
        }

        StoreEntity store = stores.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));
        CustomerEntity customer = customers
                .findByCustomerIdAndStoreIdForUpdate(request.customer_id(), storeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found in authenticated store: " + request.customer_id()));
        // A retry may have waited for the customer's row lock while its first
        // request committed. Recheck the idempotency key under that lock.
        replay = findIdempotentReplay(storeId, idempotencyKey, fingerprint);
        if (replay != null) {
            return replay;
        }
        PaymentMode mode = request.payment_mode();
        LocalDate saleDate = clock.today();

        Map<Integer, ProductEntity> productsById = new LinkedHashMap<>();
        List<SaleDetailEntity> detailEntities = new ArrayList<>();
        BigDecimal total = ZERO;
        List<CreateSaleDetailRequestDto> requestedDetails = request.details().stream()
                .sorted((a, b) -> Integer.compare(a.product_id() == null ? 0 : a.product_id(),
                        b.product_id() == null ? 0 : b.product_id()))
                .toList();
        for (CreateSaleDetailRequestDto item : requestedDetails) {
            if (item.product_id() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new BusinessException("INVALID_SALE_DETAIL",
                        "Each sale detail requires a product and positive quantity", HttpStatus.BAD_REQUEST);
            }
            if (productsById.containsKey(item.product_id())) {
                throw new BusinessException("DUPLICATE_PRODUCT",
                        "A product may appear only once in the sale", HttpStatus.BAD_REQUEST);
            }
            ProductEntity product = products
                    .findByProductIdAndStoreIdForUpdate(item.product_id(), storeId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found in authenticated store: " + item.product_id()));
            if ("DISCONTINUED".equals(product.getStatus())) {
                throw new BusinessException("PRODUCT_DISCONTINUED",
                        "The product is discontinued", HttpStatus.CONFLICT);
            }
            if ("OUT_OF_STOCK".equals(product.getStatus())
                    || product.getCurrentStock() == null
                    || product.getCurrentStock() < item.quantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Insufficient stock for product " + product.getProductId(), HttpStatus.CONFLICT);
            }
            if (mode == PaymentMode.INSTALLMENTS && !Boolean.TRUE.equals(product.getAllowsInstallments())) {
                throw new BusinessException("INSTALLMENTS_NOT_ALLOWED",
                        "The product does not allow installment payments", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            BigDecimal unitPrice = mode == PaymentMode.CASH
                    ? product.getCashPrice()
                    : product.getCreditListPrice();
            if (unitPrice == null || unitPrice.signum() <= 0) {
                throw new BusinessException("INVALID_CATALOG_PRICE",
                        "The catalog contains an invalid price", HttpStatus.CONFLICT);
            }
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
            SaleDetailEntity detail = new SaleDetailEntity();
            detail.setProductId(product.getProductId());
            detail.setQuantity(item.quantity());
            detail.setUnitPrice(unitPrice);
            detail.setSubtotal(subtotal);
            detailEntities.add(detail);
            productsById.put(product.getProductId(), product);
            total = total.add(subtotal);
        }
        total = total.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = Amounts.money(request.expected_total());
        if (expectedTotal.compareTo(total) != 0) {
            throw new BusinessException("QUOTE_CHANGED",
                    "The catalog total changed; refresh the quote before registering the sale",
                    HttpStatus.CONFLICT);
        }

        int installmentsCount = 1;
        int graceDays = 0;
        BigDecimal tea = null;
        BigDecimal monthEndInterest = ZERO;
        BigDecimal penaltyTea = null;
        LocalDate billingCutoffDate = null;
        LocalDate firstDueDate = null;
        SaleStatus status;

        if (mode == PaymentMode.CASH) {
            status = SaleStatus.REGISTERED;
        } else {
            ensureMonthlyPaymentAllowed(customer, saleDate, mode);
            billingCutoffDate = BillingCalendar.cutoffForPurchase(saleDate, customer.getCutoffDay());
            firstDueDate = BillingCalendar.paymentDueAfter(billingCutoffDate, customer.getCutoffDay(), customer.getPaymentDueDay());
            if (mode == PaymentMode.MONTH_END) {
                tea = normalizeCustomerRate(customer.getCompensatoryRateType(),
                        customer.getCompensatoryRateValue(), customer.getCompensatoryRatePeriodDays(),
                        customer.getCompensatoryCapitalizationDays());
                monthEndInterest = engine.accruedInterest(total, tea, engine.days30E360(saleDate, firstDueDate));
            }
            ensureCreditLimit(customer, total.add(monthEndInterest));
            penaltyTea = normalizeCustomerRate(customer.getPenaltyRateType(),
                    customer.getPenaltyRateValue(), customer.getPenaltyRatePeriodDays(),
                    customer.getPenaltyCapitalizationDays());
            status = SaleStatus.SCHEDULED;
        }

        if (mode == PaymentMode.INSTALLMENTS) {
            firstDueDate = BillingCalendar.firstInstallmentDue(
                    billingCutoffDate, customer.getCutoffDay(), customer.getPaymentDueDay());
            if (request.installments_count() == null) {
                throw new BusinessException("INSTALLMENTS_REQUIRED",
                        "The installment count is required for INSTALLMENTS", HttpStatus.BAD_REQUEST);
            }
            if (request.tea() == null || request.tea().isBlank()) {
                throw new BusinessException("RATE_TERMS_REQUIRED",
                        "The quoted TEA is required for INSTALLMENTS", HttpStatus.BAD_REQUEST);
            }
            LocalDate quotedFirstDueDate = parseRequiredDate(request.first_due_date(), "first_due_date");
            if (!quotedFirstDueDate.equals(firstDueDate)) {
                throw new BusinessException("QUOTE_CHANGED",
                        "The first installment due date changed; refresh the quote before registering the sale",
                        HttpStatus.CONFLICT);
            }
            installmentsCount = request.installments_count();
            if (installmentsCount < 1 || installmentsCount > customer.getMaxCreditMonths()) {
                throw new BusinessException("INSTALLMENTS_OUT_OF_RANGE",
                        "The installment count exceeds the customer's credit term", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            graceDays = engine.days30E360(saleDate,
                    BillingCalendar.paymentDueAfter(billingCutoffDate, customer.getCutoffDay(), customer.getPaymentDueDay()));
            if (request.grace_days() != null && request.grace_days() != graceDays) {
                throw new BusinessException("GRACE_PERIOD_CHANGED",
                        "The quoted grace period changed; refresh the quote before registering the sale",
                        HttpStatus.CONFLICT);
            }
            if (graceDays < 0 || graceDays > 365) {
                throw new BusinessException("INVALID_GRACE_DAYS",
                        "Grace days must be between 0 and 365", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            tea = normalizeCustomerRate(customer.getCompensatoryRateType(),
                    customer.getCompensatoryRateValue(), customer.getCompensatoryRatePeriodDays(),
                    customer.getCompensatoryCapitalizationDays());
            if (Amounts.rate(request.tea()).compareTo(tea) != 0) {
                throw new BusinessException("RATE_TERMS_CHANGED",
                        "The customer rate changed; refresh the quote before registering the sale",
                        HttpStatus.CONFLICT);
            }
        } else if (request.grace_days() != null && request.grace_days() != 0) {
            throw new BusinessException("GRACE_NOT_SUPPORTED",
                    "Grace days are only available for INSTALLMENTS", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        SaleEntity sale = new SaleEntity();
        sale.setStoreId(storeId);
        sale.setCustomerId(customer.getCustomerId());
        sale.setSaleDatetime(clock.now());
        sale.setTotalAmount(total);
        sale.setPaymentMode(mode.name());
        sale.setNumberOfMonths(installmentsCount);
        sale.setSaleStatus(status.name());
        sale.setTea(tea);
        sale.setMonthEndInterest(monthEndInterest);
        sale.setPenaltyTea(penaltyTea);
        sale.setBillingCutoffDate(billingCutoffDate);
        sale.setFirstDueDate(firstDueDate);
        sale.setIdempotencyKey(idempotencyKey);
        sale.setRequestFingerprint(idempotencyKey == null ? null : fingerprint);
        sales.saveAndFlush(sale);

        for (SaleDetailEntity detail : detailEntities) {
            detail.setSaleId(sale.getSaleId());
        }
        List<SaleDetailEntity> savedDetails = saleDetails.saveAll(detailEntities);

        for (SaleDetailEntity detail : savedDetails) {
            ProductEntity product = productsById.get(detail.getProductId());
            product.setCurrentStock(product.getCurrentStock() - detail.getQuantity());
            if (product.getCurrentStock() == 0) {
                product.setStatus("OUT_OF_STOCK");
            }
            products.save(product);
        }

        if (mode == PaymentMode.INSTALLMENTS) {
            RateType contractRateType = RateType.valueOf(customer.getCompensatoryRateType());
            Integer contractCompoundingDays = contractRateType == RateType.NOMINAL
                    ? customer.getCompensatoryCapitalizationDays() : null;
            SimulatedSchedule schedule = engine.simulate(total,
                    customer.getCompensatoryRateValue(), contractRateType,
                    customer.getCompensatoryRatePeriodDays(), contractCompoundingDays,
                    installmentsCount, graceDays, firstDueDate, customer.getPaymentDueDay());
            List<AmortizationScheduleEntity> rows = new ArrayList<>(schedule.installments().size());
            for (SimulatedInstallment installment : schedule.installments()) {
                int number = installment.installmentNumber();
                LocalDate rowCutoff = number == 0 ? null : BillingCalendar.installmentCutoff(
                        billingCutoffDate, customer.getCutoffDay(), number);
                AmortizationScheduleEntity row = new AmortizationScheduleEntity();
                row.setSaleId(sale.getSaleId());
                row.setSettlementId(null);
                row.setInstallmentNumber(number);
                row.setBillingCutoffDate(number == 0 ? billingCutoffDate : rowCutoff);
                row.setDueDate(number == 0
                        ? BillingCalendar.paymentDueAfter(billingCutoffDate, customer.getCutoffDay(), customer.getPaymentDueDay())
                        : installment.dueDate());
                BigDecimal initialBalance = number == 0
                        ? total
                        : installment.principalAmount().add(installment.balanceAmount());
                row.setInitialBalance(initialBalance.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
                row.setPrincipalAmount(installment.principalAmount());
                row.setCompensatoryInterest(installment.interestAmount());
                row.setFixedInstallment(installment.paymentAmount());
                row.setEndingBalance(installment.balanceAmount());
                row.setGracePeriodDays(graceDays);
                row.setInstallmentStatus(InstallmentStatus.PENDING.name());
                rows.add(row);
            }
            schedules.saveAll(rows);
        }

        UserEntity user = users.findById(customer.getUserId()).orElse(null);
        List<SaleDetailResponseDto> detailDtos = savedDetails.stream()
                .map(detail -> toDetailDto(detail, productsById.get(detail.getProductId())))
                .toList();
        return toDto(sale, detailDtos, customer, user,
                mode == PaymentMode.INSTALLMENTS
                        ? schedules.findBySaleIdOrderByInstallmentNumberAsc(sale.getSaleId())
                        : List.of());
    }

    @Transactional(readOnly = true)
    public List<SaleResponseDto> list() {
        Integer storeId = requireStoreId();
        List<SaleEntity> saleList = CUSTOMER.equals(tenant.currentRole())
                ? sales.findByCustomerIdAndStoreIdOrderBySaleDatetimeDesc(
                        requireCustomerId(), storeId)
                : sales.findByStoreIdOrderBySaleDatetimeDesc(storeId);
        return mapSales(saleList);
    }

    @Transactional(readOnly = true)
    public SaleResponseDto get(Integer saleId) {
        Integer storeId = requireStoreId();
        SaleEntity sale = CUSTOMER.equals(tenant.currentRole())
                ? sales.findBySaleIdAndCustomerIdAndStoreId(saleId, requireCustomerId(), storeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId))
                : sales.findBySaleIdAndStoreId(saleId, storeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));
        return getForStore(sale.getSaleId(), storeId);
    }

    private List<SaleResponseDto> mapSales(List<SaleEntity> saleList) {
        if (saleList.isEmpty()) {
            return List.of();
        }
        List<Integer> saleIds = saleList.stream().map(SaleEntity::getSaleId).toList();
        Map<Integer, List<SaleDetailEntity>> detailsBySale = saleDetails
                .findBySaleIdInOrderBySaleDetailIdAsc(saleIds).stream()
                .collect(Collectors.groupingBy(SaleDetailEntity::getSaleId));
        List<Integer> productIds = detailsBySale.values().stream().flatMap(List::stream)
                .map(SaleDetailEntity::getProductId).distinct().toList();
        Map<Integer, ProductEntity> productsById = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getProductId, Function.identity()));
        List<Integer> customerIds = saleList.stream().map(SaleEntity::getCustomerId).distinct().toList();
        Map<Integer, CustomerEntity> customersById = customers.findAllById(customerIds).stream()
                .collect(Collectors.toMap(CustomerEntity::getCustomerId, Function.identity()));
        List<Integer> userIds = customersById.values().stream().map(CustomerEntity::getUserId).distinct().toList();
        Map<Integer, UserEntity> usersById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        Map<Integer, List<AmortizationScheduleEntity>> scheduleBySale = schedules
                .findBySaleIdInOrderByInstallmentNumberAsc(saleIds).stream()
                .collect(Collectors.groupingBy(AmortizationScheduleEntity::getSaleId));

        return saleList.stream().map(sale -> {
            CustomerEntity customer = customersById.get(sale.getCustomerId());
            UserEntity user = customer == null ? null : usersById.get(customer.getUserId());
            List<SaleDetailResponseDto> detailDtos = detailsBySale
                    .getOrDefault(sale.getSaleId(), List.of()).stream()
                    .map(detail -> toDetailDto(detail, productsById.get(detail.getProductId())))
                    .toList();
            return toDto(sale, detailDtos, customer, user,
                    scheduleBySale.getOrDefault(sale.getSaleId(), List.of()));
        }).toList();
    }

    private SaleResponseDto getForStore(Integer saleId, Integer storeId) {
        SaleEntity sale = sales.findBySaleIdAndStoreId(saleId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));
        List<SaleDetailEntity> details = saleDetails.findBySaleIdOrderBySaleDetailIdAsc(saleId);
        List<Integer> productIds = details.stream().map(SaleDetailEntity::getProductId).distinct().toList();
        Map<Integer, ProductEntity> productsById = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getProductId, Function.identity()));
        CustomerEntity customer = customers.findById(sale.getCustomerId()).orElse(null);
        UserEntity user = customer == null ? null : users.findById(customer.getUserId()).orElse(null);
        List<SaleDetailResponseDto> detailDtos = details.stream()
                .map(detail -> toDetailDto(detail, productsById.get(detail.getProductId())))
                .toList();
        return toDto(sale, detailDtos, customer, user,
                schedules.findBySaleIdOrderByInstallmentNumberAsc(saleId));
    }

    private SaleResponseDto toDto(SaleEntity sale,
                                  List<SaleDetailResponseDto> details,
                                  CustomerEntity customer,
                                  UserEntity user,
                                  List<AmortizationScheduleEntity> scheduleRows) {
        PaymentMode mode = PaymentMode.valueOf(sale.getPaymentMode());
        Integer count = mode == PaymentMode.INSTALLMENTS ? sale.getNumberOfMonths()
                : mode == PaymentMode.MONTH_END ? 1 : null;
        Integer graceDays = mode == PaymentMode.INSTALLMENTS
                ? scheduleRows.stream().mapToInt(row -> row.getGracePeriodDays() == null
                        ? 0 : row.getGracePeriodDays()).max().orElse(0)
                : null;
        return new SaleResponseDto(
                sale.getSaleId(),
                ApiCodes.sale(sale.getSaleId()),
                sale.getCustomerId(),
                Names.fullName(user, sale.getCustomerId()),
                sale.getStoreId(),
                mode,
                Amounts.money(sale.getTotalAmount()),
                mode == PaymentMode.MONTH_END ? Amounts.money(safe(sale.getMonthEndInterest())) : null,
                sale.getFirstDueDate() == null ? null : sale.getFirstDueDate().toString(),
                sale.getSaleStatus(),
                count,
                graceDays,
                sale.getTea() == null ? null : Amounts.rate(sale.getTea()),
                sale.getSaleDatetime().toString(),
                details);
    }

    private SaleDetailResponseDto toDetailDto(SaleDetailEntity detail, ProductEntity product) {
        String productName = product == null ? "Product " + detail.getProductId() : product.getName();
        String sku = product == null ? "SKU-?" : ApiCodes.sku(product.getProductId());
        return new SaleDetailResponseDto(detail.getSaleDetailId(), detail.getProductId(), productName,
                sku, Amounts.money(detail.getUnitPrice()), detail.getQuantity(), Amounts.money(detail.getSubtotal()));
    }

    private void ensureCreditLimit(CustomerEntity customer, BigDecimal saleAmount) {
        BigDecimal currentDebt = safe(settlements.sumPendingBalanceByCustomer(customer.getCustomerId()))
                .add(safe(schedules.sumUnbilledPrincipal(customer.getCustomerId())))
                .add(safe(sales.sumUnbilledMonthEndByCustomer(customer.getCustomerId())));
        BigDecimal available = customer.getCreditLimit().subtract(currentDebt).max(ZERO);
        if (saleAmount.compareTo(available) > 0) {
            throw new BusinessException("CREDIT_LIMIT_EXCEEDED",
                    "The sale exceeds the customer's available credit", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void ensureMonthlyPaymentAllowed(CustomerEntity customer, LocalDate today, PaymentMode mode) {
        if (mode == PaymentMode.MONTH_END
                && customers.countOverdueSettlements(customer.getCustomerId(), today) > 0) {
            throw new BusinessException("MONTH_END_NOT_ALLOWED",
                    "The customer has an overdue settlement", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private BigDecimal normalizeCustomerRate(String type,
                                             BigDecimal value,
                                             Integer ratePeriodDays,
                                             Integer capitalizationDays) {
        try {
            RateType rateType = RateType.valueOf(type);
            return engine.normalize(value, rateType, ratePeriodDays,
                    rateType == RateType.NOMINAL ? capitalizationDays : null).tea()
                    .setScale(FinancialMathEngine.RATE_SCALE, RoundingMode.HALF_UP);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("INVALID_CUSTOMER_RATE", "The customer's rate terms are invalid",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private LocalDate parseRequiredDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("INVALID_SALE_REQUEST", field + " is required for INSTALLMENTS",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("INVALID_DATE", field + " must use yyyy-MM-dd", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateRequest(CreateSaleRequestDto request) {
        if (request == null || request.customer_id() == null || request.payment_mode() == null
                || request.expected_total() == null || request.expected_total().isBlank()
                || request.details() == null || request.details().isEmpty()) {
            throw new BusinessException("INVALID_SALE_REQUEST",
                    "Customer, payment mode and at least one detail are required", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireStoreAdmin() {
        String role = tenant.currentRole();
        if (!STORE_ADMIN.equals(role) && !SUPER_ADMIN.equals(role)) {
            throw new BusinessException("FORBIDDEN", "Only store administrators can register sales",
                    HttpStatus.FORBIDDEN);
        }
    }

    private Integer requireStoreId() {
        Integer storeId = tenant.currentStoreId();
        if (storeId == null || storeId <= 0) {
            throw new BusinessException("STORE_CONTEXT_REQUIRED",
                    "The authenticated session has no active store", HttpStatus.FORBIDDEN);
        }
        return storeId;
    }

    private Integer requireCustomerId() {
        Integer customerId = tenant.currentCustomerId();
        if (customerId == null || customerId <= 0) {
            throw new BusinessException("CUSTOMER_CONTEXT_REQUIRED",
                    "The authenticated session has no customer profile", HttpStatus.FORBIDDEN);
        }
        return customerId;
    }

    private String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required for sale registration", HttpStatus.BAD_REQUEST);
        }
        String value = raw.trim();
        if (value.length() > 100) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key cannot exceed 100 characters", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private SaleResponseDto findIdempotentReplay(Integer storeId, String key, String fingerprint) {
        var existing = sales.findByStoreIdAndIdempotencyKey(storeId, key);
        if (existing.isEmpty()) {
            return null;
        }
        SaleEntity sale = existing.get();
        if (!fingerprint.equals(sale.getRequestFingerprint())) {
            throw new BusinessException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different sale request", HttpStatus.CONFLICT);
        }
        return getForStore(sale.getSaleId(), storeId);
    }

    private String fingerprint(CreateSaleRequestDto request) {
        String details = request.details().stream()
                .sorted((a, b) -> Integer.compare(a.product_id() == null ? 0 : a.product_id(),
                        b.product_id() == null ? 0 : b.product_id()))
                .map(item -> item.product_id() + ":" + item.quantity())
                .collect(Collectors.joining(","));
        String raw = request.customer_id() + "|" + request.payment_mode() + "|"
                + request.installments_count() + "|" + request.grace_days() + "|" + request.tea() + "|"
                + request.expected_total() + "|" + request.first_due_date() + "|" + details;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", ex);
        }
    }

    private BigDecimal safe(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }
}
