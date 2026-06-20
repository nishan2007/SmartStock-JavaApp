package ui.screens;

import data.DB;
import managers.CompanyCustomizationManager;
import services.PriceTagPrintService;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** A print cart that deliberately treats normal products, custom items and custom variants alike. */
public class PriceTagPrinting extends JFrame {
    private final JTextField searchField = new JTextField(28);
    private final DefaultTableModel results = model("Type", "Name", "Size", "Description", "SKU / Barcode", "Price", "Item ID");
    private final DefaultTableModel cart = model("Name", "Size", "Description", "SKU / Barcode", "Price", "Quantity");
    private final JTable resultsTable = new JTable(results);
    private final JTable cartTable = new JTable(cart);
    private final JComboBox<CompanyCustomizationManager.PriceTagTemplateSettings> templateBox = new JComboBox<>();

    public PriceTagPrinting() {
        setTitle("Price Tag Printing"); setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); setSize(1050, 660); setLocationRelativeTo(null); setLayout(new BorderLayout(12, 12));
        setJMenuBar(AppMenuBar.create(this, "PriceTagPrinting"));
        JPanel header = new JPanel(new BorderLayout(10, 8)); header.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 14));
        JLabel title = new JLabel("Price Tag Printing"); title.setFont(new Font("SansSerif", Font.BOLD, 25)); header.add(title, BorderLayout.NORTH);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); search.add(new JLabel("Template:")); for (CompanyCustomizationManager.PriceTagTemplateSettings s : CompanyCustomizationManager.loadPriceTagTemplateSettings()) templateBox.addItem(s); templateBox.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){return super.getListCellRendererComponent(l,v instanceof CompanyCustomizationManager.PriceTagTemplateSettings t ? t.name()+" — "+t.widthInches()+" × "+t.heightInches()+" in" : v,i,s,f);}}); search.add(templateBox); search.add(new JLabel("Find an item:")); search.add(searchField); JButton find = new JButton("Search"); JButton add = new JButton("Add to tag cart"); search.add(find); search.add(add); header.add(search, BorderLayout.SOUTH); add(header, BorderLayout.NORTH);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); cartTable.setRowHeight(28); resultsTable.setRowHeight(28); cartTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(new JTextField()));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, titled("Search results", new JScrollPane(resultsTable)), titled("Tag cart — set the number of stickers to print", new JScrollPane(cartTable))); split.setResizeWeight(.5); split.setBorder(BorderFactory.createEmptyBorder(0,14,0,14)); add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10)); JButton remove = new JButton("Remove selected"); JButton preview = new JButton("Preview selected"); JButton print = new JButton("Print tags"); actions.add(remove); actions.add(preview); actions.add(print); add(actions, BorderLayout.SOUTH);
        find.addActionListener(e -> search()); searchField.addActionListener(e -> search()); add.addActionListener(e -> addSelected()); remove.addActionListener(e -> {int row=cartTable.getSelectedRow();if(row>=0)cart.removeRow(row);}); preview.addActionListener(e -> previewSelected()); print.addActionListener(e -> printTags());
        WindowHelper.configurePosWindow(this); search();
    }
    private JPanel titled(String title, JComponent content) { JPanel p=new JPanel(new BorderLayout()); p.setBorder(BorderFactory.createTitledBorder(title)); p.add(content); return p; }
    private DefaultTableModel model(String... names) { return new DefaultTableModel(names,0) { public boolean isCellEditable(int r,int c){return this==cart && c==3;} }; }
    private void search() {
        results.setRowCount(0); String q="%"+searchField.getText().trim()+"%";
        String sql="""
            SELECT 'Product' type, p.name, COALESCE(p.size,''), COALESCE(p.description,''), COALESCE(NULLIF(p.sku,''), NULLIF(p.barcode,''), 'PRODUCT-' || p.product_id) code, p.price, p.product_id id FROM products p WHERE p.name ILIKE ? OR COALESCE(p.sku,'') ILIKE ? OR COALESCE(p.barcode,'') ILIKE ?
            UNION ALL SELECT 'Custom item', coi.item_name, '', COALESCE(coi.description,''), COALESCE(NULLIF(coi.sku,''),NULLIF(coi.barcode,''),'CUSTOM-' || coi.custom_item_id), coi.fixed_price, coi.custom_item_id FROM custom_order_items coi WHERE coi.is_active=TRUE AND COALESCE(coi.has_variants,FALSE)=FALSE AND (coi.item_name ILIKE ? OR COALESCE(coi.sku,'') ILIKE ? OR COALESCE(coi.barcode,'') ILIKE ?)
            UNION ALL SELECT 'Custom variant', coi.item_name || ' - ' || coiv.variant_name, coiv.variant_name, COALESCE(coi.description,''), COALESCE(NULLIF(coiv.sku,''),NULLIF(coiv.barcode,''),'CUSTOM-' || coi.custom_item_id || '-' || coiv.custom_variant_id), COALESCE(coiv.fixed_price, coi.fixed_price), coiv.custom_variant_id FROM custom_order_item_variants coiv JOIN custom_order_items coi ON coi.custom_item_id=coiv.custom_item_id WHERE coi.is_active=TRUE AND coiv.is_active=TRUE AND (coi.item_name ILIKE ? OR coiv.variant_name ILIKE ? OR COALESCE(coiv.sku,'') ILIKE ? OR COALESCE(coiv.barcode,'') ILIKE ?)
            ORDER BY 2 LIMIT 300""";
        try(Connection c=DB.getConnection(); PreparedStatement ps=c.prepareStatement(sql)) { for(int i=1;i<=10;i++)ps.setString(i,q); try(ResultSet rs=ps.executeQuery()){while(rs.next())results.addRow(new Object[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBigDecimal(6),rs.getLong(7)});}} catch(Exception ex){JOptionPane.showMessageDialog(this,"Could not load items: "+ex.getMessage(),"Price Tags",JOptionPane.ERROR_MESSAGE);}
    }
    private void addSelected(){int row=resultsTable.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(this,"Select an item to add.");return;} row=resultsTable.convertRowIndexToModel(row); String name=String.valueOf(results.getValueAt(row,1)), code=String.valueOf(results.getValueAt(row,4)); for(int i=0;i<cart.getRowCount();i++)if(name.equals(cart.getValueAt(i,0))&&code.equals(cart.getValueAt(i,3))){cart.setValueAt(number(cart.getValueAt(i,5))+1,i,5);return;} cart.addRow(new Object[]{name,results.getValueAt(row,2),results.getValueAt(row,3),code,results.getValueAt(row,5),1});}
    private CompanyCustomizationManager.PriceTagTemplateSettings selectedTemplate(){return (CompanyCustomizationManager.PriceTagTemplateSettings)templateBox.getSelectedItem();}
    private void previewSelected(){int row=cartTable.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(this,"Select a tag in the cart to preview.");return;} PriceTagPrintService.preview(this,item(row),selectedTemplate());}
    private void printTags(){try{if(cartTable.isEditing())cartTable.getCellEditor().stopCellEditing();List<PriceTagPrintService.PriceTagItem> items=new ArrayList<>();for(int r=0;r<cart.getRowCount();r++)for(int i=0;i<number(cart.getValueAt(r,5));i++)items.add(item(r));PriceTagPrintService.print(this,items,selectedTemplate());}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Price Tags",JOptionPane.ERROR_MESSAGE);}}
    private PriceTagPrintService.PriceTagItem item(int r){String code=String.valueOf(cart.getValueAt(r,3));return new PriceTagPrintService.PriceTagItem(String.valueOf(cart.getValueAt(r,0)),String.valueOf(cart.getValueAt(r,1)),String.valueOf(cart.getValueAt(r,2)),code,code,(BigDecimal)cart.getValueAt(r,4));}
    private int number(Object o){try{return Math.max(1,Integer.parseInt(String.valueOf(o)));}catch(Exception e){return 1;}}
}
