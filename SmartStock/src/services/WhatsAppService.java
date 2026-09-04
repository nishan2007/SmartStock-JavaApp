package services;

import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.util.UUID;

/** Register-side gateway. Meta credentials remain on the store server. */
public final class WhatsAppService {
    private WhatsAppService() { }

    public static SendResult send(String documentType, long documentId) throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("documentType", documentType);
        body.addProperty("documentId", documentId);
        try {
            JsonObject response = LanApiClient.sendWhatsApp(body, UUID.randomUUID().toString());
            return LanJson.create().fromJson(response.get("result"), SendResult.class);
        } catch (Exception ex) {
            throw ex instanceof SQLException sql ? sql : new SQLException(ex.getMessage(), ex);
        }
    }

    public record SendResult(boolean accepted, long outboxId, String metaMessageId,
                             boolean budgetWarning, String message) { }
}
