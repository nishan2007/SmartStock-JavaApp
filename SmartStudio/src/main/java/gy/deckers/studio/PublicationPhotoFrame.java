package gy.deckers.studio;

import publishing.Publication;
import publishing.PublicationImport;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** A crop that keeps the same zoom and relative pan when a photo is replaced. */
record PublicationPhotoFrame(double zoom, double horizontal, double vertical) {
    static final PublicationPhotoFrame CENTERED = new PublicationPhotoFrame(1, .5, .5);

    static BufferedImage source(Publication document, Publication.Element layer) throws IOException {
        try (var stream = document.openAsset(layer.asset)) {
            return PublicationImport.decodeImage(stream, 3000);
        }
    }

    static PublicationPhotoFrame from(Publication.Element layer, BufferedImage source) {
        var base = CENTERED.crop(layer, source);
        double zoom = Math.max(1, Math.min(4, Math.min(base.width / layer.cropWidth, base.height / layer.cropHeight)));
        double x = fraction(layer.cropX, 1 - layer.cropWidth);
        double y = fraction(layer.cropY, 1 - layer.cropHeight);
        return new PublicationPhotoFrame(zoom, x, y);
    }

    private static double fraction(double offset, double available) {
        return available < .000001 ? .5 : Math.max(0, Math.min(1, offset / available));
    }

    private Crop crop(Publication.Element layer, BufferedImage source) {
        double aspect = (layer.width / layer.height) / ((double)source.getWidth() / source.getHeight());
        double width = Math.min(1, aspect) / zoom;
        double height = Math.min(1, 1 / aspect) / zoom;
        return new Crop((1 - width) * horizontal, (1 - height) * vertical, width, height);
    }

    void apply(Publication.Element layer, BufferedImage source) {
        var crop = crop(layer, source);
        layer.cropX = crop.x;
        layer.cropY = crop.y;
        layer.cropWidth = crop.width;
        layer.cropHeight = crop.height;
    }

    static PublicationPhotoFrame choose(Component owner, Publication.Element layer, BufferedImage source) {
        var current = from(layer, source);
        var zoom = new JSlider(100, 400, (int)Math.round(current.zoom * 100));
        var horizontal = new JSlider(0, 100, (int)Math.round(current.horizontal * 100));
        var vertical = new JSlider(0, 100, (int)Math.round(current.vertical * 100));
        for (var slider : new JSlider[]{zoom, horizontal, vertical}) {
            slider.setMajorTickSpacing(slider == zoom ? 100 : 25);
            slider.setPaintTicks(true);
            slider.setPaintLabels(true);
        }
        var preview = new JPanel() {
            @Override protected void paintComponent(Graphics original) {
                super.paintComponent(original);
                double scale = Math.min((getWidth() - 12) / layer.width, (getHeight() - 12) / layer.height);
                if (scale <= 0) return;
                double width = layer.width * scale;
                double height = layer.height * scale;
                var graphics = (Graphics2D)original.create();
                try {
                    graphics.translate((getWidth() - width) / 2, (getHeight() - height) / 2);
                    graphics.clip(new java.awt.geom.Rectangle2D.Double(0, 0, width, height));
                    var crop = selected(zoom, horizontal, vertical).crop(layer, source);
                    double xScale = width / (crop.width * source.getWidth());
                    double yScale = height / (crop.height * source.getHeight());
                    var transform = AffineTransform.getTranslateInstance(-width * crop.x / crop.width,
                            -height * crop.y / crop.height);
                    transform.scale(xScale, yScale);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, transform, null);
                } finally {
                    graphics.dispose();
                }
            }
        };
        preview.setPreferredSize(new Dimension(350, 230));
        var controls = new JPanel(new GridLayout(0, 1));
        addSlider(controls, "Zoom (%)", zoom);
        addSlider(controls, "Horizontal position (%)", horizontal);
        addSlider(controls, "Vertical position (%)", vertical);
        for (var slider : new JSlider[]{zoom, horizontal, vertical}) slider.addChangeListener(e -> preview.repaint());
        var content = new JPanel(new BorderLayout(0, 6));
        content.add(preview, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(owner, content, "Frame photo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        return selected(zoom, horizontal, vertical);
    }

    private static void addSlider(JPanel panel, String text, JSlider slider) {
        var label = new JLabel(text);
        label.setLabelFor(slider);
        panel.add(label);
        panel.add(slider);
    }

    private static PublicationPhotoFrame selected(JSlider zoom, JSlider horizontal, JSlider vertical) {
        return new PublicationPhotoFrame(zoom.getValue() / 100., horizontal.getValue() / 100., vertical.getValue() / 100.);
    }

    private record Crop(double x, double y, double width, double height) {}
}
