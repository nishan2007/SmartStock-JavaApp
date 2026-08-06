package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import managers.SessionManager;

import java.sql.Connection;

/** Uses local administrator enforcement on a server, including an inert recovery standby. */
public final class ServerManagementClient {
    private static final Gson GSON = new Gson();

    private ServerManagementClient() { }

    public static LanApiClient.ServerAdminState load() throws Exception {
        if (!isLocalServer()) return LanApiClient.loadServerAdminState();
        try (Connection connection = DB.getConnection()) {
            JsonObject value = GSON.toJsonTree(LanServerAdminService.list(connection,
                    requiredUserId(), requiredLocationId())).getAsJsonObject();
            return GSON.fromJson(value, LanApiClient.ServerAdminState.class);
        }
    }

    public static JsonObject update(LanApiClient.ServerAdminUpdate request, String idempotencyKey) throws Exception {
        if (!isLocalServer()) return LanApiClient.updateManagedServer(request,idempotencyKey);
        JsonObject body=GSON.toJsonTree(request).getAsJsonObject();
        body.addProperty("idempotencyKey",idempotencyKey);
        try (Connection connection = DB.getConnection()) {
            return GSON.toJsonTree(LanServerAdminService.mutate(connection,body,requiredUserId(),
                    SessionManager.getCurrentUserDisplayName(),requiredLocationId())).getAsJsonObject();
        }
    }

    private static boolean isLocalServer() {
        return DatabaseConfig.load().mode()==DatabaseMode.SERVER;
    }

    private static int requiredUserId() {
        Integer value=SessionManager.getCurrentUserId();
        if(value==null)throw new IllegalStateException("An administrator session is required.");
        return value;
    }

    private static int requiredLocationId() {
        Integer value=SessionManager.getCurrentLocationId();
        if(value==null)value=DatabaseConfig.load().locationId();
        if(value==null)throw new IllegalStateException("A store assignment is required.");
        return value;
    }
}
