package ui.screens;

import managers.NavigationManager;
import managers.ReceiptNumberManager;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;

public class LocalDeviceSettings extends JFrame {
    private final JTextField deviceIdField = new JTextField();
    private final JTextField sanitizedPreviewField = new JTextField();
    private final JTextField configPathField = new JTextField();
    private final JLabel currentStoreLabel = new JLabel();
    private final JLabel nextReceiptLabel = new JLabel();
    private final JLabel nextReceiveLabel = new JLabel();
    private final JLabel nextSequenceLabel = new JLabel();

    public LocalDeviceSettings() {
        setTitle("Local Device Settings");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(720, 430);
        setLocationRelativeTo(null);
        setJMenuBar(AppMenuBar.create(this, "LocalDeviceSettings"));

        JPanel rootPanel = new JPanel(new BorderLayout(18, 18));
        rootPanel.setBorder(new EmptyBorder(24, 24, 24, 24));
        rootPanel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Local Device Settings");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(32, 41, 57));

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(20, 20, 20, 20)
        ));

        sanitizedPreviewField.setEditable(false);
        configPathField.setEditable(false);
        configPathField.setFont(new Font("Monospaced", Font.PLAIN, 12));

        addRow(contentPanel, 0, "Device ID", deviceIdField);
        addRow(contentPanel, 1, "Saved As", sanitizedPreviewField);
        addRow(contentPanel, 2, "Config File", configPathField);
        addLabelRow(contentPanel, 3, "Current Store", currentStoreLabel);
        addLabelRow(contentPanel, 4, "Next Receipt", nextReceiptLabel);
        addLabelRow(contentPanel, 5, "Next Receive ID", nextReceiveLabel);
        addLabelRow(contentPanel, 6, "Next Sequences", nextSequenceLabel);

        JPanel warningPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        warningPanel.setOpaque(false);
        JLabel warningIcon = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
        JLabel warningText = new JLabel("Use a unique device ID for each register.");
        warningText.setForeground(new Color(146, 64, 14));
        warningText.setFont(new Font("SansSerif", Font.BOLD, 13));
        warningPanel.add(warningIcon);
        warningPanel.add(warningText);

        GridBagConstraints warningConstraints = new GridBagConstraints();
        warningConstraints.gridx = 0;
        warningConstraints.gridy = 7;
        warningConstraints.gridwidth = 2;
        warningConstraints.weightx = 1;
        warningConstraints.insets = new Insets(16, 0, 0, 0);
        warningConstraints.anchor = GridBagConstraints.WEST;
        contentPanel.add(warningPanel, warningConstraints);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");
        JButton saveButton = new JButton("Save Device ID");
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);
        buttonPanel.add(saveButton);

        rootPanel.add(titleLabel, BorderLayout.NORTH);
        rootPanel.add(contentPanel, BorderLayout.CENTER);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(rootPanel);

        deviceIdField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSanitizedPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSanitizedPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSanitizedPreview();
            }
        });

        refreshButton.addActionListener(e -> loadSettings());
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveButton.addActionListener(e -> saveDeviceId());

        loadSettings();
        WindowHelper.configurePosWindow(this);
    }

    private void addRow(JPanel panel, int row, String label, JTextField field) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        fieldLabel.setForeground(new Color(55, 65, 81));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.insets = new Insets(0, 0, 12, 16);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(fieldLabel, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.insets = new Insets(0, 0, 12, 0);
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, fieldConstraints);
    }

    private void addLabelRow(JPanel panel, int row, String label, JLabel valueLabel) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        fieldLabel.setForeground(new Color(55, 65, 81));

        valueLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        valueLabel.setForeground(new Color(17, 24, 39));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.insets = new Insets(0, 0, 12, 16);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(fieldLabel, labelConstraints);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = row;
        valueConstraints.insets = new Insets(0, 0, 12, 0);
        valueConstraints.weightx = 1;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(valueLabel, valueConstraints);
    }

    private void loadSettings() {
        try {
            int locationId = getCurrentLocationIdForPreview();
            ReceiptNumberManager.DeviceReceiptSettings settings = ReceiptNumberManager.getDeviceReceiptSettings(locationId);
            deviceIdField.setText(settings.deviceId());
            sanitizedPreviewField.setText(settings.deviceId());
            configPathField.setText(settings.configPath().toString());
            currentStoreLabel.setText(getStoreDisplay(settings.storeCode()));
            nextReceiptLabel.setText(settings.nextReceiptPreview());
            nextReceiveLabel.setText(settings.nextReceivePreview());
            nextSequenceLabel.setText("Receipt: " + settings.nextSequence() + "   Receiving: " + settings.nextReceiveSequence());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load local device settings.\n\n" + ex.getMessage(),
                    "Local Device Settings",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void saveDeviceId() {
        String currentPreview = ReceiptNumberManager.previewSanitizedDeviceId(deviceIdField.getText());
        if (currentPreview.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a device ID.", "Local Device Settings", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String savedDeviceId = ReceiptNumberManager.updateDeviceId(deviceIdField.getText());
            JOptionPane.showMessageDialog(this, "Device ID saved as " + savedDeviceId + ".");
            loadSettings();
        } catch (IllegalArgumentException | IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to save local device settings.\n\n" + ex.getMessage(),
                    "Local Device Settings",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void updateSanitizedPreview() {
        sanitizedPreviewField.setText(ReceiptNumberManager.previewSanitizedDeviceId(deviceIdField.getText()));
    }

    private int getCurrentLocationIdForPreview() {
        Integer locationId = SessionManager.getCurrentLocationId();
        return locationId == null ? 0 : locationId;
    }

    private String getStoreDisplay(String storeCode) {
        String locationName = SessionManager.getCurrentLocationName();
        if (locationName == null || locationName.isBlank()) {
            return storeCode;
        }
        return locationName + " (" + storeCode + ")";
    }
}
