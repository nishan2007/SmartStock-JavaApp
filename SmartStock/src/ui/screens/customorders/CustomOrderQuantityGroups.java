package ui.screens.customorders;

import com.google.gson.*;
import java.util.*;

/** Groups display-identical lines without changing their stored identities. */
final class CustomOrderQuantityGroups {
    static List<List<JsonObject>> group(JsonObject data) {
        Map<JsonObject,List<JsonObject>> groups=new LinkedHashMap<>();
        for(JsonElement entry:data.getAsJsonArray("lines")) {
            JsonObject line=entry.getAsJsonObject(),key=line.deepCopy();
            key.remove("custom_order_line_id");
            JsonArray addons=new JsonArray();
            for(JsonElement element:data.getAsJsonArray("addons")) {
                JsonObject addon=element.getAsJsonObject();
                if(CustomOrderDetailsPanel.text(addon,"custom_order_line_id").equals(CustomOrderDetailsPanel.text(line,"custom_order_line_id"))) {
                    JsonObject copy=addon.deepCopy();copy.remove("custom_order_line_id");addons.add(copy);
                }
            }
            key.add("addons",addons);
            JsonArray files=new JsonArray();
            if(data.has("files"))for(JsonElement element:data.getAsJsonArray("files")){
                JsonObject file=element.getAsJsonObject();
                if(CustomOrderDetailsPanel.text(file,"custom_order_line_id").equals(CustomOrderDetailsPanel.text(line,"custom_order_line_id"))){
                    JsonObject copy=file.deepCopy();copy.remove("custom_order_line_id");files.add(copy);
                }
            }
            key.add("files",files);
            groups.computeIfAbsent(key,ignored->new ArrayList<>()).add(line);
        }
        return new ArrayList<>(groups.values());
    }
}
