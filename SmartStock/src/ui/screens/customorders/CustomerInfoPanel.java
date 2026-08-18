package ui.screens.customorders;

import services.CustomOrderDataService;
import services.CustomOrderDataService.CustomerOption;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiDebouncer;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CustomerInfoPanel extends JPanel {
    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JTextField accountNumberField = new JTextField();
    private final JTextField emailField = new JTextField();
    private JComponent trailingField;
    private String trailingLabel;
    private final JPopupMenu searchPopup = new JPopupMenu();
    private final DefaultListModel<CustomerOption> searchModel = new DefaultListModel<>();
    private final JList<CustomerOption> searchList = new JList<>(searchModel);
    private CustomerOption selectedCustomer;
    private boolean updatingSearch;
    private final LoadingStatePanel searchLoadingState = new LoadingStatePanel();

    public CustomerInfoPanel() {
        super(new GridBagLayout());
        buildLayout();
        setupSearch();
    }

    public CustomerInfoPanel(String trailingLabel, JComponent trailingField) {
        super(new GridBagLayout());
        this.trailingLabel = trailingLabel;
        this.trailingField = trailingField;
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

    public String getCustomerAccountNumber(){return accountNumberField.getText().trim();}
    public String getCustomerEmail(){return emailField.getText().trim();}

    public void clear() {
        selectedCustomer = null;
        updatingSearch = true;
        searchField.setText("");
        nameField.setText("");
        updatingSearch = false;
        phoneField.setText("");
        accountNumberField.setText("");
        emailField.setText("");
        searchPopup.setVisible(false);
    }

    public void preloadCustomers(List<CustomerOption> customers) {
        CustomerSearchSnapshot snapshot = new CustomerSearchSnapshot(customers == null ? List.of() : List.copyOf(customers));
        SessionDataCache.put("custom-orders:customers:", snapshot);
    }

    private void buildLayout() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        accountNumberField.setEditable(false);
        emailField.setEditable(false);
        gbc.gridx = 0;gbc.gridy = 0;
        gbc.weightx = 0;
        add(new JLabel("Search Customer:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        searchField.setToolTipText("Search by customer name, email, phone number, or account number");
        add(searchField, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 0;gbc.gridy = 1;
        gbc.weightx = 0;
        add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;gbc.weightx = 1;add(nameField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(new JLabel("Phone:"), gbc);
        gbc.gridx = 3;
        gbc.weightx = .35;
        phoneField.setColumns(16);
        add(phoneField, gbc);
        gbc.gridx=0;gbc.gridy=2;gbc.weightx=0;add(new JLabel("Account #:"),gbc);
        gbc.gridx=1;gbc.weightx=1;add(accountNumberField,gbc);
        gbc.gridx=2;gbc.weightx=0;add(new JLabel("Email:"),gbc);
        gbc.gridx=3;gbc.weightx=.35;add(emailField,gbc);
        if (trailingField != null) {
            gbc.gridx = 4;
            gbc.gridy = 1;
            gbc.weightx = 0;
            add(new JLabel(trailingLabel == null ? "" : trailingLabel), gbc);
            gbc.gridx = 5;
            gbc.weightx = 0;
            add(trailingField, gbc);
        }
    }

    private void setupSearch() {
        searchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        searchList.setVisibleRowCount(8);
        searchPopup.setFocusable(false);
        JScrollPane scrollPane = new JScrollPane(searchList);
        scrollPane.setPreferredSize(new Dimension(360, 180));
        searchPopup.add(scrollPane);

        UiDebouncer.bind(searchField, 300, this::searchFromSearchField);
        UiDebouncer.bind(nameField,300,()->{if(!updatingSearch&&selectedCustomer!=null&&!nameField.getText().trim().equals(selectedCustomer.name())){selectedCustomer=null;accountNumberField.setText("");emailField.setText("");}});
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                searchFromSearchField();
            }
        });
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
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

    private void searchFromSearchField() {
        if (updatingSearch) {
            return;
        }
        String text = searchField.getText().trim();
        if(selectedCustomer!=null&&text.equals(selectedCustomer.toString()))return;
        if (selectedCustomer != null && !text.equals(selectedCustomer.toString())) {
            selectedCustomer = null;
            accountNumberField.setText("");emailField.setText("");
        }
        loadCustomers(text);
    }

    private void loadCustomers(String search) {
        String query=search==null?"":search.trim();
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null) return;
        CachedUiLoader.loadIfStale(owner,"custom-orders.customer-search","custom-orders:customers:"+query,
                CustomerSearchSnapshot.class, SessionDataCache.SCREEN_TTL,searchLoadingState,
                ()->new CustomerSearchSnapshot(CustomOrderDataService.searchCustomers(query)),snapshot->{
                    if(!searchField.getText().trim().equals(query))return;
                    searchModel.clear();snapshot.customers().forEach(searchModel::addElement);showCustomerPopup();
                });
    }

    private record CustomerSearchSnapshot(List<CustomerOption> customers) { }

    private void showCustomerPopup() {
        if (!searchField.isShowing()) {
            return;
        }
        if (searchModel.isEmpty()) {
            searchPopup.setVisible(false);
            return;
        }
        searchList.setSelectedIndex(0);
        searchPopup.setPopupSize(searchField.getWidth(), 180);
        searchPopup.show(searchField, 0, searchField.getHeight());
        searchField.requestFocusInWindow();
    }

    private void selectHighlightedCustomer() {
        CustomerOption customer = searchList.getSelectedValue();
        if (customer == null) {
            return;
        }
        selectedCustomer = customer;
        updatingSearch = true;
        searchField.setText(customer.toString());
        nameField.setText(customer.name() == null ? "" : customer.name());
        updatingSearch = false;
        phoneField.setText(customer.phone() == null ? "" : customer.phone());
        accountNumberField.setText(customer.accountNumber() == null ? "" : customer.accountNumber());
        emailField.setText(customer.email() == null ? "" : customer.email());
        searchPopup.setVisible(false);
        phoneField.requestFocusInWindow();
    }
}
