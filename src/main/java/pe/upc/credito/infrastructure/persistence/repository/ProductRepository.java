package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upc.credito.infrastructure.persistence.entity.ProductEntity;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Integer> {

    List<ProductEntity> findByStoreIdOrderByProductIdAsc(Integer storeId);

    Optional<ProductEntity> findByProductIdAndStoreId(Integer productId, Integer storeId);
}
