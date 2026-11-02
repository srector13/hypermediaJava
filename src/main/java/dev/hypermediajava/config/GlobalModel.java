package dev.hypermediajava.config;

import dev.hypermediajava.live.LiveHub;
import dev.hypermediajava.web.ClientNames;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

import java.util.List;
import java.util.UUID;

/** Attributes the layout needs on every full page render. */
@ControllerAdvice
public class GlobalModel {

    public static final List<String> THEMES = List.of("light", "dark", "corporate", "nord", "dim", "business", "cupcake", "synthwave");

    private final LiveHub hub;

    public GlobalModel(LiveHub hub) {
        this.hub = hub;
    }

    @ModelAttribute
    public void layout(Model model, HttpServletRequest request,
                       @CookieValue(name = "theme", required = false) String theme) {
        // A new identity per full page load; htmx then sends it on every request via hx-headers,
        // and the SSE stream uses it so a tab is not notified about its own changes.
        String clientId = UUID.randomUUID().toString();
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", ClientNames.of(clientId));
        model.addAttribute("clientHeaders", "{\"X-Client-Id\": \"" + clientId + "\"}");
        model.addAttribute("theme", theme != null && THEMES.contains(theme) ? theme : "light");
        model.addAttribute("themes", THEMES);
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("viewers", hub.viewerCount());
    }
}
