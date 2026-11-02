package dev.hypermediajava.employee;

public enum Department {
    ENGINEERING("Engineering", "badge-soft badge-primary"),
    PRODUCT("Product", "badge-soft badge-secondary"),
    SALES("Sales", "badge-soft badge-accent"),
    MARKETING("Marketing", "badge-soft badge-info"),
    FINANCE("Finance", "badge-soft badge-success"),
    PEOPLE("People", "badge-soft badge-warning"),
    OPERATIONS("Operations", "badge-outline"),
    SUPPORT("Support", "badge-soft badge-error");

    private final String label;
    private final String badge;

    Department(String label, String badge) {
        this.label = label;
        this.badge = badge;
    }

    public String label() { return label; }

    /** DaisyUI badge class — kept next to the data so templates stay dumb. */
    public String badge() { return badge; }
}
