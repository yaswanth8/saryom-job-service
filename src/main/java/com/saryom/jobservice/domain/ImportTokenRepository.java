package com.saryom.jobservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportTokenRepository extends JpaRepository<ImportToken, UUID> {

    List<ImportToken> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<ImportToken> findByIdAndUserId(UUID id, String userId);

    long countByUserId(String userId);

    /**
     * Readable only while the matching hash is presented to the database; see
     * the V1 migration.
     *
     * <p>Transactional in its own right, unlike the other queries here. This is
     * called from the authentication filter, outside any service transaction,
     * and Spring Data opens none for a declared query method by default. With
     * no transaction there is nowhere for the identity to be written, so the
     * database would show this lookup nothing and every token would be refused.
     */
    @Transactional(readOnly = true)
    Optional<ImportToken> findByTokenHash(String tokenHash);
}
