package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import utils.DeviceUtils;
import static org.junit.jupiter.api.Assertions.*;

class ClientIdentityTest {
    @Test void companionEnrollmentCannotReuseRegisterIdentity() {
        String previous = System.getProperty("smartstock.client.application");
        try {
            System.clearProperty("smartstock.client.application");
            assertEquals("smartstock", DeviceUtils.installationPreferenceNode());
            System.setProperty("smartstock.client.application","smartstudio");
            assertEquals("smartstudio",DeviceUtils.installationPreferenceNode());
        } finally {
            if (previous == null) System.clearProperty("smartstock.client.application");
            else System.setProperty("smartstock.client.application",previous);
        }
    }
}
