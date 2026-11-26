package ui.screens.customorders;

import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared presentation for the catalog lists and their focused editors. */
final class CustomCatalogDesign {
    private CustomCatalogDesign() { }

    static JPanel surface(LayoutManager layout) {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(layout);
        return panel;
    }

    static JLabel heading(String text, float size) {
        JLabel label = DeckersSwing.metaLabel(text);
        label.setFont(label.getFont().deriveFont(size));
        return label;
    }

    static JPanel heading(String title, String subtitle) {
        JPanel panel = surface(new GridLayout(0, 1, 0, 3));
        panel.add(heading(title, 21f));
        panel.add(new JLabel(subtitle));
        return panel;
    }

    static JLabel logo(int width, int height) {
        String asset = width <= 24 ? "app-icon.png" : "primary-horizontal.png";
        var url = CustomCatalogDesign.class.getResource("/Images/SmartStockLogoVariations/New/transparent/" + asset);
        ImageIcon icon = url == null ? DeckersLogoManager.loadSmartStockLogoIcon(CustomCatalogDesign.class) : new ImageIcon(url);
        if (icon == null) return heading("SmartStock", 16f);
        JLabel logo = new JLabel(new ImageIcon(DeckersLogoManager.scaleToFit(icon.getImage(), width, height)));
        if (DeckersPalette.dark()) {
            logo.setOpaque(true);
            logo.setBackground(Color.WHITE);
            logo.setBorder(new EmptyBorder(2, 4, 2, 4));
        }
        return logo;
    }

    static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    static JButton addButton(String text, Runnable action) {
        JButton button = primary(button(text, action));
        button.setIcon(OrdersIcons.icon("plus", Color.BLACK, 13));
        return button;
    }

