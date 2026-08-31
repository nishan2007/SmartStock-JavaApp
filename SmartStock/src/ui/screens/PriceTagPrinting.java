package ui.screens;

import managers.CompanyCustomizationManager;
import services.LanApiClient;
import services.PriceTagPrintService;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiDebouncer;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
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
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public PriceTagPrinting() {
        setTitle("Price Tag Printing"); setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); setSize(1050, 660); setLocationRelativeTo(null); setLayout(new BorderLayout(12, 12));
        setJMenuBar(AppMenuBar.create(this, "PriceTagPrinting"));
        JPanel header = new JPanel(new BorderLayout(10, 8)); header.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 14));
        JLabel title = new JLabel("Price Tag Printing"); title.setFont(new Font("SansSerif", Font.BOLD, 25)); header.add(title, BorderLayout.NORTH);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); search.add(new JLabel("Template:")); templateBox.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){return super.getListCellRendererComponent(l,v instanceof CompanyCustomizationManager.PriceTagTemplateSettings t ? t.name()+" — "+t.widthInches()+" × "+t.heightInches()+" in" : v,i,s,f);}}); search.add(templateBox); search.add(new JLabel("Find an item:")); search.add(searchField); JButton find = new JButton("Search"); JButton add = new JButton("Add to tag cart"); search.add(find); search.add(add); header.add(search, BorderLayout.SOUTH); add(header, BorderLayout.NORTH);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); cartTable.setRowHeight(28); resultsTable.setRowHeight(28); cartTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(new JTextField()));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, titled("Search results", new JScrollPane(resultsTable)), titled("Tag cart — set the number of stickers to print", new JScrollPane(cartTable))); split.setResizeWeight(.5); split.setBorder(BorderFactory.createEmptyBorder(0,14,0,14)); add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10)); JButton remove = new JButton("Remove selected"); JButton preview = new JButton("Preview selected"); JButton receiptPrint = new JButton("Quick print on receipt printer"); JButton print = new JButton("Print tags"); actions.add(remove); actions.add(preview); actions.add(receiptPrint); actions.add(print); JPanel footer=new JPanel(new BorderLayout());footer.add(loadingState,BorderLayout.CENTER);footer.add(actions,BorderLayout.SOUTH);add(footer, BorderLayout.SOUTH);
        find.addActionListener(e -> search()); searchField.addActionListener(e -> search());UiDebouncer.bind(searchField,300,this::search); add.addActionListener(e -> addSelected()); remove.addActionListener(e -> {int row=cartTable.getSelectedRow();if(row>=0)cart.removeRow(row);}); preview.addActionListener(e -> previewSelected()); receiptPrint.addActionListener(e -> printTagsOnReceiptPrinter()); print.addActionListener(e -> printTags());
        WindowHelper.configurePosWindow(this); loadTemplates();search();
    }
    private JPanel titled(String title, JComponent content) { JPanel p=new JPanel(new BorderLayout()); p.setBorder(BorderFactory.createTitledBorder(title)); p.add(content); return p; }
    private DefaultTableModel model(String... names) { return new DefaultTableModel(names,0) { public boolean isCellEditable(int r,int c){return this==cart && (c==3||c==5);} }; }
    private List<CompanyCustomizationManager.PriceTagTemplateSettings> loadPriceTagTemplates() throws Exception {
            LanApiClient.PriceTagSettings settings = LanApiClient.loadPriceTagSettings();
            return CompanyCustomizationManager.decodePriceTagTemplatesForLan(settings.encodedTemplates(),
                    settings.showCompany(), settings.showSku(), settings.showBarcode(),
                    settings.widthInches(), settings.heightInches());
    }
    private void loadTemplates(){CachedUiLoader.load(this,"price-tags:templates",PriceTagTemplateSnapshot.class,SessionDataCache.REFERENCE_TTL,loadingState,()->new PriceTagTemplateSnapshot(loadPriceTagTemplates()),s->{templateBox.removeAllItems();s.templates().forEach(templateBox::addItem);});}
    private void search() {
        String searchText=searchField.getText().trim();
        CachedUiLoader.load(this,"price-tags.search","price-tags:search:"+searchText,PriceTagSearchSnapshot.class,SessionDataCache.SCREEN_TTL,loadingState,()->new PriceTagSearchSnapshot(LanApiClient.searchPriceTagItems(searchText)),snapshot->{results.setRowCount(0);
            for (LanApiClient.PriceTagCatalogItem item : snapshot.items()) {
                results.addRow(new Object[]{item.itemType(), item.name(), item.size(), item.description(),
                        item.code(), item.price(), item.itemId()});
            }
        });
    }
    private record PriceTagTemplateSnapshot(List<CompanyCustomizationManager.PriceTagTemplateSettings> templates){}
    private record PriceTagSearchSnapshot(List<LanApiClient.PriceTagCatalogItem> items){}
    private void addSelected(){int row=resultsTable.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(this,"Select an item to add.");return;} row=resultsTable.convertRowIndexToModel(row); String name=String.valueOf(results.getValueAt(row,1)), code=String.valueOf(results.getValueAt(row,4)); for(int i=0;i<cart.getRowCount();i++)if(name.equals(cart.getValueAt(i,0))&&code.equals(cart.getValueAt(i,3))){cart.setValueAt(number(cart.getValueAt(i,5))+1,i,5);return;} cart.addRow(new Object[]{name,results.getValueAt(row,2),results.getValueAt(row,3),code,results.getValueAt(row,5),1});}
    private CompanyCustomizationManager.PriceTagTemplateSettings selectedTemplate(){return (CompanyCustomizationManager.PriceTagTemplateSettings)templateBox.getSelectedItem();}
    private void previewSelected(){int row=cartTable.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(this,"Select a tag in the cart to preview.");return;} PriceTagPrintService.preview(this,item(row),selectedTemplate());}
    private void printTags(){try{if(cartTable.isEditing())cartTable.getCellEditor().stopCellEditing();List<PriceTagPrintService.PriceTagItem> items=new ArrayList<>();for(int r=0;r<cart.getRowCount();r++)for(int i=0;i<number(cart.getValueAt(r,5));i++)items.add(item(r));PriceTagPrintService.print(this,items,selectedTemplate());}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Price Tags",JOptionPane.ERROR_MESSAGE);}}
    private void printTagsOnReceiptPrinter(){try{List<PriceTagPrintService.PriceTagItem> items=cartItems();int answer=JOptionPane.showConfirmDialog(this,"Print "+items.size()+" temporary price tag"+(items.size()==1?"":"s")+" on the receipt printer?\n\nEach tag will print and cut separately for taping.","Quick Receipt-Printer Tags",JOptionPane.YES_NO_OPTION);if(answer!=JOptionPane.YES_OPTION)return;String message=PriceTagPrintService.printOnReceiptPrinter(items,selectedTemplate());JOptionPane.showMessageDialog(this,message,"Quick Receipt-Printer Tags",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Quick Receipt-Printer Tags",JOptionPane.ERROR_MESSAGE);}}
    private List<PriceTagPrintService.PriceTagItem> cartItems(){if(cartTable.isEditing())cartTable.getCellEditor().stopCellEditing();List<PriceTagPrintService.PriceTagItem> items=new ArrayList<>();for(int r=0;r<cart.getRowCount();r++)for(int i=0;i<number(cart.getValueAt(r,5));i++)items.add(item(r));if(items.isEmpty())throw new IllegalArgumentException("Add at least one price tag before printing.");return items;}
    private PriceTagPrintService.PriceTagItem item(int r){String code=String.valueOf(cart.getValueAt(r,3));return new PriceTagPrintService.PriceTagItem(String.valueOf(cart.getValueAt(r,0)),String.valueOf(cart.getValueAt(r,1)),String.valueOf(cart.getValueAt(r,2)),code,code,(BigDecimal)cart.getValueAt(r,4));}
    private int number(Object o){try{return Math.max(1,Integer.parseInt(String.valueOf(o)));}catch(Exception e){return 1;}}
}
