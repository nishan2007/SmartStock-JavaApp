package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManagerApprovalCoverageTest {
    private static final Path SOURCE = Path.of(System.getProperty("user.dir"), "src");

    @Test
    void saleEditsRequestApprovalBeforeCheckoutAndCarryTrustedTokens() throws Exception {
        String sale = Files.readString(SOURCE.resolve("ui/screens/MakeASale.java"));
        String client = Files.readString(SOURCE.resolve("services/LanApiClient.java"));

        assertTrue(sale.contains("createApprovalAwareCartEditor(CART_COL_PRICE)"));
        assertTrue(sale.contains("createApprovalAwareCartEditor(CART_COL_ITEM_DISCOUNT)"));
        assertTrue(sale.contains("handleSaleDiscountEditOverride();"));
        assertTrue(sale.contains("terminateEditOnFocusLost"));
        assertFalse(sale.contains("discountPercentField.setEnabled(canApplySaleDiscount())"));
        assertTrue(client.contains("String priceApprovalToken, String priceOverrideReason"));
        assertTrue(client.contains("String saleDiscountApprovalToken"));
    }

    @Test
    void checkoutAndHeldCartsEnforceTheSameApprovalActions() throws Exception {
        String checkout = Files.readString(SOURCE.resolve("services/LanSalesService.java"));
        String held = Files.readString(SOURCE.resolve("services/LanHeldCartService.java"));
        String compactCheckout = checkout.replaceAll("\\s+", " ");
        String compactHeld = held.replaceAll("\\s+", " ");

        for (String action : new String[]{
                "\"CHANGE_SALE_ITEM_PRICE\", \"Price Override\"",
                "\"APPLY_SALE_DISCOUNT\", \"Item Discount Override\"",
                "\"APPLY_SALE_DISCOUNT\", \"Sale Discount Approval\"",
                "\"SALE_DISCOUNT_OVERRIDE\", \"Sale Discount Override\""
        }) {
            assertTrue(compactCheckout.contains(action), "Checkout is missing " + action);
            assertTrue(compactHeld.contains(action), "Held carts are missing " + action);
        }
        assertTrue(held.contains("unitPrice.compareTo(catalog.price()) != 0"));
        assertTrue(held.contains("item.put(\"catalogPrice\""));
    }

    @Test
    void otherManagerApprovalSurfacesHaveServerConsumption() throws Exception {
        String server = Files.readString(SOURCE.resolve("services/LanApiServer.java"));
        String inventory = Files.readString(SOURCE.resolve("services/LanInventoryService.java"));

        assertTrue(inventory.contains(
                "approvals.consume(request.overrideApprovalToken(), \"RECEIVING_STOCK_OVERRIDE\""));
        assertTrue(server.contains(
                "ServerTimeClockManager.MULTIPLE_SESSION_OVERRIDE_PERMISSION,\"Time Clock Multiple Session Override\""));
        assertTrue(server.contains(
                "\"CUSTOM_ORDER_REFUND_APPROVAL\",\"Custom Order Refund Approval\""));
        assertTrue(server.contains(
                "\"CUSTOM_ORDER_DEPOSIT_OVERRIDE\",\"Custom Order Deposit Override\""));
        assertTrue(server.contains(
                "\"CUSTOM_ORDER_LINE_DISCOUNT\",\"Custom Order Line Discount Override\""));
        assertTrue(server.contains(
                "\"CUSTOM_ORDER_PRICE_OVERRIDE\",\"Custom Order Price Override\""));
        assertTrue(server.contains(
                "\"CHANGE_SALE_ITEM_PRICE\",\"Quotation Price Override\""));
        assertTrue(server.contains("existingQuotationPriceApproval("));
    }
}
