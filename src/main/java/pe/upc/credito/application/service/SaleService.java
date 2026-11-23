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
import pe.upc.credito.application.support.Names;
import pe.upc.credito.application.support.SimulatedClock;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Casos de uso de ventas: registro, listado y consulta.
 *
 * <p>Reglas aplicadas:</p>
 * <ul>
 *   <li>El detalle debe pertenecer al mismo tenant y tener stock suficiente.</li>
 *   <li>Las ventas financiadas validan la linea de credito disponible.</li>
 *   <li>El plazo no puede exceder {@code customers.max_credit_months}.</li>
 *   <li>Las ventas financiadas generan el cronograma frances completo en
 *       {@code amortization_schedules} (fila 0 si hay gracia).</li>
 *   <li>El stock se descuenta en la misma transaccion (ficha 6 del cap. 8.2).</li>
 * </ul>
 */
@Service
public class SaleService {

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

    public SaleService(SaleRepository sales,
                       SaleDetailRepository saleDetails,
                       ProductRepository products,
                       CustomerRepository customers,
                       UserRepository users,
                       StoreRepository stores,
                       AmortizationScheduleRepository schedules,
                       MonthlySettlementRepository settlements,
                       FinancialMathEngine engine,
                       SimulatedClock clock) {
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
    }

    // ------------------------------------------------------------------
    // POST /sales
    // ------------------------------------------------------------------

    @Transactional
    public SaleResponseDto create(CreateSaleRequestDto request) {
        StoreEntity store = stores.findById(request.store_id())
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada: " + request.store_id()));

        CustomerEntity customer = customers
                .findByCustomerIdAndStoreId(request.customer_id(), store.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado en la tienda " + store.getStoreId() + ": " + request.customer_id()));

        if (request.details() == null || request.details().isEmpty()) {
            throw new BusinessException("EMPTY_SALE", "La venta debe contener al menos un producto");
        }

        Map<Integer, ProductEntity> productsById = new LinkedHashMap<>();
        List<SaleDetailEntity> detailEntities = new ArrayList<>();
        BigDecimal total = Amounts.zeroMoney();

        for (CreateSaleDetailRequestDto item : request.details()) {
            if (item.product_id() == null || item.quantity() == null || item.quantity() < 1) {
                throw new BusinessException("INVALID_SALE_DETAIL",
                        "Cada detalle requiere producto y cantidad positiva");
            }

            ProductEntity product = products
                    .findByProductIdAndStoreId(item.product_id(), store.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado en la tienda " + store.getStoreId() + ": " + item.product_id()));

            if (productsById.putIfAbsent(product.getProductId(), product) != null) {
                throw new BusinessException("DUPLICATE_PRODUCT",
                        "El producto " + product.getProductId() + " aparece mas de una vez en la venta");
            }
            if ("DISCONTINUED".equals(product.getStatus())) {
                throw new BusinessException("PRODUCT_DISCONTINUED",
                        "El producto " + product.getName() + " esta descontinuado");
            }
            if ("OUT_OF_STOCK".equals(product.getStatus())
                    || product.getCurrentStock() == null
                    || product.getCurrentStock() < item.quantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Stock insuficiente para " + product.getName()
                                + " (disponible: " + product.getCurrentStock() + ")");
            }

            BigDecimal unitPrice = Amounts.money(item.unit_price());
            if (unitPrice.signum() <= 0) {
                throw new BusinessException("INVALID_UNIT_PRICE", "El precio unitario debe ser mayor a cero");
            }

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);

            SaleDetailEntity detail = new SaleDetailEntity();
            detail.setProductId(product.getProductId());
            detail.setQuantity(item.quantity());
            detail.setUnitPrice(unitPrice);
            detail.setSubtotal(subtotal);
            detailEntities.add(detail);

            total = total.add(subtotal);
        }
        total = total.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);

        PaymentMode mode = request.payment_mode();
        int numberOfMonths = 1;
        Integer installmentsCount = null;
        Integer graceDays = null;
        BigDecimal tea = null;
        SaleStatus saleStatus;

