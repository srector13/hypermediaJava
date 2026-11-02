package dev.hypermediajava.live;

import dev.hypermediajava.web.ClientNames;
import dev.hypermediajava.web.Htmx;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Controller
public class LiveController {

    private final LiveHub hub;
    private final Simulator simulator;

    public LiveController(LiveHub hub, Simulator simulator) {
        this.hub = hub;
        this.simulator = simulator;
    }

    /** The one SSE connection each tab opens (via {@code sse-connect} on &lt;body&gt;). */
    @GetMapping(path = "/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream(@RequestParam(required = false) String client) {
        return hub.connect(client);
    }

    @PostMapping("/live/simulator")
    public String toggleSimulator(HttpServletRequest request, Model model) {
        boolean running = simulator.toggle();
        String who = ClientNames.of(Htmx.clientId(request));
        hub.activity(Activity.of("bulk", who, (running ? "started" : "stopped") + " the traffic simulator", null));
        // keep every other open dashboard's toggle in sync
        hub.publish("simulator", hub.render("live/fragments", "simulator-toggle", Map.of("running", running)), Htmx.clientId(request));
        model.addAttribute("running", running);
        return "live/fragments :: simulator-toggle";
    }
}
