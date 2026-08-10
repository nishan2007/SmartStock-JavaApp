package ui.screens.customorders;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import services.CustomOrderSkuGenerator;
import services.LanApiClient;
import services.LanJson;
import services.LanCustomOrderCatalogAdminService;
import ui.helpers.BarcodeGenerationHelper;
import ui.components.AppMenuBar;
import ui.components.DepartmentSelector;
import ui.components.ItemClassificationSelector;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.ProductImageHelper;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Custom-order catalog editor backed entirely by the authenticated store service. */
public final class CustomOrderItems extends JFrame {
    private static final Gson GSON=LanJson.create();
    private LanCustomOrderCatalogAdminService.State state;
    private final LoadingStatePanel loadingState=new LoadingStatePanel();

    private final DefaultTableModel itemModel=model("ID","Item","SKU","Department","Item Type","Brand","Pricing","Price","Variants","Qty","Reorder","Active");
    private final JTable itemTable=new JTable(itemModel);private final TableRowSorter<DefaultTableModel>itemSorter=new TableRowSorter<>(itemModel);private final JTextField search=new JTextField();
    private final JTextField name=new JTextField(),sku=new JTextField(),barcode=new JTextField(),price=new JTextField(),quantity=new JTextField("0"),reorder=new JTextField("0"),maxWidth=new JTextField(),maxLength=new JTextField();
    private final JTextArea description=new JTextArea(3,24),extraBarcodes=new JTextArea(3,24);private final ProductImageHelper.ImageSelector image=ProductImageHelper.createImageSelector(this);
    private final DepartmentSelector department=new DepartmentSelector();private final ItemClassificationSelector classification=new ItemClassificationSelector(department);
    private final JComboBox<String>productType=new JComboBox<>(new String[]{"INVENTORY","SERVICE","NON_INVENTORY"}),pricingType=new JComboBox<>(new String[]{"VARIABLE","FIXED","AREA"}),areaUnit=new JComboBox<>(new String[]{"SQ_FT","SQ_IN","SQ_YD","SQ_M","SQ_CM"}),dimensionUnit=new JComboBox<>(new String[]{"IN","FT","YD","M","CM"});
    private final JCheckBox variants=new JCheckBox("Track sizes / variants"),active=new JCheckBox("Active",true);private Long itemId;

    private final DefaultTableModel materialModel=model("ID","Material","Active","Description"),presetModel=model("ID","Name","Pricing","Price","Active"),placementModel=model("ID","Placement","Order","Active");
    private final JTable materialTable=new JTable(materialModel),presetTable=new JTable(presetModel),placementTable=new JTable(placementModel);
    private final JTextField materialName=new JTextField(),presetName=new JTextField(),presetPrice=new JTextField(),placementName=new JTextField(),placementOrder=new JTextField("0");
    private final JTextArea materialDescription=new JTextArea(3,20);private final JComboBox<String>presetMode=new JComboBox<>(new String[]{"FIXED_PRESET","PER_LINE"});
    private final JCheckBox materialActive=new JCheckBox("Active",true),presetActive=new JCheckBox("Active",true),placementActive=new JCheckBox("Active",true);private Long materialId,presetId,placementId;

