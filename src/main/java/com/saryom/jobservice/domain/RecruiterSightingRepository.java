package com.saryom.jobservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruiterSightingRepository extends JpaRepository<RecruiterSighting, UUID> {

    List<RecruiterSighting> findByRecruiterIdIn(Collection<UUID> recruiterIds);

    Optional<RecruiterSighting> findByRecruiterIdAndContributedByAndRoleKey(
            UUID recruiterId, String contributedBy, String roleKey);

    /** How many recruiters hire for each kind of role: the directory's filter counts. */
    @Query("""
            select s.roleCategory as category, count(distinct s.recruiterId) as total
            from RecruiterSighting s
            group by s.roleCategory
            """)
    List<CategoryCount> countRecruitersByCategory();

    @Query("select count(distinct s.contributedBy) from RecruiterSighting s")
    long countContributors();

    interface CategoryCount {
        RoleCategory getCategory();

        long getTotal();
    }
}
