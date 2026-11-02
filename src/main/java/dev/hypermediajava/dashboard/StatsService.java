package dev.hypermediajava.dashboard;

import dev.hypermediajava.employee.Department;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Aggregates are where plain SQL shines: two GROUP BY queries, no entities loaded. */
@Service
public class StatsService {

    private final JdbcClient jdbc;

    public StatsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Stats current() {
        List<Stats.DepartmentStats> departments = jdbc.sql("""
                SELECT department, COUNT(*) AS headcount, AVG(salary) AS avg_salary
                  FROM employee
                 GROUP BY department
                 ORDER BY headcount DESC
                """)
                .query((rs, n) -> new Stats.DepartmentStats(
                        Department.valueOf(rs.getString("department")),
                        rs.getLong("headcount"),
                        scale(rs.getBigDecimal("avg_salary"))))
                .list();

        return jdbc.sql("""
                SELECT COUNT(*) AS headcount,
                       COUNT(CASE WHEN status = 'ACTIVE' THEN 1 END) AS active,
                       COUNT(CASE WHEN status = 'REMOTE' THEN 1 END) AS remote,
                       COUNT(CASE WHEN status = 'ON_LEAVE' THEN 1 END) AS on_leave,
                       AVG(salary) AS avg_salary,
                       SUM(salary) AS payroll
                  FROM employee
                """)
                .query((rs, n) -> new Stats(
                        rs.getLong("headcount"),
                        rs.getLong("active"),
                        rs.getLong("remote"),
                        rs.getLong("on_leave"),
                        scale(rs.getBigDecimal("avg_salary")),
                        scale(rs.getBigDecimal("payroll")),
                        departments))
                .single();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, java.math.RoundingMode.HALF_UP);
    }
}
