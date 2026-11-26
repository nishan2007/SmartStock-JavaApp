package publishing;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

public final class PublicationPreview {
    private PublicationPreview() {}

    public static byte[] documentJson(Publication document, Publication artwork) throws IOException {
        int width = Math.max(1, (int)Math.round(640 * artwork.width / Math.max(artwork.width, artwork.height)));
        int height = Math.max(1, (int)Math.round(640 * artwork.height / Math.max(artwork.width, artwork.height)));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.scale(width / artwork.width, height / artwork.height);
            new PublicationRenderer(artwork, 1024).page(graphics, 0, Map.of());
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var json = Publication.JSON.toJsonTree(document).getAsJsonObject();
        if (bytes.size() <= 1024 * 1024) {
            json.addProperty("previewPng", Base64.getEncoder().encodeToString(bytes.toByteArray()));
        }
        return Publication.JSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }
}
