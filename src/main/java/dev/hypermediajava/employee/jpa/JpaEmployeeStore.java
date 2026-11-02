package dev.hypermediajava.employee.jpa;

import dev.hypermediajava.employee.Employee;
import dev.hypermediajava.employee.EmployeeForm;
import dev.hypermediajava.employee.EmployeeQuery;
import dev.hypermediajava.employee.EmployeeRow;
import dev.hypermediajava.employee.EmployeeStore;
import dev.hypermediajava.employee.Engine;
import dev.hypermediajava.employee.PageView;
import dev.hypermediajava.employee.ScrollView;
import dev.hypermediajava.employee.SortColumn;
import dev.hypermediajava.employee.StaleEditException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Data JPA. You describe <em>what</em> you want (Specification + Pageable / ScrollPosition)
 * and Hibernate writes the SQL — which the query inspector still shows you.
 */
@Repository
public class JpaEmployeeStore implements EmployeeStore {

    private final EmployeeRepository repository;

    public JpaEmployeeStore(EmployeeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Engine engine() {
        return Engine.JPA;
    }

    // ---------------------------------------------------------------- offset pagination

    @Override
    @Transactional(readOnly = true)
    public PageView search(EmployeeQuery query) {
        Specification<Employee> spec = EmployeeSpecs.matching(query.q(), query.department(), query.status());
        Page<Employee> page = repository.findAll(spec, pageRequest(query));

        if (page.getContent().isEmpty() && query.page() > 0 && page.getTotalElements() > 0) {
            query = query.withPage(page.getTotalPages() - 1);
            page = repository.findAll(spec, pageRequest(query));
        }
        return new PageView(Engine.JPA, query, page.map(Employee::toRow).getContent(), page.getTotalElements());
    }

    private static PageRequest pageRequest(EmployeeQuery query) {
        return PageRequest.of(query.page(), query.size(), sort(query.sortColumn(), query.descending()));
    }

    static Sort sort(SortColumn column, boolean descending) {
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        List<String> properties = new ArrayList<>(column.properties());
        properties.add("id"); // tiebreaker => stable, deterministic pages
        return Sort.by(direction, properties.toArray(String[]::new));
    }

    // ---------------------------------------------------------------- keyset pagination

    @Override
    @Transactional(readOnly = true)
    public ScrollView scroll(String q, ScrollView.Cursor after, int limit) {
        ScrollPosition position = after == null
                ? ScrollPosition.keyset()
                : ScrollPosition.forward(Map.of("lastName", after.lastName(), "id", after.id()));

        Window<Employee> window = repository.findBy(EmployeeSpecs.search(q), fluent -> fluent
                .sortBy(Sort.by("lastName", "id"))
                .limit(limit)
                .scroll(position));

        return ScrollView.of(Engine.JPA, q, window.map(Employee::toRow).getContent(), window.hasNext());
    }

    // ---------------------------------------------------------------- CRUD

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeRow> findById(long id) {
        return repository.findById(id).map(Employee::toRow);
    }

    @Override
    @Transactional
    public EmployeeRow create(EmployeeForm form) {
        return repository.saveAndFlush(new Employee(form)).toRow();
    }

    @Override
    @Transactional
    public EmployeeRow update(long id, EmployeeForm form) {
        Employee employee = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (form.getVersion() == null || employee.getVersion() != form.getVersion()) {
            throw new StaleEditException(employee.toRow());
        }
        employee.apply(form);
        // Dirty checking generates the UPDATE; @Version adds "and version=?" and bumps it.
        return repository.saveAndFlush(employee).toRow();
    }

    @Override
    @Transactional
    public boolean delete(long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    @Override
    @Transactional
    public int bulkRaise(Collection<Long> ids, BigDecimal percent) {
        if (ids.isEmpty()) return 0;
        return repository.raiseSalaries(ids, BigDecimal.ONE.add(percent.movePointLeft(2)));
    }

    @Override
    @Transactional
    public int bulkDelete(Collection<Long> ids) {
        if (ids.isEmpty()) return 0;
        return repository.deleteAllByIds(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailTaken(String email, Long excludingId) {
        return repository.existsByEmailIgnoreCaseAndIdNot(email.strip(), excludingId == null ? -1L : excludingId);
    }
}
