package ui.screens;

import com.google.gson.*;
import services.*;
import ui.helpers.SessionDataCache;
import ui.design.DeckersSwing;
import ui.helpers.ProductImageHelper;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

/** Review existing records or create a batch of variants using the same server transaction. */
public final class ProductVariantsDialog extends JDialog {
    private final JComboBox<String> department=new JComboBox<>();
    private List<LanApiClient.NamedId> departments=List.of();
    private final JTextField itemType=new JTextField(),brand=new JTextField(),shelf=new JTextField();
    private final JTextField name=new JTextField(25);
    private final JTextField barcode=new JTextField(25);
    private final JTextArea additionalBarcodes=new JTextArea(3,25);
    private final JTextField definitions=new JTextField("Color=Blue,Red; Size; Flavor",40);
    private final JTextField defaultCost=new JTextField("0",7), defaultPrice=new JTextField("0",7);
    private final DefaultTableModel model=new DefaultTableModel() {
        @Override public boolean isCellEditable(int row,int column) { return column>=10 || (getValueAt(row,0)==null && column>=2); }
    };
    private final JTable table=new JTable(model);
    private List<String> optionNames=new ArrayList<>(List.of("Color","Size","Flavor"));
    private LanApiClient.ProductGroup group;
    private final Runnable changed;
    private final JButton save=new JButton("Save variants");
    private String pendingKey,pendingFingerprint;
    private JsonObject pendingRequest;

