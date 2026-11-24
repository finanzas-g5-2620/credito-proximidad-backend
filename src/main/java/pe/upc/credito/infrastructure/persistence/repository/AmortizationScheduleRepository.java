package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upc.credito.infrastructure.persistence.entity.AmortizationScheduleEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AmortizationScheduleRepository
        extends JpaRepository<AmortizationScheduleEntity, Integer> {

    List<AmortizationScheduleEntity> findBySaleIdOrderByInstallmentNumberAsc(Integer saleId);

    List<AmortizationScheduleEntity> findBySaleIdInOrderByInstallmentNumberAsc(Collection<Integer> saleIds);

    List<AmortizationScheduleEntity> findBySettlementIdOrderByInstallmentNumberAsc(Integer settlementId);

    /** Unbilled principal for the credit line. Summing ending balances would count the same principal repeatedly. */
    @Query("""
            select s from AmortizationScheduleEntity s
            where s.installmentStatus = 'PENDING'
              and s.settlementId is null
              and s.installmentNumber > 0
              and s.billingCutoffDate <= :asOfDate
              and s.saleId in (
                  select sale.saleId from SaleEntity sale where sale.storeId = :storeId
              )
            order by s.billingCutoffDate asc, s.dueDate asc, s.installmentNumber asc
            """)
    List<AmortizationScheduleEntity> findUnbilledByStore(@Param("storeId") Integer storeId,
                                                        @Param("asOfDate") LocalDate asOfDate);

    @Query("""
            select s from AmortizationScheduleEntity s
            where s.installmentStatus = 'PENDING'
              and s.settlementId is null
              and s.installmentNumber > 0
              and s.billingCutoffDate <= :asOfDate
              and s.saleId in (
                  select sale.saleId from SaleEntity sale
                  where sale.storeId = :storeId and sale.customerId = :customerId
              )
            order by s.billingCutoffDate asc, s.dueDate asc, s.installmentNumber asc
            """)
    List<AmortizationScheduleEntity> findUnbilledByCustomerAndStore(
            @Param("customerId") Integer customerId,
            @Param("storeId") Integer storeId,
            @Param("asOfDate") LocalDate asOfDate);

    /** Store-wide unbilled principal used in the delinquency ratio. */
    @Query("""
            select coalesce(sum(s.principalAmount), 0) from AmortizationScheduleEntity s
            where s.saleId in (
                select sale.saleId from SaleEntity sale where sale.customerId = :customerId
            )
              and s.settlementId is null
              and s.installmentStatus <> 'PAID'
              and s.installmentNumber > 0
            """)
    BigDecimal sumUnbilledPrincipal(@Param("customerId") Integer customerId);

    /**
     * Capital pendiente no facturado de todo el tenant (denominador de la
     * tasa de morosidad).
     */
    @Query("""
            select coalesce(sum(s.principalAmount), 0) from AmortizationScheduleEntity s
            where s.saleId in (
                select sale.saleId from SaleEntity sale where sale.storeId = :storeId
            )
              and s.settlementId is null
              and s.installmentStatus <> 'PAID'
              and s.installmentNumber > 0
            """)
    BigDecimal sumUnbilledPrincipalByStore(@Param("storeId") Integer storeId);
}
