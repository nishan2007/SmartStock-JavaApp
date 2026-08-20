package services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Installs fresh v1 candidates and validates live schemas without repairing them. */
public final class SchemaContractService {
    public static final int BASELINE_VERSION = 1;
    private static final String RESOURCE_FINGERPRINT_TOKEN =
            "__SMARTSTOCK_RESOURCE_FINGERPRINT__";
    private static final Set<String> PRE_RETURN_RECEIPT_LOCAL_FINGERPRINTS = Set.of(
            "61fab3e60b61c1dfc6aea5b8087c81e946b64ba415bdb1cc08d642677131be9f",
            "09e4ddc87f31f8add4d7550b7058cc087c086b6872aec3853233b6e4dfb47584");
    private static final String INTERIM_RETURN_RECEIPT_LOCAL_FINGERPRINT =
            "46b3ac2ec24b641a81394def39ac5ecb9bc7707a4eb748df67ef9e285d1cbc27";
    private static final String PRE_CUSTOMER_HISTORY_CACHE_LOCAL_FINGERPRINT =
            "d42e78efda7385ce1b2a1b31770ff8c9b20bc08bc8d9ca9e37dfecfdd1a006b8";
    private static final List<String> LOCAL_BASELINE = List.of(
            "database/v1/local/001_schema.sql",
            "database/v1/local/002_seed.sql",
            "database/v1/local/003_metadata.sql"
    );
    private static final List<String> CLOUD_BASELINE = List.of(
            "database/v1/cloud/001_schema.sql",
            "database/v1/cloud/002_storage.sql",
            "database/v1/cloud/003_metadata.sql"
    );
    private static final List<String> LOCAL_POST_V1 = List.of(
            "database/migrations/v1_after/20260811130000_separate_custom_order_credit.sql",
            "database/migrations/v1_after/20260811190000_add_register_transfers.sql",
            "database/migrations/v1_after/20260811220000_add_custom_variant_barcodes.sql",
            "database/migrations/v1_after/20260811230000_add_custom_items_to_quotations.sql",
            "database/migrations/v1_after/20260811233000_add_store_transfer_uuid.sql",
            "database/migrations/v1_after/20260811233200_add_cross_store_time_clock_identity.sql",
            "database/migrations/v1_after/20260818120000_mobile_item_web.sql",
            "database/migrations/v1_after/20260819120000_onedrive_image_provider.sql"
    );
    private static final List<String> CLOUD_POST_V1 = List.of(
            "database/migrations/v1_after/20260809190000_revoke_anon_security_definer_execute.sql",
            "database/migrations/v1_after/20260809192551_restrict_service_only_rpc_execute.sql",
            "database/migrations/v1_after/20260809211000_cloud_return_receipt_numbers.sql",
            "database/migrations/v1_after/20260811190000_add_register_transfers.sql",
            "database/migrations/v1_after/20260811190100_secure_cloud_register_transfers.sql",
            "database/migrations/v1_after/20260811233100_route_store_transfer_receipts.sql",
            "database/migrations/v1_after/20260819120000_onedrive_image_provider.sql",
            "database/migrations/v1_after/20260819230000_onedrive_shared_identifiers.sql",
            "database/migrations/v1_after/20260820030000_bound_store_snapshot_retention.sql"
    );
    private static final Set<String> VALIDATED_LOCAL_DATABASES =
            ConcurrentHashMap.newKeySet();

    private SchemaContractService() {
    }

    public static List<String> localBaselineResources() {
        return LOCAL_BASELINE;
    }

    public static List<String> cloudBaselineResources() {
        return CLOUD_BASELINE;
    }

    public static List<String> localContractResources() {
        List<String> resources=new ArrayList<>(LOCAL_BASELINE);resources.addAll(LOCAL_POST_V1);return List.copyOf(resources);
    }

    public static List<String> cloudPostV1MigrationResources() {
        return CLOUD_POST_V1;
    }

    public static List<String> cloudContractResources() {
        List<String> resources = new ArrayList<>(CLOUD_BASELINE);
        resources.addAll(CLOUD_POST_V1);
        return List.copyOf(resources);
    }

    public static void installLocalBaseline(Connection connection) throws Exception {
        installBaseline(connection, localContractResources(), "LOCAL", List.of("public"));
    }

    public static Readiness validateLocal(Connection connection) throws SQLException {
        return validate(connection, "LOCAL", localContractResources(), List.of("public"), false);
    }

