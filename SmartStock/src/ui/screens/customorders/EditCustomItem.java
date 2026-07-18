package ui.screens.customorders;

import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.*;

/** Entry point into the server-backed custom-item editor. */
public final class EditCustomItem extends JPanel {
    public EditCustomItem(Window parentWindow) {
        setLayout(new GridBagLayout());
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Edit Custom Items");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel detail = new JLabel("Search, edit, activate, and manage variants in the Custom Item Manager.");
        JButton open = new JButton("Open Custom Item Manager");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        open.setAlignmentX(Component.CENTER_ALIGNMENT);
        open.addActionListener(e -> WindowHelper.showPosWindow(new CustomOrderItems(), parentWindow));
        card.add(title); card.add(Box.createVerticalStrut(12)); card.add(detail);
        card.add(Box.createVerticalStrut(18)); card.add(open);
        add(card);
    }
}
