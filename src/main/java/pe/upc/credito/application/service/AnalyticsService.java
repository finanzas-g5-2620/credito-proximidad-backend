package pe.upc.credito.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.AuditMovementResponseDto;
import pe.upc.credito.application.dto.DashboardKpisResponseDto;
import pe.upc.credito.application.dto.MonthlyFlowResponseDto;
import pe.upc.credito.application.support.Amounts;
import pe.upc.credito.application.support.ApiCodes;
import pe.upc.credito.application.support.Names;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.domain.enums.MovementEventType;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Indicadores del dashboard gerencial (MIS).
 *
 * <p>Definiciones:</p>
 * <ul>
 *   <li>{@code total_revenue}: suma de las ventas del tenant (excluye anuladas).</li>
 *   <li>{@code overdue_portfolio}: saldo pendiente de liquidaciones vencidas.</li>
 *   <li>{@code active_accounts}: clientes registrados en la tienda.</li>
 *   <li>{@code default_rate}: capital vencido / cartera total de capital,
 *       expresado en porcentaje con 7 decimales.</li>
 *   <li>Flujos: 12 meses moviles; ventas por fecha de emision y cobros por
 *       fecha de pago.</li>
 * </ul>
 */
@Service
public class AnalyticsService {

    private static final int MONTHS = 12;
    private static final int MOVEMENT_LIMIT = 100;

    private final SaleRepository sales;
    private final MonthlySettlementRepository settlements;
    private final PaymentRepository payments;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final AmortizationScheduleRepository schedules;
    private final SimulatedClock clock;

