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
    private final JCheckBox applyCustomerDiscountBox=new JCheckBox("Apply customer custom-order discount");
    private JComponent trailingField;
    private String trailingLabel;
    private final JPopupMenu searchPopup = new JPopupMenu();
    private final DefaultListModel<CustomerOption> searchModel = new DefaultListModel<>();
    private final JList<CustomerOption> searchList = new JList<>(searchModel);
    private CustomerOption selectedCustomer;
    private boolean updatingSearch;
    private final LoadingStatePanel searchLoadingState = new LoadingStatePanel();
    private final JPanel phoneMatches=new JPanel();
    private long phoneLookupVersion;
    @FunctionalInterface interface PhoneLookup {com.google.gson.JsonObject find(String phone)throws Exception;}
    private PhoneLookup phoneLookup=services.LanApiClient::customOrderCustomerPhone;
    CustomerInfoPanel(PhoneLookup lookup){this();phoneLookup=lookup;}

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
    public boolean applyCustomerDiscount(){return applyCustomerDiscountBox.isSelected();}
    public record Draft(CustomerOption customer,String name,String phone,String accountNumber,String email,boolean applyDiscount){}
    public Draft draft(){return new Draft(selectedCustomer,getCustomerName(),getCustomerPhone(),getCustomerAccountNumber(),getCustomerEmail(),applyCustomerDiscount());}
    public void restoreDraft(Draft draft){
        clear();selectedCustomer=draft.customer();updatingSearch=true;
        try{nameField.setText(draft.name());searchField.setText(selectedCustomer==null?draft.name():selectedCustomer.toString());phoneField.setText(draft.phone());accountNumberField.setText(draft.accountNumber());emailField.setText(draft.email());}
        finally{updatingSearch=false;}
        boolean available=selectedCustomer!=null&&selectedCustomer.customOrderDiscountEnabled();
        applyCustomerDiscountBox.setVisible(available);applyCustomerDiscountBox.setSelected(available&&draft.applyDiscount());
        applyCustomerDiscountBox.setText(available&&selectedCustomer.customOrderDiscountPercent()!=null?"Apply customer custom-order discount ("+selectedCustomer.customOrderDiscountPercent().stripTrailingZeros().toPlainString()+"%)":"Apply customer custom-order discount");
    }

    public void clear() {
        selectedCustomer = null;
        updatingSearch = true;
        searchField.setText("");
        nameField.setText("");
        updatingSearch = false;
        phoneField.setText("");
        accountNumberField.setText("");
        emailField.setText("");
        applyCustomerDiscountBox.setSelected(false);applyCustomerDiscountBox.setVisible(false);
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
        gbc.gridx=0;gbc.gridy=3;gbc.gridwidth=4;gbc.weightx=1;applyCustomerDiscountBox.setVisible(false);add(applyCustomerDiscountBox,gbc);gbc.gridwidth=1;
        gbc.gridx=0;gbc.gridy=4;gbc.gridwidth=4;phoneMatches.setLayout(new BoxLayout(phoneMatches,BoxLayout.Y_AXIS));phoneMatches.setVisible(false);add(phoneMatches,gbc);gbc.gridwidth=1;
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
        UiDebouncer.bind(phoneField,350,this::lookupPhone);
        phoneField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            private void changed(){phoneLookupVersion++;phoneMatches.removeAll();phoneMatches.setVisible(false);}
            public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}
        });
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

    private void lookupPhone(){
        String query=phoneField.getText().trim();long version=++phoneLookupVersion;
        if(updatingSearch||query.isBlank())return;
        try{utils.CustomerPhoneNumber.normalize(query);}catch(IllegalArgumentException e){return;}
        new SwingWorker<com.google.gson.JsonObject,Void>(){
            protected com.google.gson.JsonObject doInBackground()throws Exception{return phoneLookup.find(query);}
            protected void done(){if(version!=phoneLookupVersion||!query.equals(phoneField.getText().trim()))return;
                try{var result=get();phoneMatches.removeAll();
                    for(var element:result.getAsJsonArray("customers")){var c=element.getAsJsonObject();int id=c.get("customerId").getAsInt();if(selectedCustomer!=null&&selectedCustomer.customerId()==id)continue;
                        if(!c.get("active").getAsBoolean()){phoneMatches.add(new JLabel(c.get("name").getAsString()+" — inactive account; ask an administrator."));continue;}
                        JButton choose=new JButton("Select existing customer: "+c.get("name").getAsString()+" · "+c.get("accountNumber").getAsString());
                        choose.addActionListener(e->{if(version==phoneLookupVersion)selectCustomer(new com.google.gson.Gson().fromJson(c,CustomerOption.class));});phoneMatches.add(choose);
                    }
                    if(result.get("reserved").getAsBoolean()&&result.getAsJsonArray("customers").isEmpty())phoneMatches.add(new JLabel("This number is reserved. Ask an administrator to check the customer account."));
                    phoneMatches.setVisible(phoneMatches.getComponentCount()>0);revalidate();repaint();
                }catch(Exception e){phoneMatches.removeAll();phoneMatches.add(new JLabel("Phone lookup unavailable. Use Search Customer or retry."));phoneMatches.setVisible(true);revalidate();}
            }
        }.execute();
    }

    private void selectHighlightedCustomer() {
        CustomerOption customer = searchList.getSelectedValue();
        if (customer == null) {
            return;
        }
        selectCustomer(customer);
    }
    private void selectCustomer(CustomerOption customer){
        selectedCustomer = customer;
        updatingSearch = true;
        searchField.setText(customer.toString());
        nameField.setText(customer.name() == null ? "" : customer.name());
        updatingSearch = false;
        phoneField.setText(customer.phone() == null ? "" : customer.phone());
        accountNumberField.setText(customer.accountNumber() == null ? "" : customer.accountNumber());
        emailField.setText(customer.email() == null ? "" : customer.email());
        boolean discount=customer.customOrderDiscountEnabled()&&customer.customOrderDiscountPercent()!=null&&customer.customOrderDiscountPercent().signum()>0;applyCustomerDiscountBox.setVisible(discount);applyCustomerDiscountBox.setSelected(discount);applyCustomerDiscountBox.setText(discount?"Apply customer custom-order discount ("+customer.customOrderDiscountPercent().stripTrailingZeros().toPlainString()+"%)":"Apply customer custom-order discount");
        searchPopup.setVisible(false);
        phoneField.requestFocusInWindow();
    }
}
