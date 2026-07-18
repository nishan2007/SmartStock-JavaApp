package Receipt;

import services.LanApiClient;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

public class CustomOrderSlipBuilder {
    private CustomOrderSlipBuilder() {
    }

    public static CustomOrderSlipData buildFromOrderNumber(String orderNumber) throws SQLException {
        try{return LanApiClient.loadCustomOrderSlip(orderNumber);}catch(Exception e){throw new SQLException("Unable to load custom order slip from the SmartStock server. "+e.getMessage(),e);}
    }

    public static CustomOrderSlipData sample() {
        return new CustomOrderSlipData(
                "CO-20260522-001",
                "Alex Customer",
                "555-0199",
                "CA-000100",
                LocalDate.of(2026, 5, 30),
                Timestamp.valueOf("2026-05-22 10:30:00"),
                "Sample Cashier",
                "Main Store",
                "POS-01",
                "CASH",
                "",
                "PARTIAL",
                new BigDecimal("86.50"),
                new BigDecimal("30.00"),
                new BigDecimal("56.50"),
                "Use royal blue thread. Call before production if design is unclear.",
                List.of(
                        new CustomOrderSlipData.Line("Logo T-Shirt", "Large / Black", "Front print / 4 lines", "Place logo centered on chest.", new BigDecimal("42.50")),
                        new CustomOrderSlipData.Line("Custom Cap", "Navy", "Embroidery / Side placement", "Match thread to shirt.", new BigDecimal("44.00"))
                )
        );
    }

}
