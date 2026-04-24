package models;

import java.sql.Timestamp;

public class DeviceSessionRecord {
    private final long sessionId;
    private final String userName;
    private final String storeName;
    private final Timestamp loginTime;
    private final Timestamp logoutTime;
    private final String sessionStatus;

    public DeviceSessionRecord(
            long sessionId,
            String userName,
            String storeName,
            Timestamp loginTime,
            Timestamp logoutTime,
            String sessionStatus
    ) {
        this.sessionId = sessionId;
        this.userName = userName;
        this.storeName = storeName;
        this.loginTime = loginTime;
        this.logoutTime = logoutTime;
        this.sessionStatus = sessionStatus;
    }

    public long getSessionId() { return sessionId; }
    public String getUserName() { return userName; }
    public String getStoreName() { return storeName; }
    public Timestamp getLoginTime() { return loginTime; }
    public Timestamp getLogoutTime() { return logoutTime; }
    public String getSessionStatus() { return sessionStatus; }
}