    public ProductVariantsDialog(Window owner,List<Integer> selectedIds,Runnable changed) {
        super(owner,"Product variants",ModalityType.APPLICATION_MODAL);this.changed=changed;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setLayout(new BorderLayout(10,10));
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));
        fields.add(new JLabel("Department for new variants"));fields.add(department);
        fields.add(new JLabel("Item type for new variants"));fields.add(itemType);
        fields.add(new JLabel("Brand for new variants"));fields.add(brand);
        fields.add(new JLabel("Shelf for new variants"));fields.add(shelf);
        fields.add(new JLabel("Main product name"));fields.add(name);
        fields.add(new JLabel("Main primary barcode"));fields.add(barcode);
        fields.add(new JLabel("Main additional barcodes (one per line)"));fields.add(new JScrollPane(additionalBarcodes));
        fields.add(new JLabel("Separators (fill at least one per variant)"));fields.add(definitions);
        fields.add(new JLabel("Default cost for new variants"));fields.add(defaultCost);
        fields.add(new JLabel("Default selling price for new variants"));fields.add(defaultPrice);
        // Keep the multiline barcode field from making every form row equally tall.
        Component[] formFields=fields.getComponents();fields.removeAll();fields.setLayout(new GridBagLayout());
        for(int i=0;i<formFields.length;i++) {
            GridBagConstraints cell=new GridBagConstraints();cell.gridx=i%2;cell.gridy=i/2;
            cell.anchor=GridBagConstraints.WEST;cell.fill=GridBagConstraints.HORIZONTAL;
            cell.weightx=i%2==1?1:0;cell.insets=new Insets(3,0,3,i%2==0?8:0);
            fields.add(formFields[i],cell);
        }
        JPanel top=new JPanel(new BorderLayout(5,5));top.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));top.add(fields);
        top.add(new JLabel("Existing items keep their identifiers, prices, pictures and stock. Remove a row to detach it."),BorderLayout.SOUTH);
        add(top,BorderLayout.NORTH);rebuildColumns();DeckersSwing.styleTable(table);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        ui.helpers.TableImageHoverPreview.install(this,table,8,ui.design.DeckersPalette.PURPLE);
        add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(0,3,6,6));actions.setBorder(BorderFactory.createEmptyBorder(6,10,10,10));
        JButton apply=new JButton("Apply option columns"),generate=new JButton("Generate combinations"),attach=new JButton("Attach existing item"),remove=new JButton("Remove / detach row"),picture=new JButton("Choose picture"),cancel=new JButton("Cancel");
        apply.addActionListener(e->applyOptions());generate.addActionListener(e->generate());attach.addActionListener(e->attach());
        remove.addActionListener(e->{stopEditing();int[] rows=table.getSelectedRows();for(int i=rows.length-1;i>=0;i--)model.removeRow(rows[i]);});
        picture.addActionListener(e->choosePicture());save.addActionListener(e->save());cancel.addActionListener(e->dispose());
        for(JButton button:List.of(apply,generate,attach,remove,picture,cancel,save))actions.add(button);
        add(actions,BorderLayout.SOUTH);Rectangle screen=getGraphicsConfiguration().getBounds();setSize(Math.min(1250,screen.width-60),Math.min(720,screen.height-80));setLocationRelativeTo(owner);
        load(selectedIds);
    }

    private void load(List<Integer> ids) {
        save.setEnabled(false);
        new SwingWorker<List<LanApiClient.EditableProduct>,Void>() {
            private LanApiClient.ProductGroup found;
            @Override protected List<LanApiClient.EditableProduct> doInBackground() throws Exception {
                departments=LanApiClient.loadInventoryLookups(null).departments();
                List<Integer> wanted=new ArrayList<>(ids);
                for(var candidate:LanApiClient.loadProductGroups())if(candidate.members().stream().anyMatch(m->ids.contains(m.productId()))) {
                    if(found!=null&&!found.groupId().equals(candidate.groupId()))throw new IllegalArgumentException("Select items from only one group at a time.");found=candidate;
                }
                if(found!=null)for(var member:found.members())if(!wanted.contains(member.productId()))wanted.add(member.productId());
                return wanted.isEmpty()?List.of():LanApiClient.loadVariantSetupItems(wanted);
            }
            @Override protected void done(){try {
                List<LanApiClient.EditableProduct> products=get();group=found;
                for(var d:departments)department.addItem(d.name());
                if(!products.isEmpty()){var first=products.get(0);department.setSelectedItem(first.categoryName());itemType.setText(first.itemTypeName());brand.setText(first.brandName());shelf.setText(first.shelfName());defaultCost.setText(first.costPrice().toPlainString());defaultPrice.setText(first.price().toPlainString());}
                if(group!=null){name.setText(group.name());barcode.setText(group.barcode());additionalBarcodes.setText(String.join("\n",group.additionalBarcodes()));optionNames=new ArrayList<>(group.optionNames());definitions.setText(String.join("; ",optionNames));rebuildColumns();}
                else if(!products.isEmpty())name.setText(products.get(0).name());
                for(var product:products){Map<String,String> options=new LinkedHashMap<>();if(group!=null)for(var member:group.members())if(member.productId()==product.productId())options.putAll(member.options());addExisting(product,options);}
                save.setEnabled(true);
            } catch(Exception ex){error(ex);dispose();}}
        }.execute();
    }

    private void rebuildColumns(){
        List<String> columns=new ArrayList<>(List.of("Product ID","Existing item","SKU","Barcode","Cost","Price","Stock","Reorder","Picture URL / file","Description"));columns.addAll(optionNames);model.setColumnIdentifiers(columns.toArray());
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==1||i==8?190:110);
    }
    private void addExisting(LanApiClient.EditableProduct p,Map<String,String> values){
        List<Object> row=new ArrayList<>(Arrays.asList(p.productId(),p.name(),p.sku(),p.barcode(),p.costPrice(),p.price(),p.quantity(),p.reorderLevel(),p.imageUrl(),p.description()));
        for(String option:optionNames)row.add(values.getOrDefault(option,option.equalsIgnoreCase("Size")?p.size():option.equalsIgnoreCase("Color")?p.color():option.equalsIgnoreCase("Flavor")?p.flavor():""));model.addRow(row.toArray());
    }
    private LinkedHashMap<String,List<String>> parseDefinitions(){
        LinkedHashMap<String,List<String>> result=new LinkedHashMap<>();Set<String> seen=new HashSet<>();
        for(String expression:definitions.getText().split(";")) {
            String[] pair=expression.trim().split("=",2);String key=pair[0].trim();
            if(key.isBlank()||!seen.add(key.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Provide unique option names.");
            List<String> values=pair.length<2?List.of():Arrays.stream(pair[1].split(",")).map(String::trim).filter(v->!v.isBlank()).distinct().toList();result.put(key,values);
        }
        if(result.size()>6)throw new IllegalArgumentException("Use at most six options.");return result;
    }
    private void applyOptions(){try{stopEditing();var parsed=parseDefinitions();List<Map<String,String>> values=new ArrayList<>();for(int r=0;r<model.getRowCount();r++){Map<String,String> row=new HashMap<>();for(int i=0;i<optionNames.size();i++)row.put(optionNames.get(i),text(r,10+i));values.add(row);}optionNames=new ArrayList<>(parsed.keySet());rebuildColumns();for(int r=0;r<model.getRowCount();r++)for(int i=0;i<optionNames.size();i++)model.setValueAt(values.get(r).getOrDefault(optionNames.get(i),""),r,10+i);}catch(Exception ex){error(ex);}}
    private void generate(){try{
        applyOptions();var parsed=parseDefinitions();List<List<String>> combinations=new ArrayList<>();combinations.add(List.of());
        for(var entry:parsed.entrySet()){List<String> values=entry.getValue();if(values.isEmpty()){if(isStandardSeparator(entry.getKey()))values=List.of("");else throw new IllegalArgumentException("Supply values after = for each custom option before generating.");}List<List<String>> next=new ArrayList<>();for(var prefix:combinations)for(String value:values){var row=new ArrayList<>(prefix);row.add(value);next.add(row);if(next.size()+model.getRowCount()>500)throw new IllegalArgumentException("Limit the group to 500 variants.");}combinations=next;}
        for(var values:combinations){List<Object> row=new ArrayList<>(Arrays.asList(null,"New variant","","",new BigDecimal(defaultCost.getText().trim()),new BigDecimal(defaultPrice.getText().trim()),0,0,"",""));row.addAll(values);model.addRow(row.toArray());}
    }catch(Exception ex){error(ex);}}
    private void attach(){String query=JOptionPane.showInputDialog(this,"Search existing items by name, SKU or barcode:");if(query==null||query.isBlank())return;
        new SwingWorker<List<LanApiClient.EditableProduct>,Void>(){
            protected List<LanApiClient.EditableProduct> doInBackground()throws Exception{return LanApiClient.searchEditableProducts(query);}
            protected void done(){try{var products=get();if(products.isEmpty())throw new IllegalArgumentException("No matching items.");String[] labels=products.stream().map(p->p.name()+" | "+p.size()+" | "+p.sku()+" (#"+p.productId()+")").toArray(String[]::new);String selected=(String)JOptionPane.showInputDialog(ProductVariantsDialog.this,"Choose an item","Attach existing item",JOptionPane.PLAIN_MESSAGE,null,labels,labels[0]);if(selected!=null){int index=Arrays.asList(labels).indexOf(selected);var p=products.get(index);for(int r=0;r<model.getRowCount();r++)if(Objects.equals(model.getValueAt(r,0),p.productId()))throw new IllegalArgumentException("This item is already in the list.");addExisting(p,Map.of());}}catch(Exception ex){error(ex);}}
        }.execute();
    }
    private void choosePicture(){int row=table.getSelectedRow();if(row<0)return;if(model.getValueAt(row,0)!=null){JOptionPane.showMessageDialog(this,"Use Edit Item to change an existing item's picture.");return;}JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)model.setValueAt(chooser.getSelectedFile().getAbsolutePath(),row,8);}
    private String text(int row,int column){Object value=model.getValueAt(row,column);return value==null?"":value.toString().trim();}
    private void stopEditing(){if(table.isEditing())table.getCellEditor().stopCellEditing();}
    private void save(){try{
        stopEditing();JsonObject request=new JsonObject();if(group!=null){request.addProperty("groupId",group.groupId());request.addProperty("expectedRevision",group.revision());}request.addProperty("name",name.getText().trim());request.add("optionNames",new Gson().toJsonTree(optionNames));JsonArray members=new JsonArray();
        request.addProperty("barcode",barcode.getText().trim());
        request.add("additionalBarcodes",new Gson().toJsonTree(additionalBarcodes.getText().lines().map(String::trim).filter(value->!value.isEmpty()).toList()));
        for(int r=0;r<model.getRowCount();r++){JsonObject member=new JsonObject(),options=new JsonObject();boolean hasSeparator=false;for(int i=0;i<optionNames.size();i++){String option=optionNames.get(i),value=text(r,10+i);if(value.isBlank()&&!isStandardSeparator(option))throw new IllegalArgumentException("Fill every custom option value before saving.");if(isStandardSeparator(option)&&!value.isBlank())hasSeparator=true;options.addProperty(option,value);}if(optionNames.stream().anyMatch(ProductVariantsDialog::isStandardSeparator)&&!hasSeparator)throw new IllegalArgumentException("Each variant needs at least one Size, Color, or Flavor value.");member.add("options",options);
            if(model.getValueAt(r,0)!=null)member.addProperty("productId",((Number)model.getValueAt(r,0)).intValue());
            else {JsonObject product=new JsonObject();if(department.getSelectedIndex()<0)throw new IllegalArgumentException("Choose a department for new variants.");product.addProperty("categoryId",departments.get(department.getSelectedIndex()).id());product.addProperty("itemTypeName",itemType.getText().trim());product.addProperty("brandName",brand.getText().trim());product.addProperty("shelfName",shelf.getText().trim());product.addProperty("sku",text(r,2));product.addProperty("barcode",text(r,3));product.addProperty("costPrice",new BigDecimal(text(r,4)));product.addProperty("price",new BigDecimal(text(r,5)));product.addProperty("quantity",Integer.parseInt(text(r,6)));product.addProperty("reorderLevel",Integer.parseInt(text(r,7)));product.addProperty("imageUrl",text(r,8));product.addProperty("description",text(r,9));product.add("additionalBarcodes",new JsonArray());member.add("product",product);}members.add(member);
        }request.add("members",members);if(members.isEmpty()&&group==null)throw new IllegalArgumentException("Keep at least one variant in the group.");save.setEnabled(false);
        String fingerprint=request.toString();if(!fingerprint.equals(pendingFingerprint)){pendingFingerprint=fingerprint;pendingKey=UUID.randomUUID().toString();pendingRequest=request;}String key=pendingKey;JsonObject payload=pendingRequest;
        new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{for(JsonElement element:payload.getAsJsonArray("members")){JsonObject member=element.getAsJsonObject();if(member.has("product")){JsonObject product=member.getAsJsonObject("product");String uploaded=ProductImageHelper.uploadLocalImageIfNeeded(product.get("imageUrl").getAsString(),new ProductImageHelper.ProductImageNaming(request.get("name").getAsString(),"","","",""));product.addProperty("imageUrl",uploaded);}}LanApiClient.saveProductGroup(payload,key);SessionDataCache.invalidate("inventory-");InventoryCatalogCache.refreshAfterMutation().exceptionally(failure->null);return null;}
            protected void done(){try{get();if(changed!=null)changed.run();dispose();}catch(Exception ex){save.setEnabled(true);error(ex);}}
        }.execute();
    }catch(Exception ex){error(ex);}}
    private void error(Exception ex){Throwable cause=ex;while(cause.getCause()!=null)cause=cause.getCause();JOptionPane.showMessageDialog(this,cause.getMessage(),"Product variants",JOptionPane.ERROR_MESSAGE);}
    private static boolean isStandardSeparator(String name){return name!=null&&(name.equalsIgnoreCase("Size")||name.equalsIgnoreCase("Color")||name.equalsIgnoreCase("Flavor"));}
}
