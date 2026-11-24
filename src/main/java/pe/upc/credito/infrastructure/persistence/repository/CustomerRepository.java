package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upc.credito.infrastructure.persistence.entity.CustomerEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Integer> {

    List<CustomerEntity> findByStoreIdOrderByCustomerIdAsc(Integer storeId);

    Optional<CustomerEntity> findByCustomerIdAndStoreId(Integer customerId, Integer storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from CustomerEntity c
            where c.customerId = :customerId and c.storeId = :storeId
            """)
    Optional<CustomerEntity> findByCustomerIdAndStoreIdForUpdate(
            @Param("customerId") Integer customerId,
            @Param("storeId") Integer storeId);

    Optional<CustomerEntity> findByUserId(Integer userId);

    long countByStoreId(Integer storeId);

    /** Counts unpaid overdue statements to determine month-end credit eligibility. */
    @Query("""
            select count(s) from MonthlySettlementEntity s
            where s.customerId = :customerId
              and s.pendingBalance > 0
              and s.paymentDueDate < :today
            """)
    long countOverdueSettlements(@Param("customerId") Integer customerId,
                                 @Param("today") LocalDate today);
}
