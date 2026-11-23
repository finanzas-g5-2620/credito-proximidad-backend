package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upc.credito.infrastructure.persistence.entity.StoreEntity;

public interface StoreRepository extends JpaRepository<StoreEntity, Integer> {
}
