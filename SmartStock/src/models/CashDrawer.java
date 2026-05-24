package models;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CashDrawer {
    private final long cashDrawerId;
    private final int locationId;
    private final String locationName;
    private final String drawerName;
    private final String description;
    private final BigDecimal startingCashAmount;
    private final Map<Integer, Integer> floatMix;
    private final boolean active;
    private final int activeDeviceCount;

    public CashDrawer(
            long cashDrawerId,
            int locationId,
            String locationName,
            String drawerName,
            String description,
            BigDecimal startingCashAmount,
            Map<Integer, Integer> floatMix,
            boolean active,
            int activeDeviceCount
    ) {
        this.cashDrawerId = cashDrawerId;
        this.locationId = locationId;
        this.locationName = locationName;
        this.drawerName = drawerName;
        this.description = description;
        this.startingCashAmount = startingCashAmount == null ? BigDecimal.ZERO : startingCashAmount;
        this.floatMix = floatMix == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(floatMix));
        this.active = active;
        this.activeDeviceCount = activeDeviceCount;
    }

    public long getCashDrawerId() { return cashDrawerId; }
    public int getLocationId() { return locationId; }
    public String getLocationName() { return locationName; }
    public String getDrawerName() { return drawerName; }
    public String getDescription() { return description; }
    public BigDecimal getStartingCashAmount() { return startingCashAmount; }
    public Map<Integer, Integer> getFloatMix() { return floatMix; }
    public boolean isActive() { return active; }
    public int getActiveDeviceCount() { return activeDeviceCount; }

    @Override
    public String toString() {
        return drawerName;
    }
}
