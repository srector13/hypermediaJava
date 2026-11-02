package dev.hypermediajava.dashboard;

import dev.hypermediajava.employee.Department;

import java.math.BigDecimal;
import java.util.List;

public record Stats(long headcount, long active, long remote, long onLeave, BigDecimal averageSalary,
                    BigDecimal payroll, List<DepartmentStats> departments) {

    public record DepartmentStats(Department department, long headcount, BigDecimal averageSalary) {
    }

    public long largestDepartment() {
        return departments.stream().mapToLong(DepartmentStats::headcount).max().orElse(1);
    }

    public BigDecimal payrollBillions() {
        return payroll.movePointLeft(9);
    }

    public int percentOf(long part) {
        return headcount == 0 ? 0 : (int) Math.round(part * 100.0 / headcount);
    }
}
