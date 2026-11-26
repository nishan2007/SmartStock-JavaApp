package services;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.*;

/** Read-only history for an approved customer-account browser. */
final class CustomerAccountWebHistory {
    private static final int PAGE_SIZE = 25;
    private static final Set<String> ORDER_FILE_PERMISSIONS = Set.of("CREATE_CUSTOM_ORDER", "MANAGE_CUSTOM_ORDERS",
            "CUSTOM_ORDER_LOOKUP", "CUSTOM_ORDER_PRODUCTION_STEPS", "CUSTOM_ORDER_OVERRIDES");

    private CustomerAccountWebHistory() { }

    static Map<String, Object> list(Connection c, int customer, int user, int location,
                                    String query, int offset) throws Exception {
        String search = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (search.length() > 180 || offset < 0)
            throw new IllegalArgumentException("Enter up to 180 characters to search customer history.");
        var rows = LanCustomerAccountService.historyRows(c, customer, user, location);
        if (search.isEmpty()) return Map.of("transactions", withItemSummaries(c,customer,rows.stream().limit(10).toList()), "hasMore", false);

        var itemMatches = matchingDocuments(c, customer, search);
        var matches = rows.stream().filter(row -> matches(row, search, itemMatches)).toList();
        int start = Math.min(offset, matches.size());
        int end = start + Math.min(PAGE_SIZE, matches.size() - start);
        return Map.of("transactions", withItemSummaries(c,customer,matches.subList(start,end)), "hasMore", end < matches.size(),
                "count", matches.size());
    }

    private static List<Map<String,Object>> withItemSummaries(Connection c,int customer,List<Map<String,Object>> page)throws SQLException {
        Long[] sales=documentIds(page,"SALE");
        Long[] orders=documentIds(page,"CUSTOM_ORDER");
        if(sales.length==0&&orders.length==0)return page;
        Map<String,List<String>> byDocument=new HashMap<>();
        Array saleIds=c.createArrayOf("int8",sales);
        Array orderIds=c.createArrayOf("int8",orders);
        try {
            for(var item:rows(c,"""
                    SELECT location_id,kind,id,item_name,quantity FROM (
                        SELECT location_id,kind,id,item_name,quantity,
                            row_number() OVER (PARTITION BY location_id,kind,id ORDER BY first_line) AS slot
                        FROM (
                            SELECT s.location_id,'SALE' AS kind,s.sale_id AS id,
                              coalesce(nullif(i.item_name,''),p.name,'Item') AS item_name,
                              sum(i.quantity) AS quantity,min(i.sale_item_id) AS first_line
                            FROM sales s JOIN sale_items i ON i.sale_id=s.sale_id LEFT JOIN products p ON p.product_id=i.product_id
                            WHERE s.customer_id=? AND s.sale_id=ANY(?) GROUP BY s.location_id,s.sale_id,4
                            UNION ALL
                            SELECT o.location_id,'CUSTOM_ORDER',o.custom_order_id,i.item_name,count(*),min(i.custom_order_line_id)
                            FROM custom_orders o JOIN custom_order_lines i ON i.custom_order_id=o.custom_order_id
                            WHERE o.customer_id=? AND o.custom_order_id=ANY(?) GROUP BY o.location_id,o.custom_order_id,i.item_name
                        ) grouped_items
                    ) first_items WHERE slot<=2 ORDER BY slot
                    """,customer,saleIds,customer,orderIds)){
                var doc=key(item.get("location_id"),item.get("kind"),item.get("id"));
                byDocument.computeIfAbsent(doc,ignored->new ArrayList<>())
                        .add(item.get("quantity")+" × "+item.get("item_name"));
            }
        } finally {saleIds.free();orderIds.free();}
        return page.stream().map(row->{
            var summary=byDocument.get(key(row.get("locationId"),row.get("transactionType"),row.get("documentId")));
            if(summary==null||Boolean.TRUE.equals(row.get("remote")))return row;
            var output=new LinkedHashMap<>(row);
            output.put("itemSummary",String.join(", ",summary));
            return output;
        }).toList();
    }

