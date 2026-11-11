package com.saryom.jobservice.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruiterRepository extends JpaRepository<Recruiter, UUID> {

    Optional<Recruiter> findByDedupeKey(String dedupeKey);

    /**
     * The directory, newest entries first.
     *
     * <p>Neither filter is ever passed as null: Postgres cannot infer the type
     * of a bare null parameter in {@code :x is null}, so "no filter" is a flag
     * for the category and a match-everything pattern for the text.
     *
     * @param pattern lower-cased LIKE pattern; {@code %} for no text filter
     */
    @Query("""
            select r from Recruiter r
            where (:anyCategory = true or exists (
                       select 1 from RecruiterSighting s
                       where s.recruiterId = r.id and s.roleCategory = :category))
              and (lower(r.fullName) like :pattern
                   or lower(coalesce(r.company, '')) like :pattern
                   or exists (select 1 from RecruiterSighting s2
                              where s2.recruiterId = r.id and lower(s2.roleTitle) like :pattern))
            order by r.createdAt desc, r.id
            """)
    Page<Recruiter> browse(@Param("anyCategory") boolean anyCategory,
                           @Param("category") RoleCategory category,
                           @Param("pattern") String pattern,
                           Pageable pageable);

    @Query("""
            select r.company as company, count(r) as total from Recruiter r
            where r.company is not null and r.company <> ''
            group by r.company
            order by count(r) desc, r.company
            """)
    List<CompanyCount> topCompanies(Pageable limit);

    interface CompanyCount {
        String getCompany();

        long getTotal();
    }
}
