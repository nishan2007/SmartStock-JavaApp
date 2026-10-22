package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Friendly validation; the database trigger arbitrates concurrent writes. */
final class CustomerPhoneOwnership {
    static final String MESSAGE = "This phone number already belongs to a customer account. Select the existing account or use a different phone number.";

    private CustomerPhoneOwnership() { }

    static boolean isConflict(SQLException error) {
        return "23505".equals(error.getSQLState()) && error.getMessage() != null
                && (error.getMessage().contains(MESSAGE) || error.getMessage().contains("customer_phone_ownership_pkey"));
    }

    static void requireAvailable(Connection connection, String phone, Integer customerId) throws SQLException {
        if (phone == null || phone.isBlank()) return;
        try (var ps = connection.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM customer_phone_ownership
                  WHERE phone_key=public.customer_phone_key(?) AND customer_id<>?)
                  AND NOT EXISTS(SELECT 1 FROM customer_accounts
                  WHERE customer_id=? AND public.customer_phone_key(phone)=public.customer_phone_key(?))
                """)) {
            ps.setString(1, phone);
            ps.setInt(2, customerId == null ? -1 : customerId);
            ps.setInt(3, customerId == null ? -1 : customerId);
            ps.setString(4, phone);
            try (var rs = ps.executeQuery()) {
                if (rs.next() && rs.getBoolean(1)) throw new SQLException(MESSAGE, "23505");
            }
        }
    }
}
