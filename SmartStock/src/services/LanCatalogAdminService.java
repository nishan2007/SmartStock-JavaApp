package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only department and vendor administration. */
final class LanCatalogAdminService {
    private static final Gson GSON = new Gson();

    private LanCatalogAdminService() { }

    static Map<String, Object> departments(Connection connection, String search, int userId,
                                            int locationId) throws Exception {
        requirePermission(connection, userId, "DEPARTMENT_MANAGEMENT");
        String query = clean(search, 300);
        boolean vatEditable;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(vat_enabled,FALSE) AND COALESCE(vat_use_department_rates,FALSE)
                FROM company_customization WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) { vatEditable = rs.next() && rs.getBoolean(1); }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = """
                SELECT category_id,name,COALESCE(vat_rate_percent,0),COALESCE(description,'')
                FROM categories
                """ + (query.isBlank() ? "" : " WHERE name ILIKE ? OR COALESCE(description,'') ILIKE ?")
                + " ORDER BY name";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (!query.isBlank()) { ps.setString(1, "%" + query + "%"); ps.setString(2, "%" + query + "%"); }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(map("categoryId", rs.getInt(1), "name", rs.getString(2),
                        "vatRatePercent", rs.getBigDecimal(3), "description", rs.getString(4)));
            }
        }
        return map("departments", rows, "vatEditable", vatEditable);
    }

    static Map<String, Object> saveDepartment(Connection connection, JsonObject body, UUID deviceId,
                                               int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "DEPARTMENT_MANAGEMENT");
        DepartmentRequest request = GSON.fromJson(body, DepartmentRequest.class);
        String name = required(request == null ? null : request.name(), 200, "Department name is required.");
        String description = clean(request.description(), 2000);
        BigDecimal vat = request.vatRatePercent() == null ? BigDecimal.ZERO : request.vatRatePercent();
        if (vat.compareTo(BigDecimal.ZERO) < 0 || vat.compareTo(BigDecimal.valueOf(100)) > 0)
            throw rule(400, "VALIDATION_ERROR", "VAT percent must be between 0 and 100.");
        boolean vatEditable = departmentVatEditable(connection, locationId);
        Integer id = request.categoryId();
        try {
            if (id == null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO categories(name,vat_rate_percent,description) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name); ps.setBigDecimal(2, vatEditable ? vat : BigDecimal.ZERO);
                    ps.setString(3, description); ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Department could not be created.");
                        id = keys.getInt(1);
                    }
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement(vatEditable
                        ? "UPDATE categories SET name=?,vat_rate_percent=?,description=? WHERE category_id=?"
                        : "UPDATE categories SET name=?,description=? WHERE category_id=?")) {
                    ps.setString(1, name);
                    if (vatEditable) { ps.setBigDecimal(2, vat); ps.setString(3, description); ps.setInt(4, id); }
                    else { ps.setString(2, description); ps.setInt(3, id); }
                    if (ps.executeUpdate() != 1) throw rule(404, "DEPARTMENT_NOT_FOUND", "Department was not found.");
                }
            }
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw rule(409, "DEPARTMENT_NAME_EXISTS", "A department with this name already exists.");
            throw ex;
        }
        audit(connection, "LAN_DEPARTMENT_SAVED", deviceId, userId,
                "category_id=" + id + "; location_id=" + locationId);
        return map("categoryId", id, "name", name);
    }

    static List<Map<String, Object>> vendors(Connection connection, String search, int userId) throws Exception {
        requirePermission(connection, userId, "VENDOR_MANAGEMENT");
        String query = clean(search, 300);
        String sql = """
                SELECT vendor_id,name,COALESCE(contact_name,''),COALESCE(phone,''),COALESCE(email,''),
                  COALESCE(address,''),COALESCE(notes,''),COALESCE(is_active,TRUE)
                FROM vendors
                """ + (query.isBlank() ? "" : " WHERE name ILIKE ? OR COALESCE(contact_name,'') ILIKE ? OR COALESCE(phone,'') ILIKE ? OR COALESCE(email,'') ILIKE ? OR COALESCE(notes,'') ILIKE ?")
                + " ORDER BY name";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (!query.isBlank()) for (int i = 1; i <= 5; i++) ps.setString(i, "%" + query + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(map("vendorId", rs.getInt(1), "name", rs.getString(2),
                        "contactName", rs.getString(3), "phone", rs.getString(4), "email", rs.getString(5),
                        "address", rs.getString(6), "notes", rs.getString(7), "active", rs.getBoolean(8)));
            }
        }
        return rows;
    }

    static Map<String, Object> saveVendor(Connection connection, JsonObject body, UUID deviceId,
                                           int userId) throws Exception {
        requirePermission(connection, userId, "VENDOR_MANAGEMENT");
        VendorRequest request = GSON.fromJson(body, VendorRequest.class);
        String name = required(request == null ? null : request.name(), 200, "Vendor name is required.");
        String contact = nullable(request.contactName(), 300);
        String phone = nullable(request.phone(), 100);
        String email = nullable(request.email(), 320);
        String address = nullable(request.address(), 2000);
        String notes = nullable(request.notes(), 4000);
        Integer id = request.vendorId();
        try {
            if (id == null) {
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO vendors(name,contact_name,phone,email,address,notes,is_active)
                        VALUES (?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    bindVendor(ps, request, name, contact, phone, email, address, notes);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Vendor could not be created.");
                        id = keys.getInt(1);
                    }
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE vendors SET name=?,contact_name=?,phone=?,email=?,address=?,notes=?,is_active=?
                        WHERE vendor_id=?
                        """)) {
                    bindVendor(ps, request, name, contact, phone, email, address, notes); ps.setInt(8, id);
                    if (ps.executeUpdate() != 1) throw rule(404, "VENDOR_NOT_FOUND", "Vendor was not found.");
                }
            }
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw rule(409, "VENDOR_NAME_EXISTS", "A vendor with this name already exists.");
            throw ex;
        }
        audit(connection, "LAN_VENDOR_SAVED", deviceId, userId, "vendor_id=" + id);
        return map("vendorId", id, "name", name);
    }

    static List<Map<String, Object>> customerTypes(Connection connection, String search,
                                                   boolean activeOnly, int userId) throws Exception {
        requirePermission(connection, userId, "CUSTOMER_ACCOUNTS");
        String query = clean(search, 300);
        StringBuilder sql = new StringBuilder("""
                SELECT customer_type_id,name,COALESCE(description,''),COALESCE(is_active,TRUE)
                FROM customer_types WHERE TRUE
                """);
        if (activeOnly) sql.append(" AND COALESCE(is_active,TRUE)=TRUE");
        if (!query.isBlank()) sql.append(" AND (name ILIKE ? OR COALESCE(description,'') ILIKE ?)");
        sql.append(" ORDER BY name");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            if (!query.isBlank()) { ps.setString(1, "%" + query + "%"); ps.setString(2, "%" + query + "%"); }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(map("customerTypeId", rs.getInt(1), "name", rs.getString(2),
                        "description", rs.getString(3), "active", rs.getBoolean(4)));
            }
        }
        return rows;
    }

    static Map<String, Object> saveCustomerType(Connection connection, JsonObject body, UUID deviceId,
                                                 int userId) throws Exception {
        requirePermission(connection, userId, "CUSTOMER_ACCOUNTS");
        CustomerTypeRequest request = GSON.fromJson(body, CustomerTypeRequest.class);
        String name = required(request == null ? null : request.name(), 200, "Customer type name is required.");
        String description = nullable(request.description(), 2000);
        Integer id = request.customerTypeId();
        try {
            if (id == null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO customer_types(name,description,is_active) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name); ps.setString(2, description); ps.setBoolean(3, request.active());
                    ps.executeUpdate(); try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Customer type could not be created.");
                        id = keys.getInt(1);
                    }
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE customer_types SET name=?,description=?,is_active=? WHERE customer_type_id=?")) {
                    ps.setString(1, name); ps.setString(2, description); ps.setBoolean(3, request.active()); ps.setInt(4, id);
                    if (ps.executeUpdate() != 1) throw rule(404, "CUSTOMER_TYPE_NOT_FOUND", "Customer type was not found.");
                }
            }
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw rule(409, "CUSTOMER_TYPE_NAME_EXISTS", "A customer type with this name already exists.");
            throw ex;
        }
        audit(connection, "LAN_CUSTOMER_TYPE_SAVED", deviceId, userId, "customer_type_id=" + id);
        return map("customerTypeId", id, "name", name);
    }

    private static void bindVendor(PreparedStatement ps, VendorRequest request, String name, String contact,
                                   String phone, String email, String address, String notes) throws SQLException {
        ps.setString(1, name); ps.setString(2, contact); ps.setString(3, phone); ps.setString(4, email);
        ps.setString(5, address); ps.setString(6, notes); ps.setBoolean(7, request.active());
    }

    private static boolean departmentVatEditable(Connection connection, int locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(vat_enabled,FALSE) AND COALESCE(vat_use_department_rates,FALSE)
                FROM company_customization WHERE location_id=?
                """)) {
            ps.setInt(1, locationId); try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getBoolean(1); }
        }
    }

    private static void requirePermission(Connection connection, int userId, String permission) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1
                """)) {
            ps.setInt(1, userId); ps.setString(2, permission);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(403, "PERMISSION_DENIED", "You do not have permission for this catalog administration action.");
            }
        }
    }

    private static void audit(Connection connection, String type, UUID deviceId, int userId,
                              String details) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details)
                VALUES (?,?,?,?)
                """)) {
            ps.setString(1, type); ps.setObject(2, deviceId); ps.setInt(3, userId); ps.setString(4, details);
            ps.executeUpdate();
        }
    }

    private static String required(String value, int max, String message) throws RuleViolation {
        String cleaned = clean(value, max); if (cleaned.isBlank()) throw rule(400, "VALIDATION_ERROR", message); return cleaned;
    }
    private static String clean(String value, int max) throws RuleViolation {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > max) throw rule(400, "VALIDATION_ERROR", "A catalog field is too long.");
        return cleaned;
    }
    private static String nullable(String value, int max) throws RuleViolation {
        String cleaned = clean(value, max); return cleaned.isBlank() ? null : cleaned;
    }
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
    private static RuleViolation rule(int status, String code, String message) {
        return new RuleViolation(status, code, message);
    }

    static final class RuleViolation extends Exception {
        private final int status; private final String code; private final String safeMessage;
        RuleViolation(int status, String code, String message) { super(message); this.status=status; this.code=code; this.safeMessage=message; }
        int status() { return status; } String code() { return code; } String safeMessage() { return safeMessage; }
    }
    private record DepartmentRequest(Integer categoryId, String name, BigDecimal vatRatePercent, String description) { }
    private record VendorRequest(Integer vendorId, String name, String contactName, String phone, String email,
                                 String address, String notes, boolean active) { }
    private record CustomerTypeRequest(Integer customerTypeId, String name, String description, boolean active) { }
}
