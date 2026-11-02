package dev.hypermediajava.employee;

import dev.hypermediajava.live.Activity;
import dev.hypermediajava.live.LiveHub;
import dev.hypermediajava.sql.QueryStats;
import dev.hypermediajava.sql.Timed;
import dev.hypermediajava.web.ClientNames;
import dev.hypermediajava.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.FragmentsRendering;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One controller, two data-access engines. {@code /jdbc/employees} and {@code /jpa/employees} share every
 * template and every htmx interaction; only the {@link EmployeeStore} differs.
 *
 * <p>The pattern for every endpoint: return HTML. A full page for normal navigation, a fragment for htmx,
 * optionally with extra out-of-band fragments (toast, query inspector) in the same response.
 */
@Controller
@RequestMapping("/{engine:jdbc|jpa}/employees")
public class EmployeeController {

    private static final String FRAGMENTS = "employees/fragments";

    private final EmployeeStores stores;
    private final LiveHub hub;

    public EmployeeController(EmployeeStores stores, LiveHub hub) {
        this.stores = stores;
        this.hub = hub;
    }

    @ModelAttribute
    void common(@PathVariable Engine engine, Model model) {
        model.addAttribute("engine", engine);
        model.addAttribute("departments", Department.values());
        model.addAttribute("statuses", EmployeeStatus.values());
        model.addAttribute("pageSizes", EmployeeQuery.PAGE_SIZES);
    }

    // ------------------------------------------------------------------ the data table

    /**
     * Full page on first load / boosted navigation; just the table (+ inspector) for htmx requests
     * from search, filters, sort headers, pagination and live refreshes.
     */
    @GetMapping
    public Object list(@PathVariable Engine engine, @ModelAttribute("query") EmployeeQuery query,
                       HttpServletRequest request, Model model) {
        Timed.Result<PageView> result = Timed.run(engine, "page", () -> stores.get(engine).search(query));
        PageView page = result.value();
        hub.noteVisible(page.rows().stream().map(EmployeeRow::id).toList());
        model.addAttribute("page", page);
        model.addAttribute("sql", result.stats());

        if (!Htmx.wantsFragment(request)) {
            return "employees/table";
        }
        return FragmentsRendering
                .fragment(FRAGMENTS + " :: table")
                .fragment(FRAGMENTS + " :: inspector-oob")
                .build();
    }

    // ------------------------------------------------------------------ single rows

    /** Re-fetched by a row when the server signals {@code sse:employee-{id}}. Empty if it was deleted. */
    @GetMapping("/{id}/row")
    public String row(@PathVariable Engine engine, @PathVariable long id, Model model) {
        return stores.get(engine).findById(id)
                .map(row -> {
                    model.addAttribute("e", row);
                    model.addAttribute("flash", true);
                    return FRAGMENTS + " :: row";
                })
                .orElse(FRAGMENTS + " :: nothing");
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Engine engine, @PathVariable long id, Model model) {
        EmployeeRow row = stores.get(engine).findById(id).orElseThrow(NotFound::new);
        model.addAttribute("e", row);
        model.addAttribute("form", EmployeeForm.from(row));
        return FRAGMENTS + " :: edit-row";
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Engine engine, @PathVariable long id,
                         @Valid @ModelAttribute("form") EmployeeForm form, BindingResult errors,
                         HttpServletRequest request, Model model) {
        EmployeeStore store = stores.get(engine);
        EmployeeRow original = store.findById(id).orElseThrow(NotFound::new);
        model.addAttribute("e", original);
        rejectDuplicateEmail(store, form, id, errors);
        if (errors.hasErrors()) {
            // 422 + the same editor with messages; htmx is configured to swap 422s.
            return FragmentsRendering.fragment(FRAGMENTS + " :: edit-row").status(HttpStatus.UNPROCESSABLE_CONTENT).build();
        }

        Timed.Result<EmployeeRow> result;
        try {
            result = Timed.run(engine, "update", () -> store.update(id, form));
        } catch (StaleEditException stale) {
            model.addAttribute("e", stale.current());
            model.addAttribute("conflict", stale.current());
            return FragmentsRendering.fragment(FRAGMENTS + " :: edit-row").status(HttpStatus.CONFLICT).build();
        }

        EmployeeRow saved = result.value();
        String clientId = Htmx.clientId(request);
        hub.signal("employee-" + id, clientId);
        hub.signal("stats-changed", null);
        hub.activity(Activity.of("update", ClientNames.of(clientId), "updated " + saved.fullName(), engine));

        model.addAttribute("e", saved);
        model.addAttribute("flash", true);
        model.addAttribute("sql", result.stats());
        return FragmentsRendering
                .fragment(FRAGMENTS + " :: row")
                .fragment(FRAGMENTS + " :: inspector-oob")
                .fragment("fragments/ui :: toast", Map.of("kind", "success", "message", "Saved " + saved.fullName()))
                .build();
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Engine engine, @PathVariable long id, HttpServletRequest request) {
        EmployeeStore store = stores.get(engine);
        EmployeeRow row = store.findById(id).orElseThrow(NotFound::new);
        store.delete(id);
        announce(engine, request, "delete", "deleted " + row.fullName());
        // Empty main content: the row fades out (hx-swap="outerHTML swap:400ms") and is removed.
        return FragmentsRendering
                .fragment("fragments/ui :: toast", Map.of("kind", "info", "message", "Deleted " + row.fullName()))
                .build();
    }

