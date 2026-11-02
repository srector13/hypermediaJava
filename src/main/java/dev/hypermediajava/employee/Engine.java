package dev.hypermediajava.employee;

/**
 * The two data-access techniques this POC compares. The web layer, templates and htmx
 * interactions are identical for both — only the {@link EmployeeStore} implementation changes.
 */
public enum Engine {
    JDBC("JDBC", "JdbcClient + hand-written SQL",
            "Dynamic WHERE clause, whitelisted ORDER BY, LIMIT/OFFSET and COUNT(*) OVER() so the page and the total come back in a single round-trip.",
            "Keyset (seek) pagination: WHERE (last_name, id) > (?, ?) — constant cost no matter how deep you scroll."),
    JPA("JPA", "Spring Data JPA + Specifications",
            "JpaSpecificationExecutor.findAll(spec, Pageable) returns a Page<Employee>; Hibernate issues the page query plus a count query.",
            "Spring Data Window<T> + ScrollPosition.keyset() — the same seek strategy, generated for you.");

    private final String label;
    private final String technique;
    private final String pagingNote;
    private final String scrollNote;

    Engine(String label, String technique, String pagingNote, String scrollNote) {
        this.label = label;
        this.technique = technique;
        this.pagingNote = pagingNote;
        this.scrollNote = scrollNote;
    }

    public String label() { return label; }

    public String technique() { return technique; }

    public String pagingNote() { return pagingNote; }

    public String scrollNote() { return scrollNote; }

    /** URL path segment, e.g. {@code /jdbc/employees}. */
    public String path() { return name().toLowerCase(); }

    public Engine other() { return this == JDBC ? JPA : JDBC; }

    public static Engine fromPath(String value) {
        return Engine.valueOf(value.trim().toUpperCase());
    }
}
