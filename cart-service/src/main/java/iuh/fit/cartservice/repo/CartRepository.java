package iuh.fit.cartservice.repo;

import iuh.fit.cartservice.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {
    Optional<CartEntity> findByCustomerId(UUID customerId);
}
