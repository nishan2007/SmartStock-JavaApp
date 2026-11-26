package ui.screens;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import services.LanApiClient;
import ui.helpers.UiDebouncer;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Server-backed customer lookup, independent of the initial customer dropdown limit. */
final class SalesTaskCustomerPicker extends JPanel {
    record Customer(int id,String name,String phone,String account,String email) { }
    private Customer selected;
    private final JTextField display=new JTextField("Walk-in customer");
    private final Consumer<Customer> changed;
    SalesTaskCustomerPicker(Customer customer,Consumer<Customer> changed) {
        super(new BorderLayout(6,0));this.changed=changed;display.setEditable(false);display.setColumns(17);
        JButton lookup=new JButton("Lookup…"),clear=new JButton("Clear");
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));buttons.add(lookup);buttons.add(clear);
        add(display,BorderLayout.CENTER);add(buttons,BorderLayout.EAST);
        setCustomer(customer);lookup.addActionListener(e->lookup());clear.addActionListener(e->setCustomer(null));
    }
    Customer selected(){return selected;}
    private void setCustomer(Customer customer){selected=customer;display.setText(customer==null?"Walk-in customer":customer.name()+(customer.account().isBlank()?"":" — "+customer.account()));changed.accept(customer);}
    private void lookup() {
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Customer Lookup",Dialog.ModalityType.APPLICATION_MODAL);
        JTextField search=new JTextField(30);search.setToolTipText("Search name, phone, account number or email");
        DefaultTableModel model=new DefaultTableModel(new String[]{"Name","Phone","Account","Email"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable table=new JTable(model);table.setRowHeight(30);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);
        List<Customer> matches=new ArrayList<>();JLabel status=new JLabel("Search by name, phone, account number or email.");
        JButton find=new JButton("Search"),choose=new JButton("Use Customer"),cancel=new JButton("Cancel");choose.setEnabled(false);
        Runnable chooseCustomer=()->{int row=table.getSelectedRow();if(row<0)return;setCustomer(matches.get(table.convertRowIndexToModel(row)));dialog.dispose();};
        table.getSelectionModel().addListSelectionListener(e->choose.setEnabled(table.getSelectedRow()>=0));
        table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)chooseCustomer.run();}});
        Runnable load=()->{
            String query=search.getText().trim();status.setText("Searching…");choose.setEnabled(false);
            UiTaskRunner.submit(dialog,"sales-task.customer-search",()->{
                JsonObject request=new JsonObject();request.addProperty("action","CUSTOMERS");request.addProperty("search",query);
                List<Customer> result=new ArrayList<>();
                for(JsonElement element:LanApiClient.salesTasksRead(request).getAsJsonArray("customers")){
                    JsonObject c=element.getAsJsonObject();result.add(new Customer(c.get("customer_id").getAsInt(),text(c,"name"),text(c,"phone"),text(c,"account_number"),text(c,"email")));
                }return result;
            },result->{
                if(!query.equals(search.getText().trim()))return;
                model.setRowCount(0);matches.clear();matches.addAll(result);
                for(Customer c:result)model.addRow(new Object[]{c.name(),c.phone(),c.account(),c.email()});
                status.setText(result.size()==100?"Showing 100 matches — refine your search.":result.size()+" matching customers.");
            },error->{if(query.equals(search.getText().trim()))status.setText("Lookup failed: "+error.getMessage());});
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){private void clear(){model.setRowCount(0);matches.clear();choose.setEnabled(false);}public void insertUpdate(javax.swing.event.DocumentEvent e){clear();}public void removeUpdate(javax.swing.event.DocumentEvent e){clear();}public void changedUpdate(javax.swing.event.DocumentEvent e){clear();}});
        UiDebouncer.bind(search,250,load);search.addActionListener(e->load.run());find.addActionListener(e->load.run());
        choose.addActionListener(e->chooseCustomer.run());cancel.addActionListener(e->dialog.dispose());dialog.getRootPane().setDefaultButton(choose);
        JPanel heading=new JPanel(new BorderLayout(8,4));heading.add(new JLabel("Name / phone / account / email"),BorderLayout.NORTH);heading.add(search,BorderLayout.CENTER);heading.add(find,BorderLayout.EAST);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(choose);buttons.add(cancel);
        JPanel footer=new JPanel(new BorderLayout());footer.add(status,BorderLayout.NORTH);footer.add(buttons,BorderLayout.SOUTH);
        JPanel content=new JPanel(new BorderLayout(8,8));content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));content.add(heading,BorderLayout.NORTH);content.add(new JScrollPane(table),BorderLayout.CENTER);content.add(footer,BorderLayout.SOUTH);
        dialog.setContentPane(content);dialog.setSize(760,460);dialog.setLocationRelativeTo(this);
        dialog.addWindowListener(new WindowAdapter(){@Override public void windowOpened(WindowEvent e){search.requestFocusInWindow();load.run();}});dialog.setVisible(true);
    }
    private static String text(JsonObject c,String key){return c.has(key)&&!c.get(key).isJsonNull()?c.get(key).getAsString():"";}
}
