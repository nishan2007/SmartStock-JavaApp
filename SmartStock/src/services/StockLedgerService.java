package services;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Server-side, noncash stock ledger. Caller owns the transaction and idempotency. */
final class StockLedgerService {
    static final List<String> LOSS_REASONS = List.of("Unexplained Shortage", "Suspected Theft", "Damage", "Counting Error", "Other");
    private StockLedgerService() { }

    static void requireReport(Connection c, int user) throws Exception {
        LanInventoryService.requirePermission(c, user, "VIEW_REPORTS");
        LanInventoryService.requirePermission(c, user, "BALANCE_SHEET");
    }

    static Map<String,Object> post(Connection c, JsonObject body, UUID device, int user, String name, int location) throws Exception {
        LanInventoryService.requirePermission(c,user,"RECORD_STOCK_EXPENSE");
        String type = required(body,"itemType",40);
        int item = integer(body,"itemId"), qty = integer(body,"quantity");
        if(qty<1 || qty>1_000_000) throw invalid("Quantity must be between 1 and 1,000,000.");
        String category=required(body,"category",80), reason=required(body,"reason",2000), description=required(body,"description",2000);
        if(!List.of("Company Use","Donation").contains(category)) throw invalid("Choose Company Use or Donation.");
        byte[] signature=validateSignature(required(body,"signaturePngBase64",400000));
        var stock=LanInventoryService.lockStock(c,type,item,location);
        if(stock.currentStock()<qty) throw invalid("There is not enough stock for this expense.");
        UUID id=insert(c,stock,"EXPENSE",qty,stock.currentStock()-qty,category,description,reason,user,name,device,location,null,signature,null,null);
        LanInventoryService.applyStock(c,stock,-qty,location);
        movement(c,stock,-qty,"STOCK_EXPENSE",id,user,name,device,location);
        return Map.of("entryId",id.toString());
    }

    static void shortage(Connection c, LanInventoryService.LockedStock stock, int counted, String category, String notes,
                         int user,String name,UUID device,int location,String receive) throws Exception {
        validateLoss(category,notes);
        insert(c,stock,"LOSS",stock.currentStock()-counted,counted,category,"Receiving shortage",notes==null || notes.isBlank()?category:notes.trim(),user,name,device,location,receive,null,null,null);
    }

    static void validateLoss(String category,String notes) throws Exception {
        if(category==null || !LOSS_REASONS.contains(category) || notes!=null && notes.length()>2000)
            throw invalid("Choose a valid loss reason. Optional notes cannot exceed 2,000 characters.");
    }

