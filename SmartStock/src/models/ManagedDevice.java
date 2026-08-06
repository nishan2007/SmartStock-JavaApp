package models;

import java.sql.Timestamp;

public class ManagedDevice {
    private final String deviceId;
    private final String installationId;
    private final String deviceName;
    private final String receiptDeviceCode;
    private final String hostname;
    private final String osName;
    private final String osVersion;
    private final String osArch;
    private final String javaVersion;
    private final String appVersion;
    private final String localUsername;
    private final String macAddresses;
    private final String lastUserName;
    private final String lastStoreName;
    private final boolean approved;
    private final boolean persistentLoginAllowed;
    private final boolean autoLogoutEnabled;
    private final int autoLogoutMinutes;
    private final boolean blocked;
    private final boolean allowSales;
    private final boolean allowOrders;
    private final Timestamp firstSeen;
    private final Timestamp lastSeen;
    private final Timestamp approvedAt;
    private final String approvedByName;
    private final Timestamp blockedAt;
    private final String blockedByName;
    private final String statusNotes;
    private final long sessionCount;
    private final long activeSessionCount;
    private final Timestamp latestLoginTime;
    private final Timestamp latestLogoutTime;
    private final String latestSessionStatus;

    public ManagedDevice(
            String deviceId,
            String installationId,
            String deviceName,
            String receiptDeviceCode,
            String hostname,
            String osName,
            String osVersion,
            String osArch,
            String javaVersion,
            String appVersion,
            String localUsername,
            String macAddresses,
            String lastUserName,
            String lastStoreName,
            boolean approved,
            boolean persistentLoginAllowed,
            boolean autoLogoutEnabled,
            int autoLogoutMinutes,
            boolean blocked,
            boolean allowSales,
            boolean allowOrders,
            Timestamp firstSeen,
            Timestamp lastSeen,
            Timestamp approvedAt,
            String approvedByName,
            Timestamp blockedAt,
            String blockedByName,
            String statusNotes,
            long sessionCount,
            long activeSessionCount,
            Timestamp latestLoginTime,
            Timestamp latestLogoutTime,
            String latestSessionStatus
    ) {
        this.deviceId = deviceId;
        this.installationId = installationId;
        this.deviceName = deviceName;
        this.receiptDeviceCode = receiptDeviceCode;
        this.hostname = hostname;
        this.osName = osName;
        this.osVersion = osVersion;
        this.osArch = osArch;
        this.javaVersion = javaVersion;
        this.appVersion = appVersion;
        this.localUsername = localUsername;
        this.macAddresses = macAddresses;
        this.lastUserName = lastUserName;
        this.lastStoreName = lastStoreName;
        this.approved = approved;
        this.persistentLoginAllowed = persistentLoginAllowed;
        this.autoLogoutEnabled = autoLogoutEnabled;
        this.autoLogoutMinutes = autoLogoutMinutes;
        this.blocked = blocked;
        this.allowSales = allowSales;
        this.allowOrders = allowOrders;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.approvedAt = approvedAt;
        this.approvedByName = approvedByName;
        this.blockedAt = blockedAt;
        this.blockedByName = blockedByName;
        this.statusNotes = statusNotes;
        this.sessionCount = sessionCount;
        this.activeSessionCount = activeSessionCount;
        this.latestLoginTime = latestLoginTime;
        this.latestLogoutTime = latestLogoutTime;
        this.latestSessionStatus = latestSessionStatus;
    }

    public String getDeviceId() { return deviceId; }
    public String getInstallationId() { return installationId; }
    public String getDeviceName() { return deviceName; }
    public String getReceiptDeviceCode() { return receiptDeviceCode; }
    public String getHostname() { return hostname; }
    public String getOsName() { return osName; }
    public String getOsVersion() { return osVersion; }
    public String getOsArch() { return osArch; }
    public String getJavaVersion() { return javaVersion; }
    public String getAppVersion() { return appVersion; }
    public String getLocalUsername() { return localUsername; }
    public String getMacAddresses() { return macAddresses; }
    public String getLastUserName() { return lastUserName; }
    public String getLastStoreName() { return lastStoreName; }
    public boolean isApproved() { return approved; }
    public boolean isPersistentLoginAllowed() { return persistentLoginAllowed; }
    public boolean isAutoLogoutEnabled() { return autoLogoutEnabled; }
    public int getAutoLogoutMinutes() { return autoLogoutMinutes; }
    public boolean isBlocked() { return blocked; }
    public boolean isAllowSales() { return allowSales; }
    public boolean isAllowOrders() { return allowOrders; }
    public Timestamp getFirstSeen() { return firstSeen; }
    public Timestamp getLastSeen() { return lastSeen; }
    public Timestamp getApprovedAt() { return approvedAt; }
    public String getApprovedByName() { return approvedByName; }
    public Timestamp getBlockedAt() { return blockedAt; }
    public String getBlockedByName() { return blockedByName; }
    public String getStatusNotes() { return statusNotes; }
    public long getSessionCount() { return sessionCount; }
    public long getActiveSessionCount() { return activeSessionCount; }
    public Timestamp getLatestLoginTime() { return latestLoginTime; }
    public Timestamp getLatestLogoutTime() { return latestLogoutTime; }
    public String getLatestSessionStatus() { return latestSessionStatus; }

    public String getDisplayName() {
        if (deviceName != null && !deviceName.isBlank()) {
            return deviceName;
        }
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        return installationId == null ? "Unknown Device" : installationId;
    }

    public String getStatusLabel() {
        if (blocked) {
            return "Blocked";
        }
        if (!approved) {
            return "Pending Approval";
        }
        return "Approved";
    }
}
