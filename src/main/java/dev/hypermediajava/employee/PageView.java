package dev.hypermediajava.employee;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** One rendered page of the data table, independent of how it was fetched. */
public record PageView(Engine engine, EmployeeQuery query, List<EmployeeRow> rows, long total) {

    /** Marker for a "…" gap in the pagination bar. */
    public static final int GAP = -1;

    public int page() { return query.page(); }

    public int size() { return query.size(); }

    public int totalPages() {
        return (int) Math.max(1, (total + size() - 1) / size());
    }

    public long from() { return rows.isEmpty() ? 0 : query.offset() + 1; }

    public long to() { return query.offset() + rows.size(); }

    public boolean hasPrevious() { return page() > 0; }

    public boolean hasNext() { return page() + 1 < totalPages(); }

    public boolean isEmpty() { return rows.isEmpty(); }

    /** 1-based page numbers with gaps, e.g. [1, -1, 7, 8, 9, 10, 11, -1, 2000]. */
    public List<Integer> pageNumbers() {
        int current = page() + 1;
        int last = totalPages();
        TreeSet<Integer> pages = new TreeSet<>(List.of(1, last));
        for (int p = current - 2; p <= current + 2; p++) {
            if (p >= 1 && p <= last) pages.add(p);
        }
        List<Integer> out = new ArrayList<>();
        int previous = 0;
        for (int p : pages) {
            if (p - previous > 1) out.add(GAP);
            out.add(p);
            previous = p;
        }
        return out;
    }

    // ---- URLs rendered into hx-get attributes ------------------------------------------------

    public String pageUrl(int zeroBasedPage) {
        return query.withPage(zeroBasedPage).url(engine);
    }

    public String sortUrl(String column) {
        return query.toggleSort(column).url(engine);
    }

    public String selfUrl() {
        return query.url(engine);
    }

    /** aria-sort value for a column header. */
    public String ariaSort(String column) {
        if (!query.sort().equals(column)) return "none";
        return query.descending() ? "descending" : "ascending";
    }
}
