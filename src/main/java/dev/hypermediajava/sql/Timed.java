package dev.hypermediajava.sql;

import dev.hypermediajava.employee.Engine;

import java.util.function.Supplier;

/** Runs a data-access call while capturing its SQL and wall-clock time. */
public final class Timed {

    public record Result<T>(T value, QueryStats stats) {
    }

    private Timed() {
    }

    public static <T> Result<T> run(Engine engine, String operation, Supplier<T> work) {
        SqlCapture.begin();
        long start = System.nanoTime();
        try {
            T value = work.get();
            long micros = (System.nanoTime() - start) / 1_000;
            return new Result<>(value, new QueryStats(engine, operation, SqlCapture.end(), micros));
        } finally {
            SqlCapture.clear();
        }
    }
}
