package dev.hypermediajava.live;

import dev.hypermediajava.employee.Engine;

import java.time.LocalTime;

/** One line in the live activity feed. */
public record Activity(LocalTime at, String kind, String actor, String message, Engine engine) {

    public static Activity of(String kind, String actor, String message, Engine engine) {
        return new Activity(LocalTime.now(), kind, actor, message, engine);
    }

    /** DaisyUI status colour per kind of change. */
    public String color() {
        return switch (kind) {
            case "create" -> "status-success";
            case "delete" -> "status-error";
            case "bulk" -> "status-warning";
            case "simulate" -> "status-secondary";
            default -> "status-info";
        };
    }
}
