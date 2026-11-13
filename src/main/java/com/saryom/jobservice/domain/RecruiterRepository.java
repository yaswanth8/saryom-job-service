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
     * The directory's filters, shared by every sort order below.
     *
     * <p>Neither filter is ever passed as null: Postgres cannot infer the type
     * of a bare null parameter in {@code :x is null}, so "no filter" is a flag
     * for the category and a match-everything pattern ({@code %}) for the
     * lower-cased text. {@code :reach} is {@code any}, {@code email},
     * {@code phone} or {@code profile}: recruiters with that way to reach them.
     */
    String FILTER = """
            from Recruiter r
            where (:anyCategory = true or exists (
                       select 1 from RecruiterSighting s
                       where s.recruiterId = r.id and s.roleCategory = :category))
              and (lower(r.fullName) like :pattern
                   or lower(coalesce(r.company, '')) like :pattern
                   or lower(coalesce(r.title, '')) like :pattern
                   or exists (select 1 from RecruiterSighting s2
                              where s2.recruiterId = r.id and lower(s2.roleTitle) like :pattern))
              and (:reach = 'any'
                   or (:reach = 'email' and r.email is not null)
                   or (:reach = 'phone' and r.phone is not null)
                   or (:reach = 'profile' and r.profileUrl is not null))
            """;

    String LAST_SEEN = "(select max(ls.seenOn) from RecruiterSighting ls where ls.recruiterId = r.id)";

    String SEEN_BY = "(select count(distinct sb.contributedBy) from RecruiterSighting sb where sb.recruiterId = r.id)";

    /** Most recently seen hiring first: the default, since a recruiter seen last week is likely still hiring. */
    @Query(value = "select r " + FILTER + " order by " + LAST_SEEN + " desc, r.createdAt desc, r.id",
            countQuery = "select count(r) " + FILTER)
    Page<Recruiter> browseRecentlySeen(@Param("anyCategory") boolean anyCategory,
                                       @Param("category") RoleCategory category,
                                       @Param("pattern") String pattern, @Param("reach") String reach,
                                       Pageable pageable);

    /** Reported by the most job seekers first. */
    @Query(value = "select r " + FILTER + " order by " + SEEN_BY + " desc, " + LAST_SEEN + " desc, r.id",
            countQuery = "select count(r) " + FILTER)
    Page<Recruiter> browseMostReported(@Param("anyCategory") boolean anyCategory,
                                       @Param("category") RoleCategory category,
                                       @Param("pattern") String pattern, @Param("reach") String reach,
                                       Pageable pageable);

    @Query(value = "select r " + FILTER + " order by lower(r.fullName), r.id",
            countQuery = "select count(r) " + FILTER)
    Page<Recruiter> browseByName(@Param("anyCategory") boolean anyCategory,
                                 @Param("category") RoleCategory category,
                                 @Param("pattern") String pattern, @Param("reach") String reach,
                                 Pageable pageable);

    /** Newest entries in the directory first. */
    @Query(value = "select r " + FILTER + " order by r.createdAt desc, r.id",
            countQuery = "select count(r) " + FILTER)
    Page<Recruiter> browseNewest(@Param("anyCategory") boolean anyCategory,
                                 @Param("category") RoleCategory category,
                                 @Param("pattern") String pattern, @Param("reach") String reach,
                                 Pageable pageable);

    /** By firm, then name; recruiters with no firm recorded last. */
    @Query(value = "select r " + FILTER
            + " order by case when r.company is null then 1 else 0 end, lower(r.company), lower(r.fullName), r.id",
            countQuery = "select count(r) " + FILTER)
    Page<Recruiter> browseByCompany(@Param("anyCategory") boolean anyCategory,
                                    @Param("category") RoleCategory category,
                                    @Param("pattern") String pattern, @Param("reach") String reach,
                                    Pageable pageable);

    long countByEmailIsNotNull();

    long countByPhoneIsNotNull();

    long countByProfileUrlIsNotNull();

    /** Other recruiters at the same firm, for a profile's "also at" list. */
    @Query("""
            select r from Recruiter r
            where lower(r.company) = lower(:company) and r.id <> :id
            order by lower(r.fullName), r.id
            """)
    List<Recruiter> colleagues(@Param("company") String company, @Param("id") UUID id, Pageable limit);

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
