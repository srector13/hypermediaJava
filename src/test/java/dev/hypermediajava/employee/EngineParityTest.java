package dev.hypermediajava.employee;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole point of the POC: two very different data-access techniques must produce
 * exactly the same pages, so the UI can't tell them apart.
 */
@SpringBootTest(properties = "app.seed.count=3000")
class EngineParityTest {

    @Autowired
    EmployeeStores stores;

    static Stream<EmployeeQuery> queries() {
        List<EmployeeQuery> queries = new ArrayList<>();
        queries.add(EmployeeQuery.defaults());
        for (SortColumn column : SortColumn.values()) {
            queries.add(new EmployeeQuery(null, null, null, column.param(), "asc", 3, 10));
            queries.add(new EmployeeQuery(null, null, null, column.param(), "desc", 0, 50));
        }
        queries.add(new EmployeeQuery("ana", null, null, "salary", "desc", 0, 25));
        queries.add(new EmployeeQuery("engineer", Department.ENGINEERING, EmployeeStatus.REMOTE, "hired", "asc", 1, 10));
        queries.add(new EmployeeQuery("berlin", null, EmployeeStatus.ACTIVE, "name", "asc", 0, 100));
        queries.add(new EmployeeQuery("50%_", null, null, null, null, 0, 25)); // wildcards are literals
        queries.add(new EmployeeQuery(null, Department.SUPPORT, null, "status", "desc", 999, 25)); // past the end
        return queries.stream();
    }

    @ParameterizedTest
    @MethodSource("queries")
    void jdbcAndJpaReturnIdenticalPages(EmployeeQuery query) {
        PageView jdbc = stores.get(Engine.JDBC).search(query);
        PageView jpa = stores.get(Engine.JPA).search(query);

        assertThat(jdbc.total()).isEqualTo(jpa.total());
        assertThat(jdbc.page()).isEqualTo(jpa.page());
        assertThat(jdbc.rows()).isEqualTo(jpa.rows());
    }

    @Test
    void pastTheEndIsClampedToTheLastPage() {
        PageView page = stores.get(Engine.JDBC).search(new EmployeeQuery(null, null, null, null, null, 10_000, 25));
        assertThat(page.rows()).isNotEmpty();
        assertThat(page.page()).isEqualTo(page.totalPages() - 1);
    }

    @Test
    void likeWildcardsInSearchAreEscaped() {
        PageView page = stores.get(Engine.JPA).search(new EmployeeQuery("%", null, null, null, null, 0, 25));
        assertThat(page.total()).isZero();
    }

    @Test
    void keysetScrollingMatchesAcrossEnginesAndCoversEveryRow() {
        List<Long> jdbcIds = scrollAll(stores.get(Engine.JDBC), "son");
        List<Long> jpaIds = scrollAll(stores.get(Engine.JPA), "son");

        long expected = stores.get(Engine.JDBC).search(new EmployeeQuery("son", null, null, null, null, 0, 10)).total();
        assertThat(jdbcIds).isEqualTo(jpaIds).hasSize((int) expected).doesNotHaveDuplicates();
    }

    private static List<Long> scrollAll(EmployeeStore store, String q) {
        List<Long> ids = new ArrayList<>();
        ScrollView.Cursor cursor = null;
        do {
            ScrollView batch = store.scroll(q, cursor, 7);
            batch.rows().forEach(r -> ids.add(r.id()));
            cursor = batch.next();
        } while (cursor != null);
        return ids;
    }
}
