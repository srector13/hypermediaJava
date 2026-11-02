package dev.hypermediajava.employee;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything that defines "which rows am I looking at". Bound straight from query parameters,
 * normalised here, and turned back into URLs for sort headers and pagination links — the server
 * owns the state, the browser just follows links (HATEOAS, the original "state management library").
 */
public record EmployeeQuery(
        String q,
        Department department,
        EmployeeStatus status,
        String sort,
        String dir,
        Integer page,
        Integer size) {

    public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);
    public static final int DEFAULT_SIZE = 25;

    public EmployeeQuery {
        q = q == null ? "" : q.strip();
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        sort = SortColumn.fromParam(sort).orElse(SortColumn.NAME).param();
        dir = "desc".equalsIgnoreCase(dir) ? "desc" : "asc";
        page = page == null || page < 0 ? 0 : page;
        size = size == null || !PAGE_SIZES.contains(size) ? DEFAULT_SIZE : size;
    }

    public static EmployeeQuery defaults() {
        return new EmployeeQuery(null, null, null, null, null, null, null);
    }

    public SortColumn sortColumn() {
        return SortColumn.fromParam(sort).orElseThrow();
    }

    public boolean descending() {
        return "desc".equals(dir);
    }

    public boolean hasFilters() {
        return !q.isEmpty() || department != null || status != null;
    }

    public long offset() {
        return (long) page * size;
    }

    public EmployeeQuery withPage(int newPage) {
        return new EmployeeQuery(q, department, status, sort, dir, newPage, size);
    }

    /** Clicking the active column flips direction; clicking a new column sorts ascending from page 1. */
    public EmployeeQuery toggleSort(String column) {
        String newDir = sort.equals(column) && !descending() ? "desc" : "asc";
        return new EmployeeQuery(q, department, status, column, newDir, 0, size);
    }

    /** LIKE pattern with wildcards in user input escaped, so searching for "50%" means a literal 50%. */
    public String likePattern() {
        return likePattern(q);
    }

    public static String likePattern(String raw) {
        String escaped = raw.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public String toQueryString() {
        Map<String, Object> params = new LinkedHashMap<>();
        if (!q.isEmpty()) params.put("q", q);
        if (department != null) params.put("department", department.name());
        if (status != null) params.put("status", status.name());
        params.put("sort", sort);
        params.put("dir", dir);
        if (page > 0) params.put("page", page);
        if (size != DEFAULT_SIZE) params.put("size", size);
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public String url(Engine engine) {
        return "/" + engine.path() + "/employees?" + toQueryString();
    }
}
