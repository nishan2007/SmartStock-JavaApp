package services;

import com.google.gson.*;
import java.util.Locale;
import java.util.StringJoiner;

/** Optional named stock colors; an empty palette means varying artwork colors. */
public final class PrintMaterialColors {
    private PrintMaterialColors(){}
    public static JsonObject checked(JsonObject input){
        JsonObject result=new JsonObject();if(input==null)return result;
        if(input.size()>100)throw new IllegalArgumentException("Use up to 100 material colors.");
        for(var entry:input.entrySet()){
            String name=entry.getKey().trim();JsonElement value=entry.getValue();
            if(name.isEmpty()||name.length()>80||name.contains(",")||name.contains("=")||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!value.getAsString().matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Enter material colors as name=#RRGGBB, separated by commas.");
            if(result.keySet().stream().anyMatch(k->k.equalsIgnoreCase(name)))throw new IllegalArgumentException("Each material color name must be unique.");
            result.addProperty(name,value.getAsString().toLowerCase(Locale.ROOT));
        }return result;
    }
    public static JsonObject parse(String text){
        JsonObject result=new JsonObject();if(text==null||text.isBlank())return result;
        for(String part:text.split(",",-1)){String[] pair=part.trim().split("=",-1);if(pair.length!=2||result.has(pair[0].trim()))throw new IllegalArgumentException("Enter material colors as name=#RRGGBB, separated by commas.");result.addProperty(pair[0].trim(),pair[1].trim());}
        return checked(result);
    }
    public static String format(JsonObject colors){StringJoiner text=new StringJoiner(", ");for(var e:checked(colors).entrySet())text.add(e.getKey()+"="+e.getValue().getAsString());return text.toString();}
}