        if (mode == PaymentMode.CONTADO) {
            saleStatus = SaleStatus.REGISTRADA;
        } else {
            int maxMonths = customer.getMaxCreditMonths();

            if (mode == PaymentMode.FINANCIADO_CUOTAS) {
                if (request.installments_count() == null) {
                    throw new BusinessException("INSTALLMENTS_REQUIRED",
                            "Debe indicar el numero de cuotas para la modalidad FINANCIADO_CUOTAS");
                }
                installmentsCount = request.installments_count();
            } else {
                installmentsCount = request.installments_count() != null
                        ? request.installments_count()
                        : maxMonths;
            }

            if (installmentsCount < 1 || installmentsCount > maxMonths) {
                throw new BusinessException("INSTALLMENTS_OUT_OF_RANGE",
                        "El numero de cuotas debe estar entre 1 y " + maxMonths
                                + " (plazo maximo del cliente)");
            }

            graceDays = request.grace_days() == null ? 0 : request.grace_days();
            tea = resolveTea(request.tea(), customer);
            numberOfMonths = installmentsCount;
            saleStatus = SaleStatus.EN_CRONOGRAMA;

            // Linea de credito: liquidaciones impagas + cuotas aun no facturadas
            BigDecimal currentDebt = settlements.sumPendingBalanceByCustomer(customer.getCustomerId())
                    .add(schedules.sumUnbilledPrincipal(customer.getCustomerId()));
            BigDecimal available = customer.getCreditLimit().subtract(currentDebt).max(Amounts.zeroMoney());
            if (total.compareTo(available) > 0) {
                throw new BusinessException("CREDIT_LIMIT_EXCEEDED",
                        "El importe de la venta (S/ " + Amounts.money(total)
                                + ") excede la linea de credito disponible del cliente (S/ "
                                + Amounts.money(available) + ").");
            }
        }

        SaleEntity sale = new SaleEntity();
        sale.setStoreId(store.getStoreId());
        sale.setCustomerId(customer.getCustomerId());
        sale.setSaleDatetime(clock.now());
        sale.setTotalAmount(total);
        sale.setPaymentMode(mode.name());
        sale.setNumberOfMonths(numberOfMonths);
        sale.setSaleStatus(saleStatus.name());
        sale.setTea(tea);
        sales.saveAndFlush(sale);

        for (SaleDetailEntity detail : detailEntities) {
            detail.setSaleId(sale.getSaleId());
        }
        List<SaleDetailEntity> savedDetails = saleDetails.saveAll(detailEntities);

        // Descuento de stock en la misma transaccion
        for (SaleDetailEntity detail : savedDetails) {
            ProductEntity product = productsById.get(detail.getProductId());
            product.setCurrentStock(product.getCurrentStock() - detail.getQuantity());
            if (product.getCurrentStock() == 0) {
                product.setStatus("OUT_OF_STOCK");
            }
            products.save(product);
        }

