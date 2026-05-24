package models;

import java.sql.Timestamp;

public class CashDrawerAssignment {
    private final long assignmentId;
    private final long cashDrawerId;
    private final String drawerName;
    private final int locationId;
    private final String locationName;
    private final String deviceId;
    private final String deviceName;
    private final String hostname;
    private final boolean active;
    private final Timestamp assignedAt;
    private final String assignedByName;
    private final String notes;

    public CashDrawerAssignment(
            long assignmentId,
            long cashDrawerId,
            String drawerName,
            int locationId,
            String locationName,
            String deviceId,
            String deviceName,
            String hostname,
            boolean active,
            Timestamp assignedAt,
            String assignedByName,
            String notes
    ) {
        this.assignmentId = assignmentId;
        this.cashDrawerId = cashDrawerId;
        this.drawerName = drawerName;
        this.locationId = locationId;
        this.locationName = locationName;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.hostname = hostname;
        this.active = active;
        this.assignedAt = assignedAt;
        this.assignedByName = assignedByName;
        this.notes = notes;
    }

    public long getAssignmentId() { return assignmentId; }
    public long getCashDrawerId() { return cashDrawerId; }
    public String getDrawerName() { return drawerName; }
    public int getLocationId() { return locationId; }
    public String getLocationName() { return locationName; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getHostname() { return hostname; }
    public boolean isActive() { return active; }
    public Timestamp getAssignedAt() { return assignedAt; }
    public String getAssignedByName() { return assignedByName; }
    public String getNotes() { return notes; }

    public String getDeviceDisplayName() {
        if (deviceName != null && !deviceName.isBlank()) {
            return deviceName;
        }
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        return deviceId == null ? "Unknown Device" : deviceId;
    }
}
