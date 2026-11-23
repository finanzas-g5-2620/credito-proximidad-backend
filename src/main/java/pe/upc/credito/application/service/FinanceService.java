package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.upc.credito.application.dto.SimulateScheduleRequestDto;
import pe.upc.credito.application.dto.SimulateScheduleResponseDto;
import pe.upc.credito.application.dto.SimulatedInstallmentDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.support.Amounts;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.domain.model.SimulatedSchedule;
import pe.upc.credito.domain.service.FinancialMathEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Caso de uso: simular un cronograma frances (POST /finance/simulate). */
@Service
public class FinanceService {

    private final FinancialMathEngine engine;
    private final SimulatedClock clock;

    public FinanceService(FinancialMathEngine engine, SimulatedClock clock) {
        this.engine = engine;
        this.clock = clock;
    }

    public SimulateScheduleResponseDto simulate(SimulateScheduleRequestDto request) {
        BigDecimal capital = Amounts.money(request.initial_capital());
        BigDecimal rate = Amounts.rate(request.rate_value());
        int compoundingDays = request.compounding_days() == null
                ? FinancialMathEngine.COMMERCIAL_YEAR_DAYS
                : request.compounding_days();
        int graceDays = request.grace_days() == null ? 0 : request.grace_days();

        LocalDate firstDueDate;
        if (request.first_due_date() == null || request.first_due_date().isBlank()) {
            firstDueDate = clock.today();
        } else {
            try {
                firstDueDate = LocalDate.parse(request.first_due_date());
            } catch (DateTimeParseException ex) {
                throw new BusinessException("INVALID_DATE",
                        "La fecha de la primera cuota no es valida: " + request.first_due_date(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        SimulatedSchedule schedule = engine.simulate(
                capital,
                rate,
                request.rate_type(),
                compoundingDays,
                request.total_installments(),
                graceDays,
                firstDueDate);

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
                                i.dueDate().toString(),
                                Amounts.money(i.paymentAmount()),
                                Amounts.money(i.principalAmount()),
                                Amounts.money(i.interestAmount()),
                                Amounts.money(i.balanceAmount()),
                                i.gracePeriod()))
                        .toList());
    }
}
