package ui.screens.customorders;

import javax.swing.*;
import java.awt.*;

/** Entry point into the server-backed custom-item editor. */
public final class NewCustomItem extends JPanel {
    public NewCustomItem(Window parentWindow) {
        setLayout(new GridBagLayout());
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Create a Custom Item");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel detail = new JLabel("Custom items, variants, materials, and pricing are managed in one screen.");
        JButton open = new JButton("Open Custom Item Manager");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        open.setAlignmentX(Component.CENTER_ALIGNMENT);
        open.addActionListener(e -> new CustomOrderItems());
        card.add(title); card.add(Box.createVerticalStrut(12)); card.add(detail);
        card.add(Box.createVerticalStrut(18)); card.add(open);
        add(card);
    }
}
