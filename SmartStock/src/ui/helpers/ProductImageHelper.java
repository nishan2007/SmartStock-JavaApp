package ui.helpers;

import services.StorageObjectNameBuilder;
import utils.ImageCacheManager;
import utils.ImageOptimizationHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public final class ProductImageHelper {
    private static final String PRODUCT_IMAGE_BUCKET = getConfig("PRODUCT_IMAGE_BUCKET", "Product Images");
    private static final long MAX_ORIGINAL_IMAGE_BYTES = 15L * 1024L * 1024L;
    private static final long MAX_PRODUCT_UPLOAD_BYTES = 200L * 1024L;
    private ProductImageHelper() {
    }

    public static ImageSelector createImageSelector(Component parent) {
        return new ImageSelector(parent, false);
    }

    public static ImageSelector createSimpleImageSelector(Component parent) {
        return new ImageSelector(parent, true);
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
        return ImageCacheManager.isRemoteImageUrl(imageUrl);
    }

    public static String uploadLocalImageIfNeeded(String imageUrl, ProductImageNaming naming) throws Exception {
        if (imageUrl == null || imageUrl.isBlank() || isRemoteImageUrl(imageUrl)) {
            return imageUrl == null ? "" : imageUrl.trim();
        }

        File imageFile = new File(imageUrl.trim());
        if (!imageFile.isFile()) {
            throw new IllegalArgumentException("The selected image file was not found.");
        }

        return uploadProductImage(imageFile, naming);
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
            Image image = ImageCacheManager.loadImage(imageUrl);

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

        private ImageSelector(Component parent, boolean simplePresentation) {
            super(new BorderLayout(8, 8));
            this.parent = parent;
            setOpaque(false);

            previewLabel = createImagePreview("", 150, 110);
            imageUrlField = new JTextField();

            if (simplePresentation) {
                buildSimplePresentation();
                return;
            }

            JButton browseButton = new JButton("Browse");
            JButton previewButton = new JButton("Preview");
            JButton clearButton = new JButton("Clear");

            JPanel fieldPanel = new JPanel(new BorderLayout(6, 6));
            fieldPanel.setOpaque(false);
            fieldPanel.add(imageUrlField, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.add(browseButton);
            buttonPanel.add(previewButton);
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
            clearButton.addActionListener(e -> setImageUrl(""));
            imageUrlField.addActionListener(e -> refreshPreview());
        }

        private void buildSimplePresentation() {
            JLabel guidanceLabel = new JLabel("<html><b>Product photo</b><br>Choose a file or paste an image URL. It uploads when the item is saved.</html>");
            guidanceLabel.setVerticalAlignment(SwingConstants.TOP);

            JButton chooseButton = new JButton("Choose Image");
            JButton urlButton = new JButton("Use URL...");
            JButton removeButton = new JButton("Remove");

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.add(chooseButton);
            buttonPanel.add(urlButton);
            buttonPanel.add(removeButton);

            JPanel actionsPanel = new JPanel(new BorderLayout(0, 10));
            actionsPanel.setOpaque(false);
            actionsPanel.add(guidanceLabel, BorderLayout.NORTH);
            actionsPanel.add(buttonPanel, BorderLayout.CENTER);

            add(previewLabel, BorderLayout.WEST);
            add(actionsPanel, BorderLayout.CENTER);

            chooseButton.addActionListener(e -> chooseLocalImage());
            urlButton.addActionListener(e -> promptForImageUrl());
            removeButton.addActionListener(e -> setImageUrl(""));
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

        private void promptForImageUrl() {
            String currentValue = isRemoteImageUrl(getImageUrl()) ? getImageUrl() : "";
            String imageUrl = JOptionPane.showInputDialog(parent, "Paste the product image URL:", currentValue);
            if (imageUrl != null) {
                setImageUrl(imageUrl.trim());
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

    private static String uploadProductImage(File imageFile, ProductImageNaming naming) throws Exception {
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
            ProductImageNaming safeNaming = naming == null ? ProductImageNaming.empty() : naming;
            String filename = StorageObjectNameBuilder.filename(
                    optimizedImage.filename(), "jpg", Long.toString(System.currentTimeMillis()),
                    safeNaming.productName(), safeNaming.brand(), safeNaming.type(), safeNaming.size(),
                    safeNaming.variantName(), "product-image");
            String objectPath = "products/" + filename;
            String publicUrl = services.LanApiClient.uploadCloudFile(
                    PRODUCT_IMAGE_BUCKET, objectPath, optimizedImage.contentType(),
                    java.nio.file.Files.readAllBytes(optimizedImage.file().toPath()));
            ImageCacheManager.cacheUploadedImage(publicUrl, optimizedImage.file().toPath());
            return publicUrl;
        }
    }

    public record ProductImageNaming(String productName, String brand, String type,
                                     String size, String variantName) {
        public static ProductImageNaming empty() {
            return new ProductImageNaming("", "", "", "", "");
        }
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
