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
import pe.upc.credito.application.support.Names;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.domain.enums.InstallmentStatus;
import pe.upc.credito.domain.enums.RateType;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Casos de uso de cobranza: corte mensual, estado de cuenta, cotizacion de
 * cancelacion y registro de pagos con prelacion legal.
 *
 * <p>Decisiones de negocio:</p>
 * <ul>
 *   <li><b>Corte:</b> el estado de cuenta del periodo se emite con fecha de
 *       corte {@code hoy} y factura las cuotas cuyo vencimiento cae en el mes
 *       siguiente (igual que el mock: corte 25/10, vencimiento 05/11). Asi se
 *       respeta el CHECK {@code payment_due_date >= cutoff_date} del cap. 8.</li>
 *   <li><b>Prelacion Art. 1257 C.C.:</b> mora/penalidad, luego interes
 *       compensatorio y finalmente capital.</li>
 *   <li><b>Mora:</b> se calcula sobre el capital pendiente con la TEA
 *       moratoria del cliente. La penalidad acumulada se persiste en
 *       {@code penalty_interest_amount} antes de imputar el pago.</li>
 *   <li><b>Pago parcial:</b> rechazado (cap. 7.5). El abono debe cubrir la
 *       totalidad del saldo exigible; si es menor, la operacion no se registra.</li>
 *   <li><b>Sobre-pago:</b> aceptado (cap. 7.5). El excedente no se imputa a la
 *       liquidacion: se acumula en {@code customers.favour_balance} como saldo
 *       a favor del consumidor. La fila de {@code payments} registra como
 *       {@code amount_paid} solo el importe aplicado a la deuda para respetar
 *       el CHECK {@code ck_payment_imputation}.</li>
 * </ul>
 */
