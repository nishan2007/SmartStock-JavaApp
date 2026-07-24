package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import managers.NavigationManager;
import ui.components.AppMenuBar;
import ui.components.PreferenceTreeCellRenderer;
import ui.design.DeckersPalette;
import ui.helpers.WindowHelper;
import ui.screens.workstationprefs.HardwareSettingsPanel;
import ui.screens.workstationprefs.WorkstationSettingsPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;

public class WorkstationPreferences extends JFrame {
    public static final String NAV_GENERAL = "General";
    public static final String NAV_WORKSTATION_SETTINGS = "Workstation Settings";
    public static final String NAV_HARDWARE_SETTINGS = "Hardware Settings";
    public static final String NAV_SERVER = "Server";

    private final JPanel rightContentPanel = new JPanel(new CardLayout());
    private final boolean serverWorkstation = DatabaseConfig.load().mode() == DatabaseMode.SERVER;
    private JTree navigationTree;

    public WorkstationPreferences() {
        this(NAV_GENERAL);
    }

    public WorkstationPreferences(String initialSection) {
        setTitle("Workstation Preferences");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 640);
        setLocationRelativeTo(null);
        setJMenuBar(AppMenuBar.create(this, "WorkstationPreferences"));

        JPanel rootPanel = new JPanel(new BorderLayout(18, 18));
        rootPanel.setBorder(new EmptyBorder(24, 24, 24, 24));
        rootPanel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Workstation Preferences");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(32, 41, 57));
        rootPanel.add(titleLabel, BorderLayout.NORTH);

        addDefaultCards();

        JSplitPane mainSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildNavigationPanel(),
                rightContentPanel
        );
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder());
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setResizeWeight(0);
        mainSplitPane.setDividerLocation(290);
        rootPanel.add(mainSplitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton closeButton = new JButton("Close");
        buttonPanel.add(closeButton);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(rootPanel);

        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        routeNavigationKey(initialSection == null || initialSection.isBlank() ? NAV_GENERAL : initialSection);
        WindowHelper.configurePosWindow(this);
    }

    private JComponent buildNavigationPanel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Preferences");
        root.add(new DefaultMutableTreeNode(NAV_GENERAL));
        root.add(new DefaultMutableTreeNode(NAV_WORKSTATION_SETTINGS));
        root.add(new DefaultMutableTreeNode(NAV_HARDWARE_SETTINGS));
        if (serverWorkstation) {
            root.add(new DefaultMutableTreeNode(NAV_SERVER));
        }

        JTree tree = new JTree(new DefaultTreeModel(root));
        navigationTree = tree;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tree.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        tree.setBackground(DeckersPalette.surface());
        tree.setForeground(DeckersPalette.text());
        tree.setOpaque(true);

        tree.setCellRenderer(new PreferenceTreeCellRenderer());

        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }
            routeNavigationKey(String.valueOf(selectedNode.getUserObject()));
        });

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(Color.WHITE);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(10, 10, 10, 10)
        ));
        JLabel label = new JLabel("Preferences");
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        container.add(label, BorderLayout.NORTH);
        JScrollPane navigationScroll = new JScrollPane(tree);
        navigationScroll.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        navigationScroll.setBackground(DeckersPalette.surface());
        navigationScroll.setBorder(BorderFactory.createEmptyBorder());
        navigationScroll.getViewport().putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        navigationScroll.getViewport().setBackground(DeckersPalette.surface());
        container.add(navigationScroll, BorderLayout.CENTER);

        TreePath defaultPath = findTreePath(root, NAV_GENERAL);
        if (defaultPath != null) {
            tree.setSelectionPath(defaultPath);
        }

        return container;
    }

    private void routeNavigationKey(String key) {
        String resolvedKey = switch (key) {
            case NAV_WORKSTATION_SETTINGS -> NAV_WORKSTATION_SETTINGS;
            case NAV_HARDWARE_SETTINGS -> NAV_HARDWARE_SETTINGS;
            case NAV_SERVER -> serverWorkstation ? NAV_SERVER : NAV_GENERAL;
            default -> NAV_GENERAL;
        };
        CardLayout cardLayout = (CardLayout) rightContentPanel.getLayout();
        cardLayout.show(rightContentPanel, resolvedKey);
        selectNavigationPath(resolvedKey);
    }

    private void addDefaultCards() {
        rightContentPanel.add(buildPlaceholderPanel(
                "General",
                "This panel is ready. Share the sections you want and I will wire them here."
        ), NAV_GENERAL);
        rightContentPanel.add(new WorkstationSettingsPanel(), NAV_WORKSTATION_SETTINGS);
        rightContentPanel.add(new HardwareSettingsPanel(), NAV_HARDWARE_SETTINGS);
        if (serverWorkstation) {
            rightContentPanel.add(buildServerPanel(), NAV_SERVER);
        }
    }

    private JPanel buildServerPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel titleLabel = new JLabel("Server");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(32, 41, 57));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea description = new JTextArea(
                "Manage the database, LAN service, background synchronization, "
                        + "Supabase server connection, initial setup, and recovery tools for this server."
        );
        description.setEditable(false);
        description.setFocusable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setOpaque(false);
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(new Color(71, 85, 105));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        JButton guidedSetupButton = new JButton("Open Guided Setup");
        guidedSetupButton.setToolTipText(
                "Choose Server, Register, or Remote Admin and follow the simplified setup."
        );
        guidedSetupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidedSetupButton.addActionListener(e -> new InitialSetupWizard(this).setVisible(true));

        JButton databaseSetupButton = new JButton("Advanced Server Settings");
        databaseSetupButton.setToolTipText(
                "Open technical database, service, Supabase credential, and recovery settings."
        );
        databaseSetupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        databaseSetupButton.addActionListener(e -> {
            if (WindowHelper.focusIfAlreadyOpen(DatabaseSetup.class)) {
                return;
            }
            new DatabaseSetup(this).setVisible(true);
        });

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(description);
        panel.add(Box.createVerticalStrut(12));
        panel.add(guidedSetupButton);
        panel.add(Box.createVerticalStrut(8));
        panel.add(databaseSetupButton);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildPlaceholderPanel(String title, String message) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(32, 41, 57));

        JTextArea bodyLabel = new JTextArea(message);
        bodyLabel.setEditable(false);
        bodyLabel.setFocusable(false);
        bodyLabel.setLineWrap(true);
        bodyLabel.setWrapStyleWord(true);
        bodyLabel.setOpaque(false);
        bodyLabel.setBorder(null);
        bodyLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        bodyLabel.setForeground(new Color(71, 85, 105));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(bodyLabel, BorderLayout.CENTER);
        return panel;
    }

    private void selectNavigationPath(String key) {
        if (navigationTree == null || key == null) {
            return;
        }
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) navigationTree.getModel().getRoot();
        TreePath path = findTreePath(root, key);
        if (path != null && !path.equals(navigationTree.getSelectionPath())) {
            navigationTree.setSelectionPath(path);
        }
    }

    private TreePath findTreePath(DefaultMutableTreeNode root, String key) {
        if (root == null || key == null) {
            return null;
        }
        java.util.Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
            if (key.equals(String.valueOf(node.getUserObject()))) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }
}
