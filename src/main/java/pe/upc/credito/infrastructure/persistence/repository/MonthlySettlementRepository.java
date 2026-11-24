package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upc.credito.infrastructure.persistence.entity.MonthlySettlementEntity;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MonthlySettlementRepository
        extends JpaRepository<MonthlySettlementEntity, Integer> {

    Optional<MonthlySettlementEntity> findByCustomerIdAndPeriodYearAndPeriodMonth(
            Integer customerId, Integer periodYear, Integer periodMonth);

    List<MonthlySettlementEntity> findByCustomerIdOrderByPeriodYearDescPeriodMonthDesc(Integer customerId);

    List<MonthlySettlementEntity> findByCustomerIdOrderByCutoffDateDesc(Integer customerId);

    @Query("""
            select s from MonthlySettlementEntity s
            where s.customerId = :customerId
              and s.customerId in (
                  select c.customerId from CustomerEntity c where c.storeId = :storeId
              )
            order by s.cutoffDate desc, s.settlementId desc
            """)
    List<MonthlySettlementEntity> findByCustomerIdAndStoreIdOrderByCutoffDateDesc(
            @Param("customerId") Integer customerId,
            @Param("storeId") Integer storeId);

    @Query("""
            select s from MonthlySettlementEntity s
            where s.customerId in (
                select c.customerId from CustomerEntity c where c.storeId = :storeId
            )
            order by s.cutoffDate desc, s.settlementId desc
            """)
    List<MonthlySettlementEntity> findByStore(@Param("storeId") Integer storeId);

    @Query("""
            select s from MonthlySettlementEntity s
            where s.settlementId = :settlementId
              and s.customerId in (
                  select c.customerId from CustomerEntity c where c.storeId = :storeId
              )
            """)
    Optional<MonthlySettlementEntity> findByIdAndStore(@Param("settlementId") Integer settlementId,
                                                       @Param("storeId") Integer storeId);

    @Query("""
            select s from MonthlySettlementEntity s
            where s.settlementId = :settlementId
              and s.customerId = :customerId
              and s.customerId in (
                  select c.customerId from CustomerEntity c where c.storeId = :storeId
              )
            """)
    Optional<MonthlySettlementEntity> findByIdAndCustomerAndStore(
            @Param("settlementId") Integer settlementId,
            @Param("customerId") Integer customerId,
            @Param("storeId") Integer storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from MonthlySettlementEntity s
            where s.settlementId = :settlementId
              and s.customerId in (
                  select c.customerId from CustomerEntity c where c.storeId = :storeId
              )
            """)
    Optional<MonthlySettlementEntity> findByIdAndStoreForUpdate(
            @Param("settlementId") Integer settlementId,
            @Param("storeId") Integer storeId);

    @Query("""
            select coalesce(sum(s.pendingBalance), 0) from MonthlySettlementEntity s
            where s.customerId = :customerId
            """)
    BigDecimal sumPendingBalanceByCustomer(@Param("customerId") Integer customerId);

    @Query("""
            select s from MonthlySettlementEntity s
            where s.pendingBalance > 0
              and s.paymentDueDate < :today
              and s.customerId in (
                  select c.customerId from CustomerEntity c where c.storeId = :storeId
              )
            order by s.paymentDueDate asc
            """)
    List<MonthlySettlementEntity> findOverdueByStore(@Param("storeId") Integer storeId,
                                                     @Param("today") LocalDate today);
}
