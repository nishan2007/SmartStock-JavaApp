package services;

/** Explicit wire values; older clients continue to use the existing Fast model. */
public enum StudioQuality {
    FAST("Fast"), BEST("Best quality");

    private final String label;
    StudioQuality(String label) { this.label = label; }
    @Override public String toString() { return label; }

    public static StudioQuality parse(String value) {
        if (value == null || value.isBlank()) return FAST;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "FAST" -> FAST;
            case "BEST" -> BEST;
            default -> throw new IllegalArgumentException("Choose Fast or Best quality.");
        };
    }
}
