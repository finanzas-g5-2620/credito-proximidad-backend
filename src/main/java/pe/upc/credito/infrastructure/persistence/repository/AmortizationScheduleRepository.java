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

    /**
     * Cuotas pendientes de facturar de un cliente dentro del periodo comercial.
     * Una cuota ya facturada tiene settlement_id distinto de NULL.
     */
    @Query("""
            select s from AmortizationScheduleEntity s
            where s.installmentStatus = 'PENDIENTE'
              and s.settlementId is null
              and s.installmentNumber > 0
              and s.dueDate between :from and :to
              and s.saleId in (
                  select sale.saleId from SaleEntity sale where sale.customerId = :customerId
              )
            order by s.dueDate asc, s.installmentNumber asc
            """)
    List<AmortizationScheduleEntity> findBillable(@Param("customerId") Integer customerId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    /**
     * Capital pendiente de cuotas aun no facturadas (para la linea de credito).
     *
     * <p>Se suma {@code principal_amount} y no {@code ending_balance}: el saldo
     * final de cada fila es decreciente y sumarlos contaria el mismo capital
     * varias veces. La suma de las amortizaciones pendientes es el capital
     * realmente adeudado.</p>
     */
    @Query("""
            select coalesce(sum(s.principalAmount), 0) from AmortizationScheduleEntity s
            where s.saleId in (
                select sale.saleId from SaleEntity sale where sale.customerId = :customerId
            )
              and s.settlementId is null
              and s.installmentStatus <> 'PAGADA'
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
              and s.installmentStatus <> 'PAGADA'
              and s.installmentNumber > 0
            """)
    BigDecimal sumUnbilledPrincipalByStore(@Param("storeId") Integer storeId);
}
