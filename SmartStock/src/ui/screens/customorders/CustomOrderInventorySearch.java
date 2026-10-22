package ui.screens.customorders;

import services.LanApiClient;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/** Occasional regular-stock lookup, separate from the custom catalog search. */
final class CustomOrderInventorySearch {
    static void show(Window owner, Consumer<LanApiClient.CatalogProduct> selected) {
        JDialog dialog=new JDialog(owner,"Add from Inventory",Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content=new JPanel(new BorderLayout(8,8));
        content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JTextField query=new JTextField(28);
        JButton search=new JButton("Search"),add=new JButton("Use Item"),cancel=new JButton("Cancel");
        JPanel top=new JPanel(new BorderLayout(8,8));top.add(new JLabel("Name, SKU or barcode:"),BorderLayout.WEST);top.add(query);top.add(search,BorderLayout.EAST);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Item","Size","Colour","SKU","Selling Price","Available"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
        JTable table=new JTable(model);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);
        JLabel status=new JLabel("Search inventory, then add printing or engraving to the selected item.");
        class State {
            List<LanApiClient.CatalogProduct> products=List.of();
            void search() {
                String text=query.getText().trim();if(text.isEmpty()){status.setText("Enter a name, SKU or barcode.");return;}
                search.setEnabled(false);add.setEnabled(false);status.setText("Searching inventory…");
                UiTaskRunner.submit(dialog,"custom-orders.inventory-search",()->LanApiClient.searchCatalog(text,"INVENTORY"),results->{
                    products=results.stream().filter(p->p.productId()>0).toList();model.setRowCount(0);
                    for(var p:products)model.addRow(new Object[]{p.name(),p.size(),p.color(),p.sku(),p.price(),p.quantityOnHand()});
                    if(!products.isEmpty())table.setRowSelectionInterval(0,0);
                    search.setEnabled(true);add.setEnabled(!products.isEmpty());status.setText(products.isEmpty()?"No inventory items found.":"Selling price plus your print add-ons will be charged.");
                },failure->{search.setEnabled(true);status.setText("Search failed: "+failure.getMessage());});
            }
            void select(){int row=table.getSelectedRow();if(row<0)return;var p=products.get(table.convertRowIndexToModel(row));if(p.quantityOnHand()<1){status.setText("This item has no stock available.");return;}selected.accept(p);dialog.dispose();}
        }
        State state=new State();search.addActionListener(e->state.search());query.addActionListener(e->state.search());add.addActionListener(e->state.select());cancel.addActionListener(e->dialog.dispose());add.setEnabled(false);
        JPanel bottom=new JPanel(new BorderLayout(8,8)),buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(cancel);buttons.add(add);bottom.add(status);bottom.add(buttons,BorderLayout.SOUTH);
        content.add(top,BorderLayout.NORTH);content.add(new JScrollPane(table));content.add(bottom,BorderLayout.SOUTH);
        dialog.setContentPane(content);dialog.setSize(820,440);dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }
}
