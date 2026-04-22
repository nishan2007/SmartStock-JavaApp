package app;

import ui.screens.WelcomeFrame;
import ui.helpers.ThemeManager;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            ThemeManager.applyLookAndFeelDefaults();
            new WelcomeFrame().setVisible(true);
          // new NewItem().setVisible(true);
           // new EmployeeManagement().setVisible(true);
           // new Roles_Permission().setVisible(true);
           // new ViewSales().setVisible(true);
           // new MainMenu().setVisible(true);
        });
    }
}
