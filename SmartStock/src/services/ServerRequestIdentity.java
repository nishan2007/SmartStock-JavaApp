package services;

import managers.SessionManager;

/** Per-request server identity; avoids process-global Swing session state for concurrent register calls. */
public final class ServerRequestIdentity {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> SUPABASE_ACCESS_TOKEN = new ThreadLocal<>();

    private ServerRequestIdentity() { }

    public static void bind(int userId, int locationId, String locationName,
                            String userName, String deviceId, String deviceName) {
        CURRENT.set(new Identity(userId, locationId, locationName, userName, deviceId, deviceName));
    }

    public static void bindSupabaseAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) SUPABASE_ACCESS_TOKEN.remove();
        else SUPABASE_ACCESS_TOKEN.set(accessToken.trim());
    }

    public static String supabaseAccessToken() {
        String token = SUPABASE_ACCESS_TOKEN.get();
        if (token != null && !token.isBlank()) return token;
        try {
            return managers.SupabaseSessionManager.getValidAccessToken();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.io.IOException ignored) {
            return null;
        }
    }

    public static void clear() { CURRENT.remove(); SUPABASE_ACCESS_TOKEN.remove(); }

    public static Integer userId() { return current() == null ? SessionManager.getCurrentUserId() : Integer.valueOf(current().userId()); }
    public static Integer locationId() { return current() == null ? SessionManager.getCurrentLocationId() : Integer.valueOf(current().locationId()); }
    public static String locationName() { return current() == null ? SessionManager.getCurrentLocationName() : current().locationName(); }
    public static String userName() { return current() == null ? SessionManager.getCurrentUserDisplayName() : current().userName(); }
    public static String deviceId() { return current() == null ? SessionManager.getCurrentDeviceId() : current().deviceId(); }
    public static String deviceName() { return current() == null ? DeviceContextService.currentDeviceName() : current().deviceName(); }

    private static Identity current() { return CURRENT.get(); }

    private record Identity(int userId, int locationId, String locationName,
                            String userName, String deviceId, String deviceName) { }
}
