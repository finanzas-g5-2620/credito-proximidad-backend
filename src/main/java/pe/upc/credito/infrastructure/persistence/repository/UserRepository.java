package pe.upc.credito.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserTypeAndStatus(String userType, String status);
}
