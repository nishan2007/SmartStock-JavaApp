package ui.helpers;

import managers.SupabaseSessionManager;
import utils.ImageOptimizationHelper;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class ProductImageHelper {
    private static final String PRODUCT_IMAGE_BUCKET = getConfig("PRODUCT_IMAGE_BUCKET", "Product Images");
    private static final long MAX_ORIGINAL_IMAGE_BYTES = 15L * 1024L * 1024L;
    private static final long MAX_PRODUCT_UPLOAD_BYTES = 200L * 1024L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private ProductImageHelper() {
    }

    public static ImageSelector createImageSelector(Component parent) {
        return new ImageSelector(parent);
    }

    public static JLabel createImagePreview(String imageUrl, int width, int height) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(width, height));
        label.setMinimumSize(new Dimension(width, height));
        label.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        label.setOpaque(true);
        label.setBackground(new Color(248, 250, 252));
        setPreviewImage(label, imageUrl, width, height);
        return label;
    }

    public static boolean isRemoteImageUrl(String imageUrl) {
        return imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"));
    }

    public static String uploadLocalImageIfNeeded(String imageUrl) throws Exception {
        if (imageUrl == null || imageUrl.isBlank() || isRemoteImageUrl(imageUrl)) {
            return imageUrl == null ? "" : imageUrl.trim();
        }

        File imageFile = new File(imageUrl.trim());
        if (!imageFile.isFile()) {
            throw new IllegalArgumentException("The selected image file was not found.");
        }

        return uploadProductImage(imageFile);
    }

    public static void setPreviewImage(JLabel label, String imageUrl, int width, int height) {
        if (imageUrl == null || imageUrl.isBlank()) {
            label.setIcon(null);
            label.setText("No Image");
            label.setForeground(new Color(101, 116, 139));
            return;
        }

        ImageIcon icon = loadScaledIcon(imageUrl.trim(), width, height);
        if (icon == null) {
            label.setIcon(null);
            label.setText("Image unavailable");
            label.setForeground(new Color(185, 28, 28));
            return;
        }

        label.setText("");
        label.setIcon(icon);
    }

    private static ImageIcon loadScaledIcon(String imageUrl, int maxWidth, int maxHeight) {
        try {
            Image image;
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                URL url = URI.create(imageUrl).toURL();
                image = ImageIO.read(url);
            } else {
                image = ImageIO.read(new File(imageUrl));
            }

            if (image == null) {
                return null;
            }

            int originalWidth = image.getWidth(null);
            int originalHeight = image.getHeight(null);
            if (originalWidth <= 0 || originalHeight <= 0) {
                return null;
            }

            double scale = Math.min((double) maxWidth / originalWidth, (double) maxHeight / originalHeight);
            int width = Math.max(1, (int) Math.round(originalWidth * scale));
            int height = Math.max(1, (int) Math.round(originalHeight * scale));
            Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception ex) {
            return null;
        }
    }

    public static class ImageSelector extends JPanel {
        private final Component parent;
        private final JTextField imageUrlField;
        private final JLabel previewLabel;

        private ImageSelector(Component parent) {
            super(new BorderLayout(8, 8));
            this.parent = parent;
            setOpaque(false);

            previewLabel = createImagePreview("", 150, 110);
            imageUrlField = new JTextField();

            JButton browseButton = new JButton("Browse");
            JButton previewButton = new JButton("Preview");
            JButton uploadButton = new JButton("Upload");
            JButton clearButton = new JButton("Clear");

            JPanel fieldPanel = new JPanel(new BorderLayout(6, 6));
            fieldPanel.setOpaque(false);
            fieldPanel.add(imageUrlField, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.add(browseButton);
            buttonPanel.add(previewButton);
            buttonPanel.add(uploadButton);
            buttonPanel.add(clearButton);
            fieldPanel.add(buttonPanel, BorderLayout.SOUTH);

            JPanel wrapper = new JPanel(new BorderLayout(8, 8));
            wrapper.setOpaque(false);
            wrapper.setBorder(new EmptyBorder(0, 0, 0, 0));
            wrapper.add(previewLabel, BorderLayout.WEST);
            wrapper.add(fieldPanel, BorderLayout.CENTER);

            add(wrapper, BorderLayout.CENTER);

            browseButton.addActionListener(e -> chooseLocalImage());
            previewButton.addActionListener(e -> refreshPreview());
            uploadButton.addActionListener(e -> uploadCurrentImage());
            clearButton.addActionListener(e -> setImageUrl(""));
            imageUrlField.addActionListener(e -> refreshPreview());
        }

        public String getImageUrl() {
            return imageUrlField.getText().trim();
        }

        public void setImageUrl(String imageUrl) {
            imageUrlField.setText(imageUrl == null ? "" : imageUrl);
            refreshPreview();
        }

        public void setSelectorEnabled(boolean enabled) {
            imageUrlField.setEnabled(enabled);
            for (Component child : getAllChildren(this)) {
                if (child instanceof JButton) {
                    child.setEnabled(enabled);
                }
            }
        }

        private void refreshPreview() {
            setPreviewImage(previewLabel, getImageUrl(), 150, 110);
        }

        private void chooseLocalImage() {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "jpeg", "gif", "bmp", "webp"));
            int result = chooser.showOpenDialog(parent);
            if (result == JFileChooser.APPROVE_OPTION) {
                setImageUrl(chooser.getSelectedFile().getAbsolutePath());
            }
        }

        private void uploadCurrentImage() {
            String imagePath = getImageUrl();
            if (imagePath.isBlank()) {
                JOptionPane.showMessageDialog(parent, "Choose an image file first.");
                return;
            }

            if (isRemoteImageUrl(imagePath)) {
                JOptionPane.showMessageDialog(parent, "This image is already a URL.");
                return;
            }

            File imageFile = new File(imagePath);
            if (!imageFile.isFile()) {
                JOptionPane.showMessageDialog(parent, "The selected image file was not found.");
                return;
            }

            try {
                setImageUrl(uploadProductImage(imageFile));
                JOptionPane.showMessageDialog(parent, "Image uploaded successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Image upload failed.\n\n" + ex.getMessage(),
                        "Upload Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }

        private java.util.List<Component> getAllChildren(Container container) {
            java.util.List<Component> children = new java.util.ArrayList<>();
            for (Component child : container.getComponents()) {
                children.add(child);
                if (child instanceof Container childContainer) {
                    children.addAll(getAllChildren(childContainer));
                }
            }
            return children;
        }
    }

    private static String uploadProductImage(File imageFile) throws Exception {
        try (ImageOptimizationHelper.OptimizedImage optimizedImage = ImageOptimizationHelper.optimizeForUpload(
                imageFile,
                "product-image",
                1200,
                1200,
                0.78f,
                MAX_ORIGINAL_IMAGE_BYTES,
                MAX_PRODUCT_UPLOAD_BYTES,
                false
        )) {
            String accessToken = SupabaseSessionManager.getValidAccessToken();
            String objectPath = "products/" + System.currentTimeMillis() + "-" + sanitizeFilename(optimizedImage.filename());
            String encodedBucket = encodePathSegment(PRODUCT_IMAGE_BUCKET);
            String encodedObjectPath = encodeObjectPath(objectPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                            + "/storage/v1/object/"
                            + encodedBucket
                            + "/"
                            + encodedObjectPath))
                    .timeout(Duration.ofSeconds(45))
                    .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", optimizedImage.contentType())
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofFile(optimizedImage.file().toPath()))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Supabase Storage returned HTTP "
                        + response.statusCode()
                        + " while uploading to bucket "
                        + PRODUCT_IMAGE_BUCKET
                        + ": "
                        + response.body());
            }

            return SupabaseSessionManager.getSupabaseUrl()
                    + "/storage/v1/object/public/"
                    + encodedBucket
                    + "/"
                    + encodedObjectPath;
        }
    }

    private static String sanitizeFilename(String filename) {
        String sanitized = filename == null ? "product-image" : filename.trim();
        sanitized = sanitized.replaceAll("[^A-Za-z0-9._-]", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "product-image";
        }
        return sanitized;
    }

    private static String encodeObjectPath(String objectPath) {
        String[] parts = objectPath.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String part : parts) {
            if (!encoded.isEmpty()) {
                encoded.append("/");
            }
            encoded.append(encodePathSegment(part));
        }
        return encoded.toString();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String getConfig(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return value;
    }
}