        // Cronograma frances para ventas financiadas.
        // El motor usa una fecha base y vence la cuota k a base + k meses
        // (igual que el simulador del frontend). Para la venta, la primera
        // cuota se fija al dia de pago del cliente del mes siguiente.
        if (mode != PaymentMode.CONTADO) {
            LocalDate today = clock.today();
            LocalDate firstInstallmentDate = nextDueDate(today, customer.getPaymentDueDay());
            SimulatedSchedule schedule = engine.simulate(
                    total, tea, RateType.EFECTIVA, null,
                    installmentsCount, graceDays, today);

            List<AmortizationScheduleEntity> rows = new ArrayList<>(schedule.installments().size());
            for (SimulatedInstallment installment : schedule.installments()) {
                LocalDate dueDate = installment.installmentNumber() == 0
                        ? today
                        : firstInstallmentDate.plusMonths(installment.installmentNumber() - 1L);

                AmortizationScheduleEntity row = new AmortizationScheduleEntity();
                row.setSaleId(sale.getSaleId());
                row.setSettlementId(null);
                row.setInstallmentNumber(installment.installmentNumber());
                row.setDueDate(dueDate);
                // Cap. 7.3: en la fila de gracia (k = 0) el saldo inicial es el
                // capital original P; en las filas k >= 1 es capital + saldo final.
                BigDecimal initialBalance = installment.installmentNumber() == 0
                        ? total
                        : installment.principalAmount().add(installment.balanceAmount());
                row.setInitialBalance(initialBalance
                        .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
                row.setPrincipalAmount(installment.principalAmount());
                row.setCompensatoryInterest(installment.interestAmount());
                row.setFixedInstallment(installment.paymentAmount());
                row.setEndingBalance(installment.balanceAmount());
                row.setGracePeriodDays(graceDays);
                row.setInstallmentStatus(InstallmentStatus.PENDIENTE.name());
                rows.add(row);
            }
            schedules.saveAll(rows);
        }

        UserEntity user = users.findById(customer.getUserId()).orElse(null);
        List<SaleDetailResponseDto> detailDtos = savedDetails.stream()
                .map(detail -> toDetailDto(detail, productsById.get(detail.getProductId())))
                .toList();

        return new SaleResponseDto(
                sale.getSaleId(),
                ApiCodes.sale(sale.getSaleId()),
                customer.getCustomerId(),
                Names.fullName(user, customer.getCustomerId()),
                store.getStoreId(),
                mode,
                Amounts.money(total),
                mapSaleStatus(saleStatus.name()),
                installmentsCount,
                graceDays,
                tea == null ? null : Amounts.rate(tea),
                sale.getSaleDatetime().toString(),
                detailDtos);
    }

