package dev.hypermediajava.employee;

import dev.hypermediajava.live.LiveHub;
import dev.hypermediajava.sql.Timed;
import dev.hypermediajava.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.FragmentsRendering;

/**
 * Infinite scroll with keyset pagination. The last row of each batch carries
 * {@code hx-trigger="revealed"}: when it scrolls into view, it fetches the next batch and inserts it after itself.
 */
@Controller
@RequestMapping("/{engine:jdbc|jpa}/feed")
public class FeedController {

    private static final String FRAGMENTS = "employees/feed";

    private final EmployeeStores stores;
    private final LiveHub hub;

    public FeedController(EmployeeStores stores, LiveHub hub) {
        this.stores = stores;
        this.hub = hub;
    }

    @GetMapping
    public Object feed(@PathVariable Engine engine, @RequestParam(defaultValue = "") String q,
                       HttpServletRequest request, Model model) {
        load(engine, q, null, 1, model);
        if (Htmx.wantsFragment(request)) {
            // search box: replace the whole list
            return FragmentsRendering.fragment(FRAGMENTS + " :: list")
                    .fragment("employees/fragments :: inspector-oob").build();
        }
        return FRAGMENTS;
    }

    @GetMapping("/more")
    public Object more(@PathVariable Engine engine, @RequestParam(defaultValue = "") String q,
                       @RequestParam String afterName, @RequestParam long afterId,
                       @RequestParam(defaultValue = "2") int batch, Model model) {
        load(engine, q, new ScrollView.Cursor(afterName, afterId), batch, model);
        return FragmentsRendering.fragment(FRAGMENTS + " :: rows")
                .fragment("employees/fragments :: inspector-oob").build();
    }

    private void load(Engine engine, String q, ScrollView.Cursor after, int batch, Model model) {
        String query = q.strip();
        Timed.Result<ScrollView> result = Timed.run(engine, "scroll batch " + batch,
                () -> stores.get(engine).scroll(query, after, ScrollView.BATCH_SIZE));
        ScrollView view = result.value().withBatch(batch);
        hub.noteVisible(view.rows().stream().map(EmployeeRow::id).toList());
        model.addAttribute("engine", engine);
        model.addAttribute("feed", view);
        model.addAttribute("after", after);
        model.addAttribute("sql", result.stats());
    }
}
