package services;

import data.EnvironmentProfile;
import utils.SecureCredentialStore;

import java.util.concurrent.atomic.AtomicReference;

/** In-process write fence controlled by the secured store-server registry. */
public final class ServerRoleGuard {
    public enum State { PRIMARY, STANDBY, DRAINING, RETIRED, FENCED, UNKNOWN }

    private static final String ROLE_SECRET = EnvironmentProfile.active().secretKey("store-server-role");
    private static final AtomicReference<State> STATE = new AtomicReference<>(storedState());

    private ServerRoleGuard() { }

    public static State state() { return STATE.get(); }

    public static void update(String role) {
        State state;
        try { state=State.valueOf(role == null ? "UNKNOWN" : role.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { state=State.UNKNOWN; }
        STATE.set(state);
        try { SecureCredentialStore.write(ROLE_SECRET,state.name()); }
        catch (java.io.IOException ex) { System.err.println("Could not persist store server role: "+ex.getMessage()); }
    }

    public static boolean blocksMutations() {
        State state = STATE.get();
        return state == State.STANDBY || state == State.DRAINING
                || state == State.RETIRED || state == State.FENCED;
    }

    public static boolean blocks(String path) {
        if (!blocksMutations() || path == null || path.startsWith("/v1/security/servers/")) return false;
        return RemoteAdminPolicy.isMutation(path) || RemoteAdminPolicy.isPhysicalOperation(path)
                || path.contains("/workflow") || path.endsWith("/checkout") || path.endsWith("/refund")
                || path.endsWith("/punch") || path.endsWith("/run") || path.endsWith("/resolve")
                || path.endsWith("/deposit") || path.endsWith("/return") || path.endsWith("/receive");
    }

    public static String safeMessage() {
        return switch (STATE.get()) {
            case STANDBY -> "This machine is a standby server and cannot accept store changes.";
            case DRAINING -> "The store server is completing a verified handoff and is not accepting new changes.";
            case RETIRED -> "This server has been retired and cannot accept store changes.";
            case FENCED -> "This server was replaced during recovery and is fenced from store changes.";
            default -> "This server is not authorized to accept store changes.";
        };
    }

    private static State storedState() {
        try {
            String value=SecureCredentialStore.read(ROLE_SECRET);
            return value==null?State.UNKNOWN:State.valueOf(value);
        } catch (Exception ex) { return State.UNKNOWN; }
    }
}