    private static Long[] documentIds(List<Map<String,Object>> page,String kind) {
        return page.stream().filter(row->kind.equals(row.get("transactionType"))&&!Boolean.TRUE.equals(row.get("remote")))
                .map(row->row.get("documentId")).filter(Number.class::isInstance).map(id->((Number)id).longValue())
                .distinct().toArray(Long[]::new);
    }

    private static boolean matches(Map<String, Object> row, String query, Set<String> documents) {
        return documents.contains(key(row.get("locationId"), row.get("documentType"), row.get("documentId"))) ||
                List.of("paymentId", "documentNumber", "transactionType", "documentType", "storeName",
                        "paymentMethod", "paymentReference", "note", "documentStatus", "paymentStatus")
                .stream().map(field -> Objects.toString(row.get(field), "").toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(query) || value.replace('_', ' ').contains(query));
    }

    private static String key(Object location, Object type, Object document) {
        return location + ":" + type + ":" + document;
    }

    private static Set<String> matchingDocuments(Connection c, int customer, String query) throws SQLException {
        Set<String> keys = new HashSet<>();
        for (var row : rows(c, """
                SELECT s.location_id, 'SALE' AS kind, s.sale_id AS id
                FROM sales s JOIN sale_items i ON i.sale_id=s.sale_id
                LEFT JOIN products p ON p.product_id=i.product_id
                WHERE s.customer_id=? AND strpos(lower(concat_ws(' ', i.item_name, i.sku_snapshot,
                    i.size_snapshot, i.color_snapshot, p.name, p.sku)),?)>0
                UNION
                SELECT o.location_id, 'CUSTOM_ORDER', o.custom_order_id
                FROM custom_orders o JOIN custom_order_lines i ON i.custom_order_id=o.custom_order_id
                WHERE o.customer_id=? AND strpos(lower(concat_ws(' ',i.item_name,i.variant_name,
                    i.item_size,i.item_color,i.customization_details,i.order_instructions)),?)>0
                UNION
                SELECT s.source_location_id, 'SALE', s.sale_id
                FROM sync_cross_store_sales_cache s JOIN sync_cross_store_sale_items_cache i
                  USING (source_location_id,sale_id)
                WHERE s.customer_id=? AND strpos(lower(concat_ws(' ',i.product_name,i.sku)),?)>0
                UNION
                SELECT h.source_location_id, 'CUSTOM_ORDER', h.source_id
                FROM sync_cross_store_customer_history_cache h
                JOIN sync_cross_store_source_rows i ON i.source_location_id=h.source_location_id
                  AND i.table_name='custom_order_lines' AND i.row_data->>'custom_order_id'=h.source_id::text
                WHERE h.customer_id=? AND h.event_type='CUSTOM_ORDER' AND strpos(lower(concat_ws(' ',
                    i.row_data->>'item_name',i.row_data->>'variant_name',i.row_data->>'item_size',i.row_data->>'item_color',
                    i.row_data->>'customization_details',i.row_data->>'order_instructions')),?)>0
                """, customer, query, customer, query, customer, query, customer, query)) {
            keys.add(key(row.get("location_id"), row.get("kind"), row.get("id")));
        }
        return keys;
    }

