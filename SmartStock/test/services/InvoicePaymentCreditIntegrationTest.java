package services;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InvoicePaymentCreditIntegrationTest {
    @Test
    void repairsAllocatedInvoicePaymentsWithoutChangingOtherPayments() throws Exception {
        String url = System.getProperty("smartstock.test.jdbc", "");
        String user = System.getProperty("smartstock.test.dbUser", "");
        assumeTrue(!url.isBlank() && !user.isBlank());
        try (var c = DriverManager.getConnection(url, user,
                System.getProperty("smartstock.test.dbPassword", ""))) {
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                s.execute("CREATE TEMP TABLE customer_account_transactions (transaction_id bigint, customer_id int, invoice_id bigint, custom_order_id bigint, sale_id bigint, transaction_type text, amount numeric, credit_applied_amount numeric) ON COMMIT DROP");
                s.execute("CREATE TEMP TABLE customer_account_payment_allocations (payment_transaction_id bigint, customer_id int, invoice_id bigint, amount numeric) ON COMMIT DROP");
                s.execute("INSERT INTO customer_account_transactions VALUES (1,10,100,NULL,NULL,'INVOICE_CREDIT',100,0),(2,10,100,NULL,NULL,'PAYMENT',-40,0),(3,10,100,NULL,NULL,'PAYMENT',-60,0),(4,11,101,NULL,NULL,'PAYMENT',-20,0),(5,10,NULL,200,NULL,'PAYMENT',-30,0),(6,10,100,NULL,NULL,'PAYMENT',-10,10),(7,10,100,NULL,NULL,'PAYMENT',-9,0)");
                s.execute("INSERT INTO customer_account_payment_allocations VALUES (2,10,100,40),(3,10,100,60),(4,11,101,20),(7,10,100,8)");
                CustomerAccountLedgerService.repairInvoicePaymentCredit(c, 10);
                try (var rs = s.executeQuery("SELECT transaction_id,credit_applied_amount FROM customer_account_transactions ORDER BY transaction_id")) {
                    int[] expected = {0,40,60,0,0,10,0};
                    for (int value : expected) {
                        rs.next();
                        assertEquals(value, rs.getBigDecimal(2).intValueExact());
                    }
                }
                CustomerAccountLedgerService.repairInvoicePaymentCredit(c, null);
                CustomerAccountLedgerService.repairInvoicePaymentCredit(c, null);
                try (var rs = s.executeQuery("SELECT SUM(" + CustomerAccountLedgerService.balanceDeltaSql("t") + ") FROM customer_account_transactions t WHERE transaction_id IN (1,2,3)")) {
                    rs.next();
                    assertEquals(0, rs.getBigDecimal(1).intValueExact());
                }
            } finally {
                c.rollback();
            }
        }
    }
}
