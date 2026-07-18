package Receipt;

import services.LanApiClient;

import java.math.BigDecimal;
import java.sql.SQLException;

/** Loads receipt data through the authenticated store service; printing remains local. */
public final class ReceiptBuilder {
    private ReceiptBuilder() {
    }

    public static ReceiptData loadSaleReceipt(int saleId, BigDecimal cashCollected,
                                              BigDecimal changeDue) throws SQLException {
        try {
            return LanApiClient.loadSaleReceipt(saleId, cashCollected, changeDue);
        } catch (Exception ex) {
            throw new SQLException("Unable to load receipt from the SmartStock server: " + ex.getMessage(), ex);
        }
    }
}