    public CustomOrderItems(){setTitle("Custom Order Items");setSize(1220,760);setDefaultCloseOperation(DISPOSE_ON_CLOSE);setJMenuBar(AppMenuBar.create(this,"CustomOrderItems"));JTabbedPane tabs=new JTabbedPane();tabs.addTab("Items",itemsTab());tabs.addTab("Print Materials",materialsTab());tabs.addTab("Design Placements",placementsTab());add(tabs,BorderLayout.CENTER);add(loadingState,BorderLayout.SOUTH);wire();addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowOpened(java.awt.event.WindowEvent event){WindowHelper.configurePosWindow(CustomOrderItems.this);refresh();}});}

    private JPanel itemsTab(){JPanel p=new JPanel(new BorderLayout(10,10));p.setBorder(new EmptyBorder(12,12,12,12));JPanel top=new JPanel(new BorderLayout(8,0));top.add(new JLabel("Search:"),BorderLayout.WEST);top.add(search);JButton refresh=new JButton("Refresh");refresh.addActionListener(e->refresh());top.add(refresh,BorderLayout.EAST);itemTable.setRowSorter(itemSorter);itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);itemTable.setRowHeight(26);JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(itemTable),itemEditor());split.setResizeWeight(.55);split.setDividerLocation(650);p.add(top,BorderLayout.NORTH);p.add(split);return p;}
    private JScrollPane itemEditor(){JPanel f=form();int r=0;row(f,r++,"Item name",name);row(f,r++,"Generated SKU",sku);sku.setEditable(false);row(f,r++,"Primary barcode",BarcodeGenerationHelper.field(this,barcode));JScrollPane extraBarcodeScroll=new JScrollPane(extraBarcodes);row(f,r++,"More barcodes",BarcodeGenerationHelper.area(this,extraBarcodes,extraBarcodeScroll));row(f,r++,"Description",new JScrollPane(description));row(f,r++,"Department",department);row(f,r++,"Item type",classification.itemTypeComponent());row(f,r++,"Brand",classification.brandComponent());row(f,r++,"Image",image);row(f,r++,"Product type",productType);row(f,r++,"Pricing",pricingType);row(f,r++,"Price / area rate",price);row(f,r++,"Area unit",areaUnit);row(f,r++,"Dimension unit",dimensionUnit);row(f,r++,"Maximum width",maxWidth);row(f,r++,"Maximum length",maxLength);row(f,r++,"Quantity",quantity);row(f,r++,"Reorder level",reorder);row(f,r++,"",variants);row(f,r++,"",active);JPanel buttons=new JPanel(new GridLayout(0,2,6,6));JButton save=new JButton("Save Item"),clear=new JButton("Clear"),deactivate=new JButton("Deactivate"),manage=new JButton("Sizes / Variants");save.addActionListener(e->saveItem());clear.addActionListener(e->clearItem());deactivate.addActionListener(e->deactivate("DEACTIVATE_ITEM","itemId",itemId,"item"));manage.addActionListener(e->variantsDialog());buttons.add(save);buttons.add(clear);buttons.add(deactivate);buttons.add(manage);row(f,r,"",buttons);JScrollPane sp=new JScrollPane(f);sp.setPreferredSize(new Dimension(470,0));return sp;}
    private JPanel materialsTab(){JPanel p=new JPanel(new BorderLayout(10,10));p.setBorder(new EmptyBorder(12,12,12,12));JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,materialPanel(),presetPanel());split.setResizeWeight(.5);p.add(split);return p;}
    private JPanel materialPanel(){JPanel p=new JPanel(new BorderLayout(8,8));p.setBorder(BorderFactory.createTitledBorder("Print Materials"));p.add(new JScrollPane(materialTable));JPanel f=form();row(f,0,"Name",materialName);row(f,1,"Description",new JScrollPane(materialDescription));row(f,2,"",materialActive);JPanel b=buttons(()->saveMaterial(),()->clearMaterial(),()->deactivate("DEACTIVATE_MATERIAL","materialId",materialId,"material"));row(f,3,"",b);p.add(f,BorderLayout.SOUTH);return p;}
    private JPanel presetPanel(){JPanel p=new JPanel(new BorderLayout(8,8));p.setBorder(BorderFactory.createTitledBorder("Sizes / Pricing Presets"));p.add(new JScrollPane(presetTable));JPanel f=form();row(f,0,"Name",presetName);row(f,1,"Pricing mode",presetMode);row(f,2,"Price",presetPrice);row(f,3,"",presetActive);JPanel b=buttons(()->savePreset(),()->clearPreset(),()->deactivate("DEACTIVATE_PRESET","presetId",presetId,"preset"));row(f,4,"",b);p.add(f,BorderLayout.SOUTH);return p;}
    private JPanel placementsTab(){JPanel p=new JPanel(new BorderLayout(10,10));p.setBorder(new EmptyBorder(12,12,12,12));p.add(new JScrollPane(placementTable));JPanel f=form();row(f,0,"Placement",placementName);row(f,1,"Sort order",placementOrder);row(f,2,"",placementActive);row(f,3,"",buttons(()->savePlacement(),()->clearPlacement(),()->deactivate("DEACTIVATE_PLACEMENT","placementId",placementId,"placement")));p.add(f,BorderLayout.EAST);return p;}

    private void wire(){itemTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selectItem();});materialTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selectMaterial();});presetTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selectPreset();});placementTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selectPlacement();});search.getDocument().addDocumentListener(listener(()->{String v=search.getText().trim();itemSorter.setRowFilter(v.isBlank()?null:RowFilter.regexFilter("(?i)"+Pattern.quote(v)));}));name.getDocument().addDocumentListener(listener(()->sku.setText(CustomOrderSkuGenerator.itemSku(name.getText()))));pricingType.addActionListener(e->fieldState());productType.addActionListener(e->fieldState());variants.addActionListener(e->fieldState());}
    private void refresh(){CachedUiLoader.load(this,"custom-catalog:admin",LanCustomOrderCatalogAdminService.State.class,SessionDataCache.SCREEN_TTL,loadingState,LanApiClient::loadCustomCatalogAdmin,loaded->{state=loaded;render();});}
    private void render(){Long keepItem=itemId,keepMaterial=materialId;itemModel.setRowCount(0);for(var x:state.items())itemModel.addRow(new Object[]{x.itemId(),x.name(),x.sku(),x.categoryName(),x.itemType(),x.brand(),x.pricingType(),money(x.fixedPrice()),x.hasVariants()?"Yes":"No",x.quantity(),x.reorderLevel(),x.active()});materialModel.setRowCount(0);for(var x:state.materials())materialModel.addRow(new Object[]{x.materialId(),x.name(),x.active(),x.description()});placementModel.setRowCount(0);for(var x:state.placements())placementModel.addRow(new Object[]{x.placementId(),x.name(),x.sortOrder(),x.active()});if(keepMaterial!=null)renderPresets(keepMaterial);selectRow(itemTable,itemModel,keepItem);selectRow(materialTable,materialModel,keepMaterial);}
    private void renderPresets(Long id){presetModel.setRowCount(0);if(id==null)return;for(var x:state.presets())if(x.materialId()==id)presetModel.addRow(new Object[]{x.presetId(),x.name(),x.pricingMode(),money(x.price()),x.active()});}

    private void selectItem(){Long id=selectedId(itemTable,itemModel);if(id==null)return;var x=state.items().stream().filter(i->i.itemId()==id).findFirst().orElse(null);if(x==null)return;itemId=id;name.setText(x.name());sku.setText(x.sku());barcode.setText(x.barcode());description.setText(x.description());department.setSelectedDepartment(x.categoryId(),x.categoryName());classification.setValues(x.categoryId(),x.itemType(),x.brand());image.setImageUrl(x.imageUrl());productType.setSelectedItem(x.productType());pricingType.setSelectedItem(x.pricingType());price.setText(money(x.fixedPrice()));areaUnit.setSelectedItem(x.areaPriceUnit());dimensionUnit.setSelectedItem(x.dimensionUnit());maxWidth.setText(money(x.maxWidth()));maxLength.setText(money(x.maxLength()));quantity.setText(money(x.quantity()));reorder.setText(money(x.reorderLevel()));variants.setSelected(x.hasVariants());active.setSelected(x.active());extraBarcodes.setText(String.join("\n",x.extraBarcodes()));fieldState();}
    private void saveItem(){
        try{
            Integer categoryId=department.getSelectedDepartmentId();
            if(categoryId==null)throw new IllegalArgumentException("Select a department.");
            Long targetId=itemId;
            boolean hasVariants=variants.isSelected();
            String sourceImage=hasVariants?"":image.getImageUrl();
            List<String>codes=new ArrayList<>(new LinkedHashSet<>(List.of(extraBarcodes.getText().split("\\R"))));
            codes.removeIf(String::isBlank);
            String itemName=name.getText(),primaryBarcode=barcode.getText(),itemDescription=description.getText();
            String selectedProductType=(String)productType.getSelectedItem(),selectedPricingType=(String)pricingType.getSelectedItem();
            String selectedAreaUnit=(String)areaUnit.getSelectedItem(),selectedDimensionUnit=(String)dimensionUnit.getSelectedItem();
            BigDecimal itemPrice=decimal(price,"price",false),width=decimal(maxWidth,"maximum width",false),length=decimal(maxLength,"maximum length",false);
            BigDecimal itemQuantity=decimal(quantity,"quantity",true),reorderLevel=decimal(reorder,"reorder level",true);
            boolean isActive=active.isSelected();
            String itemType=classification.itemTypeName(),brand=classification.brandName();
            UiTaskRunner.submit(this,"custom-catalog.save-item",()->{
                String imageUrl=ProductImageHelper.uploadLocalImageIfNeeded(sourceImage,
                        new ProductImageHelper.ProductImageNaming(
                                itemName, brand, itemType, "", ""));
                var request=new LanCustomOrderCatalogAdminService.ItemSave(targetId,itemName,primaryBarcode,itemDescription,imageUrl,selectedProductType,selectedPricingType,itemPrice,selectedAreaUnit,selectedDimensionUnit,width,length,hasVariants,itemQuantity,reorderLevel,isActive,categoryId,itemType,brand,codes);
                return new ItemMutationResult(mutate("SAVE_ITEM","item",request),imageUrl);
            },result->{itemId=result.id();image.setImageUrl(result.imageUrl());refresh();info("Custom item saved.");},failure->error("Custom item was not saved",asException(failure)));
        }catch(Exception e){error("Custom item was not saved",e);}
    }
    private void clearItem(){itemId=null;itemTable.clearSelection();name.setText("");sku.setText("");barcode.setText("");description.setText("");extraBarcodes.setText("");department.setSelectedDepartmentByName("Custom");classification.clearSelection();image.setImageUrl("");productType.setSelectedItem("INVENTORY");pricingType.setSelectedItem("VARIABLE");price.setText("");maxWidth.setText("");maxLength.setText("");quantity.setText("0");reorder.setText("0");variants.setSelected(false);active.setSelected(true);fieldState();}
    private void fieldState(){boolean inventory="INVENTORY".equals(productType.getSelectedItem()),has=variants.isSelected(),area="AREA".equals(pricingType.getSelectedItem()),priced=!"VARIABLE".equals(pricingType.getSelectedItem())&&!has;price.setEnabled(priced);quantity.setEnabled(inventory&&!has);reorder.setEnabled(inventory&&!has);areaUnit.setEnabled(area);dimensionUnit.setEnabled(area);maxWidth.setEnabled(area);maxLength.setEnabled(area);image.setEnabled(!has);}

    private void variantsDialog(){
        if(itemId==null){info("Save or select an item first.");return;}
        long selectedItemId=itemId;
        JDialog d=new JDialog(this,"Sizes / Variants - "+name.getText(),true);
        d.setSize(900,500);
        DefaultTableModel m=model("ID","Name","SKU","Barcode","Price","Qty","Reorder","Active");
        JTable t=new JTable(m);
        JTextField n=new JTextField(),bc=new JTextField(),pr=new JTextField(),q=new JTextField("0"),re=new JTextField("0");
        ProductImageHelper.ImageSelector img=ProductImageHelper.createImageSelector(d);
        JCheckBox a=new JCheckBox("Active",true);
        final Long[]vid={null};
        Runnable load=()->{m.setRowCount(0);var item=state.items().stream().filter(x->x.itemId()==selectedItemId).findFirst().orElse(null);if(item!=null)for(var x:item.variants())m.addRow(new Object[]{x.variantId(),x.name(),x.sku(),x.barcode(),money(x.fixedPrice()),x.quantity(),x.reorderLevel(),x.active()});};
        t.getSelectionModel().addListSelectionListener(e->{if(e.getValueIsAdjusting())return;int tableRow=t.getSelectedRow();if(tableRow<0)return;tableRow=t.convertRowIndexToModel(tableRow);vid[0]=Long.valueOf(m.getValueAt(tableRow,0).toString());var selected=state.items().stream().flatMap(i->i.variants().stream()).filter(v->v.variantId()==vid[0]).findFirst().orElse(null);if(selected!=null){n.setText(selected.name());bc.setText(selected.barcode());pr.setText(money(selected.fixedPrice()));q.setText(money(selected.quantity()));re.setText(money(selected.reorderLevel()));img.setImageUrl(selected.imageUrl());a.setSelected(selected.active());}});
        JPanel f=form();row(f,0,"Name",n);row(f,1,"Barcode",BarcodeGenerationHelper.field(d,bc));row(f,2,"Price",pr);row(f,3,"Image",img);row(f,4,"Quantity",q);row(f,5,"Reorder",re);row(f,6,"",a);
        JButton save=new JButton("Save"),clear=new JButton("Clear"),off=new JButton("Deactivate");
        Runnable reset=()->{vid[0]=null;t.clearSelection();n.setText("");bc.setText("");pr.setText("");q.setText("0");re.setText("0");img.setImageUrl("");a.setSelected(true);};
        save.addActionListener(e->{
            try{
                Long variantId=vid[0];String variantName=n.getText(),variantBarcode=bc.getText(),sourceImage=img.getImageUrl();
                BigDecimal variantPrice=decimal(pr,"price",false),variantQuantity=decimal(q,"quantity",true),variantReorder=decimal(re,"reorder level",true);boolean isActive=a.isSelected();
                save.setEnabled(false);off.setEnabled(false);
                var parentItem=state.items().stream().filter(x->x.itemId()==selectedItemId).findFirst().orElse(null);
                ProductImageHelper.ProductImageNaming imageNaming=parentItem==null
                        ? new ProductImageHelper.ProductImageNaming("", "", "", "", variantName)
                        : new ProductImageHelper.ProductImageNaming(parentItem.name(),parentItem.brand(),
                        parentItem.itemType(),"",variantName);
                UiTaskRunner.submit(d,"custom-catalog.save-variant",()->{String url=ProductImageHelper.uploadLocalImageIfNeeded(sourceImage,imageNaming);var request=new LanCustomOrderCatalogAdminService.VariantSave(variantId,selectedItemId,variantName,variantBarcode,url,variantPrice,variantQuantity,variantReorder,isActive);return mutate("SAVE_VARIANT","variant",request);},ignored->{d.dispose();refresh();info("Variant saved.");},failure->{save.setEnabled(true);off.setEnabled(true);error("Variant was not saved",asException(failure));});
            }catch(Exception ex){error("Variant was not saved",ex);}
        });
        clear.addActionListener(e->reset.run());
        off.addActionListener(e->{
            Long variantId=vid[0];if(variantId==null){JOptionPane.showMessageDialog(d,"Select a variant first.");return;}
            if(JOptionPane.showConfirmDialog(d,"Deactivate this variant?","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            JsonObject body=new JsonObject();body.addProperty("variantId",variantId);save.setEnabled(false);off.setEnabled(false);
            UiTaskRunner.submit(d,"custom-catalog.deactivate-variant",()->LanApiClient.updateCustomCatalogAdmin("DEACTIVATE_VARIANT",body,UUID.randomUUID().toString()),ignored->{d.dispose();refresh();},failure->{save.setEnabled(true);off.setEnabled(true);error("The variant was not deactivated",asException(failure));});
        });
        JPanel buttons=new JPanel(new GridLayout(1,3,6,6));buttons.add(save);buttons.add(clear);buttons.add(off);row(f,7,"",buttons);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(t),new JScrollPane(f));split.setResizeWeight(.6);d.add(split);load.run();d.setLocationRelativeTo(this);d.setVisible(true);refresh();
    }

    private void selectMaterial(){materialId=selectedId(materialTable,materialModel);if(materialId==null)return;var x=state.materials().stream().filter(v->v.materialId()==materialId).findFirst().orElse(null);if(x!=null){materialName.setText(x.name());materialDescription.setText(x.description());materialActive.setSelected(x.active());clearPreset();renderPresets(materialId);}}
    private void saveMaterial(){try{var request=new LanCustomOrderCatalogAdminService.MaterialSave(materialId,materialName.getText(),materialDescription.getText(),materialActive.isSelected());mutateAsync("save-material","SAVE_MATERIAL","material",request,id->{materialId=id;refresh();info("Print material saved.");},"Print material was not saved");}catch(Exception e){error("Print material was not saved",e);}}
    private void clearMaterial(){materialId=null;materialTable.clearSelection();materialName.setText("");materialDescription.setText("");materialActive.setSelected(true);clearPreset();renderPresets(null);}
    private void selectPreset(){presetId=selectedId(presetTable,presetModel);if(presetId==null)return;var x=state.presets().stream().filter(v->v.presetId()==presetId).findFirst().orElse(null);if(x!=null){presetName.setText(x.name());presetMode.setSelectedItem(x.pricingMode());presetPrice.setText(money(x.price()));presetActive.setSelected(x.active());}}
    private void savePreset(){try{if(materialId==null)throw new IllegalStateException("Select a print material first.");Long selectedMaterial=materialId;var request=new LanCustomOrderCatalogAdminService.PresetSave(presetId,selectedMaterial,presetName.getText(),(String)presetMode.getSelectedItem(),decimal(presetPrice,"price",true),presetActive.isSelected());mutateAsync("save-preset","SAVE_PRESET","preset",request,id->{presetId=id;refresh();info("Print preset saved.");},"Print preset was not saved");}catch(Exception e){error("Print preset was not saved",e);}}
    private void clearPreset(){presetId=null;presetTable.clearSelection();presetName.setText("");presetPrice.setText("");presetMode.setSelectedItem("FIXED_PRESET");presetActive.setSelected(true);}
    private void selectPlacement(){placementId=selectedId(placementTable,placementModel);if(placementId==null)return;var x=state.placements().stream().filter(v->v.placementId()==placementId).findFirst().orElse(null);if(x!=null){placementName.setText(x.name());placementOrder.setText(String.valueOf(x.sortOrder()));placementActive.setSelected(x.active());}}
    private void savePlacement(){try{var request=new LanCustomOrderCatalogAdminService.PlacementSave(placementId,placementName.getText(),integer(placementOrder,"sort order"),placementActive.isSelected());mutateAsync("save-placement","SAVE_PLACEMENT","placement",request,id->{placementId=id;refresh();info("Design placement saved.");},"Design placement was not saved");}catch(Exception e){error("Design placement was not saved",e);}}
    private void clearPlacement(){placementId=null;placementTable.clearSelection();placementName.setText("");placementOrder.setText("0");placementActive.setSelected(true);}
    private void deactivate(String action,String field,Long id,String label){if(id==null){info("Select a "+label+" first.");return;}if(JOptionPane.showConfirmDialog(this,"Deactivate this "+label+"?","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;JsonObject body=new JsonObject();body.addProperty(field,id);UiTaskRunner.submit(this,"custom-catalog.deactivate-"+label,()->LanApiClient.updateCustomCatalogAdmin(action,body,UUID.randomUUID().toString()),ignored->refresh(),failure->error("The "+label+" was not deactivated",asException(failure)));}
    private void mutateAsync(String key,String action,String field,Object value,Consumer<Long> success,String failureMessage){UiTaskRunner.submit(this,"custom-catalog."+key,()->mutate(action,field,value),success,failure->error(failureMessage,asException(failure)));}
    private long mutate(String action,String field,Object value)throws Exception{JsonObject b=new JsonObject();b.add(field,GSON.toJsonTree(value));return LanApiClient.updateCustomCatalogAdmin(action,b,UUID.randomUUID().toString());}
    private static Exception asException(Throwable failure){return failure instanceof Exception exception?exception:new Exception(failure);}

    private static DefaultTableModel model(String...columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private static JPanel form(){JPanel p=new JPanel(new GridBagLayout());p.setBorder(new EmptyBorder(8,8,8,8));return p;}private static void row(JPanel p,int y,String label,Component c){GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(4,4,4,4);g.gridy=y;g.gridx=0;g.anchor=GridBagConstraints.WEST;p.add(new JLabel(label),g);g.gridx=1;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;p.add(c,g);}
    private static JPanel buttons(Runnable save,Runnable clear,Runnable off){JPanel p=new JPanel(new GridLayout(1,3,6,6));JButton s=new JButton("Save"),c=new JButton("Clear"),d=new JButton("Deactivate");s.addActionListener(e->save.run());c.addActionListener(e->clear.run());d.addActionListener(e->off.run());p.add(s);p.add(c);p.add(d);return p;}
    private static Long selectedId(JTable t,DefaultTableModel m){int r=t.getSelectedRow();if(r<0)return null;r=t.convertRowIndexToModel(r);return Long.valueOf(m.getValueAt(r,0).toString());}private static void selectRow(JTable t,DefaultTableModel m,Long id){if(id==null)return;for(int r=0;r<m.getRowCount();r++)if(id.toString().equals(String.valueOf(m.getValueAt(r,0)))){int v=t.convertRowIndexToView(r);if(v>=0)t.setRowSelectionInterval(v,v);return;}}
    private static String money(BigDecimal v){return v==null?"":v.stripTrailingZeros().toPlainString();}private static BigDecimal decimal(JTextField f,String label,boolean required){String v=f.getText().trim();if(v.isBlank()){if(required)throw new IllegalArgumentException("Enter "+label+".");return null;}try{return new BigDecimal(v);}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+".");}}private static int integer(JTextField f,String label){try{return Integer.parseInt(f.getText().trim());}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+".");}}
    private static DocumentListener listener(Runnable r){return new DocumentListener(){public void insertUpdate(DocumentEvent e){r.run();}public void removeUpdate(DocumentEvent e){r.run();}public void changedUpdate(DocumentEvent e){r.run();}};}private void info(String m){JOptionPane.showMessageDialog(this,m);}private void error(String m,Exception e){JOptionPane.showMessageDialog(this,m+".\n\n"+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),"Custom Order Catalog",JOptionPane.ERROR_MESSAGE);}
    private record ItemMutationResult(long id,String imageUrl){}
}
