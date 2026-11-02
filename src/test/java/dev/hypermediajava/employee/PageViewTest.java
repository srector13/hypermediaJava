package dev.hypermediajava.employee;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageViewTest {

    private static PageView page(int zeroBasedPage, long total) {
        return new PageView(Engine.JDBC, new EmployeeQuery(null, null, null, null, null, zeroBasedPage, 25), List.of(), total);
    }

    @Test
    void paginationShowsEdgesAndNeighboursWithGaps() {
        assertThat(page(0, 50_000).pageNumbers()).containsExactly(1, 2, 3, PageView.GAP, 2000);
        assertThat(page(999, 50_000).pageNumbers()).containsExactly(1, PageView.GAP, 998, 999, 1000, 1001, 1002, PageView.GAP, 2000);
        assertThat(page(1999, 50_000).pageNumbers()).containsExactly(1, PageView.GAP, 1998, 1999, 2000);
        assertThat(page(0, 60).pageNumbers()).containsExactly(1, 2, 3);
        assertThat(page(0, 0).pageNumbers()).containsExactly(1);
    }

    @Test
    void queryIsNormalisedAndUntrustedSortIsIgnored() {
        EmployeeQuery q = new EmployeeQuery("  ana ", null, null, "salary; drop table employee", "sideways", -4, 7);
        assertThat(q.q()).isEqualTo("ana");
        assertThat(q.sort()).isEqualTo("name");
        assertThat(q.dir()).isEqualTo("asc");
        assertThat(q.page()).isZero();
        assertThat(q.size()).isEqualTo(EmployeeQuery.DEFAULT_SIZE);
    }

    @Test
    void sortLinksToggleDirectionAndResetPage() {
        EmployeeQuery q = new EmployeeQuery("x y", Department.SALES, null, "salary", "asc", 4, 50);
        assertThat(q.toggleSort("salary").url(Engine.JPA))
                .isEqualTo("/jpa/employees?q=x+y&department=SALES&sort=salary&dir=desc&size=50");
        assertThat(q.toggleSort("hired").dir()).isEqualTo("asc");
    }
}
