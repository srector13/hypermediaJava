package dev.hypermediajava.sql;

import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures the SQL executed while rendering a request so the UI can show a live "query inspector".
 * <ul>
 *   <li>JPA: registered as a Hibernate {@link StatementInspector}, so we see the SQL Hibernate generates.</li>
 *   <li>JDBC: the store calls {@link #record(String)} with the SQL it is about to run.</li>
 * </ul>
 * Capturing is opt-in per request ({@link #begin()} / {@link #end()}), so background work is not recorded.
 */
public class SqlCapture implements StatementInspector {

    private static final ThreadLocal<List<String>> CAPTURED = new ThreadLocal<>();

    public static void begin() {
        CAPTURED.set(new ArrayList<>());
    }

    public static List<String> end() {
        List<String> captured = CAPTURED.get();
        CAPTURED.remove();
        return captured == null ? List.of() : captured;
    }

    public static void clear() {
        CAPTURED.remove();
    }

    public static void record(String sql) {
        List<String> captured = CAPTURED.get();
        if (captured != null) {
            captured.add(pretty(sql));
        }
    }

    @Override
    public String inspect(String sql) {
        record(sql);
        return sql;
    }

    private static String pretty(String sql) {
        try {
            return FormatStyle.BASIC.getFormatter().format(sql).strip();
        } catch (RuntimeException e) {
            return sql.strip();
        }
    }
}
