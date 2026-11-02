package dev.hypermediajava.employee;

public enum EmployeeStatus {
    ACTIVE("Active", "badge-success"),
    REMOTE("Remote", "badge-info"),
    ON_LEAVE("On leave", "badge-warning"),
    CONTRACTOR("Contractor", "badge-ghost");

    private final String label;
    private final String badge;

    EmployeeStatus(String label, String badge) {
        this.label = label;
        this.badge = badge;
    }

    public String label() { return label; }

    public String badge() { return badge; }
}
