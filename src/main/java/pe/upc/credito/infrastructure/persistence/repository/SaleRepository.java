package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upc.credito.infrastructure.persistence.entity.SaleEntity;

import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<SaleEntity, Integer> {

    List<SaleEntity> findByStoreIdOrderBySaleDatetimeDesc(Integer storeId);

    List<SaleEntity> findByStoreId(Integer storeId);

    Optional<SaleEntity> findBySaleIdAndStoreId(Integer saleId, Integer storeId);
}
