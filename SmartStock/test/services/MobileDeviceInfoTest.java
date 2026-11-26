package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobileDeviceInfoTest {
    @Test void diagnosticsAreWhitelistedBoundedAndNeverAcceptIdentityOrPolicyClaims() {
        JsonObject body=new JsonObject();body.addProperty("model","SM-S918B");body.addProperty("screenWidth",390);
        body.addProperty("deviceId","another-device");body.addProperty("rememberLogin",true);body.addProperty("userAgent","forged");
        JsonObject safe=MobileDeviceInfo.sanitize(body,"real-browser\r\nagent");
        assertEquals("SM-S918B",safe.get("model").getAsString());assertFalse(safe.has("deviceId"));assertFalse(safe.has("rememberLogin"));
        assertEquals("real-browseragent",safe.get("userAgent").getAsString());
        body.addProperty("model","x".repeat(161));assertThrows(IllegalArgumentException.class,()->MobileDeviceInfo.sanitize(body,""));
        body.addProperty("model","bad\nmodel");assertThrows(IllegalArgumentException.class,()->MobileDeviceInfo.sanitize(body,""));
        body.addProperty("model","");body.addProperty("screenWidth",20001);assertThrows(IllegalArgumentException.class,()->MobileDeviceInfo.sanitize(body,""));
        body.addProperty("screenWidth","390");assertThrows(IllegalArgumentException.class,()->MobileDeviceInfo.sanitize(body,""));
        body.remove("screenWidth");assertEquals(1024,MobileDeviceInfo.sanitize(body,"x".repeat(2000)).get("userAgent").getAsString().length());
    }
}
