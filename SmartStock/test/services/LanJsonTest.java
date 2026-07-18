package services;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanJsonTest {
    private final Gson gson = LanJson.create();

    @Test
    void customOrderWorkflowRowsRoundTripLocalDate() {
        LanCustomOrderWorkflowService.OrderRow row =
                new LanCustomOrderWorkflowService.OrderRow(
                        42L, "CO-42", "NEW", "Customer", "555-0100",
                        LocalDate.of(2026, 7, 31),
                        new BigDecimal("100.00"), new BigDecimal("25.00"),
                        new BigDecimal("75.00"), "CASH", "", "PARTIAL",
                        "Employee", "Employee", 1234L);

        LanCustomOrderWorkflowService.OrderRow decoded =
                gson.fromJson(gson.toJson(row), LanCustomOrderWorkflowService.OrderRow.class);

        assertEquals(row, decoded);
    }

    @Test
    void customOrderSaveRequestsSerializeLocalDateAsIsoText() {
        CustomOrderDataService.OrderSaveRequest request =
                new CustomOrderDataService.OrderSaveRequest(
                        null, "Customer", "555-0100", LocalDate.of(2026, 8, 1),
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                        null, "", "UNPAID", 1, "Employee", 1, "Store",
                        "device", "Register", BigDecimal.ZERO, null, null,
                        null, "", java.util.List.of(), null);

        assertEquals("2026-08-01",
                gson.toJsonTree(request).getAsJsonObject().get("dueDate").getAsString());
    }

    @Test
    void reportFiltersRoundTripConcreteZoneRegionAsZoneIdText() {
        ZoneId zone = ZoneId.of("America/New_York");
        ReportDataService.Filters filters = new ReportDataService.Filters(
                ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, zone),
                ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone),
                zone, 1, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

        String json = gson.toJson(filters);
        ReportDataService.Filters decoded = gson.fromJson(json, ReportDataService.Filters.class);

        assertEquals("America/New_York", gson.toJsonTree(filters).getAsJsonObject().get("zone").getAsString());
        assertEquals(filters, decoded);
    }
}
