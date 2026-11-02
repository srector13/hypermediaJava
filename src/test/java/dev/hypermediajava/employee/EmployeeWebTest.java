package dev.hypermediajava.employee;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The htmx contract: same URL, full page or fragment, plus the right status codes and headers. */
@SpringBootTest(properties = "app.seed.count=3000")
class EmployeeWebTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    EmployeeStores stores;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("varyOnHtmxFilter", Filter.class))
                .build();
    }

    @Test
    void normalRequestGetsTheFullPage() throws Exception {
        mvc.perform(get("/jdbc/employees"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")))
                .andExpect(content().string(containsString("id=\"table-container\"")))
                .andExpect(header().stringValues("Vary", org.hamcrest.Matchers.hasItem(containsString("HX-Request"))));
    }

    @Test
    void htmxRequestGetsOnlyTheTableAndAnOutOfBandInspector() throws Exception {
        mvc.perform(get("/jpa/employees").param("q", "ana").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(containsString("<tbody>")))
                .andExpect(content().string(containsString("hx-swap-oob=\"true\"")))
                .andExpect(content().string(containsString("count(e1_0.id)"))); // JPA's count query, shown in the inspector
    }

    @Test
    void boostedNavigationAndHistoryRestoreGetTheFullPage() throws Exception {
        mvc.perform(get("/jdbc/employees").header("HX-Request", "true").header("HX-Boosted", "true"))
                .andExpect(content().string(containsString("<html")));
        mvc.perform(get("/jdbc/employees").header("HX-Request", "true").header("HX-History-Restore-Request", "true"))
                .andExpect(content().string(containsString("<html")));
    }

    @Test
    void jdbcUsesOneRoundTripWithAWindowCount() throws Exception {
        mvc.perform(get("/jdbc/employees").header("HX-Request", "true"))
                .andExpect(content().string(containsString("COUNT(*) OVER()")));
    }

    @Test
    void invalidEditReturns422WithTheEditorAndMessages() throws Exception {
        EmployeeRow row = stores.get(Engine.JDBC).findById(11).orElseThrow();
        mvc.perform(put("/jdbc/employees/11").header("HX-Request", "true")
                        .contentType("application/x-www-form-urlencoded")
                        .content(form(row, "", row.version())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("class=\"editing")))
                .andExpect(content().string(containsString("must not be blank")));
    }

    @Test
    void staleEditReturns409WithAConflictNotice() throws Exception {
        EmployeeRow row = stores.get(Engine.JPA).findById(12).orElseThrow();
        mvc.perform(put("/jpa/employees/12").header("HX-Request", "true")
                        .contentType("application/x-www-form-urlencoded")
                        .content(form(row, "First", row.version())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("row-flash")))
                .andExpect(content().string(containsString("beforeend:#toasts")));
        // same version again: someone (us) already bumped it
        mvc.perform(put("/jdbc/employees/12").header("HX-Request", "true")
                        .contentType("application/x-www-form-urlencoded")
                        .content(form(row, "Second", row.version())))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Someone else just changed this row")));
    }

    @Test
    void createTellsTheTableToRefresh() throws Exception {
        mvc.perform(post("/jdbc/employees").header("HX-Request", "true")
                        .param("firstName", "Grace").param("lastName", "Hopper").param("email", "grace.hopper@acme.test")
                        .param("department", "ENGINEERING").param("title", "Rear Admiral").param("location", "Arlington")
                        .param("salary", "150000").param("hireDate", "2020-01-01").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "employees-changed"))
                .andExpect(content().string(containsString("Added Grace Hopper")));

        mvc.perform(get("/jpa/employees/validate/email").param("email", "GRACE.hopper@acme.test").header("HX-Request", "true"))
                .andExpect(content().string(containsString("Already used")));
    }

    @Test
    void htmxErrorsBecomeToasts() throws Exception {
        mvc.perform(get("/jdbc/employees/987654/edit").header("HX-Request", "true"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("HX-Retarget", "#toasts"))
                .andExpect(content().string(containsString("no longer exists")));
    }

    @Test
    void deletedRowsRenderAsNothingSoTheyDisappear() throws Exception {
        mvc.perform(get("/jpa/employees/987654/row").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void otherPagesRender() throws Exception {
        for (String url : new String[]{"/", "/how-it-works", "/jdbc/feed", "/jpa/feed?q=son"}) {
            mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().string(containsString("</html>")));
        }
        mvc.perform(get("/jdbc/feed/more").param("afterName", "M").param("afterId", "1").header("HX-Request", "true"))
                .andExpect(content().string(containsString("Batch")))
                .andExpect(content().string(containsString("hx-trigger=\"revealed\"")));
    }

    private static String form(EmployeeRow r, String firstName, long version) {
        return "firstName=" + firstName + "&lastName=" + r.lastName() + "&email=" + r.email()
                + "&department=" + r.department() + "&title=" + r.title() + "&location=" + r.location()
                + "&salary=" + r.salary() + "&hireDate=" + r.hireDate() + "&status=" + r.status()
                + "&version=" + version;
    }
}
