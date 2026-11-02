package dev.hypermediajava.seed;

import dev.hypermediajava.employee.Department;
import dev.hypermediajava.employee.EmployeeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates a deterministic data set (same seed => same people) large enough that server-side
 * pagination is obviously the only sane option. Uses JDBC batch inserts in a single transaction, and runs
 * during startup (after schema.sql, before the web server accepts requests) so nobody sees a half-filled table.
 */
@Component
@DependsOnDatabaseInitialization
public class DataSeeder implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[] FIRST = {
            "Ava", "Liam", "Olivia", "Noah", "Emma", "Mateo", "Sophia", "Lucas", "Isabella", "Ethan", "Mia", "Aiden",
            "Amelia", "Leo", "Harper", "Kai", "Evelyn", "Arjun", "Aaliyah", "Diego", "Chloe", "Hiro", "Zoe", "Omar",
            "Nora", "Sebastian", "Layla", "Elijah", "Priya", "Jonas", "Freya", "Mohammed", "Ines", "Tariq", "Yuki",
            "Santiago", "Grace", "Ravi", "Astrid", "Malik", "Lucia", "Felix", "Amara", "Theo", "Sana", "Nikolai",
            "Camila", "Kwame", "Elena", "Oscar", "Mei", "Gabriel", "Leila", "Henrik", "Zara", "Tomas", "Ingrid",
            "Rafael", "Hana", "Samuel"};
    private static final String[] LAST = {
            "Anderson", "Bauer", "Chen", "Dubois", "Eriksen", "Fernandez", "Garcia", "Haddad", "Ivanova", "Jensen",
            "Kowalski", "Larsen", "Martinez", "Nakamura", "Okafor", "Patel", "Quinn", "Rossi", "Silva", "Tanaka",
            "Underwood", "Varga", "Williams", "Xu", "Yilmaz", "Zimmermann", "Abara", "Becker", "Costa", "Dimitrov",
            "Evans", "Fischer", "Gupta", "Hoffmann", "Ibrahim", "Johansson", "Kim", "Lopez", "Muller", "Nguyen",
            "O'Brien", "Petrov", "Ramirez", "Schmidt", "Thompson", "Ueda", "Vasquez", "Weber", "Young", "Zhang",
            "Almeida", "Brooks", "Castillo", "Duarte", "Edwards", "Fontaine", "Gallagher", "Horvat", "Iyer", "Jovanovic",
            "Keller", "Lindqvist", "Moreau", "Novak", "Olsen", "Park", "Reyes", "Sato", "Torres", "Vogel"};
    private static final String[] LOCATIONS = {
            "New York", "San Francisco", "Austin", "Chicago", "Seattle", "Denver", "Boston", "Toronto", "London",
            "Berlin", "Amsterdam", "Lisbon", "Dublin", "Stockholm", "Singapore", "Tokyo", "Sydney", "Remote"};
    private static final Map<Department, String[]> TITLES = Map.of(
            Department.ENGINEERING, new String[]{"Software Engineer", "Backend Engineer", "Frontend Engineer", "Site Reliability Engineer", "Data Engineer", "QA Engineer", "Engineering Manager"},
            Department.PRODUCT, new String[]{"Product Manager", "Product Designer", "UX Researcher", "Technical Writer", "Product Analyst"},
            Department.SALES, new String[]{"Account Executive", "Sales Engineer", "Sales Development Rep", "Account Manager", "Sales Director"},
            Department.MARKETING, new String[]{"Content Strategist", "Growth Marketer", "Brand Designer", "Marketing Manager", "SEO Specialist"},
            Department.FINANCE, new String[]{"Financial Analyst", "Accountant", "Controller", "Payroll Specialist", "FP&A Manager"},
            Department.PEOPLE, new String[]{"Recruiter", "HR Business Partner", "People Ops Specialist", "Talent Partner"},
            Department.OPERATIONS, new String[]{"Operations Analyst", "Program Manager", "IT Administrator", "Facilities Coordinator", "Procurement Lead"},
            Department.SUPPORT, new String[]{"Support Engineer", "Customer Success Manager", "Support Specialist", "Onboarding Specialist"});
    private static final Department[] DEPARTMENTS = Department.values();
    private static final int[] DEPARTMENT_WEIGHTS = {30, 8, 16, 9, 6, 5, 10, 16};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final int count;

    public DataSeeder(JdbcTemplate jdbc, TransactionTemplate tx, @Value("${app.seed.count:50000}") int count) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.count = count;
    }

    @Override
    public void afterPropertiesSet() {
        tx.executeWithoutResult(status -> seed());
    }

    private void seed() {
        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM employee", Long.class);
        if (existing != null && existing > 0) {
            log.info("Database already has {} employees, skipping seed", existing);
            return;
        }
        long start = System.currentTimeMillis();
        Random rnd = new Random(42);
        LocalDate today = LocalDate.now();
        List<Object[]> batch = new ArrayList<>(1_000);
        String sql = """
                INSERT INTO employee (first_name, last_name, email, department, title, location, salary, hire_date, status, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""";

        for (int i = 1; i <= count; i++) {
            String first = FIRST[rnd.nextInt(FIRST.length)];
            String last = LAST[rnd.nextInt(LAST.length)];
            Department dept = weighted(rnd);
            String[] titles = TITLES.get(dept);
            String title = titles[rnd.nextInt(titles.length)];
            int level = rnd.nextInt(10);
            if (level >= 8) title = "Senior " + title;
            int base = switch (dept) {
                case ENGINEERING -> 115_000;
                case PRODUCT -> 105_000;
                case SALES -> 85_000;
                case FINANCE -> 90_000;
                case MARKETING -> 80_000;
                case PEOPLE, OPERATIONS -> 72_000;
                case SUPPORT -> 62_000;
            };
            int salary = (int) Math.round((base * (0.75 + level * 0.07) + rnd.nextGaussian() * 9_000) / 100.0) * 100;
            String email = (first + "." + last.replace("'", "")).toLowerCase() + "." + i + "@acme.test";
            EmployeeStatus status = rnd.nextInt(100) < 64 ? EmployeeStatus.ACTIVE
                    : rnd.nextInt(100) < 60 ? EmployeeStatus.REMOTE
                    : rnd.nextBoolean() ? EmployeeStatus.CONTRACTOR : EmployeeStatus.ON_LEAVE;
            LocalDate hired = today.minusDays(rnd.nextInt(15 * 365));
            batch.add(new Object[]{first, last, email, dept.name(), title, LOCATIONS[rnd.nextInt(LOCATIONS.length)],
                    BigDecimal.valueOf(Math.max(salary, 32_000)), Date.valueOf(hired), status.name()});
            if (batch.size() == 1_000) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);
        log.info("Seeded {} employees in {} ms", count, System.currentTimeMillis() - start);
    }

    private static Department weighted(Random rnd) {
        int total = 0;
        for (int w : DEPARTMENT_WEIGHTS) total += w;
        int r = rnd.nextInt(total);
        for (int i = 0; i < DEPARTMENT_WEIGHTS.length; i++) {
            r -= DEPARTMENT_WEIGHTS[i];
            if (r < 0) return DEPARTMENTS[i];
        }
        return DEPARTMENTS[0];
    }
}
