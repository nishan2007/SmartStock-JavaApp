package services;
import java.util.Set;
/** Server-side allowlist based only on the persisted approved device role. */
public final class DisplayDevicePolicy {
    private DisplayDevicePolicy() { }
    public static boolean allows(String path) {
        return Set.of("/v1/display/update/latest","/v1/display/update/sign","/v1/display/work-queue", "/v1/display/image", "/v1/devices/metadata", "/v1/devices/rotate").contains(path);
    }
}
