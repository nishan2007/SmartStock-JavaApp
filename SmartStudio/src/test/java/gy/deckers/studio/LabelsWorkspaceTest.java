package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LabelsWorkspaceTest {
    @TempDir Path folder;
    @Test void templatesKeepIndependentSizesAcrossArchiveImport()throws Exception{
        for(double[] size:new double[][]{{25.4,12.7},{100,60},{38,90}}){
            Publication p=BusinessCardsPanel.labelTemplate();p.resize(size[0],size[1],false);
            Path file=folder.resolve("label.sspub");Files.write(file,PublicationFiles.archive(p));
            Publication imported=BusinessCardsPanel.readTemplate(file,null,true);
            assertEquals(size[0],imported.width,.00001);assertEquals(size[1],imported.height,.00001);
            Publication card=BusinessCardsPanel.readTemplate(file,null,false); assertEquals(size[0],card.width,.00001);assertEquals(size[1],card.height,.00001);
        }
    }
    @Test void resizeCanPreserveOrScaleArtworkAndRejectsInvalidSizes()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            BusinessCardsPanel panel=new BusinessCardsPanel(true);
            panel.resizeTemplate(76.2,38.1,false);
            assertEquals(76.2,panel.output().width,.00001);
            panel.resizeTemplate(38.1,19.05,true);
            assertEquals(19.05,panel.output().height,.00001);
            assertThrows(IllegalArgumentException.class,()->panel.resizeTemplate(Double.NaN,20,false));
            assertEquals(38.1,panel.output().width,.00001);
            panel.resetFields();
        });
        Publication p=BusinessCardsPanel.builtin();double x=p.pages.get(0).objects.get(0).x;
        p.resize(177.8,101.6,false);assertEquals(x,p.pages.get(0).objects.get(0).x);
        p.resize(88.9,50.8,true);assertEquals(x/2,p.pages.get(0).objects.get(0).x);
    }
}
