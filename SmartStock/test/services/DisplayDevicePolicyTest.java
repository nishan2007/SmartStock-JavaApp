package services;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class DisplayDevicePolicyTest {
    @Test void onlyDisplayReadsAndCredentialMaintenanceAreAllowed() throws Exception {
        Set<String> expected=Set.of("/v1/display/update/latest","/v1/display/update/sign","/v1/display/work-queue","/v1/display/image","/v1/devices/rotate","/v1/devices/metadata");
        String source=Files.readString(Path.of("src/services/LanApiServer.java"));
        int checked=0;
        for(String line:source.split("\n"))if(line.contains("server.createContext(\"")){
            String path=line.split("\"")[1];assertEquals(expected.contains(path),DisplayDevicePolicy.allows(path),path);checked++;
        }
        assertTrue(checked>100);
        for(String path:expected)assertTrue(DisplayDevicePolicy.allows(path));
        assertFalse(DisplayDevicePolicy.allows("/v1/display/work-queue/anything"));
        assertFalse(DisplayDevicePolicy.allows("/v1/sessions/login"));
        assertFalse(DisplayDevicePolicy.allows("/v1/security/devices/update"));
    }
}
