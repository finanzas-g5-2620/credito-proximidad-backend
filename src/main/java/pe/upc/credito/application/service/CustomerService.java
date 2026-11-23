package pe.upc.credito.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.CustomerResponseDto;
import pe.upc.credito.application.support.Amounts;
import pe.upc.credito.application.support.ApiCodes;
import pe.upc.credito.application.support.Names;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.AmortizationScheduleRepository;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.MonthlySettlementRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Caso de uso: listar clientes con su linea de credito (GET /customers). */
@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final UserRepository users;
    private final MonthlySettlementRepository settlements;
    private final AmortizationScheduleRepository schedules;
    private final SimulatedClock clock;

    public CustomerService(CustomerRepository customers,
                           UserRepository users,
                           MonthlySettlementRepository settlements,
                           AmortizationScheduleRepository schedules,
                           SimulatedClock clock) {
        this.customers = customers;
        this.users = users;
        this.settlements = settlements;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponseDto> list(Integer storeId) {
        List<CustomerEntity> customerList = customers.findByStoreIdOrderByCustomerIdAsc(storeId);
        if (customerList.isEmpty()) {
            return List.of();
        }

        Map<Integer, UserEntity> usersById = users.findAllById(
                        customerList.stream().map(CustomerEntity::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));

        return customerList.stream().map(customer -> {
            BigDecimal debt = settlements.sumPendingBalanceByCustomer(customer.getCustomerId())
                    .add(schedules.sumUnbilledPrincipal(customer.getCustomerId()));
            boolean allowsMonthly = customers
                    .countOverdueSettlements(customer.getCustomerId(), clock.today()) == 0;

            return new CustomerResponseDto(
                    customer.getCustomerId(),
                    Names.fullName(usersById.get(customer.getUserId()), customer.getCustomerId()),
                    ApiCodes.documentNumber(customer.getCustomerId()),
                    Amounts.money(customer.getCreditLimit()),
                    Amounts.money(debt),
                    allowsMonthly,
                    Amounts.money(customer.getFavourBalance()));
        }).toList();
    }
}
