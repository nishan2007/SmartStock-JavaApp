package gy.deckers.studio;

import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import publishing.PublicationImport;
import static org.junit.jupiter.api.Assertions.*;

class BannerResamplingQualityTest {
    @Test void fullResolutionRendererUsesPreparedPixelsAndFallsBackWhenReclaimed() throws Exception {
        var source=BannerDocuments.create(1,1,"IN");
        var original=new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB);
        var bytes=new ByteArrayOutputStream();ImageIO.write(original,"PNG",bytes);
        PublicationImport.addEncodedImage(source,source.pages.get(0),bytes.toByteArray(),0,0,25.4,25.4);
        var output=BannerImageResolution.resample(source,BannerImageResolution.belowTarget(source));
        String asset=output.pages.get(0).objects.get(0).asset;
        var file=output.assetFiles.get(asset);
        try {
            // An unavailable backing file proves that the first render avoids PNG decoding.
            output.assetFiles.put(asset,file.resolveSibling("missing-resampled-image.png"));
            renderWithoutWarnings(output);
            output.assetFiles.put(asset,file);
            output.decodedAssets.get(asset).clear();
            renderWithoutWarnings(output);
            assertFalse(publishing.Publication.JSON.toJson(output).contains("decodedAssets"));
        } finally {output.assetFiles.put(asset,file);BannerImageResolution.cleanup(source,output);}
    }
    private static void renderWithoutWarnings(publishing.Publication output) {
        var canvas=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
        var g=canvas.createGraphics();var renderer=new publishing.PublicationRenderer(output);
        try {g.scale(300/25.4,300/25.4);renderer.page(g,0,java.util.Map.of());}
        finally {g.dispose();}
        assertTrue(renderer.warnings().isEmpty(),renderer.warnings().toString());
    }
    @Test void unchangedJobsReusePixelsButChangedSourceInvalidatesCache() throws Exception {
        var source = BannerDocuments.create(3,2,"IN");
        var image = new BufferedImage(20,20,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5,5,0xff123456);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image,"PNG",bytes);
        PublicationImport.addEncodedImage(source,source.pages.get(0),bytes.toByteArray(),0,0,25.4,25.4);
        var pictures = BannerImageResolution.belowTarget(source);
        var first = BannerImageResolution.resample(source,pictures);
        var second = BannerImageResolution.resample(source,pictures);
        try {
            String a=first.pages.get(0).objects.get(0).asset,b=second.pages.get(0).objects.get(0).asset;
            assertSame(first.decodedAssets.get(a).get(), second.decodedAssets.get(b).get());
            assertNotEquals(first.assetFiles.get(a),second.assetFiles.get(b));
            BannerImageResolution.cleanup(source,first);
            assertNotNull(ImageIO.read(second.assetFiles.get(b).toFile()));
            image.setRGB(5,5,0xffabcdef);bytes.reset();ImageIO.write(image,"PNG",bytes);
            String original=source.pages.get(0).objects.get(0).asset;
            source.assetFiles.remove(original);
            source.assets.put(original,java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
            var changed=BannerImageResolution.resample(source,pictures);
            try {
                String c=changed.pages.get(0).objects.get(0).asset;
                assertNotSame(second.decodedAssets.get(b).get(),changed.decodedAssets.get(c).get());
                assertNotEquals(ImageIO.read(second.assetFiles.get(b).toFile()).getRGB(82,82),
                        ImageIO.read(changed.assetFiles.get(c).toFile()).getRGB(82,82));
            } finally {BannerImageResolution.cleanup(source,changed);}
        } finally {BannerImageResolution.cleanup(source,first);BannerImageResolution.cleanup(source,second);}
    }

    @Test void independentImagesKeepIdenticalBicubicPixels() throws Exception {
        var source = BannerDocuments.create(3,2,"IN");
        var originals = new java.util.ArrayList<BufferedImage>();
        for(int i=0;i<3;i++) {
            var image=new BufferedImage(23,19,BufferedImage.TYPE_INT_RGB);
            var random=new java.util.Random(112+i);
            for(int y=0;y<19;y++)for(int x=0;x<23;x++)image.setRGB(x,y,random.nextInt());
            originals.add(image);
            var bytes=new ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);
            PublicationImport.addEncodedImage(source,source.pages.get(0),bytes.toByteArray(),0,0,25.4,25.4);
        }
        var pictures=BannerImageResolution.belowTarget(source);
        var output=BannerImageResolution.resample(source,pictures);
        try {
            for(int i=0;i<3;i++) {
                var size=pictures.get(i).outputSize();
                var expected=new BufferedImage(size.width,size.height,BufferedImage.TYPE_INT_RGB);
                var g=expected.createGraphics();
                try {g.setComposite(AlphaComposite.Src);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(originals.get(i),0,0,size.width,size.height,null);
                } finally {g.dispose();}
                var actual=ImageIO.read(output.assetFiles.get(output.pages.get(0).objects.get(i).asset).toFile());
                assertArrayEquals(expected.getRGB(0,0,size.width,size.height,null,0,size.width),actual.getRGB(0,0,size.width,size.height,null,0,size.width));
            }
        } finally {BannerImageResolution.cleanup(source,output);}
    }
    @Test void repeatedArtworkSharesOutputAndPreservesEveryBicubicPixel() throws Exception {
        for (int type : new int[]{BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB}) {
            var image = new BufferedImage(40, 30, type);
            var random = new java.util.Random(72);
            for (int y=0;y<30;y++) for (int x=0;x<40;x++) image.setRGB(x,y,random.nextInt());
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image,"PNG",bytes);
            var source = BannerDocuments.create(3,2,"IN");
            PublicationImport.addEncodedImage(source,source.pages.get(0),bytes.toByteArray(),0,0,25.4,25.4);
            var first = source.pages.get(0).objects.get(0);
            first.cropX=.25; first.cropWidth=.5;
            // Repeated layers share the same immutable source asset.
            var duplicate = source.copy().pages.get(0).objects.get(0);
            source.pages.get(0).objects.add(duplicate);
            var pictures = BannerImageResolution.belowTarget(source);
            var output = BannerImageResolution.resample(source,pictures);
            var file = output.assetFiles.get(output.pages.get(0).objects.get(0).asset);
            try {
                assertEquals(output.pages.get(0).objects.get(0).asset,output.pages.get(0).objects.get(1).asset);
                var actual = ImageIO.read(file.toFile());
                var size = pictures.get(0).outputSize();
                var expected = new BufferedImage(size.width,size.height,type);
                var g = expected.createGraphics();
                try {
                    g.setComposite(AlphaComposite.Src);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(image.getSubimage(10,0,20,30),0,0,size.width,size.height,null);
                } finally {g.dispose();}
                assertArrayEquals(expected.getRGB(0,0,size.width,size.height,null,0,size.width),
                        actual.getRGB(0,0,size.width,size.height,null,0,size.width));
                assertArrayEquals(bytes.toByteArray(),source.assetBytes(first.asset));
            } finally {BannerImageResolution.cleanup(source,output);}
            assertFalse(Files.exists(file));
        }
    }
}
