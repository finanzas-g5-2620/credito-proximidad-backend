package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upc.credito.infrastructure.persistence.entity.SaleDetailEntity;

import java.util.Collection;
import java.util.List;

public interface SaleDetailRepository extends JpaRepository<SaleDetailEntity, Integer> {

    List<SaleDetailEntity> findBySaleIdOrderBySaleDetailIdAsc(Integer saleId);

    List<SaleDetailEntity> findBySaleIdInOrderBySaleDetailIdAsc(Collection<Integer> saleIds);
}
