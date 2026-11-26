package services;

import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeckersScreensTest {
    @Test void everyRegisteredAppHasAnInDocumentController() throws Exception {
        Path directory=Files.createTempDirectory("deckers-screen-modules-");
        try {
            for(var app:MobileDeviceService.APPS) {
                var packet=DeckersScreens.packet(app.key());
                assertFalse(packet.get("body").toString().contains("<script"),app.key());
                Path module=directory.resolve(app.key()+".mjs");
                Files.writeString(module,DeckersScreens.module(app.key()));
                Process check=new ProcessBuilder("node","--check",module.toString()).redirectErrorStream(true).start();
                String output=new String(check.getInputStream().readAllBytes());
                assertEquals(0,check.waitFor(),app.key()+": "+output);
                String fixture=System.getProperty("smartstock.workspace.fixture");
                if(fixture!=null) {
                    Path target=Path.of(fixture);Files.createDirectories(target);
                    Files.writeString(target.resolve(app.key()+".json"),new Gson().toJson(packet));
                    Files.writeString(target.resolve(app.key()+".js"),DeckersScreens.module(app.key()));
                }
            }
        }finally {try(var files=Files.list(directory)){for(Path file:files.toList())Files.delete(file);}Files.delete(directory);}
    }
    @Test void screenOperationsCannotCrossDeviceAppBoundaries() throws Exception {
        JsonObject body=new JsonObject();
        assertTrue(DeckersScreens.allows("RECEIVING","mobile/receiving/search",body));
        assertFalse(DeckersScreens.allows("RECEIVING","mobile/products/save",body));
        assertFalse(DeckersScreens.allows("INVENTORY","customers/save",body));
        assertTrue(DeckersScreens.allows("PAYROLL","payroll/pay",body));
        assertFalse(DeckersScreens.allows("EMPLOYEE_WEB","payroll/pay",body));
        assertFalse(DeckersScreens.allows("PAYROLL","history/correct",body));
        assertFalse(DeckersScreens.allows("EMPLOYEE_WEB","overseas",body));
        assertFalse(DeckersScreens.allows("ORDERS","auth/login",body));
        assertFalse(DeckersScreens.allows("SCHEDULER","scheduler/unknown",body));
        assertTrue(DeckersScreens.allows("SCHEDULER","scheduler/update",body));
        assertNull(DeckersScreens.asset("screen-assets/../../database.properties"));
        assertNull(DeckersScreens.asset("screen-assets/employee-web/app.js"));
    }
}
