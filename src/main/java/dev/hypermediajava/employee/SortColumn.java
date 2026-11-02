package dev.hypermediajava.employee;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Whitelist of sortable columns. User input is only ever used to <em>look up</em> one of these
 * constants — it is never concatenated into SQL/JPQL — so {@code ?sort=salary;drop table} is harmless.
 */
public enum SortColumn {
    NAME("name", List.of("last_name", "first_name"), List.of("lastName", "firstName")),
    EMAIL("email", List.of("email"), List.of("email")),
    DEPARTMENT("department", List.of("department"), List.of("department")),
    TITLE("title", List.of("title"), List.of("title")),
    LOCATION("location", List.of("location"), List.of("location")),
    SALARY("salary", List.of("salary"), List.of("salary")),
    HIRED("hired", List.of("hire_date"), List.of("hireDate")),
    STATUS("status", List.of("status"), List.of("status"));

    private final String param;
    private final List<String> columns;
    private final List<String> properties;

    SortColumn(String param, List<String> columns, List<String> properties) {
        this.param = param;
        this.columns = columns;
        this.properties = properties;
    }

    public String param() { return param; }

    /** SQL column names (JDBC). */
    public List<String> columns() { return columns; }

    /** Entity property names (JPA). */
    public List<String> properties() { return properties; }

    public static Optional<SortColumn> fromParam(String param) {
        return Arrays.stream(values()).filter(c -> c.param.equalsIgnoreCase(param)).findFirst();
    }
}
