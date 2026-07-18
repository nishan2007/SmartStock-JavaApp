package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Rollback-only proof that two registers serialize on stock while still allowing negative inventory. */
public final class ConcurrentSalesProbe {
    private ConcurrentSalesProbe() { }

    public static void main(String[] args) throws Exception {
        String url = required("SMARTSTOCK_TEST_JDBC_URL");
        String user = required("SMARTSTOCK_TEST_DB_USER");
        String password = required("SMARTSTOCK_TEST_DB_PASSWORD");
        Fixture fixture;
        int original;
        try (Connection control = DriverManager.getConnection(url, user, password)) {
            fixture = fixture(control);
            original = stock(control, fixture.productId(), fixture.locationId());
        }

        CountDownLatch firstHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Integer> firstVisibleStock = new AtomicReference<>();
        AtomicReference<Integer> secondVisibleStock = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try (Connection c = connection(url, user, password)) {
                LanSalesService.checkout(c, saleBody(fixture.productId()), fixture.firstDevice(), fixture.userId(),
                        "Concurrent Sale Probe A", fixture.locationId(), ConcurrentSalesProbe::unexpectedApproval);
                firstVisibleStock.set(stock(c, fixture.productId(), fixture.locationId()));
                firstHoldingLock.countDown();
                if (!releaseFirst.await(15, TimeUnit.SECONDS)) throw new AssertionError("Timed out releasing first sale.");
                c.rollback();
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
                firstHoldingLock.countDown();
                releaseFirst.countDown();
            }
        }, "probe-register-a");

        Thread second = new Thread(() -> {
            try {
                if (!firstHoldingLock.await(15, TimeUnit.SECONDS)) throw new AssertionError("First sale did not acquire its stock lock.");
                try (Connection c = connection(url, user, password)) {
                    secondStarted.countDown();
                    LanSalesService.checkout(c, saleBody(fixture.productId()), fixture.secondDevice(), fixture.userId(),
                            "Concurrent Sale Probe B", fixture.locationId(), ConcurrentSalesProbe::unexpectedApproval);
                    secondVisibleStock.set(stock(c, fixture.productId(), fixture.locationId()));
                    c.rollback();
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            } finally {
                secondCompleted.countDown();
            }
        }, "probe-register-b");

        first.start();
        second.start();
        if (!secondStarted.await(15, TimeUnit.SECONDS)) throw new AssertionError("Second register did not begin its sale.");
        if (secondCompleted.await(750, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Second register bypassed the first sale's inventory row lock.");
        }
        releaseFirst.countDown();
        first.join(15_000);
        second.join(15_000);
        if (first.isAlive() || second.isAlive()) throw new AssertionError("Concurrent sales did not finish without deadlock.");
        if (failure.get() != null) throw new AssertionError("Concurrent sale failed.", failure.get());
        if (!Integer.valueOf(original - 1).equals(firstVisibleStock.get()))
            throw new AssertionError("First sale did not subtract inventory relatively.");
        if (!Integer.valueOf(original - 1).equals(secondVisibleStock.get()))
            throw new AssertionError("Second sale did not resume from the committed/rolled-back row state.");
        try (Connection control = DriverManager.getConnection(url, user, password)) {
            if (stock(control, fixture.productId(), fixture.locationId()) != original)
                throw new AssertionError("Rollback-only probe changed persistent inventory.");
        }
        System.out.println("Two-register concurrent sale locking and rollback checks passed.");
    }

    private static Connection connection(String url, String user, String password) throws Exception {
        Connection c = DriverManager.getConnection(url, user, password);
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            s.execute("SET LOCAL lock_timeout='10s'");
            s.execute("SET LOCAL statement_timeout='15s'");
        }
        return c;
    }

    private static Fixture fixture(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT u.user_id,ul.location_id,p.product_id,
                       (SELECT device_id FROM devices WHERE COALESCE(is_approved,FALSE) AND NOT COALESCE(is_blocked,FALSE)
                          AND COALESCE(allow_sales,TRUE) AND COALESCE(receipt_device_code,'')<>''
                        ORDER BY device_id LIMIT 1),
                       (SELECT device_id FROM devices WHERE COALESCE(is_approved,FALSE) AND NOT COALESCE(is_blocked,FALSE)
                          AND COALESCE(allow_sales,TRUE) AND COALESCE(receipt_device_code,'')<>''
                        ORDER BY device_id OFFSET 1 LIMIT 1)
                FROM users u
                JOIN user_locations ul ON ul.user_id=u.user_id
                JOIN inventory i ON i.location_id=ul.location_id
                JOIN products p ON p.product_id=i.product_id
                WHERE u.is_active=TRUE AND COALESCE(p.product_type,'INVENTORY')='INVENTORY'
                  AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions x ON x.permission_id=rp.permission_id
                              WHERE rp.role_id=u.role_id AND UPPER(x.permission_key)='MAKE_SALE')
                ORDER BY u.user_id,p.product_id LIMIT 1
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Probe needs an inventory item and employee with MAKE_SALE.");
                UUID first = (UUID) rs.getObject(4), second = (UUID) rs.getObject(5);
                if (first == null || second == null) throw new IllegalStateException("Probe needs two approved sales devices.");
                return new Fixture(rs.getInt(1), rs.getInt(2), rs.getInt(3), first, second);
            }
        }
    }

    private static int stock(Connection c, int productId, int locationId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT quantity_on_hand FROM inventory WHERE product_id=? AND location_id=?")) {
            ps.setInt(1, productId); ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new AssertionError("Probe inventory row disappeared.");
                return rs.getInt(1);
            }
        }
    }

    private static JsonObject saleBody(int productId) {
        JsonObject body = new JsonObject();
        body.addProperty("paymentMethod", "CARD");
        body.addProperty("saleDiscountPercent", BigDecimal.ZERO);
        body.addProperty("cashCollected", BigDecimal.ZERO);
        JsonObject line = new JsonObject();
        line.addProperty("productId", productId); line.addProperty("quantity", 1);
        line.addProperty("unitPrice", BigDecimal.TEN); line.addProperty("discountPercent", BigDecimal.ZERO);
        JsonArray lines = new JsonArray(); lines.add(line); body.add("lines", lines);
        return body;
    }

    private static LanSalesService.Approval unexpectedApproval(String token, String permission, String action, String reason) {
        throw new AssertionError("Probe unexpectedly requested manager approval.");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
        return value;
    }

    private record Fixture(int userId, int locationId, int productId, UUID firstDevice, UUID secondDevice) { }
}
