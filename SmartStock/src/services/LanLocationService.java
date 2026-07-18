package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.sql.*;
import java.time.ZoneId;
import java.util.*;

/** Server-only location and store-email configuration. */
final class LanLocationService{
    private static final Gson GSON=LanJson.create();private LanLocationService(){}
    static Map<String,Object>list(Connection c,JsonObject b,int userId)throws Exception{require(c,userId);String search=text(b,"search");List<Map<String,Object>>rows=new ArrayList<>();
        String sql="""
                SELECT location_id,name,COALESCE(receipt_store_code,'0001'),COALESCE(address,''),COALESCE(company_address_line1,''),
                  COALESCE(company_address_line2,''),COALESCE(company_address_line3,''),COALESCE(company_phone_line1,''),COALESCE(company_phone_line2,''),
                  COALESCE(company_email_line1,''),COALESCE(company_email_line2,''),COALESCE(email_sender_address,''),COALESCE(email_sender_name,''),
                  COALESCE(email_bcc_address,''),COALESCE(balance_sheet_recipient_email,''),COALESCE(email_receipts_enabled,FALSE),
                  COALESCE(email_order_confirmations_enabled,FALSE),COALESCE(email_quotes_enabled,FALSE),COALESCE(email_invoices_enabled,FALSE),
                  COALESCE(email_delivery_bills_enabled,FALSE),COALESCE(timezone,'America/New_York') FROM locations
                WHERE (?='' OR name ILIKE ? OR COALESCE(address,'') ILIKE ? OR COALESCE(company_phone_line1,'') ILIKE ? OR COALESCE(company_email_line1,'') ILIKE ? OR location_id::text LIKE ?)
                ORDER BY location_id
                """;try(PreparedStatement ps=c.prepareStatement(sql)){String p="%"+search+"%";ps.setString(1,search);for(int i=2;i<=6;i++)ps.setString(i,p);try(ResultSet r=ps.executeQuery()){while(r.next())rows.add(row(r));}}return map("locations",rows);}
    static Map<String,Object>save(Connection c,JsonObject b,int userId)throws Exception{require(c,userId);Request r=parse(b);String name=required(r.name(),"Location name is required.");String code=required(r.storeCode(),"Store code is required.");
        try{ZoneId.of(required(r.timezone(),"Timezone is required."));}catch(Exception e){throw rule(400,"VALIDATION_ERROR","Enter a valid timezone.");}
        Integer id=r.locationId();String sql=id==null?"""
                INSERT INTO locations(name,receipt_store_code,address,company_address_line1,company_address_line2,company_address_line3,company_phone_line1,company_phone_line2,
                  company_email_line1,company_email_line2,email_sender_address,email_sender_name,email_bcc_address,balance_sheet_recipient_email,email_receipts_enabled,
                  email_order_confirmations_enabled,email_quotes_enabled,email_invoices_enabled,email_delivery_bills_enabled,timezone)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING location_id
                """:"""
                UPDATE locations SET name=?,receipt_store_code=?,address=?,company_address_line1=?,company_address_line2=?,company_address_line3=?,company_phone_line1=?,company_phone_line2=?,
                  company_email_line1=?,company_email_line2=?,email_sender_address=?,email_sender_name=?,email_bcc_address=?,balance_sheet_recipient_email=?,email_receipts_enabled=?,
                  email_order_confirmations_enabled=?,email_quotes_enabled=?,email_invoices_enabled=?,email_delivery_bills_enabled=?,timezone=? WHERE location_id=? RETURNING location_id
                """;try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,name);ps.setString(2,code);ps.setString(3,blank(r.address()));ps.setString(4,text(r.addressLine1()));ps.setString(5,text(r.addressLine2()));ps.setString(6,text(r.addressLine3()));
            ps.setString(7,text(r.phoneLine1()));ps.setString(8,text(r.phoneLine2()));ps.setString(9,text(r.emailLine1()));ps.setString(10,text(r.emailLine2()));ps.setString(11,text(r.senderEmail()));ps.setString(12,text(r.senderName()));
            ps.setString(13,text(r.bccEmail()));ps.setString(14,text(r.balanceSheetEmail()));ps.setBoolean(15,r.emailReceipts());ps.setBoolean(16,r.emailOrders());ps.setBoolean(17,r.emailQuotes());ps.setBoolean(18,r.emailInvoices());ps.setBoolean(19,r.emailDelivery());ps.setString(20,r.timezone());if(id!=null)ps.setInt(21,id);
            try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Location could not be saved.");id=rs.getInt(1);}}return map("locationId",id);}
    static Map<String,Object>processEmail(Connection c,int userId)throws Exception{require(c,userId);List<ServerEmailOutboxService.SendResult>results=ServerEmailOutboxService.processQueued(25);long sent=results.stream().filter(x->"SENT".equals(x.status())).count(),failed=results.stream().filter(x->"FAILED".equals(x.status())).count(),skipped=results.stream().filter(x->"SKIPPED".equals(x.status())).count();return map("processed",results.size(),"sent",sent,"failed",failed,"skipped",skipped);}
    private static Map<String,Object>row(ResultSet r)throws SQLException{return map("locationId",r.getInt(1),"name",r.getString(2),"storeCode",r.getString(3),"address",r.getString(4),"addressLine1",r.getString(5),"addressLine2",r.getString(6),"addressLine3",r.getString(7),"phoneLine1",r.getString(8),"phoneLine2",r.getString(9),"emailLine1",r.getString(10),"emailLine2",r.getString(11),"senderEmail",r.getString(12),"senderName",r.getString(13),"bccEmail",r.getString(14),"balanceSheetEmail",r.getString(15),"emailReceipts",r.getBoolean(16),"emailOrders",r.getBoolean(17),"emailQuotes",r.getBoolean(18),"emailInvoices",r.getBoolean(19),"emailDelivery",r.getBoolean(20),"timezone",r.getString(21));}
    private static void require(Connection c,int userId)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)='LOCATION_MANAGEMENT' LIMIT 1")){ps.setInt(1,userId);try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}}throw rule(403,"PERMISSION_DENIED","You do not have permission to manage locations.");}
    private static Request parse(JsonObject b)throws RuleViolation{try{return GSON.fromJson(b,Request.class);}catch(Exception e){throw rule(400,"VALIDATION_ERROR","Location details are invalid.");}}
    private static String text(JsonObject b,String k){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString().trim():"";}private static String text(String s){return s==null?"":s.trim();}private static String blank(String s){s=text(s);return s.isBlank()?null:s;}private static String required(String s,String m)throws RuleViolation{s=text(s);if(s.isBlank())throw rule(400,"VALIDATION_ERROR",m);return s;}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}
    private record Request(Integer locationId,String name,String storeCode,String address,String addressLine1,String addressLine2,String addressLine3,String phoneLine1,String phoneLine2,String emailLine1,String emailLine2,String senderEmail,String senderName,String bccEmail,String balanceSheetEmail,boolean emailReceipts,boolean emailOrders,boolean emailQuotes,boolean emailInvoices,boolean emailDelivery,String timezone){}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
