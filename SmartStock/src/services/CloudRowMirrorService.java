package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Materializes exact per-store rows in Supabase without exposing PostgreSQL
 * credentials or repeatedly downloading cloud tables during routine sync.
 */
public final class CloudRowMirrorService {
    private static final int BATCH_SIZE = 200;
    private static final Set<String> GLOBAL_TABLES = Set.of(
            "roles", "permissions", "role_permissions", "mobile_permissions",
            "role_mobile_permissions", "company_info", "locations", "users", "customer_types",
            "customer_accounts", "categories", "item_types", "item_brands", "vendors",
            "products", "product_barcodes", "custom_order_items",
            "custom_order_item_barcodes", "custom_order_item_variants", "custom_order_item_variant_barcodes",
            "custom_order_print_materials", "custom_order_print_size_presets",
            "custom_order_design_placements", "employee_payroll_settings",
            "employee_schedule_holidays", "time_clock_auto_close_settings",
            "image_assets", "image_asset_references", "maintenance_parts"
    );
    private static final Map<String, String> DEPENDENT_LOCATION_PREDICATES = Map.ofEntries(
            Map.entry("sale_items", "EXISTS (SELECT 1 FROM sales p WHERE p.sale_id=t.sale_id AND p.location_id=?)"),
            Map.entry("sale_return_items", "EXISTS (SELECT 1 FROM sale_returns p WHERE p.return_id=t.return_id AND p.location_id=?)"),
            Map.entry("custom_order_audit_log", customOrderPredicate()),
            Map.entry("custom_order_inventory_reservations", customOrderPredicate()),
            Map.entry("custom_order_line_deliveries", customOrderPredicate()),
            Map.entry("custom_order_line_production_history", customOrderPredicate()),
            Map.entry("custom_order_line_returns", customOrderPredicate()),
            Map.entry("custom_order_lines", customOrderPredicate()),
            Map.entry("custom_order_payments", customOrderPredicate()),
            Map.entry("custom_order_status_history", customOrderPredicate()),
            Map.entry("custom_order_line_print_addons", "EXISTS (SELECT 1 FROM custom_order_lines l JOIN custom_orders p ON p.custom_order_id=l.custom_order_id WHERE l.custom_order_line_id=t.custom_order_line_id AND p.location_id=?)"),
            Map.entry("customer_account_payment_allocations", "EXISTS (SELECT 1 FROM customer_account_transactions p WHERE p.transaction_id=t.payment_transaction_id AND p.location_id=?)"),
            Map.entry("device_sessions", "t.store_id=?"),
            Map.entry("register_transfers", "(t.source_location_id=? OR t.destination_location_id=?)"),
            Map.entry("devices", "(t.last_store_id=?"
                    + " OR EXISTS (SELECT 1 FROM device_sessions s WHERE s.device_id=t.device_id AND s.store_id=?)"
                    + " OR EXISTS (SELECT 1 FROM cash_drawer_device_assignments a WHERE a.device_id=t.device_id AND a.location_id=?)"
                    + " OR EXISTS (SELECT 1 FROM cash_drawer_handovers h WHERE h.device_id=t.device_id AND h.location_id=?)"
                    + " OR EXISTS (SELECT 1 FROM cash_drawer_sessions d WHERE d.device_id=t.device_id AND d.location_id=?)"
                    + " OR EXISTS (SELECT 1 FROM change_basket_updates c WHERE c.device_id=t.device_id AND c.location_id=?))"),
            Map.entry("email_outbox_events", "EXISTS (SELECT 1 FROM email_outbox p WHERE p.email_outbox_id=t.email_outbox_id AND p.location_id=?)"),
            Map.entry("employee_time_clock_adjustments", "EXISTS (SELECT 1 FROM employee_time_clock p WHERE p.clock_id=t.clock_id AND p.location_id=?)"),
            Map.entry("held_cart_items", "EXISTS (SELECT 1 FROM held_carts p WHERE p.held_cart_id=t.held_cart_id AND p.location_id=?)"),
            Map.entry("invoice_audit_log", invoicePredicate()),
            Map.entry("invoice_delivery_lines", invoicePredicate()),
            Map.entry("invoice_lines", invoicePredicate()),
            Map.entry("invoice_status_history", invoicePredicate()),
            Map.entry("quotation_audit_log", quotationPredicate()),
            Map.entry("quotation_lines", quotationPredicate()),
            Map.entry("quotation_status_history", quotationPredicate()),
            Map.entry("maintenance_machine_parts", "EXISTS (SELECT 1 FROM maintenance_machines p WHERE p.machine_id=t.machine_id AND p.location_id=?)"),
            Map.entry("maintenance_tickets", "EXISTS (SELECT 1 FROM maintenance_machines p WHERE p.machine_id=t.machine_id AND p.location_id=?)"),
            Map.entry("receiving_batches", "(t.location_id=? OR EXISTS (SELECT 1 FROM store_transfers x WHERE x.receive_id=t.receive_id AND (x.from_location_id=? OR x.to_location_id=?)))"),
            Map.entry("store_transfers", "(t.from_location_id=? OR t.to_location_id=?)"),
            Map.entry("store_transfer_items", "EXISTS (SELECT 1 FROM store_transfers p WHERE p.transfer_id=t.transfer_id AND (p.from_location_id=? OR p.to_location_id=?))"),
            Map.entry("cross_store_refund_requests",
                    "(t.source_location_id=? OR t.receiving_location_id=?)"),
            Map.entry("cross_store_refund_lines",
                    "EXISTS (SELECT 1 FROM cross_store_refund_requests p WHERE p.request_id=t.request_id AND (p.source_location_id=? OR p.receiving_location_id=?))"),
            Map.entry("cross_store_refund_reconciliation",
                    "(t.source_location_id=? OR t.receiving_location_id=?)"),
            Map.entry("security_audit_events",
                    "((t.device_id IS NOT NULL AND EXISTS (SELECT 1 FROM devices d WHERE d.device_id=t.device_id AND d.last_store_id=?)) OR (t.device_id IS NULL AND EXISTS (SELECT 1 FROM user_locations ul WHERE ul.user_id=t.actor_user_id AND ul.location_id=?)))")
    );

