package models;

public record CashDrawerContext(Long cashDrawerId, String drawerName, Long sessionId) {
    public CashDrawerContext(Long cashDrawerId, String drawerName) {
        this(cashDrawerId, drawerName, null);
    }

    public boolean isAssigned() {
        return cashDrawerId != null;
    }

    public boolean hasActiveSession() {
        return sessionId != null;
    }
}
