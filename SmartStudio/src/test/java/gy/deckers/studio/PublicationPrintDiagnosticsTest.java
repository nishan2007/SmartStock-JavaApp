package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationPrintDiagnosticsTest {
    @TempDir Path folder;
    @Test void recordsActualCallbackGeometryWithoutChangingItAndBoundsRepeatedCalls()throws Exception{
        Path file=Files.createFile(folder.resolve("direct-print-test.txt"));
        var diagnostics=new PublicationPrintDiagnostics(file);
        var image=new BufferedImage(40,40,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{
            g.scale(2,3);g.translate(-4,-5);g.setClip(1,2,10,11);var original=g.getTransform();var clip=g.getClip().getBounds2D();
            Printable artwork=(graphics,page,index)->{assertEquals(original,((java.awt.Graphics2D)graphics).getTransform());assertEquals(clip,graphics.getClip().getBounds2D());return Printable.PAGE_EXISTS;};
            var wrapped=diagnostics.wrap(artwork);for(int call=0;call<40;call++)assertEquals(Printable.PAGE_EXISTS,wrapped.print(g,new PageFormat(),0));
            String log=PublicationPrintDiagnostics.latestReport();assertTrue(log.contains("transform="));assertTrue(log.contains("clip="));assertTrue(log.contains("page points="));
            assertEquals(32,log.lines().filter(line->line.startsWith("Callback")).count());
        }finally{g.dispose();}
    }
}
