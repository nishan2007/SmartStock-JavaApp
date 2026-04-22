package ui.helpers;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicMenuBarUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ThemeManager {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "device.properties");
    private static final String DARK_MODE_KEY = "dark_mode";

    private static final Color LIGHT_BACKGROUND = new Color(245, 247, 250);
    private static final Color LIGHT_SURFACE = Color.WHITE;
    private static final Color LIGHT_FIELD = Color.WHITE;
    private static final Color LIGHT_TEXT = new Color(17, 24, 39);
    private static final Color LIGHT_MUTED = new Color(55, 65, 81);
    private static final Color LIGHT_BORDER = new Color(203, 213, 225);
    private static final Color LIGHT_TABLE_HEADER = new Color(241, 245, 249);

    private static final Color DARK_BACKGROUND = new Color(18, 18, 18);
    private static final Color DARK_SURFACE = new Color(30, 30, 30);
    private static final Color DARK_FIELD = new Color(24, 24, 24);
    private static final Color DARK_TEXT = new Color(235, 235, 235);
    private static final Color DARK_MUTED = new Color(180, 180, 180);
    private static final Color DARK_BORDER = new Color(75, 75, 75);
    private static final Color DARK_TABLE_HEADER = new Color(38, 38, 38);
    private static final Color DARK_BUTTON = new Color(88, 88, 88);
    private static final Color DARK_BUTTON_TEXT = Color.WHITE;
    private static final Color DARK_MENU_BAR = new Color(42, 42, 42);

    private ThemeManager() {
    }

    public static boolean isDarkModeEnabled() {
        try {
            return Boolean.parseBoolean(loadProperties().getProperty(DARK_MODE_KEY, "false"));
        } catch (IOException ex) {
            return false;
        }
    }

    public static void setDarkModeEnabled(boolean enabled) throws IOException {
        Properties properties = loadProperties();
        properties.setProperty(DARK_MODE_KEY, String.valueOf(enabled));
        saveProperties(properties);
        applyLookAndFeelDefaults();
        applyToOpenWindows();
    }

    public static void applyLookAndFeelDefaults() {
        boolean dark = isDarkModeEnabled();
        Color background = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        Color surface = dark ? DARK_SURFACE : LIGHT_SURFACE;
        Color field = dark ? DARK_FIELD : LIGHT_FIELD;
        Color text = dark ? DARK_TEXT : LIGHT_TEXT;
        Color muted = dark ? DARK_MUTED : LIGHT_MUTED;
        Color border = dark ? DARK_BORDER : LIGHT_BORDER;
        Color tableHeader = dark ? DARK_TABLE_HEADER : LIGHT_TABLE_HEADER;
        Color button = dark ? DARK_BUTTON : surface;
        Color buttonText = dark ? DARK_BUTTON_TEXT : text;
        Color menuBar = dark ? DARK_MENU_BAR : surface;

        UIManager.put("Panel.background", background);
        UIManager.put("OptionPane.background", surface);
        UIManager.put("OptionPane.messageForeground", text);
        UIManager.put("Label.foreground", text);
        UIManager.put("Button.background", button);
        UIManager.put("Button.foreground", buttonText);
        UIManager.put("Button.disabledText", muted);
        UIManager.put("ToggleButton.background", button);
        UIManager.put("ToggleButton.foreground", buttonText);
        UIManager.put("TextField.background", field);
        UIManager.put("TextField.foreground", text);
        UIManager.put("TextField.caretForeground", text);
        UIManager.put("TextField.inactiveBackground", field);
        UIManager.put("TextField.inactiveForeground", dark ? text : muted);
        UIManager.put("TextField.disabledBackground", dark ? new Color(42, 42, 42) : new Color(243, 244, 246));
        UIManager.put("TextField.disabledForeground", dark ? text : muted);
        UIManager.put("PasswordField.background", field);
        UIManager.put("PasswordField.foreground", text);
        UIManager.put("PasswordField.caretForeground", text);
        UIManager.put("PasswordField.inactiveBackground", field);
        UIManager.put("PasswordField.inactiveForeground", dark ? text : muted);
        UIManager.put("TextArea.background", field);
        UIManager.put("TextArea.foreground", text);
        UIManager.put("TextArea.caretForeground", text);
        UIManager.put("TextArea.inactiveBackground", field);
        UIManager.put("TextArea.inactiveForeground", dark ? text : muted);
        UIManager.put("ComboBox.background", field);
        UIManager.put("ComboBox.foreground", text);
        UIManager.put("ComboBox.disabledBackground", dark ? new Color(42, 42, 42) : new Color(243, 244, 246));
        UIManager.put("ComboBox.disabledForeground", dark ? text : muted);
        UIManager.put("CheckBox.background", background);
        UIManager.put("CheckBox.foreground", text);
        UIManager.put("Table.background", surface);
        UIManager.put("Table.foreground", text);
        UIManager.put("Table.gridColor", border);
        UIManager.put("Table.selectionBackground", dark ? new Color(30, 64, 175) : new Color(219, 234, 254));
        UIManager.put("Table.selectionForeground", text);
        UIManager.put("TableHeader.background", tableHeader);
        UIManager.put("TableHeader.foreground", text);
        UIManager.put("ScrollPane.background", background);
        UIManager.put("Viewport.background", surface);
        UIManager.put("MenuBar.background", menuBar);
        UIManager.put("MenuBar.foreground", text);
        UIManager.put("MenuBar.selectionBackground", dark ? new Color(64, 64, 64) : new Color(229, 231, 235));
        UIManager.put("MenuBar.selectionForeground", text);
        UIManager.put("Menu.background", menuBar);
        UIManager.put("Menu.foreground", text);
        UIManager.put("Menu.selectionBackground", dark ? new Color(64, 64, 64) : new Color(229, 231, 235));
        UIManager.put("Menu.selectionForeground", text);
        UIManager.put("MenuItem.background", surface);
        UIManager.put("MenuItem.foreground", text);
        UIManager.put("MenuItem.selectionBackground", dark ? DARK_BUTTON : new Color(229, 231, 235));
        UIManager.put("MenuItem.selectionForeground", text);
        UIManager.put("PopupMenu.background", surface);
        UIManager.put("PopupMenu.foreground", text);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(border));
        UIManager.put("Separator.background", surface);
        UIManager.put("Separator.foreground", border);
        UIManager.put("PopupMenuSeparator.background", surface);
        UIManager.put("PopupMenuSeparator.foreground", border);
        UIManager.put("TabbedPane.background", background);
        UIManager.put("TabbedPane.foreground", text);
        UIManager.put("TitledBorder.titleColor", text);
    }

    public static void applyToOpenWindows() {
        for (Window window : Window.getWindows()) {
            if (window.isDisplayable()) {
                applyToWindow(window);
            }
        }
    }

    public static void applyToWindow(Window window) {
        if (window == null) {
            return;
        }
        applyLookAndFeelDefaults();
        SwingUtilities.updateComponentTreeUI(window);
        applyToComponent(window);
        window.invalidate();
        window.validate();
        window.repaint();
    }

    private static void applyToComponent(Component component) {
        boolean dark = isDarkModeEnabled();
        Color background = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        Color surface = dark ? DARK_SURFACE : LIGHT_SURFACE;
        Color field = dark ? DARK_FIELD : LIGHT_FIELD;
        Color text = dark ? DARK_TEXT : LIGHT_TEXT;
        Color muted = dark ? DARK_MUTED : LIGHT_MUTED;
        Color border = dark ? DARK_BORDER : LIGHT_BORDER;
        Color buttonColor = dark ? DARK_BUTTON : surface;
        Color buttonText = dark ? DARK_BUTTON_TEXT : text;
        Color menuBarColor = dark ? DARK_MENU_BAR : surface;

        if (component instanceof JPopupMenu popupMenu) {
            if (dark) {
                popupMenu.setUI(new BasicPopupMenuUI());
            }
            popupMenu.setBackground(surface);
            popupMenu.setForeground(text);
            popupMenu.setOpaque(true);
            popupMenu.setBorder(BorderFactory.createLineBorder(border));
        } else if (component instanceof JSeparator separator) {
            separator.setBackground(surface);
            separator.setForeground(border);
            separator.setOpaque(true);
            separator.setPreferredSize(new Dimension(1, 1));
        } else if (component instanceof JMenuBar menuBar) {
            if (dark) {
                menuBar.setUI(new BasicMenuBarUI());
            }
            menuBar.setBackground(menuBarColor);
            menuBar.setForeground(text);
            menuBar.setOpaque(true);
            menuBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        } else if (component instanceof JMenu menu) {
            if (dark) {
                menu.setUI(new BasicMenuUI());
            }
            boolean topLevelMenu = menu.getParent() instanceof JMenuBar;
            menu.setBackground(topLevelMenu ? menuBarColor : surface);
            menu.setForeground(text);
            menu.setOpaque(true);
            menu.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        } else if (component instanceof JMenuItem menuItem) {
            if (dark) {
                menuItem.setUI(new BasicMenuItemUI());
            }
            menuItem.setBackground(surface);
            menuItem.setForeground(text);
            menuItem.setOpaque(true);
        } else if (component instanceof JTable table) {
            table.setBackground(surface);
            table.setForeground(text);
            table.setGridColor(border);
            table.setSelectionBackground(dark ? new Color(30, 64, 175) : new Color(219, 234, 254));
            table.setSelectionForeground(text);
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                header.setBackground(dark ? DARK_TABLE_HEADER : LIGHT_TABLE_HEADER);
                header.setForeground(text);
            }
        } else if (component instanceof JTextComponent textComponent) {
            component.setBackground(field);
            component.setForeground(text);
            textComponent.setCaretColor(text);
            if (!component.isEnabled() || !textComponent.isEditable()) {
                component.setBackground(dark ? new Color(42, 42, 42) : new Color(243, 244, 246));
                component.setForeground(dark ? text : muted);
            }
        } else if (component instanceof JComboBox<?> comboBox) {
            if (dark) {
                comboBox.setUI(new BasicComboBoxUI());
            }
            component.setBackground(field);
            component.setForeground(text);
            if (!component.isEnabled()) {
                component.setBackground(dark ? new Color(42, 42, 42) : new Color(243, 244, 246));
                component.setForeground(dark ? text : muted);
            }
        } else if (component instanceof AbstractButton button) {
            if (button instanceof JCheckBox || button instanceof JRadioButton) {
                button.setBackground(background);
                button.setForeground(text);
            } else if (Boolean.TRUE.equals(button.getClientProperty("SmartStock.customPaintedButton"))) {
                button.setOpaque(false);
                button.setContentAreaFilled(false);
            } else if (!(button instanceof JMenuItem) && isNeutralColor(button.getBackground())) {
                button.setUI(new BasicButtonUI());
                button.setBackground(buttonColor);
                button.setForeground(buttonText);
                button.setOpaque(true);
                button.setContentAreaFilled(true);
                button.setBorderPainted(true);
            } else if (button instanceof JMenuItem) {
                button.setBackground(surface);
                button.setForeground(text);
            }
        } else if (component instanceof JLabel label) {
            if (Boolean.TRUE.equals(label.getClientProperty("SmartStock.preserveForeground"))) {
                return;
            } else if (dark && ("menuButtonTitle".equals(label.getName()) || "menuButtonDescription".equals(label.getName()))) {
                label.setForeground(Color.WHITE);
            } else {
                label.setForeground(label.isEnabled() ? text : muted);
            }
        } else if (component instanceof JPanel || component instanceof JScrollPane || component instanceof JViewport || component instanceof JTabbedPane) {
            component.setBackground(component instanceof JViewport ? surface : background);
            component.setForeground(text);
        }

        if (component instanceof JComponent jComponent) {
            updateBorder(jComponent, text, border);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyToComponent(child);
            }
        }
    }

    private static void updateBorder(JComponent component, Color text, Color borderColor) {
        Border border = component.getBorder();
        if (border instanceof TitledBorder titledBorder) {
            titledBorder.setTitleColor(text);
            if (titledBorder.getBorder() == null) {
                titledBorder.setBorder(BorderFactory.createLineBorder(borderColor));
            }
        }
    }

    private static boolean isNeutralColor(Color color) {
        if (color == null || color instanceof UIResource) {
            return true;
        }
        int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        int min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        return max - min <= 24;
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        return properties;
    }

    private static void saveProperties(Properties properties) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock local device settings");
        }
    }
}
