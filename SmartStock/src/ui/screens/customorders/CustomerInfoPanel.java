package ui.screens.customorders;

import services.CustomOrderDataService;
import services.CustomOrderDataService.CustomerOption;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class CustomerInfoPanel extends JPanel {
    private final JTextField nameField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JPopupMenu searchPopup = new JPopupMenu();
    private final DefaultListModel<CustomerOption> searchModel = new DefaultListModel<>();
    private final JList<CustomerOption> searchList = new JList<>(searchModel);
    private CustomerOption selectedCustomer;
    private boolean updatingSearch;

    public CustomerInfoPanel() {
        super(new GridBagLayout());
        buildLayout();
        setupSearch();
    }

    public CustomerOption getSelectedCustomer() {
        return selectedCustomer;
    }

    public String getCustomerName() {
        return nameField.getText().trim();
    }

    public String getCustomerPhone() {
        return phoneField.getText().trim();
    }

    public void clear() {
        selectedCustomer = null;
        updatingSearch = true;
        nameField.setText("");
        updatingSearch = false;
        phoneField.setText("");
        searchPopup.setVisible(false);
    }

    private void buildLayout() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(nameField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(new JLabel("Phone:"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 1;
        add(phoneField, gbc);
    }

    private void setupSearch() {
        searchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        searchList.setVisibleRowCount(8);
        searchPopup.setFocusable(false);
        JScrollPane scrollPane = new JScrollPane(searchList);
        scrollPane.setPreferredSize(new Dimension(360, 180));
        searchPopup.add(scrollPane);

        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                searchFromNameField();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                searchFromNameField();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                searchFromNameField();
            }
        });
        nameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                searchFromNameField();
            }
        });
        nameField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (!searchPopup.isVisible()) {
                    return;
                }
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                    int next = Math.min(searchList.getSelectedIndex() + 1, searchModel.getSize() - 1);
                    searchList.setSelectedIndex(Math.max(next, 0));
                    e.consume();
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                    int previous = Math.max(searchList.getSelectedIndex() - 1, 0);
                    searchList.setSelectedIndex(previous);
                    e.consume();
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    selectHighlightedCustomer();
                    e.consume();
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    searchPopup.setVisible(false);
                }
            }
        });
        searchList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    selectHighlightedCustomer();
                }
            }
        });
    }

    private void searchFromNameField() {
        if (updatingSearch) {
            return;
        }
        String text = nameField.getText().trim();
        if (selectedCustomer != null && !text.equals(selectedCustomer.name())) {
            selectedCustomer = null;
            phoneField.setText("");
        }
        SwingUtilities.invokeLater(() -> {
            loadCustomers(text);
            showCustomerPopup();
        });
    }

    private void loadCustomers(String search) {
        searchModel.clear();
        try {
            for (CustomerOption customer : CustomOrderDataService.searchCustomers(search)) {
                searchModel.addElement(customer);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load customers: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showCustomerPopup() {
        if (!nameField.isShowing()) {
            return;
        }
        if (searchModel.isEmpty()) {
            searchPopup.setVisible(false);
            return;
        }
        searchList.setSelectedIndex(0);
        searchPopup.setPopupSize(nameField.getWidth(), 180);
        searchPopup.show(nameField, 0, nameField.getHeight());
        nameField.requestFocusInWindow();
    }

    private void selectHighlightedCustomer() {
        CustomerOption customer = searchList.getSelectedValue();
        if (customer == null) {
            return;
        }
        selectedCustomer = customer;
        updatingSearch = true;
        nameField.setText(customer.name() == null ? "" : customer.name());
        updatingSearch = false;
        phoneField.setText(customer.phone() == null ? "" : customer.phone());
        searchPopup.setVisible(false);
        nameField.requestFocusInWindow();
    }
}