    private CloudRowMirrorService() {
    }

    public static MirrorResult synchronize(Connection local, int locationId) throws SQLException {
        if (locationId <= 0) throw new SQLException("A valid store location is required.");
        SyncSchemaInstaller.ensureSchema(local);
        discardAbandonedGenerations(locationId);
        int uploaded = 0;
        int unchanged = 0;
        int deleted = 0;
        int activeRows = 0;
        JsonObject tableCounts = new JsonObject();
        boolean reusableBaseline = hasReusableCloudBaseline(local, locationId);
        GenerationUpload generation = new GenerationUpload(locationId, UUID.randomUUID(),
                reusableBaseline);
        try {
            for (String table : ReferenceDataSyncService.cloudMirrorTablesForApi()) {
                if (!tableExists(local, table)) continue;
                TableResult result;
                result = mirrorTable(local, locationId, table, generation);
                if (!result.supported()) continue;
                uploaded += result.uploaded();
                unchanged += result.unchanged();
                deleted += result.deleted();
                activeRows += result.activeRows();
                tableCounts.addProperty(table, result.activeRows());
            }
            UUID matchingGeneration = matchingCompletionGeneration(
                    local, locationId, tableCounts, activeRows);
            if (uploaded > 0 || deleted > 0 || matchingGeneration == null) {
                generation.ensureStarted();
                Finalization finalization = finalizeMirror(local, locationId,
                        generation.generationId, tableCounts, activeRows);
                verifyMirror(locationId, finalization.generationId(), tableCounts, activeRows);
                synchronizeProtectedUserCredentials(
                        local, locationId, finalization.generationId());
                persistCompletedState(local, locationId, generation.pendingRows, tableCounts,
                        activeRows, finalization);
            } else {
                verifyMirror(locationId, matchingGeneration, tableCounts, activeRows);
                synchronizeProtectedUserCredentials(local, locationId, matchingGeneration);
            }
            return new MirrorResult(uploaded, unchanged, deleted, activeRows);
        } catch (SQLException ex) {
            generation.abandonQuietly(ex);
            throw ex;
        }
    }

