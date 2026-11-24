package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upc.credito.infrastructure.persistence.entity.SaleEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<SaleEntity, Integer> {

    List<SaleEntity> findByStoreIdOrderBySaleDatetimeDesc(Integer storeId);

    List<SaleEntity> findByStoreId(Integer storeId);

    List<SaleEntity> findBySettlementIdAndPaymentMode(Integer settlementId, String paymentMode);

    List<SaleEntity> findByCustomerIdAndStoreIdOrderBySaleDatetimeDesc(Integer customerId, Integer storeId);

    Optional<SaleEntity> findBySaleIdAndStoreId(Integer saleId, Integer storeId);

    Optional<SaleEntity> findBySaleIdAndCustomerIdAndStoreId(Integer saleId,
                                                             Integer customerId,
                                                             Integer storeId);

    Optional<SaleEntity> findByStoreIdAndIdempotencyKey(Integer storeId, String idempotencyKey);

    List<SaleEntity> findByCustomerIdAndBillingCutoffDateAndSettlementIdIsNullAndPaymentMode(
            Integer customerId, LocalDate billingCutoffDate, String paymentMode);

    @Query("""
            select coalesce(sum(s.totalAmount), 0) from SaleEntity s
            where s.customerId = :customerId
              and s.paymentMode = 'MONTH_END'
              and s.settlementId is null
              and s.saleStatus <> 'CANCELLED'
            """)
    BigDecimal sumUnbilledMonthEndByCustomer(@Param("customerId") Integer customerId);

    @Query("""
            select s from SaleEntity s
            where s.storeId = :storeId
              and s.paymentMode = 'MONTH_END'
              and s.settlementId is null
              and s.billingCutoffDate <= :asOfDate
              and s.saleStatus <> 'CANCELLED'
            order by s.billingCutoffDate asc, s.saleId asc
            """)
    List<SaleEntity> findUnbilledMonthEndByStore(@Param("storeId") Integer storeId,
                                                  @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            select s from SaleEntity s
            where s.storeId = :storeId
              and s.customerId = :customerId
              and s.paymentMode = 'MONTH_END'
              and s.settlementId is null
              and s.billingCutoffDate <= :asOfDate
              and s.saleStatus <> 'CANCELLED'
            order by s.billingCutoffDate asc, s.saleId asc
            """)
    List<SaleEntity> findUnbilledMonthEndByCustomerAndStore(
            @Param("customerId") Integer customerId,
            @Param("storeId") Integer storeId,
            @Param("asOfDate") LocalDate asOfDate);
}
