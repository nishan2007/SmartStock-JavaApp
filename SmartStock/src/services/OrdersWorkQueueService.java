package services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** A read-only, store-scoped view of unfinished production and service work. */
public final class OrdersWorkQueueService {
    private OrdersWorkQueueService() { }
    private static final Set<String> ALL_CUSTOM = Set.of("CREATE_CUSTOM_ORDER", "MANAGE_CUSTOM_ORDERS",
            "CUSTOM_ORDER_LOOKUP", "CUSTOM_ORDER_OVERRIDES", "ORDERS_MANAGER_DASHBOARD");

    public static Snapshot load(Connection c, int location, int user, Set<String> permissions, ZoneId zone) throws SQLException {
        boolean allCustom = permissions.stream().anyMatch(ALL_CUSTOM::contains);
        boolean assignedCustom = permissions.contains("VIEW_ASSIGNED_CUSTOM_ORDERS");
        boolean tasks = permissions.contains("MANAGE_SALES_TASKS");
        if (!allCustom && !assignedCustom && !tasks) throw new SQLException("You do not have permission to view the work queue.");
        List<Row> rows = new ArrayList<>();
        LocalDate today = LocalDate.now(zone);
        if (allCustom || assignedCustom) {
            String sql = """
                SELECT o.custom_order_id,o.order_number,o.status,o.customer_name,o.customer_phone,
                    COALESCE(o.assigned_to_name,''),o.due_date,o.pricing_state,o.balance_due,o.created_at,
                    concat_ws(E'\n',NULLIF(o.order_notes,''),
                        (SELECT string_agg(concat_ws(' — ',l.item_name,NULLIF(l.customization_details,''),
                            NULLIF(l.order_instructions,'')),E'\n' ORDER BY l.sort_order,l.custom_order_line_id)
                         FROM custom_order_lines l WHERE l.custom_order_id=o.custom_order_id)) AS instructions,
                    (SELECT COALESCE(NULLIF(v.image_url,''),NULLIF(i.image_url,''))
                     FROM custom_order_lines l LEFT JOIN custom_order_item_variants v ON v.custom_variant_id=l.custom_variant_id
                     LEFT JOIN custom_order_items i ON i.custom_item_id=l.custom_item_id
                     WHERE l.custom_order_id=o.custom_order_id AND COALESCE(NULLIF(v.image_url,''),NULLIF(i.image_url,'')) IS NOT NULL
                     ORDER BY l.sort_order,l.custom_order_line_id LIMIT 1) AS image_reference,
                    EXISTS(SELECT 1 FROM custom_order_lines l WHERE l.custom_order_id=o.custom_order_id
                        AND l.production_status='DESIGN_APPROVED') AS design_approved,o.payment_status
                FROM custom_orders o WHERE o.location_id=? AND o.order_type='CUSTOM'
                    AND o.status NOT IN ('DELIVERED','CANCELLED')
                """ + (allCustom ? "" : " AND o.assigned_to_user_id=?");
            try (var p=c.prepareStatement(sql)) {
                p.setInt(1,location);if(!allCustom)p.setInt(2,user);
                try(var r=p.executeQuery()) { while(r.next()) {
                    var due=r.getDate(7);boolean pending="PENDING".equals(r.getString(8));
                    rows.add(new Row("CUSTOM_ORDER",r.getLong(1),r.getString(2),r.getString(3),
                        r.getString(4),r.getString(5),r.getString(6),r.getString(11),
                        due==null?null:due.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(),true,
                        due!=null&&due.toLocalDate().isBefore(today),pending,pending?null:r.getBigDecimal(9),r.getTimestamp(10).getTime(),false,r.getString(12),r.getBoolean(13),"PARTIAL".equals(r.getString(14))));
                }}
            }
        }
        if (tasks) {
            try(var p=c.prepareStatement("""
                SELECT t.task_id,t.status,t.customer_name,t.phone,COALESCE(NULLIF(u.full_name,''),u.username,''),
                    t.instructions,t.due_at,t.billing_status,t.final_total-payments.paid AS balance,t.created_at,
                    t.due_at<CURRENT_TIMESTAMP AS overdue,t.due_asap,payments.paid
                FROM sales_tasks t LEFT JOIN users u ON u.user_id=t.assigned_user_id
                LEFT JOIN LATERAL (
                    SELECT COALESCE(sum(CASE WHEN p.payment_action='REFUND' THEN -p.amount ELSE p.amount END),0) AS paid
                    FROM sales_task_payments p WHERE p.task_id=t.task_id
                ) payments ON true
                WHERE t.location_id=? AND t.status NOT IN ('COLLECTED','CANCELLED')
                """)) {
                p.setInt(1,location);try(var r=p.executeQuery()){while(r.next()) {
                    var due=r.getTimestamp(7);boolean pending=!"FINALIZED".equals(r.getString(8));
                    rows.add(new Row("SALES_TASK",r.getLong(1),"ST-"+r.getLong(1),r.getString(2),r.getString(3),
                        r.getString(4),r.getString(5),r.getString(6),due==null?null:due.getTime(),false,
                        r.getBoolean(11),pending,pending?null:r.getBigDecimal(9),r.getTimestamp(10).getTime(),r.getBoolean(12),null,false,
                        !pending&&r.getBigDecimal(13).signum()>0&&r.getBigDecimal(9).signum()>0));
                }}
            }
        }
        rows.sort(Comparator.comparing(Row::asap).reversed().thenComparing(Row::dueEpochMillis,Comparator.nullsLast(Long::compareTo))
                .thenComparingLong(Row::createdEpochMillis).thenComparing(Row::kind).thenComparingLong(Row::id));
        return new Snapshot(List.copyOf(rows),zone.getId());
    }

