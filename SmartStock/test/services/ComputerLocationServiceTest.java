package services;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ComputerLocationServiceTest {
    @Test void parsesValidatedWindowsPosition() throws Exception {
        var position=ComputerLocationService.parse("6.8013,-58.1551,12.5");
        assertEquals(6.8013,position.latitude());
        assertEquals(-58.1551,position.longitude());
        assertEquals(12.5,position.accuracyMeters());
    }

    @Test void rejectsInvalidOrOutOfRangePosition() {
        assertThrows(IOException.class,()->ComputerLocationService.parse("91,0,10"));
        assertThrows(IOException.class,()->ComputerLocationService.parse("not-a-position"));
    }
}
