package dev.hypermediajava.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA entity. The JDBC store never touches this class — it reads straight into {@link EmployeeRow}.
 * The table itself is defined in {@code schema.sql} so that both techniques share one schema
 * that neither "owns".
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Department department;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeStatus status;

    /** Optimistic locking: concurrent edits from two browser tabs are detected, not silently lost. */
    @Version
    private long version;

    protected Employee() {
    }

    public Employee(EmployeeForm form) {
        apply(form);
    }

    public void apply(EmployeeForm form) {
        this.firstName = form.getFirstName().trim();
        this.lastName = form.getLastName().trim();
        this.email = form.getEmail().trim().toLowerCase();
        this.department = form.getDepartment();
        this.title = form.getTitle().trim();
        this.location = form.getLocation().trim();
        this.salary = form.getSalary();
        this.hireDate = form.getHireDate();
        this.status = form.getStatus();
    }

    public EmployeeRow toRow() {
        return new EmployeeRow(id, firstName, lastName, email, department, title, location, salary, hireDate, status, version);
    }

    public Long getId() { return id; }

    public String getFirstName() { return firstName; }

    public String getLastName() { return lastName; }

    public String getEmail() { return email; }

    public Department getDepartment() { return department; }

    public String getTitle() { return title; }

    public String getLocation() { return location; }

    public BigDecimal getSalary() { return salary; }

    public void setSalary(BigDecimal salary) { this.salary = salary; }

    public LocalDate getHireDate() { return hireDate; }

    public EmployeeStatus getStatus() { return status; }

    public void setStatus(EmployeeStatus status) { this.status = status; }

    public void setTitle(String title) { this.title = title; }

    public long getVersion() { return version; }
}