    static Map<String, Object> detail(Connection c, int customer, int user, int activeStore,
                                      int store, String event, Set<String> permissions) throws Exception {
        for (var row : LanCustomerAccountService.historyRows(c, customer, user, activeStore)) {
            if (!event.equals(row.get("eventId")) || !Objects.equals(store == 0 ? null : store, row.get("locationId"))) continue;
            var result = new LinkedHashMap<String, Object>();
            result.put("transaction", row);
            String kind = (String) row.get("transactionType");
            if (!Boolean.TRUE.equals(row.get("remote"))) {
                switch (kind) {
                    case "SALE" -> result.put("sale", sale(c, customer, store, ((Number) row.get("documentId")).intValue()));
                    case "CUSTOM_ORDER" -> {
                        var details=CustomOrderDetailsService.read(c,((Number) row.get("documentId")).longValue(),store,customer);
                        details.put("canOpenFiles",store==activeStore&&!Collections.disjoint(permissions,ORDER_FILE_PERMISSIONS));
                        result.put("order",details);
                    }
                    case "PAYMENT" -> {
                        if (row.get("transactionId") instanceof Number id)
                            result.put("payment", LanCustomerAccountService.receipt(c, customer, id.longValue(), user));
                    }
                    default -> { }
                }
            } else if ("SALE".equals(kind) && row.get("documentId") instanceof Number id) {
                result.put("sale", cachedSale(c, customer, store, id.intValue()));
            } else if ("CUSTOM_ORDER".equals(kind) && row.get("documentId") instanceof Number id) {
                result.put("order", cachedOrder(c, customer, store, id.longValue()));
            }
            return result;
        }
        throw new LanCustomerAccountService.RuleViolation(404, "TRANSACTION_NOT_FOUND",
                "This transaction was not found on the customer account.");
    }

    private static Map<String,Object> cachedOrder(Connection c,int customer,int store,long order)throws SQLException {
        var headers=rows(c,"""
                SELECT row_data::text AS data FROM sync_cross_store_source_rows
                WHERE source_location_id=? AND table_name='custom_orders'
                  AND row_data->>'custom_order_id'=?
                  AND row_data->>'customer_id'=? AND row_data->>'location_id'=?
                """,store,Long.toString(order),Integer.toString(customer),Integer.toString(store));
        if(headers.isEmpty())return null;
        var summary=fields(readJson(headers.get(0)),"created_at delivered_at completed_at location_name assigned_at assigned_by_name "
                +"cancelled_at cancelled_by_name cancellation_reason source_invoice_id source_quotation_id minimum_deposit_required "
                +"deposit_override_reason deposit_override_by_name custom_order_id order_number customer_name customer_phone "
                +"taken_by_name status due_date assigned_to_name payment_method payment_status payment_reference total_amount amount_paid "
                +"balance_due order_notes invoice_billed pricing_state pricing_defer_reason pricing_defer_by_name pricing_finalized_at pricing_finalized_by_name");
        if(rows(c,"""
                SELECT 1 FROM sync_cross_store_table_state
                WHERE source_location_id=? AND table_name='custom_order_lines'
                """,store).isEmpty())return null;
        var result=new LinkedHashMap<String,Object>();
        result.put("summary",summary);
        result.put("lines",cachedOrderRows(c,store,order,"custom_order_lines",
                "custom_order_line_id width_value length_value dimension_unit area_value area_unit area_price item_name variant_name "
                +"item_size item_color unit_price line_total production_status delivery_status return_status returned_amount customization_details order_instructions"));
        result.put("payments",cachedOrderRows(c,store,order,"custom_order_payments",
                "custom_order_payment_id created_at payment_action payment_amount payment_method payment_reference taken_by_name void_reason voided_at"));
        result.put("activity",cachedOrderRows(c,store,order,"custom_order_audit_log",
                "created_at action_type field_name old_value new_value user_name reason"));
        result.put("deliveries",cachedOrderRows(c,store,order,"custom_order_line_deliveries",
                "delivered_at item_name device_name delivered_by_name delivery_notes"));
        result.put("production",cachedOrderRows(c,store,order,"custom_order_line_production_history",
                "created_at item_name old_status new_status device_name updated_by_name notes"));
        result.put("returns",cachedOrderRows(c,store,order,"custom_order_line_returns",
                "created_at item_name return_type refund_amount restock_action created_by_name reason"));
        result.put("addons",rows(c,"""
                SELECT a.row_data::text AS data FROM sync_cross_store_source_rows l
                JOIN sync_cross_store_source_rows a ON a.source_location_id=l.source_location_id
                  AND a.table_name='custom_order_line_print_addons'
                  AND a.row_data->>'custom_order_line_id'=l.row_data->>'custom_order_line_id'
                WHERE l.source_location_id=? AND l.table_name='custom_order_lines' AND l.row_data->>'custom_order_id'=?
                ORDER BY a.row_key
                """,store,Long.toString(order)).stream().map(row->fields(readJson(row),
                "custom_order_line_id print_material_name print_size_name print_description print_charge")).toList());
        result.put("canOpenFiles",false);
        return result;
    }

