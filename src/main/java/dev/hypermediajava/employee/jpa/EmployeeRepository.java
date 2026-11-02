package dev.hypermediajava.employee.jpa;

import dev.hypermediajava.employee.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Spring Data JPA repository. Paging, sorting and keyset scrolling come from
 * {@link JpaSpecificationExecutor}; the bulk operations are explicit JPQL.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Employee e set e.salary = round(e.salary * :factor, 2), e.version = e.version + 1 where e.id in :ids")
    int raiseSalaries(@Param("ids") Collection<Long> ids, @Param("factor") BigDecimal factor);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Employee e where e.id in :ids")
    int deleteAllByIds(@Param("ids") Collection<Long> ids);
}
