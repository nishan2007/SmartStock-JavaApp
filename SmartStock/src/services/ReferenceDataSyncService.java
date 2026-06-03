package services;

import managers.SessionManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public final class ReferenceDataSyncService {
    private static final List<String> TABLES = List.of(
            "roles",
            "permissions",
            "role_permissions",
            "mobile_permissions",
            "role_mobile_permissions",
            "locations",
            "company_customization",
            "users",
            "user_locations",
            "devices",
            "categories",
            "vendors",
            "products",
            "product_barcodes",
            "inventory",
            "customer_accounts",
            "cash_drawers",
            "cash_drawer_device_assignments"
    );
    private static final List<String> CLOUD_PULL_ORDER = List.of(
            "roles",
            "permissions",
            "mobile_permissions",
            "role_permissions",
            "role_mobile_permissions",
            "locations",
            "company_customization",
            "customer_types",
            "users",
            "user_locations",
            "devices",
            "device_sessions",
            "categories",
            "vendors",
            "products",
            "product_barcodes",
            "inventory",
            "customer_accounts",
            "cash_drawers",
            "cash_drawer_device_assignments",
            "cash_drawer_sessions",
            "cash_drawer_handovers",
            "receiving_batches",
            "sales",
            "sale_items",
            "sale_returns",
            "sale_return_items",
            "sale_audit_log",
            "inventory_movements",
            "custom_order_items",
            "custom_order_item_barcodes",
            "custom_order_item_variants",
            "custom_order_print_materials",
            "custom_order_print_size_presets",
            "custom_order_design_placements",
            "custom_orders",
            "custom_order_lines",
            "custom_order_line_print_addons",
            "custom_order_payments",
            "custom_order_inventory_reservations",
            "custom_order_status_history",
            "custom_order_line_deliveries",
            "custom_order_line_production_history",
            "custom_order_line_returns",
            "custom_order_item_movements",
            "custom_order_audit_log",
            "customer_account_transactions",
            "customer_account_payment_allocations",
            "balance_sheet_submissions",
            "expense_categories",
            "expenses",
            "employee_time_clock",
            "held_carts",
            "held_cart_items",
            "store_transfers",
            "store_transfer_items",
            "maintenance_machines",
            "maintenance_parts",
            "maintenance_machine_parts",
            "maintenance_tickets",
            "wifi_sessions"
    );
    private static final List<String> LOCAL_PUSH_ORDER = List.of(
            "roles",
            "permissions",
            "mobile_permissions",
            "role_permissions",
            "role_mobile_permissions",
            "locations",
            "company_customization",
            "users",
            "user_locations",
            "cash_drawers",
            "cash_drawer_device_assignments",
            "customer_accounts",
            "cash_drawer_sessions",
            "cash_drawer_handovers",
            "products",
            "product_barcodes",
            "receiving_batches",
            "sales",
            "sale_items",
            "sale_returns",
            "sale_return_items",
            "sale_audit_log",
            "inventory",
            "inventory_movements",
            "custom_order_items",
            "custom_order_item_barcodes",
            "custom_order_item_variants",
            "custom_order_print_materials",
            "custom_order_print_size_presets",
            "custom_order_design_placements",
            "custom_orders",
            "custom_order_lines",
            "custom_order_line_print_addons",
            "custom_order_payments",
            "custom_order_inventory_reservations",
            "custom_order_status_history",
            "custom_order_line_deliveries",
            "custom_order_line_production_history",
            "custom_order_line_returns",
            "custom_order_item_movements",
            "custom_order_audit_log",
            "customer_account_transactions",
            "customer_account_payment_allocations",
            "balance_sheet_submissions",
            "expense_categories",
            "expenses",
            "employee_time_clock",
            "held_carts",
            "held_cart_items",
            "store_transfers",
            "store_transfer_items",
            "maintenance_machines",
            "maintenance_parts",
            "maintenance_machine_parts",
            "maintenance_tickets"
    );
    private static final Set<String> UPDATED_AT_SCHEMA_READY = ConcurrentHashMap.newKeySet();
    private static final Set<String> TOMBSTONE_SCHEMA_READY = ConcurrentHashMap.newKeySet();
    private static final Set<String> DEVICE_UPDATED_AT_SCHEMA_READY = ConcurrentHashMap.newKeySet();

    private ReferenceDataSyncService() {
    }

    public static int refreshFromCloud(Connection local, Connection cloud) throws SQLException {
        int copied = 0;
        truncateLocalTables(local);
        for (String table : TABLES) {
            if (!tableExists(local, table) || !tableExists(cloud, table)) {
                continue;
            }
            copied += replaceTable(local, cloud, table);
        }
        repairSequences(local);
        return copied;
    }

    public static int pullReferenceData(Connection local, Connection cloud) throws SQLException {
        ensureMissingCloudTables(local, cloud);
        ensureUpdatedAtSyncSchema(local);
        ensureUpdatedAtSyncSchema(cloud);
        ensureTombstoneSchema(local);
        ensureTombstoneSchema(cloud);
        int copied = 0;
        copied += pullTombstones(cloud, local);
        for (String table : TABLES) {
            if (!tableExists(local, table) || !tableExists(cloud, table)) {
                continue;
            }
            copied += upsertAll(cloud, local, table);
        }
        repairSequences(local);
        return copied;
    }

    public static int pullExistingLocationHistory(Connection local, Connection cloud, Integer locationId) throws SQLException {
        ensureMissingCloudTables(local, cloud);
        ensureUpdatedAtSyncSchema(local);
        ensureUpdatedAtSyncSchema(cloud);
        int copied = 0;
        for (String table : orderedCloudTables(cloud)) {
            copied += insertMissingAll(local, cloud, table);
        }
        repairSequences(local);
        return copied;
    }

    public static int pushLocalOperationalChanges(Connection local, Connection cloud) throws SQLException {
        ensureUpdatedAtSyncSchema(local);
        ensureUpdatedAtSyncSchema(cloud);
        ensureTombstoneSchema(local);
        ensureTombstoneSchema(cloud);
        int copied = 0;
        copied += pushTombstones(local, cloud);
        copied += pullTombstones(cloud, local);
        for (String table : LOCAL_PUSH_ORDER) {
            if (!tableExists(local, table) || !tableExists(cloud, table)) {
                continue;
            }
            if ("sales".equals(table)) {
                copied += pushSalesAndAlignIds(local, cloud);
            } else if ("custom_orders".equals(table)) {
                copied += pushCustomOrdersAndAlignIds(local, cloud);
            } else if ("products".equals(table)) {
                copied += pushProductsAndAlignIds(local, cloud);
            } else {
                copied += upsertAll(local, cloud, table);
            }
        }
        repairSequences(cloud);
        repairSequences(local);
        return copied;
    }

    public static void recordTombstone(Connection conn, String tableName, Map<String, ?> keyData) throws SQLException {
        if (conn == null || tableName == null || tableName.isBlank() || keyData == null || keyData.isEmpty()) {
            return;
        }
        ensureTombstoneSchema(conn);
        String sql = """
                INSERT INTO sync_tombstones (table_name, key_data, origin_device_id)
                VALUES (?, ?::jsonb, ?)
                ON CONFLICT (table_name, key_data)
                DO UPDATE SET
                    deleted_at = GREATEST(sync_tombstones.deleted_at, EXCLUDED.deleted_at),
                    origin_device_id = COALESCE(EXCLUDED.origin_device_id, sync_tombstones.origin_device_id)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, SyncJson.object(keyData));
            ps.setString(3, currentDeviceId());
            ps.executeUpdate();
        }
    }

    public static int syncDevicesByUpdatedAt(Connection local, Connection cloud) throws SQLException {
        if (!tableExists(local, "devices") || !tableExists(cloud, "devices")) {
            return 0;
        }
        ensureDeviceUpdatedAtSchema(local);
        ensureDeviceUpdatedAtSchema(cloud);
        int changed = 0;
        changed += syncNewerDeviceRows(local, cloud, "cloud");
        changed += syncNewerDeviceRows(cloud, local, "local");
        return changed;
    }

    public static int pushPendingDeviceAccessChanges(Connection local, Connection cloud) throws SQLException {
        if (!tableExists(local, "devices") || !tableExists(cloud, "devices") || !tableExists(local, "sync_outbox")) {
            return 0;
        }
        List<String> columns = commonDeviceAccessColumns(cloud, local);
        if (columns.isEmpty()) {
            return 0;
        }

        String selectSql = "SELECT DISTINCT ON (d.device_id) d.device_id::text AS device_id, "
                + selectExpressions("devices", "d", columns)
                + " FROM devices d"
                + " JOIN sync_outbox s ON s.payload->>'device_id' = d.device_id::text"
                + " WHERE s.event_type = 'DEVICE_ACCESS_UPDATED'"
                + "   AND s.status IN ('PENDING', 'FAILED')"
                + " ORDER BY d.device_id, s.created_at DESC";

        return updateDeviceAccessRows(local, cloud, columns, selectSql, "cloud", Set.of());
    }

    public static int pullCloudDeviceAccessChanges(Connection local, Connection cloud) throws SQLException {
        if (!tableExists(local, "devices") || !tableExists(cloud, "devices")) {
            return 0;
        }
        List<String> columns = commonDeviceAccessColumns(local, cloud);
        if (columns.isEmpty()) {
            return 0;
        }

        String selectSql = "SELECT d.device_id::text AS device_id, "
                + selectExpressions("devices", "d", columns)
                + " FROM devices d";

        return updateDeviceAccessRows(cloud, local, columns, selectSql, "local", pendingLocalDeviceAccessIds(local));
    }

    private static void ensureMissingCloudTables(Connection local, Connection cloud) throws SQLException {
        ensureMissingEnumTypes(local, cloud);
        for (String table : cloudTables(cloud)) {
            if (shouldSkipCloudPullTable(table) || tableExists(local, table)) {
                continue;
            }
            createLocalCopyTable(local, cloud, table);
        }
    }

    private static List<String> orderedCloudTables(Connection cloud) throws SQLException {
        List<String> available = cloudTables(cloud);
        List<String> ordered = new ArrayList<>();
        for (String table : CLOUD_PULL_ORDER) {
            if (available.contains(table) && !shouldSkipCloudPullTable(table)) {
                ordered.add(table);
            }
        }
        for (String table : available) {
            if (!ordered.contains(table) && !shouldSkipCloudPullTable(table)) {
                ordered.add(table);
            }
        }
        return ordered;
    }

    private static List<String> cloudTables(Connection cloud) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;
        try (PreparedStatement ps = cloud.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
        }
        return tables;
    }

    private static boolean shouldSkipCloudPullTable(String table) {
        return table.startsWith("sync_");
    }

    private static void ensureMissingEnumTypes(Connection local, Connection cloud) throws SQLException {
        String sql = """
                SELECT t.typname, array_agg(e.enumlabel ORDER BY e.enumsortorder) AS labels
                FROM pg_type t
                JOIN pg_namespace n ON n.oid = t.typnamespace
                JOIN pg_enum e ON e.enumtypid = t.oid
                WHERE n.nspname = 'public'
                GROUP BY t.typname
                ORDER BY t.typname
                """;
        try (PreparedStatement ps = cloud.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String typeName = rs.getString("typname");
                if (enumTypeExists(local, typeName)) {
                    continue;
                }
                String[] labels = (String[]) rs.getArray("labels").getArray();
                StringJoiner joiner = new StringJoiner(", ");
                for (String label : labels) {
                    joiner.add("'" + label.replace("'", "''") + "'");
                }
                try (Statement stmt = local.createStatement()) {
                    stmt.executeUpdate("CREATE TYPE " + quote(typeName) + " AS ENUM (" + joiner + ")");
                }
            }
        }
    }

    private static boolean enumTypeExists(Connection local, String typeName) throws SQLException {
        String sql = """
                SELECT 1
                FROM pg_type t
                JOIN pg_namespace n ON n.oid = t.typnamespace
                WHERE n.nspname = 'public'
                  AND t.typname = ?
                """;
        try (PreparedStatement ps = local.prepareStatement(sql)) {
            ps.setString(1, typeName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void createLocalCopyTable(Connection local, Connection cloud, String table) throws SQLException {
        List<String> definitions = new ArrayList<>();
        String columnsSql = """
                SELECT a.attname,
                       format_type(a.atttypid, a.atttypmod) AS column_type
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relname = ?
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                ORDER BY a.attnum
                """;
        try (PreparedStatement ps = cloud.prepareStatement(columnsSql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    definitions.add(quote(rs.getString("attname")) + " " + rs.getString("column_type"));
                }
            }
        }
        if (definitions.isEmpty()) {
            return;
        }
        List<String> primaryKeys = primaryKeyColumns(cloud, table);
        if (!primaryKeys.isEmpty()) {
            definitions.add("PRIMARY KEY (" + joinIdentifiers(primaryKeys) + ")");
        }
        try (Statement stmt = local.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + quote(table) + " (" + String.join(", ", definitions) + ")");
        }
    }

    private static List<String> primaryKeyColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = """
                SELECT a.attname
                FROM pg_index i
                JOIN pg_class c ON c.oid = i.indrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ANY(i.indkey)
                WHERE n.nspname = 'public'
                  AND c.relname = ?
                  AND i.indisprimary
                ORDER BY array_position(i.indkey::int[], a.attnum)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("attname"));
                }
            }
        }
        return columns;
    }

    private static void truncateLocalTables(Connection local) throws SQLException {
        StringJoiner joiner = new StringJoiner(", ");
        for (String table : TABLES) {
            if (tableExists(local, table)) {
                joiner.add(quote(table));
            }
        }
        if (joiner.length() == 0) {
            return;
        }
        try (Statement stmt = local.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE " + joiner + " RESTART IDENTITY CASCADE");
        }
    }

    private static int replaceTable(Connection local, Connection cloud, String table) throws SQLException {
        if (usesNaturalKeyReferenceSync(table)) {
            return upsertAll(cloud, local, table);
        }
        List<String> columns = commonColumns(local, cloud, table);
        if (columns.isEmpty()) {
            return 0;
        }
        Map<String, String> localTypes = columnTypes(local, table);
        String columnSql = joinIdentifiers(columns);
        String placeholders = castPlaceholders(columns, localTypes);
        int count = 0;
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try (Statement delete = local.createStatement();
             PreparedStatement select = cloud.prepareStatement("SELECT " + columnSql + " FROM " + quote(table));
             PreparedStatement insert = local.prepareStatement("INSERT INTO " + quote(table) + " (" + columnSql + ") VALUES (" + placeholders + ")")) {
            try (ResultSet rs = select.executeQuery()) {
                int batch = 0;
                while (rs.next()) {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = rs.getObject(i + 1);
                        if (value == null) {
                            insert.setObject(i + 1, null);
                        } else {
                            insert.setString(i + 1, rs.getString(i + 1));
                        }
                    }
                    insert.addBatch();
                    batch++;
                    count++;
                    if (batch >= 500) {
                        insert.executeBatch();
                        batch = 0;
                    }
                }
                if (batch > 0) {
                    insert.executeBatch();
                }
            }
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static int insertMissingByLocation(Connection local, Connection cloud, String table, Integer locationId) throws SQLException {
        if (!tableExists(local, table) || !tableExists(cloud, table) || !columns(cloud, table).contains("location_id")) {
            return 0;
        }
        String where = locationId == null ? "" : " WHERE location_id = ?";
        return insertMissing(local, cloud, table, "SELECT %s FROM " + quote(table) + where, locationId);
    }

    private static int insertMissingJoinedToLocation(Connection local, Connection cloud, String table, String parentTable,
                                                    String joinColumn, Integer locationId) throws SQLException {
        if (!tableExists(local, table) || !tableExists(cloud, table) || !tableExists(local, parentTable) || !tableExists(cloud, parentTable)) {
            return 0;
        }
        String where = locationId == null ? "" : " WHERE parent.location_id = ?";
        return insertMissing(local, cloud, table,
                "SELECT %s FROM " + quote(table) + " child JOIN " + quote(parentTable)
                        + " parent ON parent." + quote(joinColumn) + " = child." + quote(joinColumn) + where,
                locationId,
                "child");
    }

    private static int insertMissingAll(Connection local, Connection cloud, String table) throws SQLException {
        if (!tableExists(local, table) || !tableExists(cloud, table)) {
            return 0;
        }
        if (usesNaturalKeyReferenceSync(table)) {
            return upsertAll(cloud, local, table);
        }
        return insertMissing(local, cloud, table, "SELECT %s FROM " + quote(table), null);
    }

    private static boolean usesNaturalKeyReferenceSync(String table) {
        return "permissions".equals(table)
                || "mobile_permissions".equals(table)
                || "role_permissions".equals(table)
                || "role_mobile_permissions".equals(table);
    }

    private static int pushSalesAndAlignIds(Connection local, Connection cloud) throws SQLException {
        List<String> columns = commonColumns(cloud, local, "sales");
        if (!columns.contains("sale_id")) {
            return upsertAll(local, cloud, "sales");
        }
        int changed = 0;
        String selectSql = "SELECT " + selectExpressions("sales", null, columns) + " FROM sales ORDER BY sale_id";
        try (PreparedStatement select = local.prepareStatement(selectSql);
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                int localSaleId = rs.getInt(columns.indexOf("sale_id") + 1);
                String receiptNumber = columns.contains("receipt_number")
                        ? rs.getString(columns.indexOf("receipt_number") + 1)
                        : null;
                Integer cloudSaleId = findCloudSaleIdById(cloud, localSaleId);
                if (cloudSaleId == null) {
                    cloudSaleId = findCloudSaleIdByReceipt(cloud, receiptNumber);
                }
                if (cloudSaleId == null) {
                    cloudSaleId = insertCloudSale(cloud, columns, rs);
                    changed++;
                } else {
                    updateCloudSale(cloud, columns, rs, cloudSaleId);
                    changed++;
                }
                if (cloudSaleId != localSaleId) {
                    remapLocalSaleId(local, localSaleId, cloudSaleId, receiptNumber);
                    recordIdMap(local, "sales", String.valueOf(localSaleId), String.valueOf(cloudSaleId));
                    SyncAuditService.record(local,
                            "SALE_ID_REMAP",
                            "sales",
                            localSaleId,
                            cloudSaleId,
                            cloudSaleId,
                            receiptNumber,
                            "APPLIED",
                            Map.of(
                                    "receipt_number", receiptNumber == null ? "" : receiptNumber,
                                    "reason", "Cloud sale id differed from local sale id; local sale references were remapped before child tables synced."
                            ));
                }
            }
        }
        return changed;
    }

    private static Integer findCloudSaleIdById(Connection cloud, int saleId) throws SQLException {
        try (PreparedStatement ps = cloud.prepareStatement("SELECT sale_id FROM sales WHERE sale_id = ?")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("sale_id") : null;
            }
        }
    }

    private static Integer findCloudSaleIdByReceipt(Connection cloud, String receiptNumber) throws SQLException {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            return null;
        }
        try (PreparedStatement ps = cloud.prepareStatement("SELECT sale_id FROM sales WHERE receipt_number = ? ORDER BY sale_id LIMIT 1")) {
            ps.setString(1, receiptNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("sale_id") : null;
            }
        }
    }

    private static int insertCloudSale(Connection cloud, List<String> columns, ResultSet rs) throws SQLException {
        List<String> insertColumns = new ArrayList<>();
        for (String column : columns) {
            if (!"sale_id".equals(column)) {
                insertColumns.add(column);
            }
        }
        Map<String, String> cloudTypes = columnTypes(cloud, "sales");
        List<String> notNullable = notNullableColumns(cloud, "sales");
        String sql = "INSERT INTO sales (" + joinIdentifiers(insertColumns) + ") VALUES ("
                + castPlaceholders(insertColumns, cloudTypes) + ") RETURNING sale_id";
        try (PreparedStatement ps = cloud.prepareStatement(sql)) {
            bindRowValues(ps, insertColumns, columns, rs, cloudTypes, notNullable);
            try (ResultSet inserted = ps.executeQuery()) {
                if (inserted.next()) {
                    return inserted.getInt("sale_id");
                }
            }
        }
        throw new SQLException("Cloud sale insert did not return sale_id.");
    }

    private static void updateCloudSale(Connection cloud, List<String> columns, ResultSet rs, int cloudSaleId) throws SQLException {
        List<String> updateColumns = new ArrayList<>();
        for (String column : columns) {
            if (!"sale_id".equals(column)) {
                updateColumns.add(column);
            }
        }
        if (updateColumns.isEmpty()) {
            return;
        }
        Map<String, String> cloudTypes = columnTypes(cloud, "sales");
        List<String> notNullable = notNullableColumns(cloud, "sales");
        StringJoiner assignments = new StringJoiner(", ");
        for (String column : updateColumns) {
            String type = cloudTypes.get(column);
            assignments.add(quote(column) + " = " + (type == null || type.isBlank() ? "?" : "CAST(? AS " + type + ")"));
        }
        try (PreparedStatement ps = cloud.prepareStatement("UPDATE sales SET " + assignments + " WHERE sale_id = ?")) {
            bindRowValues(ps, updateColumns, columns, rs, cloudTypes, notNullable);
            ps.setInt(updateColumns.size() + 1, cloudSaleId);
            ps.executeUpdate();
        }
    }

    private static void bindRowValues(PreparedStatement ps, List<String> bindColumns, List<String> resultColumns,
                                      ResultSet rs, Map<String, String> targetTypes, List<String> notNullable) throws SQLException {
        for (int i = 0; i < bindColumns.size(); i++) {
            String column = bindColumns.get(i);
            Object value = rs.getObject(resultColumns.indexOf(column) + 1);
            if (value == null) {
                ps.setObject(i + 1, notNullable.contains(column) ? fallbackValue(targetTypes.get(column)) : null);
            } else {
                ps.setString(i + 1, rs.getString(resultColumns.indexOf(column) + 1));
            }
        }
    }

    private static void remapLocalSaleId(Connection local, int oldSaleId, int newSaleId, String receiptNumber) throws SQLException {
        if (oldSaleId == newSaleId) {
            return;
        }
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try {
            ensureSaleForeignKeysCascade(local);
            if (localSaleExists(local, newSaleId)) {
                updateLocalSaleReferences(local, oldSaleId, newSaleId);
                removeDuplicateLocalSale(local, oldSaleId, receiptNumber);
            } else {
                try (PreparedStatement ps = local.prepareStatement("UPDATE sales SET sale_id = ? WHERE sale_id = ?")) {
                    ps.setInt(1, newSaleId);
                    ps.setInt(2, oldSaleId);
                    ps.executeUpdate();
                }
                updateLocalSaleReferences(local, oldSaleId, newSaleId);
            }
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
    }

    private static void ensureSaleForeignKeysCascade(Connection local) throws SQLException {
        String sql = """
                SELECT con.conname,
                       rel.relname AS table_name,
                       att.attname AS column_name,
                       con.confdeltype
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = rel.relnamespace
                JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
                JOIN pg_class ref ON ref.oid = con.confrelid
                JOIN pg_attribute refatt ON refatt.attrelid = con.confrelid AND refatt.attnum = con.confkey[1]
                WHERE n.nspname = 'public'
                  AND con.contype = 'f'
                  AND ref.relname = 'sales'
                  AND refatt.attname = 'sale_id'
                  AND array_length(con.conkey, 1) = 1
                  AND array_length(con.confkey, 1) = 1
                  AND con.confupdtype <> 'c'
                """;
        try (PreparedStatement ps = local.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                String constraint = rs.getString("conname");
                String column = rs.getString("column_name");
                String deleteAction = deleteActionSql(rs.getString("confdeltype"));
                try (Statement stmt = local.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE " + quote(table) + " DROP CONSTRAINT " + quote(constraint));
                    stmt.executeUpdate("ALTER TABLE " + quote(table) + " ADD CONSTRAINT " + quote(constraint)
                            + " FOREIGN KEY (" + quote(column) + ") REFERENCES sales(sale_id) ON UPDATE CASCADE " + deleteAction);
                }
            }
        }
    }

    private static String deleteActionSql(String action) {
        if ("c".equals(action)) {
            return "ON DELETE CASCADE";
        }
        if ("n".equals(action)) {
            return "ON DELETE SET NULL";
        }
        if ("d".equals(action)) {
            return "ON DELETE SET DEFAULT";
        }
        if ("r".equals(action)) {
            return "ON DELETE RESTRICT";
        }
        return "ON DELETE NO ACTION";
    }

    private static boolean localSaleExists(Connection local, int saleId) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("SELECT 1 FROM sales WHERE sale_id = ?")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void updateLocalSaleReferences(Connection local, int oldSaleId, int newSaleId) throws SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'sale_id'
                  AND table_name <> 'sales'
                """;
        try (PreparedStatement ps = local.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                try (PreparedStatement update = local.prepareStatement("UPDATE " + quote(table) + " SET sale_id = ? WHERE sale_id = ?")) {
                    update.setInt(1, newSaleId);
                    update.setInt(2, oldSaleId);
                    update.executeUpdate();
                }
            }
        }
    }

    private static void removeDuplicateLocalSale(Connection local, int oldSaleId, String receiptNumber) throws SQLException {
        if (receiptNumber != null && !receiptNumber.isBlank() && columns(local, "sales").contains("receipt_number")) {
            try (PreparedStatement ps = local.prepareStatement("UPDATE sales SET receipt_number = ? WHERE sale_id = ?")) {
                ps.setString(1, receiptNumber + "__LOCAL_REMAP_" + oldSaleId);
                ps.setInt(2, oldSaleId);
                ps.executeUpdate();
            }
        }
        boolean triggersDisabled = false;
        try (Statement stmt = local.createStatement()) {
            stmt.executeUpdate("ALTER TABLE sales DISABLE TRIGGER USER");
            triggersDisabled = true;
        } catch (SQLException ignored) {
            triggersDisabled = false;
        }
        try (PreparedStatement ps = local.prepareStatement("DELETE FROM sales WHERE sale_id = ?")) {
            ps.setInt(1, oldSaleId);
            ps.executeUpdate();
        } finally {
            if (triggersDisabled) {
                try (Statement stmt = local.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE sales ENABLE TRIGGER USER");
                }
            }
        }
    }

    private static void recordIdMap(Connection local, String tableName, String localId, String cloudId) throws SQLException {
        if (!tableExists(local, "sync_id_map")) {
            return;
        }
        try (PreparedStatement ps = local.prepareStatement("""
                INSERT INTO sync_id_map (table_name, local_id, cloud_id, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (table_name, local_id) DO UPDATE
                SET cloud_id = EXCLUDED.cloud_id,
                    updated_at = CURRENT_TIMESTAMP
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, localId);
            ps.setString(3, cloudId);
            ps.executeUpdate();
        }
    }

    private static int pushCustomOrdersAndAlignIds(Connection local, Connection cloud) throws SQLException {
        List<String> columns = commonColumns(cloud, local, "custom_orders");
        if (!columns.contains("custom_order_id")) {
            return upsertAll(local, cloud, "custom_orders");
        }
        int changed = 0;
        String selectSql = "SELECT " + selectExpressions("custom_orders", null, columns)
                + " FROM custom_orders ORDER BY custom_order_id";
        try (PreparedStatement select = local.prepareStatement(selectSql);
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                long localOrderId = rs.getLong(columns.indexOf("custom_order_id") + 1);
                String orderNumber = columns.contains("order_number")
                        ? rs.getString(columns.indexOf("order_number") + 1)
                        : null;
                Long cloudOrderId = findCloudCustomOrderIdById(cloud, localOrderId);
                if (cloudOrderId == null) {
                    cloudOrderId = findCloudCustomOrderIdByOrderNumber(cloud, orderNumber);
                }
                if (cloudOrderId == null) {
                    cloudOrderId = insertCloudCustomOrder(cloud, columns, rs);
                    changed++;
                } else {
                    if (updateCloudCustomOrder(cloud, columns, rs, cloudOrderId)) {
                        changed++;
                    }
                }
                if (cloudOrderId != localOrderId) {
                    remapLocalCustomOrderId(local, localOrderId, cloudOrderId, orderNumber);
                    recordIdMap(local, "custom_orders", String.valueOf(localOrderId), String.valueOf(cloudOrderId));
                    SyncAuditService.record(local,
                            "CUSTOM_ORDER_ID_REMAP",
                            "custom_orders",
                            localOrderId,
                            cloudOrderId,
                            cloudOrderId,
                            orderNumber,
                            "APPLIED",
                            Map.of(
                                    "order_number", orderNumber == null ? "" : orderNumber,
                                    "reason", "Cloud custom order id differed from local custom order id; local order references were remapped before child tables synced."
                            ));
                }
            }
        }
        return changed;
    }

    private static Long findCloudCustomOrderIdById(Connection cloud, long customOrderId) throws SQLException {
        try (PreparedStatement ps = cloud.prepareStatement("SELECT custom_order_id FROM custom_orders WHERE custom_order_id = ?")) {
            ps.setLong(1, customOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("custom_order_id") : null;
            }
        }
    }

    private static Long findCloudCustomOrderIdByOrderNumber(Connection cloud, String orderNumber) throws SQLException {
        if (orderNumber == null || orderNumber.isBlank() || !columns(cloud, "custom_orders").contains("order_number")) {
            return null;
        }
        try (PreparedStatement ps = cloud.prepareStatement("SELECT custom_order_id FROM custom_orders WHERE order_number = ? ORDER BY custom_order_id LIMIT 1")) {
            ps.setString(1, orderNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("custom_order_id") : null;
            }
        }
    }

    private static long insertCloudCustomOrder(Connection cloud, List<String> columns, ResultSet rs) throws SQLException {
        List<String> insertColumns = new ArrayList<>();
        for (String column : columns) {
            if (!"custom_order_id".equals(column)) {
                insertColumns.add(column);
            }
        }
        Map<String, String> cloudTypes = columnTypes(cloud, "custom_orders");
        List<String> notNullable = notNullableColumns(cloud, "custom_orders");
        String sql = "INSERT INTO custom_orders (" + joinIdentifiers(insertColumns) + ") VALUES ("
                + castPlaceholders(insertColumns, cloudTypes) + ") RETURNING custom_order_id";
        try (PreparedStatement ps = cloud.prepareStatement(sql)) {
            bindRowValues(ps, insertColumns, columns, rs, cloudTypes, notNullable);
            try (ResultSet inserted = ps.executeQuery()) {
                if (inserted.next()) {
                    return inserted.getLong("custom_order_id");
                }
            }
        }
        throw new SQLException("Cloud custom order insert did not return custom_order_id.");
    }

    private static boolean updateCloudCustomOrder(Connection cloud, List<String> columns, ResultSet rs, long cloudOrderId) throws SQLException {
        if (columns.contains("updated_at")) {
            Timestamp localUpdatedAt = rs.getTimestamp(columns.indexOf("updated_at") + 1);
            Timestamp cloudUpdatedAt = findCustomOrderUpdatedAt(cloud, cloudOrderId);
            if (localUpdatedAt != null && cloudUpdatedAt != null && !localUpdatedAt.after(cloudUpdatedAt)) {
                return false;
            }
        }
        List<String> updateColumns = new ArrayList<>();
        for (String column : columns) {
            if (!"custom_order_id".equals(column)) {
                updateColumns.add(column);
            }
        }
        if (updateColumns.isEmpty()) {
            return false;
        }
        Map<String, String> cloudTypes = columnTypes(cloud, "custom_orders");
        List<String> notNullable = notNullableColumns(cloud, "custom_orders");
        StringJoiner assignments = new StringJoiner(", ");
        for (String column : updateColumns) {
            String type = cloudTypes.get(column);
            assignments.add(quote(column) + " = " + (type == null || type.isBlank() ? "?" : "CAST(? AS " + type + ")"));
        }
        try (PreparedStatement ps = cloud.prepareStatement("UPDATE custom_orders SET " + assignments + " WHERE custom_order_id = ?")) {
            bindRowValues(ps, updateColumns, columns, rs, cloudTypes, notNullable);
            ps.setLong(updateColumns.size() + 1, cloudOrderId);
            return ps.executeUpdate() > 0;
        }
    }

    private static Timestamp findCustomOrderUpdatedAt(Connection conn, long customOrderId) throws SQLException {
        if (!columns(conn, "custom_orders").contains("updated_at")) {
            return null;
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT updated_at FROM custom_orders WHERE custom_order_id = ?")) {
            ps.setLong(1, customOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getTimestamp("updated_at") : null;
            }
        }
    }

    private static void remapLocalCustomOrderId(Connection local, long oldOrderId, long newOrderId, String orderNumber) throws SQLException {
        if (oldOrderId == newOrderId) {
            return;
        }
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try {
            ensureCustomOrderForeignKeysCascade(local);
            if (localCustomOrderExists(local, newOrderId)) {
                updateLocalCustomOrderReferences(local, oldOrderId, newOrderId);
                removeDuplicateLocalCustomOrder(local, oldOrderId, orderNumber);
            } else {
                try (PreparedStatement ps = local.prepareStatement("UPDATE custom_orders SET custom_order_id = ? WHERE custom_order_id = ?")) {
                    ps.setLong(1, newOrderId);
                    ps.setLong(2, oldOrderId);
                    ps.executeUpdate();
                }
                updateLocalCustomOrderReferences(local, oldOrderId, newOrderId);
            }
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
    }

    private static void ensureCustomOrderForeignKeysCascade(Connection local) throws SQLException {
        String sql = """
                SELECT con.conname,
                       rel.relname AS table_name,
                       att.attname AS column_name,
                       con.confdeltype
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = rel.relnamespace
                JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
                JOIN pg_class ref ON ref.oid = con.confrelid
                JOIN pg_attribute refatt ON refatt.attrelid = con.confrelid AND refatt.attnum = con.confkey[1]
                WHERE n.nspname = 'public'
                  AND con.contype = 'f'
                  AND ref.relname = 'custom_orders'
                  AND refatt.attname = 'custom_order_id'
                  AND array_length(con.conkey, 1) = 1
                  AND array_length(con.confkey, 1) = 1
                  AND con.confupdtype <> 'c'
                """;
        try (PreparedStatement ps = local.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                String constraint = rs.getString("conname");
                String column = rs.getString("column_name");
                String deleteAction = deleteActionSql(rs.getString("confdeltype"));
                try (Statement stmt = local.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE " + quote(table) + " DROP CONSTRAINT " + quote(constraint));
                    stmt.executeUpdate("ALTER TABLE " + quote(table) + " ADD CONSTRAINT " + quote(constraint)
                            + " FOREIGN KEY (" + quote(column) + ") REFERENCES custom_orders(custom_order_id) ON UPDATE CASCADE " + deleteAction);
                }
            }
        }
    }

    private static boolean localCustomOrderExists(Connection local, long customOrderId) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("SELECT 1 FROM custom_orders WHERE custom_order_id = ?")) {
            ps.setLong(1, customOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void updateLocalCustomOrderReferences(Connection local, long oldOrderId, long newOrderId) throws SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'custom_order_id'
                  AND table_name <> 'custom_orders'
                """;
        try (PreparedStatement ps = local.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                try (PreparedStatement update = local.prepareStatement("UPDATE " + quote(table) + " SET custom_order_id = ? WHERE custom_order_id = ?")) {
                    update.setLong(1, newOrderId);
                    update.setLong(2, oldOrderId);
                    update.executeUpdate();
                }
            }
        }
    }

    private static void removeDuplicateLocalCustomOrder(Connection local, long oldOrderId, String orderNumber) throws SQLException {
        if (orderNumber != null && !orderNumber.isBlank() && columns(local, "custom_orders").contains("order_number")) {
            try (PreparedStatement ps = local.prepareStatement("UPDATE custom_orders SET order_number = ? WHERE custom_order_id = ?")) {
                ps.setString(1, orderNumber + "__LOCAL_REMAP_" + oldOrderId);
                ps.setLong(2, oldOrderId);
                ps.executeUpdate();
            }
        }
        boolean triggersDisabled = false;
        try (Statement stmt = local.createStatement()) {
            stmt.executeUpdate("ALTER TABLE custom_orders DISABLE TRIGGER USER");
            triggersDisabled = true;
        } catch (SQLException ignored) {
            triggersDisabled = false;
        }
        try (PreparedStatement ps = local.prepareStatement("DELETE FROM custom_orders WHERE custom_order_id = ?")) {
            ps.setLong(1, oldOrderId);
            ps.executeUpdate();
        } finally {
            if (triggersDisabled) {
                try (Statement stmt = local.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE custom_orders ENABLE TRIGGER USER");
                }
            }
        }
    }

    private static int pushProductsAndAlignIds(Connection local, Connection cloud) throws SQLException {
        List<String> columns = commonColumns(cloud, local, "products");
        if (!columns.contains("product_id")) {
            return upsertAll(local, cloud, "products");
        }
        int changed = 0;
        String selectSql = "SELECT " + selectExpressions("products", null, columns) + " FROM products ORDER BY product_id";
        try (PreparedStatement select = local.prepareStatement(selectSql);
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                int localProductId = rs.getInt(columns.indexOf("product_id") + 1);
                String sku = columns.contains("sku") ? rs.getString(columns.indexOf("sku") + 1) : null;
                String barcode = columns.contains("barcode") ? rs.getString(columns.indexOf("barcode") + 1) : null;
                Integer cloudProductId = findCloudProductIdById(cloud, localProductId);
                if (cloudProductId == null) {
                    cloudProductId = findCloudProductIdBySkuOrBarcode(cloud, sku, barcode);
                }
                if (cloudProductId == null) {
                    cloudProductId = insertCloudProduct(cloud, columns, rs);
                    changed++;
                } else {
                    if (updateCloudProduct(cloud, columns, rs, cloudProductId)) {
                        changed++;
                    }
                }
                if (cloudProductId != localProductId) {
                    remapLocalProductId(local, localProductId, cloudProductId, sku, barcode);
                    recordIdMap(local, "products", String.valueOf(localProductId), String.valueOf(cloudProductId));
                    SyncAuditService.record(local,
                            "PRODUCT_ID_REMAP",
                            "products",
                            localProductId,
                            cloudProductId,
                            cloudProductId,
                            firstNotBlank(sku, barcode),
                            "APPLIED",
                            Map.of(
                                    "sku", sku == null ? "" : sku,
                                    "barcode", barcode == null ? "" : barcode,
                                    "reason", "Cloud product id differed from local product id; local inventory and item references were remapped before child tables synced."
                            ));
                }
            }
        }
        return changed;
    }

    private static Integer findCloudProductIdById(Connection cloud, int productId) throws SQLException {
        try (PreparedStatement ps = cloud.prepareStatement("SELECT product_id FROM products WHERE product_id = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("product_id") : null;
            }
        }
    }

    private static Integer findCloudProductIdBySkuOrBarcode(Connection cloud, String sku, String barcode) throws SQLException {
        boolean hasSku = sku != null && !sku.isBlank() && columns(cloud, "products").contains("sku");
        boolean hasBarcode = barcode != null && !barcode.isBlank() && columns(cloud, "products").contains("barcode");
        if (!hasSku && !hasBarcode) {
            return null;
        }
        StringBuilder sql = new StringBuilder("SELECT product_id FROM products WHERE ");
        if (hasSku && hasBarcode) {
            sql.append("sku = ? OR barcode = ?");
        } else if (hasSku) {
            sql.append("sku = ?");
        } else {
            sql.append("barcode = ?");
        }
        sql.append(" ORDER BY product_id LIMIT 1");
        try (PreparedStatement ps = cloud.prepareStatement(sql.toString())) {
            int index = 1;
            if (hasSku) {
                ps.setString(index++, sku);
            }
            if (hasBarcode) {
                ps.setString(index, barcode);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("product_id") : null;
            }
        }
    }

    private static int insertCloudProduct(Connection cloud, List<String> columns, ResultSet rs) throws SQLException {
        List<String> insertColumns = new ArrayList<>();
        for (String column : columns) {
            if (!"product_id".equals(column)) {
                insertColumns.add(column);
            }
        }
        Map<String, String> cloudTypes = columnTypes(cloud, "products");
        List<String> notNullable = notNullableColumns(cloud, "products");
        String sql = "INSERT INTO products (" + joinIdentifiers(insertColumns) + ") VALUES ("
                + castPlaceholders(insertColumns, cloudTypes) + ") RETURNING product_id";
        try (PreparedStatement ps = cloud.prepareStatement(sql)) {
            bindRowValues(ps, insertColumns, columns, rs, cloudTypes, notNullable);
            try (ResultSet inserted = ps.executeQuery()) {
                if (inserted.next()) {
                    return inserted.getInt("product_id");
                }
            }
        }
        throw new SQLException("Cloud product insert did not return product_id.");
    }

    private static boolean updateCloudProduct(Connection cloud, List<String> columns, ResultSet rs, int cloudProductId) throws SQLException {
        if (columns.contains("updated_at")) {
            Timestamp localUpdatedAt = rs.getTimestamp(columns.indexOf("updated_at") + 1);
            Timestamp cloudUpdatedAt = findProductUpdatedAt(cloud, cloudProductId);
            if (localUpdatedAt != null && cloudUpdatedAt != null && !localUpdatedAt.after(cloudUpdatedAt)) {
                return false;
            }
        }
        List<String> updateColumns = new ArrayList<>();
        for (String column : columns) {
            if (!"product_id".equals(column)) {
                updateColumns.add(column);
            }
        }
        if (updateColumns.isEmpty()) {
            return false;
        }
        Map<String, String> cloudTypes = columnTypes(cloud, "products");
        List<String> notNullable = notNullableColumns(cloud, "products");
        StringJoiner assignments = new StringJoiner(", ");
        for (String column : updateColumns) {
            String type = cloudTypes.get(column);
            assignments.add(quote(column) + " = " + (type == null || type.isBlank() ? "?" : "CAST(? AS " + type + ")"));
        }
        try (PreparedStatement ps = cloud.prepareStatement("UPDATE products SET " + assignments + " WHERE product_id = ?")) {
            bindRowValues(ps, updateColumns, columns, rs, cloudTypes, notNullable);
            ps.setInt(updateColumns.size() + 1, cloudProductId);
            return ps.executeUpdate() > 0;
        }
    }

    private static Timestamp findProductUpdatedAt(Connection conn, int productId) throws SQLException {
        if (!columns(conn, "products").contains("updated_at")) {
            return null;
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT updated_at FROM products WHERE product_id = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getTimestamp("updated_at") : null;
            }
        }
    }

    private static void remapLocalProductId(Connection local, int oldProductId, int newProductId, String sku, String barcode) throws SQLException {
        if (oldProductId == newProductId) {
            return;
        }
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try {
            ensureProductForeignKeysCascade(local);
            if (localProductExists(local, newProductId)) {
                updateLocalProductReferences(local, oldProductId, newProductId);
                removeDuplicateLocalProduct(local, oldProductId, sku, barcode);
            } else {
                try (PreparedStatement ps = local.prepareStatement("UPDATE products SET product_id = ? WHERE product_id = ?")) {
                    ps.setInt(1, newProductId);
                    ps.setInt(2, oldProductId);
                    ps.executeUpdate();
                }
                updateLocalProductReferences(local, oldProductId, newProductId);
            }
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
    }

    private static void ensureProductForeignKeysCascade(Connection local) throws SQLException {
        String sql = """
                SELECT con.conname,
                       rel.relname AS table_name,
                       att.attname AS column_name,
                       con.confdeltype
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = rel.relnamespace
                JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
                JOIN pg_class ref ON ref.oid = con.confrelid
                JOIN pg_attribute refatt ON refatt.attrelid = con.confrelid AND refatt.attnum = con.confkey[1]
                WHERE n.nspname = 'public'
                  AND con.contype = 'f'
                  AND ref.relname = 'products'
                  AND refatt.attname = 'product_id'
                  AND array_length(con.conkey, 1) = 1
                  AND array_length(con.confkey, 1) = 1
                  AND con.confupdtype <> 'c'
                """;
        try (PreparedStatement ps = local.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                String constraint = rs.getString("conname");
                String column = rs.getString("column_name");
                String deleteAction = deleteActionSql(rs.getString("confdeltype"));
                try (Statement stmt = local.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE " + quote(table) + " DROP CONSTRAINT " + quote(constraint));
                    stmt.executeUpdate("ALTER TABLE " + quote(table) + " ADD CONSTRAINT " + quote(constraint)
                            + " FOREIGN KEY (" + quote(column) + ") REFERENCES products(product_id) ON UPDATE CASCADE " + deleteAction);
                }
            }
        }
    }

    private static boolean localProductExists(Connection local, int productId) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("SELECT 1 FROM products WHERE product_id = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void updateLocalProductReferences(Connection local, int oldProductId, int newProductId) throws SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'product_id'
                  AND table_name <> 'products'
                """;
        try (PreparedStatement ps = local.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                try (PreparedStatement update = local.prepareStatement("UPDATE " + quote(table) + " SET product_id = ? WHERE product_id = ?")) {
                    update.setInt(1, newProductId);
                    update.setInt(2, oldProductId);
                    update.executeUpdate();
                }
            }
        }
    }

    private static void removeDuplicateLocalProduct(Connection local, int oldProductId, String sku, String barcode) throws SQLException {
        List<String> productColumns = columns(local, "products");
        if (productColumns.contains("sku") || productColumns.contains("barcode")) {
            StringJoiner assignments = new StringJoiner(", ");
            if (productColumns.contains("sku")) {
                assignments.add("sku = ?");
            }
            if (productColumns.contains("barcode")) {
                assignments.add("barcode = ?");
            }
            try (PreparedStatement ps = local.prepareStatement("UPDATE products SET " + assignments + " WHERE product_id = ?")) {
                int index = 1;
                if (productColumns.contains("sku")) {
                    ps.setString(index++, (sku == null || sku.isBlank() ? "SKU" : sku) + "__LOCAL_REMAP_" + oldProductId);
                }
                if (productColumns.contains("barcode")) {
                    ps.setString(index++, (barcode == null || barcode.isBlank() ? "BARCODE" : barcode) + "__LOCAL_REMAP_" + oldProductId);
                }
                ps.setInt(index, oldProductId);
                ps.executeUpdate();
            }
        }
        boolean triggersDisabled = false;
        try (Statement stmt = local.createStatement()) {
            stmt.executeUpdate("ALTER TABLE products DISABLE TRIGGER USER");
            triggersDisabled = true;
        } catch (SQLException ignored) {
            triggersDisabled = false;
        }
        try (PreparedStatement ps = local.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            ps.setInt(1, oldProductId);
            ps.executeUpdate();
        } finally {
            if (triggersDisabled) {
                try (Statement stmt = local.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE products ENABLE TRIGGER USER");
                }
            }
        }
    }

    private static int upsertAll(Connection source, Connection target, String table) throws SQLException {
        if ("permissions".equals(table)) {
            return upsertPermissionsByKey(source, target);
        }
        if ("mobile_permissions".equals(table)) {
            return upsertMobilePermissionsByKey(source, target);
        }
        if ("role_permissions".equals(table)) {
            return upsertRolePermissionsByNaturalKey(source, target);
        }
        if ("role_mobile_permissions".equals(table)) {
            return upsertRoleMobilePermissionsByNaturalKey(source, target);
        }
        List<String> columns = commonColumns(target, source, table);
        if (columns.isEmpty()) {
            return 0;
        }
        List<String> primaryKeys = primaryKeyColumns(target, table);
        List<String> conflictKeys = conflictKeys(target, table, primaryKeys, columns);
        Map<String, String> targetTypes = columnTypes(target, table);
        List<String> notNullable = notNullableColumns(target, table);
        String columnSql = joinIdentifiers(columns);
        String placeholders = castPlaceholders(columns, targetTypes);
        String conflictSql = conflictSql(table, conflictKeys, primaryKeys, columns);
        int count = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement("SELECT " + selectExpressions(table, null, columns) + " FROM " + quote(table));
             PreparedStatement insert = target.prepareStatement("INSERT INTO " + quote(table) + " (" + columnSql + ") VALUES (" + placeholders + ") " + conflictSql)) {
            try (ResultSet rs = select.executeQuery()) {
                int batch = 0;
                while (rs.next()) {
                    if (shouldSkipPushRow(target, table, columns, rs)) {
                        continue;
                    }
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = rs.getObject(i + 1);
                        String column = columns.get(i);
                        if (value == null) {
                            insert.setObject(i + 1, notNullable.contains(column) ? fallbackValue(targetTypes.get(column)) : null);
                        } else {
                            insert.setString(i + 1, rs.getString(i + 1));
                        }
                    }
                    insert.addBatch();
                    batch++;
                    if (batch >= 500) {
                        count += sumBatch(insert.executeBatch());
                        batch = 0;
                    }
                }
                if (batch > 0) {
                    count += sumBatch(insert.executeBatch());
                }
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static int upsertPermissionsByKey(Connection source, Connection target) throws SQLException {
        if (!hasUniqueConflictTarget(target, "permissions", List.of("permission_key"))) {
            return upsertAllByPrimaryKey(source, target, "permissions");
        }
        List<String> columns = commonColumns(target, source, "permissions");
        columns.remove("permission_id");
        if (!columns.contains("permission_key")) {
            return 0;
        }
        Map<String, String> targetTypes = columnTypes(target, "permissions");
        List<String> notNullable = notNullableColumns(target, "permissions");
        String columnSql = joinIdentifiers(columns);
        String placeholders = castPlaceholders(columns, targetTypes);
        String conflictSql = conflictSql("permissions", List.of("permission_key"),
                primaryKeyColumns(target, "permissions"), columns);
        int count = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement("SELECT " + selectExpressions("permissions", null, columns) + " FROM permissions");
             PreparedStatement insert = target.prepareStatement("INSERT INTO permissions (" + columnSql + ") VALUES (" + placeholders + ") " + conflictSql)) {
            try (ResultSet rs = select.executeQuery()) {
                int batch = 0;
                while (rs.next()) {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = rs.getObject(i + 1);
                        String column = columns.get(i);
                        if (value == null) {
                            insert.setObject(i + 1, notNullable.contains(column) ? fallbackValue(targetTypes.get(column)) : null);
                        } else {
                            insert.setString(i + 1, rs.getString(i + 1));
                        }
                    }
                    insert.addBatch();
                    batch++;
                    if (batch >= 500) {
                        count += sumBatch(insert.executeBatch());
                        batch = 0;
                    }
                }
                if (batch > 0) {
                    count += sumBatch(insert.executeBatch());
                }
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static int upsertMobilePermissionsByKey(Connection source, Connection target) throws SQLException {
        List<String> targetColumns = columns(target, "mobile_permissions");
        List<String> sourceColumns = columns(source, "mobile_permissions");
        if (!targetColumns.contains("permission_key") || !sourceColumns.contains("permission_key")) {
            return 0;
        }
        List<String> insertColumns = new ArrayList<>();
        for (String column : targetColumns) {
            if ("permission_key".equals(column) || sourceColumns.contains(column)) {
                insertColumns.add(column);
            } else if ("display_name".equals(column)) {
                insertColumns.add(column);
            }
        }
        if (!insertColumns.contains("permission_key")) {
            return 0;
        }

        Map<String, String> targetTypes = columnTypes(target, "mobile_permissions");
        List<String> notNullable = notNullableColumns(target, "mobile_permissions");
        String columnSql = joinIdentifiers(insertColumns);
        String placeholders = castPlaceholders(insertColumns, targetTypes);
        String conflictSql = conflictSql("mobile_permissions", List.of("permission_key"),
                primaryKeyColumns(target, "mobile_permissions"), insertColumns);
        String selectSql = "SELECT " + mobilePermissionSelectExpressions(sourceColumns, insertColumns) + " FROM mobile_permissions";
        int count = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement(selectSql);
             PreparedStatement insert = target.prepareStatement("INSERT INTO mobile_permissions (" + columnSql + ") VALUES (" + placeholders + ") " + conflictSql)) {
            try (ResultSet rs = select.executeQuery()) {
                int batch = 0;
                while (rs.next()) {
                    for (int i = 0; i < insertColumns.size(); i++) {
                        Object value = rs.getObject(i + 1);
                        String column = insertColumns.get(i);
                        if (value == null) {
                            insert.setObject(i + 1, notNullable.contains(column) ? fallbackValue(targetTypes.get(column)) : null);
                        } else {
                            insert.setString(i + 1, rs.getString(i + 1));
                        }
                    }
                    insert.addBatch();
                    batch++;
                    if (batch >= 500) {
                        count += sumBatch(insert.executeBatch());
                        batch = 0;
                    }
                }
                if (batch > 0) {
                    count += sumBatch(insert.executeBatch());
                }
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static String mobilePermissionSelectExpressions(List<String> sourceColumns, List<String> insertColumns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String column : insertColumns) {
            if ("display_name".equals(column) && !sourceColumns.contains("display_name")) {
                String labelExpression = sourceColumns.contains("permission_name")
                        ? "COALESCE(NULLIF(TRIM(permission_name), ''), initcap(replace(permission_key, '_', ' ')))"
                        : "initcap(replace(permission_key, '_', ' '))";
                joiner.add(labelExpression + " AS display_name");
            } else if (isTimestampFallbackColumn("mobile_permissions", column)) {
                joiner.add("COALESCE(" + quote(column) + ", CURRENT_TIMESTAMP) AS " + quote(column));
            } else {
                joiner.add(quote(column));
            }
        }
        return joiner.toString();
    }

    private static int upsertAllByPrimaryKey(Connection source, Connection target, String table) throws SQLException {
        List<String> columns = commonColumns(target, source, table);
        if (columns.isEmpty()) {
            return 0;
        }
        List<String> primaryKeys = primaryKeyColumns(target, table);
        Map<String, String> targetTypes = columnTypes(target, table);
        List<String> notNullable = notNullableColumns(target, table);
        String columnSql = joinIdentifiers(columns);
        String placeholders = castPlaceholders(columns, targetTypes);
        String conflictSql = conflictSql(table, primaryKeys, primaryKeys, columns);
        int count = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement("SELECT " + selectExpressions(table, null, columns) + " FROM " + quote(table));
             PreparedStatement insert = target.prepareStatement("INSERT INTO " + quote(table) + " (" + columnSql + ") VALUES (" + placeholders + ") " + conflictSql)) {
            try (ResultSet rs = select.executeQuery()) {
                int batch = 0;
                while (rs.next()) {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = rs.getObject(i + 1);
                        String column = columns.get(i);
                        if (value == null) {
                            insert.setObject(i + 1, notNullable.contains(column) ? fallbackValue(targetTypes.get(column)) : null);
                        } else {
                            insert.setString(i + 1, rs.getString(i + 1));
                        }
                    }
                    insert.addBatch();
                    batch++;
                    if (batch >= 500) {
                        count += sumBatch(insert.executeBatch());
                        batch = 0;
                    }
                }
                if (batch > 0) {
                    count += sumBatch(insert.executeBatch());
                }
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static int upsertRolePermissionsByNaturalKey(Connection source, Connection target) throws SQLException {
        if (!tableExists(source, "roles") || !tableExists(source, "permissions")
                || !tableExists(target, "roles") || !tableExists(target, "permissions")) {
            return 0;
        }
        String selectSql = """
                SELECT r.role_name, p.permission_key, COALESCE(rp.updated_at, CURRENT_TIMESTAMP) AS updated_at
                FROM role_permissions rp
                JOIN roles r ON r.role_id = rp.role_id
                JOIN permissions p ON p.permission_id = rp.permission_id
                """;
        String insertSql = """
                INSERT INTO role_permissions (role_id, permission_id, updated_at)
                SELECT r.role_id, p.permission_id, CAST(? AS timestamptz)
                FROM roles r
                JOIN permissions p ON UPPER(p.permission_key) = UPPER(?)
                WHERE UPPER(r.role_name) = UPPER(?)
                ON CONFLICT (role_id, permission_id)
                DO UPDATE SET updated_at = EXCLUDED.updated_at
                WHERE EXCLUDED.updated_at > role_permissions.updated_at
                """;
        int count = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql);
             ResultSet rs = select.executeQuery()) {
            int batch = 0;
            while (rs.next()) {
                insert.setString(1, rs.getString("updated_at"));
                insert.setString(2, rs.getString("permission_key"));
                insert.setString(3, rs.getString("role_name"));
                insert.addBatch();
                batch++;
                if (batch >= 500) {
                    count += sumBatch(insert.executeBatch());
                    batch = 0;
                }
            }
            if (batch > 0) {
                count += sumBatch(insert.executeBatch());
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static int upsertRoleMobilePermissionsByNaturalKey(Connection source, Connection target) throws SQLException {
        if (!tableExists(source, "roles") || !tableExists(source, "mobile_permissions")
                || !tableExists(target, "roles") || !tableExists(target, "mobile_permissions")) {
            return 0;
        }
        String selectSql = """
                SELECT r.role_name, rmp.permission_key, COALESCE(rmp.updated_at, CURRENT_TIMESTAMP) AS updated_at
                FROM role_mobile_permissions rmp
                JOIN roles r ON r.role_id = rmp.role_id
                JOIN mobile_permissions p ON p.permission_key = rmp.permission_key
                """;
        String insertSql = """
                INSERT INTO role_mobile_permissions (role_id, permission_key, updated_at)
                SELECT r.role_id, p.permission_key, CAST(? AS timestamptz)
                FROM roles r
                JOIN mobile_permissions p ON UPPER(p.permission_key) = UPPER(?)
                WHERE UPPER(r.role_name) = UPPER(?)
                ON CONFLICT (role_id, permission_key)
                DO UPDATE SET updated_at = EXCLUDED.updated_at
                WHERE EXCLUDED.updated_at > role_mobile_permissions.updated_at
                """;
        int count = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql);
             ResultSet rs = select.executeQuery()) {
            int batch = 0;
            while (rs.next()) {
                insert.setString(1, rs.getString("updated_at"));
                insert.setString(2, rs.getString("permission_key"));
                insert.setString(3, rs.getString("role_name"));
                insert.addBatch();
                batch++;
                if (batch >= 500) {
                    count += sumBatch(insert.executeBatch());
                    batch = 0;
                }
            }
            if (batch > 0) {
                count += sumBatch(insert.executeBatch());
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static int pushTombstones(Connection local, Connection cloud) throws SQLException {
        return syncTombstones(local, cloud);
    }

    private static int pullTombstones(Connection cloud, Connection local) throws SQLException {
        return syncTombstones(cloud, local);
    }

    private static int syncTombstones(Connection source, Connection target) throws SQLException {
        if (!tableExists(source, "sync_tombstones") || !tableExists(target, "sync_tombstones")) {
            return 0;
        }
        int changed = upsertAll(source, target, "sync_tombstones");
        changed += applyTombstones(source, target);
        return changed;
    }

    private static int applyTombstones(Connection source, Connection target) throws SQLException {
        String sql = """
                SELECT table_name, key_data::text AS key_data, deleted_at
                FROM sync_tombstones
                ORDER BY deleted_at, table_name
                """;
        int changed = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement ps = source.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                changed += applyTombstone(target, rs.getString("table_name"), rs.getString("key_data"), rs.getTimestamp("deleted_at"));
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return changed;
    }

    private static int applyTombstone(Connection conn, String tableName, String keyJson, Timestamp deletedAt) throws SQLException {
        if (tableName == null || keyJson == null || deletedAt == null || !tableExists(conn, tableName)) {
            return 0;
        }
        String sql = tombstoneDeleteSql(tableName);
        if (sql == null) {
            return 0;
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, keyJson);
            ps.setTimestamp(2, deletedAt);
            return ps.executeUpdate();
        }
    }

    private static String tombstoneDeleteSql(String tableName) {
        return switch (tableName) {
            case "role_permissions" -> """
                    WITH tombstone AS (SELECT ?::jsonb AS key_data, ?::timestamptz AS deleted_at)
                    DELETE FROM role_permissions r
                    USING tombstone
                    WHERE r.role_id = (tombstone.key_data->>'role_id')::integer
                      AND r.permission_id = (tombstone.key_data->>'permission_id')::integer
                      AND r.updated_at <= tombstone.deleted_at
                    """;
            case "role_mobile_permissions" -> """
                    WITH tombstone AS (SELECT ?::jsonb AS key_data, ?::timestamptz AS deleted_at)
                    DELETE FROM role_mobile_permissions r
                    USING tombstone
                    WHERE r.role_id = (tombstone.key_data->>'role_id')::integer
                      AND r.permission_key = tombstone.key_data->>'permission_key'
                      AND r.updated_at <= tombstone.deleted_at
                    """;
            case "user_locations" -> """
                    WITH tombstone AS (SELECT ?::jsonb AS key_data, ?::timestamptz AS deleted_at)
                    DELETE FROM user_locations r
                    USING tombstone
                    WHERE r.user_id = (tombstone.key_data->>'user_id')::integer
                      AND r.location_id = (tombstone.key_data->>'location_id')::integer
                      AND r.updated_at <= tombstone.deleted_at
                    """;
            case "product_barcodes" -> """
                    WITH tombstone AS (SELECT ?::jsonb AS key_data, ?::timestamptz AS deleted_at)
                    DELETE FROM product_barcodes r
                    USING tombstone
                    WHERE r.barcode = tombstone.key_data->>'barcode'
                      AND r.updated_at <= tombstone.deleted_at
                    """;
            default -> null;
        };
    }

    private static String conflictSql(String table, List<String> conflictKeys, List<String> primaryKeys, List<String> columns) {
        if (conflictKeys.isEmpty()) {
            return "ON CONFLICT DO NOTHING";
        }
        if ("sync_tombstones".equals(table) && columns.contains("deleted_at")) {
            return """
                    ON CONFLICT (table_name, key_data)
                    DO UPDATE SET
                        deleted_at = GREATEST(sync_tombstones.deleted_at, EXCLUDED.deleted_at),
                        origin_device_id = COALESCE(EXCLUDED.origin_device_id, sync_tombstones.origin_device_id)
                    """;
        }
        List<String> updateColumns = new ArrayList<>();
        for (String column : columns) {
            if (!conflictKeys.contains(column) && !primaryKeys.contains(column)) {
                updateColumns.add(column);
            }
        }
        if (updateColumns.isEmpty()) {
            return "ON CONFLICT (" + joinIdentifiers(conflictKeys) + ") DO NOTHING";
        }
        StringJoiner updates = new StringJoiner(", ");
        for (String column : updateColumns) {
            updates.add(quote(column) + " = EXCLUDED." + quote(column));
        }
        String sql = "ON CONFLICT (" + joinIdentifiers(conflictKeys) + ") DO UPDATE SET " + updates;
        if (syncByUpdatedAt(table, columns)) {
            sql += " WHERE EXCLUDED.updated_at > " + quote(table) + ".updated_at";
        }
        return sql;
    }

    private static boolean syncByUpdatedAt(String table, List<String> columns) {
        return columns.contains("updated_at");
    }

    private static boolean shouldSkipPushRow(Connection target, String table, List<String> columns, ResultSet rs) throws SQLException {
        if (!"sales".equals(table) || !columns.contains("receipt_number") || !columns.contains("sale_id")) {
            return false;
        }
        String receiptNumber = rs.getString(columns.indexOf("receipt_number") + 1);
        if (receiptNumber == null || receiptNumber.isBlank()) {
            return false;
        }
        int localSaleId = rs.getInt(columns.indexOf("sale_id") + 1);
        try (PreparedStatement ps = target.prepareStatement("SELECT sale_id FROM sales WHERE receipt_number = ? LIMIT 1")) {
            ps.setString(1, receiptNumber);
            try (ResultSet existing = ps.executeQuery()) {
                return existing.next() && existing.getInt("sale_id") != localSaleId;
            }
        }
    }

    private static List<String> conflictKeys(Connection target, String table, List<String> primaryKeys, List<String> columns) throws SQLException {
        if ("sync_tombstones".equals(table) && columns.contains("table_name") && columns.contains("key_data")) {
            return List.of("table_name", "key_data");
        }
        if ("company_customization".equals(table) && columns.contains("location_id")) {
            return List.of("location_id");
        }
        if ("role_permissions".equals(table) && columns.contains("role_id") && columns.contains("permission_id")) {
            return List.of("role_id", "permission_id");
        }
        if ("role_mobile_permissions".equals(table) && columns.contains("role_id") && columns.contains("permission_key")) {
            return List.of("role_id", "permission_key");
        }
        if ("user_locations".equals(table) && columns.contains("user_id") && columns.contains("location_id")) {
            return List.of("user_id", "location_id");
        }
        if ("inventory".equals(table) && columns.contains("product_id") && columns.contains("location_id")) {
            return List.of("product_id", "location_id");
        }
        if ("receiving_batches".equals(table) && columns.contains("receive_id")) {
            return List.of("receive_id");
        }
        if ("product_barcodes".equals(table)
                && columns.contains("barcode")
                && hasUniqueConflictTarget(target, table, List.of("barcode"))) {
            return List.of("barcode");
        }
        if ("custom_order_items".equals(table) && columns.contains("item_name")) {
            return List.of("item_name");
        }
        if ("custom_order_item_barcodes".equals(table) && columns.contains("barcode")) {
            return List.of("barcode");
        }
        if ("custom_order_item_variants".equals(table) && columns.contains("custom_item_id") && columns.contains("variant_name")) {
            return List.of("custom_item_id", "variant_name");
        }
        if ("custom_order_print_materials".equals(table) && columns.contains("material_name")) {
            return List.of("material_name");
        }
        if ("custom_order_print_size_presets".equals(table) && columns.contains("print_material_id") && columns.contains("preset_name")) {
            return List.of("print_material_id", "preset_name");
        }
        if ("custom_order_design_placements".equals(table) && columns.contains("placement_name")) {
            return List.of("placement_name");
        }
        return primaryKeys;
    }

    private static boolean hasUniqueConflictTarget(Connection conn, String table, List<String> conflictColumns) throws SQLException {
        if (conflictColumns == null || conflictColumns.isEmpty()) {
            return false;
        }
        String sql = """
                SELECT 1
                FROM pg_index i
                JOIN pg_class rel ON rel.oid = i.indrelid
                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                WHERE nsp.nspname = 'public'
                  AND rel.relname = ?
                  AND (i.indisunique OR i.indisprimary)
                  AND array_length(i.indkey::int[], 1) = ?
                  AND (
                      SELECT array_agg(att.attname::text ORDER BY ord.ordinality)
                      FROM unnest(i.indkey) WITH ORDINALITY AS ord(attnum, ordinality)
                      JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ord.attnum
                  ) = ?::text[]
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setInt(2, conflictColumns.size());
            ps.setArray(3, conn.createArrayOf("text", conflictColumns.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String firstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static int insertMissingCustomerAccountTransactions(Connection local, Connection cloud) throws SQLException {
        String table = "customer_account_transactions";
        if (!tableExists(local, table) || !tableExists(cloud, table)) {
            return 0;
        }
        List<String> localColumns = columns(local, table);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (localColumns.contains("sale_id")) {
            where.append(" AND (t.sale_id IS NULL OR EXISTS (SELECT 1 FROM sales s WHERE s.sale_id = t.sale_id)) ");
        }
        return insertMissing(local, cloud, table, "SELECT %s FROM " + quote(table) + " t" + where, null, "t");
    }

    private static int insertMissingCustomerAccountPaymentAllocations(Connection local, Connection cloud) throws SQLException {
        String table = "customer_account_payment_allocations";
        if (!tableExists(local, table) || !tableExists(cloud, table)) {
            return 0;
        }
        List<String> localColumns = columns(local, table);
        StringBuilder where = new StringBuilder("""
                 WHERE EXISTS (
                    SELECT 1
                    FROM customer_account_transactions t
                    WHERE t.transaction_id = a.payment_transaction_id
                 )
                """);
        if (localColumns.contains("sale_id")) {
            where.append(" AND (a.sale_id IS NULL OR EXISTS (SELECT 1 FROM sales s WHERE s.sale_id = a.sale_id)) ");
        }
        if (localColumns.contains("custom_order_id")) {
            where.append(" AND (a.custom_order_id IS NULL OR EXISTS (SELECT 1 FROM custom_orders co WHERE co.custom_order_id = a.custom_order_id)) ");
        }
        return insertMissing(local, cloud, table, "SELECT %s FROM " + quote(table) + " a" + where, null, "a");
    }

    private static void ensureDeviceUpdatedAtSchema(Connection conn) throws SQLException {
        String key = schemaCacheKey(conn, "devices-updated-at");
        if (DEVICE_UPDATED_AT_SCHEMA_READY.contains(key)) {
            return;
        }
        synchronized (DEVICE_UPDATED_AT_SCHEMA_READY) {
            if (DEVICE_UPDATED_AT_SCHEMA_READY.contains(key)) {
                return;
            }
            installDeviceUpdatedAtSchema(conn);
            DEVICE_UPDATED_AT_SCHEMA_READY.add(key);
        }
    }

    private static void installDeviceUpdatedAtSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE devices ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION set_devices_updated_at()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        IF TG_OP = 'INSERT' THEN
                            NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                        ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                            NEW.updated_at = CURRENT_TIMESTAMP;
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS devices_set_updated_at ON devices");
            stmt.executeUpdate("""
                    CREATE OR REPLACE TRIGGER devices_set_updated_at
                    BEFORE INSERT OR UPDATE ON devices
                    FOR EACH ROW
                    EXECUTE FUNCTION set_devices_updated_at()
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS devices_updated_at_idx ON devices(updated_at DESC)");
        }
    }

    private static void ensureUpdatedAtSyncSchema(Connection conn) throws SQLException {
        String key = schemaCacheKey(conn, "updated-at-sync");
        if (UPDATED_AT_SCHEMA_READY.contains(key)) {
            return;
        }
        synchronized (UPDATED_AT_SCHEMA_READY) {
            if (UPDATED_AT_SCHEMA_READY.contains(key)) {
                return;
            }
            installUpdatedAtSyncSchema(conn);
            UPDATED_AT_SCHEMA_READY.add(key);
        }
    }

    private static void installUpdatedAtSyncSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            CustomerAccountLedgerService.ensureSchema(conn);
            ensureUpdatedAtTableSchema(conn, stmt, "role_permissions");
            ensureUpdatedAtTableSchema(conn, stmt, "role_mobile_permissions");
            ensureUpdatedAtTableSchema(conn, stmt, "roles");
            ensureUpdatedAtTableSchema(conn, stmt, "locations");
            ensureUpdatedAtTableSchema(conn, stmt, "company_customization");
            ensureUpdatedAtTableSchema(conn, stmt, "users");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_by_user_id INTEGER");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_by_name TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_photo_url TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS date_of_birth DATE");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_secret_salt TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_secret_hash TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_generated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_print_count INTEGER NOT NULL DEFAULT 0");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS users_badge_normalized_idx ON users(UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')))");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_company_name TEXT NOT NULL DEFAULT 'SmartStock'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_logo_url TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_quote TEXT NOT NULL DEFAULT '\"Sales goes up and down, Service is Forever\"'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_signatory_name TEXT NOT NULL DEFAULT 'Authorized Signature'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_signatory_title TEXT NOT NULL DEFAULT 'Management'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_back_instructions TEXT NOT NULL DEFAULT 'Scan or swipe this badge for SmartStock access.'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_quote BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_employee_id BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_issue_date BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_barcode BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_badge_text BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_track1 TEXT NOT NULL DEFAULT '{badge_id}'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_track2 TEXT NOT NULL DEFAULT '{badge_id}'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_track3 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_command TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_layout_data TEXT NOT NULL DEFAULT ''");
            ensureUpdatedAtTableSchema(conn, stmt, "user_locations");
            ensureUpdatedAtTableSchema(conn, stmt, "inventory");
            ensureUpdatedAtTableSchema(conn, stmt, "customer_accounts");
            CustomerAccountLedgerService.ensureSchema(conn);
            ensureUpdatedAtTableSchema(conn, stmt, "cash_drawers");
            ensureUpdatedAtTableSchema(conn, stmt, "cash_drawer_device_assignments");
            if (tableExists(conn, "products")) {
                stmt.executeUpdate("ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
                stmt.executeUpdate("""
                        CREATE OR REPLACE FUNCTION set_products_updated_at()
                        RETURNS TRIGGER AS $$
                        BEGIN
                            IF TG_OP = 'INSERT' THEN
                                NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                            ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                                NEW.updated_at = CURRENT_TIMESTAMP;
                            END IF;
                            RETURN NEW;
                        END;
                        $$ LANGUAGE plpgsql
                        """);
                stmt.executeUpdate("DROP TRIGGER IF EXISTS products_set_updated_at ON products");
                stmt.executeUpdate("""
                        CREATE OR REPLACE TRIGGER products_set_updated_at
                        BEFORE INSERT OR UPDATE ON products
                        FOR EACH ROW
                        EXECUTE FUNCTION set_products_updated_at()
                        """);
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS products_updated_at_idx ON products(updated_at DESC)");
            }
            if (tableExists(conn, "product_barcodes")) {
                stmt.executeUpdate("ALTER TABLE product_barcodes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
                stmt.executeUpdate("""
                        DO $$
                        BEGIN
                            IF EXISTS (
                                SELECT 1
                                FROM information_schema.columns
                                WHERE table_schema = 'public'
                                  AND table_name = 'product_barcodes'
                                  AND column_name = 'barcode_id'
                            ) AND NOT EXISTS (
                                SELECT 1
                                FROM information_schema.columns
                                WHERE table_schema = 'public'
                                  AND table_name = 'product_barcodes'
                                  AND column_name = 'product_barcode_id'
                            ) THEN
                                ALTER TABLE product_barcodes RENAME COLUMN barcode_id TO product_barcode_id;
                            END IF;
                        END
                        $$
                        """);
                stmt.executeUpdate("""
                        DELETE FROM product_barcodes older
                        USING product_barcodes newer
                        WHERE older.barcode = newer.barcode
                          AND older.product_barcode_id < newer.product_barcode_id
                        """);
                stmt.executeUpdate("""
                        DO $$
                        BEGIN
                            IF EXISTS (
                                SELECT 1
                                FROM pg_constraint con
                                JOIN pg_class rel ON rel.oid = con.conrelid
                                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                                WHERE nsp.nspname = 'public'
                                  AND rel.relname = 'product_barcodes'
                                  AND con.contype = 'u'
                                  AND pg_get_constraintdef(con.oid) = 'UNIQUE (barcode)'
                            ) THEN
                                DROP INDEX IF EXISTS product_barcodes_barcode_uidx;
                            ELSIF NOT EXISTS (
                                SELECT 1
                                FROM pg_index i
                                JOIN pg_class rel ON rel.oid = i.indrelid
                                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                                JOIN pg_class idx ON idx.oid = i.indexrelid
                                WHERE nsp.nspname = 'public'
                                  AND rel.relname = 'product_barcodes'
                                  AND i.indisunique
                                  AND pg_get_indexdef(idx.oid) ILIKE '%(barcode)%'
                            ) THEN
                                CREATE UNIQUE INDEX product_barcodes_barcode_uidx
                                ON product_barcodes(barcode);
                            END IF;
                        END
                        $$
                        """);
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_product_barcodes_product_id ON product_barcodes(product_id)");
                stmt.executeUpdate("""
                        CREATE OR REPLACE FUNCTION set_product_barcodes_updated_at()
                        RETURNS TRIGGER AS $$
                        BEGIN
                            IF TG_OP = 'INSERT' THEN
                                NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                            ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                                NEW.updated_at = CURRENT_TIMESTAMP;
                            END IF;
                            RETURN NEW;
                        END;
                        $$ LANGUAGE plpgsql
                        """);
                stmt.executeUpdate("DROP TRIGGER IF EXISTS product_barcodes_set_updated_at ON product_barcodes");
                stmt.executeUpdate("""
                        CREATE OR REPLACE TRIGGER product_barcodes_set_updated_at
                        BEFORE INSERT OR UPDATE ON product_barcodes
                        FOR EACH ROW
                        EXECUTE FUNCTION set_product_barcodes_updated_at()
                        """);
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS product_barcodes_updated_at_idx ON product_barcodes(updated_at DESC)");
            }
            ensureUpdatedAtTableSchema(conn, stmt, "employee_time_clock");
            ensureUpdatedAtTableSchema(conn, stmt, "held_carts");
            ensureUpdatedAtTableSchema(conn, stmt, "held_cart_items");
            ensureUpdatedAtTableSchema(conn, stmt, "custom_order_items");
            ensureUpdatedAtTableSchema(conn, stmt, "custom_order_item_variants");
            ensureUpdatedAtTableSchema(conn, stmt, "custom_order_print_materials");
            ensureUpdatedAtTableSchema(conn, stmt, "custom_order_print_size_presets");
            ensureUpdatedAtTableSchema(conn, stmt, "custom_order_design_placements");
            ensureUpdatedAtTableSchema(conn, stmt, "custom_orders");
        }
    }

    private static void ensureTombstoneSchema(Connection conn) throws SQLException {
        String key = schemaCacheKey(conn, "tombstones");
        if (TOMBSTONE_SCHEMA_READY.contains(key)) {
            return;
        }
        synchronized (TOMBSTONE_SCHEMA_READY) {
            if (TOMBSTONE_SCHEMA_READY.contains(key)) {
                return;
            }
            installTombstoneSchema(conn);
            TOMBSTONE_SCHEMA_READY.add(key);
        }
    }

    private static void installTombstoneSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_tombstones (
                        tombstone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        table_name TEXT NOT NULL,
                        key_data JSONB NOT NULL DEFAULT '{}'::jsonb,
                        deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        origin_device_id TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(table_name, key_data)
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_tombstones_deleted_idx ON sync_tombstones(deleted_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_tombstones_table_idx ON sync_tombstones(table_name)");
        }
    }

    private static String schemaCacheKey(Connection conn, String schemaName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        return schemaName + "|" + metaData.getURL() + "|" + metaData.getUserName();
    }

    private static void ensureUpdatedAtTableSchema(Connection conn, Statement stmt, String table) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        String functionName = "set_" + table + "_updated_at";
        String triggerName = table + "_set_updated_at";
        String indexName = table + "_updated_at_idx";
        stmt.executeUpdate("ALTER TABLE " + quote(table) + " ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
        stmt.executeUpdate("""
                CREATE OR REPLACE FUNCTION %s()
                RETURNS TRIGGER AS $$
                BEGIN
                    IF TG_OP = 'INSERT' THEN
                        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                        NEW.updated_at = CURRENT_TIMESTAMP;
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(quote(functionName)));
        stmt.executeUpdate("DROP TRIGGER IF EXISTS " + quote(triggerName) + " ON " + quote(table));
        stmt.executeUpdate("""
                CREATE OR REPLACE TRIGGER %s
                BEFORE INSERT OR UPDATE ON %s
                FOR EACH ROW
                EXECUTE FUNCTION %s()
                """.formatted(quote(triggerName), quote(table), quote(functionName)));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS " + quote(indexName) + " ON " + quote(table) + "(updated_at DESC)");
    }

    private static int syncNewerDeviceRows(Connection source, Connection target, String targetLabel) throws SQLException {
        List<String> columns = commonColumns(target, source, "devices");
        if (!columns.contains("device_id") || !columns.contains("updated_at")) {
            return 0;
        }
        Map<String, String> targetTypes = columnTypes(target, "devices");
        List<String> notNullable = notNullableColumns(target, "devices");
        String selectSql = "SELECT " + selectExpressions("devices", null, columns) + " FROM devices";
        String insertSql = "INSERT INTO devices (" + joinIdentifiers(columns) + ") VALUES ("
                + castPlaceholders(columns, targetTypes) + ")";
        List<String> updateColumns = new ArrayList<>(columns);
        updateColumns.remove("device_id");
        StringJoiner assignments = new StringJoiner(", ");
        for (String column : updateColumns) {
            String type = targetTypes.get(column);
            assignments.add(quote(column) + " = " + (type == null || type.isBlank() ? "?" : "CAST(? AS " + type + ")"));
        }
        String updateSql = "UPDATE devices SET " + assignments + " WHERE device_id = ?::uuid";

        int changed = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql);
             PreparedStatement update = target.prepareStatement(updateSql);
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String deviceId = rs.getString(columns.indexOf("device_id") + 1);
                Timestamp sourceUpdatedAt = rs.getTimestamp(columns.indexOf("updated_at") + 1);
                Timestamp targetUpdatedAt = findDeviceUpdatedAt(target, deviceId);
                if (targetUpdatedAt == null) {
                    bindResultColumns(insert, columns, columns, rs, targetTypes, notNullable, 1);
                    changed += insert.executeUpdate();
                } else if (sourceUpdatedAt != null && sourceUpdatedAt.after(targetUpdatedAt)) {
                    bindResultColumns(update, updateColumns, columns, rs, targetTypes, notNullable, 1);
                    update.setString(updateColumns.size() + 1, deviceId);
                    changed += update.executeUpdate();
                }
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw new SQLException("Failed to sync devices to " + targetLabel + ": " + ex.getMessage(), ex);
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return changed;
    }

    private static Timestamp findDeviceUpdatedAt(Connection conn, String deviceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT updated_at FROM devices WHERE device_id = ?::uuid")) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getTimestamp("updated_at") : null;
            }
        }
    }

    private static void bindResultColumns(PreparedStatement ps, List<String> bindColumns, List<String> resultColumns,
                                          ResultSet rs, Map<String, String> targetTypes, List<String> notNullable,
                                          int startIndex) throws SQLException {
        for (int i = 0; i < bindColumns.size(); i++) {
            String column = bindColumns.get(i);
            Object value = rs.getObject(resultColumns.indexOf(column) + 1);
            if (value == null) {
                ps.setObject(startIndex + i, notNullable.contains(column) ? fallbackValue(targetTypes.get(column)) : null);
            } else {
                ps.setString(startIndex + i, rs.getString(resultColumns.indexOf(column) + 1));
            }
        }
    }

    private static List<String> commonDeviceAccessColumns(Connection target, Connection source) throws SQLException {
        List<String> targetColumns = columns(target, "devices");
        List<String> sourceColumns = columns(source, "devices");
        List<String> desired = List.of(
                "is_approved",
                "is_blocked",
                "allow_sales",
                "allow_orders",
                "approved_at",
                "approved_by_user_id",
                "blocked_at",
                "blocked_by_user_id",
                "status_notes",
                "receipt_device_code",
                "last_store_id"
        );
        List<String> common = new ArrayList<>();
        for (String column : desired) {
            if (targetColumns.contains(column) && sourceColumns.contains(column)) {
                common.add(column);
            }
        }
        return common;
    }

    private static Set<String> pendingLocalDeviceAccessIds(Connection local) throws SQLException {
        Set<String> deviceIds = new HashSet<>();
        if (!tableExists(local, "sync_outbox")) {
            return deviceIds;
        }
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT DISTINCT payload->>'device_id' AS device_id
                FROM sync_outbox
                WHERE event_type = 'DEVICE_ACCESS_UPDATED'
                  AND status IN ('PENDING', 'FAILED')
                  AND NULLIF(payload->>'device_id', '') IS NOT NULL
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                deviceIds.add(rs.getString("device_id"));
            }
        }
        return deviceIds;
    }

    private static int updateDeviceAccessRows(Connection source, Connection target, List<String> columns,
                                              String selectSql, String targetLabel, Set<String> skipDeviceIds) throws SQLException {
        Map<String, String> targetTypes = columnTypes(target, "devices");
        List<String> notNullable = notNullableColumns(target, "devices");
        StringJoiner assignments = new StringJoiner(", ");
        for (String column : columns) {
            String type = targetTypes.get(column);
            assignments.add(quote(column) + " = " + (type == null || type.isBlank() ? "?" : "CAST(? AS " + type + ")"));
        }
        if (assignments.length() == 0) {
            return 0;
        }

        int changed = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (PreparedStatement select = source.prepareStatement(selectSql);
             PreparedStatement update = target.prepareStatement("UPDATE devices SET " + assignments + " WHERE device_id = ?::uuid")) {
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String deviceId = rs.getString("device_id");
                    if (skipDeviceIds.contains(deviceId)) {
                        continue;
                    }
                    for (int i = 0; i < columns.size(); i++) {
                        String column = columns.get(i);
                        Object value = rs.getObject(i + 2);
                        if (value == null) {
                            update.setObject(i + 1, notNullable.contains(column) ? fallbackValue(targetTypes.get(column)) : null);
                        } else {
                            update.setString(i + 1, rs.getString(i + 2));
                        }
                    }
                    update.setString(columns.size() + 1, deviceId);
                    changed += update.executeUpdate();
                }
            }
            target.commit();
        } catch (SQLException ex) {
            target.rollback();
            throw new SQLException("Failed to sync device access changes to " + targetLabel + ": " + ex.getMessage(), ex);
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
        return changed;
    }

    private static int insertMissing(Connection local, Connection cloud, String table, String selectTemplate, Integer locationId) throws SQLException {
        return insertMissing(local, cloud, table, selectTemplate, locationId, null);
    }

    private static int insertMissing(Connection local, Connection cloud, String table, String selectTemplate, Integer locationId, String selectAlias) throws SQLException {
        if (usesNaturalKeyReferenceSync(table)) {
            return upsertAll(cloud, local, table);
        }
        List<String> columns = commonColumns(local, cloud, table);
        if (columns.isEmpty()) {
            return 0;
        }
        Map<String, String> localTypes = columnTypes(local, table);
        List<String> notNullable = notNullableColumns(local, table);
        String columnSql = joinIdentifiers(columns);
        String selectColumnSql = selectExpressions(table, selectAlias, columns);
        String selectSql = selectTemplate.formatted(selectColumnSql);
        String placeholders = castPlaceholders(columns, localTypes);
        int count = 0;
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try (PreparedStatement select = cloud.prepareStatement(selectSql);
             PreparedStatement insert = local.prepareStatement("INSERT INTO " + quote(table) + " (" + columnSql + ") VALUES (" + placeholders + ") ON CONFLICT DO NOTHING")) {
            if (locationId != null && selectSql.contains("?")) {
                select.setInt(1, locationId);
            }
            try (ResultSet rs = select.executeQuery()) {
                int batch = 0;
                while (rs.next()) {
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = rs.getObject(i + 1);
                        String column = columns.get(i);
                        if (value == null) {
                            Object fallback = notNullable.contains(column) ? fallbackValue(localTypes.get(column)) : null;
                            insert.setObject(i + 1, fallback);
                        } else {
                            insert.setString(i + 1, rs.getString(i + 1));
                        }
                    }
                    insert.addBatch();
                    batch++;
                    if (batch >= 500) {
                        count += sumBatch(insert.executeBatch());
                        batch = 0;
                    }
                }
                if (batch > 0) {
                    count += sumBatch(insert.executeBatch());
                }
            }
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
        return count;
    }

    private static Object fallbackValue(String type) {
        if (type == null) {
            return "";
        }
        String clean = type.toLowerCase();
        if (clean.contains("char") || clean.contains("text") || clean.contains("uuid")) {
            return "";
        }
        if (clean.contains("bool")) {
            return false;
        }
        if (clean.contains("timestamp") || clean.equals("date")) {
            return java.sql.Timestamp.from(java.time.Instant.now());
        }
        if (clean.contains("numeric") || clean.contains("decimal")) {
            return java.math.BigDecimal.ZERO;
        }
        if (clean.contains("int") || clean.contains("serial")) {
            return 0;
        }
        return "";
    }

    private static int sumBatch(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result > 0) {
                count += result;
            }
        }
        return count;
    }

    private static List<String> commonColumns(Connection local, Connection cloud, String table) throws SQLException {
        List<String> localColumns = columns(local, table);
        List<String> cloudColumns = columns(cloud, table);
        Set<String> generatedColumns = new HashSet<>(generatedAlwaysColumns(local, table));
        generatedColumns.addAll(generatedAlwaysColumns(cloud, table));
        List<String> common = new ArrayList<>();
        for (String column : cloudColumns) {
            if (generatedColumns.contains(column)) {
                continue;
            }
            if ("product_barcodes".equals(table)
                    && ("product_barcode_id".equals(column) || "barcode_id".equals(column))) {
                continue;
            }
            if (localColumns.contains(column)) {
                common.add(column);
            }
        }
        return common;
    }

    private static List<String> generatedAlwaysColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND (is_generated = 'ALWAYS' OR identity_generation = 'ALWAYS')
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }

    private static List<String> columns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + quote(table) + " WHERE 1=0");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnName(i));
            }
        }
        return columns;
    }

    private static Map<String, String> columnTypes(Connection conn, String table) throws SQLException {
        Map<String, String> types = new LinkedHashMap<>();
        String sql = """
                SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS column_type
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relname = ?
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                ORDER BY a.attnum
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    types.put(rs.getString("attname"), rs.getString("column_type"));
                }
            }
        }
        return types;
    }

    private static List<String> notNullableColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND is_nullable = 'NO'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }

    private static void repairSequences(Connection local) throws SQLException {
        String sql = """
                SELECT c.table_name, c.column_name, pg_get_serial_sequence(quote_ident(c.table_name), c.column_name) AS sequence_name
                FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND pg_get_serial_sequence(quote_ident(c.table_name), c.column_name) IS NOT NULL
                """;
        try (PreparedStatement ps = local.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    repairSequence(local, rs.getString("table_name"), rs.getString("column_name"), rs.getString("sequence_name"));
                }
            }
        }
    }

    private static void repairSequence(Connection local, String table, String column, String sequenceName) throws SQLException {
        String sql = "SELECT setval(?, COALESCE((SELECT MAX(" + quote(column) + ") FROM " + quote(table) + "), 1), "
                + "COALESCE((SELECT MAX(" + quote(column) + ") FROM " + quote(table) + "), 0) > 0)";
        try (PreparedStatement ps = local.prepareStatement(sql)) {
            ps.setString(1, sequenceName);
            ps.executeQuery();
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND table_type = 'BASE TABLE'
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(10);
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String joinIdentifiers(List<String> columns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String column : columns) {
            joiner.add(quote(column));
        }
        return joiner.toString();
    }

    private static String selectExpressions(String table, String alias, List<String> columns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String column : columns) {
            String qualified = alias == null ? quote(column) : alias + "." + quote(column);
            if (isTimestampFallbackColumn(table, column)) {
                joiner.add("COALESCE(" + qualified + ", CURRENT_TIMESTAMP) AS " + quote(column));
            } else {
                joiner.add(qualified);
            }
        }
        return joiner.toString();
    }

    private static boolean isTimestampFallbackColumn(String table, String column) {
        return "created_at".equals(column)
                || "updated_at".equals(column)
                || ("sales".equals(table) && "completed_at".equals(column));
    }

    private static String castPlaceholders(List<String> columns, Map<String, String> localTypes) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String column : columns) {
            String type = localTypes.get(column);
            if (type == null || type.isBlank()) {
                joiner.add("?");
            } else {
                joiner.add("CAST(? AS " + type + ")");
            }
        }
        return joiner.toString();
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String currentDeviceId() {
        try {
            String deviceId = SessionManager.getCurrentDeviceId();
            return deviceId == null || deviceId.isBlank() ? null : deviceId;
        } catch (Exception ignored) {
            return null;
        }
    }
}