    private static List<JsonObject> cachedOrderRows(Connection c,int store,long order,String table,String fields)throws SQLException {
        return rows(c,"""
                SELECT row_data::text AS data FROM sync_cross_store_source_rows
                WHERE source_location_id=? AND table_name=? AND row_data->>'custom_order_id'=?
                ORDER BY (row_data->>'sort_order')::integer NULLS LAST,row_data->>'created_at' DESC NULLS LAST,row_key
                """,store,table,Long.toString(order)).stream().map(row->fields(readJson(row),fields)).toList();
    }

    private static JsonObject readJson(Map<String,Object> row) {
        return JsonParser.parseString((String)row.get("data")).getAsJsonObject();
    }

    private static JsonObject fields(JsonObject source,String names) {
        var result=new JsonObject();
        for(String name:names.split(" ")) if(source.has(name))result.add(name,source.get(name));
        return result;
    }

    static Map<String,Object> orderFile(Connection c,int customer,int activeStore,String event,UUID file,int offset,
                                        Set<String> permissions) throws Exception {
        if (Collections.disjoint(permissions,ORDER_FILE_PERMISSIONS))
            throw new SecurityException("Your role cannot open custom order attachments.");
        if (!event.matches("CUSTOM_ORDER:[0-9]{1,18}") || offset<0)
            throw new IllegalArgumentException("Invalid attachment request.");
        long order=Long.parseLong(event.substring("CUSTOM_ORDER:".length()));
        var files=rows(c,"""
                SELECT f.storage_key,f.filename,f.byte_size
                FROM custom_order_files f JOIN custom_orders o USING (custom_order_id)
                WHERE o.custom_order_id=? AND o.customer_id=? AND o.location_id=? AND f.file_id=?
                  AND f.removed_at IS NULL AND f.deleted_at IS NULL
                  AND (o.status<>'DELIVERED' OR o.delivered_at>now()-interval '30 days')
                UNION ALL
                SELECT f.storage_key,f.filename,f.byte_size
                FROM custom_order_design_proofs f JOIN custom_orders o USING (custom_order_id)
                WHERE o.custom_order_id=? AND o.customer_id=? AND o.location_id=? AND f.proof_id=?
                  AND f.deleted_at IS NULL AND (f.decided_at IS NULL OR
                    (f.status='APPROVED' AND f.decided_at>now()-interval '3 months') OR
                    (f.status<>'APPROVED' AND f.decided_at>now()-interval '30 days'))
                """,order,customer,activeStore,file,order,customer,activeStore,file);
        if(files.isEmpty()) throw new LanCustomerAccountService.RuleViolation(404,"FILE_UNAVAILABLE","This order file is unavailable.");
        var entry=files.get(0);
        long size=((Number)entry.get("byte_size")).longValue();
        if(offset>=size)throw new IllegalArgumentException("Invalid attachment range.");
        byte[] bytes=new byte[(int)Math.min(524288,size-offset)];
        try(var channel=FileChannel.open(CustomOrderMediaService.localFile((String)entry.get("storage_key")),StandardOpenOption.READ)){
            var buffer=ByteBuffer.wrap(bytes);
            while(buffer.hasRemaining()&&channel.read(buffer,offset+buffer.position())>0){}
            if(buffer.hasRemaining())throw new IOException("The file could not be read.");
        }
        return Map.of("bytesBase64",Base64.getEncoder().encodeToString(bytes),"filename",entry.get("filename"),"totalBytes",size);
    }