    public record Snapshot(List<Row> rows,String timezone) { }
    public record Row(String kind,long id,String number,String status,String customer,String phone,String assigned,
                      String instructions,Long dueEpochMillis,boolean dateOnly,boolean overdue,boolean pricePending,
                      BigDecimal balance,long createdEpochMillis,boolean asap,String imageReference,boolean designApproved,boolean partPaid) {
        public Row(String kind,long id,String number,String status,String customer,String phone,String assigned,String instructions,Long dueEpochMillis,boolean dateOnly,boolean overdue,boolean pricePending,BigDecimal balance,long createdEpochMillis,boolean asap,String imageReference,boolean designApproved){this(kind,id,number,status,customer,phone,assigned,instructions,dueEpochMillis,dateOnly,overdue,pricePending,balance,createdEpochMillis,asap,imageReference,designApproved,false);}
        public Row(String kind,long id,String number,String status,String customer,String phone,String assigned,String instructions,Long dueEpochMillis,boolean dateOnly,boolean overdue,boolean pricePending,BigDecimal balance,long createdEpochMillis,boolean asap){this(kind,id,number,status,customer,phone,assigned,instructions,dueEpochMillis,dateOnly,overdue,pricePending,balance,createdEpochMillis,asap,null,false);}
        public Row(String kind,long id,String number,String status,String customer,String phone,String assigned,String instructions,Long dueEpochMillis,boolean dateOnly,boolean overdue,boolean pricePending,BigDecimal balance,long createdEpochMillis){this(kind,id,number,status,customer,phone,assigned,instructions,dueEpochMillis,dateOnly,overdue,pricePending,balance,createdEpochMillis,false);}
        public String stage() {
            if(Set.of("READY","COMPLETED").contains(status))return pricePending?"Awaiting pricing":"Ready for pickup";
            return switch(status){case "NEW","RECEIVED"->"Received";case "ASSIGNED"->"Assigned";case "IN_PROGRESS"->"In progress";default->status;};
        }
        public String type(){return "SALES_TASK".equals(kind)?"Sales Task":"Custom Order";}
    }
}
