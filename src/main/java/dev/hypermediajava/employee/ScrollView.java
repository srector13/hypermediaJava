package dev.hypermediajava.employee;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** One batch of the infinite-scroll feed plus the keyset cursor for the next batch. */
public record ScrollView(Engine engine, String q, List<EmployeeRow> rows, Cursor next, int batch) {

    public static final int BATCH_SIZE = 30;

    /** Seek position: the sort key of the last row we sent. */
    public record Cursor(String lastName, long id) {
    }

    /** Builds a batch; the cursor for the next batch is the sort key of the last row. */
    public static ScrollView of(Engine engine, String q, List<EmployeeRow> rows, boolean more) {
        Cursor next = more && !rows.isEmpty()
                ? new Cursor(rows.getLast().lastName(), rows.getLast().id())
                : null;
        return new ScrollView(engine, q, List.copyOf(rows), next, 1);
    }

    public ScrollView withBatch(int batchNumber) {
        return new ScrollView(engine, q, rows, next, batchNumber);
    }

    public boolean hasMore() {
        return next != null;
    }

    public long loadedSoFar() {
        return (long) (batch - 1) * BATCH_SIZE + rows.size();
    }

    public String nextUrl() {
        if (next == null) return null;
        return "/" + engine.path() + "/feed/more?q=" + enc(q)
                + "&afterName=" + enc(next.lastName())
                + "&afterId=" + next.id()
                + "&batch=" + (batch + 1);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
