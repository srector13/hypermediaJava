package dev.hypermediajava.sql;

import dev.hypermediajava.employee.Engine;

import java.util.List;

/** What the query inspector panel shows after each table interaction. */
public record QueryStats(Engine engine, String operation, List<String> statements, long elapsedMicros) {

    public String elapsed() {
        return elapsedMicros >= 1000
                ? String.format("%.1f ms", elapsedMicros / 1000.0)
                : elapsedMicros + " µs";
    }

    public int roundTrips() {
        return statements.size();
    }
}
