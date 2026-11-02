package dev.hypermediajava.employee.jpa;

import dev.hypermediajava.employee.Department;
import dev.hypermediajava.employee.Employee;
import dev.hypermediajava.employee.EmployeeQuery;
import dev.hypermediajava.employee.EmployeeStatus;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

/** Composable, type-checked filters — the JPA counterpart of the JDBC store's dynamic WHERE builder. */
final class EmployeeSpecs {

    private static final char ESCAPE = '\\';

    private EmployeeSpecs() {
    }

    static Specification<Employee> matching(String q, Department department, EmployeeStatus status) {
        return Specification.allOf(search(q), inDepartment(department), hasStatus(status));
    }

    static Specification<Employee> search(String q) {
        if (q == null || q.isBlank()) return Specification.unrestricted();
        String pattern = EmployeeQuery.likePattern(q.strip());
        return (root, cq, cb) -> {
            Expression<String> fullName = cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName")));
            return cb.or(
                    cb.like(fullName, pattern, ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("title")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("location")), pattern, ESCAPE));
        };
    }

    static Specification<Employee> inDepartment(Department department) {
        if (department == null) return Specification.unrestricted();
        return (root, cq, cb) -> cb.equal(root.get("department"), department);
    }

    static Specification<Employee> hasStatus(EmployeeStatus status) {
        if (status == null) return Specification.unrestricted();
        return (root, cq, cb) -> cb.equal(root.get("status"), status);
    }
}
