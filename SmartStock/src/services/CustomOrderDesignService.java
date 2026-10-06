package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-owned editable documents. Customer proofs are immutable exports of these documents. */
public final class CustomOrderDesignService {
    private CustomOrderDesignService() { }
    private static final BigDecimal DEFAULT_WIDTH=new BigDecimal("8.5");
    private static final BigDecimal DEFAULT_HEIGHT=new BigDecimal("11");

    public static void createForOrder(Connection c,long orderId)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO custom_order_design_documents(custom_order_id,width,height,unit) VALUES(?,16,9,'IN') ON CONFLICT DO NOTHING")){
            p.setLong(1,orderId);p.executeUpdate();
        }
        try(PreparedStatement p=c.prepareStatement("""
                SELECT l.custom_order_line_id,l.pricing_type,l.width_value,l.length_value,l.dimension_unit,
                       COALESCE(v.template_uses_order_area,i.template_uses_order_area,FALSE),i.template_width,i.template_height,i.template_unit,
                       v.template_width,v.template_height,v.template_unit
                FROM custom_order_lines l
                LEFT JOIN custom_order_items i ON i.custom_item_id=l.custom_item_id
                LEFT JOIN custom_order_item_variants v ON v.custom_variant_id=l.custom_variant_id
                WHERE l.custom_order_id=? ORDER BY l.custom_order_line_id
                """)){
            p.setLong(1,orderId);
            try(ResultSet r=p.executeQuery()){
                while(r.next()){
                    Dimensions dimensions=resolveDimensions("AREA".equals(r.getString(2)),r.getBoolean(6),
                            r.getBigDecimal(3),r.getBigDecimal(4),r.getString(5),
                            r.getBigDecimal(7),r.getBigDecimal(8),r.getString(9),
                            r.getBigDecimal(10),r.getBigDecimal(11),r.getString(12));
                    BigDecimal width=dimensions.width(),height=dimensions.height();String unit=dimensions.unit();
                    long lineId=r.getLong(1);
                    try(PreparedStatement snapshot=c.prepareStatement("UPDATE custom_order_lines SET template_width=?,template_height=?,template_unit=? WHERE custom_order_line_id=?")){
                        snapshot.setBigDecimal(1,width);snapshot.setBigDecimal(2,height);snapshot.setString(3,unit);snapshot.setLong(4,lineId);snapshot.executeUpdate();
                    }
                    try(PreparedStatement insert=c.prepareStatement("INSERT INTO custom_order_design_documents(custom_order_id,custom_order_line_id,width,height,unit) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING")){
                        insert.setLong(1,orderId);insert.setLong(2,lineId);insert.setBigDecimal(3,width);insert.setBigDecimal(4,height);insert.setString(5,unit);insert.executeUpdate();
                    }
                }
            }
        }
    }

    record Dimensions(BigDecimal width,BigDecimal height,String unit) { }
    static Dimensions resolveDimensions(boolean area,boolean useOrderMeasurements,
            BigDecimal orderWidth,BigDecimal orderHeight,String orderUnit,
            BigDecimal itemWidth,BigDecimal itemHeight,String itemUnit,
            BigDecimal variantWidth,BigDecimal variantHeight,String variantUnit)throws SQLException {
        BigDecimal width=variantWidth,height=variantHeight;String unit=variantUnit;
        if(width==null||height==null){width=itemWidth;height=itemHeight;unit=itemUnit;}
        if(area&&useOrderMeasurements){width=orderWidth;height=orderHeight;unit=orderUnit;}
        if(width==null||height==null){width=DEFAULT_WIDTH;height=DEFAULT_HEIGHT;unit="IN";}
        if(unit==null||unit.isBlank())unit="IN";
        if(width.signum()<=0||height.signum()<=0||width.compareTo(new BigDecimal("10000"))>0||height.compareTo(new BigDecimal("10000"))>0)
            throw new SQLException("Order line has invalid design dimensions.");
        return new Dimensions(width,height,unit);
    }

    public static List<JsonObject> list(Connection c,long orderId,int location)throws Exception {
        List<JsonObject> out=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("""
                SELECT d.document_id,d.custom_order_line_id,d.width,d.height,d.unit,d.revision,d.document_json::text,
                       COALESCE(l.item_name,'Order overview'),COALESCE(l.variant_name,''),o.status
                FROM custom_order_design_documents d JOIN custom_orders o ON o.custom_order_id=d.custom_order_id
                LEFT JOIN custom_order_lines l ON l.custom_order_line_id=d.custom_order_line_id
                WHERE d.custom_order_id=? AND o.location_id=? ORDER BY d.custom_order_line_id NULLS FIRST
                """)){
            p.setLong(1,orderId);p.setInt(2,location);
            try(ResultSet r=p.executeQuery()){
                while(r.next()){
                    JsonObject row=new JsonObject();row.addProperty("id",r.getObject(1).toString());
                    if(r.getObject(2)!=null)row.addProperty("lineId",r.getLong(2));
                    row.addProperty("width",r.getBigDecimal(3));row.addProperty("height",r.getBigDecimal(4));
                    row.addProperty("unit",r.getString(5));row.addProperty("revision",r.getInt(6));
                    row.add("document",JsonParser.parseString(r.getString(7)));
                    row.addProperty("name",r.getString(8));row.addProperty("variant",r.getString(9));
                    row.addProperty("orderStatus",r.getString(10));out.add(row);
                }
            }
        }
        if(out.isEmpty())throw new IllegalArgumentException("This order is unavailable or has no design workspace.");
        return out;
    }

    public static JsonObject save(Connection c,long orderId,int location,UUID documentId,int expectedRevision,JsonObject document,int staff)throws Exception {
        validateDocument(c,orderId,documentId,document);
        try(PreparedStatement p=c.prepareStatement("""
                UPDATE custom_order_design_documents d SET document_json=?::jsonb,revision=revision+1,
                    updated_by_user_id=?,updated_at=now()
                FROM custom_orders o WHERE d.document_id=? AND d.custom_order_id=? AND o.custom_order_id=d.custom_order_id
                    AND o.location_id=? AND o.status<>'DELIVERED' AND d.revision=?
                RETURNING d.revision
                """)){
            p.setString(1,document.toString());p.setInt(2,staff);p.setObject(3,documentId);p.setLong(4,orderId);
            p.setInt(5,location);p.setInt(6,expectedRevision);
            try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("The design changed on another screen or this order is unavailable. Reload it before saving.");
                int revision=r.getInt(1);
                try(PreparedStatement history=c.prepareStatement("INSERT INTO custom_order_design_document_revisions(document_id,revision,document_json,saved_by_user_id) VALUES(?,?,?::jsonb,?)")){
                    history.setObject(1,documentId);history.setInt(2,revision);history.setString(3,document.toString());history.setInt(4,staff);history.executeUpdate();
                }
                JsonObject result=new JsonObject();result.addProperty("revision",revision);return result;
            }
        }
    }

    private static void validateDocument(Connection c,long orderId,UUID documentId,JsonObject document)throws Exception {
        if(document==null||document.toString().length()>750_000||!document.has("version")||document.get("version").getAsInt()!=1)
            throw new IllegalArgumentException("Invalid or oversized design document.");
        JsonArray objects=document.getAsJsonArray("objects");
        if(objects==null||objects.size()>300)throw new IllegalArgumentException("A design can have up to 300 objects.");
        String background=document.has("background")?document.get("background").getAsString():"";
        if(!background.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Choose a valid background color.");
        Long lineId=null;
        try(PreparedStatement p=c.prepareStatement("SELECT custom_order_line_id FROM custom_order_design_documents WHERE document_id=? AND custom_order_id=?")){
            p.setObject(1,documentId);p.setLong(2,orderId);
            try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Design document is unavailable.");if(r.getObject(1)!=null)lineId=r.getLong(1);}
        }
        for(JsonElement element:objects){
            if(!element.isJsonObject())throw new IllegalArgumentException("Invalid design object.");
            JsonObject object=element.getAsJsonObject();String type=object.has("type")?object.get("type").getAsString():"";
            if(!List.of("text","rect","ellipse","line","path","image").contains(type))throw new IllegalArgumentException("Unsupported design object.");
            for(String key:List.of("x","y","w","h")){
                double value=object.has(key)?object.get(key).getAsDouble():Double.NaN;
                if(!Double.isFinite(value)||Math.abs(value)>100000)throw new IllegalArgumentException("Invalid design position or size.");
            }
            if("image".equals(type)){
                if(lineId==null)throw new IllegalArgumentException("Add images to an order line design, then place them on the overview.");
                UUID file=UUID.fromString(object.get("fileId").getAsString());
                try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM custom_order_files WHERE file_id=? AND custom_order_id=? AND custom_order_line_id=? AND removed_at IS NULL AND deleted_at IS NULL")){
                    p.setObject(1,file);p.setLong(2,orderId);p.setLong(3,lineId);
                    try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("An image attachment is unavailable for this line.");}
                }
            }
            if("text".equals(type)&&(!object.has("text")||object.get("text").getAsString().length()>5000))throw new IllegalArgumentException("Text is too long.");
        }
    }
}
