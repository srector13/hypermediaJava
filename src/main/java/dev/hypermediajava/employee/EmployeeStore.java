package dev.hypermediajava.employee;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

/**
 * Data-access contract implemented twice: once with plain JDBC, once with JPA.
 * Controllers pick an implementation from the {@code /{engine}/...} path segment.
 */
public interface EmployeeStore {

    Engine engine();

    /** Offset pagination with filtering and sorting — classic "page 3 of 2,000". */
    PageView search(EmployeeQuery query);

    /** Keyset / seek pagination for infinite scroll. {@code after} is null for the first batch. */
    ScrollView scroll(String q, ScrollView.Cursor after, int limit);

    Optional<EmployeeRow> findById(long id);

    EmployeeRow create(EmployeeForm form);

    /** @throws StaleEditException when {@code form.getVersion()} no longer matches the database */
    EmployeeRow update(long id, EmployeeForm form);

    boolean delete(long id);

    int bulkRaise(Collection<Long> ids, BigDecimal percent);

    int bulkDelete(Collection<Long> ids);

    boolean emailTaken(String email, Long excludingId);
}