@Service
public class SettlementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);

    private final MonthlySettlementRepository settlements;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final AmortizationScheduleRepository schedules;
    private final PaymentRepository payments;
    private final SaleRepository sales;
    private final FinancialMathEngine engine;
    private final SimulatedClock clock;

    public SettlementService(MonthlySettlementRepository settlements,
                             CustomerRepository customers,
                             UserRepository users,
                             AmortizationScheduleRepository schedules,
                             PaymentRepository payments,
                             SaleRepository sales,
                             FinancialMathEngine engine,
                             SimulatedClock clock) {
        this.settlements = settlements;
        this.customers = customers;
        this.users = users;
        this.schedules = schedules;
        this.payments = payments;
        this.sales = sales;
        this.engine = engine;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // GET /settlements y GET /settlements/{id}
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SettlementResponseDto> list(Integer storeId) {
        List<MonthlySettlementEntity> settlementList = settlements.findByStore(storeId);
        if (settlementList.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<PaymentEntity>> paymentsBySettlement = payments.findByStore(storeId).stream()
                .collect(Collectors.groupingBy(PaymentEntity::getSettlementId));

        Map<Integer, CustomerEntity> customersById = customers.findAllById(
                        settlementList.stream().map(MonthlySettlementEntity::getCustomerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CustomerEntity::getCustomerId, Function.identity()));

        Map<Integer, UserEntity> usersById = users.findAllById(
                        customersById.values().stream().map(CustomerEntity::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));

        return settlementList.stream()
                .map(settlement -> {
                    CustomerEntity customer = customersById.get(settlement.getCustomerId());
                    UserEntity user = customer == null ? null : usersById.get(customer.getUserId());
                    return toDto(settlement, customer, user,
                            paymentsBySettlement.getOrDefault(settlement.getSettlementId(), List.of()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public SettlementResponseDto get(Integer settlementId, Integer storeId) {
        MonthlySettlementEntity settlement = requireSettlement(settlementId, storeId);
        CustomerEntity customer = customers.findById(settlement.getCustomerId()).orElse(null);
        UserEntity user = customer == null ? null : users.findById(customer.getUserId()).orElse(null);
        List<PaymentEntity> history = payments.findBySettlementIdOrderByPaymentDatetimeAsc(settlementId);
        return toDto(settlement, customer, user, history);
    }

    // ------------------------------------------------------------------
    // GET /settlements/{id}/payoff-quote
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PayoffQuoteResponseDto payoffQuote(Integer settlementId, Integer storeId, String targetDate) {
        MonthlySettlementEntity settlement = requireSettlement(settlementId, storeId);
        if (isPaid(settlement)) {
            throw new BusinessException("SETTLEMENT_ALREADY_PAID",
                    "La liquidacion " + ApiCodes.settlement(settlementId) + " ya fue cancelada",
                    HttpStatus.CONFLICT);
        }

        CustomerEntity customer = customers.findById(settlement.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado: " + settlement.getCustomerId()));

        LocalDate calculationDate = parseDate(targetDate, clock.today());
        List<PaymentEntity> history = payments.findBySettlementIdOrderByPaymentDatetimeAsc(settlementId);
        Quote quote = quote(settlement, customer, calculationDate, history);

        return new PayoffQuoteResponseDto(
                settlement.getSettlementId(),
                calculationDate.toString(),
                quote.daysOverdue(),
                Amounts.money(quote.principal()),
                Amounts.money(quote.interest()),
                Amounts.money(quote.moratorium()),
                Amounts.money(quote.total()));
    }

    // ------------------------------------------------------------------
    // POST /settlements/{id}/pay
    // ------------------------------------------------------------------

    @Transactional
    public PaymentResponseDto pay(Integer settlementId, Integer storeId, PaymentRequestDto request) {
        MonthlySettlementEntity settlement = requireSettlement(settlementId, storeId);
        if (isPaid(settlement)) {
            throw new BusinessException("SETTLEMENT_ALREADY_PAID",
                    "La liquidacion " + ApiCodes.settlement(settlementId) + " ya fue cancelada",
                    HttpStatus.CONFLICT);
        }

        CustomerEntity customer = customers.findById(settlement.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado: " + settlement.getCustomerId()));

        BigDecimal amount = Amounts.money(request.amount());
        if (amount.signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "El monto debe ser mayor a cero",
                    HttpStatus.BAD_REQUEST);
        }

        LocalDate today = clock.today();
        List<PaymentEntity> history = payments.findBySettlementIdOrderByPaymentDatetimeAsc(settlementId);
        Quote quote = quote(settlement, customer, today, history);
        BigDecimal totalDue = quote.total();

        if (totalDue.signum() == 0) {
            throw new BusinessException("SETTLEMENT_ALREADY_PAID",
                    "La liquidacion " + ApiCodes.settlement(settlementId)
                            + " no tiene saldo exigible pendiente",
                    HttpStatus.CONFLICT);
        }

        // Cap. 7.5: el sistema no admite pagos parciales. El importe recibido
        // debe cubrir la totalidad de la deuda exigible consolidada.
        if (amount.compareTo(totalDue) < 0) {
            throw new BusinessException("PARTIAL_PAYMENT_REJECTED",
                    "La politica de cobranza exige la cancelacion integral de la liquidacion (S/ "
                            + Amounts.money(totalDue) + "). Se recibio: S/ "
                            + Amounts.money(amount) + ".",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Cap. 7.5: el excedente se registra como saldo a favor del consumidor.
        // La imputacion (y el CHECK ck_payment_imputation) solo considera el
        // importe aplicado a la deuda.
        BigDecimal received = amount;
        BigDecimal applied = totalDue;
        BigDecimal surplus = received.subtract(applied)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);

        // Prelacion legal Art. 1257 C.C.: mora -> interes compensatorio -> capital
        BigDecimal available = applied;
        BigDecimal toPenalty = available.min(quote.moratorium());
        available = available.subtract(toPenalty);
        BigDecimal toInterest = available.min(quote.interest());
        available = available.subtract(toInterest);
        BigDecimal toPrincipal = available.min(quote.principal());
        available = available.subtract(toPrincipal);
        if (available.signum() != 0) {
            // Protege el CHECK del pago: la imputacion debe agotar el importe aplicado.
            throw new BusinessException("INVALID_IMPUTATION",
                    "No fue posible imputar el pago a la liquidacion", HttpStatus.CONFLICT);
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setSettlementId(settlementId);
        payment.setPaymentDatetime(clock.now());
        payment.setAmountPaid(applied);
        payment.setCalculatedOverdueDays(quote.daysOverdue());
        payment.setAllocatedToPenalty(toPenalty);
        payment.setAllocatedToInterest(toInterest);
        payment.setAllocatedToPrincipal(toPrincipal);
        payment.setPaymentMethod(request.payment_method().name());
        payment.setReferenceNumber(normalizeReference(request.reference_number()));
        payments.saveAndFlush(payment);

        if (surplus.signum() > 0) {
            BigDecimal currentFavour = customer.getFavourBalance() == null
                    ? ZERO
                    : customer.getFavourBalance();
            customer.setFavourBalance(currentFavour.add(surplus)
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
            customers.save(customer);
        }

        BigDecimal newPending = totalDue.subtract(applied)
                .max(ZERO)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        boolean fullyPaid = newPending.signum() == 0;
        boolean overdue = today.isAfter(settlement.getPaymentDueDate());

        // Las columnas de la liquidacion guardan importes acumulados:
        // total_amount_due - paid_amount = pending_balance. La penalidad se
        // acumula y luego se le resta lo ya imputado a mora.
        BigDecimal previousPaid = settlement.getPaidAmount();
        BigDecimal paidPenaltyBefore = sumPayments(history, PaymentEntity::getAllocatedToPenalty);

        settlement.setPenaltyInterestAmount(quote.moratorium().add(paidPenaltyBefore)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
        settlement.setPaidAmount(previousPaid.add(applied)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
        settlement.setTotalAmountDue(previousPaid.add(totalDue)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP));
        settlement.setPendingBalance(newPending);
        settlement.setSettlementStatus(fullyPaid
                ? SettlementStatus.PAGADA_TOTAL.name()
                : (overdue ? SettlementStatus.EN_MORA.name() : SettlementStatus.EMITIDA.name()));
        settlements.save(settlement);

        List<AmortizationScheduleEntity> billedRows =
                schedules.findBySettlementIdOrderByInstallmentNumberAsc(settlementId);
        if (fullyPaid) {
            for (AmortizationScheduleEntity row : billedRows) {
                row.setInstallmentStatus(InstallmentStatus.PAGADA.name());
            }
            schedules.saveAll(billedRows);
            completeSalesIfPaid(billedRows);
        } else if (overdue) {
            for (AmortizationScheduleEntity row : billedRows) {
                row.setInstallmentStatus(InstallmentStatus.EN_MORA.name());
            }
            schedules.saveAll(billedRows);
        }

        BigDecimal favourBalance = customer.getFavourBalance() == null
                ? ZERO
                : customer.getFavourBalance();

        return new PaymentResponseDto(
                payment.getPaymentId(),
                ApiCodes.payment(payment.getPaymentId()),
                settlementId,
                settlement.getCustomerId(),
                Amounts.money(applied),
                payment.getPaymentDatetime().toString(),
                request.payment_method(),
                payment.getReferenceNumber(),
                ApiCodes.receipt(payment.getPaymentId()),
                Amounts.money(received),
                Amounts.money(favourBalance));
    }

    // ------------------------------------------------------------------
    // POST /__mock/run-cutoff
    // ------------------------------------------------------------------

    @Transactional
    public RunCutoffResponseDto runCutoff(Integer storeId) {
        LocalDate today = clock.today();
        YearMonth cutoffPeriod = YearMonth.from(today);
        YearMonth billingPeriod = cutoffPeriod.plusMonths(1);
        LocalDate from = billingPeriod.atDay(1);
        LocalDate to = billingPeriod.atEndOfMonth();

        int generated = 0;
        for (CustomerEntity customer : customers.findByStoreIdOrderByCustomerIdAsc(storeId)) {
            boolean alreadyBilled = settlements
                    .findByCustomerIdAndPeriodYearAndPeriodMonth(
                            customer.getCustomerId(), cutoffPeriod.getYear(), cutoffPeriod.getMonthValue())
                    .isPresent();
            if (alreadyBilled) {
                continue;
            }

            List<AmortizationScheduleEntity> rows =
                    schedules.findBillable(customer.getCustomerId(), from, to);
            if (rows.isEmpty()) {
                continue;
            }

            BigDecimal principal = sumRows(rows, AmortizationScheduleEntity::getPrincipalAmount);
            BigDecimal interest = sumRows(rows, AmortizationScheduleEntity::getCompensatoryInterest);
            LocalDate dueDate = rows.stream()
                    .map(AmortizationScheduleEntity::getDueDate)
                    .min(LocalDate::compareTo)
                    .orElse(to);

            int overdueDays = today.isAfter(dueDate)
                    ? (int) ChronoUnit.DAYS.between(dueDate, today)
                    : 0;
            BigDecimal penalty = engine.overduePenalty(principal, penaltyTea(customer), overdueDays);
            BigDecimal total = principal.add(interest).add(penalty)
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
            boolean overdue = today.isAfter(dueDate);

            MonthlySettlementEntity settlement = new MonthlySettlementEntity();
            settlement.setCustomerId(customer.getCustomerId());
            settlement.setPeriodMonth(cutoffPeriod.getMonthValue());
            settlement.setPeriodYear(cutoffPeriod.getYear());
            settlement.setCutoffDate(today);
            settlement.setPaymentDueDate(dueDate);
            // Las compras a credito siempre se financian en cuotas: su importe
            // ya esta representado por month_installments_amount. Esta columna
            // queda en cero para no duplicar la deuda.
            settlement.setMonthPurchasesAmount(ZERO);
            settlement.setMonthInstallmentsAmount(principal);
            settlement.setCompensatoryInterestAmount(interest);
            settlement.setPenaltyInterestAmount(penalty);
            settlement.setTotalAmountDue(total);
            settlement.setPaidAmount(ZERO);
            settlement.setPendingBalance(total);
            settlement.setSettlementStatus(overdue
                    ? SettlementStatus.EN_MORA.name()
                    : SettlementStatus.EMITIDA.name());
            settlements.saveAndFlush(settlement);

            for (AmortizationScheduleEntity row : rows) {
                row.setSettlementId(settlement.getSettlementId());
                row.setInstallmentStatus(overdue
                        ? InstallmentStatus.EN_MORA.name()
                        : InstallmentStatus.FACTURADA.name());
            }
            schedules.saveAll(rows);
            generated++;
        }

        String periodLabel = "%04d-%02d".formatted(cutoffPeriod.getYear(), cutoffPeriod.getMonthValue());
        String message = generated == 0
                ? "Corte del periodo " + periodLabel + " sin cuotas pendientes de facturar"
                : "Corte ejecutado para el periodo " + periodLabel + ": "
                        + generated + " liquidaciones generadas";
        return new RunCutoffResponseDto(message, generated);
    }

    // ------------------------------------------------------------------
    // Calculo del saldo exigible
    // ------------------------------------------------------------------

    /**
     * Saldo exigible a una fecha de calculo.
     *
     * @param daysOverdue dias totales desde el vencimiento
     * @param principal   capital pendiente
     * @param interest    interes compensatorio pendiente
     * @param moratorium  penalidad pendiente + mora devengada desde el ultimo corte de calculo
     */
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

        BigDecimal principal = nonNegative(settlement.getMonthInstallmentsAmount().subtract(paidPrincipal));
        BigDecimal interest = nonNegative(settlement.getCompensatoryInterestAmount().subtract(paidInterest));
        BigDecimal storedPenalty = nonNegative(settlement.getPenaltyInterestAmount().subtract(paidPenalty));

        int daysOverdue = 0;
        if (principal.signum() > 0 && calculationDate.isAfter(settlement.getPaymentDueDate())) {
            daysOverdue = (int) ChronoUnit.DAYS.between(settlement.getPaymentDueDate(), calculationDate);
        }

        // La penalidad se devenga sobre el capital pendiente desde el ultimo
        // punto de calculo (ultimo pago o fecha de corte). El historial puede
        // venir en orden ascendente o descendente segun el repositorio usado.
        LocalDate lastAccrual = history.stream()
                .map(PaymentEntity::getPaymentDatetime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate())
                .orElse(settlement.getCutoffDate());
        LocalDate accrualFrom = lastAccrual.isAfter(settlement.getPaymentDueDate())
                ? lastAccrual
                : settlement.getPaymentDueDate();
        int additionalDays = calculationDate.isAfter(accrualFrom)
                ? (int) ChronoUnit.DAYS.between(accrualFrom, calculationDate)
                : 0;

        BigDecimal additionalPenalty = engine.overduePenalty(
                principal, penaltyTea(customer), additionalDays);
        BigDecimal moratorium = storedPenalty.add(additionalPenalty)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);

        return new Quote(daysOverdue, principal, interest, moratorium, calculationDate);
    }

    private SettlementResponseDto toDto(MonthlySettlementEntity settlement,
                                        CustomerEntity customer,
                                        UserEntity user,
                                        List<PaymentEntity> history) {
        LocalDate today = clock.today();
        boolean paid = isPaid(settlement);
        Quote quote = paid ? null : quote(settlement, customer, today, history);
        String status = paid
                ? "PAID"
                : (today.isAfter(settlement.getPaymentDueDate()) ? "OVERDUE" : "PENDING");

        // total_due es el importe facturado (no el saldo pendiente): el
        // frontend muestra la misma cifra antes y despues del pago. Para las
        // liquidaciones abiertas se suman los pagos ya imputados para no
        // perder el historico cuando hubo abonos parciales.
        BigDecimal penaltyAmount;
        BigDecimal totalDue;
        if (paid) {
            penaltyAmount = nonNegative(settlement.getPenaltyInterestAmount());
            totalDue = nonNegative(settlement.getTotalAmountDue());
        } else {
            BigDecimal paidPenalty = sumPayments(history, PaymentEntity::getAllocatedToPenalty);
            penaltyAmount = quote.moratorium().add(paidPenalty)
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
            totalDue = nonNegative(settlement.getPaidAmount()).add(quote.total())
                    .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return new SettlementResponseDto(
                settlement.getSettlementId(),
                ApiCodes.settlement(settlement.getSettlementId()),
                settlement.getCustomerId(),
                Names.fullName(user, settlement.getCustomerId()),
                settlement.getCutoffDate().toString(),
                settlement.getPaymentDueDate().toString(),
                Amounts.money(settlement.getMonthInstallmentsAmount()),
                Amounts.money(settlement.getCompensatoryInterestAmount()),
                Amounts.money(penaltyAmount),
                Amounts.money(totalDue),
                status,
                paid ? lastPaymentDate(history) : null);
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private MonthlySettlementEntity requireSettlement(Integer settlementId, Integer storeId) {
        return settlements.findByIdAndStore(settlementId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Liquidacion no encontrada: " + settlementId));
    }

    private boolean isPaid(MonthlySettlementEntity settlement) {
        return SettlementStatus.PAGADA_TOTAL.name().equals(settlement.getSettlementStatus())
                || settlement.getPendingBalance() == null
                || settlement.getPendingBalance().signum() == 0;
    }

    private BigDecimal penaltyTea(CustomerEntity customer) {
        if (customer == null) {
            return ZERO;
        }
        RateType type = RateType.valueOf(customer.getPenaltyRateType());
        return engine.normalize(customer.getPenaltyRateValue(), type,
                FinancialMathEngine.COMMERCIAL_YEAR_DAYS).tea();
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("INVALID_DATE",
                    "La fecha de calculo no es valida: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeReference(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal sumRows(List<AmortizationScheduleEntity> rows,
                               Function<AmortizationScheduleEntity, BigDecimal> getter) {
        return rows.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumPayments(List<PaymentEntity> history,
                                   Function<PaymentEntity, BigDecimal> getter) {
        return history.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.signum() < 0 ? ZERO : value.setScale(Amounts.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String lastPaymentDate(List<PaymentEntity> history) {
        return history.stream()
                .map(PaymentEntity::getPaymentDatetime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate().toString())
                .orElse(null);
    }

    /** Marca la venta como completada cuando todas sus cuotas fueron pagadas. */
    private void completeSalesIfPaid(List<AmortizationScheduleEntity> paidRows) {
        Set<Integer> saleIds = paidRows.stream()
                .map(AmortizationScheduleEntity::getSaleId)
                .collect(Collectors.toSet());

        for (Integer saleId : saleIds) {
            List<AmortizationScheduleEntity> saleRows =
                    schedules.findBySaleIdOrderByInstallmentNumberAsc(saleId);
            boolean allPaid = saleRows.stream()
                    .filter(row -> row.getInstallmentNumber() != null && row.getInstallmentNumber() > 0)
                    .allMatch(row -> InstallmentStatus.PAGADA.name().equals(row.getInstallmentStatus()));
            if (allPaid) {
                SaleEntity sale = sales.findById(saleId).orElse(null);
                if (sale != null) {
                    sale.setSaleStatus(SaleStatus.REGISTRADA.name());
                    sales.save(sale);
                }
            }
        }
    }
}
