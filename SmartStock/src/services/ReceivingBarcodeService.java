package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only catalog mutation used by the Receiving Inventory screen. */
final class ReceivingBarcodeService {
    private ReceivingBarcodeService() { }

    static Map<String,Object> add(Connection c,String itemType,int itemId,String rawBarcode,
                                  UUID deviceId,int userId)throws Exception{
        requirePermission(c,userId,"RECEIVING_INVENTORY");
        requirePermission(c,userId,"EDIT_ITEM");
        String type=itemType==null?"":itemType.trim().toUpperCase();
        if(!List.of("PRODUCT","CUSTOM_ITEM","CUSTOM_VARIANT").contains(type)||itemId<=0)
            throw rule(400,"VALIDATION_ERROR","Choose a valid receiving item.");
        String barcode=BarcodeNormalizer.normalize(rawBarcode);
        if(barcode.isBlank())throw rule(400,"VALIDATION_ERROR","Scan or enter a barcode first.");
        if(barcode.length()>200)throw rule(400,"VALIDATION_ERROR","The barcode is too long.");

        Target target=lockTarget(c,type,itemId);
        if(containsOnTarget(c,target,itemId,barcode))
            throw rule(409,"BARCODE_ALREADY_ON_ITEM","This barcode is already assigned to the selected item.");
        try{CatalogBarcodeService.requireAvailable(c,List.of(barcode),
                "PRODUCT".equals(type)?itemId:null,"CUSTOM_ITEM".equals(type)?(long)itemId:null,
                "CUSTOM_VARIANT".equals(type)?(long)itemId:null);}
        catch(CatalogBarcodeService.ConflictException ex){throw rule(409,"BARCODE_EXISTS",ex.getMessage());}

        String destination;
        if(target.primary().isBlank()){
            try(PreparedStatement ps=c.prepareStatement(target.primaryUpdate())){
                ps.setString(1,barcode);ps.setInt(2,itemId);
                if(ps.executeUpdate()!=1)throw rule(404,"ITEM_NOT_FOUND","The selected item no longer exists.");
            }
            destination="PRIMARY";
        }else{
            try(PreparedStatement ps=c.prepareStatement(target.additionalInsert())){
                ps.setInt(1,itemId);ps.setString(2,barcode);ps.executeUpdate();
            }
            destination="ADDITIONAL";
        }
        audit(c,deviceId,userId,type,itemId,barcode,destination);
        return map("itemType",type,"itemId",itemId,"barcode",barcode,"destination",destination);
    }

    private static Target lockTarget(Connection c,String type,int id)throws Exception{
        String sql=switch(type){
            case "PRODUCT"->"SELECT COALESCE(barcode,'') FROM products WHERE product_id=? AND COALESCE(product_type,'INVENTORY')='INVENTORY' AND is_active=TRUE FOR UPDATE";
            case "CUSTOM_ITEM"->"SELECT COALESCE(barcode,'') FROM custom_order_items WHERE custom_item_id=? AND is_active=TRUE AND COALESCE(product_type,'INVENTORY')='INVENTORY' AND COALESCE(has_variants,FALSE)=FALSE FOR UPDATE";
            default->"SELECT COALESCE(v.barcode,'') FROM custom_order_item_variants v JOIN custom_order_items i ON i.custom_item_id=v.custom_item_id WHERE v.custom_variant_id=? AND v.is_active=TRUE AND i.is_active=TRUE AND COALESCE(i.product_type,'INVENTORY')='INVENTORY' FOR UPDATE OF v";
        };
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){
            if(!rs.next())throw rule(404,"ITEM_NOT_FOUND","The selected receiving item no longer exists.");
            return switch(type){
                case "PRODUCT"->new Target(type,rs.getString(1),"UPDATE products SET barcode=?,updated_at=CURRENT_TIMESTAMP WHERE product_id=?","INSERT INTO product_barcodes(product_id,barcode) VALUES(?,?)","product_barcodes","product_id");
                case "CUSTOM_ITEM"->new Target(type,rs.getString(1),"UPDATE custom_order_items SET barcode=?,updated_at=CURRENT_TIMESTAMP WHERE custom_item_id=?","INSERT INTO custom_order_item_barcodes(custom_item_id,barcode) VALUES(?,?)","custom_order_item_barcodes","custom_item_id");
                default->new Target(type,rs.getString(1),"UPDATE custom_order_item_variants SET barcode=?,updated_at=CURRENT_TIMESTAMP WHERE custom_variant_id=?","INSERT INTO custom_order_item_variant_barcodes(custom_variant_id,barcode) VALUES(?,?)","custom_order_item_variant_barcodes","custom_variant_id");
            };
        }}
    }

    private static boolean containsOnTarget(Connection c,Target target,int itemId,String barcode)throws SQLException{
        for(String candidate:BarcodeNormalizer.lookupCandidates(barcode)){
            if(BarcodeNormalizer.lookupCandidates(target.primary()).contains(candidate))return true;
            String sql="SELECT 1 FROM "+target.additionalTable()+" WHERE "+target.idColumn()+"=? AND UPPER(REGEXP_REPLACE(COALESCE(barcode,''), '[\\s-]+', '', 'g'))=? LIMIT 1";
            try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,itemId);ps.setString(2,candidate);try(ResultSet rs=ps.executeQuery()){if(rs.next())return true;}}
        }
        return false;
    }

    private static void requirePermission(Connection c,int userId,String permission)throws Exception{
        try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){
            ps.setInt(1,userId);ps.setString(2,permission);try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())throw rule(403,"PERMISSION_DENIED","You need Receiving Inventory and Edit Item permissions to add barcodes here.");
            }
        }
    }
    private static void audit(Connection c,UUID device,int user,String type,int item,String barcode,String destination)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES(?,?,?,?)")){
            ps.setString(1,"RECEIVING_BARCODE_ADDED");ps.setObject(2,device);ps.setInt(3,user);
            ps.setString(4,"item_type="+type+"; item_id="+item+"; destination="+destination+"; barcode="+barcode);ps.executeUpdate();
        }
    }
    private static Map<String,Object> map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    private static RuleViolation rule(int status,String code,String message){return new RuleViolation(status,code,message);}
    static final class RuleViolation extends Exception{final int status;final String code;final String safeMessage;RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}}
    private record Target(String type,String primary,String primaryUpdate,String additionalInsert,String additionalTable,String idColumn){}
}
