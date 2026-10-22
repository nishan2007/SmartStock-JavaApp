package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudioMemoryTest {
    @Test void memoryIsAvailableThroughWindowsWithoutTheOptionalJavaModule() {
        org.junit.jupiter.api.Assumptions.assumeTrue(com.sun.jna.Platform.isWindows());
        assertTrue(StudioMemory.nativeAvailable()>0);
        assertTrue(StudioMemory.availablePhysical()>0);
    }
}
