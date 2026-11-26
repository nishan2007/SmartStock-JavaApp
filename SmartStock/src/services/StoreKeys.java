package services;

import java.util.List;
import java.util.UUID;

/** Wire models shared by the desktop and the authenticated store server. */
public final class StoreKeys {
    private StoreKeys() { }
    public record Key(UUID keyId, int keyNumber, String label, boolean retired, boolean paired,
                      long version, UUID custodyId, Integer holderUserId, String holderName, String checkedOutAt) {
        public String status() { return retired ? "Retired" : custodyId != null ? "Checked out" : "Available"; }
    }
    public record Employee(int userId, String name) { @Override public String toString() { return name; } }
    public record Dashboard(List<Key> keys, List<Employee> employees, int currentUserId) { }
    public record Event(UUID eventId, String keyLabel, String eventType, String actorName,
                        String holderName, String method, String reason, String createdAt) { }
    public record History(List<Event> events) { }
}
