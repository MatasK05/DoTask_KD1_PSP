package lt.dotask.domain;

/** Skaitinis svoris nustato {@code LOW < MEDIUM < HIGH}, kad eilė nepriklausytų nuo abėcėlės. */
public enum Priority {
    LOW(1), MEDIUM(2), HIGH(3);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
