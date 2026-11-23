package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upc.credito.infrastructure.persistence.entity.PaymentEntity;

import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Integer> {

    List<PaymentEntity> findBySettlementIdOrderByPaymentDatetimeAsc(Integer settlementId);

    List<PaymentEntity> findBySettlementIdInOrderByPaymentDatetimeAsc(Collection<Integer> settlementIds);

    @Query("""
            select p from PaymentEntity p
            where p.settlementId in (
                select s.settlementId from MonthlySettlementEntity s
                where s.customerId in (
                    select c.customerId from CustomerEntity c where c.storeId = :storeId
                )
            )
            order by p.paymentDatetime desc
            """)
    List<PaymentEntity> findByStore(@Param("storeId") Integer storeId);
}
