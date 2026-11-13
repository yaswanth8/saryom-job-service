package com.saryom.jobservice.service;

import com.saryom.jobservice.domain.Application.Contact;
import com.saryom.jobservice.domain.ApplicationRepository;
import com.saryom.jobservice.domain.Recruiter;
import com.saryom.jobservice.domain.RecruiterRepository;
import com.saryom.jobservice.domain.RecruiterSighting;
import com.saryom.jobservice.domain.RecruiterSightingRepository;
import com.saryom.jobservice.domain.RecruiterReach;
import com.saryom.jobservice.domain.RecruiterSort;
import com.saryom.jobservice.domain.RoleCategory;
import com.saryom.jobservice.error.NotFoundException;
import com.saryom.jobservice.web.dto.DirectorySummaryResponse;
import com.saryom.jobservice.web.dto.RecruiterProfileResponse;
import com.saryom.jobservice.web.dto.RecruiterRequest;
import com.saryom.jobservice.web.dto.RecruiterResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The shared recruiter directory: one entry per recruiter, built from what
 * every user's job search turns up, and browsable by the kind of role they
 * hire for.
 */
@Service
public class RecruiterService {

    private static final int TOP_COMPANIES = 8;
    private static final int COLLEAGUES = 6;

    private final RecruiterRepository recruiters;
    private final RecruiterSightingRepository sightings;
    private final ApplicationRepository applications;
    private final Clock clock;

    public RecruiterService(RecruiterRepository recruiters, RecruiterSightingRepository sightings,
                            ApplicationRepository applications, Clock clock) {
        this.recruiters = recruiters;
        this.sightings = sightings;
        this.applications = applications;
        this.clock = clock;
    }

