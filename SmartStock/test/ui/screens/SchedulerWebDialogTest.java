package ui.screens;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerWebDialogTest {
    @Test void buildsCanonicalHttpsSchedulerLink(){assertEquals("https://example.trycloudflare.com/scheduler/",SchedulerWebDialog.schedulerUrl("https://example.trycloudflare.com"));assertEquals("https://example.dev/scheduler/",SchedulerWebDialog.schedulerUrl(" https://example.dev/scheduler/ "));assertNull(SchedulerWebDialog.schedulerUrl("http://example.com"));assertNull(SchedulerWebDialog.schedulerUrl("https://user@example.com"));assertNull(SchedulerWebDialog.schedulerUrl("not a URL"));}
    @Test void qrCodeContainsBlackAndWhitePixelsAtRequestedSize(){var image=SchedulerWebDialog.qrCode("https://example.dev/scheduler/",180);assertEquals(180,image.getWidth());assertEquals(180,image.getHeight());boolean black=false,white=false;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){black|=image.getRGB(x,y)==Color.BLACK.getRGB();white|=image.getRGB(x,y)==Color.WHITE.getRGB();}assertTrue(black);assertTrue(white);}
    @Test void dialogKeepsRegisterQrSeparateFromServerOnlyControls()throws Exception{String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/ui/screens/SchedulerWebDialog.java"));assertTrue(source.contains("if(canControl){actions.add(devices);actions.add(start);actions.add(stop);}"));assertTrue(source.contains("Start and stop controls remain available only on the active store server."));}
}
