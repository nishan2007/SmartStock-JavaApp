package gy.deckers.studio;

import publishing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class RisoImageAssignmentsTest {
    @TempDir Path folder;
    List<RisoSeparation.Ink> inks=List.of(new RisoSeparation.Ink("Black",Color.BLACK,true),new RisoSeparation.Ink("Red",Color.RED,true),new RisoSeparation.Ink("Green",new Color(0,128,0),true));
    Publication document(){try{var p=new Publication();p.width=60;p.height=40;p.print.width=p.print.height=100;p.print.left=p.print.right=p.print.top=p.print.bottom=10;p.print.alignment="TOP_LEFT";p.print.quantity=1;p.print.cropMarks=false;
        var rect=new Publication.Element();rect.type="RECT";rect.x=rect.y=3;rect.width=rect.height=4;rect.fill="#000000";rect.stroke="none";p.pages.get(0).objects.add(rect);
        var image=new BufferedImage(4,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xffff0000);image.setRGB(1,0,0xff000000);image.setRGB(2,0,0xffffffff);image.setRGB(3,0,0x80000000);PublicationImport.addImage(p,p.pages.get(0),image,10,10,20,10);
        var second=Publication.JSON.fromJson(Publication.JSON.toJson(p.pages.get(0).objects.get(1)),Publication.Element.class);second.x=35;second.id="second-image";p.pages.get(0).objects.add(second);p.validate();return p;}catch(Exception ex){throw new RuntimeException(ex);}}
    int sample(BufferedImage image,double x,double y){return image.getRaster().getSample((int)Math.round(x*10),(int)Math.round(y*10),0);}
    @Test void manualAssignmentUsesOnlyChosenInkPreservesShadingAlphaAndUnassignedArtwork(){
        var p=document();String json=Publication.JSON.toJson(p);var assets=Map.copyOf(p.assets);var assigned=Map.of("p:0:1",2);
        var black=RisoOutput.plate(p,0,254,inks,0,false,assigned);var red=RisoOutput.plate(p,0,254,inks,1,false,assigned);var green=RisoOutput.plate(p,0,254,inks,2,false,assigned);
        assertEquals(255,sample(black,27,25));assertEquals(255,sample(red,27,25));assertEquals(0,sample(green,27,25));assertEquals(54,sample(green,22,25),1);assertEquals(255,sample(green,32,25));assertEquals(127,sample(green,37,25),2);
        assertEquals(0,sample(black,14,14));assertEquals(255,sample(green,14,14));assertEquals(0,sample(black,52,25));assertEquals(0,sample(red,47,25));assertEquals(255,sample(green,52,25));
        assertEquals(json,Publication.JSON.toJson(p));assertEquals(assets,p.assets);
    }
    @Test void equalInkColoursStillRespectExplicitInkIdentity(){var p=document();var same=List.of(new RisoSeparation.Ink("Black A",Color.BLACK,true),new RisoSeparation.Ink("Black B",Color.BLACK,true));var assigned=Map.of("p:0:1",1);assertEquals(255,sample(RisoOutput.plate(p,0,254,same,0,false,assigned),27,25));assertEquals(0,sample(RisoOutput.plate(p,0,254,same,1,false,assigned),27,25));}
    @Test void laterObjectsOccludeAssignedPictureAndCombinedPreviewUsesItsChosenColour(){var p=document();var cover=new Publication.Element();cover.type="RECT";cover.x=16;cover.y=10;cover.width=3;cover.height=10;cover.fill="#ff0000";cover.stroke="none";p.pages.get(0).objects.add(cover);var assigned=Map.of("p:0:1",2);assertEquals(255,sample(RisoOutput.plate(p,0,254,inks,2,false,assigned),27,25));assertEquals(0,sample(RisoOutput.plate(p,0,254,inks,1,false,assigned),27,25));var combined=RisoOutput.plate(document(),0,254,inks,-1,false,assigned);assertEquals(new Color(0,128,0).getRGB(),combined.getRGB(270,250));}
    @Test void assignmentValidationBlocksDisabledOrMissingInksAndUnusedMastersAreOmitted(){var p=document();var disabled=List.of(new RisoSeparation.Ink("Off",Color.BLACK,false),inks.get(1));assertThrows(IllegalArgumentException.class,()->RisoImageAssignments.resolve(p,disabled,Map.of("p:0:1",0)));assertThrows(IllegalArgumentException.class,()->RisoImageAssignments.resolve(p,inks,Map.of("p:0:1",9)));assertThrows(IllegalArgumentException.class,()->RisoImageAssignments.resolve(p,inks,Map.of("missing",0)));var master=new Publication.Page();master.name="Unused";master.objects.add(p.pages.get(0).objects.get(1));p.masters.add(master);assertEquals(2,RisoImageAssignments.targets(p).size());p.pages.get(0).master="Unused";assertEquals(3,RisoImageAssignments.targets(p).size());}
    @Test void assignedPdfStillUsesSingleChannelLosslessOutput()throws Exception{var path=folder.resolve("assigned.pdf");RisoOutput.pdf(document(),inks,false,path,Map.of("p:0:1",2));try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(path.toFile())){assertEquals(3,pdf.getNumberOfPages());var page=pdf.getPage(2);var image=(org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject)page.getResources().getXObject(page.getResources().getXObjectNames().iterator().next());assertEquals(1,image.getColorSpace().getNumberOfComponents());}}
    @Test void rotatedCroppedAndMasterPicturesUseTheSameAssignmentAcrossCopies(){
        var p=document();p.pages.get(0).objects.remove(2);p.pages.get(0).objects.remove(0);var e=p.pages.get(0).objects.get(0);e.cropX=.25;e.cropWidth=.25;e.rotation=90;
        var master=new Publication.Page();master.name="Background";master.objects.add(e);p.pages.get(0).objects.clear();p.pages.get(0).master=master.name;p.masters.add(master);p.print.quantity=2;
        var assigned=Map.of("m:0:0",2);int dark=0;
        for(int sheet=0;sheet<new PublicationRenderer(p).sheets();sheet++){
            var green=RisoOutput.plate(p,sheet,127,inks,2,false,assigned);var red=RisoOutput.plate(p,sheet,127,inks,1,false,assigned);var black=RisoOutput.plate(p,sheet,127,inks,0,false,assigned);
            for(int y=0;y<green.getHeight();y++)for(int x=0;x<green.getWidth();x++){if(green.getRaster().getSample(x,y,0)<100)dark++;assertEquals(255,red.getRaster().getSample(x,y,0));assertEquals(255,black.getRaster().getSample(x,y,0));}
        }
        assertTrue(dark>9000,"Both rotated copies retain the black crop as green coverage");
    }
    @Test void assignmentTableSupportsSeveralPicturesAndReturningToAutomatic()throws Exception{SwingUtilities.invokeAndWait(()->{var panel=new RisoImageAssignmentsPanel(document(),inks,Map.of());panel.setSize(panel.getPreferredSize());panel.doLayout();assertTrue(panel.getComponent(0).getHeight()>200,"Picture list remains visible on initial dialog layout");panel.table.addRowSelectionInterval(0,1);panel.ink.setSelectedIndex(3);for(var child:((JPanel)panel.getComponent(1)).getComponents())if(child instanceof JButton b&&b.getText().equals("Assign ink"))b.doClick();assertEquals(Map.of("p:0:1",2,"p:0:2",2),panel.assignments());panel.table.getModel().setValueAt(new RisoImageAssignmentsPanel.Choice(-1,"Automatic"),0,1);assertEquals(Map.of("p:0:2",2),panel.assignments());});}
}
