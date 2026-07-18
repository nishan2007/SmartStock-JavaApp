package ui.screens.workstationprefs;

import managers.ReceiptNumberManager;
import managers.SessionManager;
import services.LanApiClient;
import ui.helpers.ThemeManager;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.time.ZoneId;

public class WorkstationSettingsPanel extends JPanel {
    private final JTextField deviceIdField = new JTextField();
    private final JTextField storeTimezoneField = new JTextField();
    private final JTextField sanitizedPreviewField = new JTextField();
    private final JTextField configPathField = new JTextField();
    private final JLabel currentStoreLabel = new JLabel();
    private final JLabel nextReceiptLabel = new JLabel();
    private final JLabel nextReceiveLabel = new JLabel();
    private final JLabel nextSequenceLabel = new JLabel();
    private final JCheckBox darkModeBox = new JCheckBox("Use dark mode on this workstation");
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public WorkstationSettingsPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel titleLabel = new JLabel("Workstation Settings");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(32, 41, 57));

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);

        sanitizedPreviewField.setEditable(false);
        configPathField.setEditable(false);
        configPathField.setFont(new Font("Monospaced", Font.PLAIN, 12));

        addRow(contentPanel, 0, "Workstation ID", deviceIdField);
        addRow(contentPanel, 1, "Saved As", sanitizedPreviewField);
        addRow(contentPanel, 2, "Config File", configPathField);
        addLabelRow(contentPanel, 3, "Current Store", currentStoreLabel);
        addRow(contentPanel, 4, "Store Timezone", storeTimezoneField);
        addLabelRow(contentPanel, 5, "Next Receipt", nextReceiptLabel);
        addLabelRow(contentPanel, 6, "Next Receive ID", nextReceiveLabel);
        addLabelRow(contentPanel, 7, "Next Sequences", nextSequenceLabel);
        addCheckRow(contentPanel, 8, "Appearance", darkModeBox);

        JPanel warningPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        warningPanel.setOpaque(false);
        JLabel warningIcon = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
        JLabel warningText = new JLabel("Use a unique workstation ID for each register.");
        warningText.setForeground(new Color(146, 64, 14));
        warningText.setFont(new Font("SansSerif", Font.BOLD, 13));
        warningPanel.add(warningIcon);
        warningPanel.add(warningText);

        GridBagConstraints warningConstraints = new GridBagConstraints();
        warningConstraints.gridx = 0;
        warningConstraints.gridy = 9;
        warningConstraints.gridwidth = 2;
        warningConstraints.weightx = 1;
        warningConstraints.insets = new Insets(16, 0, 0, 0);
        warningConstraints.anchor = GridBagConstraints.WEST;
        contentPanel.add(warningPanel, warningConstraints);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton refreshButton = new JButton("Refresh");
        JButton saveButton = new JButton("Save Workstation ID");
        JButton saveTimezoneButton = new JButton("Save Store Timezone");
        JButton saveAppearanceButton = new JButton("Save Appearance");
        buttonPanel.add(refreshButton);
        buttonPanel.add(saveAppearanceButton);
        buttonPanel.add(saveTimezoneButton);
        buttonPanel.add(saveButton);

        add(titleLabel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);footer.add(loadingState,BorderLayout.CENTER);footer.add(buttonPanel,BorderLayout.SOUTH);add(footer, BorderLayout.SOUTH);

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
        saveButton.addActionListener(e -> saveDeviceId());
        saveTimezoneButton.addActionListener(e -> saveStoreTimezone());
        saveAppearanceButton.addActionListener(e -> saveAppearance());

        loadSettings();
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

    private void addCheckRow(JPanel panel, int row, String label, JCheckBox checkBox) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        fieldLabel.setForeground(new Color(55, 65, 81));

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
        panel.add(checkBox, valueConstraints);
    }

    private void loadSettings() {
        CachedUiLoader.loadAfterDisplay(this,"workstation:settings",LanApiClient.WorkstationSettings.class,
                SessionDataCache.SCREEN_TTL,loadingState,LanApiClient::loadWorkstationSettings,settings->{
            deviceIdField.setText(settings.deviceCode());
            sanitizedPreviewField.setText(settings.deviceCode());
            configPathField.setText(ReceiptNumberManager.getConfigPath().toString());
            currentStoreLabel.setText(getStoreDisplay(settings.storeCode()));
            storeTimezoneField.setText(settings.timezone()==null||settings.timezone().isBlank()
                    ? getCurrentStoreTimezone():settings.timezone());
            nextReceiptLabel.setText(settings.nextReceiptPreview());
            nextReceiveLabel.setText(settings.nextReceivePreview());
            nextSequenceLabel.setText("Receipt: " + settings.nextSequence() + "   Receiving: " + settings.nextReceiveSequence());
            darkModeBox.setSelected(ThemeManager.isDarkModeEnabled());
        });
    }

    private void saveDeviceId() {
        String currentPreview = ReceiptNumberManager.previewSanitizedDeviceId(deviceIdField.getText());
        if (currentPreview.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a workstation ID.", "Workstation Settings", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String savedDeviceId = LanApiClient.updateWorkstationDeviceCode(deviceIdField.getText());
            JOptionPane.showMessageDialog(this, "Workstation ID saved as " + savedDeviceId + ".");
            loadSettings();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to save workstation settings.\n\n" + ex.getMessage(),
                    "Workstation Settings",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void saveStoreTimezone() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            JOptionPane.showMessageDialog(this, "No store is selected.", "Store Timezone", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String timezone = storeTimezoneField.getText().trim();
        try {
            ZoneId.of(timezone);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid timezone, for example America/New_York.", "Store Timezone", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            timezone = LanApiClient.updateWorkstationTimezone(timezone);
            SessionManager.setCurrentLocationTimezone(timezone);
            JOptionPane.showMessageDialog(this, "Store timezone saved.");
            loadSettings();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save store timezone.\n\n" + ex.getMessage(), "Store Timezone", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAppearance() {
        try {
            ThemeManager.setDarkModeEnabled(darkModeBox.isSelected());
            JOptionPane.showMessageDialog(this, darkModeBox.isSelected() ? "Dark mode enabled for this workstation." : "Dark mode disabled for this workstation.");
            loadSettings();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to save appearance settings.\n\n" + ex.getMessage(),
                    "Workstation Settings",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void updateSanitizedPreview() {
        sanitizedPreviewField.setText(ReceiptNumberManager.previewSanitizedDeviceId(deviceIdField.getText()));
    }

    private String getStoreDisplay(String storeCode) {
        String locationName = SessionManager.getCurrentLocationName();
        if (locationName == null || locationName.isBlank()) {
            return storeCode;
        }
        return locationName + " (" + storeCode + ")";
    }

    private String getCurrentStoreTimezone() {
        String timezone = SessionManager.getCurrentLocationTimezone();
        if (timezone == null || timezone.isBlank()) {
            timezone = ZoneId.systemDefault().getId();
        }
        return timezone;
    }
}
