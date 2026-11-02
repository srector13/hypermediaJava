package dev.hypermediajava.employee;

/** Thrown when the row changed (someone else saved) since the user opened the editor. */
public class StaleEditException extends RuntimeException {

    private final EmployeeRow current;

    public StaleEditException(EmployeeRow current) {
        super("Employee " + current.id() + " was modified by someone else");
        this.current = current;
    }

    public EmployeeRow current() { return current; }
}