    // ------------------------------------------------------------------ create (modal)

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", EmployeeForm.blank());
        return FRAGMENTS + " :: create-modal";
    }

    @PostMapping
    public Object create(@PathVariable Engine engine, @Valid @ModelAttribute("form") EmployeeForm form,
                         BindingResult errors, HttpServletRequest request) {
        EmployeeStore store = stores.get(engine);
        rejectDuplicateEmail(store, form, null, errors);
        if (errors.hasErrors()) {
            return FragmentsRendering.fragment(FRAGMENTS + " :: create-modal").status(HttpStatus.UNPROCESSABLE_CONTENT).build();
        }
        EmployeeRow created = store.create(form);
        announce(engine, request, "create", "hired " + created.fullName());
        // Empty main content closes the modal; HX-Trigger tells this tab's table to refresh.
        return FragmentsRendering
                .fragment("fragments/ui :: toast", Map.of("kind", "success", "message", "Added " + created.fullName()))
                .header(Htmx.TRIGGER, "employees-changed")
                .build();
    }

    /** Inline validation while typing in the create form. */
    @GetMapping("/validate/email")
    public String validateEmail(@PathVariable Engine engine, @RequestParam(defaultValue = "") String email, Model model) {
        String trimmed = email.strip();
        String message = null;
        boolean ok = false;
        if (trimmed.isEmpty()) {
            message = null;
        } else if (!trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            message = "That doesn't look like an email address";
        } else if (stores.get(engine).emailTaken(trimmed, null)) {
            message = "Already used by another employee";
        } else {
            ok = true;
            message = "Available";
        }
        model.addAttribute("ok", ok);
        model.addAttribute("message", message);
        return FRAGMENTS + " :: email-feedback";
    }

    // ------------------------------------------------------------------ bulk actions

    @PostMapping("/bulk/raise")
    public Object bulkRaise(@PathVariable Engine engine, @RequestParam(name = "ids", required = false) List<Long> ids,
                            @RequestParam(defaultValue = "5") BigDecimal percent, HttpServletRequest request) {
        List<Long> selected = ids == null ? List.of() : ids;
        Timed.Result<Integer> result = Timed.run(engine, "bulk raise",
                () -> stores.get(engine).bulkRaise(selected, percent.min(BigDecimal.valueOf(50))));
        return bulkResponse(engine, request, result, "Gave a " + percent.stripTrailingZeros().toPlainString()
                + "%% raise to %d employee(s)");
    }

    @PostMapping("/bulk/delete")
    public Object bulkDelete(@PathVariable Engine engine, @RequestParam(name = "ids", required = false) List<Long> ids,
                             HttpServletRequest request) {
        List<Long> selected = ids == null ? List.of() : ids;
        Timed.Result<Integer> result = Timed.run(engine, "bulk delete", () -> stores.get(engine).bulkDelete(selected));
        return bulkResponse(engine, request, result, "Deleted %d employee(s)");
    }

    private Object bulkResponse(Engine engine, HttpServletRequest request, Timed.Result<Integer> result, String template) {
        int n = result.value();
        String message = template.formatted(n);
        if (n > 0) {
            announce(engine, request, "bulk", message.substring(0, 1).toLowerCase() + message.substring(1));
        }
        QueryStats stats = result.stats();
        return FragmentsRendering
                .fragment("fragments/ui :: toast", Map.of("kind", n > 0 ? "success" : "warning",
                        "message", n > 0 ? message : "Select some rows first"))
                .fragment(FRAGMENTS + " :: inspector-oob", Map.of("sql", stats))
                .header(Htmx.TRIGGER, "employees-changed")
                .build();
    }

    // ------------------------------------------------------------------ helpers

    /** Notify every other tab (they refresh via their own engine) and the activity feed. */
    private void announce(Engine engine, HttpServletRequest request, String kind, String message) {
        String clientId = Htmx.clientId(request);
        hub.signal("employees-changed", clientId);
        hub.signal("stats-changed", null);
        hub.activity(Activity.of(kind, ClientNames.of(clientId), message, engine));
    }

    private static void rejectDuplicateEmail(EmployeeStore store, EmployeeForm form, Long id, BindingResult errors) {
        if (form.getEmail() != null && !errors.hasFieldErrors("email") && store.emailTaken(form.getEmail(), id)) {
            errors.rejectValue("email", "duplicate", "Already used by another employee");
        }
    }

    static class NotFound extends org.springframework.web.server.ResponseStatusException {
        NotFound() {
            super(HttpStatus.NOT_FOUND);
        }
    }
}
