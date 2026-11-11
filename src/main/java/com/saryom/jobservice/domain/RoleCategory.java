package com.saryom.jobservice.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The handful of buckets the recruiter directory is browsed by.
 *
 * <p>Job titles are free text and nobody writes the same one twice ("Sr Java
 * Engineer", "Java/Spring Developer III"), so filtering on the title itself
 * would split one kind of recruiter across dozens of near-identical filters.
 * The title is kept as written for display and search; this is only the facet.
 */
public enum RoleCategory {
    // Order matters: the first match wins, so the more specific come first.
    // "Full stack Java developer" is full stack; "JavaScript developer" must
    // not be caught by the Java pattern, hence the word boundaries.
    FULL_STACK("Full stack", "full[\\s-]?stack|mern|mean\\b"),
    FRONTEND("Frontend", "front[\\s-]?end|\\breact|angular|\\bvue|\\bui\\b|javascript|typescript"),
    MOBILE("Mobile", "android|\\bios\\b|mobile|flutter|swift|kotlin"),
    // Before Data: a "QA Analyst" is a tester, not an analyst.
    QA("QA & Testing", "\\bqa\\b|sdet|\\btest|quality|automation engineer"),
    DATA("Data & AI", "\\bdata\\b|\\betl\\b|analyst|scientist|machine learning|\\bml\\b|\\bai\\b|databricks|snowflake"),
    DEVOPS_CLOUD("DevOps & Cloud", "devops|\\bsre\\b|site reliability|cloud|\\baws\\b|azure|kubernetes|platform engineer|infrastructure"),
    DOTNET(".NET", "\\.net|dotnet|c#|\\basp\\b"),
    PYTHON("Python", "python|django|flask"),
    JAVA_BACKEND("Java & Backend", "\\bjava\\b|spring|j2ee|back[\\s-]?end|microservice"),
    MANAGEMENT("Management", "manager|scrum|product owner|\\blead\\b|director|architect"),
    OTHER("Other", null);

    private final String label;
    private final Pattern pattern;

    RoleCategory(String label, String regex) {
        this.label = label;
        this.pattern = regex == null ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    public String label() {
        return label;
    }

    private static final List<RoleCategory> IN_ORDER = List.of(values());

    public static RoleCategory of(String roleTitle) {
        if (roleTitle == null || roleTitle.isBlank()) {
            return OTHER;
        }
        return IN_ORDER.stream()
                .filter(c -> c.pattern != null && c.pattern.matcher(roleTitle).find())
                .findFirst()
                .orElse(OTHER);
    }
}