    /**
     * @param viewerUid null for anonymous visitors, who get the entries without
     *                  the means to contact anyone
     */
    @Transactional(readOnly = true)
    public Page<RecruiterResponse> browse(RoleCategory category, String q, RecruiterSort sort,
                                          RecruiterReach reach, int page, int size, String viewerUid) {
        String text = Text.clean(q);
        String pattern = text == null ? "%" : "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        boolean any = category == null;
        RoleCategory facet = any ? RoleCategory.OTHER : category;
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50));
        String by = (reach == null ? RecruiterReach.ANY : reach).param();
        Page<Recruiter> found = switch (sort == null ? RecruiterSort.RECENT : sort) {
            case RECENT -> recruiters.browseRecentlySeen(any, facet, pattern, by, request);
            case REPORTED -> recruiters.browseMostReported(any, facet, pattern, by, request);
            case NAME -> recruiters.browseByName(any, facet, pattern, by, request);
            case COMPANY -> recruiters.browseByCompany(any, facet, pattern, by, request);
            case NEWEST -> recruiters.browseNewest(any, facet, pattern, by, request);
        };

        // One read for the whole page's roles rather than one per recruiter.
        Map<UUID, List<RecruiterSighting>> byRecruiter = sightings
                .findByRecruiterIdIn(found.getContent().stream().map(Recruiter::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(RecruiterSighting::getRecruiterId));

        return found.map(r -> toResponse(r, byRecruiter.getOrDefault(r.getId(), List.of()), viewerUid));
    }

    /**
     * One recruiter in full, for their profile page.
     *
     * @param viewerUid null for anonymous visitors, who get no contact details
     *                  and no applications
     */
    @Transactional(readOnly = true)
    public RecruiterProfileResponse profile(UUID id, String viewerUid) {
        Recruiter r = recruiters.findById(id)
                .orElseThrow(() -> new NotFoundException("No recruiter " + id));
        List<RecruiterSighting> seen = sightings.findByRecruiterIdIn(List.of(id));
        boolean signedIn = viewerUid != null;

        // One row per role, however many people reported it.
        Map<String, List<RecruiterSighting>> byRole = seen.stream()
                .collect(Collectors.groupingBy(RecruiterSighting::getRoleKey, LinkedHashMap::new, Collectors.toList()));
        List<RecruiterProfileResponse.Role> roles = byRole.values().stream()
                .map(RecruiterService::mergeRole)
                .sorted(Comparator.comparing(RecruiterProfileResponse.Role::lastSeenOn,
                                Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
                        .thenComparing(RecruiterProfileResponse.Role::title, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<RecruiterProfileResponse.Colleague> colleagues = r.getCompany() == null ? List.of()
                : recruiters.colleagues(r.getCompany(), id, PageRequest.of(0, COLLEAGUES)).stream()
                        .map(c -> new RecruiterProfileResponse.Colleague(c.getId(), c.getFullName(), c.getTitle()))
                        .toList();

        List<RecruiterProfileResponse.LinkedApplication> mineToo = !signedIn ? List.of()
                : applications.findWithContact(viewerUid, lower(r.getEmail()),
                                r.getProfileUrl() == null ? "" : r.getProfileUrl(), lower(r.getFullName()),
                                lower(r.getCompany())).stream()
                        .map(a -> new RecruiterProfileResponse.LinkedApplication(a.getId(), a.getCompany(),
                                a.getRole(), a.getStatus(), a.getAppliedOn()))
                        .toList();

        return new RecruiterProfileResponse(r.getId(), r.getFullName(), r.getTitle(), r.getCompany(),
                signedIn ? r.getEmail() : null,
                signedIn ? r.getPhone() : null,
                signedIn ? r.getProfileUrl() : null,
                !signedIn, signedIn && r.wasContributedBy(viewerUid),
                (int) seen.stream().map(RecruiterSighting::getContributedBy).distinct().count(),
                seen.stream().map(RecruiterSighting::getSeenOn).max(Comparator.naturalOrder()).orElse(null),
                LocalDate.ofInstant(r.getCreatedAt(), clock.getZone()),
                roles, colleagues, mineToo);
    }

    private static RecruiterProfileResponse.Role mergeRole(List<RecruiterSighting> reports) {
        RecruiterSighting first = reports.getFirst();
        return new RecruiterProfileResponse.Role(first.getRoleTitle(), first.getRoleCategory(),
                first.getRoleCategory().label(),
                distinct(reports.stream().map(RecruiterSighting::getCompany).toList()),
                distinct(reports.stream().map(RecruiterSighting::getLocation).toList()),
                reports.stream().map(RecruiterSighting::getSeenOn).max(Comparator.naturalOrder()).orElse(null),
                (int) reports.stream().map(RecruiterSighting::getContributedBy).distinct().count());
    }

    /** Non-blank values, first spelling kept, compared without case. */
    private static List<String> distinct(List<String> values) {
        Map<String, String> kept = new LinkedHashMap<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                kept.putIfAbsent(v.trim().toLowerCase(Locale.ROOT), v.trim());
            }
        }
        return List.copyOf(kept.values());
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public DirectorySummaryResponse summary() {
        var categories = sightings.countRecruitersByCategory().stream()
                .map(c -> new DirectorySummaryResponse.CategoryCount(
                        c.getCategory(), c.getCategory().label(), c.getTotal()))
                .sorted(Comparator.comparingLong(DirectorySummaryResponse.CategoryCount::recruiters).reversed()
                        .thenComparing(c -> c.category().ordinal()))
                .toList();
        var companies = recruiters.topCompanies(PageRequest.of(0, TOP_COMPANIES)).stream()
                .map(c -> new DirectorySummaryResponse.CompanyCount(c.getCompany(), c.getTotal()))
                .toList();
        return new DirectorySummaryResponse(recruiters.count(), sightings.countContributors(),
                categories, companies, recruiters.countByEmailIsNotNull(), recruiters.countByPhoneIsNotNull(),
                recruiters.countByProfileUrlIsNotNull());
    }

    @Transactional
    public RecruiterResponse contribute(String uid, RecruiterRequest request) {
        Contact contact = new Contact(request.fullName(), request.title(), request.email(),
                request.phone(), request.profileUrl());
        if (!isReachable(contact)) {
            throw new IllegalArgumentException(
                    "Add an email, phone number or profile link so others can reach this recruiter");
        }
        Recruiter recruiter = share(uid, contact, request.roleTitle(), request.company(),
                request.location(), LocalDate.now(clock));
        return toResponse(recruiter, sightings.findByRecruiterIdIn(List.of(recruiter.getId())), uid);
    }

    /**
     * Records that {@code uid} came across this recruiter hiring for
     * {@code roleTitle}, adding the recruiter to the directory if they are new
     * to it. Returns null, changing nothing, when there is no way to reach the
     * person: a bare name is not a directory entry.
     *
     * <p>Someone else's entry is never rewritten. The first contributor's
     * details stand; a later contributor adds their sighting to it. Besides
     * being the safer rule for shared data, it is the only one the database
     * allows: its policies let a user update only rows they contributed.
     */
    @Transactional
    public Recruiter share(String uid, Contact contact, String roleTitle, String company,
                           String location, LocalDate seenOn) {
        String name = Text.clean(contact.name(), 200);
        String role = Text.clean(roleTitle, 200);
        if (name == null || role == null || !isReachable(contact)) {
            return null;
        }
        String email = Text.clean(contact.email(), 320);
        String phone = Text.clean(contact.phone(), 60);
        String url = Text.clean(contact.url(), 2000);
        String title = Text.clean(contact.title(), 200);
        String firm = Text.clean(company, 200);
        var now = clock.instant();

        Recruiter recruiter = recruiters.findByDedupeKey(Recruiter.dedupeKey(name, firm, email, url))
                .orElse(null);
        if (recruiter == null) {
            recruiter = recruiters.save(new Recruiter(UUID.randomUUID(), name, title, firm, email,
                    phone, url, uid, now));
        } else if (recruiter.wasContributedBy(uid) && recruiter.fillGaps(title, firm, phone, url, now)) {
            recruiters.save(recruiter);
        }

        UUID recruiterId = recruiter.getId();
        var existing = sightings.findByRecruiterIdAndContributedByAndRoleKey(
                recruiterId, uid, RecruiterSighting.roleKey(role));
        if (existing.isPresent()) {
            existing.get().seenAgain(seenOn);
            sightings.save(existing.get());
        } else {
            sightings.save(new RecruiterSighting(UUID.randomUUID(), recruiterId, role, firm,
                    Text.clean(location, 200), uid, seenOn, now));
        }
        return recruiter;
    }

    /** Contributors remove their own entries; moderators may remove any. */
    @Transactional
    public void remove(UUID id, String uid, boolean admin) {
        Recruiter recruiter = recruiters.findById(id)
                .orElseThrow(() -> new NotFoundException("No recruiter " + id));
        if (!admin && !recruiter.wasContributedBy(uid)) {
            throw new AccessDeniedException("Only the person who added this recruiter can remove them");
        }
        recruiters.delete(recruiter);
    }

    static boolean isReachable(Contact contact) {
        return Text.clean(contact.email()) != null || Text.clean(contact.phone()) != null
                || Text.clean(contact.url()) != null;
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static RecruiterResponse toResponse(Recruiter r, List<RecruiterSighting> seen, String viewerUid) {
        boolean signedIn = viewerUid != null;
        // The same title reported by three people is one role on the card.
        var roles = seen.stream()
                .collect(Collectors.toMap(RecruiterSighting::getRoleKey, s -> s, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(RecruiterSighting::getRoleTitle, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new RecruiterResponse.Role(s.getRoleTitle(), s.getRoleCategory(),
                        s.getRoleCategory().label()))
                .toList();
        LocalDate lastSeen = seen.stream().map(RecruiterSighting::getSeenOn)
                .max(Comparator.naturalOrder()).orElse(null);
        int seenBy = (int) seen.stream().map(RecruiterSighting::getContributedBy).distinct().count();
        return new RecruiterResponse(r.getId(), r.getFullName(), r.getTitle(), r.getCompany(),
                signedIn ? r.getEmail() : null,
                signedIn ? r.getPhone() : null,
                signedIn ? r.getProfileUrl() : null,
                !signedIn, roles, lastSeen, seenBy,
                signedIn && r.wasContributedBy(viewerUid));
    }
}
