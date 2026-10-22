package services;

import com.google.gson.*;
import java.math.*;
import java.sql.*;
import java.util.*;
import static services.StorefrontService.*;

/** Local-store shirt catalogue, bounded design documents, and authoritative estimates. */
public final class ShirtDesignerService {
    public static final List<String> AREAS=List.of("FRONT","CHEST","BACK");
    public static final List<String> FONTS=List.of("Deckers Sans","Deckers Serif","Deckers Mono");
    private ShirtDesignerService(){}
    public static String styleLabel(JsonObject config){
        String type=config==null||!config.has("shirtType")?"TEE":text(config,"shirtType");
        String neck=config==null||!config.has("neckline")?"ROUND":text(config,"neckline");
        return type.equals("COLLARED")?"Collared shirt":neck.equals("V")?"T-shirt · V-neck":"T-shirt · Round neck";
    }
    public static String searchLabels(boolean shirt,JsonObject config){
        if(!shirt)return "";
        JsonObject c=config==null||config.size()==0?defaults():config;
        return "shirt "+("COLLARED".equals(text(c,"shirtType"))?"polo collared collar shirt":"t-shirt tshirt tee shirt "+("V".equals(text(c,"neckline"))?"v-neck v neck":"round-neck round neck crew neck"))+" "+("DRY_FIT".equals(text(c,"fabric"))?"dry-fit dry fit dri fit dry fir":"normal fabric")+(c.has("websiteEnabled")&&c.get("websiteEnabled").getAsBoolean()?" designer enabled":" designer disabled");
    }
    public static String styleKey(JsonObject config){
        String type=config!=null&&config.has("shirtType")?text(config,"shirtType"):"TEE";
        return type+":"+(type.equals("COLLARED")?"COLLAR":config!=null&&config.has("neckline")?text(config,"neckline"):"ROUND");
    }
    public static String defaultMockup(String type,String neckline,boolean back){
        if(type.equals("COLLARED"))return back?"/shop/shirt-collared-back.png":"/shop/shirt-collared-front.png";
        return back?"/shop/shirt-back.png":neckline.equals("V")?"/shop/shirt-vneck-front.png":"/shop/shirt-front.png";
    }
    public static JsonObject defaults(){
        JsonObject c=new JsonObject();c.addProperty("websiteEnabled",false);
        c.addProperty("useSystemPresets",true);
        c.addProperty("shirtType","TEE");c.addProperty("neckline","ROUND");c.addProperty("fabric","NORMAL");
        c.addProperty("frontMockup","/shop/shirt-front.png");c.addProperty("backMockup","/shop/shirt-back.png");
        c.add("colors",new JsonObject());JsonArray vinyl=new JsonArray();vinyl.add("#000000");vinyl.add("#ffffff");c.add("vinylColors",vinyl);
        JsonObject areas=new JsonObject();
        for(String name:AREAS){JsonObject a=new JsonObject();a.addProperty("widthIn",0);a.addProperty("heightIn",0);
            a.addProperty("x",name.equals("CHEST")?.57:.30);a.addProperty("y",name.equals("CHEST")?.30:.31);
            a.addProperty("w",name.equals("CHEST")?.13:.40);a.addProperty("h",name.equals("CHEST")?.13:.48);
            a.addProperty("singlePresetId",0);a.addProperty("fullPresetId",0);areas.add(name,a);}
        c.add("areas",areas);return c;
    }
    static boolean hex(String value){return value.matches("#[0-9a-fA-F]{6}");}
    public static JsonObject configuration(JsonObject input,boolean complete){
        try{return checkedConfiguration(input,complete);}catch(IllegalArgumentException e){throw e;}catch(RuntimeException e){throw new IllegalArgumentException("Shirt setup contains missing or invalid fields.",e);}
    }
    private static JsonObject checkedConfiguration(JsonObject input,boolean complete){
        if(input==null||input.size()==0){if(complete)throw new IllegalArgumentException("Configure the shirt designer first.");return new JsonObject();}
        if(input.toString().length()>12000)throw new IllegalArgumentException("Shirt setup is too large.");
        JsonObject c=new JsonObject();c.addProperty("websiteEnabled",input.has("websiteEnabled")&&input.get("websiteEnabled").getAsBoolean());
        c.addProperty("useSystemPresets",!input.has("useSystemPresets")||input.get("useSystemPresets").getAsBoolean());
        String type=input.has("shirtType")?text(input,"shirtType"):"TEE",neck=input.has("neckline")?text(input,"neckline"):type.equals("COLLARED")?"COLLAR":"ROUND";
        if(!List.of("TEE","COLLARED").contains(type)||!(type.equals("TEE")?List.of("ROUND","V").contains(neck):neck.equals("COLLAR")))throw new IllegalArgumentException("Choose a round-neck or V-neck T-shirt, or a collared shirt.");
        c.addProperty("shirtType",type);c.addProperty("neckline",neck);
        String fabric=input.has("fabric")?text(input,"fabric"):"NORMAL";
        if(!List.of("NORMAL","DRY_FIT").contains(fabric))throw new IllegalArgumentException("Choose normal or dry-fit fabric.");
        c.addProperty("fabric",fabric);
        for(String key:List.of("frontMockup","backMockup")){String path=text(input,key);
            if(!path.matches("/shop/[A-Za-z0-9_./-]+\\.(png|jpg|jpeg)")||path.contains(".."))throw new IllegalArgumentException("Use a bundled /shop/ PNG or JPEG mockup path.");c.addProperty(key,path);}
        JsonObject colors=new JsonObject();JsonObject supplied=input.getAsJsonObject("colors");
        if(supplied==null||supplied.size()>100)throw new IllegalArgumentException("Configure up to 100 shirt colors.");
        for(var e:supplied.entrySet()){String color=e.getValue().getAsString();if(e.getKey().length()>120||!hex(color))throw new IllegalArgumentException("Each shirt color needs a name and a six-digit hex swatch.");colors.addProperty(e.getKey(),color.toLowerCase(Locale.ROOT));}c.add("colors",colors);
        JsonArray vinyl=input.getAsJsonArray("vinylColors");if(vinyl==null||vinyl.isEmpty()||vinyl.size()>30)throw new IllegalArgumentException("Configure 1–30 vinyl colors.");
        JsonArray cleanVinyl=new JsonArray();for(var value:vinyl){if(!hex(value.getAsString()))throw new IllegalArgumentException("Enter six-digit vinyl color swatches.");cleanVinyl.add(value.getAsString().toLowerCase(Locale.ROOT));}c.add("vinylColors",cleanVinyl);
        JsonObject areas=new JsonObject();for(String name:AREAS){JsonObject a=input.getAsJsonObject("areas").getAsJsonObject(name),clean=new JsonObject();
            for(String k:List.of("x","y","w","h")){double n=number(a,k,0,1);if((k.equals("w")||k.equals("h"))&&n==0)throw new IllegalArgumentException("Print boundaries need positive dimensions.");clean.addProperty(k,n);}
            if(decimal(clean,"x").add(decimal(clean,"w")).compareTo(BigDecimal.ONE)>0||decimal(clean,"y").add(decimal(clean,"h")).compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Print boundaries must fit the mockup.");
            for(String k:List.of("widthIn","heightIn"))clean.addProperty(k,number(a,k,complete&&!systemPresets(c)?.01:0,60));
            boolean customerOptions=c.get("useSystemPresets").getAsBoolean()||input.has("addOns")&&!input.getAsJsonArray("addOns").isEmpty();
            for(String k:List.of("singlePresetId","fullPresetId")){long id=positiveId(a,k, !complete||customerOptions);clean.addProperty(k,id);}areas.add(name,clean);}
        c.add("areas",areas);
        if(input.has("addOns")){
            JsonArray options=input.getAsJsonArray("addOns"),cleanOptions=new JsonArray();Set<Long> ids=new HashSet<>();
            if(options.size()>40)throw new IllegalArgumentException("Configure up to 40 customer add-ons.");
            for(var value:options){JsonObject option=value.getAsJsonObject(),clean=new JsonObject();long id=positiveId(option,"presetId",false);
                if(!ids.add(id))throw new IllegalArgumentException("Choose each customer preset once.");
                String kind=text(option,"kind");if(!List.of("TEXT","IMAGE").contains(kind))throw new IllegalArgumentException("Choose text or logo for each add-on.");
                clean.addProperty("presetId",id);clean.addProperty("kind",kind);
                String mode=option.has("mode")?text(option,"mode"):"FULL";if(!List.of("SINGLE","FULL").contains(mode))throw new IllegalArgumentException("Choose a print mode for each add-on.");clean.addProperty("mode",mode);
                for(String key:List.of("widthIn","heightIn"))clean.addProperty(key,number(option,key,.1,60));
                cleanOptions.add(clean);
            }c.add("addOns",cleanOptions);
        }
        if(complete&&!systemPresets(c)&&colors.size()==0)throw new IllegalArgumentException("Configure shirt color swatches.");return c;
    }
    static double number(JsonObject o,String k,double min,double max){
        if(o==null||!o.has(k))throw new IllegalArgumentException("Missing "+k+".");double n=o.get(k).getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid "+k+".");return n;
    }
    static long positiveId(JsonObject o,String k,boolean zero){try{long n=o.get(k).getAsBigDecimal().longValueExact();if(n<(zero?0:1))throw new ArithmeticException();return n;}catch(RuntimeException e){throw new IllegalArgumentException("Invalid "+k+".");}}
    static JsonObject preset(Connection c,long id)throws SQLException{
        JsonObject p=one(rows(c,"SELECT p.print_size_preset_id AS id,p.print_material_id AS \"materialId\",p.preset_name AS name,p.fixed_price AS price,m.material_name AS material,m.available_colors AS \"availableColors\" FROM custom_order_print_size_presets p JOIN custom_order_print_materials m USING(print_material_id) WHERE p.print_size_preset_id=? AND p.is_active AND m.is_active AND p.pricing_mode='FIXED_PRESET' AND p.fixed_price>=0",id));
        if(p.size()==0)throw new IllegalArgumentException("A shirt print preset is missing or inactive.");return p;
    }
    public static void validatePresets(Connection c,JsonObject config)throws SQLException{
        for(String key:List.of("frontMockup","backMockup"))if(ShirtDesignerService.class.getClassLoader().getResource("storefront-web/"+text(config,key).substring("/shop/".length()))==null)throw new IllegalArgumentException("The "+key+" image is not bundled. Add it and rebuild before enabling this shirt.");
        if(!systemPresets(config)){
            if(!config.has("addOns")||config.getAsJsonArray("addOns").isEmpty())for(String a:AREAS)for(String k:List.of("singlePresetId","fullPresetId"))preset(c,positiveId(config.getAsJsonObject("areas").getAsJsonObject(a),k,false));
            if(config.has("addOns"))for(var value:config.getAsJsonArray("addOns"))preset(c,positiveId(value.getAsJsonObject(),"presetId",false));
        }
    }
    public static String readiness(Connection c,boolean shirt,JsonObject config)throws SQLException{
        if(!shirt)return "";try{JsonObject clean=configuration(config,true);validatePresets(c,clean);return clean.get("websiteEnabled").getAsBoolean()?"Ready":"Website disabled";}catch(IllegalArgumentException e){return e.getMessage();}
    }
    public static String readiness(Connection c,long id,boolean shirt,JsonObject config)throws SQLException{
        String result=readiness(c,shirt,config);if(!shirt||!result.equals("Ready"))return result;
        JsonObject item=one(rows(c,"SELECT product_type,pricing_type,is_active,is_archived,has_variants,fixed_price,COALESCE(color,'') AS color FROM custom_order_items WHERE custom_item_id=?",id));
        if(!item.get("is_active").getAsBoolean()||item.get("is_archived").getAsBoolean())return "Inactive / archived";
        if(!text(item,"product_type").equals("INVENTORY")||!text(item,"pricing_type").equals("FIXED"))return "Use inventory and fixed pricing";
        if(item.get("has_variants").getAsBoolean()){
            boolean priced=false;for(var v:rows(c,"SELECT COALESCE(NULLIF(v.color,''),i.color,'') AS color FROM custom_order_item_variants v JOIN custom_order_items i USING(custom_item_id) WHERE v.custom_item_id=? AND v.is_active AND v.fixed_price>=0",id))if(systemPresets(config)||config.getAsJsonObject("colors").has(text(v.getAsJsonObject(),"color")))priced=true;
            if(!priced)return "No priced variants with color swatches";
        }else if(item.get("fixed_price").isJsonNull()||(!systemPresets(config)&&!config.getAsJsonObject("colors").has(text(item,"color"))))return "Set a price and color swatch";
        return result;
    }
    public static JsonArray catalog(Connection c,int location)throws SQLException{
        return catalog(c,location,null);
    }
    static boolean systemPresets(JsonObject config){return config.has("useSystemPresets")&&config.get("useSystemPresets").getAsBoolean();}
    /** Inventory names stay authoritative; optional swatches only refine the preview. */
    static void ensureInventoryColor(JsonObject config,String color){
        JsonObject colors=config.getAsJsonObject("colors");if(colors.has(color))return;
        String name=color.trim().toLowerCase(Locale.ROOT).replace(" ","").replace("-","");
        String swatch=hex(color)?color:switch(name){
            case "black"->"#000000";case "white"->"#ffffff";case "red"->"#ff0000";
            case "blue","royalblue"->"#0000ff";case "navy","navyblue"->"#000080";
            case "green"->"#008000";case "yellow"->"#ffff00";case "orange"->"#ffa500";
            case "purple"->"#800080";case "pink"->"#ffc0cb";case "brown"->"#a52a2a";
            case "gray","grey"->"#808080";case "maroon"->"#800000";
            case "lightblue","skyblue"->"#87ceeb";case "beige"->"#f5f5dc";
            default->"#d1d5db";
        };
        colors.addProperty(color,swatch);
    }
    /** All active materials participate; names never determine eligibility, price, or print method. */
    static JsonArray systemAddOns(JsonArray presets,JsonObject config){
        JsonArray result=new JsonArray();
        for(var value:presets){JsonObject p=value.getAsJsonObject().deepCopy();
            p.addProperty("kind",text(p,"pricingMode").equals("PER_LINE")?"TEXT":"BOTH");
            if(config.has("addOns"))for(var override:config.getAsJsonArray("addOns")){JsonObject option=override.getAsJsonObject();if(positiveId(option,"presetId",false)==positiveId(p,"id",false)){
                for(String key:List.of("widthIn","heightIn","mode"))if(option.has(key))p.add(key,option.get(key));
            }}if(p.has("availableColors"))p.addProperty("mode",p.getAsJsonObject("availableColors").size()>0?"SINGLE":"FULL");result.add(p);
        }return result;
    }
    static JsonArray catalog(Connection c,int location,Long selected)throws SQLException{
        JsonArray result=new JsonArray();
        JsonArray systemOptions=rows(c,"SELECT p.print_size_preset_id AS id,p.print_material_id AS \"materialId\",p.preset_name AS name,p.fixed_price AS price,p.pricing_mode AS \"pricingMode\",m.material_name AS material,m.available_colors AS \"availableColors\" FROM custom_order_print_size_presets p JOIN custom_order_print_materials m USING(print_material_id) WHERE p.is_active AND m.is_active AND p.pricing_mode IN ('FIXED_PRESET','PER_LINE') AND p.fixed_price>=0 ORDER BY m.material_name,p.preset_name,p.print_size_preset_id");
        String query="SELECT custom_item_id AS id,item_name AS name,COALESCE(description,'') AS description,COALESCE(size,'') AS size,COALESCE(color,'') AS color,fixed_price AS price,has_variants AS \"hasVariants\",shirt_designer_config AS config FROM custom_order_items WHERE is_shirt AND NOT is_sublimation AND NOT is_business_cards AND NOT is_large_format_printing AND NOT is_laser AND is_active AND NOT is_archived AND product_type='INVENTORY' AND pricing_type='FIXED'"+(selected==null?"":" AND custom_item_id=?")+" ORDER BY item_name";
        for(var row:rows(c,query,selected==null?new Object[0]:new Object[]{selected})){
            JsonObject item=row.getAsJsonObject();try{
                JsonObject config=configuration(item.getAsJsonObject("config"),true);if(!config.get("websiteEnabled").getAsBoolean())continue;validatePresets(c,config);
                JsonArray variants=item.get("hasVariants").getAsBoolean()?rows(c,"SELECT v.custom_variant_id AS id,v.variant_name AS name,COALESCE(NULLIF(v.size,''),i.size,'') AS size,COALESCE(NULLIF(v.color,''),i.color,'') AS color,v.fixed_price AS price FROM custom_order_item_variants v JOIN custom_order_items i USING(custom_item_id) WHERE v.custom_item_id=? AND v.is_active AND v.fixed_price>=0 ORDER BY size,color",item.get("id").getAsLong()):new JsonArray();
                if(!item.get("hasVariants").getAsBoolean()){if(item.get("price").isJsonNull()||decimal(item,"price").signum()<0)continue;JsonObject v=new JsonObject();v.addProperty("id",0);v.addProperty("name",text(item,"name"));for(String k:List.of("size","color","price"))v.add(k,item.get(k));variants.add(v);}
                JsonArray eligible=new JsonArray();for(var v:variants){String color=text(v.getAsJsonObject(),"color");if(systemPresets(config)){ensureInventoryColor(config,color);eligible.add(v);}else if(config.getAsJsonObject("colors").has(color))eligible.add(v);}
                if(eligible.isEmpty())continue;item.add("variants",eligible);item.add("config",config);item.remove("hasVariants");item.remove("price");item.remove("size");item.remove("color");
                JsonObject guides=new JsonObject();for(String area:AREAS){JsonObject guide=new JsonObject(),a=config.getAsJsonObject("areas").getAsJsonObject(area);if(!systemPresets(config)&&(!config.has("addOns")||config.getAsJsonArray("addOns").isEmpty())){guide.add("SINGLE",preset(c,positiveId(a,"singlePresetId",false)));guide.add("FULL",preset(c,positiveId(a,"fullPresetId",false)));}guides.add(area,guide);}item.add("guides",guides);
                JsonArray options=systemPresets(config)?systemAddOns(systemOptions,config):new JsonArray();if(!systemPresets(config)&&config.has("addOns"))for(var value:config.getAsJsonArray("addOns")){JsonObject option=value.getAsJsonObject(),p=preset(c,positiveId(option,"presetId",false)).deepCopy();for(String key:List.of("kind","widthIn","heightIn","mode"))p.add(key,option.get(key));options.add(p);}item.add("addOns",options);result.add(item);
            }catch(IllegalArgumentException e){/* incomplete shirts stay private */}
        }return result;
    }
    public static JsonObject design(JsonObject input){
        try{return checkedDesign(input);}catch(IllegalArgumentException e){throw e;}catch(RuntimeException e){throw new IllegalArgumentException("Shirt design contains missing or invalid fields.",e);}
    }
    private static JsonObject checkedDesign(JsonObject input){
        if(input==null||input.toString().length()>48000||integer(input,"version")!=1)throw new IllegalArgumentException("Unsupported shirt design.");
        JsonObject out=new JsonObject();out.addProperty("version",1);out.addProperty("itemId",positiveId(input,"itemId",false));
        if(input.has("styleKey")){String key=text(input,"styleKey");if(!List.of("TEE:ROUND","TEE:V","COLLARED:COLLAR").contains(key))throw new IllegalArgumentException("Choose a valid shirt type and neckline.");out.addProperty("styleKey",key);}
        JsonArray groups=input.getAsJsonArray("groups");if(groups==null||groups.isEmpty()||groups.size()>50)throw new IllegalArgumentException("Choose 1–50 size/color groups.");
        JsonArray cleanGroups=new JsonArray();Set<Long> ids=new HashSet<>();int total=0;
        for(var value:groups){JsonObject g=value.getAsJsonObject(),clean=new JsonObject();long id=positiveId(g,"variantId",true);int qty=integer(g,"quantity");if(qty<1||qty>1000||!ids.add(id))throw new IllegalArgumentException("Each variant needs one quantity between 1 and 1,000.");total+=qty;if(total>1000)throw new IllegalArgumentException("Request up to 1,000 shirts.");clean.addProperty("variantId",id);clean.addProperty("quantity",qty);cleanGroups.add(clean);}out.add("groups",cleanGroups);
        JsonObject areas=input.getAsJsonObject("areas");if(areas==null)throw new IllegalArgumentException("Add a print design.");JsonObject cleanAreas=new JsonObject();int count=0;
        for(var e:areas.entrySet()){if(!AREAS.contains(e.getKey()))throw new IllegalArgumentException("Invalid print area.");JsonObject a=e.getValue().getAsJsonObject(),clean=new JsonObject();String mode=text(a,"mode");if(!List.of("SINGLE","FULL").contains(mode))throw new IllegalArgumentException("Choose single color or full color.");clean.addProperty("mode",mode);
            String color=text(a,"vinylColor").toLowerCase(Locale.ROOT);if(mode.equals("SINGLE")&&!hex(color))throw new IllegalArgumentException("Choose a vinyl color.");clean.addProperty("vinylColor",color);
            JsonArray layers=a.getAsJsonArray("layers");if(layers==null||layers.size()>20)throw new IllegalArgumentException("Use up to 20 layers per area.");if(layers.isEmpty())continue;JsonArray cleanLayers=new JsonArray();
            for(var layer:layers){if(++count>40)throw new IllegalArgumentException("Use up to 40 design layers.");JsonObject l=layer.getAsJsonObject(),cl=new JsonObject();String type=text(l,"type");if(!List.of("TEXT","IMAGE").contains(type))throw new IllegalArgumentException("Invalid design layer.");cl.addProperty("type",type);
                if(l.has("presetId"))cl.addProperty("presetId",positiveId(l,"presetId",false));
                if(l.has("printMode")){String printMode=text(l,"printMode"),ink=text(l,"vinylColor").toLowerCase(Locale.ROOT);if(!List.of("SINGLE","FULL").contains(printMode)||!hex(ink))throw new IllegalArgumentException("Invalid element print colors.");cl.addProperty("printMode",printMode);cl.addProperty("vinylColor",ink);}
                for(String k:List.of("x","y","w","h")){double n=number(l,k,0,1);if((k.equals("w")||k.equals("h"))&&n<.01)throw new IllegalArgumentException("A design layer is too small.");cl.addProperty(k,n);}cl.addProperty("rotation",number(l,"rotation",-180,180));
                if(type.equals("TEXT")){String t=text(l,"text"),font=text(l,"font"),ink=text(l,"color");if(t.isBlank()||t.length()>300||!FONTS.contains(font)||!hex(ink))throw new IllegalArgumentException("Invalid text layer.");cl.addProperty("text",t);cl.addProperty("font",font);cl.addProperty("color",ink.toLowerCase(Locale.ROOT));}
                else cl.addProperty("fileId",UUID.fromString(text(l,"fileId")).toString());cleanLayers.add(cl);
            }clean.add("layers",cleanLayers);cleanAreas.add(e.getKey(),clean);
        }if(count==0)throw new IllegalArgumentException("Add text or artwork to your shirt.");out.add("areas",cleanAreas);return out;
    }
    public static JsonObject estimate(Connection c,int location,JsonObject input)throws SQLException{
        StorefrontServiceAvailability.requireSlugAvailable(c,location,"custom-t-shirts");JsonObject design=design(input),item=null;
        for(var e:catalog(c,location,design.get("itemId").getAsLong()))item=e.getAsJsonObject();
        if(item==null)throw new IllegalArgumentException("This shirt is no longer available for designing.");
        JsonObject settings=one(rows(c,"SELECT round_custom_orders_to_nearest_twenty FROM company_customization WHERE location_id=?",location));
        return calculate(item,design,!settings.has("round_custom_orders_to_nearest_twenty")||settings.get("round_custom_orders_to_nearest_twenty").getAsBoolean());
    }
    static JsonObject calculate(JsonObject item,JsonObject input,boolean round){
        JsonObject design=design(input);
        if(design.has("styleKey")&&!text(design,"styleKey").equals(styleKey(item.getAsJsonObject("config"))))throw new IllegalArgumentException("This shirt's type or neckline changed. Refresh and review your design before submitting.");
        BigDecimal prints=BigDecimal.ZERO;JsonArray charges=new JsonArray();
        boolean perElement=systemPresets(item.getAsJsonObject("config"))||item.has("addOns")&&!item.getAsJsonArray("addOns").isEmpty();
        for(var entry:design.getAsJsonObject("areas").entrySet()){JsonObject area=entry.getValue().getAsJsonObject();if(!perElement&&text(area,"mode").equals("SINGLE")&&!item.getAsJsonObject("config").getAsJsonArray("vinylColors").contains(area.get("vinylColor")))throw new IllegalArgumentException("This vinyl color is unavailable.");
            if(perElement){int index=0;for(var value:area.getAsJsonArray("layers")){JsonObject layer=value.getAsJsonObject(),option=null;
                if(layer.has("presetId"))for(var candidate:item.getAsJsonArray("addOns"))if(candidate.getAsJsonObject().get("id").getAsLong()==layer.get("presetId").getAsLong())option=candidate.getAsJsonObject();
                if(option==null||!text(option,"kind").equals("BOTH")&&!text(option,"kind").equals(text(layer,"type")))throw new IllegalArgumentException("Choose an available add-on for every design element.");
                if(text(option,"pricingMode").equals("PER_LINE")&&(!text(layer,"type").equals("TEXT")||text(layer,"text").contains("\n")||text(layer,"text").contains("\r")))throw new IllegalArgumentException("Add each priced text line as a separate element.");
                String printMode=layer.has("printMode")?text(layer,"printMode"):text(area,"mode"),optionMode=option.has("mode")?text(option,"mode"):printMode;
                if(!printMode.equals(optionMode))throw new IllegalArgumentException("This add-on's print method changed. Select it again and review your design.");
                if(printMode.equals("SINGLE")){JsonElement ink=layer.has("printMode")?layer.get("vinylColor"):area.get("vinylColor");boolean allowed=option.has("availableColors")?option.getAsJsonObject("availableColors").asMap().containsValue(ink):item.getAsJsonObject("config").getAsJsonArray("vinylColors").contains(ink);if(!allowed)throw new IllegalArgumentException("This material color is unavailable. Select an available color.");}
                JsonObject boundary=item.getAsJsonObject("config").getAsJsonObject("areas").getAsJsonObject(entry.getKey());
                if(option.has("widthIn")&&decimal(layer,"w").multiply(decimal(boundary,"widthIn")).compareTo(decimal(option,"widthIn").add(new BigDecimal("0.001")))>0||option.has("heightIn")&&decimal(layer,"h").multiply(decimal(boundary,"heightIn")).compareTo(decimal(option,"heightIn").add(new BigDecimal("0.001")))>0)throw new IllegalArgumentException("This element exceeds its preset size. Choose a larger add-on.");
                JsonObject p=option.deepCopy();p.addProperty("area",entry.getKey());p.addProperty("mode",printMode);p.addProperty("layerIndex",index++);p.addProperty("description",text(layer,"type").equals("TEXT")?text(layer,"text"):"Uploaded logo");p.addProperty("lineCount",1);charges.add(p);prints=prints.add(decimal(p,"price"));
            }}else{for(var value:area.getAsJsonArray("layers"))if(value.getAsJsonObject().has("presetId")||value.getAsJsonObject().has("printMode"))throw new IllegalArgumentException("This add-on is no longer available. Refresh and review your design.");
                JsonObject p=item.getAsJsonObject("guides").getAsJsonObject(entry.getKey()).getAsJsonObject(text(area,"mode")).deepCopy();p.addProperty("area",entry.getKey());p.addProperty("mode",text(area,"mode"));charges.add(p);prints=prints.add(decimal(p,"price"));}}
        JsonArray lines=new JsonArray();BigDecimal subtotal=BigDecimal.ZERO,total=BigDecimal.ZERO;int quantity=0;
        for(var e:design.getAsJsonArray("groups")){JsonObject g=e.getAsJsonObject(),variant=null;for(var v:item.getAsJsonArray("variants"))if(v.getAsJsonObject().get("id").getAsLong()==g.get("variantId").getAsLong())variant=v.getAsJsonObject();
            if(variant==null)throw new IllegalArgumentException("A selected size/color is unavailable for this shirt.");int qty=integer(g,"quantity");quantity+=qty;JsonObject line=variant.deepCopy();line.addProperty("quantity",qty);line.addProperty("printPrice",prints);BigDecimal unit=utils.CurrencyFormatter.normalize(decimal(variant,"price").add(prints)),raw=unit.multiply(BigDecimal.valueOf(qty));BigDecimal rounded=raw;line.addProperty("unitTotal",unit);line.addProperty("total",rounded);subtotal=subtotal.add(raw);total=total.add(rounded);lines.add(line);}
        // Native custom orders round each shirt line and do not add the normal-POS VAT again.
        total=utils.CurrencyFormatter.customOrderTotal(subtotal,round);
        JsonObject result=new JsonObject();result.addProperty("itemId",design.get("itemId").getAsLong());result.addProperty("itemName",text(item,"name"));result.addProperty("shirtStyle",styleLabel(item.getAsJsonObject("config")));result.add("lines",lines);result.add("charges",charges);result.addProperty("quantity",quantity);result.addProperty("subtotal",subtotal);result.addProperty("vat",0);result.addProperty("rounding",total.subtract(subtotal));result.addProperty("total",total);result.addProperty("estimated",true);
        result.addProperty("fingerprint",LanSecurity.sha256(result.toString()));return result;
    }
    static void validateImage(byte[] bytes){
        try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException("Unreadable shirt artwork.");var reader=readers.next();try{reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>8192||h>8192||(long)w*h>24000000)throw new IllegalArgumentException("Artwork must be at most 8,192 pixels per side and 24 megapixels.");if(reader.read(0)==null)throw new IllegalArgumentException("Unreadable shirt artwork.");}finally{reader.dispose();}}
        catch(java.io.IOException e){throw new IllegalArgumentException("Unreadable shirt artwork.",e);}
    }
    static void verifyFiles(Connection c,int location,UUID auth,UUID request,JsonObject design)throws SQLException{
        for(var a:design.getAsJsonObject("areas").entrySet())for(var l:a.getValue().getAsJsonObject().getAsJsonArray("layers")){JsonObject layer=l.getAsJsonObject();if(!text(layer,"type").equals("IMAGE"))continue;
            if(rows(c,"SELECT 1 FROM storefront.quote_files f JOIN storefront.quote_requests q USING(request_id) WHERE f.file_id=? AND q.request_id=? AND q.location_id=? AND q.auth_id=? AND f.content_type IN ('image/png','image/jpeg')",UUID.fromString(text(layer,"fileId")),request,location,auth).isEmpty())throw new IllegalArgumentException("Upload every placed image before submitting the shirt design.");}
    }
}
