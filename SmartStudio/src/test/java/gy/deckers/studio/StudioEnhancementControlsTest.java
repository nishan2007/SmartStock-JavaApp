package gy.deckers.studio;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioEnhancementControlsTest {
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void invoke(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(object);
    }
    @Test void enhancementAvailabilityAndOperationSelectionControlProcessing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SmartStudio studio = new SmartStudio(false);
            try {
                var source = studio.getClass().getDeclaredField("sourceBytes"); source.setAccessible(true); source.set(studio, new byte[]{1});
                JComboBox<?> scale = (JComboBox<?>) field(studio, "enhancement");
                JCheckBox removal = (JCheckBox) field(studio, "removeBackground");
                JButton process = (JButton) field(studio, "remove");
                invoke(studio, "updateButtons"); assertFalse(scale.isEnabled()); assertTrue(process.isEnabled());
                removal.doClick(); assertFalse(process.isEnabled());
                var brand = new JsonObject(); brand.addProperty("name", "Deckers"); brand.addProperty("motto", "Image Studio");
                brand.addProperty("studioEnhancementAvailable", true);
                var apply = studio.getClass().getDeclaredMethod("applyBrand", JsonObject.class); apply.setAccessible(true); apply.invoke(studio, brand);
                invoke(studio, "updateButtons"); assertTrue(scale.isEnabled());
                scale.setSelectedIndex(1); assertTrue(process.isEnabled());
                assertFalse(((JComboBox<?>) field(studio, "quality")).isEnabled());
                removal.doClick(); assertTrue(((JComboBox<?>) field(studio, "quality")).isEnabled());
                scale.setSelectedIndex(2); assertTrue(process.isEnabled());
                CardLayout cards = (CardLayout) field(studio, "cards"); cards.show((JPanel) field(studio, "pages"), "studio");
                for (int width : new int[]{1100, 820}) {
                    studio.setSize(width, 760); studio.addNotify(); studio.validate();
                    assertFits(removal); assertFits(scale); assertFits((Component) field(studio, "cleanEdges"));
                    assertFits((Component) field(studio, "paste"));
                    assertFits((Component) field(studio,"processingMode"));assertFits((Component) field(studio,"cancelImage"));
                    var image = new BufferedImage(studio.getContentPane().getWidth(), studio.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics(); studio.getContentPane().printAll(graphics); graphics.dispose();
                    Path output = Path.of("target", "image-studio-" + width + ".png"); Files.createDirectories(output.getParent());
                    javax.imageio.ImageIO.write(image, "png", output.toFile());
                }
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { studio.dispose(); }
        });
    }
    private static void assertFits(Component component) {
        assertTrue(component.getX() >= 0 && component.getY() >= 0
                && component.getX() + component.getWidth() <= component.getParent().getWidth()
                && component.getY() + component.getHeight() <= component.getParent().getHeight(),
                "Image Studio control is clipped: " + component);
    }
}
