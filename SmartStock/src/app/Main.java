package app;

import ui.screens.WelcomeFrame;
import ui.helpers.ThemeManager;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            ThemeManager.applyLookAndFeelDefaults();
            new WelcomeFrame().setVisible(true);
        });
    }
}
