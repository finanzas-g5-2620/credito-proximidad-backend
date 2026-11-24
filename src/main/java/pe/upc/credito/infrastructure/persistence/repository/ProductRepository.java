package pe.upc.credito.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upc.credito.infrastructure.persistence.entity.ProductEntity;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Integer> {

    List<ProductEntity> findByStoreIdOrderByProductIdAsc(Integer storeId);

    Optional<ProductEntity> findByProductIdAndStoreId(Integer productId, Integer storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select product from ProductEntity product
            where product.productId = :productId and product.storeId = :storeId
            """)
    Optional<ProductEntity> findByProductIdAndStoreIdForUpdate(
            @Param("productId") Integer productId, @Param("storeId") Integer storeId);
}