    public AnalyticsService(SaleRepository sales,
                            MonthlySettlementRepository settlements,
                            PaymentRepository payments,
                            CustomerRepository customers,
                            UserRepository users,
                            AmortizationScheduleRepository schedules,
                            SimulatedClock clock) {
        this.sales = sales;
        this.settlements = settlements;
        this.payments = payments;
        this.customers = customers;
        this.users = users;
        this.schedules = schedules;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // GET /analytics/kpis
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardKpisResponseDto kpis(Integer storeId) {
        LocalDate today = clock.today();

        List<SaleEntity> saleList = sales.findByStoreId(storeId);
        BigDecimal totalRevenue = saleList.stream()
                .filter(sale -> !"ANULADA".equals(sale.getSaleStatus()))
                .map(SaleEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);

        List<MonthlySettlementEntity> settlementList = settlements.findByStore(storeId);
        Map<Integer, BigDecimal> paidPrincipalBySettlement = payments.findByStore(storeId).stream()
                .collect(Collectors.groupingBy(
                        PaymentEntity::getSettlementId,
                        Collectors.reducing(BigDecimal.ZERO,
                                payment -> payment.getAllocatedToPrincipal() == null
                                        ? BigDecimal.ZERO
                                        : payment.getAllocatedToPrincipal(),
                                BigDecimal::add)));

        BigDecimal overduePrincipal = BigDecimal.ZERO;
        BigDecimal outstandingPrincipal = BigDecimal.ZERO;

        for (MonthlySettlementEntity settlement : settlementList) {
            BigDecimal paidPrincipal = paidPrincipalBySettlement
                    .getOrDefault(settlement.getSettlementId(), BigDecimal.ZERO);
            BigDecimal pendingPrincipal = settlement.getMonthInstallmentsAmount()
                    .subtract(paidPrincipal)
                    .max(BigDecimal.ZERO);

            outstandingPrincipal = outstandingPrincipal.add(pendingPrincipal);

            boolean overdue = settlement.getPendingBalance() != null
                    && settlement.getPendingBalance().signum() > 0
                    && settlement.getPaymentDueDate().isBefore(today);
            if (overdue) {
                overduePrincipal = overduePrincipal.add(pendingPrincipal);
            }
        }

        BigDecimal unbilledPrincipal = schedules.sumUnbilledPrincipalByStore(storeId);
        BigDecimal totalPortfolio = outstandingPrincipal.add(
                unbilledPrincipal == null ? BigDecimal.ZERO : unbilledPrincipal);

        BigDecimal defaultRate = totalPortfolio.signum() == 0
                ? BigDecimal.ZERO
                : overduePrincipal.multiply(BigDecimal.valueOf(100))
                        .divide(totalPortfolio, Amounts.RATE_SCALE, RoundingMode.HALF_UP);

        BigDecimal overduePortfolio = settlements.findOverdueByStore(storeId, today).stream()
                .map(MonthlySettlementEntity::getPendingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);

        return new DashboardKpisResponseDto(
                Amounts.money(totalRevenue),
                Amounts.money(overduePortfolio),
                customers.countByStoreId(storeId),
                Amounts.rate(defaultRate));
    }

    // ------------------------------------------------------------------
    // GET /analytics/monthly-flows
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MonthlyFlowResponseDto> monthlyFlows(Integer storeId) {
        YearMonth current = YearMonth.from(clock.today());
        Map<YearMonth, BigDecimal[]> flows = new LinkedHashMap<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            flows.put(current.minusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (SaleEntity sale : sales.findByStoreId(storeId)) {
            YearMonth month = toYearMonth(sale.getSaleDatetime());
            BigDecimal[] bucket = flows.get(month);
            if (bucket != null) {
                bucket[0] = bucket[0].add(sale.getTotalAmount());
            }
        }

        for (PaymentEntity payment : payments.findByStore(storeId)) {
            YearMonth month = toYearMonth(payment.getPaymentDatetime());
            BigDecimal[] bucket = flows.get(month);
            if (bucket != null) {
                bucket[1] = bucket[1].add(payment.getAmountPaid());
            }
        }

        List<MonthlyFlowResponseDto> result = new ArrayList<>(flows.size());
        flows.forEach((month, bucket) -> result.add(new MonthlyFlowResponseDto(
                month.toString(),
                Amounts.money(bucket[0]),
                Amounts.money(bucket[1]))));
        return result;
    }

    // ------------------------------------------------------------------
    // GET /analytics/movements
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AuditMovementResponseDto> movements(Integer storeId) {
        List<CustomerEntity> customerList = customers.findByStoreIdOrderByCustomerIdAsc(storeId);
        Map<Integer, UserEntity> usersById = users.findAllById(
                        customerList.stream().map(CustomerEntity::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        Map<Integer, String> customerNames = customerList.stream()
                .collect(Collectors.toMap(
                        CustomerEntity::getCustomerId,
                        customer -> Names.fullName(usersById.get(customer.getUserId()),
                                customer.getCustomerId())));

        List<MonthlySettlementEntity> settlementList = settlements.findByStore(storeId);
        Map<Integer, MonthlySettlementEntity> settlementsById = settlementList.stream()
                .collect(Collectors.toMap(
                        MonthlySettlementEntity::getSettlementId, Function.identity()));

        List<Movement> movements = new ArrayList<>();

        for (SaleEntity sale : sales.findByStoreId(storeId)) {
            movements.add(new Movement(
                    sale.getSaleId().longValue(),
                    sale.getSaleDatetime(),
                    MovementEventType.SALE_CREATED,
                    customerNames.getOrDefault(sale.getCustomerId(), "Cliente " + sale.getCustomerId()),
                    sale.getTotalAmount(),
                    "Venta " + ApiCodes.sale(sale.getSaleId()) + " (" + sale.getPaymentMode() + ")"));
        }

        for (MonthlySettlementEntity settlement : settlementList) {
            movements.add(new Movement(
                    1_000_000L + settlement.getSettlementId(),
                    settlement.getCutoffDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                    MovementEventType.SETTLEMENT_CUTOFF,
                    customerNames.getOrDefault(settlement.getCustomerId(),
                            "Cliente " + settlement.getCustomerId()),
                    settlement.getTotalAmountDue(),
                    "Liquidacion " + ApiCodes.settlement(settlement.getSettlementId())
                            + " del periodo " + settlement.getPeriodYear()
                            + "-" + "%02d".formatted(settlement.getPeriodMonth())));
        }

        for (PaymentEntity payment : payments.findByStore(storeId)) {
            MonthlySettlementEntity settlement = settlementsById.get(payment.getSettlementId());
            Integer customerId = settlement == null ? null : settlement.getCustomerId();
            String customerName = customerId == null
                    ? "Cliente"
                    : customerNames.getOrDefault(customerId, "Cliente " + customerId);
            movements.add(new Movement(
                    2_000_000L + payment.getPaymentId(),
                    payment.getPaymentDatetime(),
                    MovementEventType.PAYMENT_RECEIVED,
                    customerName,
                    payment.getAmountPaid(),
                    "Pago " + ApiCodes.payment(payment.getPaymentId())
                            + " (" + payment.getPaymentMethod() + ")"));
        }

        return movements.stream()
                .sorted(Comparator.comparing(Movement::timestamp).reversed()
                        .thenComparing(Comparator.comparingLong(Movement::id).reversed()))
                .limit(MOVEMENT_LIMIT)
                .map(movement -> new AuditMovementResponseDto(
                        movement.id(),
                        movement.timestamp().toString(),
                        movement.eventType(),
                        movement.customerName(),
                        Amounts.money(movement.amount()),
                        movement.description()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private record Movement(long id,
                            Instant timestamp,
                            MovementEventType eventType,
                            String customerName,
                            BigDecimal amount,
                            String description) {
    }

    private YearMonth toYearMonth(Instant instant) {
        return YearMonth.from(instant.atZone(ZoneOffset.UTC).toLocalDate());
    }
}
