package ui.helpers;

import com.sun.jna.Memory;
import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import static org.junit.jupiter.api.Assertions.*;

class SignatureTabletMappingTest {
    @Test void screenMappingUsesWin32LayoutIncludingNegativeMonitorCoordinates(){
        try(Memory context=new Memory(172)){
            context.clear();context.setInt(40,1);context.setInt(168,65536);
            SignatureTabletMapping.map(context,new Rectangle(-1500,225,620,240));
            // Offsets are the LOGCONTEXTA ABI, independent of JVM pointer size.
            assertEquals(0,context.getInt(144));
            assertEquals(-1500,context.getInt(148));assertEquals(225,context.getInt(152));
            assertEquals(620,context.getInt(156));assertEquals(240,context.getInt(160));
            assertEquals(1,context.getInt(40));assertEquals(65536,context.getInt(168));
            SignatureTabletMapping.map(context,new Rectangle(80,90,930,360));
            assertEquals(80,context.getInt(148));assertEquals(930,context.getInt(156));
        }
    }
}
