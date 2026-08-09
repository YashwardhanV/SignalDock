package dev.signaldock.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndActiveTrueAndRevokedAtIsNull(String keyHash);

    boolean existsByKeyHash(String keyHash);
}

