package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
}
