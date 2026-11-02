package dev.hypermediajava.employee;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable view model rendered by the templates. Both stores produce exactly this shape. */
public record EmployeeRow(
        long id,
        String firstName,
        String lastName,
        String email,
        Department department,
        String title,
        String location,
        BigDecimal salary,
        LocalDate hireDate,
        EmployeeStatus status,
        long version) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    public String initials() {
        return ("" + firstName.charAt(0) + lastName.charAt(0)).toUpperCase();
    }
}
