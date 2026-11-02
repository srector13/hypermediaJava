package dev.hypermediajava.dashboard;

import dev.hypermediajava.live.LiveHub;
import dev.hypermediajava.live.Simulator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final StatsService stats;
    private final Simulator simulator;
    private final LiveHub hub;

    public DashboardController(StatsService stats, Simulator simulator, LiveHub hub) {
        this.stats = stats;
        this.simulator = simulator;
        this.hub = hub;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", stats.current());
        model.addAttribute("running", simulator.isRunning());
        model.addAttribute("activity", hub.recentActivity());
        return "dashboard/index";
    }

    /** Re-fetched by the stats panel whenever the server signals {@code stats-changed}. */
    @GetMapping("/dashboard/stats")
    public String stats(Model model) {
        model.addAttribute("stats", stats.current());
        return "dashboard/index :: stats";
    }

    @GetMapping("/how-it-works")
    public String howItWorks() {
        return "dashboard/how-it-works";
    }
}
