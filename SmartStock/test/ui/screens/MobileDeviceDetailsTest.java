package ui.screens;

import org.junit.jupiter.api.Test;
import services.LanApiClient;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MobileDeviceDetailsTest {
    @Test void modelAndUnavailableFieldsAreDisplayedWithoutGuessing() {
        var android=new LanApiClient.MobileManagedDevice("one","Manager phone","","",false,1,List.of(),false,Map.of("deviceFamily","Android device","model","SM-S918B","screenWidth",390.0,"browser","Google Chrome"),"2026-11-20T20:00:00Z");
        String text=MobileManagement.deviceDetailsText(android);
        assertTrue(text.contains("Model: SM-S918B"));assertTrue(text.contains("Screen width (CSS pixels): 390\n"));
        assertTrue(text.contains("not verified hardware identity"));
        var iphone=new LanApiClient.MobileManagedDevice("two","iPhone","","",false,1,List.of(),false,Map.of("deviceFamily","iPhone","model",""),"");
        text=MobileManagement.deviceDetailsText(iphone);
        assertTrue(text.contains("Device type: iPhone"));assertTrue(text.contains("Model: Not provided by browser"));
        var pending=new LanApiClient.MobileManagedDevice("three","New phone","","",false,1,List.of());
        assertTrue(MobileManagement.deviceDetailsText(pending).contains("No device information reported yet"));
    }
}