    private static void discardAbandonedGenerations(int locationId) throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.addProperty("p_older_than_seconds", 900);
        try {
            SupabaseServerApi.Response response = SupabaseServerApi.postRpc(
                    "smartstock_discard_abandoned_store_mirrors", body);
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Supabase abandoned mirror cleanup", response));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Supabase abandoned mirror cleanup was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Supabase abandoned mirror cleanup failed.", ex);
        }
    }

    private static UUID matchingCompletionGeneration(Connection local, int locationId,
                                                      JsonObject tableCounts, int activeRows)
            throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT generation_id FROM sync_row_mirror_completion
                WHERE location_id=? AND generation_id IS NOT NULL
                  AND table_counts=?::jsonb AND active_row_count=?
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, tableCounts.toString());
            ps.setLong(3, activeRows);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    private static void verifyMirror(int locationId, UUID generationId,
                                     JsonObject tableCounts, int activeRows)
            throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.addProperty("p_generation_id", generationId.toString());
        body.add("p_table_counts", tableCounts);
        body.addProperty("p_active_row_count", activeRows);
        try {
            SupabaseServerApi.Response response = SupabaseServerApi.postRpc(
                    "smartstock_verify_store_mirror", body);
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Supabase mirror verification", response));
            }
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!result.has("verified") || !result.get("verified").getAsBoolean()
                    || !result.has("generation_id")
                    || !generationId.toString().equals(
                    result.get("generation_id").getAsString())) {
                throw new SQLException("Supabase did not verify the current recovery generation.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Supabase mirror verification was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Supabase mirror verification failed.", ex);
        }
    }

    private static void synchronizeProtectedUserCredentials(Connection local, int locationId,
                                                             UUID generationId)
            throws SQLException {
        JsonArray rows = new JsonArray();
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT user_id,password_hash,password_cache_invalidated_at,
                       employee_pin_salt,employee_pin_hash,employee_pin_updated_at,
                       badge_secret_salt,badge_secret_hash
                FROM users ORDER BY user_id
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject row = new JsonObject();
                row.addProperty("user_id", rs.getInt("user_id"));
                addNullable(row, "password_hash", rs.getString("password_hash"));
                addNullableInstant(row, "password_cache_invalidated_at",
                        rs.getTimestamp("password_cache_invalidated_at"));
                addNullable(row, "employee_pin_salt", rs.getString("employee_pin_salt"));
                addNullable(row, "employee_pin_hash", rs.getString("employee_pin_hash"));
                addNullableInstant(row, "employee_pin_updated_at",
                        rs.getTimestamp("employee_pin_updated_at"));
                addNullable(row, "badge_secret_salt", rs.getString("badge_secret_salt"));
                addNullable(row, "badge_secret_hash", rs.getString("badge_secret_hash"));
                rows.add(row);
            }
        }

        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.addProperty("p_generation_id", generationId.toString());
        body.add("p_rows", rows);
        try {
            SupabaseServerApi.Response response = SupabaseServerApi.postRpc(
                    "smartstock_upsert_store_user_credentials", body);
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Supabase protected credential synchronization", response));
            }
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!result.has("generation_id")
                    || !generationId.toString().equals(
                    result.get("generation_id").getAsString())
                    || !result.has("credential_rows")
                    || result.get("credential_rows").getAsInt() != rows.size()) {
                throw new SQLException(
                        "Supabase did not verify every protected credential row.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException(
                    "Supabase protected credential synchronization was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException(
                    "Supabase protected credential synchronization failed.", ex);
        }
    }

    private static void addNullable(JsonObject target, String name, String value) {
        if (value == null) target.add(name, com.google.gson.JsonNull.INSTANCE);
        else target.addProperty(name, value);
    }

    private static void addNullableInstant(JsonObject target, String name, Timestamp value) {
        if (value == null) target.add(name, com.google.gson.JsonNull.INSTANCE);
        else target.addProperty(name, value.toInstant().toString());
    }

    private static boolean hasReusableCloudBaseline(Connection local, int locationId)
            throws SQLException {
        UUID localGeneration = null;
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT generation_id
                FROM sync_row_mirror_completion WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) localGeneration = rs.getObject(1, UUID.class);
            }
        }
        if (localGeneration == null) return false;
        try {
            CloudSyncManifest cloud = CloudSyncManifest.fetchStoreSnapshot(locationId);
            return sameBaseline(localGeneration, cloud.snapshotGenerationId());
        } catch (IOException ex) {
            throw new SQLException("Supabase mirror baseline verification failed.", ex);
        }
    }

    static boolean sameBaseline(UUID localGeneration, String cloudGeneration) {
        return localGeneration != null && cloudGeneration != null
                && localGeneration.toString().equals(cloudGeneration);
    }

    private static TableResult mirrorTable(Connection local, int locationId, String table,
                                           GenerationUpload generation)
            throws SQLException {
        List<String> primaryKeys = primaryKeys(local, table);
        if (primaryKeys.isEmpty()) return new TableResult(0, 0, 0, 0, false);
        Map<String, String> existing = generation.cloneCurrent
                ? existingHashes(local, locationId, table)
                : new HashMap<>();
        List<MirrorRow> pending = new ArrayList<>();
        int unchanged = 0;
        int activeRows = 0;

        String scope = scopePredicate(table, columnExists(local, table, "location_id"));
        try (PreparedStatement ps = local.prepareStatement(
                "SELECT t.* FROM " + quote(table) + " t WHERE " + scope)) {
            bindLocation(ps, scope, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metadata = rs.getMetaData();
                while (rs.next()) {
                    activeRows++;
                    JsonObject key = new JsonObject();
                    for (String primaryKey : primaryKeys) {
                        String value = rs.getString(primaryKey);
                        if (value == null) {
                            throw new SQLException("Cloud mirror primary key is null: "
                                    + table + "." + primaryKey);
                        }
                        key.addProperty(primaryKey, value);
                    }
                    JsonObject data = new JsonObject();
                    String sourceUpdatedAt = null;
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        String column = metadata.getColumnName(index);
                        Object value = rs.getObject(index);
                        if ("updated_at".equals(column) && value != null) {
                            Timestamp timestamp = rs.getTimestamp(index);
                            if (timestamp != null) sourceUpdatedAt = timestamp.toInstant().toString();
                        }
                        if (sensitiveColumn(table, column)
                                || excludedOperationalColumn(table, column)) continue;
                        if (value == null) {
                            data.add(column, null);
                        } else if (value instanceof byte[] bytes) {
                            data.addProperty(column, "\\x" + hex(bytes));
                        } else {
                            data.addProperty(column, rs.getString(index));
                        }
                    }
                    String keyJson = key.toString();
                    String hash = sha256(data.toString());
                    String priorHash = existing.remove(keyJson);
                    if (hash.equals(priorHash)) {
                        unchanged++;
                    } else {
                        pending.add(new MirrorRow(table, keyJson, data, hash, false, sourceUpdatedAt));
                    }
                }
            }
        }

        for (String deletedKey : existing.keySet()) {
            pending.add(new MirrorRow(table, deletedKey, new JsonObject(),
                    sha256("deleted:" + deletedKey), true, null));
        }

        int uploaded = 0;
        int deleted = 0;
        for (int offset = 0; offset < pending.size(); offset += BATCH_SIZE) {
            List<MirrorRow> batch = pending.subList(offset,
                    Math.min(offset + BATCH_SIZE, pending.size()));
            generation.ensureStarted();
            uploadBatch(local, locationId, generation.generationId, batch);
            generation.pendingRows.addAll(batch);
            uploaded += batch.size();
            deleted += (int) batch.stream().filter(MirrorRow::deleted).count();
        }
        return new TableResult(uploaded, unchanged, deleted, activeRows, true);
    }

    private static Finalization finalizeMirror(Connection local, int locationId,
                                               UUID generationId, JsonObject tableCounts,
                                               int activeRows)
            throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.addProperty("p_generation_id", generationId.toString());
        body.add("p_table_counts", tableCounts);
        try {
            SupabaseServerApi.Response response =
                    SupabaseServerApi.postRpc("smartstock_finalize_store_mirror", body);
            CloudTransferMetrics.record(local, "finalize_store_mirror",
                    body.toString(), response.body());
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Supabase mirror completion", response));
            }
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!result.has("completed") || !result.get("completed").getAsBoolean()
                    || !result.has("active_row_count")
                    || result.get("active_row_count").getAsInt() != activeRows
                    || !result.has("generation_id")
                    || !generationId.toString().equals(result.get("generation_id").getAsString())
                    || !result.has("completed_at")) {
                throw new SQLException("Supabase did not confirm the complete store mirror.");
            }
            return new Finalization(generationId,
                    Instant.parse(result.get("completed_at").getAsString()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Supabase mirror completion was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Supabase mirror completion failed.", ex);
        }
    }

    private static void uploadBatch(Connection local, int locationId, UUID generationId,
                                    List<MirrorRow> batch) throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.addProperty("p_generation_id", generationId.toString());
        JsonArray rows = new JsonArray();
        for (MirrorRow row : batch) {
            JsonObject item = new JsonObject();
            item.addProperty("table_name", row.table());
            item.add("row_key", JsonParser.parseString(row.keyJson()));
            item.add("row_data", row.data());
            item.addProperty("row_hash", row.hash());
            item.addProperty("is_deleted", row.deleted());
            if (row.sourceUpdatedAt() != null) {
                item.addProperty("source_updated_at", row.sourceUpdatedAt());
            }
            rows.add(item);
        }
        body.add("p_rows", rows);
        try {
            SupabaseServerApi.Response response =
                    SupabaseServerApi.postRpc("smartstock_materialize_store_rows", body);
            CloudTransferMetrics.record(local, "materialize_store_rows",
                    body.toString(), response.body());
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Supabase row materialization", response));
            }
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!result.has("acknowledged")
                    || result.get("acknowledged").getAsInt() != batch.size()) {
                throw new SQLException("Supabase did not acknowledge the complete row batch.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Supabase row materialization was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Supabase row materialization failed.", ex);
        }
    }

    private static void persistCompletedState(Connection local, int locationId,
                                              List<MirrorRow> batch,
                                              JsonObject tableCounts, int activeRows,
                                              Finalization finalization) throws SQLException {
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try (PreparedStatement upsert = local.prepareStatement("""
                     INSERT INTO sync_row_mirror_state(
                         location_id,table_name,row_key,row_hash,materialized_at
                     )
                     VALUES (?,?,?,?,CURRENT_TIMESTAMP)
                     ON CONFLICT(location_id,table_name,row_key) DO UPDATE
                     SET row_hash=EXCLUDED.row_hash,materialized_at=CURRENT_TIMESTAMP
                     """);
             PreparedStatement delete = local.prepareStatement("""
                     DELETE FROM sync_row_mirror_state
                     WHERE location_id=? AND table_name=? AND row_key=?
                     """);
             PreparedStatement completion = local.prepareStatement("""
                     INSERT INTO sync_row_mirror_completion(
                         location_id,table_counts,active_row_count,generation_id,completed_at
                     ) VALUES(?,?::jsonb,?,?,?)
                     ON CONFLICT(location_id) DO UPDATE SET
                         table_counts=EXCLUDED.table_counts,
                         active_row_count=EXCLUDED.active_row_count,
                         generation_id=EXCLUDED.generation_id,
                         completed_at=EXCLUDED.completed_at
                     """)) {
            for (MirrorRow row : batch) {
                PreparedStatement statement = row.deleted() ? delete : upsert;
                statement.setInt(1, locationId);
                statement.setString(2, row.table());
                statement.setString(3, row.keyJson());
                if (!row.deleted()) statement.setString(4, row.hash());
                statement.addBatch();
            }
            upsert.executeBatch();
            delete.executeBatch();
            completion.setInt(1, locationId);
            completion.setString(2, tableCounts.toString());
            completion.setLong(3, activeRows);
            completion.setObject(4, finalization.generationId());
            completion.setTimestamp(5, Timestamp.from(finalization.completedAt()));
            completion.executeUpdate();
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
    }

    private static Map<String, String> existingHashes(Connection local, int locationId,
                                                       String table) throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement completion = local.prepareStatement("""
                SELECT generation_id IS NOT NULL AND pg_catalog.jsonb_exists(table_counts, ?)
                FROM sync_row_mirror_completion WHERE location_id=?
                """)) {
            completion.setString(1, table);
            completion.setInt(2, locationId);
            try (ResultSet rs = completion.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) return result;
            }
        }
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT row_key,row_hash FROM sync_row_mirror_state
                WHERE location_id=? AND table_name=?
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString(1), rs.getString(2));
            }
        }
        return result;
    }

    private static List<String> primaryKeys(Connection local, String table) throws SQLException {
        Map<Short, String> ordered = new LinkedHashMap<>();
        DatabaseMetaData metadata = local.getMetaData();
        try (ResultSet rs = metadata.getPrimaryKeys(null, "public", table)) {
            while (rs.next()) ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
        }
        return ordered.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList();
    }

    static boolean sensitiveColumn(String table, String column) {
        String clean = column.toLowerCase(Locale.ROOT);
        return clean.contains("password")
                || clean.equals("pin") || clean.startsWith("pin_") || clean.endsWith("_pin")
                || clean.contains("_pin_")
                || clean.contains("token")
                || clean.contains("credential")
                || clean.contains("secret");
    }

    /**
     * Omits device telemetry and derived counters from the durable business
     * snapshot. These fields change during normal polling/login activity and
     * are reconstructed by the server, so mirroring them would create cloud
     * egress without improving recovery.
     */
    static boolean excludedOperationalColumn(String table, String column) {
        if (!"devices".equals(table) || column == null) return false;
        return switch (column.toLowerCase(Locale.ROOT)) {
            case "last_seen", "updated_at", "session_count" -> true;
            default -> false;
        };
    }

    private static boolean tableExists(Connection local, String table) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT EXISTS(
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema='public' AND table_name=?
                )
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean columnExists(Connection local, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT EXISTS(
                  SELECT 1 FROM information_schema.columns
                  WHERE table_schema='public' AND table_name=? AND column_name=?
                )
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    static String scopePredicate(String table, boolean hasLocationColumn) throws SQLException {
        if (GLOBAL_TABLES.contains(table)) return "TRUE";
        String predicate = DEPENDENT_LOCATION_PREDICATES.get(table);
        if (predicate != null) return predicate;
        if (hasLocationColumn) return "t.location_id=?";
        throw new SQLException("Cloud mirror table has no location ownership rule: " + table);
    }

    static long countScopedRows(Connection local, int locationId, String table)
            throws SQLException {
        if (!tableExists(local, table)) return -1;
        String scope = scopePredicate(table, columnExists(local, table, "location_id"));
        try (PreparedStatement ps = local.prepareStatement(
                "SELECT COUNT(*) FROM " + quote(table) + " t WHERE " + scope)) {
            bindLocation(ps, scope, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private static void bindLocation(PreparedStatement ps, String predicate, int locationId)
            throws SQLException {
        int parameters = 0;
        for (int index = 0; index < predicate.length(); index++) {
            if (predicate.charAt(index) == '?') parameters++;
        }
        for (int index = 1; index <= parameters; index++) ps.setInt(index, locationId);
    }

    private static String customOrderPredicate() {
        return "EXISTS (SELECT 1 FROM custom_orders p WHERE p.custom_order_id=t.custom_order_id AND p.location_id=?)";
    }

    private static String invoicePredicate() {
        return "EXISTS (SELECT 1 FROM invoices p WHERE p.invoice_id=t.invoice_id AND p.location_id=?)";
    }

    private static String quotationPredicate() {
        return "EXISTS (SELECT 1 FROM quotations p WHERE p.quotation_id=t.quotation_id AND p.location_id=?)";
    }

    private static String quote(String identifier) throws SQLException {
        if (identifier == null || !identifier.matches("[a-z][a-z0-9_]{0,100}")) {
            throw new SQLException("Unsafe cloud mirror table name.");
        }
        return "\"" + identifier + "\"";
    }

    private static String sha256(String value) throws SQLException {
        try {
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new SQLException("SHA-256 is unavailable.", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    private record MirrorRow(String table, String keyJson, JsonObject data, String hash,
                             boolean deleted, String sourceUpdatedAt) {
    }

    private record Finalization(UUID generationId, Instant completedAt) {
    }

    private static final class GenerationUpload {
        private final int locationId;
        private final UUID generationId;
        private final boolean cloneCurrent;
        private final List<MirrorRow> pendingRows = new ArrayList<>();
        private boolean started;

        private GenerationUpload(int locationId, UUID generationId, boolean cloneCurrent) {
            this.locationId = locationId;
            this.generationId = generationId;
            this.cloneCurrent = cloneCurrent;
        }

        private void ensureStarted() throws SQLException {
            if (started) return;
            JsonObject body = new JsonObject();
            body.addProperty("p_location_id", locationId);
            body.addProperty("p_generation_id", generationId.toString());
            body.addProperty("p_clone_current", cloneCurrent);
            try {
                SupabaseServerApi.Response response =
                        SupabaseServerApi.postRpc("smartstock_begin_store_mirror", body);
                if (!response.successful()) {
                    throw new SQLException(SupabaseServerApi.failureMessage(
                            "Supabase mirror generation", response));
                }
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!result.has("generation_id")
                        || !generationId.toString().equals(
                        result.get("generation_id").getAsString())) {
                    throw new SQLException("Supabase did not start the requested mirror generation.");
                }
                started = true;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SQLException("Supabase mirror generation was interrupted.", ex);
            } catch (IOException | RuntimeException ex) {
                throw new SQLException("Supabase mirror generation could not start.", ex);
            }
        }

        private void abandonQuietly(SQLException primary) {
            if (!started) return;
            JsonObject body = new JsonObject();
            body.addProperty("p_location_id", locationId);
            body.addProperty("p_generation_id", generationId.toString());
            try {
                SupabaseServerApi.Response response = SupabaseServerApi.postRpc(
                        "smartstock_abandon_store_mirror", body);
                if (!response.successful()) {
                    primary.addSuppressed(new SQLException(SupabaseServerApi.failureMessage(
                            "Supabase mirror abandonment", response)));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                primary.addSuppressed(new SQLException(
                        "Supabase mirror abandonment was interrupted.", ex));
            } catch (IOException | RuntimeException ex) {
                primary.addSuppressed(new SQLException(
                        "Supabase mirror abandonment failed.", ex));
            }
        }
    }

    private record TableResult(int uploaded, int unchanged, int deleted, int activeRows,
                               boolean supported) {
    }

    public record MirrorResult(int uploaded, int unchanged, int deleted, int activeRows) {
    }
}
