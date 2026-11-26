package services;

import com.google.gson.*;
import java.sql.Connection;
import java.util.*;

/** Diagnostic browser reports only. Never used for approval, identity or authorization. */
final class MobileDeviceInfo {
    private MobileDeviceInfo() {}
    static JsonObject sanitize(JsonObject body,String userAgent) {
        JsonObject safe=new JsonObject();
        for(String key:List.of("platform","osVersion","browser","browserVersion","model","deviceFamily","language","timeZone","displayMode")) {
            if(!body.has(key))continue;
            JsonElement value=body.get(key);
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid device information.");
            String text=value.getAsString().strip();
            if(text.length()>160||text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid device information.");
            safe.addProperty(key,text);
        }
        for(String key:List.of("screenWidth","screenHeight","pixelRatio","touchPoints")) {
            if(!body.has(key))continue;
            JsonElement value=body.get(key);
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid device information.");
            double number=value.getAsDouble();
            double max=key.equals("pixelRatio")?10:key.equals("touchPoints")?32:20000;
            if(!Double.isFinite(number)||number<0||number>max)throw new IllegalArgumentException("Invalid device information.");
            safe.addProperty(key,number);
        }
        String agent=Objects.toString(userAgent,"");
        safe.addProperty("userAgent",agent.codePoints().filter(c->!Character.isISOControl(c)).limit(1024).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString());
        return safe;
    }
    static void save(Connection c,UUID device,JsonObject body,String userAgent)throws Exception {
        JsonObject report=sanitize(body,userAgent);
        try(var p=c.prepareStatement("UPDATE mobile_managed_devices SET device_info=?::jsonb,device_info_updated_at=clock_timestamp() WHERE device_id=? AND revoked_at IS NULL AND (device_info_updated_at IS NULL OR device_info_updated_at<clock_timestamp()-INTERVAL '5 minutes')")) {
            p.setString(1,report.toString());p.setObject(2,device);p.executeUpdate();
        }
    }
}