    private static Map<String, Object> sale(Connection c, int customer, int store, int id) throws SQLException {
        var summaries = rows(c, """
                SELECT s.receipt_number,s.created_at,l.name AS store_name, s.user_name AS cashier,
                       s.status,s.payment_method,s.payment_status,s.payment_reference,
                       s.subtotal_amount,s.discount_amount,s.vat_amount,s.total_amount,s.amount_paid,s.returned_amount,
                       (SELECT a.representative_name FROM customer_charge_authorizations a
                        WHERE a.document_type='SALE' AND a.document_id=s.sale_id LIMIT 1) AS representative_name
                FROM sales s LEFT JOIN locations l ON l.location_id=s.location_id
                WHERE s.customer_id=? AND s.location_id=? AND s.sale_id=?
                """, customer, store, id);
        if (summaries.isEmpty()) return null;
        var items = rows(c, """
                SELECT coalesce(nullif(i.item_name,''),p.name,'Item') AS item_name,
                       coalesce(nullif(i.sku_snapshot,''),p.sku) AS sku,
                       i.size_snapshot,i.color_snapshot,i.quantity,i.original_unit_price,i.unit_price,
                       i.discount_amount,coalesce(i.loyalty_net_amount,i.quantity*i.unit_price) AS line_total
                FROM sale_items i LEFT JOIN products p ON p.product_id=i.product_id
                WHERE i.sale_id=? ORDER BY i.sale_item_id
                """, id);
        var result = new LinkedHashMap<String, Object>();
        result.put("summary", summaries.get(0));
        result.put("items", items);
        result.put("taskPaymentSummary", SalesTaskService.salePaymentSummary(c, id));
        result.put("payments",rows(c,"""
                SELECT t.created_at,t.payment_id,t.payment_method,t.payment_reference,a.amount
                FROM customer_account_payment_allocations a
                JOIN customer_account_transactions t ON t.transaction_id=a.payment_transaction_id
                WHERE a.sale_id=? AND a.customer_id=? AND t.customer_id=?
                ORDER BY t.created_at DESC,t.transaction_id DESC
                """,id,customer,customer));
        result.put("returns",rows(c,"""
                SELECT created_at,return_receipt_number,refund_method,refund_amount,user_name,reason
                FROM sale_returns WHERE sale_id=? ORDER BY created_at DESC,return_id DESC
                """,id));
        return result;
    }

    private static Map<String, Object> cachedSale(Connection c, int customer, int store, int id) throws SQLException {
        var summaries = rows(c, """
                SELECT receipt_number,source_created_at AS created_at,store_name,user_name AS cashier,
                       payment_method,payment_status,subtotal_amount,discount_amount,total_amount,amount_paid,returned_amount
                FROM sync_cross_store_sales_cache WHERE customer_id=? AND source_location_id=? AND sale_id=?
                """, customer, store, id);
        if (summaries.isEmpty()) return null;
        return Map.of("summary", summaries.get(0), "items", rows(c, """
                SELECT product_name AS item_name,sku,quantity,unit_price,original_unit_price,discount_amount,
                       quantity*unit_price AS line_total
                FROM sync_cross_store_sale_items_cache WHERE source_location_id=? AND sale_id=? ORDER BY sale_item_id
                """, store, id));
    }

    private static List<Map<String, Object>> rows(Connection c, String sql, Object... args) throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (var result = statement.executeQuery()) {
                var output = new ArrayList<Map<String, Object>>();
                var columns = result.getMetaData();
                while (result.next()) {
                    var row = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= columns.getColumnCount(); i++) {
                        Object value = result.getObject(i);
                        if (value instanceof Timestamp stamp) value = stamp.toInstant().toString();
                        row.put(columns.getColumnLabel(i), value);
                    }
                    output.add(row);
                }
                return output;
            }
        }
    }
}
