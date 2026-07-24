package data;

public enum DatabaseMode {
    SERVER,
    CLIENT,
    REMOTE_ADMIN;

    public static DatabaseMode from(String value) {
        if (value == null || value.isBlank()) {
            return CLIENT;
        }
        String normalized = value.trim().replace("-", "_").toUpperCase();
        for (DatabaseMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return CLIENT;
    }
}