    // ------------------------------------------------------------------
    // GET /sales y GET /sales/{id}
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SaleResponseDto> list(Integer storeId) {
        List<SaleEntity> saleList = sales.findByStoreIdOrderBySaleDatetimeDesc(storeId);
        if (saleList.isEmpty()) {
            return List.of();
        }

        List<Integer> saleIds = saleList.stream().map(SaleEntity::getSaleId).toList();

        Map<Integer, List<SaleDetailEntity>> detailsBySale = saleDetails
                .findBySaleIdInOrderBySaleDetailIdAsc(saleIds).stream()
                .collect(Collectors.groupingBy(SaleDetailEntity::getSaleId));

        List<Integer> productIds = detailsBySale.values().stream()
                .flatMap(List::stream)
                .map(SaleDetailEntity::getProductId)
                .distinct()
                .toList();
        Map<Integer, ProductEntity> productsById = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getProductId, Function.identity()));

        List<Integer> customerIds = saleList.stream()
                .map(SaleEntity::getCustomerId).distinct().toList();
        Map<Integer, CustomerEntity> customersById = customers.findAllById(customerIds).stream()
                .collect(Collectors.toMap(CustomerEntity::getCustomerId, Function.identity()));

        List<Integer> userIds = customersById.values().stream()
                .map(CustomerEntity::getUserId).distinct().toList();
        Map<Integer, UserEntity> usersById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));

        Map<Integer, List<AmortizationScheduleEntity>> scheduleBySale = schedules
                .findBySaleIdInOrderByInstallmentNumberAsc(saleIds).stream()
                .collect(Collectors.groupingBy(AmortizationScheduleEntity::getSaleId));

        return saleList.stream().map(sale -> {
            CustomerEntity customer = customersById.get(sale.getCustomerId());
            UserEntity user = customer == null ? null : usersById.get(customer.getUserId());
            return toDto(
                    sale,
                    detailsBySale.getOrDefault(sale.getSaleId(), List.of()),
                    productsById,
                    customer,
                    user,
                    scheduleBySale.getOrDefault(sale.getSaleId(), List.of()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public SaleResponseDto get(Integer saleId, Integer storeId) {
        SaleEntity sale = sales.findBySaleIdAndStoreId(saleId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + saleId));

        List<SaleDetailEntity> details = saleDetails.findBySaleIdOrderBySaleDetailIdAsc(saleId);

        List<Integer> productIds = details.stream().map(SaleDetailEntity::getProductId).distinct().toList();
        Map<Integer, ProductEntity> productsById = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getProductId, Function.identity()));

        CustomerEntity customer = customers.findById(sale.getCustomerId()).orElse(null);
        UserEntity user = customer == null ? null : users.findById(customer.getUserId()).orElse(null);

        List<AmortizationScheduleEntity> scheduleRows =
                schedules.findBySaleIdOrderByInstallmentNumberAsc(saleId);

        return toDto(sale, details, productsById, customer, user, scheduleRows);
    }

    // ------------------------------------------------------------------
    // Mapeo
    // ------------------------------------------------------------------

    private SaleResponseDto toDto(SaleEntity sale,
                                  List<SaleDetailEntity> details,
                                  Map<Integer, ProductEntity> productsById,
                                  CustomerEntity customer,
                                  UserEntity user,
                                  List<AmortizationScheduleEntity> scheduleRows) {
        boolean cash = PaymentMode.CONTADO.name().equals(sale.getPaymentMode());

        Integer installmentsCount = cash ? null : sale.getNumberOfMonths();

        Integer graceDays = null;
        if (!cash) {
            graceDays = scheduleRows.isEmpty()
                    ? 0
                    : scheduleRows.stream()
                            .mapToInt(AmortizationScheduleEntity::getGracePeriodDays)
                            .max()
                            .orElse(0);
        }

        BigDecimal tea = sale.getTea();
        if (tea == null && !cash && customer != null) {
            RateType type = RateType.valueOf(customer.getCompensatoryRateType());
            tea = engine.normalize(customer.getCompensatoryRateValue(), type,
                    FinancialMathEngine.COMMERCIAL_YEAR_DAYS).tea();
        }

        List<SaleDetailResponseDto> detailDtos = details.stream()
                .map(detail -> toDetailDto(detail, productsById.get(detail.getProductId())))
                .toList();

        return new SaleResponseDto(
                sale.getSaleId(),
                ApiCodes.sale(sale.getSaleId()),
                sale.getCustomerId(),
                Names.fullName(user, sale.getCustomerId()),
                sale.getStoreId(),
                PaymentMode.valueOf(sale.getPaymentMode()),
                Amounts.money(sale.getTotalAmount()),
                mapSaleStatus(sale.getSaleStatus()),
                installmentsCount,
                graceDays,
                tea == null ? null : Amounts.rate(tea),
                sale.getSaleDatetime().toString(),
                detailDtos);
    }

    private SaleDetailResponseDto toDetailDto(SaleDetailEntity detail, ProductEntity product) {
        String productName = product != null ? product.getName() : "Producto " + detail.getProductId();
        String sku = product != null ? ApiCodes.sku(product.getProductId()) : "SKU-?";
        return new SaleDetailResponseDto(
                detail.getSaleDetailId(),
                detail.getProductId(),
                productName,
                sku,
                Amounts.money(detail.getUnitPrice()),
                detail.getQuantity(),
                Amounts.money(detail.getSubtotal()));
    }

    /** Estados del PDF (cap. 8) -> estados que muestra el frontend. */
    private String mapSaleStatus(String dbStatus) {
        return switch (dbStatus) {
            case "REGISTRADA" -> "COMPLETED";
            case "EN_CRONOGRAMA" -> "PENDING";
            case "CANCELADA" -> "CANCELLED";
            case "ANULADA" -> "VOIDED";
            default -> dbStatus;
        };
    }

    private BigDecimal resolveTea(String rawTea, CustomerEntity customer) {
        if (rawTea != null && !rawTea.isBlank()) {
            BigDecimal tea = Amounts.rate(rawTea);
            if (tea.signum() <= 0) {
                throw new BusinessException("INVALID_TEA", "La TEA debe ser mayor a cero",
                        HttpStatus.BAD_REQUEST);
            }
            return tea;
        }
        RateType type = RateType.valueOf(customer.getCompensatoryRateType());
        return engine.normalize(customer.getCompensatoryRateValue(), type,
                FinancialMathEngine.COMMERCIAL_YEAR_DAYS).tea();
    }

    /** Primera cuota: siguiente mes, en el dia de pago configurado del cliente. */
    private LocalDate nextDueDate(LocalDate from, int dueDay) {
        LocalDate base = from.plusMonths(1);
        int day = Math.min(dueDay, base.lengthOfMonth());
        return base.withDayOfMonth(day);
    }
}
