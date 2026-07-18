package services;

/**
 * Clean-break LAN policy. There is no compatibility switch: register builds
 * always use the authenticated HTTPS service and can never fall back to JDBC.
 */
public final class LanCutoverPolicy {
    private LanCutoverPolicy() {
    }

    public static boolean isEnforced() {
        return true;
    }
}