    /** Safely advances an otherwise-valid local v1 schema to the mobile item web contract. */
    public static void ensureMobileItemWebUpgrade(Connection connection) throws SQLException {
        if (!tableExists(connection, "public", "smartstock_schema_metadata")
                || tableExists(connection, "public", "mobile_item_web_runtime")) return;
        String storedResource;
        String storedCatalog;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256
                FROM public.smartstock_schema_metadata
                WHERE schema_scope='LOCAL' AND baseline_version=?
                """)) {
            ps.setInt(1, BASELINE_VERSION);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                storedResource = rs.getString(1);
                storedCatalog = rs.getString(2);
            }
        }
        if (!catalogFingerprint(connection, List.of("public"), false).equals(storedCatalog))
            throw new SQLException("The local schema has drifted; automatic mobile item web installation is blocked.", "55000");
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            SqlScriptRunner.runSql(connection, SqlScriptRunner.readResource(
                    "database/migrations/v1_after/20260818120000_mobile_item_web.sql"));
            String resource = resourceFingerprint(localContractResources());
            String catalog = catalogFingerprint(connection, List.of("public"), false);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE public.smartstock_schema_metadata
                    SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=?
                    WHERE schema_scope='LOCAL' AND baseline_version=? AND resource_fingerprint_sha256=?
                    """)) {
                update.setString(1, resource); update.setString(2, catalog);
                update.setInt(3, BASELINE_VERSION); update.setString(4, storedResource);
                if (update.executeUpdate() != 1) throw new SQLException("Local schema metadata changed during mobile web installation.");
            }
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("The mobile item web schema could not be installed.", ex);
        } finally { connection.setAutoCommit(auto); }
    }

    /** Blocks database-dependent work unless this database matches the packaged v1 baseline. */
    public static void requireLocalReady(Connection connection) throws SQLException {
        String key = connection.getMetaData().getURL() + "|" + connection.getMetaData().getUserName();
        if (VALIDATED_LOCAL_DATABASES.contains(key)) return;
        upgradePreReturnReceiptBaseline(connection);
        normalizeInterimReturnReceiptFingerprint(connection);
        upgradeCustomerHistoryCacheBaseline(connection);
        upgradeCustomOrderCreditSeparation(connection);
        upgradeRegisterTransfers(connection);
        upgradeCustomVariantBarcodes(connection);
        upgradeQuotationCustomItems(connection);
        upgradeStoreTransferUuid(connection);
        upgradeCrossStoreTimeClockIdentity(connection);
        ensureMobileItemWebUpgrade(connection);
        ensureOneDriveImageUpgrade(connection);
        Readiness readiness = validateLocal(connection);
        if (!readiness.ready()) throw new SQLException(readiness.message(), "55000");
        VALIDATED_LOCAL_DATABASES.add(key);
    }

    private static void ensureOneDriveImageUpgrade(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","image_assets"))return;
        boolean providerUpgrade=!columnExists(connection,"public","image_assets","cloud_provider");
        boolean sharedConfigurationUpgrade=!tableExists(connection,"public","image_cloud_configuration");
        if(!providerUpgrade&&!sharedConfigurationUpgrade)return;
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try{
            if(providerUpgrade)SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource(
                    "database/migrations/v1_after/20260819120000_onedrive_image_provider.sql"));
            if(sharedConfigurationUpgrade)SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource(
                    "database/migrations/v1_after/20260819230000_onedrive_shared_identifiers.sql"));
            if(tableExists(connection,"public","smartstock_schema_metadata")){
                String resource=resourceFingerprint(localContractResources());
                String catalog=catalogFingerprint(connection,List.of("public"),false);
                try(PreparedStatement update=connection.prepareStatement("UPDATE public.smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=?")){
                    update.setString(1,resource);update.setString(2,catalog);update.setInt(3,BASELINE_VERSION);update.executeUpdate();
                }
            }
            connection.commit();
        }catch(Exception ex){connection.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("The OneDrive image schema could not be installed.",ex);}finally{connection.setAutoCommit(auto);}
    }

    private static void upgradeStoreTransferUuid(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","smartstock_schema_metadata")
                ||columnExists(connection,"public","store_transfers","transfer_uuid"))return;
        String stored,catalog;try(PreparedStatement ps=connection.prepareStatement("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL' AND baseline_version=?")){ps.setInt(1,BASELINE_VERSION);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;stored=rs.getString(1);catalog=rs.getString(2);}}
        if(!catalogFingerprint(connection,List.of("public"),false).equals(catalog))throw new SQLException("The local schema has drifted; automatic store-transfer UUID installation is blocked.","55000");
        String resource;try{resource=resourceFingerprint(localContractResources());}catch(Exception ex){throw new SQLException("The packaged local schema is unreadable.",ex);}
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try{SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource("database/migrations/v1_after/20260811233000_add_store_transfer_uuid.sql"));String next=catalogFingerprint(connection,List.of("public"),false);try(PreparedStatement update=connection.prepareStatement("UPDATE public.smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=? AND resource_fingerprint_sha256=?")){update.setString(1,resource);update.setString(2,next);update.setInt(3,BASELINE_VERSION);update.setString(4,stored);if(update.executeUpdate()!=1)throw new SQLException("Local store-transfer metadata changed during upgrade.");}connection.commit();}catch(Exception ex){connection.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("The store-transfer UUID schema could not be installed.",ex);}finally{connection.setAutoCommit(auto);}
    }

    private static void upgradeCrossStoreTimeClockIdentity(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","employee_time_clock")
                ||(columnExists(connection,"public","employee_time_clock","clock_uuid")
                &&columnExists(connection,"public","payroll_payments","sync_uuid")))return;
        if(tableExists(connection,"public","smartstock_schema_metadata")){
            try(PreparedStatement ps=connection.prepareStatement("SELECT catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL' AND baseline_version=?")){
                ps.setInt(1,BASELINE_VERSION);try(ResultSet rs=ps.executeQuery()){
                    if(rs.next()&&!catalogFingerprint(connection,List.of("public"),false).equals(rs.getString(1)))
                        throw new SQLException("The local schema has drifted; automatic cross-store time-clock installation is blocked.","55000");
                }
            }
        }
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try{
            SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource("database/migrations/v1_after/20260811233200_add_cross_store_time_clock_identity.sql"));
            if(tableExists(connection,"public","smartstock_schema_metadata")){
                String resource=resourceFingerprint(localContractResources()),next=catalogFingerprint(connection,List.of("public"),false);
                try(PreparedStatement update=connection.prepareStatement("UPDATE public.smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=?")){
                    update.setString(1,resource);update.setString(2,next);update.setInt(3,BASELINE_VERSION);update.executeUpdate();
                }
            }
            connection.commit();
        }catch(Exception ex){connection.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("The cross-store time-clock identity schema could not be installed.",ex);}finally{connection.setAutoCommit(auto);}
    }

    private static void upgradeQuotationCustomItems(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","smartstock_schema_metadata")||columnExists(connection,"public","quotations","production_due_date"))return;
        String stored,catalog;try(PreparedStatement ps=connection.prepareStatement("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL' AND baseline_version=?")){ps.setInt(1,BASELINE_VERSION);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;stored=rs.getString(1);catalog=rs.getString(2);}}
        if(!catalogFingerprint(connection,List.of("public"),false).equals(catalog))throw new SQLException("The local schema has drifted; automatic quotation custom-item installation is blocked.","55000");
        String resource;try{resource=resourceFingerprint(localContractResources());}catch(Exception ex){throw new SQLException("The packaged local schema is unreadable.",ex);}
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try{SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource("database/migrations/v1_after/20260811230000_add_custom_items_to_quotations.sql"));String next=catalogFingerprint(connection,List.of("public"),false);try(PreparedStatement update=connection.prepareStatement("UPDATE public.smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=? AND resource_fingerprint_sha256=?")){update.setString(1,resource);update.setString(2,next);update.setInt(3,BASELINE_VERSION);update.setString(4,stored);if(update.executeUpdate()!=1)throw new SQLException("Local quotation custom-item metadata changed during upgrade.");}connection.commit();}catch(Exception ex){connection.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("The quotation custom-item schema could not be installed.",ex);}finally{connection.setAutoCommit(auto);}
    }

    private static void upgradeRegisterTransfers(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","smartstock_schema_metadata")||tableExists(connection,"public","register_transfers"))return;
        String stored,catalog;try(PreparedStatement ps=connection.prepareStatement("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL' AND baseline_version=?")){
            ps.setInt(1,BASELINE_VERSION);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;stored=rs.getString(1);catalog=rs.getString(2);}}
        if(!catalogFingerprint(connection,List.of("public"),false).equals(catalog))throw new SQLException("The local schema has drifted; automatic register-transfer installation is blocked.","55000");
        String resource;try{resource=resourceFingerprint(localContractResources());}catch(Exception ex){throw new SQLException("The packaged local schema is unreadable.",ex);}
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try{
            SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource("database/migrations/v1_after/20260811190000_add_register_transfers.sql"));
            String next=catalogFingerprint(connection,List.of("public"),false);try(PreparedStatement update=connection.prepareStatement("UPDATE public.smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=? AND resource_fingerprint_sha256=?")){
                update.setString(1,resource);update.setString(2,next);update.setInt(3,BASELINE_VERSION);update.setString(4,stored);if(update.executeUpdate()!=1)throw new SQLException("Local register-transfer metadata changed during upgrade.");}
            connection.commit();
        }catch(Exception ex){connection.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("The register-transfer schema could not be installed.",ex);}finally{connection.setAutoCommit(auto);}
    }

    private static void upgradeCustomVariantBarcodes(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","smartstock_schema_metadata")||tableExists(connection,"public","custom_order_item_variant_barcodes"))return;
        String stored,catalog;try(PreparedStatement ps=connection.prepareStatement("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL' AND baseline_version=?")){
            ps.setInt(1,BASELINE_VERSION);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;stored=rs.getString(1);catalog=rs.getString(2);}}
        if(!catalogFingerprint(connection,List.of("public"),false).equals(catalog))throw new SQLException("The local schema has drifted; automatic custom variant barcode installation is blocked.","55000");
        String resource;try{resource=resourceFingerprint(localContractResources());}catch(Exception ex){throw new SQLException("The packaged local schema is unreadable.",ex);}
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try{
            SqlScriptRunner.runSql(connection,SqlScriptRunner.readResource("database/migrations/v1_after/20260811220000_add_custom_variant_barcodes.sql"));
            String next=catalogFingerprint(connection,List.of("public"),false);try(PreparedStatement update=connection.prepareStatement("UPDATE public.smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=? AND resource_fingerprint_sha256=?")){
                update.setString(1,resource);update.setString(2,next);update.setInt(3,BASELINE_VERSION);update.setString(4,stored);if(update.executeUpdate()!=1)throw new SQLException("Local schema metadata changed during custom variant barcode installation.");}
            connection.commit();
        }catch(Exception ex){connection.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("The custom variant barcode schema could not be installed.",ex);}finally{connection.setAutoCommit(auto);}
    }

    private static void upgradeCustomOrderCreditSeparation(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","customer_account_transactions")||columnExists(connection,"public","customer_account_transactions","credit_applied_amount"))return;
        String stored,catalog;try(PreparedStatement ps=connection.prepareStatement("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL' AND baseline_version=?")){
            ps.setInt(1,BASELINE_VERSION);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;stored=rs.getString(1);catalog=rs.getString(2);}}
        if(!catalogFingerprint(connection,List.of("public"),false).equals(catalog))throw new SQLException("The local schema has drifted; automatic custom-order credit separation is blocked.","55000");
        String resource;try{resource=resourceFingerprint(localContractResources());}catch(Exception ex){throw new SQLException("The packaged local schema is unreadable.",ex);}
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try(Statement s=connection.createStatement()){
            s.execute("ALTER TABLE customer_account_transactions ADD COLUMN credit_applied_amount numeric(12,2) NOT NULL DEFAULT 0");
            s.execute("ALTER TABLE sync_cross_store_customer_history_cache ADD COLUMN credit_applied_amount numeric(12,2) NOT NULL DEFAULT 0");
            s.execute("ALTER TABLE sync_cross_store_customer_history_cache ADD COLUMN document_balance numeric(12,2) NOT NULL DEFAULT 0");
            s.execute("UPDATE customer_account_transactions SET transaction_type='CUSTOM_ORDER_BALANCE',credit_applied_amount=0 WHERE transaction_type='CUSTOM_ORDER_CREDIT'");
            s.execute("UPDATE customer_account_transactions t SET credit_applied_amount=CASE WHEN t.transaction_type='PAYMENT' AND t.custom_order_id IS NULL THEN GREATEST(ABS(COALESCE(t.amount,0))-COALESCE((SELECT SUM(a.amount) FROM customer_account_payment_allocations a WHERE a.payment_transaction_id=t.transaction_id AND a.custom_order_id IS NOT NULL),0),0) ELSE 0 END");
            s.execute("UPDATE customer_account_transactions SET transaction_type='CUSTOM_ORDER_PAYMENT',credit_applied_amount=0 WHERE transaction_type='PAYMENT' AND custom_order_id IS NOT NULL");
            String next=catalogFingerprint(connection,List.of("public"),false);try(PreparedStatement u=connection.prepareStatement("UPDATE smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=? AND resource_fingerprint_sha256=?")){
                u.setString(1,resource);u.setString(2,next);u.setInt(3,BASELINE_VERSION);u.setString(4,stored);if(u.executeUpdate()!=1)throw new SQLException("Local custom-order credit separation metadata changed during upgrade.");}
            connection.commit();
        }catch(SQLException ex){connection.rollback();throw ex;}finally{connection.setAutoCommit(auto);}
    }

    /** Accepts the released 1.0.45 catalog while normalizing its SQL resource bytes. */
    private static void normalizeInterimReturnReceiptFingerprint(Connection connection)
            throws SQLException {
        if (!tableExists(connection, "public", "smartstock_schema_metadata")) return;
        String storedResource;
        String storedCatalog;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256
                FROM public.smartstock_schema_metadata
                WHERE schema_scope='LOCAL' AND baseline_version=?
                """)) {
            ps.setInt(1, BASELINE_VERSION);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                storedResource = rs.getString(1);
                storedCatalog = rs.getString(2);
            }
        }
        if (!INTERIM_RETURN_RECEIPT_LOCAL_FINGERPRINT.equals(storedResource)) return;
        String actualCatalog = catalogFingerprint(connection, List.of("public"), false);
        if (!actualCatalog.equals(storedCatalog)) {
            throw new SQLException("The interim return-receipt schema has drifted; fingerprint normalization is blocked.", "55000");
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE public.smartstock_schema_metadata
                SET resource_fingerprint_sha256=?
                WHERE schema_scope='LOCAL' AND baseline_version=?
                  AND resource_fingerprint_sha256=?
                """)) {
            update.setString(1, PRE_CUSTOMER_HISTORY_CACHE_LOCAL_FINGERPRINT);
            update.setInt(2, BASELINE_VERSION);
            update.setString(3, INTERIM_RETURN_RECEIPT_LOCAL_FINGERPRINT);
            if (update.executeUpdate() != 1) {
                throw new SQLException("The interim return-receipt fingerprint changed during normalization.");
            }
        }
    }

    /** Exact-contract upgrade adding the read-only cross-store customer history cache. */
    private static void upgradeCustomerHistoryCacheBaseline(Connection connection)throws SQLException{
        if(!tableExists(connection,"public","smartstock_schema_metadata"))return;
        String stored,catalog;try(PreparedStatement ps=connection.prepareStatement("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL' AND baseline_version=?")){
            ps.setInt(1,BASELINE_VERSION);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return;stored=rs.getString(1);catalog=rs.getString(2);}}
        if(!PRE_CUSTOMER_HISTORY_CACHE_LOCAL_FINGERPRINT.equals(stored))return;
        if(!catalogFingerprint(connection,List.of("public"),false).equals(catalog))throw new SQLException("The previous local schema has drifted; automatic customer-history cache upgrade is blocked.","55000");
        String resource;try{resource=resourceFingerprint(LOCAL_BASELINE);}catch(Exception ex){throw new SQLException("The packaged local schema is unreadable.",ex);}
        boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);try(Statement s=connection.createStatement()){
            s.execute("CREATE TABLE public.sync_cross_store_customer_history_cache (source_location_id integer NOT NULL,event_key text NOT NULL,customer_id integer NOT NULL,event_type text NOT NULL,source_id bigint,document_number text,source_created_at timestamp with time zone,store_name text NOT NULL,user_name text,device_name text,cash_drawer_name text,payment_method text,payment_reference text,amount numeric(12,2) DEFAULT 0 NOT NULL,payment_status text,document_status text,document_total numeric(12,2) DEFAULT 0 NOT NULL,note text,cache_refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,cache_status text DEFAULT 'CURRENT' NOT NULL,PRIMARY KEY(source_location_id,event_key))");
            s.execute("CREATE INDEX sync_cross_store_customer_history_customer_idx ON public.sync_cross_store_customer_history_cache(customer_id,source_created_at DESC)");
            s.execute("CREATE TABLE public.sync_cross_store_customer_history_status (source_location_id integer NOT NULL PRIMARY KEY,store_name text NOT NULL,row_count integer DEFAULT 0 NOT NULL,status text NOT NULL,last_error text,refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL)");
            s.execute("UPDATE customer_account_transactions t SET location_id=s.location_id FROM sales s WHERE t.sale_id=s.sale_id AND t.location_id IS NULL");
            s.execute("UPDATE customer_account_transactions t SET location_id=o.location_id FROM custom_orders o WHERE t.custom_order_id=o.custom_order_id AND t.location_id IS NULL");
            s.execute("UPDATE customer_account_transactions t SET location_id=i.location_id FROM invoices i WHERE t.invoice_id=i.invoice_id AND t.location_id IS NULL");
            String nextCatalog=catalogFingerprint(connection,List.of("public"),false);try(PreparedStatement update=connection.prepareStatement("UPDATE public.smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL' AND baseline_version=?")){
                update.setString(1,resource);update.setString(2,nextCatalog);update.setInt(3,BASELINE_VERSION);if(update.executeUpdate()!=1)throw new SQLException("Local customer-history cache contract could not be upgraded.");}
            connection.commit();
        }catch(SQLException ex){connection.rollback();throw ex;}finally{connection.setAutoCommit(auto);}
    }

    /** One-time, exact-contract upgrade from the accepted pre-return-receipt v1 baseline. */
    private static void upgradePreReturnReceiptBaseline(Connection connection) throws SQLException {
        if (!tableExists(connection, "public", "smartstock_schema_metadata")) return;
        int version;
        String storedResource;
        String storedCatalog;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT baseline_version,resource_fingerprint_sha256,catalog_fingerprint_sha256
                FROM public.smartstock_schema_metadata WHERE schema_scope='LOCAL'
                """); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return;
            version = rs.getInt(1); storedResource = rs.getString(2); storedCatalog = rs.getString(3);
        }
        if (version != BASELINE_VERSION || !isPreReturnReceiptFingerprint(storedResource)) return;
        String actualBefore = catalogFingerprint(connection, List.of("public"), false);
        if (!actualBefore.equals(storedCatalog)) {
            throw new SQLException("The previous local schema has drifted; automatic return-receipt upgrade is blocked.", "55000");
        }
        String newResource;
        try { newResource = resourceFingerprint(LOCAL_BASELINE); }
        catch (Exception ex) { throw new SQLException("The packaged local schema is unreadable.", ex); }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE public.sale_returns ADD COLUMN return_receipt_number text, ADD COLUMN receipt_device_id text, ADD COLUMN receipt_sequence integer");
            statement.execute("CREATE UNIQUE INDEX sale_returns_receipt_number_uidx ON public.sale_returns(return_receipt_number) WHERE COALESCE(return_receipt_number,'')<>''");
            statement.execute("ALTER TABLE public.cross_store_refund_requests ADD COLUMN return_receipt_number text, ADD COLUMN receipt_device_id text, ADD COLUMN receipt_sequence integer");
            statement.execute("CREATE UNIQUE INDEX cross_store_refund_requests_receipt_number_uidx ON public.cross_store_refund_requests(return_receipt_number) WHERE COALESCE(return_receipt_number,'')<>''");
            statement.execute("ALTER TABLE public.sync_cross_store_returns_cache ADD COLUMN return_receipt_number text");
            statement.execute("CREATE TABLE public.sync_cross_store_customer_history_cache (source_location_id integer NOT NULL,event_key text NOT NULL,customer_id integer NOT NULL,event_type text NOT NULL,source_id bigint,document_number text,source_created_at timestamp with time zone,store_name text NOT NULL,user_name text,device_name text,cash_drawer_name text,payment_method text,payment_reference text,amount numeric(12,2) DEFAULT 0 NOT NULL,payment_status text,document_status text,document_total numeric(12,2) DEFAULT 0 NOT NULL,note text,cache_refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,cache_status text DEFAULT 'CURRENT' NOT NULL,PRIMARY KEY(source_location_id,event_key))");
            statement.execute("CREATE INDEX sync_cross_store_customer_history_customer_idx ON public.sync_cross_store_customer_history_cache(customer_id,source_created_at DESC)");
            statement.execute("CREATE TABLE public.sync_cross_store_customer_history_status (source_location_id integer NOT NULL PRIMARY KEY,store_name text NOT NULL,row_count integer DEFAULT 0 NOT NULL,status text NOT NULL,last_error text,refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL)");
            statement.execute("UPDATE customer_account_transactions t SET location_id=s.location_id FROM sales s WHERE t.sale_id=s.sale_id AND t.location_id IS NULL");
            statement.execute("UPDATE customer_account_transactions t SET location_id=o.location_id FROM custom_orders o WHERE t.custom_order_id=o.custom_order_id AND t.location_id IS NULL");
            statement.execute("UPDATE customer_account_transactions t SET location_id=i.location_id FROM invoices i WHERE t.invoice_id=i.invoice_id AND t.location_id IS NULL");
            String newCatalog = catalogFingerprint(connection, List.of("public"), false);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE public.smartstock_schema_metadata
                    SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=?
                    WHERE schema_scope='LOCAL' AND baseline_version=?
                    """)) {
                update.setString(1, newResource); update.setString(2, newCatalog); update.setInt(3, BASELINE_VERSION);
                if (update.executeUpdate() != 1) throw new SQLException("Local schema contract could not be upgraded.");
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    static boolean isPreReturnReceiptFingerprint(String fingerprint) {
        return PRE_RETURN_RECEIPT_LOCAL_FINGERPRINTS.contains(fingerprint);
    }

    private static boolean columnExists(Connection connection,String schema,String table,String column)throws SQLException{
        try(ResultSet rs=connection.getMetaData().getColumns(null,schema,table,column)){return rs.next();}
    }

    public static Readiness validateCloud(Connection connection) throws SQLException {
        return validate(connection, "CLOUD", cloudContractResources(),
                List.of("public", "smartstock_private"), true);
    }

    static Readiness validateCloudApplied(Connection connection, List<String> resources)
            throws SQLException {
        boolean grantFingerprint = resources.contains(
                "database/migrations/v1_after/20260809190000_revoke_anon_security_definer_execute.sql");
        return validate(connection, "CLOUD", resources,
                List.of("public", "smartstock_private"), grantFingerprint);
    }

    static void installCloudBaseline(Connection connection) throws Exception {
        installBaseline(connection, CLOUD_BASELINE, "CLOUD",
                List.of("public", "smartstock_private"));
    }

    static void refreshCloudContract(Connection connection) throws Exception {
        String resourceFingerprint = resourceFingerprint(cloudContractResources());
        String catalogFingerprint = catalogFingerprint(connection,
                List.of("public", "smartstock_private"), true);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE smartstock_private.smartstock_schema_metadata
                SET resource_fingerprint_sha256=?, catalog_fingerprint_sha256=?
                WHERE schema_scope='CLOUD' AND baseline_version=?
                """)) {
            statement.setString(1, resourceFingerprint);
            statement.setString(2, catalogFingerprint);
            statement.setInt(3, BASELINE_VERSION);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Cloud schema metadata could not be refreshed.");
            }
        }
    }

    private static void installBaseline(Connection connection, List<String> resources,
                                        String scope, List<String> schemas) throws Exception {
        String resourceFingerprint = resourceFingerprint(resources);
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String resource : resources) {
                String sql = SqlScriptRunner.readResource(resource)
                        .replace(RESOURCE_FINGERPRINT_TOKEN, resourceFingerprint);
                SqlScriptRunner.runSql(connection, sql);
            }
            // The already-applied v1 baseline predates grant fingerprinting. The
            // first post-v1 hardening migration upgrades the contract atomically.
            String catalogFingerprint = catalogFingerprint(connection, schemas, false);
            String metadataTable = "CLOUD".equals(scope)
                    ? "smartstock_private.smartstock_schema_metadata"
                    : "public.smartstock_schema_metadata";
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE %s
                    SET catalog_fingerprint_sha256=?
                    WHERE schema_scope=? AND baseline_version=?
                    """.formatted(metadataTable))) {
                statement.setString(1, catalogFingerprint);
                statement.setString(2, scope);
                statement.setInt(3, BASELINE_VERSION);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("SmartStock schema metadata was not installed.");
                }
            }
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static Readiness validate(Connection connection, String scope,
                                      List<String> resources, List<String> schemas)
            throws SQLException {
        return validate(connection, scope, resources, schemas,
                "CLOUD".equals(scope));
    }

    private static Readiness validate(Connection connection, String scope,
                                      List<String> resources, List<String> schemas,
                                      boolean includeGrants)
            throws SQLException {
        String metadataSchema = "CLOUD".equals(scope) ? "smartstock_private" : "public";
        if (!tableExists(connection, metadataSchema, "smartstock_schema_metadata")) {
            return new Readiness(false, null, null, null,
                    "Schema metadata is missing. Build and verify a side-by-side v1 candidate.");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT baseline_version, resource_fingerprint_sha256,
                       catalog_fingerprint_sha256
                FROM %s.smartstock_schema_metadata
                WHERE schema_scope=?
                """.formatted(metadataSchema))) {
            statement.setString(1, scope);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return new Readiness(false, null, null, null,
                            "Schema metadata has no " + scope + " contract.");
                }
                int version = rows.getInt(1);
                String storedResource = rows.getString(2);
                String storedCatalog = rows.getString(3);
                String expectedResource;
                try {
                    expectedResource = resourceFingerprint(resources);
                } catch (Exception ex) {
                    throw new SQLException("Packaged v1 SQL is missing or unreadable: "
                            + ex.getMessage(), ex);
                }
                String actualCatalog = catalogFingerprint(connection, schemas, includeGrants);
                boolean ready = version == BASELINE_VERSION
                        && expectedResource.equals(storedResource)
                        && actualCatalog.equals(storedCatalog);
                String message = ready ? "Schema v1 is ready."
                        : "Schema v1 fingerprint mismatch. Use side-by-side repair; in-place repair is blocked.";
                return new Readiness(ready, version, storedResource, actualCatalog, message);
            }
        }
    }

    static String resourceFingerprint(List<String> resources) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String resource : resources) {
            digest.update(resource.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(SqlScriptRunner.readResource(resource)
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String catalogFingerprint(Connection connection, List<String> schemas)
            throws SQLException {
        return catalogFingerprint(connection, schemas,
                schemas.contains("smartstock_private"));
    }

    private static String catalogFingerprint(Connection connection, List<String> schemas,
                                             boolean includeGrants) throws SQLException {
        List<String> entries = new ArrayList<>();
        collect(connection, entries, """
                SELECT 'column|'||table_schema||'|'||table_name||'|'||
                       lpad(ordinal_position::text,5,'0')||'|'||column_name||'|'||
                       data_type||'|'||coalesce(udt_schema,'')||'|'||coalesce(udt_name,'')||'|'||
                       is_nullable||'|'||coalesce(column_default,'')
                FROM information_schema.columns
                WHERE table_schema = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'constraint|'||n.nspname||'|'||c.relname||'|'||con.conname||'|'||
                       pg_get_constraintdef(con.oid, true)
                FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid
                JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'index|'||schemaname||'|'||tablename||'|'||indexname||'|'||indexdef
                FROM pg_indexes WHERE schemaname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'trigger|'||n.nspname||'|'||c.relname||'|'||t.tgname||'|'||
                       pg_get_triggerdef(t.oid, true)
                FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
                JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE NOT t.tgisinternal AND n.nspname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'function|'||n.nspname||'|'||p.proname||'|'||
                       pg_get_function_identity_arguments(p.oid)||'|'||pg_get_functiondef(p.oid)
                FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE p.prokind IN ('f','p') AND n.nspname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'policy|'||schemaname||'|'||tablename||'|'||policyname||'|'||
                       permissive||'|'||coalesce(array_to_string(roles,','),'')||'|'||cmd||'|'||
                       coalesce(qual,'')||'|'||coalesce(with_check,'')
                FROM pg_policies WHERE schemaname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'sequence|'||schemaname||'|'||sequencename||'|'||data_type||'|'||
                       start_value||'|'||min_value||'|'||max_value||'|'||increment_by||'|'||cycle
                FROM pg_sequences WHERE schemaname = ANY (?)
                """, schemas);
        if (schemas.contains("smartstock_private")) {
            if (includeGrants) {
            collect(connection, entries, """
                    SELECT 'relation-grant|'||n.nspname||'|'||c.relname||'|'||
                           coalesce(grantee.rolname,'PUBLIC')||'|'||acl.privilege_type||'|'||
                           acl.is_grantable
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid=c.relnamespace
                    CROSS JOIN LATERAL aclexplode(c.relacl) acl
                    LEFT JOIN pg_roles grantee ON grantee.oid=acl.grantee
                    WHERE n.nspname = ANY (?)
                      AND coalesce(grantee.rolname,'PUBLIC') = ANY
                          (ARRAY['PUBLIC','anon','authenticated','service_role'])
                    """, schemas);
            collect(connection, entries, """
                    SELECT 'function-grant|'||n.nspname||'|'||p.proname||'|'||
                           pg_get_function_identity_arguments(p.oid)||'|'||
                           coalesce(grantee.rolname,'PUBLIC')||'|'||acl.privilege_type||'|'||
                           acl.is_grantable
                    FROM pg_proc p
                    JOIN pg_namespace n ON n.oid=p.pronamespace
                    CROSS JOIN LATERAL aclexplode(p.proacl) acl
                    LEFT JOIN pg_roles grantee ON grantee.oid=acl.grantee
                    WHERE n.nspname = ANY (?)
                      AND coalesce(grantee.rolname,'PUBLIC') = ANY
                          (ARRAY['PUBLIC','anon','authenticated','service_role'])
                    """, schemas);
            collect(connection, entries, """
                    SELECT 'schema-grant|'||n.nspname||'|'||
                           coalesce(grantee.rolname,'PUBLIC')||'|'||acl.privilege_type||'|'||
                           acl.is_grantable
                    FROM pg_namespace n
                    CROSS JOIN LATERAL aclexplode(n.nspacl) acl
                    LEFT JOIN pg_roles grantee ON grantee.oid=acl.grantee
                    WHERE n.nspname = ANY (?)
                      AND coalesce(grantee.rolname,'PUBLIC') = ANY
                          (ARRAY['PUBLIC','anon','authenticated','service_role'])
                    """, schemas);
            }
            collectWithoutSchemas(connection, entries, """
                    SELECT 'storage-policy|'||schemaname||'|'||tablename||'|'||policyname||'|'||
                           permissive||'|'||coalesce(array_to_string(roles,','),'')||'|'||cmd||'|'||
                           coalesce(qual,'')||'|'||coalesce(with_check,'')
                    FROM pg_policies
                    WHERE schemaname='storage' AND tablename='objects'
                      AND policyname IN (
                        'Anyone can view product images',
                        'Authenticated users can upload product images',
                        'Authenticated users can update product images',
                        'employee files staff insert', 'employee files staff read',
                        'employee files staff update',
                        'smartstock releases admin insert',
                        'smartstock releases admin update',
                        'smartstock releases authenticated read'
                      )
                    """);
            collectWithoutSchemas(connection, entries, """
                    SELECT 'storage-bucket|'||id||'|'||name||'|'||public||'|'||
                           coalesce(file_size_limit::text,'')||'|'||
                           coalesce(array_to_string(allowed_mime_types,','),'')
                    FROM storage.buckets
                    WHERE id IN ('employee files','Product Images','smartstock-releases')
                    """);
        }
        entries.sort(Comparator.naturalOrder());
        return sha256(String.join("\n", entries));
    }

    private static void collect(Connection connection, List<String> target, String sql,
                                List<String> schemas) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("text", schemas.toArray()));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) target.add(rows.getString(1));
            }
        }
    }

    private static void collectWithoutSchemas(Connection connection, List<String> target,
                                              String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) target.add(rows.getString(1));
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, schema + "." + table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    public record Readiness(boolean ready, Integer version, String resourceFingerprint,
                            String catalogFingerprint, String message) {
    }
}