    static JButton primary(JButton button) {
        button.putClientProperty("SmartStock.ownedButtonBackground", DeckersPalette.ORANGE);
        button.putClientProperty("SmartStock.ownedButtonForeground", Color.BLACK);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setForeground(Color.BLACK);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.ORANGE), new EmptyBorder(5, 11, 5, 11)));
        Runnable appearance = () -> button.setBackground(button.isEnabled() ? DeckersPalette.ORANGE : DeckersPalette.tableStripe());
        button.addPropertyChangeListener("enabled", event -> appearance.run());
        appearance.run();
        return button;
    }

    static void table(JTable table) {
        DeckersSwing.styleTable(table, DeckersPalette.PURPLE);
        table.setRowHeight(30);
        table.setShowHorizontalLines(true);
        table.setSelectionBackground(DeckersPalette.tilePressed(DeckersPalette.ORANGE));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                if (!selected) setBackground(row % 2 == 0 ? source.getBackground() : DeckersPalette.tableStripe());
                setForeground(selected ? source.getSelectionForeground() : source.getForeground());
                if (!focused) setBorder(new EmptyBorder(0, 7, 0, 4));
                String heading = source.getColumnName(column);
                boolean group = heading.equals("Item / Variant") && value != null && !value.toString().startsWith("    ");
                setFont(source.getFont().deriveFont(group ? Font.BOLD : Font.PLAIN));
                setHorizontalAlignment(heading.equals("Active") ? SwingConstants.CENTER : SwingConstants.LEFT);
                if (heading.equals("Active") && "Active".equals(value)) {
                    setForeground(DeckersPalette.dark() ? new Color(157, 224, 164) : new Color(26, 107, 50));
                    setFont(source.getFont().deriveFont(Font.BOLD));
                }
                return this;
            }
        });
        var header = table.getTableHeader();
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                setFont(header.getFont());
                setForeground(header.getForeground());
                setBackground(header.getBackground());
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.sectionBorder(DeckersPalette.PURPLE)),
                        new EmptyBorder(6, 8, 6, 4)));
                setIcon(null);
                if (source.getRowSorter() != null && !source.getRowSorter().getSortKeys().isEmpty()) {
                    var key = source.getRowSorter().getSortKeys().get(0);
                    if (key.getColumn() == source.convertColumnIndexToModel(column)) {
                        setIcon(UIManager.getIcon(key.getSortOrder() == SortOrder.ASCENDING
                                ? "Table.ascendingSortIcon" : "Table.descendingSortIcon"));
                    }
                }
                return this;
            }
        });
    }

    static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.getViewport().setBackground(DeckersPalette.surface());
        pane.setBorder(BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.PURPLE)));
        pane.getVerticalScrollBar().setUnitIncrement(20);
        return pane;
    }

    static JScrollPane multiline(JTextArea area, int height) {
        JScrollPane pane = new JScrollPane(area);
        pane.setPreferredSize(new Dimension(120, height));
        pane.setMinimumSize(new Dimension(70, height));
        return pane;
    }

    static void resizeDialog(JDialog dialog, int width, int height) {
        Window owner = dialog.getOwner();
        Rectangle usable = usableBounds(owner);
        dialog.setSize(Math.min(width, usable.width - 24), Math.min(height, usable.height - 24));
        dialog.setMinimumSize(new Dimension(Math.min(660, dialog.getWidth()), Math.min(420, dialog.getHeight())));
        dialog.setLocationRelativeTo(owner);
    }

    static Rectangle usableBounds(Window window) {
        GraphicsConfiguration config = window.getGraphicsConfiguration();
        Rectangle bounds = config.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    }

    static JButton fullScreen(JDialog dialog) {
        return fullScreen(dialog, () -> true, expanded -> { });
    }

    static JButton fullScreen(JDialog dialog, java.util.function.BooleanSupplier canToggle,
                              java.util.function.Consumer<Boolean> onToggle) {
        JButton button = new JButton("Full Screen");
        button.addActionListener(new java.awt.event.ActionListener() {
            private Rectangle restoreBounds;

            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (!canToggle.getAsBoolean()) return;
                if (restoreBounds == null) {
                    restoreBounds = dialog.getBounds();
                    dialog.setBounds(usableBounds(dialog));
                    button.setText("Restore");
                } else {
                    dialog.setBounds(restoreBounds);
                    restoreBounds = null;
                    button.setText("Full Screen");
                }
                onToggle.accept(restoreBounds != null);
            }
        });
        return button;
    }

    static void open(JDialog dialog) {
        var owner = (RootPaneContainer) dialog.getOwner();
        Component oldGlass = owner.getGlassPane();
        boolean wasVisible = oldGlass.isVisible();
        JComponent shade = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(new Color(15, 23, 42, 83));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        owner.setGlassPane(shade);
        shade.setVisible(true);
        try {
            dialog.setVisible(true);
        } finally {
            owner.setGlassPane(oldGlass);
            oldGlass.setVisible(wasVisible);
        }
    }

    record Section(String name, String title, JComponent content, JComponent leading) {
        Section(String name, JComponent content) { this(name, name, content, null); }
    }

    private static final class EditorBody extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(20, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 20);
        }
        @Override public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport viewport && viewport.getWidth() >= 530;
        }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static JPanel fields() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    static JPanel columns(JComponent left, JComponent right, JComponent... fullWidth) {
        JPanel columns = fields();
        JPanel pair = new JPanel(new GridLayout(1, 2, 20, 0));
        pair.setOpaque(false);
        pair.add(topAligned(left));
        pair.add(topAligned(right));
        GridBagConstraints position = new GridBagConstraints();
        position.fill = GridBagConstraints.HORIZONTAL;
        position.anchor = GridBagConstraints.NORTH;
        position.weightx = 1;
        position.gridx = 0;
        position.gridy = 0;
        columns.add(pair, position);
        position.insets = new Insets(8, 0, 0, 0);
        for (JComponent field : fullWidth) {
            position.gridy++;
            columns.add(field, position);
        }
        return topAligned(columns);
    }

    static JPanel topAligned(JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    static final class Editor extends JDialog {
        private boolean busy;
        private JLabel title;
        private Supplier<List<?>> snapshot = List::of;
        private List<?> baseline = List.of();
        private JButton[] footerButtons;
        private boolean[] enabledButtons;
        private JToggleButton firstSection;
        private final KeyEventDispatcher blockInput = event ->
                KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == this;

        Editor(Window owner, String name) {
            super(owner, name, ModalityType.DOCUMENT_MODAL);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent event) { close(); }
            });
            getRootPane().registerKeyboardAction(event -> close(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        void layoutEditor(JButton save, Section[] sections, JButton... actions) {
            layoutEditor(save, sections, false, actions);
        }

        void layoutEditor(JButton save, Section[] sections, boolean continuous, JButton... actions) {
            JPanel root = surface(new BorderLayout());
            JPanel banner = surface(new FlowLayout(FlowLayout.LEFT, 10, 8));
            banner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.sectionBorder(DeckersPalette.PURPLE)));
            banner.add(logo(24, 24));
            title = heading(getTitle(), 16f);
            banner.add(title);
            root.add(banner, BorderLayout.NORTH);

            JPanel navigation = surface(new GridBagLayout());
            navigation.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, DeckersPalette.border()), new EmptyBorder(12, 8, 12, 8)));
            ButtonGroup group = new ButtonGroup();
            JPanel pages = new JPanel(new CardLayout());
            JPanel stack = new EditorBody();
            stack.setBackground(DeckersPalette.surface());
            stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
            JScrollPane continuousScroll = scroll(stack);
            continuousScroll.setBorder(null);
            GridBagConstraints position = new GridBagConstraints();
            position.gridx = 0;
            position.gridy = 0;
            position.fill = GridBagConstraints.HORIZONTAL;
            position.weightx = 1;
            position.insets = new Insets(0, 0, 5, 0);
            for (Section section : sections) {
                JToggleButton tab = new JToggleButton(section.name());
                tab.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
                String icon = switch (section.name()) {
                    case "Pricing & stock" -> "payment";
                    case "Template" -> "production";
                    case "Photos", "Template & photos" -> "clip";
                    default -> "file";
                };
                tab.setHorizontalAlignment(SwingConstants.LEFT);
                tab.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
                tab.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
                tab.setOpaque(true);
                tab.setMargin(new Insets(7, 9, 7, 9));
                tab.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()),
                        new EmptyBorder(7, 9, 7, 9)));
                tab.addItemListener(event -> {
                    tab.setBackground(tab.isSelected() ? DeckersPalette.PURPLE : DeckersPalette.surface());
                    tab.setForeground(tab.isSelected() ? Color.WHITE : DeckersPalette.text());
                    tab.setIcon(OrdersIcons.icon(icon, tab.getForeground(), 12));
                });
                tab.setBackground(DeckersPalette.surface());
                tab.setForeground(DeckersPalette.text());
                tab.setIcon(OrdersIcons.icon(icon, tab.getForeground(), 12));
                group.add(tab);
                navigation.add(tab, position);
                position.gridy = group.getButtonCount();

                JPanel content = new EditorBody();
                content.setLayout(new BorderLayout(0, 8));
                content.setBackground(DeckersPalette.surface());
                content.setBorder(new EmptyBorder(continuous ? 9 : 14, 18, continuous ? 4 : 14, 18));
                JPanel groupContent = new JPanel(new BorderLayout(0, 8));
                groupContent.setOpaque(false);
                groupContent.add(heading(section.title(), 15f), BorderLayout.NORTH);
                groupContent.add(section.content());
                if (section.leading() != null) content.add(section.leading(), BorderLayout.NORTH);
                content.add(groupContent);
                if (continuous) {
                    content.setMaximumSize(new Dimension(Integer.MAX_VALUE, content.getPreferredSize().height));
                    stack.add(content);
                    tab.addActionListener(event -> stack.scrollRectToVisible(new Rectangle(0, content.getY(),
                            1, continuousScroll.getViewport().getHeight())));
                } else {
                    JScrollPane scroller = scroll(content);
                    scroller.setBorder(null);
                    pages.add(scroller, section.name());
                    tab.addActionListener(event -> ((CardLayout) pages.getLayout()).show(pages, section.name()));
                }
            }
            firstSection = (JToggleButton) group.getElements().nextElement();
            firstSection.setSelected(true);
            position.weighty = 1;
            navigation.add(Box.createGlue(), position);
            root.add(navigation, BorderLayout.WEST);
            if (continuous) stack.add(Box.createVerticalGlue());
            root.add(continuous ? continuousScroll : pages, BorderLayout.CENTER);

            JPanel footer = surface(new FlowLayout(FlowLayout.RIGHT, 7, 8));
            footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DeckersPalette.border()));
            footer.add(primary(save));
            for (JButton action : actions) footer.add(action);
            footerButtons = new JButton[actions.length + 1];
            footerButtons[0] = save;
            System.arraycopy(actions, 0, footerButtons, 1, actions.length);
            root.add(footer, BorderLayout.SOUTH);
            setContentPane(root);
            getRootPane().setDefaultButton(save);
            resizeDialog(this, 950, 670);
            getGlassPane().addMouseListener(new java.awt.event.MouseAdapter() { });
            getGlassPane().addMouseMotionListener(new java.awt.event.MouseMotionAdapter() { });
            getGlassPane().addMouseWheelListener(event -> event.consume());
        }

        void rename(String text) { setTitle(text); title.setText(text); }

        void showDetails() { firstSection.doClick(0); }

        void watchDraft(Supplier<List<?>> value) {
            snapshot = value;
            markSaved();
        }

        void markSaved() { baseline = snapshot.get(); }

        void receiveSavedChanges(Runnable update) {
            List<?> before = snapshot.get();
            update.run();
            List<?> current = snapshot.get();
            List<Object> saved = new ArrayList<>(baseline);
            for (int index = 0; index < saved.size(); index++) {
                if (Objects.equals(before.get(index), baseline.get(index))) saved.set(index, current.get(index));
            }
            baseline = saved;
        }

        boolean confirmDiscard() {
            return Objects.equals(baseline, snapshot.get()) || JOptionPane.showConfirmDialog(this,
                    "Discard unsaved changes?", getTitle(), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }

        void setBusy(boolean value) {
            if (busy == value) return;
            busy = value;
            var focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            if (value) {
                focusManager.addKeyEventDispatcher(blockInput);
                enabledButtons = new boolean[footerButtons.length];
                for (int index = 0; index < footerButtons.length; index++) {
                    enabledButtons[index] = footerButtons[index].isEnabled();
                    footerButtons[index].setEnabled(false);
                }
            } else {
                focusManager.removeKeyEventDispatcher(blockInput);
                for (int index = 0; index < footerButtons.length; index++) {
                    footerButtons[index].setEnabled(enabledButtons[index]);
                }
            }
            getGlassPane().setCursor(value ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
            getGlassPane().setVisible(value);
        }

        private void close() {
            if (!busy && confirmDiscard()) dispose();
        }

        @Override public void dispose() {
            if (busy) KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(blockInput);
            super.dispose();
        }
    }
}
