
package managers;

public final class SessionManager {

    private static Integer currentUserId;
    private static String currentUsername;
    private static String currentRole;
    private static Integer currentLocationId;
    private static String currentLocationName;
    private static String currentAccessToken;
    private static String currentRefreshToken;
    private static String currentDeviceId;
    private static Long currentDeviceSessionId;

    private SessionManager() {
    }

    public static Integer getCurrentUserId() {
        return currentUserId;
    }

    public static void setCurrentUserId(Integer currentUserId) {
        SessionManager.currentUserId = currentUserId;
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    public static void setCurrentUsername(String currentUsername) {
        SessionManager.currentUsername = currentUsername;
    }

    public static String getCurrentRole() {
        return currentRole;
    }

    public static void setCurrentRole(String currentRole) {
        SessionManager.currentRole = currentRole;
    }

    public static Integer getCurrentLocationId() {
        return currentLocationId;
    }

    public static void setCurrentLocationId(Integer currentLocationId) {
        SessionManager.currentLocationId = currentLocationId;
    }

    public static String getCurrentLocationName() {
        return currentLocationName;
    }

    public static void setCurrentLocationName(String currentLocationName) {
        SessionManager.currentLocationName = currentLocationName;
    }

    public static String getCurrentAccessToken() {
        return currentAccessToken;
    }

    public static void setCurrentAccessToken(String currentAccessToken) {
        SessionManager.currentAccessToken = currentAccessToken;
    }

    public static String getCurrentRefreshToken() {
        return currentRefreshToken;
    }

    public static void setCurrentRefreshToken(String currentRefreshToken) {
        SessionManager.currentRefreshToken = currentRefreshToken;
    }

    public static String getCurrentDeviceId() {
        return currentDeviceId;
    }

    public static void setCurrentDeviceId(String currentDeviceId) {
        SessionManager.currentDeviceId = currentDeviceId;
    }

    public static Long getCurrentDeviceSessionId() {
        return currentDeviceSessionId;
    }

    public static void setCurrentDeviceSessionId(Long currentDeviceSessionId) {
        SessionManager.currentDeviceSessionId = currentDeviceSessionId;
    }

    public static void clearSessionState() {
        currentUserId = null;
        currentUsername = null;
        currentRole = null;
        currentLocationId = null;
        currentLocationName = null;
        currentAccessToken = null;
        currentRefreshToken = null;
        currentDeviceId = null;
        currentDeviceSessionId = null;
    }
}
