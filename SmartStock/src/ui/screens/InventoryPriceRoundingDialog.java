package ui.screens;

import services.InventoryCatalogCache;
import services.LanApiClient;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class InventoryPriceRoundingDialog extends JDialog {
    private static final int COL_SELECTED=0;
    private static final int COL_ID=1;
    private static final int COL_CURRENT=5;
    private static final int COL_NEW=6;

    private final DefaultTableModel model=new DefaultTableModel(
            new Object[]{"Update","Product ID","SKU","Item","Size","Current Price","New Price"},0){
        @Override public Class<?> getColumnClass(int column){return column==COL_SELECTED?Boolean.class:Object.class;}
        @Override public boolean isCellEditable(int row,int column){return column==COL_SELECTED||column==COL_NEW;}
    };
    private final JTable table=new JTable(model);
    private final JLabel status=new JLabel("Scanning item prices...");
    private final JButton updateButton=new JButton("Update Selected Prices");
    private final Runnable afterUpdate;
    private List<LanApiClient.NonRoundedPriceItem> loaded=List.of();

    InventoryPriceRoundingDialog(Window owner,Runnable afterUpdate){
        super(owner,"Review Prices Not Rounded to $20",ModalityType.APPLICATION_MODAL);
        this.afterUpdate=afterUpdate;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(900,560);setLocationRelativeTo(owner);
        JPanel content=new JPanel(new BorderLayout(12,12));content.setBorder(new EmptyBorder(16,16,16,16));
        JLabel help=new JLabel("Only item prices that are not multiples of $20 are shown. Review the proposed price before updating.");
        content.add(help,BorderLayout.NORTH);table.setRowHeight(28);table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(COL_SELECTED).setPreferredWidth(65);
        table.getColumnModel().getColumn(COL_ID).setPreferredWidth(75);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);
        content.add(new JScrollPane(table),BorderLayout.CENTER);
        JButton selectAll=new JButton("Select All"),clear=new JButton("Clear Selection"),close=new JButton("Close");
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(status);actions.add(selectAll);actions.add(clear);actions.add(close);actions.add(updateButton);
        content.add(actions,BorderLayout.SOUTH);setContentPane(content);
        selectAll.addActionListener(e->setAll(true));clear.addActionListener(e->setAll(false));close.addActionListener(e->dispose());
        updateButton.addActionListener(e->updateSelected());updateButton.setEnabled(false);loadItems();
    }

    private void loadItems(){
        updateButton.setEnabled(false);status.setText("Scanning item prices...");
        UiTaskRunner.submit(this,"inventory.price-rounding.load",LanApiClient::loadNonRoundedProductPrices,items->{
            loaded=items==null?List.of():List.copyOf(items);model.setRowCount(0);
            for(var item:loaded)model.addRow(new Object[]{true,item.productId(),item.sku(),item.name(),item.size(),
                    item.currentPrice().toPlainString(),item.suggestedPrice().toPlainString()});
            status.setText(loaded.isEmpty()?"All item prices are already rounded.":loaded.size()+" price(s) need review.");
            updateButton.setEnabled(!loaded.isEmpty());
        },ex->{status.setText("Price scan failed.");JOptionPane.showMessageDialog(this,ex.getMessage(),"Price Review",JOptionPane.ERROR_MESSAGE);});
    }

    private void setAll(boolean selected){for(int row=0;row<model.getRowCount();row++)model.setValueAt(selected,row,COL_SELECTED);}

    private void updateSelected(){
        try{
            List<LanApiClient.PriceRoundingLine> lines=new ArrayList<>();
            for(int row=0;row<model.getRowCount();row++){
                if(!Boolean.TRUE.equals(model.getValueAt(row,COL_SELECTED)))continue;
                BigDecimal replacement=new BigDecimal(String.valueOf(model.getValueAt(row,COL_NEW)).replace("$","").replace(",","").trim());
                if(replacement.signum()<0||replacement.remainder(BigDecimal.valueOf(20)).signum()!=0)
                    throw new IllegalArgumentException("New price for row "+(row+1)+" must be a non-negative multiple of $20.");
                var original=loaded.get(row);
                lines.add(new LanApiClient.PriceRoundingLine(original.productId(),original.currentPrice(),replacement));
            }
            if(lines.isEmpty()){JOptionPane.showMessageDialog(this,"Select at least one item to update.");return;}
            int answer=JOptionPane.showConfirmDialog(this,"Update "+lines.size()+" selected item price(s)?",
                    "Confirm Price Updates",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
            if(answer!=JOptionPane.OK_OPTION)return;
            updateButton.setEnabled(false);status.setText("Updating selected prices...");String key=UUID.randomUUID().toString();
            UiTaskRunner.submit(this,"inventory.price-rounding.update",()->LanApiClient.updateRoundedProductPrices(lines,key),result->{
                SessionDataCache.invalidate("inventory-");InventoryCatalogCache.refreshAfterMutation().exceptionally(failure->null);
                if(afterUpdate!=null)afterUpdate.run();JOptionPane.showMessageDialog(this,result.updatedCount()+" item price(s) updated.");loadItems();
            },ex->{updateButton.setEnabled(true);status.setText("Update failed.");JOptionPane.showMessageDialog(this,ex.getMessage(),"Price Update",JOptionPane.ERROR_MESSAGE);});
        }catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Invalid Price",JOptionPane.WARNING_MESSAGE);}
    }
}
