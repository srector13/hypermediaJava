package dev.hypermediajava.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events hub: one long-lived {@link SseEmitter} per browser tab.
 *
 * <p>Two kinds of messages go out:
 * <ul>
 *   <li><b>Signals</b> such as {@code employee-42} or {@code employees-changed}. They carry no HTML;
 *       elements subscribe with {@code hx-trigger="sse:employee-42"} and re-fetch themselves through their
 *       own URL (so a JDBC page re-fetches via JDBC, a JPA page via JPA).</li>
 *   <li><b>HTML</b> such as {@code activity}: rendered once here with Thymeleaf and swapped straight in by
 *       elements declaring {@code sse-swap="activity"}.</li>
 * </ul>
 * No websocket protocol, no client-side store, no JSON — just HTML and events over plain HTTP.
 */
@Component
public class LiveHub {

    private static final Logger log = LoggerFactory.getLogger(LiveHub.class);
    private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_VISIBLE_IDS = 400;

    private record Client(String id, SseEmitter emitter) {
    }

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final Deque<Long> recentlyVisible = new ConcurrentLinkedDeque<>();
    private final Deque<Activity> recentActivity = new ConcurrentLinkedDeque<>();
    private final TemplateEngine templates;

    public LiveHub(TemplateEngine templates) {
        this.templates = templates;
    }

    public SseEmitter connect(String clientId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        Client client = new Client(clientId == null ? "anonymous" : clientId, emitter);
        Runnable remove = () -> {
            if (clients.remove(client)) {
                broadcastViewers();
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        clients.add(client);
        send(client, "connected", "ok");
        broadcastViewers();
        return emitter;
    }

    public int viewerCount() {
        return clients.size();
    }

    /** Tell everyone (except the tab that caused it) that something changed. */
    public void signal(String event, String exceptClientId) {
        publish(event, String.valueOf(System.currentTimeMillis()), exceptClientId);
    }

    public void activity(Activity activity) {
        recentActivity.addFirst(activity);
        while (recentActivity.size() > 15) {
            recentActivity.pollLast();
        }
        publish("activity", render("live/fragments", "activity-item", Map.of("a", activity)), null);
    }

    /** Render a Thymeleaf fragment outside of a request, e.g. for an SSE payload. */
    public String render(String template, String fragment, Map<String, Object> variables) {
        Context ctx = new Context(Locale.US, variables);
        return templates.process(template, Set.of(fragment), ctx);
    }

    public void publish(String event, String data, String exceptClientId) {
        // SSE data must not contain bare newlines; HTML doesn't care about them anyway.
        String payload = data.replace("\r", "").replace('\n', ' ');
        for (Client client : clients) {
            if (exceptClientId == null || !exceptClientId.equals(client.id())) {
                send(client, event, payload);
            }
        }
    }

    private void broadcastViewers() {
        publish("viewers", String.valueOf(clients.size()), null);
    }

    private void send(Client client, String event, String data) {
        try {
            synchronized (client) {
                client.emitter().send(SseEmitter.event().name(event).data(data));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping SSE client {}: {}", client.id(), e.getMessage());
            clients.remove(client);
            client.emitter().completeWithError(e);
        }
    }

    /** Keeps connections alive through proxies and detects closed tabs. */
    @Scheduled(fixedRate = 20_000)
    void heartbeat() {
        for (Client client : clients) {
            try {
                synchronized (client) {
                    client.emitter().send(SseEmitter.event().comment("ping"));
                }
            } catch (IOException | IllegalStateException e) {
                clients.remove(client);
            }
        }
    }

    /** Newest first; rendered into the dashboard on page load, after that SSE keeps it current. */
    public List<Activity> recentActivity() {
        return new ArrayList<>(recentActivity);
    }

    // ---- which rows are people looking at? (lets the simulator touch visible rows) ----------

    public void noteVisible(Collection<Long> ids) {
        ids.forEach(recentlyVisible::addFirst);
        while (recentlyVisible.size() > MAX_VISIBLE_IDS) {
            recentlyVisible.pollLast();
        }
    }

    public List<Long> recentlyVisible() {
        return new ArrayList<>(recentlyVisible);
    }
}
