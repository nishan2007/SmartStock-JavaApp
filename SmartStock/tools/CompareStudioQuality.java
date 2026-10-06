import services.CatalogStudioImageProcessor;
import services.StudioBackgroundService;
import services.StudioQuality;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;

/** Local comparison helper; never calls a live server or saves customer files into the repository. */
public final class CompareStudioQuality {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Supply source image, Fast model, Best model, output directory.");
        byte[] source;
        try (var stream = Files.newInputStream(Path.of(args[0]))) { source = stream.readNBytes(6 * 1024 * 1024 + 1); }
        StudioBackgroundService.validate(source);
        Path output = Path.of(args[3]); Files.createDirectories(output);
        var original = ImageIO.read(new ByteArrayInputStream(source));
        BufferedImage comparison = new BufferedImage(1536, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = comparison.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 1536, 600);
        draw(g, original, "Original", 0);
        StringBuilder timings = new StringBuilder();
        int column = 1;
        for (StudioQuality quality : StudioQuality.values()) {
            long start = System.nanoTime();
            try (var processor = new CatalogStudioImageProcessor(Path.of(args[quality == StudioQuality.FAST ? 1 : 2]), quality)) {
                byte[] png = processor.removeBackgroundPng(source, quality == StudioQuality.BEST);
                var image = ImageIO.read(new ByteArrayInputStream(png));
                if (image.getWidth() != original.getWidth() || image.getHeight() != original.getHeight())
                    throw new IllegalStateException("Export dimensions changed.");
                Files.write(output.resolve(quality.name().toLowerCase() + ".png"), png);
                double seconds = (System.nanoTime() - start) / 1e9;
                String line = String.format(java.util.Locale.ROOT, "%s: %.2f seconds", quality, seconds);
                timings.append(line).append(System.lineSeparator()); System.out.println(line);
                draw(g, image, line, column++);
            }
        }
        g.dispose(); ImageIO.write(comparison, "png", output.resolve("comparison.png").toFile());
        Files.writeString(output.resolve("timings.txt"), timings);
    }
    private static void draw(Graphics2D g, BufferedImage image, String title, int column) {
        int start = column * 512;
        for (int y = 44; y < 600; y += 16) for (int x = start; x < start + 512; x += 16) {
            g.setColor((x / 16 + y / 16) % 2 == 0 ? Color.WHITE : new Color(220,220,220)); g.fillRect(x,y,16,16);
        }
        double scale = Math.min(488.0 / image.getWidth(), 532.0 / image.getHeight());
        int w = (int)(image.getWidth()*scale), h = (int)(image.getHeight()*scale);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image,start+(512-w)/2,44+(556-h)/2,w,h,null);
        g.setColor(new Color(112,34,168));g.setFont(new Font("SansSerif",Font.BOLD,18));g.drawString(title,start+12,28);
    }
}
