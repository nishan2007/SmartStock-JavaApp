package ui.screens;

import managers.SupabaseSessionManager;
import managers.SessionManager;
import managers.CompanyCustomizationManager;
import services.BadgeEncoderService;
import services.EmployeeDocumentService;
import services.BadgePrintService;
import services.EmployeePhotoService;
import services.EmployeePayrollSettingsService;
import services.LanApiClient;
import services.LanEmployeeAdminService;
import services.EmployeePayrollSettingsService.PeriodType;
import ui.components.AppMenuBar;
import ui.helpers.ProductImageHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class EmployeeManagement extends JFrame {
    private static final Color PAGE_BG = new Color(17, 17, 17);
    private static final Color CARD_BG = new Color(28, 28, 28);
    private static final Color FIELD_BG = new Color(21, 21, 21);
    private static final Color BORDER = new Color(67, 67, 67);
    private static final Color TEXT = new Color(238, 238, 238);
    private static final Color MUTED_TEXT = new Color(182, 182, 182);
    private static final Color PRIMARY = new Color(78, 111, 158);

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private ViewportWidthPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private JTable employeeTable;
    private DefaultTableModel employeeModel;
    private TableRowSorter<DefaultTableModel> employeeSorter;
    private JTextField employeeSearchField;
    private JTable storeTable;
    private DefaultTableModel storeModel;
    private TableRowSorter<DefaultTableModel> storeSorter;
    private JTextField storeSearchField;

    private JTextField usernameField;
    private JTextField passwordField;
    private JTextField firstNameField;
    private JTextField middleNameField;
    private JTextField lastNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField employeePhotoField;
    private JLabel employeePhotoPreviewLabel;
    private JTextField employeeIdCardDocumentField;
    private JTextField dateOfBirthField;
    private JTextField hireDateField;
    private JTextField deactivationDateField;
    private JTextField badgeIdField;
    private JComboBox<CompensationOption> compensationTypeBox;
    private JTextField salaryAmountField;
    private JLabel salaryAmountLabel;
    private JComboBox<PeriodType> payrollPeriodBox;
    private JTextField workHourLimitField;
    private JLabel payrollSettingsStatusLabel;
    private JComboBox<RoleOption> roleBox;
    private JCheckBox activeCheckBox;

    private JButton addButton;
    private JButton updateButton;
    private JButton clearButton;
    private JButton refreshButton;
    private JButton deleteButton;
    private JButton previewBadgeButton;
    private JButton printBadgeButton;
    private JButton saveBadgePdfButton;
    private JButton writeMagStripeButton;
    private JButton programNfcButton;
    private JButton rotateBadgeIdButton;

    private Integer selectedUserId = null;
    private String originalFirstName = "";
    private String originalMiddleName = "";
    private String originalLastName = "";
    private String originalFullName = "";
    private String originalEmail = "";
    private boolean originalIsActive = true;
    private boolean updatingGeneratedUsername;
    private String lastGeneratedUsername = "";
    private boolean loadingPayrollSettings;
    private PeriodType originalPayrollPeriodType = PeriodType.SEMI_MONTHLY;
    private BigDecimal originalWorkHourLimit = EmployeePayrollSettingsService.DEFAULT_SEMI_MONTHLY_LIMIT;

    public EmployeeManagement() {
        setTitle("Employee Management");
        setSize(1320, 760);
        setMinimumSize(new Dimension(1120, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setJMenuBar(AppMenuBar.create(this, "EmployeeManagement"));
        getContentPane().setBackground(PAGE_BG);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(PAGE_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        employeeModel = new DefaultTableModel(
                new Object[]{"User ID", "Username", "Full Name", "First Name", "Middle Name", "Last Name", "Email", "Phone", "Photo", "DOB", "Badge ID", "Badge Prints", "Pay Type", "Salary", "Role", "Active", "ID Card Document", "Hire Date", "Deactivation Date"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        employeeSorter = new TableRowSorter<>(employeeModel);

        employeeTable = new JTable(employeeModel);
        employeeTable.setRowSorter(employeeSorter);
        employeeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        leftPanel.setBackground(CARD_BG);
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.setOpaque(false);
        employeeSearchField = new JTextField();
        JLabel searchLabel = new JLabel("Employees");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        searchLabel.setForeground(TEXT);
        employeeSearchField.putClientProperty("JTextField.placeholderText", "Search employees");
        styleTextField(employeeSearchField);
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(employeeSearchField, BorderLayout.CENTER);
        JScrollPane tableScrollPane = new JScrollPane(employeeTable);
        leftPanel.setPreferredSize(new Dimension(430, 0));
        leftPanel.setMinimumSize(new Dimension(320, 0));
        tableScrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        tableScrollPane.getViewport().setBackground(CARD_BG);
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(tableScrollPane, BorderLayout.CENTER);
        styleTable(employeeTable);
        employeeTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        employeeTable.getColumnModel().getColumn(0).setMinWidth(60);
        employeeTable.getColumnModel().getColumn(0).setMaxWidth(90);

        employeeTable.getColumnModel().getColumn(1).setPreferredWidth(135);
        employeeTable.getColumnModel().getColumn(2).setPreferredWidth(190);
        for (int hiddenColumn = 3; hiddenColumn <= 5; hiddenColumn++) {
            TableColumn column = employeeTable.getColumnModel().getColumn(hiddenColumn);
            column.setMinWidth(0);
            column.setPreferredWidth(0);
            column.setMaxWidth(0);
        }
        employeeTable.getColumnModel().getColumn(6).setPreferredWidth(250);
        employeeTable.getColumnModel().getColumn(7).setPreferredWidth(120);
        TableColumn photoColumn = employeeTable.getColumnModel().getColumn(8);
        photoColumn.setMinWidth(0);
        photoColumn.setPreferredWidth(0);
        photoColumn.setMaxWidth(0);
        employeeTable.getColumnModel().getColumn(9).setPreferredWidth(95);
        employeeTable.getColumnModel().getColumn(10).setPreferredWidth(120);
        employeeTable.getColumnModel().getColumn(11).setPreferredWidth(105);
        employeeTable.getColumnModel().getColumn(12).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(13).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(14).setPreferredWidth(125);
        employeeTable.getColumnModel().getColumn(15).setPreferredWidth(75);
        employeeTable.getColumnModel().getColumn(15).setMinWidth(60);
        employeeTable.getColumnModel().getColumn(15).setMaxWidth(80);
        hideEmployeeColumn(7);
        hideEmployeeColumn(9);
        hideEmployeeColumn(10);
        hideEmployeeColumn(12);
        hideEmployeeColumn(13);
        hideEmployeeColumn(16);

        employeeTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JPanel formPanel = new ViewportWidthPanel(new GridBagLayout());
        formPanel.setBackground(CARD_BG);
        formPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        usernameField = new JTextField();
        passwordField = new JTextField();
        firstNameField = new JTextField();
        middleNameField = new JTextField();
        lastNameField = new JTextField();
        emailField = new JTextField();
        phoneField = new JTextField();
        employeePhotoField = new JTextField();
        employeePhotoField.setColumns(24);
        employeePhotoField.setEditable(false);
        employeeIdCardDocumentField = new JTextField();
        employeeIdCardDocumentField.setColumns(24);
        employeeIdCardDocumentField.setEditable(false);
        dateOfBirthField = new JTextField();
        dateOfBirthField.setToolTipText("Optional. Use YYYY-MM-DD. If present, it is included in the badge verification hash.");
        hireDateField = new JTextField(storeToday().toString());
        hireDateField.setToolTipText("Use YYYY-MM-DD. New employees default to today's date.");
        deactivationDateField = new JTextField();
        deactivationDateField.setEditable(false);
        deactivationDateField.setToolTipText("Set automatically when the employee is deactivated.");
        badgeIdField = new JTextField();
        badgeIdField.setToolTipText("Auto-generated for Code 128 badge barcodes when blank.");
        badgeIdField.setEditable(false);
        compensationTypeBox = new JComboBox<>(new CompensationOption[]{
                new CompensationOption("HOURLY", "Hourly"),
                new CompensationOption("SALARY", "Salary (per pay period)"),
                new CompensationOption("DAILY", "Daily")
        });
        compensationTypeBox.setEditable(false);
        salaryAmountField = new JTextField();
        salaryAmountLabel = new JLabel("Hourly Rate:");
        payrollPeriodBox = new JComboBox<>(PeriodType.values());
        payrollPeriodBox.setEditable(false);
        workHourLimitField = new JTextField(EmployeePayrollSettingsService.DEFAULT_SEMI_MONTHLY_LIMIT.toPlainString());
        payrollSettingsStatusLabel = new JLabel("Applies to hourly employees only.");
        payrollSettingsStatusLabel.setForeground(MUTED_TEXT);
        roleBox = new JComboBox<>();
        activeCheckBox = new JCheckBox("Active", true);
        activeCheckBox.setEnabled(true);
        storeSearchField = new JTextField();
        styleEmployeeInputs();
        storeModel = new DefaultTableModel(new Object[]{"Assigned", "Store ID", "Store Name", "Address"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return Boolean.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        storeSorter = new TableRowSorter<>(storeModel);
        storeTable = new JTable(storeModel);
        storeTable.setRowSorter(storeSorter);
        storeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(storeTable);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JLabel("Username (auto):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Password * (new employee only):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(new JLabel("First Name *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(firstNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Middle Name (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(middleNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Last Name *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(lastNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Email *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(emailField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Phone Number (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(phoneField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Badge Photo (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(buildPhotoSelectorPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.weightx = 0;
        formPanel.add(new JLabel("ID Card Document (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(buildIdCardDocumentSelectorPanel(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Date of Birth (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(dateOfBirthField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Hire Date *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(hireDateField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Badge ID (auto):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(badgeIdField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 12;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Pay Type *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(compensationTypeBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 13;
        gbc.weightx = 0;
        salaryAmountLabel.setText("Hourly Rate *:");
        formPanel.add(salaryAmountLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(salaryAmountField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 14;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Payroll Period *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(payrollPeriodBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 15;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Work Hour Limit *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(workHourLimitField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 16;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Payroll Setting:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(payrollSettingsStatusLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 17;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Role *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(roleBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 18;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(activeCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 19;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Deactivation Date:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(deactivationDateField, gbc);

        JPanel storePanel = new JPanel(new BorderLayout(6, 6));
        storePanel.setBackground(CARD_BG);
        storePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JPanel storeSearchPanel = new JPanel(new BorderLayout(6, 0));
        storeSearchPanel.setOpaque(false);
        JLabel assignedStoresLabel = new JLabel("Assigned Stores *");
        assignedStoresLabel.setForeground(TEXT);
        assignedStoresLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        storeSearchField.putClientProperty("JTextField.placeholderText", "Search stores");
        styleTextField(storeSearchField);
        storeSearchPanel.add(assignedStoresLabel, BorderLayout.WEST);
        storeSearchPanel.add(storeSearchField, BorderLayout.CENTER);
        storePanel.add(storeSearchPanel, BorderLayout.NORTH);

        JScrollPane storeScrollPane = new JScrollPane(storeTable);
        storeScrollPane.setPreferredSize(new Dimension(0, 135));
        storeScrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        storeScrollPane.getViewport().setBackground(CARD_BG);
        storePanel.add(storeScrollPane, BorderLayout.CENTER);

        TableColumn assignedStoreColumn = storeTable.getColumnModel().getColumn(0);
        assignedStoreColumn.setPreferredWidth(80);
        assignedStoreColumn.setMaxWidth(100);
        TableColumn storeIdColumn = storeTable.getColumnModel().getColumn(1);
        storeIdColumn.setPreferredWidth(70);
        storeIdColumn.setMaxWidth(90);

        gbc.gridx = 0;
        gbc.gridy = 20;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(storePanel, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 21;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createVerticalGlue(), gbc);
        styleLabels(formPanel);

        JPanel topButtonPanel = new JPanel(new GridLayout(0, 3, 8, 8));
        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topButtonPanel.setOpaque(false);
        bottomButtonPanel.setOpaque(false);
        addButton = new JButton("Add Employee");
        updateButton = new JButton("Update Employee");
        clearButton = new JButton("Clear");
        refreshButton = new JButton("Refresh");
        deleteButton = new JButton("Deactivate Employee");
        previewBadgeButton = new JButton("Preview Badge");
        printBadgeButton = new JButton("Print Badge");
        saveBadgePdfButton = new JButton("Save Badge PDF");
        writeMagStripeButton = new JButton("Write Stripe");
        programNfcButton = new JButton("Program NFC/RFID");
        rotateBadgeIdButton = new JButton("Rotate Badge ID");

        Dimension compactButtonSize = new Dimension(145, 32);
        addButton.setPreferredSize(compactButtonSize);
        updateButton.setPreferredSize(compactButtonSize);
        deleteButton.setPreferredSize(compactButtonSize);

        Dimension smallButtonSize = new Dimension(125, 32);
        clearButton.setPreferredSize(smallButtonSize);
        refreshButton.setPreferredSize(smallButtonSize);
        styleActionButtons();

        topButtonPanel.add(addButton);
        topButtonPanel.add(updateButton);
        topButtonPanel.add(deleteButton);
        topButtonPanel.add(previewBadgeButton);
        topButtonPanel.add(printBadgeButton);
        topButtonPanel.add(saveBadgePdfButton);
        topButtonPanel.add(writeMagStripeButton);
        topButtonPanel.add(programNfcButton);
        topButtonPanel.add(rotateBadgeIdButton);

        bottomButtonPanel.add(clearButton);
        bottomButtonPanel.add(refreshButton);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBackground(CARD_BG);
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        rightPanel.setPreferredSize(new Dimension(700, 0));
        rightPanel.setMinimumSize(new Dimension(540, 0));
        JLabel detailsTitle = new JLabel("Employee Details");
        detailsTitle.setForeground(TEXT);
        detailsTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        JPanel rightHeader = new JPanel(new BorderLayout(0, 10));
        rightHeader.setOpaque(false);
        rightHeader.add(detailsTitle, BorderLayout.NORTH);
        rightHeader.add(topButtonPanel, BorderLayout.SOUTH);
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        formScrollPane.getViewport().setBackground(CARD_BG);
        formScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScrollPane.getVerticalScrollBar().setUnitIncrement(14);
        rightPanel.add(rightHeader, BorderLayout.NORTH);
        rightPanel.add(formScrollPane, BorderLayout.CENTER);
        rightPanel.add(bottomButtonPanel, BorderLayout.SOUTH);

        JSplitPane contentSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                rightPanel
        );
        contentSplitPane.setBorder(BorderFactory.createEmptyBorder());
        contentSplitPane.setOpaque(false);
        contentSplitPane.setContinuousLayout(true);
        contentSplitPane.setOneTouchExpandable(true);
        contentSplitPane.setResizeWeight(0.38);
        contentSplitPane.setDividerLocation(430);
        contentSplitPane.setDividerSize(8);
        mainPanel.add(contentSplitPane, BorderLayout.CENTER);

        add(mainPanel);

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addEmployee();
            }
        });

        updateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateEmployee();
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteEmployee();
            }
        });

        previewBadgeButton.addActionListener(e -> previewSelectedBadge());
        printBadgeButton.addActionListener(e -> printSelectedBadge());
        saveBadgePdfButton.addActionListener(e -> saveSelectedBadgePdf());
        writeMagStripeButton.addActionListener(e -> writeSelectedMagStripe());
        programNfcButton.addActionListener(e -> programSelectedNfc());
        rotateBadgeIdButton.addActionListener(e -> rotateSelectedBadgeId());

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearFields();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadRoles();
                loadStoresForUser(selectedUserId);
                loadEmployees();
            }
        });
        employeeSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyEmployeeFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyEmployeeFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyEmployeeFilter();
            }
        });
        storeSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyStoreFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyStoreFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyStoreFilter();
            }
        });
        DocumentListener generatedUsernameListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateGeneratedUsername();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateGeneratedUsername();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateGeneratedUsername();
            }
        };
        firstNameField.getDocument().addDocumentListener(generatedUsernameListener);
        lastNameField.getDocument().addDocumentListener(generatedUsernameListener);
        compensationTypeBox.addActionListener(e -> refreshPayrollSettingsControls());
        payrollPeriodBox.addActionListener(e -> {
            if (!loadingPayrollSettings) {
                PeriodType type = (PeriodType) payrollPeriodBox.getSelectedItem();
                if (type != null) workHourLimitField.setText(type.defaultLimit().toPlainString());
            }
        });

        employeeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedEmployee();
            }
        });

        deleteButton.setEnabled(false);
        previewBadgeButton.setEnabled(false);
        printBadgeButton.setEnabled(false);
        saveBadgePdfButton.setEnabled(false);
        writeMagStripeButton.setEnabled(false);
        programNfcButton.setEnabled(false);
        rotateBadgeIdButton.setEnabled(false);
        loadRoles();
        loadStoresForUser(null);
        loadEmployees();
        refreshPayrollSettingsControls();
        WindowHelper.showPosWindow(this);
    }

    private void hideEmployeeColumn(int modelColumn) {
        int viewColumn = employeeTable.convertColumnIndexToView(modelColumn);
        if (viewColumn < 0) {
            return;
        }
        TableColumn column = employeeTable.getColumnModel().getColumn(viewColumn);
        column.setMinWidth(0);
        column.setPreferredWidth(0);
        column.setMaxWidth(0);
        column.setResizable(false);
    }

    private void styleEmployeeInputs() {
        for (JTextField field : new JTextField[]{
                usernameField,
                passwordField,
                firstNameField,
                middleNameField,
                lastNameField,
                emailField,
                phoneField,
                employeePhotoField,
                employeeIdCardDocumentField,
                dateOfBirthField,
                badgeIdField,
                salaryAmountField
                , workHourLimitField
        }) {
            styleTextField(field);
        }
        styleComboBox(compensationTypeBox);
        styleComboBox(payrollPeriodBox);
        styleComboBox(roleBox);
        activeCheckBox.setForeground(TEXT);
        activeCheckBox.setOpaque(false);
    }

    private void styleTextField(JTextField field) {
        field.setBackground(FIELD_BG);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setSelectionColor(PRIMARY);
        field.setSelectedTextColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)
        ));
    }

    private void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setBackground(FIELD_BG);
        comboBox.setForeground(TEXT);
        comboBox.setBorder(BorderFactory.createLineBorder(BORDER));
    }

    private void styleTable(JTable table) {
        table.setRowHeight(30);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setBackground(CARD_BG);
        table.setForeground(TEXT);
        table.setSelectionBackground(PRIMARY);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(BORDER);
        table.getTableHeader().setBackground(new Color(38, 38, 38));
        table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    component.setBackground(row % 2 == 0 ? new Color(31, 31, 31) : new Color(25, 25, 25));
                    component.setForeground(TEXT);
                }
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return component;
            }
        });
        table.setDefaultRenderer(Boolean.class, table.getDefaultRenderer(Boolean.class));
    }

    private void styleLabels(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) {
                label.setForeground(MUTED_TEXT);
                label.setFont(new Font("SansSerif", Font.BOLD, 12));
            } else if (child instanceof JPanel panel) {
                panel.setBackground(CARD_BG);
                styleLabels(panel);
            } else if (child instanceof JScrollPane scrollPane) {
                scrollPane.setBackground(CARD_BG);
                scrollPane.getViewport().setBackground(CARD_BG);
            }
        }
    }

    private void styleActionButtons() {
        styleButton(addButton, true);
        styleButton(updateButton, true);
        styleButton(deleteButton, false);
        styleButton(previewBadgeButton, false);
        styleButton(printBadgeButton, false);
        styleButton(saveBadgePdfButton, false);
        styleButton(writeMagStripeButton, false);
        styleButton(clearButton, false);
        styleButton(refreshButton, false);
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        button.setBackground(primary ? PRIMARY : new Color(82, 82, 82));
        button.setForeground(Color.WHITE);
    }

    private JPanel buildPhotoSelectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        employeePhotoPreviewLabel = ProductImageHelper.createImagePreview("", 90, 110);
        employeePhotoPreviewLabel.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(employeePhotoPreviewLabel, BorderLayout.WEST);

        JPanel fieldPanel = new JPanel(new BorderLayout(6, 4));
        fieldPanel.setOpaque(false);
        fieldPanel.add(employeePhotoField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        JButton chooseButton = new JButton("Choose");
        JButton previewButton = new JButton("Preview");
        JButton clearButton = new JButton("Clear");
        styleButton(chooseButton, false);
        styleButton(previewButton, false);
        styleButton(clearButton, false);
        buttons.add(chooseButton);
        buttons.add(previewButton);
        buttons.add(clearButton);
        fieldPanel.add(buttons, BorderLayout.SOUTH);
        panel.add(fieldPanel, BorderLayout.CENTER);
        chooseButton.addActionListener(e -> chooseEmployeePhoto());
        previewButton.addActionListener(e -> refreshEmployeePhotoPreview());
        clearButton.addActionListener(e -> {
            employeePhotoField.setText("");
            refreshEmployeePhotoPreview();
        });
        return panel;
    }

    private JPanel buildIdCardDocumentSelectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        panel.setOpaque(false);
        panel.add(employeeIdCardDocumentField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        JButton chooseButton = new JButton("Choose");
        JButton openButton = new JButton("Open");
        JButton clearButton = new JButton("Clear");
        styleButton(chooseButton, false);
        styleButton(openButton, false);
        styleButton(clearButton, false);
        buttons.add(chooseButton);
        buttons.add(openButton);
        buttons.add(clearButton);
        panel.add(buttons, BorderLayout.SOUTH);
        chooseButton.addActionListener(e -> chooseEmployeeIdCardDocument());
        openButton.addActionListener(e -> openEmployeeIdCardDocument());
        clearButton.addActionListener(e -> employeeIdCardDocumentField.setText(""));
        return panel;
    }

    private void chooseEmployeeIdCardDocument() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Employee ID Card Document");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "ID Card Documents", "pdf", "png", "jpg", "jpeg", "heic", "doc", "docx"
        ));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        try {
            Path targetDirectory = Path.of(System.getProperty("user.home"), ".smartstock", "employee-id-cards");
            Files.createDirectories(targetDirectory);
            String extension = extension(source.getFileName().toString());
            String namePrefix = selectedUserId == null ? "pending" : "employee-" + selectedUserId;
            Path target = targetDirectory.resolve(namePrefix + "-" + System.currentTimeMillis() + "." + extension);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            employeeIdCardDocumentField.setText(target.toString());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to copy ID card document.\n\n" + ex.getMessage(), "ID Card Document", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openEmployeeIdCardDocument() {
        String value = employeeIdCardDocumentField.getText().trim();
        if (value.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No ID card document is saved for this employee.", "ID Card Document", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(this, "Opening documents is not supported on this workstation.", "ID Card Document", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (EmployeeDocumentService.isAuthenticatedStorageUrl(value)) {
                Desktop.getDesktop().open(EmployeeDocumentService.downloadAuthenticatedDocument(value));
            } else if (value.startsWith("http://") || value.startsWith("https://")) {
                Desktop.getDesktop().browse(URI.create(value));
            } else {
                Desktop.getDesktop().open(Path.of(value).toFile());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to open ID card document.\n\n" + ex.getMessage(), "ID Card Document", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseEmployeePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Employee Badge Photo");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Image Files", "png", "jpg", "jpeg", "gif", "bmp"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        try {
            Path targetDirectory = Path.of(System.getProperty("user.home"), ".smartstock", "employee-photos");
            Files.createDirectories(targetDirectory);
            String extension = extension(source.getFileName().toString());
            String namePrefix = selectedUserId == null ? "pending" : "employee-" + selectedUserId;
            Path target = targetDirectory.resolve(namePrefix + "-" + System.currentTimeMillis() + "." + extension);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            employeePhotoField.setText(target.toString());
            refreshEmployeePhotoPreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to copy employee photo.\n\n" + ex.getMessage(), "Employee Photo", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshEmployeePhotoPreview() {
        if (employeePhotoPreviewLabel != null) {
            ProductImageHelper.setPreviewImage(employeePhotoPreviewLabel, employeePhotoField.getText(), 90, 110);
        }
    }

    private static String extension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "png";
        }
        return fileName.substring(dotIndex + 1).replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private void previewSelectedBadge() {
        try {
            BadgePrintService.EmployeeBadgeData employee = selectedBadgeData();
            CompanyCustomizationManager.BadgeTemplateSettings settings = chooseBadgeTemplateSettings("Preview Badge");
            if (settings == null) {
                return;
            }
            BadgePrintService.previewBadge(this, employee, settings);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to preview badge.\n\n" + ex.getMessage(), "Badge Preview", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void printSelectedBadge() {
        try {
            BadgePrintService.EmployeeBadgeData employee = selectedBadgeData();
            BadgePrintService.BadgePrintSide side = chooseBadgePrintSide();
            if (side == null) {
                return;
            }
            LocalDate expiryDate = chooseBadgeExpiryDate(side);
            if (expiryDate == null) {
                return;
            }
            CompanyCustomizationManager.BadgeTemplateSettings settings = chooseBadgeTemplateSettings("Print Badge");
            if (settings == null) {
                return;
            }
            BadgePrintService.printBadge(this, employee, settings, side, expiryDate);
            loadEmployees();
            selectEmployeeInTable(employee.userId());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to print badge.\n\n" + ex.getMessage(), "Badge Print", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveSelectedBadgePdf() {
        try {
            BadgePrintService.EmployeeBadgeData employee = selectedBadgeData();
            BadgePrintService.BadgePrintSide side = chooseBadgePrintSide();
            if (side == null) {
                return;
            }
            LocalDate expiryDate = chooseBadgeExpiryDate(side);
            if (expiryDate == null) {
                return;
            }
            CompanyCustomizationManager.BadgeTemplateSettings settings = chooseBadgeTemplateSettings("Save Badge PDF");
            if (settings == null) {
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Badge PDF");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF files", "pdf"));
            chooser.setSelectedFile(new java.io.File(defaultBadgePdfFileName(employee, side)));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            Path outputPath = ensurePdfExtension(chooser.getSelectedFile().toPath());
            BadgePrintService.saveBadgePdf(outputPath, employee, settings, side, expiryDate);
            JOptionPane.showMessageDialog(this, "Badge PDF saved:\n" + outputPath, "Badge PDF", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save badge PDF.\n\n" + ex.getMessage(), "Badge PDF", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String defaultBadgePdfFileName(BadgePrintService.EmployeeBadgeData employee, BadgePrintService.BadgePrintSide side) {
        String name = employee.displayName() == null || employee.displayName().isBlank() ? employee.username() : employee.displayName();
        return sanitizeFileName(name) + "-badge-" + side.name().toLowerCase() + ".pdf";
    }

    private static String sanitizeFileName(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "employee" : cleaned;
    }

    private static Path ensurePdfExtension(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".pdf")) {
            return path;
        }
        Path parent = path.getParent();
        Path withExtension = Path.of(fileName + ".pdf");
        return parent == null ? withExtension : parent.resolve(withExtension);
    }

    private CompanyCustomizationManager.BadgeTemplateSettings chooseBadgeTemplateSettings(String title) {
        CompanyCustomizationManager.BadgeTemplateSettings settings = CompanyCustomizationManager.loadBadgeTemplateSettings();
        String[] labels = new String[CompanyCustomizationManager.badgeTemplateCount()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = CompanyCustomizationManager.badgeTemplateDisplayName(i);
        }
        int activeIndex = CompanyCustomizationManager.activeBadgeTemplateIndex(settings.layoutData());
        String selected = (String) JOptionPane.showInputDialog(
                this,
                "Which badge template do you want to use?",
                title,
                JOptionPane.QUESTION_MESSAGE,
                null,
                labels,
                labels[Math.max(0, Math.min(labels.length - 1, activeIndex))]
        );
        if (selected == null) {
            return null;
        }
        int selectedIndex = 0;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(selected)) {
                selectedIndex = i;
                break;
            }
        }
        return settings.withLayoutData(CompanyCustomizationManager.badgeTemplateLayout(settings.layoutData(), selectedIndex));
    }

    private BadgePrintService.BadgePrintSide chooseBadgePrintSide() {
        BadgePrintService.BadgePrintSide[] options = BadgePrintService.BadgePrintSide.values();
        BadgePrintService.BadgePrintSide selected = (BadgePrintService.BadgePrintSide) JOptionPane.showInputDialog(
                this,
                "Which side do you want to print?",
                "Print Badge",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                BadgePrintService.BadgePrintSide.BOTH
        );
        return selected;
    }

    private LocalDate chooseBadgeExpiryDate(BadgePrintService.BadgePrintSide side) {
        LocalDate defaultDate = BadgePrintService.defaultExpiryDate();
        if (side == null || !side.includesBack()) {
            return defaultDate;
        }
        while (true) {
            Object input = JOptionPane.showInputDialog(
                    this,
                    "Expiry date (YYYY-MM-DD):",
                    "Badge Expiry Date",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    defaultDate.toString()
            );
            if (input == null) {
                return null;
            }
            String value = input.toString().trim();
            if (value.isBlank()) {
                return defaultDate;
            }
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Enter the expiry date as YYYY-MM-DD.",
                        "Invalid Expiry Date",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void writeSelectedMagStripe() {
        try {
            BadgePrintService.EmployeeBadgeData employee = selectedBadgeData();
            CompanyCustomizationManager.BadgeTemplateSettings settings = CompanyCustomizationManager.loadBadgeTemplateSettings();
            String track1 = BadgePrintService.buildTrackData(settings.magStripeTrack1(), employee, settings);
            String track2 = BadgePrintService.buildTrackData(settings.magStripeTrack2(), employee, settings);
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Write magnetic stripe for " + employee.displayName() + "?\n\nTrack 1: " + track1 + "\nTrack 2: " + track2,
                    "Write Magnetic Stripe",
                    JOptionPane.OK_CANCEL_OPTION
            );
            if (confirm != JOptionPane.OK_OPTION) {
                return;
            }
            String output = BadgePrintService.writeMagStripe(employee, settings);
            JOptionPane.showMessageDialog(this, output, "Magnetic Stripe", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to write magnetic stripe.\n\n" + ex.getMessage(), "Magnetic Stripe", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void programSelectedNfc() {
        try {
            BadgePrintService.EmployeeBadgeData employee = selectedBadgeData();
            CompanyCustomizationManager.BadgeTemplateSettings settings =
                    CompanyCustomizationManager.loadBadgeTemplateSettings();
            String payload = BadgePrintService.buildTrackData(settings.nfcPayloadTemplate(), employee, settings);
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Program RFID/NFC badge for " + employee.displayName() + "?\n\nPayload: " + payload,
                    "Program RFID/NFC",
                    JOptionPane.OK_CANCEL_OPTION
            );
            if (confirm != JOptionPane.OK_OPTION) {
                return;
            }
            String writeOutput = BadgeEncoderService.programNfc(employee, settings);
            String verifyOutput = BadgeEncoderService.verifyNfc(employee, settings);
            JOptionPane.showMessageDialog(this, writeOutput + "\n\n" + verifyOutput,
                    "RFID/NFC", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to program RFID/NFC badge.\n\n" + ex.getMessage(),
                    "RFID/NFC", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void rotateSelectedBadgeId() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }
        String employeeName = composeFullName(firstNameField.getText().trim(),
                middleNameField.getText().trim(), lastNameField.getText().trim());
        if (employeeName.isBlank()) {
            employeeName = usernameField.getText().trim();
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Rotate the badge ID for " + employeeName + "?\n\n"
                        + "The current barcode, magnetic stripe, and RFID/NFC badge will stop working immediately.\n"
                        + "Printing or programming a badge does not rotate the ID.",
                "Rotate Badge ID",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        try {
                String newBadgeId=LanApiClient.updateEmployeeAdmin("ROTATE_BADGE",selectedUserId,null,null,null,UUID.randomUUID().toString()).get("badgeId").getAsString();
                badgeIdField.setText(newBadgeId);
                loadEmployees();
                selectEmployeeInTable(selectedUserId);
                JOptionPane.showMessageDialog(this,
                        "Badge ID rotated successfully. Program or print the replacement badge using the new ID.",
                        "Rotate Badge ID", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to rotate badge ID.\n\n" + ex.getMessage(),
                    "Rotate Badge ID", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BadgePrintService.EmployeeBadgeData selectedBadgeData() throws Exception {
        if (selectedUserId == null) {
            throw new IllegalStateException("Select an employee first.");
        }
        String currentBadgeId = badgeIdField.getText().trim();
        if (currentBadgeId.isEmpty()) {
            throw new IllegalStateException("Save a badge ID for this employee before printing or writing a badge.");
        }
        return BadgePrintService.loadEmployeeBadgeData(selectedUserId);
    }

    private void loadRoles() {
        roleBox.removeAllItems();
        try {for(String roleName:LanApiClient.loadEmployeeAdminState(null).roles()) {
                if (roleName != null && !roleName.isBlank()) {
                    roleBox.addItem(new RoleOption(roleName));
                }
            }} catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load roles: " + ex.getMessage());
        }

        if (roleBox.getItemCount() == 0) {
            roleBox.addItem(new RoleOption("USER"));
        }
    }

    private void loadEmployees() {
        employeeModel.setRowCount(0);

        try {for(var row:LanApiClient.loadEmployeeAdminState(null).employees()) {
                employeeModel.addRow(new Object[]{
                        row.userId(),row.username(),row.fullName(),row.firstName(),row.middleName(),row.lastName(),row.email(),row.phone(),row.photoUrl(),dateText(row.dateOfBirth()),row.badgeId(),row.badgePrintCount(),row.compensationType(),row.salary(),row.role(),row.active(),row.idCardUrl(),dateText(row.hireDate()),dateText(row.deactivationDate())
                });
            }} catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load employees: " + ex.getMessage());
        }
    }

    private void loadStoresForUser(Integer userId) {
        storeModel.setRowCount(0);

        try {for(var row:LanApiClient.loadEmployeeAdminState(userId).stores()) {
                    storeModel.addRow(new Object[]{
                            row.assigned(),String.valueOf(row.locationId()),row.name(),row.address()
                    });
                }} catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load stores: " + ex.getMessage());
        }

        applyStoreFilter();
    }

    private void loadSelectedEmployee() {
        int selectedRow = employeeTable.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }
        selectedRow = employeeTable.convertRowIndexToModel(selectedRow);

        selectedUserId = Integer.parseInt(employeeModel.getValueAt(selectedRow, 0).toString());
        usernameField.setText(employeeModel.getValueAt(selectedRow, 1) == null ? "" : employeeModel.getValueAt(selectedRow, 1).toString());
        String selectedFullName = employeeModel.getValueAt(selectedRow, 2) == null ? "" : employeeModel.getValueAt(selectedRow, 2).toString();
        String selectedFirstName = employeeModel.getValueAt(selectedRow, 3) == null ? "" : employeeModel.getValueAt(selectedRow, 3).toString();
        String selectedMiddleName = employeeModel.getValueAt(selectedRow, 4) == null ? "" : employeeModel.getValueAt(selectedRow, 4).toString();
        String selectedLastName = employeeModel.getValueAt(selectedRow, 5) == null ? "" : employeeModel.getValueAt(selectedRow, 5).toString();
        updatingGeneratedUsername = true;
        firstNameField.setText(selectedFirstName);
        middleNameField.setText(selectedMiddleName);
        lastNameField.setText(selectedLastName);
        updatingGeneratedUsername = false;
        emailField.setText(employeeModel.getValueAt(selectedRow, 6) == null ? "" : employeeModel.getValueAt(selectedRow, 6).toString());
        phoneField.setText(employeeModel.getValueAt(selectedRow, 7) == null ? "" : employeeModel.getValueAt(selectedRow, 7).toString());
        employeePhotoField.setText(employeeModel.getValueAt(selectedRow, 8) == null ? "" : employeeModel.getValueAt(selectedRow, 8).toString());
        refreshEmployeePhotoPreview();
        employeeIdCardDocumentField.setText(employeeModel.getValueAt(selectedRow, 16) == null ? "" : employeeModel.getValueAt(selectedRow, 16).toString());
        dateOfBirthField.setText(employeeModel.getValueAt(selectedRow, 9) == null ? "" : employeeModel.getValueAt(selectedRow, 9).toString());
        hireDateField.setText(employeeModel.getValueAt(selectedRow, 17) == null ? "" : employeeModel.getValueAt(selectedRow, 17).toString());
        deactivationDateField.setText(employeeModel.getValueAt(selectedRow, 18) == null ? "" : employeeModel.getValueAt(selectedRow, 18).toString());
        badgeIdField.setText(employeeModel.getValueAt(selectedRow, 10) == null ? "" : employeeModel.getValueAt(selectedRow, 10).toString());
        selectCompensationType(employeeModel.getValueAt(selectedRow, 12) == null ? "HOURLY" : employeeModel.getValueAt(selectedRow, 12).toString());
        salaryAmountField.setText(employeeModel.getValueAt(selectedRow, 13) == null ? "" : employeeModel.getValueAt(selectedRow, 13).toString());
        loadPayrollSettings(selectedUserId, getSelectedCompensationType());
        selectRole(String.valueOf(employeeModel.getValueAt(selectedRow, 14)));

        Object activeValue = employeeModel.getValueAt(selectedRow, 15);
        activeCheckBox.setSelected(activeValue instanceof Boolean ? (Boolean) activeValue : true);
        originalFirstName = firstNameField.getText().trim();
        originalMiddleName = middleNameField.getText().trim();
        originalLastName = lastNameField.getText().trim();
        originalFullName = selectedFullName;
        originalEmail = emailField.getText().trim();
        originalIsActive = activeCheckBox.isSelected();
        lastGeneratedUsername = generateUsername(originalFirstName, originalLastName);

        passwordField.setText("");
        loadStoresForUser(selectedUserId);
        deleteButton.setEnabled(true);
        previewBadgeButton.setEnabled(true);
        printBadgeButton.setEnabled(true);
        saveBadgePdfButton.setEnabled(true);
        writeMagStripeButton.setEnabled(true);
        programNfcButton.setEnabled(true);
        rotateBadgeIdButton.setEnabled(true);
    }

    private void addEmployee() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String firstName = firstNameField.getText().trim();
        String middleName = middleNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String fullName = composeFullName(firstName, middleName, lastName);
        String email = emailField.getText().trim();
        String phoneNumber = phoneField.getText().trim();
        String employeePhotoUrl = employeePhotoField.getText().trim();
        String idCardDocumentUrl = employeeIdCardDocumentField.getText().trim();
        LocalDate dateOfBirth = parseOptionalDateOfBirth();
        if (dateOfBirthField.getText() != null && !dateOfBirthField.getText().trim().isEmpty() && dateOfBirth == null) {
            return;
        }
        LocalDate hireDate = parseHireDate();
        if (hireDate == null) return;
        String compensationType = getSelectedCompensationType();
        BigDecimal salary = parseMoneyAmount(salaryAmountField, "Salary");
        if (salary == null) {
            return;
        }
        PeriodType payrollPeriodType = selectedPayrollPeriodType();
        BigDecimal workHourLimit = parseWorkHourLimit(compensationType);
        if (workHourLimit == null) return;
        String role = getSelectedRole();
        boolean isActive = activeCheckBox.isSelected();

        List<String> missingFields = missingRequiredEmployeeFields(
                true, password, firstName, lastName, email, salaryAmountField.getText(), role);
        if (!missingFields.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Complete the required fields: " + String.join(", ", missingFields) + ".");
            return;
        }
        if (username.isEmpty()) {
            username = generateUsername(firstName, lastName);
            usernameField.setText(username);
        }

        List<Integer> selectedLocationIds = getSelectedLocationIds();
        if (selectedLocationIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one assigned store.");
            return;
        }
        try {
            employeePhotoUrl = EmployeePhotoService.uploadLocalPhotoIfNeeded(employeePhotoUrl, username);
            employeePhotoField.setText(employeePhotoUrl);
            refreshEmployeePhotoPreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload employee photo: " + ex.getMessage(), "Employee Photo", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            idCardDocumentUrl = EmployeeDocumentService.uploadLocalIdCardDocumentIfNeeded(idCardDocumentUrl, username);
            employeeIdCardDocumentField.setText(idCardDocumentUrl);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload ID card document: " + ex.getMessage(), "ID Card Document", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
                String token=SupabaseSessionManager.getValidAccessToken();
                LanEmployeeAdminService.SaveRequest request=new LanEmployeeAdminService.SaveRequest(username,password,firstName,middleName,lastName,fullName,email,phoneNumber,employeePhotoUrl,idCardDocumentUrl,dateOfBirth,hireDate,compensationType,salary,role,isActive,selectedLocationIds,payrollPeriodType,workHourLimit,true,token);
                int newUserId=LanApiClient.updateEmployeeAdmin("CREATE",null,request,null,null,UUID.randomUUID().toString()).get("userId").getAsInt();
                JOptionPane.showMessageDialog(this, "Employee added successfully.");
                loadEmployees();
                selectEmployeeInTable(newUserId);
        } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to add employee: " + getFriendlyEmployeeError(ex));
        }
    }

    private void updateEmployee() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String firstName = firstNameField.getText().trim();
        String middleName = middleNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String fullName = composeFullName(firstName, middleName, lastName);
        String email = emailField.getText().trim();
        String phoneNumber = phoneField.getText().trim();
        String employeePhotoUrl = employeePhotoField.getText().trim();
        String idCardDocumentUrl = employeeIdCardDocumentField.getText().trim();
        LocalDate dateOfBirth = parseOptionalDateOfBirth();
        if (dateOfBirthField.getText() != null && !dateOfBirthField.getText().trim().isEmpty() && dateOfBirth == null) {
            return;
        }
        LocalDate hireDate = parseHireDate();
        if (hireDate == null) return;
        String compensationType = getSelectedCompensationType();
        BigDecimal salary = parseMoneyAmount(salaryAmountField, "Salary");
        if (salary == null) {
            return;
        }
        PeriodType payrollPeriodType = selectedPayrollPeriodType();
        BigDecimal workHourLimit = parseWorkHourLimit(compensationType);
        if (workHourLimit == null) return;
        String role = getSelectedRole();
        boolean isActive = activeCheckBox.isSelected();

        List<String> missingFields = missingRequiredEmployeeFields(
                false, password, firstName, lastName, email, salaryAmountField.getText(), role);
        if (!missingFields.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Complete the required fields: " + String.join(", ", missingFields) + ".");
            return;
        }
        if (username.isEmpty()) {
            username = generateUsername(firstName, lastName);
            usernameField.setText(username);
        }

        List<Integer> selectedLocationIds = getSelectedLocationIds();
        if (selectedLocationIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one assigned store.");
            return;
        }
        try {
            employeePhotoUrl = EmployeePhotoService.uploadLocalPhotoIfNeeded(employeePhotoUrl, username);
            employeePhotoField.setText(employeePhotoUrl);
            refreshEmployeePhotoPreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload employee photo: " + ex.getMessage(), "Employee Photo", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            idCardDocumentUrl = EmployeeDocumentService.uploadLocalIdCardDocumentIfNeeded(idCardDocumentUrl, username);
            employeeIdCardDocumentField.setText(idCardDocumentUrl);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload ID card document: " + ex.getMessage(), "ID Card Document", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String token = SupabaseSessionManager.getValidAccessToken();
            boolean payrollChanged = payrollPeriodType != originalPayrollPeriodType
                    || workHourLimit.compareTo(originalWorkHourLimit) != 0;
            LanEmployeeAdminService.SaveRequest request = new LanEmployeeAdminService.SaveRequest(
                    username, password, firstName, middleName, lastName, fullName, email, phoneNumber,
                    employeePhotoUrl, idCardDocumentUrl, dateOfBirth, hireDate, compensationType, salary,
                    role, isActive, selectedLocationIds, payrollPeriodType, workHourLimit, payrollChanged, token);
            LanApiClient.updateEmployeeAdmin("UPDATE", selectedUserId, request, null, null,
                    UUID.randomUUID().toString());
            JOptionPane.showMessageDialog(this, "Employee updated successfully.");
            clearFields();
            loadEmployees();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update employee: " + getFriendlyEmployeeError(ex));
        }
    }

    private static List<String> missingRequiredEmployeeFields(boolean creating, String password,
                                                               String firstName, String lastName,
                                                               String email, String salary, String role) {
        List<String> missing = new ArrayList<>();
        if (creating && password.isBlank()) missing.add("Password");
        if (firstName.isBlank()) missing.add("First Name");
        if (lastName.isBlank()) missing.add("Last Name");
        if (email.isBlank()) missing.add("Email");
        if (salary.isBlank()) missing.add("Hourly Rate / Salary");
        if (role.isBlank()) missing.add("Role");
        return missing;
    }

    private void clearFields() {
        selectedUserId = null;
        usernameField.setText("");
        passwordField.setText("");
        updatingGeneratedUsername = true;
        firstNameField.setText("");
        middleNameField.setText("");
        lastNameField.setText("");
        updatingGeneratedUsername = false;
        emailField.setText("");
        phoneField.setText("");
        employeePhotoField.setText("");
        refreshEmployeePhotoPreview();
        employeeIdCardDocumentField.setText("");
        dateOfBirthField.setText("");
        hireDateField.setText(storeToday().toString());
        deactivationDateField.setText("");
        badgeIdField.setText("");
        selectCompensationType("HOURLY");
        salaryAmountField.setText("");
        loadingPayrollSettings = true;
        payrollPeriodBox.setSelectedItem(PeriodType.SEMI_MONTHLY);
        workHourLimitField.setText(EmployeePayrollSettingsService.DEFAULT_SEMI_MONTHLY_LIMIT.toPlainString());
        loadingPayrollSettings = false;
        originalPayrollPeriodType = PeriodType.SEMI_MONTHLY;
        originalWorkHourLimit = EmployeePayrollSettingsService.DEFAULT_SEMI_MONTHLY_LIMIT;
        payrollSettingsStatusLabel.setText("Applies to hourly employees only.");
        refreshPayrollSettingsControls();
        roleBox.setSelectedIndex(0);
        activeCheckBox.setSelected(true);
        activeCheckBox.setEnabled(true);
        originalFirstName = "";
        originalMiddleName = "";
        originalLastName = "";
        originalFullName = "";
        originalEmail = "";
        originalIsActive = true;
        lastGeneratedUsername = "";
        employeeTable.clearSelection();
        storeSearchField.setText("");
        loadStoresForUser(null);
        deleteButton.setEnabled(false);
        previewBadgeButton.setEnabled(false);
        printBadgeButton.setEnabled(false);
        saveBadgePdfButton.setEnabled(false);
        writeMagStripeButton.setEnabled(false);
        programNfcButton.setEnabled(false);
        rotateBadgeIdButton.setEnabled(false);
        usernameField.requestFocusInWindow();
    }

    private LocalDate parseOptionalDateOfBirth() {
        String value = dateOfBirthField.getText() == null ? "" : dateOfBirthField.getText().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Date of birth must use YYYY-MM-DD.");
            return null;
        }
    }

    private LocalDate parseHireDate() {
        String value = hireDateField.getText() == null ? "" : hireDateField.getText().trim();
        if (value.isEmpty()) {
            hireDateField.setText(storeToday().toString());
            return storeToday();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Hire date must use YYYY-MM-DD.");
            return null;
        }
    }

    private void updateGeneratedUsername() {
        if (updatingGeneratedUsername) {
            return;
        }

        String currentUsername = usernameField.getText().trim();
        if (!currentUsername.isEmpty() && !currentUsername.equalsIgnoreCase(lastGeneratedUsername)) {
            return;
        }

        String generatedUsername = generateUsername(firstNameField.getText().trim(), lastNameField.getText().trim());
        updatingGeneratedUsername = true;
        usernameField.setText(generatedUsername);
        updatingGeneratedUsername = false;
        lastGeneratedUsername = generatedUsername;
    }

    private static String generateUsername(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            return "";
        }
        String firstInitial = firstName.trim().substring(0, 1).toUpperCase();
        return firstInitial + "-" + toDisplayNamePart(lastName);
    }

    private static String composeFullName(String firstName, String middleName, String lastName) {
        StringBuilder fullName = new StringBuilder();
        appendNamePart(fullName, firstName);
        appendNamePart(fullName, middleName);
        appendNamePart(fullName, lastName);
        return fullName.toString();
    }

    private static void appendNamePart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" ");
        }
        builder.append(value.trim());
    }

    private static String toDisplayNamePart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase();
        }
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1);
    }

    private static boolean sameText(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }

    private void selectEmployeeInTable(int userId) {
        for (int modelRow = 0; modelRow < employeeModel.getRowCount(); modelRow++) {
            Object value = employeeModel.getValueAt(modelRow, 0);
            if (value != null && Integer.parseInt(value.toString()) == userId) {
                int viewRow = employeeTable.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    employeeTable.setRowSelectionInterval(viewRow, viewRow);
                    employeeTable.scrollRectToVisible(employeeTable.getCellRect(viewRow, 0, true));
                    loadSelectedEmployee();
                }
                return;
            }
        }
    }

    private List<Integer> getSelectedLocationIds() {
        List<Integer> locationIds = new ArrayList<>();
        for (int row = 0; row < storeModel.getRowCount(); row++) {
            Object assignedValue = storeModel.getValueAt(row, 0);
            boolean assigned = assignedValue instanceof Boolean && (Boolean) assignedValue;
            if (assigned) {
                locationIds.add(Integer.parseInt(storeModel.getValueAt(row, 1).toString()));
            }
        }
        return locationIds;
    }

    private static String getFriendlyEmployeeError(Exception ex) {
        if (isDuplicateBadgeError(ex)) {
            return "That badge ID is already assigned to another employee.";
        }
        return ex.getMessage();
    }

    private static boolean isDuplicateBadgeError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase().contains("badge")
                    && (message.contains("23505") || message.toLowerCase().contains("duplicate"))) {
                return true;
            }
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("duplicate")
                        && normalized.contains("badge")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String getSelectedRole() {
        Object selectedRole = roleBox.getSelectedItem();
        if (selectedRole instanceof RoleOption roleOption) {
            return roleOption.roleName;
        }
        return selectedRole == null ? "" : selectedRole.toString();
    }

    private String getSelectedCompensationType() {
        Object selected = compensationTypeBox.getSelectedItem();
        if (selected instanceof CompensationOption option) {
            return option.key;
        }
        return "HOURLY";
    }

    private void selectCompensationType(String compensationType) {
        String key = compensationType == null || compensationType.isBlank()
                ? "HOURLY"
                : compensationType.trim().toUpperCase();

        for (int i = 0; i < compensationTypeBox.getItemCount(); i++) {
            CompensationOption option = compensationTypeBox.getItemAt(i);
            if (option.key.equalsIgnoreCase(key)) {
                compensationTypeBox.setSelectedIndex(i);
                return;
            }
        }
        compensationTypeBox.setSelectedIndex(0);
    }

    private void refreshPayrollSettingsControls() {
        String compensationType = getSelectedCompensationType();
        boolean hourly = EmployeePayrollSettingsService.isHourly(compensationType);
        boolean selectablePeriod = EmployeePayrollSettingsService.usesSelectablePeriod(compensationType);
        payrollPeriodBox.setEnabled(selectablePeriod);
        workHourLimitField.setEnabled(hourly);
        salaryAmountLabel.setText(switch (compensationType.toUpperCase()) {
            case "SALARY" -> "Salary Per Period *:";
            case "DAILY" -> "Daily Rate *:";
            default -> "Hourly Rate *:";
        });
        if ("SALARY".equalsIgnoreCase(compensationType)) {
            payrollSettingsStatusLabel.setText("Salary is paid in full once per selected pay period; overtime does not apply.");
        } else if (!hourly) {
            payrollSettingsStatusLabel.setText("Semi-monthly; overtime does not apply to this pay type.");
        }
    }

    private void loadPayrollSettings(int userId, String compensationType) {
        loadingPayrollSettings = true;
        try {
            EmployeePayrollSettingsService.SettingView view =
                    LanApiClient.loadEmployeeAdminState(userId).payroll();
            if (view == null) {
                throw new IllegalStateException("Payroll settings were not returned by the server.");
            }
            EmployeePayrollSettingsService.PayrollSetting displayed = view.pending() == null
                    ? view.current() : view.pending();
            payrollPeriodBox.setSelectedItem(displayed.periodType());
            workHourLimitField.setText(displayed.workHourLimit().stripTrailingZeros().toPlainString());
            originalPayrollPeriodType = displayed.periodType();
            originalWorkHourLimit = displayed.workHourLimit();
            if ("SALARY".equalsIgnoreCase(compensationType)) {
                payrollSettingsStatusLabel.setText(displayed.periodType().label()
                        + "; salary is paid in full once per period and overtime does not apply.");
            } else if (!EmployeePayrollSettingsService.isHourly(compensationType)) {
                payrollSettingsStatusLabel.setText("Semi-monthly; overtime does not apply to this pay type.");
            } else if (view.pending() == null) {
                payrollSettingsStatusLabel.setText("Current since " + view.current().effectiveFrom());
            } else {
                payrollSettingsStatusLabel.setText("Current: " + view.current().periodType().label()
                        + " / " + view.current().workHourLimit().stripTrailingZeros().toPlainString()
                        + " hours. Pending from " + view.pending().effectiveFrom());
            }
        } catch (Exception ex) {
            payrollPeriodBox.setSelectedItem(PeriodType.SEMI_MONTHLY);
            workHourLimitField.setText(EmployeePayrollSettingsService.DEFAULT_SEMI_MONTHLY_LIMIT.toPlainString());
            originalPayrollPeriodType = PeriodType.SEMI_MONTHLY;
            originalWorkHourLimit = EmployeePayrollSettingsService.DEFAULT_SEMI_MONTHLY_LIMIT;
            payrollSettingsStatusLabel.setText("Could not load payroll setting: " + ex.getMessage());
        } finally {
            loadingPayrollSettings = false;
            refreshPayrollSettingsControls();
        }
    }

    private PeriodType selectedPayrollPeriodType() {
        Object selected = payrollPeriodBox.getSelectedItem();
        return selected instanceof PeriodType type ? type : PeriodType.SEMI_MONTHLY;
    }

    private BigDecimal parseWorkHourLimit(String compensationType) {
        if (!EmployeePayrollSettingsService.isHourly(compensationType)) {
            return selectedPayrollPeriodType().defaultLimit();
        }
        String value = workHourLimitField.getText().trim();
        try {
            BigDecimal limit = new BigDecimal(value);
            if (limit.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            return limit.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid work hour limit greater than zero.");
            return null;
        }
    }

    private static LocalDate storeToday() {
        String timezone = SessionManager.getCurrentLocationTimezone();
        try {
            return LocalDate.now(ZoneId.of(timezone == null || timezone.isBlank()
                    ? ZoneId.systemDefault().getId() : timezone));
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private static String dateText(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private BigDecimal parseMoneyAmount(JTextField field, String label) {
        String value = field.getText().trim();
        if (value.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal amount = new BigDecimal(value.replace("$", "").replace(",", ""));
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, label + " cannot be negative.");
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid " + label.toLowerCase() + ".");
            return null;
        }
    }

    private void selectRole(String roleName) {
        if (roleName == null) {
            roleBox.setSelectedIndex(roleBox.getItemCount() > 0 ? 0 : -1);
            return;
        }

        for (int i = 0; i < roleBox.getItemCount(); i++) {
            RoleOption option = roleBox.getItemAt(i);
            if (option.roleName.equalsIgnoreCase(roleName)) {
                roleBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String formatRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }

        String[] words = roleName.trim().replace("_", " ").split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!formatted.isEmpty()) {
                formatted.append(" ");
            }
            formatted.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                formatted.append(word.substring(1).toLowerCase());
            }
        }
        return formatted.toString();
    }

    private static class RoleOption {
        private final String roleName;

        private RoleOption(String roleName) {
            this.roleName = roleName;
        }

        @Override
        public String toString() {
            return formatRoleName(roleName);
        }
    }

    private static class CompensationOption {
        private final String key;
        private final String label;

        private CompensationOption(String key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private void openAssignStoresDialog() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        JDialog dialog = new JDialog(this, "Assign Stores", true);
        dialog.setSize(700, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JTextField searchField = new JTextField();
        topPanel.add(new JLabel("Search Store:"), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);

        DefaultTableModel storeModel = new DefaultTableModel(
                new Object[]{"Assigned", "Location ID", "Store Name", "Address"}, 0
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return Boolean.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        JTable storeTable = new JTable(storeModel);
        storeTable.setRowHeight(24);
        TableRowSorter<DefaultTableModel> dialogSorter = new TableRowSorter<>(storeModel);
        storeTable.setRowSorter(dialogSorter);
        JScrollPane scrollPane = new JScrollPane(storeTable);

        TableColumn assignedColumn = storeTable.getColumnModel().getColumn(0);
        assignedColumn.setPreferredWidth(80);
        assignedColumn.setMaxWidth(100);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save Assignments");
        JButton closeButton = new JButton("Close");
        bottomPanel.add(saveButton);
        bottomPanel.add(closeButton);

        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        Runnable loadStores = () -> {
            storeModel.setRowCount(0);
            try {
                for (LanEmployeeAdminService.Store store : LanApiClient.loadEmployeeAdminState(selectedUserId).stores()) {
                    storeModel.addRow(new Object[]{store.assigned(), String.valueOf(store.locationId()),
                            store.name(), store.address()});
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to load stores: " + ex.getMessage());
            }
        };

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateFilter() {
                String value = searchField.getText().trim();
                dialogSorter.setRowFilter(value.isBlank() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(value)));
            }
            @Override public void insertUpdate(DocumentEvent e) { updateFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { updateFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { updateFilter(); }
        });

        saveButton.addActionListener(e -> {
            try {
                List<Integer> selectedLocationIds = new ArrayList<>();
                for (int i = 0; i < storeModel.getRowCount(); i++) {
                    if (Boolean.TRUE.equals(storeModel.getValueAt(i, 0))) {
                        selectedLocationIds.add(Integer.parseInt(storeModel.getValueAt(i, 1).toString()));
                    }
                }
                LanApiClient.updateEmployeeAdmin("SAVE_STORES", selectedUserId, null,
                        selectedLocationIds, null, UUID.randomUUID().toString());
                JOptionPane.showMessageDialog(dialog, "Store assignments saved.");
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to save assignments: " + ex.getMessage());
            }
        });

        closeButton.addActionListener(e -> dialog.dispose());

        loadStores.run();
        dialog.setVisible(true);
    }

    private void deleteEmployee() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Deactivate this employee? Their POS history and store assignments will be kept, but their Supabase auth account will be removed so they cannot sign in.",
                "Confirm Deactivation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            String token = SupabaseSessionManager.getValidAccessToken();
            LanApiClient.updateEmployeeAdmin("DEACTIVATE", selectedUserId, null, null, token,
                    UUID.randomUUID().toString());
            JOptionPane.showMessageDialog(this,
                    "Employee deactivated successfully. Their history was kept for reporting and audit records.");
            clearFields();
            loadEmployees();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to deactivate employee: " + ex.getMessage());
        }
    }

    private void applyEmployeeFilter() {
        if (employeeSorter == null) {
            return;
        }

        String text = employeeSearchField == null ? "" : employeeSearchField.getText().trim();
        if (text.isEmpty()) {
            employeeSorter.setRowFilter(null);
        } else {
            employeeSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }

    private void applyStoreFilter() {
        if (storeSorter == null) {
            return;
        }

        String text = storeSearchField == null ? "" : storeSearchField.getText().trim();
        if (text.isEmpty()) {
            storeSorter.setRowFilter(null);
        } else {
            storeSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }
}
