package data;

public enum DatabaseMode {
    SERVER,
    CLIENT,
    CLOUD_DIRECT;

    public static DatabaseMode from(String value) {
        if (value == null || value.isBlank()) {
            return CLOUD_DIRECT;
        }
        String normalized = value.trim().replace("-", "_").toUpperCase();
        for (DatabaseMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return CLOUD_DIRECT;
    }
}
