package services;

import data.DB;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class ReportDataService {
    private ReportDataService() {}

    public enum Bucket { HOUR, DAY, WEEK, MONTH }
    public record Option(int id, String label) { @Override public String toString() { return label; } }
    public record Filters(ZonedDateTime from, ZonedDateTime to, ZoneId zone, Integer locationId,
                          Set<Integer> products, Set<Integer> brands, Set<Integer> departments,
                          Set<Integer> itemTypes, Set<Integer> employees, Set<String> paymentMethods) {}
    public record Point(String label, BigDecimal value) {}
    public record Series(String name, List<Point> points) {}
    public record Rank(String label, BigDecimal amount, long quantity) {}
    public record SaleRow(int id, String receipt, LocalDateTime time, String employee, String payment,
                          String status, BigDecimal paid, BigDecimal total) {}
    public record ProductRow(String product, String brand, String department, String itemType,
                             long units, long returned, BigDecimal revenue, BigDecimal returnValue,
                             BigDecimal averagePrice, BigDecimal cost, BigDecimal profit) {}
    public record EmployeeRow(String employee, long transactions, long items, BigDecimal gross,
                              BigDecimal discounts, BigDecimal returns, BigDecimal net,
                              BigDecimal average, BigDecimal share) {}
    public record CashRow(LocalDateTime time, String direction, String source, String method,
                          String reference, BigDecimal amount) {}
    public record ExpenseRow(long id, LocalDate date, String category, String payee, String description,
                             String method, String status, String source, String creator, BigDecimal amount) {}
    public record BalanceRow(long id, LocalDate from, LocalDate to, LocalDateTime submittedAt,
                             String submitter, BigDecimal bf, BigDecimal income, BigDecimal expenses,
                             BigDecimal payables, BigDecimal cf) {}
    public record Snapshot(Map<String, BigDecimal> metrics, List<Series> revenueSeries,
                           List<Series> cashSeries, List<Rank> departments, List<Rank> brands,
                           List<Rank> products, List<Rank> employees, List<SaleRow> sales,
                           List<ProductRow> productRows, List<EmployeeRow> employeeRows,
                           List<CashRow> cashRows, List<ExpenseRow> expenses,
                           List<BalanceRow> balances) {}
    public record FilterOptions(List<Option> products, List<Option> brands, List<Option> departments,
                                List<Option> itemTypes, List<Option> employees, List<String> paymentMethods) {}

    public static Bucket bucket(Filters f) {
        long days = Math.max(1, Duration.between(f.from(), f.to()).toDays());
        if (days <= 1) return Bucket.HOUR;
        if (days <= 90) return Bucket.DAY;
        if (days <= 366) return Bucket.WEEK;
        return Bucket.MONTH;
    }

    public static FilterOptions loadOptions(Integer locationId) throws SQLException {
        try{return LanApiClient.loadReportOptions();}catch(Exception e){throw sql("Unable to load report options from the SmartStock server.",e);}
    }
    public static FilterOptions loadOptions(Connection c,Integer locationId)throws SQLException{
        return new FilterOptions(
                    options(c, "SELECT product_id, name FROM products WHERE is_active ORDER BY name"),
                    options(c, "SELECT brand_id, name FROM item_brands ORDER BY name"),
                    options(c, "SELECT category_id, name FROM categories ORDER BY name"),
                    options(c, "SELECT item_type_id, name FROM item_types ORDER BY name"),
                    options(c, "SELECT DISTINCT u.user_id, COALESCE(NULLIF(u.full_name,''),u.username) FROM users u " +
                            (locationId == null ? "" : "JOIN user_locations ul ON ul.user_id=u.user_id AND ul.location_id=" + locationId + " ") +
                            "WHERE u.is_active ORDER BY 2"),
                    List.of("CASH", "CARD", "CHEQUE", "MMG", "ACCOUNT")
            );
    }

    private static List<Option> options(Connection c, String sql) throws SQLException {
        List<Option> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) out.add(new Option(r.getInt(1), r.getString(2)));
        }
        return out;
    }

    public static Snapshot load(Filters f, boolean allRevenue, boolean includeAccounting) throws SQLException {
        try{return LanApiClient.loadReportSnapshot(f,allRevenue);}catch(Exception e){throw sql("Unable to load reports from the SmartStock server.",e);}
    }
    public static Snapshot load(Connection c,Filters f,boolean allRevenue,boolean includeAccounting)throws SQLException{
            List<SaleFact> facts = loadSaleFacts(c, f);
            List<SaleRow> sales = loadSales(c, f);
            List<ProductRow> productRows = productRows(facts);
            List<EmployeeRow> employeeRows = employeeRows(facts);
            List<CashRow> cash = loadCash(c, f, includeAccounting);
            List<ExpenseRow> expenses = includeAccounting ? loadExpenses(c, f) : List.of();
            List<BalanceRow> balances = includeAccounting ? loadBalances(c, f) : List.of();

            BigDecimal gross = facts.stream().map(x -> x.revenue.add(x.discount)).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal discounts = facts.stream().map(x -> x.discount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal returns = facts.stream().map(x -> x.returnValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal net = gross.subtract(discounts).subtract(returns);
            long items = facts.stream().mapToLong(x -> x.quantity - x.returned).sum();
            long transactions = sales.size();
            BigDecimal received = cash.stream().filter(x -> x.direction.equals("IN")).map(CashRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paidOut = cash.stream().filter(x -> x.direction.equals("OUT")).map(CashRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paidExpenses = expenses.stream().filter(x -> "PAID".equalsIgnoreCase(x.status)).map(ExpenseRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal latestCf = balances.isEmpty() ? BigDecimal.ZERO : balances.get(balances.size() - 1).cf;

            Map<String, BigDecimal> metrics = new LinkedHashMap<>();
            metrics.put("Gross Sales", gross); metrics.put("Discounts", discounts); metrics.put("Returns", returns);
            metrics.put("Net Sales", net); metrics.put("Money Received", received);
            metrics.put("Transactions", BigDecimal.valueOf(transactions)); metrics.put("Items Sold", BigDecimal.valueOf(items));
            metrics.put("Average Sale", transactions == 0 ? BigDecimal.ZERO : net.divide(BigDecimal.valueOf(transactions), 2, java.math.RoundingMode.HALF_UP));
            if (includeAccounting) metrics.put("Paid Expenses", paidExpenses);
            metrics.put("Net Cash Movement", received.subtract(paidOut));
            if (includeAccounting) metrics.put("Latest Balance C/F", latestCf);

            List<Series> revenue = new ArrayList<>();
            revenue.add(new Series("POS Sales", groupFacts(facts, f)));
            if (allRevenue) {
                revenue.add(new Series("Custom Orders", loadPaymentSeries(c, f, "custom_order_payments", "payment_amount", "custom_order_id", "payment_action", null)));
                revenue.add(new Series("Invoices", loadPaymentSeries(c, f, "invoice_payments", "payment_amount", "invoice_id", "payment_action", "voided_at IS NULL")));
            }
            return new Snapshot(metrics, revenue, cashSeries(cash, f),
                    rank(productRows, "department"), rank(productRows, "brand"), rank(productRows, "product"),
                    rankEmployees(employeeRows), sales, productRows, employeeRows, cash, expenses, balances);
    }

    private record SaleFact(int saleId, int productId, String product, String brand, String department,
                            String itemType, int employeeId, String employee, String payment, LocalDateTime time,
                            long quantity, long returned, BigDecimal revenue, BigDecimal discount,
                            BigDecimal returnValue, BigDecimal cost) {}

    private static List<SaleFact> loadSaleFacts(Connection c, Filters f) throws SQLException {
        StringBuilder q = new StringBuilder("""
            SELECT s.sale_id, COALESCE(p.product_id,0), COALESCE(p.name,'Unknown Product'),
              COALESCE(b.name,'Unassigned'), COALESCE(d.name,'Unassigned'), COALESCE(it.name,'Unassigned'),
              COALESCE(s.user_id,0), COALESCE(NULLIF(s.user_name,''),u.full_name,u.username,'Unknown'),
              COALESCE(s.payment_method,'UNKNOWN'), s.created_at AT TIME ZONE ?,
              si.quantity, COALESCE(SUM(sri.quantity),0),
              (si.quantity * si.unit_price - COALESCE(si.discount_amount,0)),
              COALESCE(si.discount_amount,0), COALESCE(SUM(sri.quantity*sri.unit_price),0),
              (si.quantity * COALESCE(p.cost_price,0))
            FROM sales s JOIN sale_items si ON si.sale_id=s.sale_id
            LEFT JOIN products p ON p.product_id=si.product_id LEFT JOIN item_brands b ON b.brand_id=p.brand_id
            LEFT JOIN categories d ON d.category_id=p.category_id LEFT JOIN item_types it ON it.item_type_id=p.item_type_id
            LEFT JOIN users u ON u.user_id=s.user_id LEFT JOIN sale_return_items sri ON sri.sale_item_id=si.sale_item_id
            LEFT JOIN sale_returns sr ON sr.return_id=sri.return_id AND sr.created_at>=? AND sr.created_at<?
            WHERE s.created_at>=? AND s.created_at<? AND COALESCE(s.status,'COMPLETED')='COMPLETED'
            """);
        List<Object> p = new ArrayList<>(List.of(f.zone.getId(), Timestamp.from(f.from.toInstant()),
                Timestamp.from(f.to.toInstant()), Timestamp.from(f.from.toInstant()), Timestamp.from(f.to.toInstant())));
        filter(q,p,"s.location_id",f.locationId()==null?Set.of():Set.of(f.locationId()));
        filter(q,p,"si.product_id",f.products()); filter(q,p,"p.brand_id",f.brands());
        filter(q,p,"p.category_id",f.departments()); filter(q,p,"p.item_type_id",f.itemTypes());
        filter(q,p,"s.user_id",f.employees()); filterStrings(q,p,"s.payment_method",f.paymentMethods());
        q.append(" GROUP BY s.sale_id,p.product_id,p.name,b.name,d.name,it.name,s.user_id,s.user_name,u.full_name,u.username,s.payment_method,s.created_at,si.sale_item_id,si.quantity,si.unit_price,si.discount_amount,p.cost_price ORDER BY s.created_at");
        List<SaleFact> out = new ArrayList<>();
        try (PreparedStatement ps=c.prepareStatement(q.toString())) { bind(ps,p); try(ResultSet r=ps.executeQuery()) {
            while(r.next()) out.add(new SaleFact(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),
                    r.getInt(7),r.getString(8),r.getString(9),r.getTimestamp(10).toLocalDateTime(),r.getLong(11),r.getLong(12),
                    z(r.getBigDecimal(13)),z(r.getBigDecimal(14)),z(r.getBigDecimal(15)),z(r.getBigDecimal(16))));
        }} return out;
    }

    private static List<SaleRow> loadSales(Connection c, Filters f) throws SQLException {
        StringBuilder q=new StringBuilder("""
            SELECT DISTINCT s.sale_id,COALESCE(s.receipt_number,''),s.created_at AT TIME ZONE ?,
              COALESCE(NULLIF(s.user_name,''),u.full_name,u.username,'Unknown'),COALESCE(s.payment_method,''),
              COALESCE(s.payment_status,''),COALESCE(s.amount_paid,0),COALESCE(s.total_amount,0)
            FROM sales s LEFT JOIN users u ON u.user_id=s.user_id JOIN sale_items si ON si.sale_id=s.sale_id
            LEFT JOIN products p ON p.product_id=si.product_id WHERE s.created_at>=? AND s.created_at<?
            """);
        List<Object> p=base(f); filter(q,p,"s.location_id",f.locationId()==null?Set.of():Set.of(f.locationId()));
        filter(q,p,"si.product_id",f.products()); filter(q,p,"p.brand_id",f.brands()); filter(q,p,"p.category_id",f.departments());
        filter(q,p,"p.item_type_id",f.itemTypes()); filter(q,p,"s.user_id",f.employees()); filterStrings(q,p,"s.payment_method",f.paymentMethods());
        q.append(" ORDER BY 3");
        List<SaleRow> out=new ArrayList<>(); try(PreparedStatement ps=c.prepareStatement(q.toString())){bind(ps,p);try(ResultSet r=ps.executeQuery()){
            while(r.next())out.add(new SaleRow(r.getInt(1),r.getString(2),r.getTimestamp(3).toLocalDateTime(),r.getString(4),r.getString(5),r.getString(6),z(r.getBigDecimal(7)),z(r.getBigDecimal(8))));
        }}return out;
    }

    private static List<ProductRow> productRows(List<SaleFact> facts) {
        record K(String p,String b,String d,String i){} record A(long q,long r,BigDecimal rev,BigDecimal ret,BigDecimal cost){}
        Map<K,A> m=new LinkedHashMap<>();
        for(SaleFact x:facts){K k=new K(x.product,x.brand,x.department,x.itemType);A a=m.getOrDefault(k,new A(0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
            m.put(k,new A(a.q+x.quantity,a.r+x.returned,a.rev.add(x.revenue),a.ret.add(x.returnValue),a.cost.add(x.cost)));}
        List<ProductRow> out=new ArrayList<>();m.forEach((k,a)->{long sold=Math.max(0,a.q-a.r);BigDecimal net=a.rev.subtract(a.ret);
            out.add(new ProductRow(k.p,k.b,k.d,k.i,sold,a.r,net,a.ret,sold==0?BigDecimal.ZERO:net.divide(BigDecimal.valueOf(sold),2,java.math.RoundingMode.HALF_UP),a.cost,net.subtract(a.cost)));});
        out.sort(Comparator.comparing(ProductRow::revenue).reversed());return out;
    }

    private static List<EmployeeRow> employeeRows(List<SaleFact> facts) {
        record A(Set<Integer>s,long items,BigDecimal g,BigDecimal d,BigDecimal r){} Map<String,A> m=new LinkedHashMap<>();
        for(SaleFact x:facts){A a=m.getOrDefault(x.employee,new A(new HashSet<>(),0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));a.s.add(x.saleId);
            m.put(x.employee,new A(a.s,a.items+Math.max(0,x.quantity-x.returned),a.g.add(x.revenue).add(x.discount),a.d.add(x.discount),a.r.add(x.returnValue)));}
        BigDecimal all=facts.stream().map(x->x.revenue.subtract(x.returnValue)).reduce(BigDecimal.ZERO,BigDecimal::add);List<EmployeeRow> out=new ArrayList<>();
        m.forEach((n,a)->{BigDecimal net=a.g.subtract(a.d).subtract(a.r);out.add(new EmployeeRow(n,a.s.size(),a.items,a.g,a.d,a.r,net,a.s.isEmpty()?BigDecimal.ZERO:net.divide(BigDecimal.valueOf(a.s.size()),2,java.math.RoundingMode.HALF_UP),all.signum()==0?BigDecimal.ZERO:net.multiply(BigDecimal.valueOf(100)).divide(all,2,java.math.RoundingMode.HALF_UP)));});
        out.sort(Comparator.comparing(EmployeeRow::net).reversed());return out;
    }

    private static List<CashRow> loadCash(Connection c, Filters f, boolean includeAccounting) throws SQLException {
        List<CashRow> out=new ArrayList<>();
        cashQuery(c,f,out,"SELECT created_at, 'IN','POS',payment_method,COALESCE(payment_reference,receipt_number,''),amount_paid FROM sales WHERE created_at>=? AND created_at<? AND payment_method<>'ACCOUNT' AND amount_paid>0","location_id");
        cashQuery(c,f,out,"SELECT p.created_at,CASE WHEN p.payment_action IN ('REFUND','REVERSAL') THEN 'OUT' ELSE 'IN' END,'CUSTOM ORDER',p.payment_method,COALESCE(p.payment_reference,''),p.payment_amount FROM custom_order_payments p JOIN custom_orders o ON o.custom_order_id=p.custom_order_id WHERE p.created_at>=? AND p.created_at<? AND p.payment_method<>'ACCOUNT'","o.location_id");
        cashQuery(c,f,out,"SELECT created_at,CASE WHEN payment_action IN ('REFUND','REVERSAL') THEN 'OUT' ELSE 'IN' END,'INVOICE',payment_method,COALESCE(payment_reference,''),payment_amount FROM invoice_payments WHERE created_at>=? AND created_at<? AND voided_at IS NULL AND payment_method<>'ACCOUNT'","location_id");
        cashQuery(c,f,out,"SELECT created_at,'IN','ACCOUNT',COALESCE(payment_method,'ACCOUNT'),COALESCE(payment_reference,payment_id,''),ABS(amount) FROM customer_account_transactions WHERE created_at>=? AND created_at<? AND transaction_type='PAYMENT'","location_id");
        cashQuery(c,f,out,"SELECT deposited_at,'IN','CHEQUE DEPOSIT','CHEQUE',COALESCE(payment_reference,''),amount FROM cheque_bank_deposits WHERE deposited_at>=? AND deposited_at<?","location_id");
        if (includeAccounting) {
            cashDateQuery(c,f,out,"SELECT transaction_date,CASE WHEN transaction_direction='PAID' THEN 'OUT' ELSE 'IN' END,'BANK',CASE WHEN transaction_direction='PAID' THEN 'BANK PAID' ELSE 'BANK RECEIVED' END,COALESCE(payment_reference,''),amount FROM bank_transactions WHERE transaction_date>=? AND transaction_date<?","location_id");
            cashDateQuery(c,f,out,"SELECT expense_date,'OUT','EXPENSE',COALESCE(payment_method,'UNKNOWN'),COALESCE(payment_reference,''),amount FROM expenses WHERE expense_date>=? AND expense_date<? AND status='PAID'","location_id");
        }
        cashQuery(c,f,out,"SELECT created_at,'OUT','SALE REFUND',COALESCE(refund_method,'UNKNOWN'),'Sale #'||sale_id,refund_amount FROM sale_returns WHERE created_at>=? AND created_at<?","location_id");
        if (f.paymentMethods() != null && !f.paymentMethods().isEmpty()) {
            out.removeIf(row -> !f.paymentMethods().contains(row.method().toUpperCase()));
        }
        out.sort(Comparator.comparing(CashRow::time));return out;
    }

    private static void cashQuery(Connection c,Filters f,List<CashRow> out,String sql,String location) throws SQLException {
        String q=sql+(f.locationId()==null?"":" AND "+location+"=?");try(PreparedStatement ps=c.prepareStatement(q)){ps.setTimestamp(1,Timestamp.from(f.from.toInstant()));ps.setTimestamp(2,Timestamp.from(f.to.toInstant()));if(f.locationId()!=null)ps.setInt(3,f.locationId());try(ResultSet r=ps.executeQuery()){while(r.next())out.add(new CashRow(r.getTimestamp(1).toInstant().atZone(f.zone).toLocalDateTime(),r.getString(2),r.getString(3),r.getString(4),r.getString(5),z(r.getBigDecimal(6))));}}
    }
    private static void cashDateQuery(Connection c,Filters f,List<CashRow> out,String sql,String location) throws SQLException {
        String q=sql+(f.locationId()==null?"":" AND "+location+"=?");try(PreparedStatement ps=c.prepareStatement(q)){ps.setDate(1,java.sql.Date.valueOf(f.from.toLocalDate()));ps.setDate(2,java.sql.Date.valueOf(f.to.toLocalDate()));if(f.locationId()!=null)ps.setInt(3,f.locationId());try(ResultSet r=ps.executeQuery()){while(r.next())out.add(new CashRow(r.getDate(1).toLocalDate().atStartOfDay(),r.getString(2),r.getString(3),r.getString(4),r.getString(5),z(r.getBigDecimal(6))));}}
    }

    private static List<ExpenseRow> loadExpenses(Connection c,Filters f)throws SQLException{
        String q="SELECT expense_id,expense_date,category,COALESCE(payee,''),COALESCE(description,''),COALESCE(payment_method,''),status,COALESCE(source_type,''),COALESCE(created_by_name,''),amount FROM expenses WHERE expense_date>=? AND expense_date<?"+(f.locationId()==null?"":" AND location_id=?")+" ORDER BY expense_date";
        List<ExpenseRow>o=new ArrayList<>();try(PreparedStatement p=c.prepareStatement(q)){p.setDate(1,java.sql.Date.valueOf(f.from.toLocalDate()));p.setDate(2,java.sql.Date.valueOf(f.to.toLocalDate()));if(f.locationId()!=null)p.setInt(3,f.locationId());try(ResultSet r=p.executeQuery()){while(r.next())o.add(new ExpenseRow(r.getLong(1),r.getDate(2).toLocalDate(),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),z(r.getBigDecimal(10))));}}return o;
    }
    private static List<BalanceRow> loadBalances(Connection c,Filters f)throws SQLException{
        String q="SELECT balance_sheet_submission_id,period_start,period_end,submitted_at,COALESCE(submitted_by_name,''),balance_bf,total_income,total_expenses,total_payables,balance_cf FROM balance_sheet_submissions WHERE period_end>=? AND period_start<?"+(f.locationId()==null?"":" AND location_id=?")+" ORDER BY period_end,submitted_at";
        List<BalanceRow>o=new ArrayList<>();try(PreparedStatement p=c.prepareStatement(q)){p.setDate(1,java.sql.Date.valueOf(f.from.toLocalDate()));p.setDate(2,java.sql.Date.valueOf(f.to.toLocalDate()));if(f.locationId()!=null)p.setInt(3,f.locationId());try(ResultSet r=p.executeQuery()){while(r.next())o.add(new BalanceRow(r.getLong(1),r.getDate(2).toLocalDate(),r.getDate(3).toLocalDate(),r.getTimestamp(4).toLocalDateTime(),r.getString(5),z(r.getBigDecimal(6)),z(r.getBigDecimal(7)),z(r.getBigDecimal(8)),z(r.getBigDecimal(9)),z(r.getBigDecimal(10))));}}return o;
    }

    private static List<Point> groupFacts(List<SaleFact> f,Filters x){Map<LocalDateTime,BigDecimal>m=new TreeMap<>();for(SaleFact a:f)m.merge(key(a.time,bucket(x)),a.revenue.subtract(a.returnValue),BigDecimal::add);return points(m,bucket(x));}
    private static List<Series> cashSeries(List<CashRow> rows,Filters f){
        Map<LocalDateTime,BigDecimal>in=new TreeMap<>(),out=new TreeMap<>(),net=new TreeMap<>();
        for(CashRow r:rows){LocalDateTime k=key(r.time,bucket(f));(r.direction.equals("IN")?in:out).merge(k,r.amount,BigDecimal::add);net.merge(k,r.direction.equals("IN")?r.amount:r.amount.negate(),BigDecimal::add);}
        BigDecimal running=BigDecimal.ZERO;Map<LocalDateTime,BigDecimal>cumulative=new TreeMap<>();
        for(var e:net.entrySet()){running=running.add(e.getValue());cumulative.put(e.getKey(),running);}
        return List.of(new Series("Cash In",points(in,bucket(f))),new Series("Cash Out",points(out,bucket(f))),new Series("Cumulative Net",points(cumulative,bucket(f))));
    }
    private static List<Point> loadPaymentSeries(Connection c,Filters f,String table,String amount,String id,String action,String extra)throws SQLException{
        String locationFilter = "";
        if (f.locationId() != null) {
            locationFilter = table.equals("custom_order_payments")
                    ? " AND custom_order_id IN (SELECT custom_order_id FROM custom_orders WHERE location_id=?)"
                    : " AND location_id=?";
        }
        String q="SELECT created_at AT TIME ZONE ?, "+amount+",COALESCE("+action+",'PAYMENT') FROM "+table+" WHERE created_at>=? AND created_at<?"+(extra==null?"":" AND "+extra)+locationFilter;
        Map<LocalDateTime,BigDecimal>m=new TreeMap<>();
        try(PreparedStatement p=c.prepareStatement(q)){
            p.setString(1,f.zone.getId());p.setTimestamp(2,Timestamp.from(f.from.toInstant()));p.setTimestamp(3,Timestamp.from(f.to.toInstant()));
            if (f.locationId() != null) p.setInt(4, f.locationId());
            try(ResultSet r=p.executeQuery()){
                while(r.next()){BigDecimal v=z(r.getBigDecimal(2));if(Set.of("REFUND","REVERSAL").contains(r.getString(3).toUpperCase()))v=v.negate();m.merge(key(r.getTimestamp(1).toLocalDateTime(),bucket(f)),v,BigDecimal::add);}
            }
        }
        return points(m,bucket(f));
    }
    private static LocalDateTime key(LocalDateTime t,Bucket b){return switch(b){case HOUR->t.withMinute(0).withSecond(0).withNano(0);case DAY->t.toLocalDate().atStartOfDay();case WEEK->t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();case MONTH->t.withDayOfMonth(1).toLocalDate().atStartOfDay();};}
    private static List<Point> points(Map<LocalDateTime,BigDecimal>m,Bucket b){return m.entrySet().stream().map(e->new Point(switch(b){case HOUR->e.getKey().toLocalTime().toString();case DAY->e.getKey().toLocalDate().toString();case WEEK->"Week "+e.getKey().toLocalDate();case MONTH->e.getKey().getYear()+"-"+String.format("%02d",e.getKey().getMonthValue());},e.getValue())).toList();}
    private static List<Rank> rank(List<ProductRow>r,String type){Map<String,Rank>m=new HashMap<>();for(ProductRow x:r){String k=switch(type){case"brand"->x.brand;case"department"->x.department;default->x.product;};Rank a=m.getOrDefault(k,new Rank(k,BigDecimal.ZERO,0));m.put(k,new Rank(k,a.amount.add(x.revenue),a.quantity+x.units));}return m.values().stream().sorted(Comparator.comparing(Rank::amount).reversed()).limit(10).toList();}
    private static List<Rank> rankEmployees(List<EmployeeRow>r){return r.stream().limit(10).map(x->new Rank(x.employee,x.net,x.items)).toList();}
    private static List<Object> base(Filters f){return new ArrayList<>(List.of(f.zone.getId(),Timestamp.from(f.from.toInstant()),Timestamp.from(f.to.toInstant())));}
    private static void filter(StringBuilder q,List<Object>p,String col,Set<Integer>s){if(s==null||s.isEmpty())return;q.append(" AND ").append(col).append(" IN (").append("?,".repeat(s.size()),0,s.size()*2-1).append(")");p.addAll(s);}
    private static void filterStrings(StringBuilder q,List<Object>p,String col,Set<String>s){if(s==null||s.isEmpty())return;q.append(" AND UPPER(").append(col).append(") IN (").append("?,".repeat(s.size()),0,s.size()*2-1).append(")");p.addAll(s);}
    private static void bind(PreparedStatement p,List<Object>v)throws SQLException{for(int i=0;i<v.size();i++)p.setObject(i+1,v.get(i));}
    private static BigDecimal z(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
    private static SQLException sql(String message,Exception cause){return new SQLException(message+" "+cause.getMessage(),cause);}
}
