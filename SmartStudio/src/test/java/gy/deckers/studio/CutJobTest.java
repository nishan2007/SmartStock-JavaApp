package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CutJobTest {
    @TempDir Path temporary;
    private static Publication art(){Publication p=new Publication();p.width=50;p.height=25;return p;}
    private static CutJob.Settings media(){var s=new CutJob.Settings();s.width=200;s.margin=5;s.bleed=1;s.gap=3;s.copies=10;s.rotateLoading=false;return s;}
    @Test void autoLengthCountsRowsBleedMarginsRegistrationAndGrip()throws Exception {
        var s=media();var job=new CutJob(art(),0,s);
        // 3 columns, 4 rows; 4*27 + 3*3 + 10 margins + 16 registration + 19.05 grip.
        assertEquals(162.05,job.height(),1e-8);assertEquals(10,job.cuts().size());
        s.gripEdges.add(CutJob.Edge.TOP);assertEquals(job.height()+19.05,new CutJob(art(),0,s).height(),1e-8);
        s.roll=false;s.length=100;assertThrows(IllegalArgumentException.class,()->new CutJob(art(),0,s));
    }
    @Test void everyGripEdgeExcludesArtworkBleedAndTargets()throws Exception {
        for(CutJob.Edge edge:CutJob.Edge.values()){
            var s=media();s.gripEdges=EnumSet.of(edge);var job=new CutJob(art(),0,s);
            for(Shape cut:job.cuts()){var b=cut.getBounds2D();assertTrue(b.getMinX()>=5+s.bleed+(edge==CutJob.Edge.LEFT?s.grip:0));assertTrue(b.getMaxX()<=job.width()-5-s.bleed-(edge==CutJob.Edge.RIGHT?s.grip:0)+1e-7);assertTrue(b.getMinY()>=5+s.bleed+(edge==CutJob.Edge.TOP?s.grip:0));assertTrue(b.getMaxY()<=job.height()-5-s.bleed-(edge==CutJob.Edge.BOTTOM?s.grip:0)+1e-7);}
            for(var mark:job.marks()){assertTrue(mark.x>=5+(edge==CutJob.Edge.LEFT?s.grip:0));assertTrue(mark.y>=5+(edge==CutJob.Edge.TOP?s.grip:0));assertTrue(mark.x<=job.width()-5-(edge==CutJob.Edge.RIGHT?s.grip:0));assertTrue(mark.y<=job.height()-5-(edge==CutJob.Edge.BOTTOM?s.grip:0));}
        }
    }
    @Test void rotated44InchSheetMapsPhysicalGripAndBounds()throws Exception {
        var s=media();s.width=44*25.4;s.roll=false;s.length=20*25.4;s.rotateLoading=true;s.gripEdges=EnumSet.of(CutJob.Edge.RIGHT);var job=new CutJob(art(),0,s);
        assertEquals(508,job.loadedWidth(),1e-6);assertEquals(1117.6,job.loadedLength(),1e-6);assertEquals(CutJob.Edge.BOTTOM,job.loadedEdge(CutJob.Edge.RIGHT));
        assertEquals(CutJob.Edge.RIGHT,job.loadedEdge(CutJob.Edge.TOP));assertEquals(CutJob.Edge.TOP,job.loadedEdge(CutJob.Edge.LEFT));assertEquals(CutJob.Edge.LEFT,job.loadedEdge(CutJob.Edge.BOTTOM));
        assertDoesNotThrow(()->job.requireFits(870,765,1500,job.loading()));
        s.rotateLoading=false;var unrotated=new CutJob(art(),0,s);assertThrows(IllegalArgumentException.class,()->unrotated.requireFits(870,765,1500,unrotated.loading()));
    }
    @Test void affineRegistrationRecoversRotationScaleAndTranslation()throws Exception {
        List<Point2D.Double> source=List.of(new Point2D.Double(5,5),new Point2D.Double(100,5),new Point2D.Double(5,200));
        var expected=new AffineTransform(0,1.001,-.999,.002,220,12);List<Point2D.Double> measured=source.stream().map(p->{Point2D q=expected.transform(p,null);return new Point2D.Double(q.getX(),q.getY());}).toList();
        var result=CutJob.register(source,measured);for(Point2D p:source)assertEquals(0,expected.transform(p,null).distance(result.transform(p,null)),1e-8);
        assertThrows(IllegalArgumentException.class,()->CutJob.register(source,List.of(new Point2D.Double(),new Point2D.Double(1,1),new Point2D.Double(2,2))));
        assertThrows(IllegalArgumentException.class,()->CutJob.register(source,List.of(measured.get(0),measured.get(2),measured.get(1))));
    }
    @Test void invalidOrNonfiniteGeometryAndUnreachableMarksFail()throws Exception {
        var s=media();s.width=Double.NaN;assertThrows(IllegalArgumentException.class,()->new CutJob(art(),0,s));s.width=10;assertThrows(IllegalArgumentException.class,()->new CutJob(art(),0,s));
        var job=new CutJob(art(),0,media());assertThrows(IllegalArgumentException.class,()->job.requireFits(250,180,1000,job.loading()));
        var p=art();p.mergeRows.add(Map.of("name","A"));assertThrows(IllegalArgumentException.class,()->new CutJob(p,0,media()));
    }
    @Test void contourSelectionsRotationAndWeedingProduceExplicitGeometry()throws Exception {
        var s=media();s.contour=CutJob.Contour.CUSTOM;s.custom="0,0;50,0;25,25";assertEquals(10,new CutJob(art(),0,s).cuts().size());
        s.custom="0,0;100,0;25,25";assertThrows(IllegalArgumentException.class,()->new CutJob(art(),0,s));
        s.contour=CutJob.Contour.CIRCLE;assertThrows(IllegalArgumentException.class,()->new CutJob(art(),0,s));
        var p=art();p.height=50;assertEquals(10,new CutJob(p,0,s).cuts().size());
        s.contour=CutJob.Contour.ROUNDED;s.rotateItems=true;s.weeding=true;s.bleed=2;var rotated=new CutJob(art(),0,s);assertEquals(11,rotated.cuts().size());assertEquals(25,rotated.cuts().get(0).getBounds2D().getWidth(),1e-8);
        s.bleed=0;assertThrows(IllegalArgumentException.class,()->new CutJob(art(),0,s));
    }
    @Test void plainTextHasOutlinesAndRasterNeedsExplicitContour()throws Exception {
        Publication p=art();Publication.Element e=new Publication.Element();e.text="Hi";e.x=2;e.y=2;e.width=40;e.height=20;p.pages.get(0).objects.add(e);
        var s=media();s.vinyl=true;s.contour=CutJob.Contour.TEXT;var job=new CutJob(p,0,s);assertFalse(job.cuts().get(0).getBounds2D().isEmpty());
        e.type="IMAGE";assertThrows(IllegalArgumentException.class,()->new CutJob(p,0,s));
        e.type="TEXT";e.text="{{name}}";assertThrows(IllegalArgumentException.class,()->new CutJob(p,0,s));
    }
    @Test void frozenSettingsAndAssetsSurviveSaveWithoutMachinePaths()throws Exception {
        var p=art();var image=new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);Path asset=temporary.resolve("source.png");javax.imageio.ImageIO.write(image,"png",asset.toFile());p.assetFiles.put("image",asset);
        var s=media();var job=new CutJob(p,0,s);s.copies=1;p.width=100;Files.delete(asset);
        assertEquals(10,job.copies());Path saved=temporary.resolve("job.sscut");job.save(saved);assertFalse(Files.readString(saved).contains(asset.toString().replace("\\","\\\\")));var restored=CutJob.load(saved);
        assertEquals(job.width(),restored.width());assertEquals(job.height(),restored.height());assertEquals(10,restored.copies());assertNotEquals(job.id,restored.id);
        Files.writeString(saved,"{}");assertThrows(java.io.IOException.class,()->CutJob.load(saved));
    }
    @Test void actualSizePrintingRejectsScalingAndInsufficientMargins()throws Exception {
        var job=new CutJob(art(),0,media());PageFormat format=CutJobOutput.format(job);assertDoesNotThrow(()->CutJobOutput.requireFormat(job,format));
        Paper paper=format.getPaper();paper.setSize(paper.getWidth()/2,paper.getHeight());format.setPaper(paper);assertThrows(PrinterException.class,()->CutJobOutput.requireFormat(job,format));
        PageFormat clipped=CutJobOutput.format(job);paper=clipped.getPaper();paper.setImageableArea(40,40,paper.getWidth()-80,paper.getHeight()-80);clipped.setPaper(paper);assertThrows(PrinterException.class,()->CutJobOutput.requireFormat(job,clipped));
    }
    @Test void pdfHasMatchingPhysicalDimensionsAndPreviewRenders()throws Exception {
        var s=media();s.width=1117.6;s.roll=false;s.length=508;s.rotateLoading=true;s.gripEdges=EnumSet.of(CutJob.Edge.RIGHT);var job=new CutJob(art(),0,s);Path pdf=temporary.resolve("print.pdf");CutJobOutput.pdf(job,pdf);
        try(var document=org.apache.pdfbox.pdmodel.PDDocument.load(pdf.toFile())){assertEquals(1,document.getNumberOfPages());assertEquals(job.width()*72/25.4,document.getPage(0).getMediaBox().getWidth(),.01);assertEquals(job.height()*72/25.4,document.getPage(0).getMediaBox().getHeight(),.01);}
        for(boolean loaded:new boolean[]{false,true}){var preview=new CutProduction.Preview(job,loaded);preview.setSize(900,520);BufferedImage img=new BufferedImage(900,520,BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();preview.paint(g);g.dispose();Files.createDirectories(Path.of("target"));javax.imageio.ImageIO.write(img,"png",Path.of("target",loaded?"cut-loading-preview.png":"cut-print-preview.png").toFile());}
    }
}
