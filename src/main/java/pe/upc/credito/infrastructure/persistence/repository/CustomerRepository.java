package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Integer> {

    List<CustomerEntity> findByStoreIdOrderByCustomerIdAsc(Integer storeId);

    Optional<CustomerEntity> findByCustomerIdAndStoreId(Integer customerId, Integer storeId);

    long countByStoreId(Integer storeId);

    /**
     * Cuentas con al menos una liquidacion vencida e impaga.
     * Se usa para bloquear la modalidad FINANCIADO_MENSUAL.
     */
    @Query("""
            select count(s) from MonthlySettlementEntity s
            where s.customerId = :customerId
              and s.pendingBalance > 0
              and s.paymentDueDate < :today
            """)
    long countOverdueSettlements(@Param("customerId") Integer customerId,
                                 @Param("today") LocalDate today);
}
