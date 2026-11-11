package com.saryom.jobservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every query names the user even though row level security would filter to
 * them anyway. The policy is the guarantee; the predicate is so the code reads
 * as what it means, and so the H2 test suite, which has no policies, still
 * exercises per-user behaviour.
 */
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<Application> findByIdAndUserId(UUID id, String userId);

    List<Application> findByUserIdAndExternalIdIn(String userId, Collection<String> externalIds);

    @Query("select a.status as status, count(a) as total from Application a "
            + "where a.userId = :userId group by a.status")
    List<StatusCount> countByStatus(@Param("userId") String userId);

    interface StatusCount {
        ApplicationStatus getStatus();

        long getTotal();
    }
}
