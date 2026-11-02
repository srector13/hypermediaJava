package dev.hypermediajava.employee.jdbc;

import dev.hypermediajava.employee.Department;
import dev.hypermediajava.employee.EmployeeForm;
import dev.hypermediajava.employee.EmployeeQuery;
import dev.hypermediajava.employee.EmployeeRow;
import dev.hypermediajava.employee.EmployeeStatus;
import dev.hypermediajava.employee.EmployeeStore;
import dev.hypermediajava.employee.Engine;
import dev.hypermediajava.employee.PageView;
import dev.hypermediajava.employee.ScrollView;
import dev.hypermediajava.employee.SortColumn;
import dev.hypermediajava.employee.StaleEditException;
import dev.hypermediajava.sql.SqlCapture;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Plain JDBC via Spring's fluent {@link JdbcClient}. You write the SQL, you see the SQL, you own the SQL.
 */
@Repository
public class JdbcEmployeeStore implements EmployeeStore {

    private static final String COLUMNS =
            "id, first_name, last_name, email, department, title, location, salary, hire_date, status, version";

    private static final RowMapper<EmployeeRow> ROW_MAPPER = (rs, rowNum) -> new EmployeeRow(
            rs.getLong("id"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            Department.valueOf(rs.getString("department")),
            rs.getString("title"),
            rs.getString("location"),
            rs.getBigDecimal("salary"),
            rs.getObject("hire_date", java.time.LocalDate.class),
            EmployeeStatus.valueOf(rs.getString("status")),
            rs.getLong("version"));

    private final JdbcClient jdbc;

    public JdbcEmployeeStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every statement goes through here so the query inspector can show it. */
    private JdbcClient.StatementSpec sql(String sql) {
        SqlCapture.record(sql);
        return jdbc.sql(sql);
    }

    @Override
    public Engine engine() {
        return Engine.JDBC;
    }

    // ---------------------------------------------------------------- offset pagination

    @Override
    @Transactional(readOnly = true)
    public PageView search(EmployeeQuery query) {
        Where where = Where.of(query.q(), query.department(), query.status());
        long[] total = {0};
        List<EmployeeRow> rows = fetchPage(query, where, total);

        if (rows.isEmpty() && query.page() > 0) {
            // Asked for a page past the end (e.g. after filtering): clamp to the last page.
            long count = count(where);
            if (count > 0) {
                query = query.withPage((int) ((count - 1) / query.size()));
                rows = fetchPage(query, where, total);
            }
            total[0] = count;
        }
        return new PageView(Engine.JDBC, query, rows, total[0]);
    }

    private List<EmployeeRow> fetchPage(EmployeeQuery query, Where where, long[] total) {
        // COUNT(*) OVER() attaches the full match count to every row: one round-trip instead of two.
        String sql = "SELECT " + COLUMNS + ", COUNT(*) OVER() AS total_count FROM employee"
                + where.sql()
                + " ORDER BY " + orderBy(query.sortColumn(), query.descending())
                + " LIMIT :limit OFFSET :offset";
        return sql(sql)
                .params(where.params())
                .param("limit", query.size())
                .param("offset", query.offset())
                .query((rs, n) -> {
                    total[0] = rs.getLong("total_count");
                    return ROW_MAPPER.mapRow(rs, n);
                })
                .list();
    }

    private long count(Where where) {
        String sql = "SELECT COUNT(*) FROM employee" + where.sql();
        return sql(sql).params(where.params()).query(Long.class).single();
    }

    /** ORDER BY built only from whitelisted column names, with id as a tiebreaker for stable paging. */
    static String orderBy(SortColumn column, boolean descending) {
        String direction = descending ? " DESC" : " ASC";
        List<String> parts = new ArrayList<>();
        column.columns().forEach(c -> parts.add(c + direction));
        parts.add("id" + direction);
        return String.join(", ", parts);
    }

    // ---------------------------------------------------------------- keyset pagination

    @Override
    @Transactional(readOnly = true)
    public ScrollView scroll(String q, ScrollView.Cursor after, int limit) {
        Where where = Where.of(q, null, null);
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM employee").append(where.sql());
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        if (after != null) {
            sql.append(where.sql().isEmpty() ? " WHERE " : " AND ")
               .append("(last_name > :afterName OR (last_name = :afterName AND id > :afterId))");
            params.put("afterName", after.lastName());
            params.put("afterId", after.id());
        }
        sql.append(" ORDER BY last_name ASC, id ASC LIMIT :limit");
        // Fetch one extra row to learn whether another batch exists, without a COUNT.
        List<EmployeeRow> rows = sql(sql.toString()).params(params).param("limit", limit + 1)
                .query(ROW_MAPPER).list();
        boolean more = rows.size() > limit;
        return ScrollView.of(Engine.JDBC, q, more ? rows.subList(0, limit) : rows, more);
    }

    // ---------------------------------------------------------------- CRUD

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeRow> findById(long id) {
        return sql("SELECT " + COLUMNS + " FROM employee WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    @Transactional
    public EmployeeRow create(EmployeeForm f) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        sql("""
                INSERT INTO employee (first_name, last_name, email, department, title, location, salary, hire_date, status, version)
                VALUES (:firstName, :lastName, :email, :department, :title, :location, :salary, :hireDate, :status, 0)
                """)
                .params(formParams(f))
                .update(keys, "id");
        long id = keys.getKeyAs(Long.class);
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public EmployeeRow update(long id, EmployeeForm f) {
        Map<String, Object> params = formParams(f);
        params.put("id", id);
        params.put("version", f.getVersion());
        int updated = sql("""
                UPDATE employee
                   SET first_name = :firstName, last_name = :lastName, email = :email, department = :department,
                       title = :title, location = :location, salary = :salary, hire_date = :hireDate,
                       status = :status, version = version + 1
                 WHERE id = :id AND version = :version
                """)
                .params(params)
                .update();
        if (updated == 0) {
            // Either it is gone, or someone else bumped the version first.
            EmployeeRow current = findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            throw new StaleEditException(current);
        }
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public boolean delete(long id) {
        return sql("DELETE FROM employee WHERE id = :id").param("id", id).update() > 0;
    }

    @Override
    @Transactional
    public int bulkRaise(Collection<Long> ids, BigDecimal percent) {
        if (ids.isEmpty()) return 0;
        BigDecimal factor = BigDecimal.ONE.add(percent.movePointLeft(2));
        // A collection parameter expands to IN (?, ?, ?) automatically.
        return sql("UPDATE employee SET salary = ROUND(salary * :factor, 2), version = version + 1 WHERE id IN (:ids)")
                .param("factor", factor)
                .param("ids", ids)
                .update();
    }

    @Override
    @Transactional
    public int bulkDelete(Collection<Long> ids) {
        if (ids.isEmpty()) return 0;
        return sql("DELETE FROM employee WHERE id IN (:ids)").param("ids", ids).update();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailTaken(String email, Long excludingId) {
        return sql("SELECT COUNT(*) FROM employee WHERE LOWER(email) = :email AND id <> :id")
                .param("email", email.strip().toLowerCase())
                .param("id", excludingId == null ? -1L : excludingId)
                .query(Long.class)
                .single() > 0;
    }

    private static Map<String, Object> formParams(EmployeeForm f) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("firstName", f.getFirstName().strip());
        p.put("lastName", f.getLastName().strip());
        p.put("email", f.getEmail().strip().toLowerCase());
        p.put("department", f.getDepartment().name());
        p.put("title", f.getTitle().strip());
        p.put("location", f.getLocation().strip());
        p.put("salary", f.getSalary().setScale(2, RoundingMode.HALF_UP));
        p.put("hireDate", f.getHireDate());
        p.put("status", f.getStatus().name());
        return p;
    }

    /** A tiny dynamic WHERE builder: only the filters the user actually set become SQL. */
    record Where(String sql, Map<String, Object> params) {

        static Where of(String q, Department department, EmployeeStatus status) {
            List<String> clauses = new ArrayList<>();
            Map<String, Object> params = new LinkedHashMap<>();
            if (q != null && !q.isBlank()) {
                clauses.add("""
                        (LOWER(first_name || ' ' || last_name) LIKE :q ESCAPE '\\' \
                        OR LOWER(email) LIKE :q ESCAPE '\\' \
                        OR LOWER(title) LIKE :q ESCAPE '\\' \
                        OR LOWER(location) LIKE :q ESCAPE '\\')""");
                params.put("q", EmployeeQuery.likePattern(q.strip()));
            }
            if (department != null) {
                clauses.add("department = :department");
                params.put("department", department.name());
            }
            if (status != null) {
                clauses.add("status = :status");
                params.put("status", status.name());
            }
            String sql = clauses.isEmpty() ? "" : clauses.stream().collect(Collectors.joining(" AND ", " WHERE ", ""));
            return new Where(sql, params);
        }
    }
}
