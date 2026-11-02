package dev.hypermediajava.employee;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Form backing bean for create + inline edit. Bean Validation errors are rendered server-side, next to the field. */
public class EmployeeForm {

    @NotBlank @Size(max = 60)
    private String firstName;

    @NotBlank @Size(max = 60)
    private String lastName;

    @NotBlank @Email @Size(max = 160)
    private String email;

    @NotNull
    private Department department;

    @NotBlank @Size(max = 80)
    private String title;

    @NotBlank @Size(max = 60)
    private String location;

    @NotNull @DecimalMin("15000") @DecimalMax("1000000")
    private BigDecimal salary;

    @NotNull @PastOrPresent
    private LocalDate hireDate;

    @NotNull
    private EmployeeStatus status;

    /** Version the user started editing from; used for optimistic-lock checks. */
    private Long version;

    public static EmployeeForm blank() {
        EmployeeForm f = new EmployeeForm();
        f.department = Department.ENGINEERING;
        f.status = EmployeeStatus.ACTIVE;
        f.hireDate = LocalDate.now();
        f.salary = new BigDecimal("85000");
        return f;
    }

    public static EmployeeForm from(EmployeeRow r) {
        EmployeeForm f = new EmployeeForm();
        f.firstName = r.firstName();
        f.lastName = r.lastName();
        f.email = r.email();
        f.department = r.department();
        f.title = r.title();
        f.location = r.location();
        f.salary = r.salary();
        f.hireDate = r.hireDate();
        f.status = r.status();
        f.version = r.version();
        return f;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public EmployeeStatus getStatus() { return status; }
    public void setStatus(EmployeeStatus status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
