package dev.hypermediajava.live;

import dev.hypermediajava.employee.EmployeeForm;
import dev.hypermediajava.employee.EmployeeRow;
import dev.hypermediajava.employee.EmployeeStatus;
import dev.hypermediajava.employee.EmployeeStore;
import dev.hypermediajava.employee.EmployeeStores;
import dev.hypermediajava.employee.Engine;
import dev.hypermediajava.employee.StaleEditException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Other users" for a single-person demo: while enabled, it edits a random row that somebody is currently
 * looking at every ~1.5s, alternating between the JDBC and JPA stores, and broadcasts the change.
 * Watch rows flash and the dashboard tick without touching the keyboard.
 */
@Component
public class Simulator {

    private static final Logger log = LoggerFactory.getLogger(Simulator.class);
    private static final EmployeeStatus[] STATUSES = EmployeeStatus.values();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final EmployeeStores stores;
    private final LiveHub hub;
    private final JdbcClient jdbc;
    private long ticks;

    public Simulator(EmployeeStores stores, LiveHub hub, JdbcClient jdbc) {
        this.stores = stores;
        this.hub = hub;
        this.jdbc = jdbc;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean toggle() {
        boolean now = !running.get();
        running.set(now);
        return now;
    }

    @Scheduled(fixedDelayString = "${app.simulator.interval-ms:1500}")
    void tick() {
        if (!running.get()) return;
        if (hub.viewerCount() == 0) {
            running.set(false); // nobody watching: stop burning cycles
            return;
        }
        Engine engine = (ticks++ % 2 == 0) ? Engine.JDBC : Engine.JPA;
        EmployeeStore store = stores.get(engine);
        pickTarget().flatMap(store::findById).ifPresent(row -> mutate(store, row));
    }

    private Optional<Long> pickTarget() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Long> visible = hub.recentlyVisible();
        if (!visible.isEmpty() && rnd.nextInt(10) < 9) {
            // favour the first ~50 entries: the rows on screen most recently
            return Optional.of(visible.get(rnd.nextInt(Math.min(50, visible.size()))));
        }
        return jdbc.sql("SELECT id FROM employee ORDER BY RAND() LIMIT 1").query(Long.class).optional();
    }

    private void mutate(EmployeeStore store, EmployeeRow row) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        EmployeeForm form = EmployeeForm.from(row);
        String message;
        int roll = rnd.nextInt(100);
        if (roll < 65) {
            double pct = rnd.nextDouble(-0.04, 0.09);
            BigDecimal salary = row.salary().multiply(BigDecimal.valueOf(1 + pct)).setScale(-2, RoundingMode.HALF_UP).max(new BigDecimal("15000"));
            form.setSalary(salary);
            message = "%s's salary %s to %s".formatted(row.fullName(), pct >= 0 ? "rose" : "fell",
                    NumberFormat.getCurrencyInstance(Locale.US).format(salary));
        } else if (roll < 85) {
            EmployeeStatus next = STATUSES[rnd.nextInt(STATUSES.length)];
            form.setStatus(next);
            message = "%s is now %s".formatted(row.fullName(), next.label());
        } else {
            String title = row.title().startsWith("Senior ") ? "Lead " + row.title().substring(7)
                    : row.title().startsWith("Lead ") ? row.title() : "Senior " + row.title();
            form.setTitle(title.length() > 80 ? row.title() : title);
            message = "%s was promoted to %s".formatted(row.fullName(), form.getTitle());
        }
        try {
            store.update(row.id(), form);
            hub.signal("employee-" + row.id(), null);
            hub.signal("stats-changed", null);
            hub.activity(Activity.of("simulate", "Simulator", message, store.engine()));
        } catch (StaleEditException e) {
            log.debug("Simulator lost a race on {}", row.id());
        }
    }
}
