package pe.upc.credito.application.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.application.dto.CustomerCreateRequestDto;
import pe.upc.credito.application.dto.CustomerProfileResponseDto;
import pe.upc.credito.application.dto.CustomerResponseDto;
import pe.upc.credito.application.dto.CustomerUpdateRequestDto;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.exception.ResourceNotFoundException;
import pe.upc.credito.application.support.Amounts;
import pe.upc.credito.application.support.ApiCodes;
import pe.upc.credito.application.support.Names;
import pe.upc.credito.application.support.SimulatedClock;
import pe.upc.credito.domain.enums.RateType;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.AmortizationScheduleRepository;
import pe.upc.credito.infrastructure.persistence.repository.CustomerRepository;
import pe.upc.credito.infrastructure.persistence.repository.MonthlySettlementRepository;
import pe.upc.credito.infrastructure.persistence.repository.SaleRepository;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class CustomerService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final Set<Integer> RATE_PERIODS = Set.of(1, 15, 30, 360);
    private static final Set<Integer> CAPITALIZATION_PERIODS = Set.of(1, 7, 15, 30, 60, 90, 180, 360);

    private final CustomerRepository customers;
    private final UserRepository users;
    private final MonthlySettlementRepository settlements;
    private final AmortizationScheduleRepository schedules;
    private final SaleRepository sales;
    private final SimulatedClock clock;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customers,
                           UserRepository users,
                           MonthlySettlementRepository settlements,
                           AmortizationScheduleRepository schedules,
                           SaleRepository sales,
                           SimulatedClock clock,
                           PasswordEncoder passwordEncoder) {
        this.customers = customers;
        this.users = users;
        this.settlements = settlements;
        this.schedules = schedules;
        this.sales = sales;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
    }

    /** Checkout projection consumed by GET /sales/customers. */
    @Transactional(readOnly = true)
    public List<CustomerResponseDto> list(Integer storeId) {
        return customers.findByStoreIdOrderByCustomerIdAsc(storeId).stream().map(customer -> {
            UserEntity user = users.findById(customer.getUserId()).orElse(null);
            return new CustomerResponseDto(
                    customer.getCustomerId(),
                    Names.fullName(user, customer.getCustomerId()),
                    ApiCodes.documentNumber(customer.getCustomerId()),
                    Amounts.money(customer.getCreditLimit()),
                    Amounts.money(currentDebt(customer.getCustomerId())),
                    customers.countOverdueSettlements(customer.getCustomerId(), clock.today()) == 0,
                    Amounts.money(customer.getFavourBalance()),
                    customer.getMaxCreditMonths(),
                    customer.getCompensatoryRateType(),
                    Amounts.rate(customer.getCompensatoryRateValue()),
                    customer.getCompensatoryRatePeriodDays(),
                    customer.getCompensatoryCapitalizationDays());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerProfileResponseDto> listProfiles(Integer storeId) {
        return customers.findByStoreIdOrderByCustomerIdAsc(storeId).stream().map(this::toProfile).toList();
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponseDto getProfile(Integer storeId, Integer id) {
        return toProfile(requireCustomer(storeId, id));
    }

    @Transactional
    public CustomerProfileResponseDto create(Integer storeId, CustomerCreateRequestDto request) {
        validateProfileValues(request.credit_limit(), request.max_credit_months(),
                request.cutoff_day(), request.payment_due_day());
        validateTerms(request.compensatory_rate_type(), request.compensatory_rate_period_days(),
                request.compensatory_capitalization_days(), request.penalty_rate_type(),
                request.penalty_rate_period_days(), request.penalty_capitalization_days(), request.credit_currency());
        ensureUserAvailable(request.username(), request.email());

        UserEntity user = new UserEntity();
        user.setStoreId(storeId);
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.first_name().trim());
        user.setLastName(request.last_name().trim());
        user.setEmail(request.email().trim());
        user.setUserType("CUSTOMER");
        user.setStatus("ACTIVE");
        UserEntity savedUser = users.saveAndFlush(user);

        CustomerEntity customer = new CustomerEntity();
        customer.setStoreId(storeId);
        customer.setUserId(savedUser.getUserId());
        customer.setHomeAddress(request.home_address().trim());
        customer.setPhoneNumber(request.phone_number().trim());
        customer.setCreditLimit(request.credit_limit());
        customer.setMaxCreditMonths(request.max_credit_months());
        customer.setCutoffDay(request.cutoff_day());
        customer.setPaymentDueDay(request.payment_due_day());
        customer.setCompensatoryRateType(request.compensatory_rate_type().name());
        customer.setCompensatoryRatePeriodDays(request.compensatory_rate_period_days());
        customer.setCompensatoryCapitalizationDays(request.compensatory_capitalization_days());
        customer.setCompensatoryRateValue(request.compensatory_rate_value());
        customer.setPenaltyRateType(request.penalty_rate_type().name());
        customer.setPenaltyRatePeriodDays(request.penalty_rate_period_days());
        customer.setPenaltyCapitalizationDays(request.penalty_capitalization_days());
        customer.setPenaltyRateValue(request.penalty_rate_value());
        customer.setCreditCurrency("PEN");
        customer.setFavourBalance(ZERO_MONEY);
        return toProfile(customers.saveAndFlush(customer));
    }

    @Transactional
    public CustomerProfileResponseDto update(Integer storeId, Integer id, CustomerUpdateRequestDto request) {
        CustomerEntity customer = customers.findByCustomerIdAndStoreIdForUpdate(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        validateProfileValues(request.credit_limit(), request.max_credit_months(),
                request.cutoff_day(), request.payment_due_day());
        validateTerms(request.compensatory_rate_type(), request.compensatory_rate_period_days(),
                request.compensatory_capitalization_days(), request.penalty_rate_type(),
                request.penalty_rate_period_days(), request.penalty_capitalization_days(), request.credit_currency());

        BigDecimal debt = currentDebt(customer.getCustomerId());
        boolean hasActiveDebt = debt.signum() > 0;
        if (hasActiveDebt && hasRateOrCycleChanges(customer, request)) {
            throw new BusinessException("ACTIVE_DEBT_TERMS_LOCKED",
                    "Rate and billing-cycle terms cannot change while the customer has outstanding debt.",
                    HttpStatus.CONFLICT);
        }
        if (request.credit_limit().compareTo(debt) < 0) {
            throw new BusinessException("CREDIT_LIMIT_BELOW_DEBT",
                    "Credit limit cannot be lower than the current outstanding debt.", HttpStatus.CONFLICT);
        }

        customer.setHomeAddress(request.home_address().trim());
        customer.setPhoneNumber(request.phone_number().trim());
        customer.setCreditLimit(request.credit_limit());
        customer.setMaxCreditMonths(request.max_credit_months());
        if (!hasActiveDebt) {
            customer.setCutoffDay(request.cutoff_day());
            customer.setPaymentDueDay(request.payment_due_day());
            customer.setCompensatoryRateType(request.compensatory_rate_type().name());
            customer.setCompensatoryRatePeriodDays(request.compensatory_rate_period_days());
            customer.setCompensatoryCapitalizationDays(request.compensatory_capitalization_days());
            customer.setCompensatoryRateValue(request.compensatory_rate_value());
            customer.setPenaltyRateType(request.penalty_rate_type().name());
            customer.setPenaltyRatePeriodDays(request.penalty_rate_period_days());
            customer.setPenaltyCapitalizationDays(request.penalty_capitalization_days());
            customer.setPenaltyRateValue(request.penalty_rate_value());
        }
        customer.setCreditCurrency("PEN");
        return toProfile(customers.save(customer));
    }

    @Transactional(readOnly = true)
    public BigDecimal currentDebt(Integer customerId) {
        BigDecimal pendingSettlements = settlements.sumPendingBalanceByCustomer(customerId);
        BigDecimal unbilledInstallments = schedules.sumUnbilledPrincipal(customerId);
        BigDecimal unbilledMonthEnd = sales.sumUnbilledMonthEndByCustomer(customerId);
        return pendingSettlements.add(unbilledInstallments).add(unbilledMonthEnd);
    }

    private CustomerEntity requireCustomer(Integer storeId, Integer customerId) {
        return customers.findByCustomerIdAndStoreId(customerId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
    }

    private CustomerProfileResponseDto toProfile(CustomerEntity customer) {
        UserEntity user = users.findById(customer.getUserId()).orElse(null);
        return new CustomerProfileResponseDto(
                customer.getCustomerId(), customer.getStoreId(), customer.getUserId(),
                user == null ? null : user.getUsername(), user == null ? null : user.getEmail(),
                Names.fullName(user, customer.getCustomerId()), customer.getHomeAddress(), customer.getPhoneNumber(),
                customer.getCreditLimit(), customer.getMaxCreditMonths(), customer.getCutoffDay(),
                customer.getPaymentDueDay(), customer.getCompensatoryRateType(),
                customer.getCompensatoryRatePeriodDays(), customer.getCompensatoryCapitalizationDays(),
                customer.getCompensatoryRateValue(), customer.getPenaltyRateType(),
                customer.getPenaltyRatePeriodDays(), customer.getPenaltyCapitalizationDays(),
                customer.getPenaltyRateValue(), customer.getCreditCurrency(), customer.getFavourBalance());
    }

    private void ensureUserAvailable(String username, String email) {
        if (users.existsByUsernameIgnoreCase(username.trim())) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "A user with this username already exists.", HttpStatus.CONFLICT);
        }
        if (users.existsByEmailIgnoreCase(email.trim())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "A user with this email already exists.", HttpStatus.CONFLICT);
        }
    }

    private void validateTerms(RateType compensatoryType,
                               Integer compensatoryPeriod,
                               Integer compensatoryCapitalization,
                               RateType penaltyType,
                               Integer penaltyPeriod,
                               Integer penaltyCapitalization,
                               String currency) {
        if (!RATE_PERIODS.contains(compensatoryPeriod) || !RATE_PERIODS.contains(penaltyPeriod)) {
            throw new BusinessException("INVALID_RATE_PERIOD", "Rate period must be 1, 15, 30 or 360 days.", HttpStatus.BAD_REQUEST);
        }
        validateCapitalization(compensatoryType, compensatoryCapitalization, "compensatory");
        validateCapitalization(penaltyType, penaltyCapitalization, "penalty");
        if (!"PEN".equals(currency)) {
            throw new BusinessException("UNSUPPORTED_CURRENCY", "Only PEN credit accounts are supported.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateProfileValues(BigDecimal creditLimit,
                                       Integer maxCreditMonths,
                                       Integer cutoffDay,
                                       Integer paymentDueDay) {
        if (creditLimit == null || creditLimit.signum() < 0) {
            throw new BusinessException("INVALID_CREDIT_LIMIT", "Credit limit cannot be negative.", HttpStatus.BAD_REQUEST);
        }
        if (maxCreditMonths == null || maxCreditMonths < 1 || maxCreditMonths > 36) {
            throw new BusinessException("INVALID_CREDIT_TERM", "Maximum credit term must be between 1 and 36 months.", HttpStatus.BAD_REQUEST);
        }
        if (cutoffDay == null || cutoffDay < 1 || cutoffDay > 30
                || paymentDueDay == null || paymentDueDay < 1 || paymentDueDay > 30) {
            throw new BusinessException("INVALID_BILLING_CYCLE", "Cutoff and due days must be between 1 and 30.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateCapitalization(RateType type, Integer capitalization, String name) {
        if (type == RateType.NOMINAL) {
            if (capitalization == null || !CAPITALIZATION_PERIODS.contains(capitalization)) {
                throw new BusinessException("INVALID_CAPITALIZATION_PERIOD",
                        "Nominal " + name + " rate requires a supported capitalization period.", HttpStatus.BAD_REQUEST);
            }
        } else if (capitalization != null) {
            throw new BusinessException("UNEXPECTED_CAPITALIZATION_PERIOD",
                    "Effective rates must not define a capitalization period.", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean hasRateOrCycleChanges(CustomerEntity existing, CustomerUpdateRequestDto request) {
        return !Objects.equals(existing.getCutoffDay(), request.cutoff_day())
                || !Objects.equals(existing.getPaymentDueDay(), request.payment_due_day())
                || !Objects.equals(existing.getCompensatoryRateType(), request.compensatory_rate_type().name())
                || !Objects.equals(existing.getCompensatoryRatePeriodDays(), request.compensatory_rate_period_days())
                || !Objects.equals(existing.getCompensatoryCapitalizationDays(), request.compensatory_capitalization_days())
                || existing.getCompensatoryRateValue().compareTo(request.compensatory_rate_value()) != 0
                || !Objects.equals(existing.getPenaltyRateType(), request.penalty_rate_type().name())
                || !Objects.equals(existing.getPenaltyRatePeriodDays(), request.penalty_rate_period_days())
                || !Objects.equals(existing.getPenaltyCapitalizationDays(), request.penalty_capitalization_days())
                || existing.getPenaltyRateValue().compareTo(request.penalty_rate_value()) != 0;
    }
}
