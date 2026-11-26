package services;

import com.google.gson.*;
import managers.ServerTimeClockManager;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Browser adapter for the existing authoritative payroll journal. */
final class PayrollWebService {
    static final class Validation extends IllegalArgumentException {Validation(String message){super(message);}}
    private static final Gson JSON=LanJson.create();
    private PayrollWebService(){}
    static Object execute(Connection c,int actor,String name,UUID browser,String route,JsonObject body)throws Exception {
        return execute(c,actor,name,browser,route,body,Objects.requireNonNull(data.DatabaseConfig.load().locationId(),"Store is not configured."));
    }
    static Object execute(Connection c,int actor,String name,UUID browser,String route,JsonObject body,int current)throws Exception {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND u.is_active=TRUE AND UPPER(p.permission_key)='PAYROLL_DASHBOARD'")){p.setInt(1,actor);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Your role does not have Payroll Dashboard permission.");}}
        String storeName="",zone="America/Guyana";List<Map<String,Object>> stores=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT location_id,name,COALESCE(NULLIF(timezone,''),'America/Guyana') FROM locations ORDER BY name");var r=p.executeQuery()){
            while(r.next()){int id=r.getInt(1);stores.add(Map.of("id",id,"name",r.getString(2)));if(id==current){storeName=r.getString(2);zone=r.getString(3);}}
        }
        ServerTimeClockManager.bindRequest(actor,current,storeName,zone,name);
        try {
            if(route.equals("payroll/dashboard")) {
                var dashboard=ServerTimeClockManager.loadPayrollDashboard(c);
                var periods=dashboard.summaries().stream().map(row->row.payPeriodStart()+":"+row.payPeriodEnd()).distinct().sorted(Comparator.reverseOrder()).toList();
                String today=LocalDate.now(ZoneId.of(zone)).toString();
                String selected=body.has("period")?body.get("period").getAsString():periods.stream().filter(period->{String[] dates=period.split(":");return dates[0].compareTo(today)<=0&&dates[1].compareTo(today)>=0;}).findFirst().orElse(periods.isEmpty()?"":periods.get(0));
                if(!selected.isBlank()&&!periods.contains(selected))throw new Validation("This payroll period is no longer available. Refresh payroll.");
                var summaries=dashboard.summaries().stream().filter(row->(row.payPeriodStart()+":"+row.payPeriodEnd()).equals(selected)).toList();
                // Store filters employees, never creating a second balance for the same wages.
                var worked=new LinkedHashMap<String,Set<Integer>>();
                for(var row:dashboard.timeRows())if(row.locationId()!=null)worked.computeIfAbsent(key(row.userId(),row.payPeriodStart(),row.payPeriodEnd()),ignored->new TreeSet<>()).add(row.locationId());
                Set<Integer> employeeIds=new HashSet<>();summaries.forEach(row->employeeIds.add(row.userId()));
                var photos=new HashMap<Integer,String>();if(!employeeIds.isEmpty())try(var p=c.prepareStatement("SELECT user_id,employee_photo_url,employee_photo_frame FROM users WHERE user_id=ANY(?)")){p.setArray(1,c.createArrayOf("integer",employeeIds.toArray()));try(var r=p.executeQuery()){while(r.next())photos.put(r.getInt(1),EmployeeAccountService.thumbnail(r.getString(2),r.getString(3)!=null&&!r.getString(3).isBlank()));}}
                var rows=new ArrayList<Map<String,Object>>();
                for(var summary:summaries){
                    var row=new LinkedHashMap<String,Object>();row.put("userId",summary.userId());row.put("name",summary.employeeName());row.put("start",summary.payPeriodStart());row.put("end",summary.payPeriodEnd());row.put("hours",summary.totalHours());row.put("total",summary.totalPay());row.put("paid",summary.paidAmount());row.put("balance",summary.amountDue());row.put("photo",photos.getOrDefault(summary.userId(),""));
                    row.put("stores",worked.getOrDefault(key(summary.userId(),summary.payPeriodStart(),summary.payPeriodEnd()),summary.locationId()==null?Set.of():Set.of(summary.locationId())));rows.add(row);
                    row.put("cashBalance",ServerTimeClockManager.paymentAmountDue(summary.totalPay(),summary.paidAmount(),"CASH"));row.put("bankBalance",ServerTimeClockManager.paymentAmountDue(summary.totalPay(),summary.paidAmount(),"BANK"));
                }
                return Map.of("stores",stores,"rows",rows,"periods",periods,"selectedPeriod",selected,"today",today,"currentStore",current,"defaultStore",body.has("networkStoreId")?body.get("networkStoreId").getAsInt():0);
            }
            if(!route.equals("payroll/pay"))throw new Validation("Unknown payroll action.");
            UUID requestKey=UUID.fromString(body.get("idempotencyKey").getAsString());
            var canonical=body.deepCopy();canonical.remove("idempotencyKey");String hash=LanSecurity.sha256(JSON.toJson(canonical));
            boolean inserted;
            try(var p=c.prepareStatement("INSERT INTO payroll_web_requests(browser_id,user_id,request_key,request_hash) VALUES(?,?,?,?) ON CONFLICT DO NOTHING")){p.setObject(1,browser);p.setInt(2,actor);p.setObject(3,requestKey);p.setString(4,hash);inserted=p.executeUpdate()==1;}
            if(!inserted)try(var p=c.prepareStatement("SELECT request_hash,response::text FROM payroll_web_requests WHERE browser_id=? AND user_id=? AND request_key=? FOR UPDATE")){p.setObject(1,browser);p.setInt(2,actor);p.setObject(3,requestKey);try(var r=p.executeQuery()){if(!r.next()||!hash.equals(r.getString(1))||r.getString(2)==null)throw new Validation("This payment retry key was already used for a different request.");return JSON.fromJson(r.getString(2),Map.class);}}
            int user=body.get("userId").getAsInt();LocalDate start=LocalDate.parse(body.get("start").getAsString()),end=LocalDate.parse(body.get("end").getAsString()),date=LocalDate.parse(body.get("date").getAsString());
            BigDecimal amount=body.get("amount").getAsBigDecimal();
            if(amount.stripTrailingZeros().scale()>0||amount.signum()<=0||amount.compareTo(new BigDecimal("9999999999"))>0)throw new Validation("Enter a positive whole GYD payment below G$10 billion.");
            if(date.isAfter(LocalDate.now(ZoneId.of(zone))))throw new Validation("Payment date cannot be in the future.");
            ManualTimeClockService.lockEmployee(c,user);
            var summary=ServerTimeClockManager.loadPayrollDashboard(c).summaries().stream().filter(row->row.userId()==user&&row.payPeriodStart().equals(start)&&row.payPeriodEnd().equals(end)).findFirst().orElseThrow(()->new Validation("Payroll changed. Refresh this pay period."));
            if(!body.has("expectedPaid")||summary.paidAmount().compareTo(body.get("expectedPaid").getAsBigDecimal())!=0)throw new Validation("Another payment was recorded. Refresh before recording another payment.");
            String method=body.has("method")?body.get("method").getAsString():"CASH",reference=body.has("reference")?body.get("reference").getAsString():null;
            if(!Set.of("CASH","BANK").contains(method))throw new Validation("Choose cash in hand or bank account.");
            if("BANK".equals(method)&&(reference==null||reference.isBlank()||reference.length()>500))throw new Validation("Enter the bank payment reference (up to 500 characters).");
            BigDecimal available=ServerTimeClockManager.paymentAmountDue(summary.totalPay(),summary.paidAmount(),method);
            if("CASH".equals(method))available=available.max(summary.amountDue()); // Preserve an exact payment of the unrounded balance, too.
            if(amount.compareTo(available)>0)throw new Validation("The payment exceeds the remaining balance for this payment method.");
            ServerTimeClockManager.markPayrollPaid(c,summary,amount,method,reference,date);
            var result=Map.of("recorded",true,"amount",amount);
            try(var p=c.prepareStatement("UPDATE payroll_web_requests SET response=?::jsonb WHERE browser_id=? AND user_id=? AND request_key=?")){p.setString(1,JSON.toJson(result));p.setObject(2,browser);p.setInt(3,actor);p.setObject(4,requestKey);p.executeUpdate();}
            return result;
        } finally {ServerTimeClockManager.clearRequest();}
    }
    private static String key(int user,LocalDate start,LocalDate end){return user+":"+start+":"+end;}
}