    private static UUID insert(Connection c,LanInventoryService.LockedStock stock,String kind,int qty,int counted,String category,
                               String description,String reason,int user,String name,UUID device,int location,String receive,
                               byte[] signature,UUID reversal,BigDecimal[] originalPrices) throws Exception {
        BigDecimal[] prices=originalPrices==null?prices(c,stock):originalPrices;
        UUID id=UUID.randomUUID();
        try(var p=c.prepareStatement("""
                INSERT INTO stock_ledger_entries(entry_id,location_id,ledger_type,item_type,item_id,item_name,quantity,
                system_quantity,counted_quantity,selling_price,unit_cost,category,description,reason,user_id,user_name,
                device_id,receive_id,signature_png,reversal_of) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            Object[] values={id,location,kind,stock.type(),stock.itemId(),stock.name(),qty,stock.currentStock(),counted,
                    prices[0],prices[1],category,description,reason,user,name,device,receive,signature,reversal};
            for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);
            p.executeUpdate();
        }
        SyncOutboxService.recordEvent(c,"STOCK_LEDGER_ENTRY_CREATED",Map.of("entry_id",id.toString(),"ledger_type",kind),location,device.toString(),user);
        return id;
    }

    private static BigDecimal[] prices(Connection c,LanInventoryService.LockedStock stock) throws Exception {
        String sql=switch(stock.type()) {
            case "PRODUCT" -> "SELECT price,cost_price FROM products WHERE product_id=?";
            case "CUSTOM_ITEM" -> "SELECT COALESCE(fixed_price,area_price,0),NULL::numeric FROM custom_order_items WHERE custom_item_id=?";
            default -> "SELECT COALESCE(v.fixed_price,i.fixed_price,i.area_price,0),NULL::numeric FROM custom_order_item_variants v JOIN custom_order_items i USING(custom_item_id) WHERE v.custom_variant_id=?";
        };
        try(var p=c.prepareStatement(sql)){p.setInt(1,stock.itemId());try(var r=p.executeQuery()){
            if(!r.next())throw invalid("Item price is unavailable.");
            BigDecimal price=r.getBigDecimal(1),cost=r.getBigDecimal(2);
            if(price==null || price.signum()<0 || cost!=null && cost.signum()<0)throw invalid("Item has an invalid price or cost.");
            return new BigDecimal[]{price,cost};
        }}
    }

    static Map<String,Object> reverse(Connection c,JsonObject body,UUID device,int user,String name,int location) throws Exception {
        LanInventoryService.requirePermission(c,user,"REVERSE_STOCK_LEDGER");
        UUID original=UUID.fromString(required(body,"entryId",40));String why=required(body,"reason",2000);
        try(var p=c.prepareStatement("SELECT * FROM stock_ledger_entries WHERE entry_id=? AND location_id=? FOR UPDATE")) {
            p.setObject(1,original);p.setInt(2,location);
            try(var r=p.executeQuery()) {
                if(!r.next() || r.getObject("reversal_of")!=null)throw invalid("Choose an original ledger entry to reverse.");
                try(var q=c.prepareStatement("SELECT 1 FROM stock_ledger_entries WHERE reversal_of=?")){
                    q.setObject(1,original);try(var existing=q.executeQuery()){if(existing.next())throw invalid("This entry has already been reversed.");}
                }
                var stock=LanInventoryService.lockStock(c,r.getString("item_type"),r.getInt("item_id"),location);
                int qty=r.getInt("quantity");
                if((long)stock.currentStock()+qty>Integer.MAX_VALUE)throw invalid("Reversal would exceed the stock limit.");
                UUID id=insert(c,stock,r.getString("ledger_type"),qty,stock.currentStock()+qty,r.getString("category"),
                        "Reversal of "+original,why,user,name,device,location,r.getString("receive_id"),null,original,
                        new BigDecimal[]{r.getBigDecimal("selling_price"),r.getBigDecimal("unit_cost")});
                try(var valuation=c.prepareStatement("UPDATE stock_ledger_entries SET valuation_source=? WHERE entry_id=?")){
                    valuation.setString(1,r.getString("valuation_source"));valuation.setObject(2,id);valuation.executeUpdate();
                }
                LanInventoryService.applyStock(c,stock,qty,location);
                movement(c,stock,qty,"STOCK_LEDGER_REVERSAL",id,user,name,device,location);
                return Map.of("entryId",id.toString());
            }
        }
    }

    private static void movement(Connection c,LanInventoryService.LockedStock stock,int delta,String reason,UUID id,int user,String name,UUID device,int location)throws Exception {
        LanInventoryService.movement(c,stock,delta,reason,"ledger_entry="+id,location,user,name,device,null);
        SyncOutboxService.recordEvent(c,"INVENTORY_MOVEMENT_CREATED",Map.of("source","STOCK_LEDGER","entry_id",id.toString()),location,device.toString(),user);
    }

    static Map<String,Object> load(Connection c,JsonObject body,int user,int location) throws Exception {
        requireReport(c,user);
        long from=Long.parseLong(required(body,"fromEpochMillis",20)),to=Long.parseLong(required(body,"toEpochMillis",20));
        if(to<=from)throw invalid("The end date must follow the start date.");
        boolean cost=LanInventoryService.hasPermission(c,user,"VIEW_COST_PRICE");
        List<Map<String,Object>> entries=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT *, EXISTS(SELECT 1 FROM stock_ledger_entries rev WHERE rev.reversal_of=e.entry_id) AS reversed FROM stock_ledger_entries e WHERE location_id=? AND created_at>=? AND created_at<? ORDER BY created_at,entry_id")) {
            p.setInt(1,location);p.setTimestamp(2,new Timestamp(from));p.setTimestamp(3,new Timestamp(to));
            try(var r=p.executeQuery()){while(r.next()){
                Map<String,Object> row=new LinkedHashMap<>();
                for(String key:List.of("entry_id","ledger_type","item_type","item_id","item_name","quantity","system_quantity","counted_quantity","selling_price","category","description","reason","user_id","user_name","receive_id","reversal_of","reversed","valuation_source","source_movement_type","source_movement_id")){
                    Object value=r.getObject(key);row.put(key,value instanceof UUID?value.toString():value);
                }
                row.put("created_at",r.getTimestamp("created_at").getTime());
                row.put("unit_cost",cost?r.getBigDecimal("unit_cost"):null);
                entries.add(row);
            }}
        }
        return Map.of("entries",entries);
    }

    static Map<String,Object> detail(Connection c,JsonObject body,int user,int location)throws Exception {
        requireReport(c,user);
        try(var p=c.prepareStatement("SELECT signature_png FROM stock_ledger_entries WHERE entry_id=? AND location_id=?")){
            p.setObject(1,UUID.fromString(required(body,"entryId",40)));p.setInt(2,location);
            try(var r=p.executeQuery()){if(!r.next())throw invalid("Ledger entry was not found.");byte[] bytes=r.getBytes(1);
                return Map.of("signaturePngBase64",bytes==null?"":Base64.getEncoder().encodeToString(bytes));}
        }
    }

    static byte[] validateSignature(String encoded)throws Exception {
        if(encoded==null || encoded.length()>400000)throw invalid("A valid handwritten signature is required.");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw invalid("Invalid signature image.");}
        if(bytes.length<8 || bytes.length>300000 || bytes[0]!=(byte)137 || bytes[1]!=80 || bytes[2]!=78 || bytes[3]!=71)throw invalid("Signature must be a PNG image.");
        // Inspect dimensions before decoding to bound memory use.
        try(var stream=ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw invalid("Invalid signature image.");
            var reader=readers.next();try{
                reader.setInput(stream);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<100 || width>1200 || height<40 || height>600)throw invalid("Invalid signature dimensions.");
                var image=reader.read(0);int ink=0,minX=width,maxX=0,minY=height,maxY=0;
                for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                    int rgb=image.getRGB(x,y);
                    if((rgb>>>24)>128 && ((rgb>>16&255)<180 || (rgb>>8&255)<180 || (rgb&255)<180)){
                        ink++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                    }
                }
                if(ink<80 || maxX-minX<20 || maxY-minY<8 || ink>width*height/2)throw invalid("Please provide a complete handwritten signature.");
            }finally{reader.dispose();}
        }
        return bytes;
    }

    private static String required(JsonObject body,String key,int max)throws Exception {
        if(body==null || !body.has(key) || body.get(key).isJsonNull())throw invalid("Missing "+key+".");
        String s=body.get(key).getAsString().trim();if(s.isEmpty() || s.length()>max)throw invalid("Invalid "+key+".");return s;
    }
    private static int integer(JsonObject body,String key)throws Exception {
        try{return Integer.parseInt(required(body,key,12));}
        catch(NumberFormatException e){throw invalid("Invalid "+key+".");}
    }
    private static LanInventoryService.RuleViolation invalid(String message){return new LanInventoryService.RuleViolation(400,"VALIDATION_ERROR",message);}
}
