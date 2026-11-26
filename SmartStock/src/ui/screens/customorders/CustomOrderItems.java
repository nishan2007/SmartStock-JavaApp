package ui.screens.customorders;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import services.CustomOrderSkuGenerator;
import services.ShirtDesignerService;
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
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import static ui.screens.customorders.CustomCatalogDesign.*;

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

/** Custom-order catalog editor backed entirely by the authenticated store service. */
public final class CustomOrderItems extends JFrame {
    private static final Gson GSON=LanJson.create();
    interface CatalogAccess {
        LanCustomOrderCatalogAdminService.State load() throws Exception;
        long update(String action, JsonObject body, String requestId) throws Exception;

        default LanApiClient.MobileItemPhotoHandoff createPhonePhoto(String type, long id) throws Exception {
            return LanApiClient.createMobileItemPhotoHandoff(type, id);
        }

        default LanApiClient.MobileItemPhotoHandoffStatus phonePhotoStatus(String id) throws Exception {
            return LanApiClient.mobileItemPhotoHandoffStatus(id);
        }
    }

    private final CatalogAccess catalog;
    private LanCustomOrderCatalogAdminService.State state;
    private CustomItemInventoryPanel inventoryPanel;
    private Editor itemDialog;

    private final LoadingStatePanel loadingState=new LoadingStatePanel();

    private final DefaultTableModel itemModel=model("ID","Item","Size","Color","SKU","Department","Item Type","Brand","Pricing","Price","Variants","Qty","Reorder","Active","Sell in POS");
    private final JTable itemTable=new JTable(itemModel);private final TableRowSorter<DefaultTableModel>itemSorter=new TableRowSorter<>(itemModel);private final JTextField search=new JTextField();
    private final JTextField name=new JTextField(),size=new JTextField(),color=new JTextField(),sku=new JTextField(),barcode=new JTextField(),price=new JTextField(),quantity=new JTextField("0"),reorder=new JTextField("0"),maxWidth=new JTextField(),maxLength=new JTextField(),templateWidth=new JTextField(),templateHeight=new JTextField();
    private final JTextArea description=new JTextArea(3,24),extraBarcodes=new JTextArea(3,24);private final JPanel imageHost = new JPanel(new BorderLayout());
    private final ProductImageHelper.ImageSelector image=ProductImageHelper.createSimpleImageSelector(imageHost);
    private final DepartmentSelector department=new DepartmentSelector();private final ItemClassificationSelector classification=new ItemClassificationSelector(department);
    private final JComboBox<String>productType=new JComboBox<>(new String[]{"INVENTORY","SERVICE","NON_INVENTORY"}),pricingType=new JComboBox<>(new String[]{"VARIABLE","FIXED","AREA"}),areaUnit=new JComboBox<>(new String[]{"SQ_FT","SQ_IN","SQ_YD","SQ_M","SQ_CM"}),dimensionUnit=new JComboBox<>(new String[]{"IN","FT","YD","M","CM"});
    private final JComboBox<String>templateUnit=new JComboBox<>(new String[]{"IN","FT","YD","CM","M"});
    private final JComboBox<String> shirtType=new JComboBox<>(new String[]{"T-shirt","Polo"}),shirtNeckline=new JComboBox<>(new String[]{"Round neck","V-neck"}),shirtFabric=new JComboBox<>(new String[]{"Normal","Dry-fit"});
    private final JComboBox<Object> serviceCategory=new JComboBox<>(services.CustomItemCategory.values());
    private boolean loadingShirtFields;
    private final JCheckBox variants=new JCheckBox("Track sizes / variants"),sellInPos=new JCheckBox("Sell in normal POS (without printing)"),active=new JCheckBox("Active",true),templateUsesOrderArea=new JCheckBox("Use order measurements for area-priced design");private Long itemId;

    private final DefaultTableModel materialModel=model("ID","Material","Active","Description","Colors"),presetModel=model("ID","Name","Pricing","Price","Active","Max width (in)","Max height (in)"),placementModel=model("ID","Placement","Order","Active");
    private final JTable materialTable=new JTable(materialModel),presetTable=new JTable(presetModel),placementTable=new JTable(placementModel);
    private final JTextField materialName=new JTextField(),materialColors=new JTextField(),presetName=new JTextField(),presetPrice=new JTextField(),presetWidth=new JTextField(),presetHeight=new JTextField(),placementName=new JTextField(),placementOrder=new JTextField("0");
    private final JTextArea materialDescription=new JTextArea(3,20);private final JComboBox<String>presetMode=new JComboBox<>(new String[]{"FIXED_PRESET","PER_LINE"});
    private final JCheckBox materialActive=new JCheckBox("Active",true),presetActive=new JCheckBox("Active",true),placementActive=new JCheckBox("Active",true);private Long materialId,presetId,placementId;

    public CustomOrderItems() {
        this(new CatalogAccess() {
            @Override public LanCustomOrderCatalogAdminService.State load() throws Exception {
                return LanApiClient.loadCustomCatalogAdmin();
            }

            @Override public long update(String action, JsonObject body, String requestId) throws Exception {
                long saved = LanApiClient.updateCustomCatalogAdmin(action, body, requestId);
                services.InventoryCatalogCache.invalidate();
                services.InventoryCatalogCache.refreshAfterMutation().exceptionally(failure -> null);
                return saved;
            }
        });
    }

    CustomOrderItems(CatalogAccess catalog) {
        this.catalog = catalog;
        setTitle("Custom Order Items");
        setSize(1220, 760);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "CustomOrderItems"));
        JTabbedPane tabs = new JTabbedPane() {
            @Override public void updateUI() { setUI(new OrdersTabsUI()); }
        };
        tabs.addTab("Items", itemsTab());
        inventoryPanel = new CustomItemInventoryPanel(this, this::refresh, (action, value) -> {
            try {
                mutate(action, action.equals("SAVE_VARIANT") ? "variant" : "item", value);
            } catch (Exception failure) {
                throw new RuntimeException(failure.getMessage(), failure);
            }
        }, this::openItemEditor, () -> openItemEditor(null));
        tabs.addTab("Custom Inventory", inventoryPanel);
        tabs.addTab("Print Materials", materialsTab());
        tabs.addTab("Design Placements", placementsTab());
        tabs.setSelectedComponent(inventoryPanel);

        add(tabs, BorderLayout.CENTER);
        add(loadingState, BorderLayout.SOUTH);
        imageHost.setOpaque(false);
        imageHost.add(image);
        wire();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent event) {
                WindowHelper.configurePosWindow(CustomOrderItems.this);
                refresh();
            }
        });
    }

    private JPanel itemsTab() {
        JPanel panel = surface(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel header = surface(new BorderLayout(8, 10));
        header.add(heading("Items", "Custom item details and sizes / variants"), BorderLayout.NORTH);
        JPanel toolbar = surface(new BorderLayout(8, 0));
        search.putClientProperty("JTextField.placeholderText", "Find item, SKU or barcode");
        search.getAccessibleContext().setAccessibleName("Search items");
        DeckersSwing.styleField(search);
        toolbar.add(search);
        JPanel actions = surface(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(addButton("Add Item", () -> {
            if (inventoryPanel.canOpenEditor()) openItemEditor(null);
        }));
        JButton edit = button("Edit Item Details", () -> {
            if (inventoryPanel.canOpenEditor()) openItemEditor(selectedId(itemTable, itemModel));
        });
        JButton sizes = button("Sizes / Variants", () -> {
            if (inventoryPanel.canOpenEditor()) variantsDialog(this, selectedId(itemTable, itemModel));
        });
        edit.setEnabled(false);
        sizes.setEnabled(false);
        itemTable.getSelectionModel().addListSelectionListener(event -> {
            boolean selected = itemTable.getSelectedRow() >= 0;
            edit.setEnabled(selected);
            sizes.setEnabled(selected);
        });
        actions.add(edit);
        actions.add(sizes);
        actions.add(button("Refresh", this::refresh));
        toolbar.add(actions, BorderLayout.EAST);
        header.add(toolbar);
        panel.add(header, BorderLayout.NORTH);

        CustomCatalogDesign.table(itemTable);
        itemTable.setRowSorter(itemSorter);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        openOnActivation(itemTable, edit);
        ui.helpers.TableImageHoverPreview.install(this, itemTable, viewRow -> {
            if (state == null || viewRow < 0 || viewRow >= itemTable.getRowCount()) return List.of();
            long id = ((Number) itemModel.getValueAt(itemTable.convertRowIndexToModel(viewRow), 0)).longValue();
            return state.items().stream().filter(item -> item.itemId() == id).findFirst()
                    .map(ui.helpers.CustomItemPictures::forItem).orElse(List.of());
        }, DeckersPalette.ORANGE);
        panel.add(scroll(itemTable));
        return panel;
    }

    private void openItemEditor(Long id) {
        if (state == null || itemDialog != null) return;
        var record = state.items().stream().filter(item -> id != null && item.itemId() == id).findFirst().orElse(null);
        if (id != null && record == null) return;
        serviceCategory.setModel(new DefaultComboBoxModel<>(services.CustomItemCategories.choices(state.customCategories())));
        clearItem();
        if (record != null) loadItem(record);

        Editor dialog = new Editor(this, record == null ? "Add Item" : "Edit Item – " + record.name());
        itemDialog = dialog;
        JButton save = new JButton("Save Item");
        JButton deactivate = button("Deactivate", () -> deactivateItem(dialog));
        JButton sizes = new JButton("Sizes / Variants");
        JButton photo = button("Add Phone Photo", () -> {
            Long target = itemId;
            var previous = state.items().stream().filter(item -> java.util.Objects.equals(item.itemId(), target))
                    .findFirst().orElse(null);
            if (previous == null) return;
            showPhonePhoto("item", target, dialog, saved -> saved.items().stream()
                    .filter(item -> item.itemId() == target && java.util.Objects.equals(target, itemId)).findFirst()
                    .ifPresent(updated -> dialog.receiveSavedChanges(() -> mergePhonePhoto(image, previous.imageUrl(),
                            previous.additionalImageUrls(), updated.imageUrl(), updated.additionalImageUrls()))));
        });
        Runnable updateActions = () -> {
            save.setEnabled(!name.getText().isBlank());
            deactivate.setEnabled(itemId != null && active.isSelected());
            sizes.setEnabled(itemId != null);
            photo.setEnabled(itemId != null);
        };
        save.addActionListener(event -> saveItem(dialog, updateActions));
        sizes.addActionListener(event -> {
            if (!dialog.confirmDiscard()) return;
            long parentId = itemId;
            variantsDialog(dialog, parentId);
            state.items().stream().filter(item -> item.itemId() == parentId).findFirst().ifPresent(this::loadItem);
            dialog.markSaved();
            updateActions.run();
        });
        JButton clear = button("Clear", () -> {
            if (!dialog.confirmDiscard()) return;
            clearItem();
            dialog.rename("Add Item");
            dialog.markSaved();
            updateActions.run();
            dialog.showDetails();
            name.requestFocusInWindow();
        });
        dialog.layoutEditor(save, itemSections(dialog), clear, deactivate, sizes, photo);
        dialog.watchDraft(this::itemDraft);
        DocumentListener nameListener = listener(updateActions);
        name.getDocument().addDocumentListener(nameListener);
        updateActions.run();
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        try {
            open(dialog);
        } finally {
            name.getDocument().removeDocumentListener(nameListener);
            itemDialog = null;
            itemId = null;
        }
    }

    private Section[] itemSections(Editor dialog) {
        JPanel f = fields();
        int r = 0;
        row(f,r++,"Item name",name);
        row(f,r++,"Department",department);
        row(f,r++,"Item Type",classification.itemTypeComponent());
        row(f,r++,"",new JLabel("<html>Variants inherit this Item Type.</html>"));
        row(f,r++,"Brand",classification.brandComponent());
        row(f,r++,"",sellInPos);
        row(f,r,"Category",serviceCategory);

        JPanel details = f;
        JPanel shirt = fields();
        row(shirt,0,"Shirt type",shirtType);
        row(shirt,1,"Neckline",shirtNeckline);
        row(shirt,2,"Fabric",shirtFabric);
        row(shirt,3,"",button("Shirt designer setup", this::shirtSetup));
        r = 4;
        f = shirt;
        row(f,r++,"Size",size);
        row(f,r++,"Color",color);
        JPanel identifiers = fields();
        sku.setEditable(false);
        row(identifiers,0,"Generated SKU",sku);
        row(identifiers,1,"Primary barcode",BarcodeGenerationHelper.field(dialog,barcode));
        extraBarcodes.setFont(name.getFont());
        row(identifiers,2,"More barcodes",BarcodeGenerationHelper.area(dialog,extraBarcodes,multiline(extraBarcodes, 49)));
        description.setFont(name.getFont());
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        row(identifiers,3,"Description",multiline(description, 68));

        JPanel pricing = fields();
        row(pricing,0,"Product type",productType);
        row(pricing,1,"Pricing",pricingType);
        row(pricing,2,"Price / area rate",price);
        row(pricing,3,"Area unit",areaUnit);
        row(pricing,4,"Dimension unit",dimensionUnit);
        row(pricing,5,"Maximum width",maxWidth);
        row(pricing,6,"Maximum length",maxLength);
        JPanel stock = fields();
        row(stock,0,"Quantity",quantity);
        row(stock,1,"Reorder level",reorder);
        row(stock,2,"",variants);
        row(stock,3,"",active);
        JPanel template = fields();
        row(template,0,"Template width",templateWidth);
        row(template,1,"Template height",templateHeight);
        row(template,2,"Template unit",templateUnit);
        row(template,3,"",templateUsesOrderArea);
        return new Section[]{
                new Section("Details", "Item Details", columns(details, shirt, identifiers), null),
                new Section("Pricing & stock", columns(pricing, stock)),
                new Section("Template", topAligned(template)),
                new Section("Photos", topAligned(imageHost))
        };
    }

    private List<?> itemDraft() {
        return java.util.Arrays.asList(name.getText(), size.getText(), color.getText(), barcode.getText(),
                extraBarcodes.getText(), description.getText(), department.getSelectedDepartmentName(),
                classification.itemTypeName(), classification.brandName(), serviceCategory.getSelectedItem(),
                productType.getSelectedItem(), pricingType.getSelectedItem(), price.getText(), areaUnit.getSelectedItem(),
                dimensionUnit.getSelectedItem(), maxWidth.getText(), maxLength.getText(), templateWidth.getText(),
                templateHeight.getText(), templateUnit.getSelectedItem(), templateUsesOrderArea.isSelected(),
                quantity.getText(), reorder.getText(), variants.isSelected(), active.isSelected(), sellInPos.isSelected(),
                currentShirtConfig(), image.getImageUrl(), image.getAdditionalImageUrls());
    }

    private static void openOnActivation(JTable table, JButton edit) {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2
                        && table.rowAtPoint(event.getPoint()) >= 0) edit.doClick();
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "openEditor");
        table.getActionMap().put("openEditor", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { edit.doClick(); }
        });
    }
    private JPanel materialsTab(){JPanel p=new JPanel(new BorderLayout(10,10));p.setBorder(new EmptyBorder(12,12,12,12));JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,materialPanel(),presetPanel());split.setResizeWeight(.5);p.add(split);return p;}
    private JPanel materialPanel(){JPanel p=new JPanel(new BorderLayout(8,8));p.setBorder(BorderFactory.createTitledBorder("Print Materials"));p.add(new JScrollPane(materialTable));JPanel f=form();row(f,0,"Name",materialName);row(f,1,"Description",new JScrollPane(materialDescription));row(f,2,"Colors (name=#RRGGBB, comma separated)",materialColors);row(f,3,"Blank = artwork colors",materialActive);JPanel b=buttons(()->saveMaterial(),()->clearMaterial(),()->deactivate("DEACTIVATE_MATERIAL","materialId",materialId,"material"));row(f,4,"",b);p.add(f,BorderLayout.SOUTH);return p;}
    private JPanel presetPanel(){JPanel p=new JPanel(new BorderLayout(8,8));p.setBorder(BorderFactory.createTitledBorder("Sizes / Pricing Presets"));p.add(new JScrollPane(presetTable));JPanel f=form();row(f,0,"Name",presetName);row(f,1,"Pricing mode",presetMode);row(f,2,"Price",presetPrice);row(f,3,"Max width (inches)",presetWidth);row(f,4,"Max height (inches)",presetHeight);row(f,5,"",presetActive);JPanel b=buttons(()->savePreset(),()->clearPreset(),()->deactivate("DEACTIVATE_PRESET","presetId",presetId,"preset"));row(f,6,"",b);p.add(f,BorderLayout.SOUTH);return p;}
    private JPanel placementsTab(){JPanel p=new JPanel(new BorderLayout(10,10));p.setBorder(new EmptyBorder(12,12,12,12));p.add(new JScrollPane(placementTable));JPanel f=form();row(f,0,"Placement",placementName);row(f,1,"Sort order",placementOrder);row(f,2,"",placementActive);row(f,3,"",buttons(()->savePlacement(),()->clearPlacement(),()->deactivate("DEACTIVATE_PLACEMENT","placementId",placementId,"placement")));p.add(f,BorderLayout.EAST);return p;}

    private void wire(){materialTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selectMaterial();});presetTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selectPreset();});placementTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selectPlacement();});search.getDocument().addDocumentListener(listener(()->{String v=search.getText().trim();itemSorter.setRowFilter(v.isBlank()?null:new RowFilter<DefaultTableModel,Integer>(){
            public boolean include(Entry<? extends DefaultTableModel,? extends Integer> entry){
                if(state==null)return false;long id=((Number)entry.getValue(0)).longValue();
                return state.items().stream().filter(item->item.itemId()==id).anyMatch(item->services.CustomCatalogSearch.matchesItem(item,v));
            }
        });}));name.getDocument().addDocumentListener(listener(()->sku.setText(CustomOrderSkuGenerator.itemSku(name.getText()))));pricingType.addActionListener(e->fieldState());productType.addActionListener(e->fieldState());variants.addActionListener(e->fieldState());serviceCategory.addActionListener(e->shirtFieldState());shirtType.addActionListener(e->{if(!loadingShirtFields){shirtNeckline.setSelectedIndex(0);shirtFieldState();}});shirtFieldState();}
    private void refresh(){CachedUiLoader.load(this,"custom-catalog:admin",LanCustomOrderCatalogAdminService.State.class,SessionDataCache.SCREEN_TTL,loadingState,catalog::load,this::applyCatalogState);}
    private void applyCatalogState(LanCustomOrderCatalogAdminService.State loaded) { state=loaded;render();inventoryPanel.setState(loaded); }
    private void render(){Long keepItem=selectedId(itemTable,itemModel),keepMaterial=materialId;itemModel.setRowCount(0);for(var x:state.items())if(!x.archived())itemModel.addRow(new Object[]{x.itemId(),x.name(),x.size(),x.color(),x.sku(),x.categoryName(),x.itemType(),x.brand(),x.pricingType(),money(x.fixedPrice()),x.hasVariants()?"Yes":"No",x.quantity(),x.reorderLevel(),x.active(),x.sellInPos()});materialModel.setRowCount(0);for(var x:state.materials())materialModel.addRow(new Object[]{x.materialId(),x.name(),x.active(),x.description(),services.PrintMaterialColors.format(x.availableColors())});placementModel.setRowCount(0);for(var x:state.placements())placementModel.addRow(new Object[]{x.placementId(),x.name(),x.sortOrder(),x.active()});if(keepMaterial!=null)renderPresets(keepMaterial);selectRow(itemTable,itemModel,keepItem);selectRow(materialTable,materialModel,keepMaterial);}
    private void renderPresets(Long id){presetModel.setRowCount(0);if(id==null)return;for(var x:state.presets())if(x.materialId()==id)presetModel.addRow(new Object[]{x.presetId(),x.name(),x.pricingMode(),money(x.price()),x.active(),x.widthIn(),x.heightIn()});}

    private void loadItem(LanCustomOrderCatalogAdminService.Item x){itemId=x.itemId();sellInPos.setSelected(x.sellInPos());serviceCategory.setSelectedItem(services.CustomItemCategories.choice(GSON.toJsonTree(x).getAsJsonObject(),state.customCategories()));name.setText(x.name());size.setText(x.size());color.setText(x.color());sku.setText(x.sku());barcode.setText(x.barcode());description.setText(x.description());department.setSelectedDepartment(x.categoryId(),x.categoryName());classification.setValues(x.categoryId(),x.itemType(),x.brand());image.setFallbackPictures(ui.helpers.CustomItemPictures.variants(x));image.setImageUrl(x.imageUrl());image.setAdditionalImageUrls(x.additionalImageUrls());shirtConfig=x.shirtDesignerConfig().deepCopy();loadShirtFields();productType.setSelectedItem(x.productType());pricingType.setSelectedItem(x.pricingType());price.setText(money(x.fixedPrice()));areaUnit.setSelectedItem(x.areaPriceUnit());dimensionUnit.setSelectedItem(x.dimensionUnit());maxWidth.setText(money(x.maxWidth()));maxLength.setText(money(x.maxLength()));templateWidth.setText(money(x.templateWidth()));templateHeight.setText(money(x.templateHeight()));templateUnit.setSelectedItem(x.templateUnit());templateUsesOrderArea.setSelected(x.templateUsesOrderArea());quantity.setText(money(x.quantity()));reorder.setText(money(x.reorderLevel()));variants.setSelected(x.hasVariants());active.setSelected(x.active());extraBarcodes.setText(String.join("\n",x.extraBarcodes()));fieldState();}
    private void saveItem(Editor dialog, Runnable updateActions){
        try{
            Integer categoryId=department.getSelectedDepartmentId();
            if(categoryId==null)throw new IllegalArgumentException("Select a department.");
            Long targetId=itemId;
            boolean hasVariants=variants.isSelected();
            String sourceImage=image.getImageUrl();
            List<String> sourceExtras=image.getAdditionalImageUrls();
            List<String>codes=new ArrayList<>(new LinkedHashSet<>(List.of(extraBarcodes.getText().split("\\R"))));
            codes.removeIf(String::isBlank);
            String itemName=name.getText(),itemSize=size.getText(),itemColor=color.getText(),primaryBarcode=barcode.getText(),itemDescription=description.getText();
            String selectedProductType=(String)productType.getSelectedItem(),selectedPricingType=(String)pricingType.getSelectedItem();
            String selectedAreaUnit=(String)areaUnit.getSelectedItem(),selectedDimensionUnit=(String)dimensionUnit.getSelectedItem();
            BigDecimal itemPrice=decimal(price,"price",false),width=decimal(maxWidth,"maximum width",false),length=decimal(maxLength,"maximum length",false),canvasWidth=decimal(templateWidth,"template width",false),canvasHeight=decimal(templateHeight,"template height",false);
            BigDecimal itemQuantity=decimal(quantity,"quantity",true),reorderLevel=decimal(reorder,"reorder level",true);
            boolean isActive=active.isSelected(), allowPos=sellInPos.isSelected(),isShirt=(serviceCategory.getSelectedItem()==services.CustomItemCategory.SHIRT),isSublimation=(serviceCategory.getSelectedItem()==services.CustomItemCategory.SUBLIMATION),isBusinessCards=(serviceCategory.getSelectedItem()==services.CustomItemCategory.BUSINESS_CARDS),isLargeFormatPrinting=(serviceCategory.getSelectedItem()==services.CustomItemCategory.LARGE_FORMAT),isLaser=(serviceCategory.getSelectedItem()==services.CustomItemCategory.LASER),orderArea=templateUsesOrderArea.isSelected();
            String customCategoryId=serviceCategory.getSelectedItem() instanceof services.CustomItemCategories.Choice choice?choice.id():"";
            JsonObject shirtSetup=currentShirtConfig();String canvasUnit=(String)templateUnit.getSelectedItem();
            String itemType=classification.itemTypeName(),brand=classification.brandName();
            dialog.setBusy(true);
            UiTaskRunner.submit(dialog,"custom-catalog.save-item",()->{
                String imageUrl=ProductImageHelper.uploadLocalImageIfNeeded(sourceImage,
                        new ProductImageHelper.ProductImageNaming(
                                itemName, brand, itemType, itemSize, ""),"CUSTOM_ITEM");
                List<String> extras=ProductImageHelper.uploadAdditionalImages(sourceExtras,new ProductImageHelper.ProductImageNaming(itemName,brand,itemType,itemSize,""),"CUSTOM_ITEM");
                var request=new LanCustomOrderCatalogAdminService.ItemSave(targetId,itemName,itemSize,itemColor,primaryBarcode,itemDescription,imageUrl,selectedProductType,selectedPricingType,itemPrice,selectedAreaUnit,selectedDimensionUnit,width,length,hasVariants,itemQuantity,reorderLevel,isActive,categoryId,itemType,brand,allowPos,codes,extras,canvasWidth,canvasHeight,canvasUnit,orderArea,isShirt,shirtSetup,isSublimation,isBusinessCards,isLargeFormatPrinting,isLaser,customCategoryId);
                return new ItemMutationResult(mutate("SAVE_ITEM","item",request),imageUrl,extras);
            }, result -> reloadAfterSaved(dialog, () -> {
                itemId = result.id();
                image.setImageUrl(result.imageUrl());
                image.setAdditionalImageUrls(result.additionalImageUrls());
                dialog.setBusy(false);
                dialog.rename("Edit Item – " + itemName.trim());
                dialog.markSaved();
                updateActions.run();
                JOptionPane.showMessageDialog(dialog, "Custom item saved.");
            }), failure -> {
                dialog.setBusy(false);
                catalogError(dialog, "Custom item was not saved", failure);
            });
        }catch(Exception e){catalogError(dialog, "Custom item was not saved", e);}
    }

    private void reloadAfterSaved(Editor dialog, Runnable after) {
        UiTaskRunner.submit(dialog, "custom-catalog.reload-after-save", catalog::load, loaded -> {
            SessionDataCache.put("custom-catalog:admin", loaded);
            applyCatalogState(loaded);
            after.run();
        }, failure -> {
            catalogError(dialog, "The change was saved. The catalog could not be refreshed", failure);
            refresh();
            after.run();
        });
    }
    private JsonObject shirtConfig=new JsonObject();
    private void shirtSetup(){JsonObject value=ShirtSetupDialog.edit(itemDialog,currentShirtConfig(),state);if(value!=null){shirtConfig=value;serviceCategory.setSelectedItem(services.CustomItemCategory.SHIRT);loadShirtFields();}}
    private JsonObject currentShirtConfig(){
        if(!(serviceCategory.getSelectedItem()==services.CustomItemCategory.SHIRT))return shirtConfig.deepCopy();
        JsonObject config=shirtConfig.size()==0?ShirtDesignerService.defaults():shirtConfig.deepCopy();
        String type=shirtType.getSelectedIndex()==1?"COLLARED":"TEE",neck=type.equals("COLLARED")?"COLLAR":shirtNeckline.getSelectedIndex()==1?"V":"ROUND";
        boolean changed=!config.has("shirtType")||!config.has("neckline")||!config.get("shirtType").getAsString().equals(type)||!config.get("neckline").getAsString().equals(neck);
        config.addProperty("shirtType",type);config.addProperty("neckline",neck);config.addProperty("fabric",shirtFabric.getSelectedIndex()==1?"DRY_FIT":"NORMAL");
        if(changed){config.addProperty("frontMockup",ShirtDesignerService.defaultMockup(type,neck,false));config.addProperty("backMockup",ShirtDesignerService.defaultMockup(type,neck,true));}
        return config;
    }
    private void loadShirtFields(){
        loadingShirtFields=true;
        try{JsonObject config=shirtConfig.size()==0?ShirtDesignerService.defaults():shirtConfig;
            boolean polo=config.has("shirtType")&&"COLLARED".equals(config.get("shirtType").getAsString());
            shirtType.setSelectedIndex(polo?1:0);shirtFieldState();shirtNeckline.setSelectedIndex(!polo&&config.has("neckline")&&"V".equals(config.get("neckline").getAsString())?1:0);
            shirtFabric.setSelectedIndex(config.has("fabric")&&"DRY_FIT".equals(config.get("fabric").getAsString())?1:0);
        }finally{loadingShirtFields=false;shirtFieldState();}
    }
    private void shirtFieldState(){
        boolean shirt=(serviceCategory.getSelectedItem()==services.CustomItemCategory.SHIRT),polo=shirtType.getSelectedIndex()==1;
        if(polo&&shirtNeckline.getItemCount()!=1)shirtNeckline.setModel(new DefaultComboBoxModel<>(new String[]{"Collar"}));
        if(!polo&&shirtNeckline.getItemCount()!=2)shirtNeckline.setModel(new DefaultComboBoxModel<>(new String[]{"Round neck","V-neck"}));
        shirtType.setEnabled(shirt);shirtFabric.setEnabled(shirt);shirtNeckline.setEnabled(shirt&&!polo);
    }

    private void clearItem(){serviceCategory.setSelectedItem(services.CustomItemCategory.NONE);image.setFallbackPictures(List.of());shirtConfig=new JsonObject();loadShirtFields();itemId=null;name.setText("");size.setText("");color.setText("");sku.setText("");barcode.setText("");description.setText("");extraBarcodes.setText("");department.setSelectedDepartmentByName("Custom");classification.clearSelection();image.setImageUrl("");image.setAdditionalImageUrls(List.of());productType.setSelectedItem("INVENTORY");pricingType.setSelectedItem("VARIABLE");price.setText("");areaUnit.setSelectedIndex(0);dimensionUnit.setSelectedIndex(0);maxWidth.setText("");maxLength.setText("");templateWidth.setText("");templateHeight.setText("");templateUnit.setSelectedItem("IN");templateUsesOrderArea.setSelected(false);quantity.setText("0");reorder.setText("0");variants.setSelected(false);sellInPos.setSelected(false);active.setSelected(true);fieldState();}
    private void fieldState(){boolean inventory="INVENTORY".equals(productType.getSelectedItem()),has=variants.isSelected(),area="AREA".equals(pricingType.getSelectedItem()),priced=!"VARIABLE".equals(pricingType.getSelectedItem())&&!has;price.setEnabled(priced);quantity.setEnabled(inventory&&!has);reorder.setEnabled(inventory&&!has);areaUnit.setEnabled(area);dimensionUnit.setEnabled(area);maxWidth.setEnabled(area);maxLength.setEnabled(area);templateUsesOrderArea.setEnabled(area);if(!area)templateUsesOrderArea.setSelected(false);image.setEnabled(true);}

    private void deactivateItem(Editor dialog) {
        if (itemId == null || !dialog.confirmDiscard()) return;
        if (JOptionPane.showConfirmDialog(dialog, "Deactivate this item?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        JsonObject body = new JsonObject();
        body.addProperty("itemId", itemId);
        dialog.setBusy(true);
        UiTaskRunner.submit(dialog, "custom-catalog.deactivate-item", () -> {
            return mutate("DEACTIVATE_ITEM", body);
        }, saved -> {
            refresh();
            dialog.setBusy(false);
            dialog.dispose();
        }, failure -> {
            dialog.setBusy(false);
            catalogError(dialog, "The item was not deactivated", failure);
        });
    }

    private void variantsDialog(Window owner, Long parentId) {
        if (state == null || parentId == null) return;
        state.items().stream().filter(item -> item.itemId() == parentId).findFirst()
                .ifPresent(parent -> new VariantList(owner, parent).show());
    }

    private final class VariantList {
        private final long itemId;
        private final JDialog dialog;
        private final CustomVariantGrid table;
        private final JLabel count = new JLabel();
        private final JLabel status = new JLabel("Edit a cell, then press Enter or leave it to save. ID and SKU are read-only.");
        private final JButton add, edit, expand, refreshButton, phone;
        private LanCustomOrderCatalogAdminService.Item parent;
        private boolean busy, stale;

        VariantList(Window owner, LanCustomOrderCatalogAdminService.Item item) {
            parent = item;
            itemId = item.itemId();
            dialog = new JDialog(owner, "Sizes / Variants – " + item.name(), Dialog.ModalityType.DOCUMENT_MODAL);
            dialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent event) {
                    if (finishEditing()) dialog.dispose();
                }
            });
            table = new CustomVariantGrid(dialog, item, this::saveCell);
            add = addButton("Add Variant", () -> {
                if (finishEditing()) new VariantEditor(dialog, parent, null, this::reload).show();
            });
            edit = button("Edit Variant", () -> {
                if (finishEditing() && table.selectedVariant() != null) {
                    new VariantEditor(dialog, parent, table.selectedVariant(), this::reload).show();
                }
            });
            expand = fullScreen(dialog, this::finishEditing, full -> {
                table.setFullScreen(full);
                updateActions();
            });
            refreshButton = button("Refresh Variants", () -> {
                if (finishEditing()) reloadFromServer("Variants refreshed.");
            });
            phone = button("Add Phone Photo", () -> {
                if (!finishEditing() || table.selectedVariant() == null) return;
                showPhonePhoto("variant", table.selectedVariant().variantId(), dialog, this::setBusy, loaded -> reload());
            });
            table.getSelectionModel().addListSelectionListener(event -> updateActions());
            installActivation();
            build();
            reload();
        }

        private boolean finishEditing() {
            if (busy || table.hasPendingDimensionEdit()) return false;
            if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return false;
            return !busy && !table.hasPendingDimensionEdit();
        }

        private void installActivation() {
            table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "activateVariant");
            table.getActionMap().put("activateVariant", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (table.isEditing()) {
                        table.getCellEditor().stopCellEditing();
                    } else if (table.editCellAt(table.getSelectedRow(), table.getSelectedColumn())
                            && table.getEditorComponent() != null) {
                        table.getEditorComponent().requestFocusInWindow();
                    }
                }
            });
        }

        private void build() {
            JPanel root = surface(new BorderLayout(0, 10));
            root.setBorder(new EmptyBorder(12, 12, 12, 12));
            JPanel header = surface(new BorderLayout(0, 8));
            JPanel banner = surface(new FlowLayout(FlowLayout.LEFT, 12, 3));
            banner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.ORANGE));
            banner.add(logo(135, 34));
            header.add(banner, BorderLayout.NORTH);
            header.add(heading("Variants", "Item variants, sizes, colours and SKUs"), BorderLayout.CENTER);
            JPanel actions = surface(new FlowLayout(FlowLayout.LEFT, 6, 0));
            for (JButton action : List.of(add, edit, expand, phone, refreshButton)) actions.add(action);
            JPanel controls = surface(new BorderLayout(0, 8));
            controls.add(actions, BorderLayout.NORTH);
            JLabel parentInfo = DeckersSwing.metaLabel("Parent item: " + parent.name()
                    + "     •     Item Type: " + java.util.Objects.toString(parent.itemType(), "—") + " (inherited)");
            DeckersSwing.styleBand(parentInfo, DeckersPalette.PURPLE, new Insets(6, 9, 6, 9));
            controls.add(parentInfo, BorderLayout.SOUTH);
            header.add(controls, BorderLayout.SOUTH);
            JPanel footer = surface(new BorderLayout(12, 0));
            footer.add(count, BorderLayout.WEST);
            footer.add(status);
            root.add(header, BorderLayout.NORTH);
            root.add(scroll(table));
            root.add(footer, BorderLayout.SOUTH);
            dialog.setContentPane(root);
            resizeDialog(dialog, 1120, 660);
        }

        void show() {
            open(dialog);
            refresh();
        }

        private void reload() {
            var updated = state.items().stream().filter(item -> item.itemId() == itemId).findFirst().orElse(null);
            stale = updated == null;
            if (updated != null) {
                parent = updated;
                table.reload(parent);
            }
            int size = table.getRowCount();
            count.setText(size == 1 ? "1 variant" : size + " variants");
            updateActions();
        }

        private void setBusy(boolean value) {
            busy = value;
            updateActions();
        }

        private void updateActions() {
            boolean selected = table.getSelectedRow() >= 0;
            add.setEnabled(!busy && !stale);
            edit.setEnabled(!busy && !stale && selected);
            expand.setEnabled(!busy);
            refreshButton.setVisible(table.isFullScreen() || stale);
            refreshButton.setEnabled(!busy);
            phone.setVisible(table.isFullScreen());
            phone.setEnabled(!busy && !stale && selected);
            table.setEditable(!busy && !stale);
        }

        private void saveCell(LanCustomOrderCatalogAdminService.Variant before, JsonObject changes) {
            if (busy || stale) return;
            JsonObject original = GSON.toJsonTree(before).getAsJsonObject();
            JsonObject expected = new JsonObject();
            for (String key : changes.keySet()) {
                expected.add(key, original.has(key) ? original.get(key) : com.google.gson.JsonNull.INSTANCE);
            }
            if (expected.equals(changes)) return;
            setBusy(true);
            status.setText("Saving " + before.name() + "…");
            var naming = new ProductImageHelper.ProductImageNaming(parent.name(), parent.brand(), parent.itemType(), "", before.name());
            UiTaskRunner.submit(dialog, "custom-catalog.inline-variant", () -> {
                if (changes.has("imageUrl")) {
                    String url = ProductImageHelper.uploadLocalImageIfNeeded(changes.get("imageUrl").getAsString(), naming, "CUSTOM_VARIANT");
                    var images = changes.getAsJsonArray("additionalImageUrls").asList().stream().map(value -> value.getAsString()).toList();
                    changes.addProperty("imageUrl", url);
                    changes.add("additionalImageUrls", GSON.toJsonTree(ProductImageHelper.uploadAdditionalImages(images, naming, "CUSTOM_VARIANT")));
                }
                JsonObject payload = new JsonObject();
                payload.addProperty("itemId", itemId);
                payload.addProperty("variantId", before.variantId());
                payload.add("expected", wireCells(expected));
                payload.add("changes", wireCells(changes));
                return mutate("EDIT_VARIANT_CELLS", payload);
            }, saved -> reloadFromServer("Saved " + before.name() + "."), failure -> {
                catalogError(dialog, "Could not confirm the cell was saved", failure);
                reloadFromServer("Variant values refreshed.");
            });
        }

        private void reloadFromServer(String message) {
            setBusy(true);
            UiTaskRunner.submit(dialog, "custom-catalog.inline-variant-refresh", catalog::load, loaded -> {
                SessionDataCache.put("custom-catalog:admin", loaded);
                applyCatalogState(loaded);
                reload();
                status.setText(stale ? "This item is no longer available." : message);
                setBusy(false);
            }, failure -> {
                stale = true;
                status.setText("Could not refresh variants. Use Refresh Variants before editing again.");
                setBusy(false);
            });
        }
    }

    // JSON array elements preserve explicit nulls through the LAN codec.
    static JsonObject wireCells(JsonObject cells) {
        JsonObject wire = new JsonObject();
        cells.entrySet().forEach(entry -> {
            var value = new com.google.gson.JsonArray();
            value.add(entry.getValue());
            wire.add(entry.getKey(), value);
        });
        return wire;
    }

    private final class VariantEditor {
        private final LanCustomOrderCatalogAdminService.Item parent;
        private final Editor dialog;
        private final Runnable reload;
        private final JTextField variantName = new JTextField(), variantSize = new JTextField(),
                variantColor = new JTextField(), variantBrand = new JTextField(), barcode = new JTextField(),
                price = new JTextField(), quantity = new JTextField("0"), reorder = new JTextField("0"),
                templateWidth = new JTextField(), templateHeight = new JTextField();
        private final JTextArea extraBarcodes = new JTextArea(3, 20);
        private final JComboBox<String> templateUnit = new JComboBox<>(new String[]{"IN","FT","YD","CM","M"});
        private final JRadioButton inheritSizing = new JRadioButton("Inherit item setting", true);
        private final JRadioButton orderSizing = new JRadioButton("Use order measurements");
        private final JRadioButton templateSizing = new JRadioButton("Use template dimensions");
        private final JCheckBox active = new JCheckBox("Active", true);
        private final ProductImageHelper.ImageSelector image;
        private final JButton save, deactivate, photo;
        private Long variantId;

        VariantEditor(Window owner, LanCustomOrderCatalogAdminService.Item parent,
                      LanCustomOrderCatalogAdminService.Variant variant, Runnable reload) {
            this.parent = parent;
            this.reload = reload;
            dialog = new Editor(owner, variant == null ? addTitle() : "Edit Variant – " + variant.name());
            image = ProductImageHelper.createSimpleImageSelector(dialog);
            ButtonGroup sizingOptions = new ButtonGroup();
            for (JRadioButton choice : List.of(inheritSizing, orderSizing, templateSizing)) sizingOptions.add(choice);
            if (variant != null) load(variant);

            save = button("Save Variant", this::save);
            deactivate = button("Deactivate", this::deactivate);
            photo = button("Add Phone Photo", this::phonePhoto);
            JButton clear = button("Clear", () -> {
                if (dialog.confirmDiscard()) reset();
            });
            dialog.layoutEditor(save, sections(), true, clear, deactivate, photo, fullScreen(dialog));
            dialog.watchDraft(this::draft);
            variantName.getDocument().addDocumentListener(listener(this::updateActions));
            updateActions();
        }

        private String addTitle() { return "Add Variant – " + parent.name(); }

        void show() {
            SwingUtilities.invokeLater(variantName::requestFocusInWindow);
            open(dialog);
        }

        private Section[] sections() {
            JPanel identity = fields();
            row(identity,0,"Name",variantName);
            row(identity,1,"Size",variantSize);
            row(identity,2,"Color",variantColor);
            row(identity,3,"Brand (blank inherits item)",variantBrand);
            variantBrand.putClientProperty("JTextField.placeholderText", parent.brand());
            JPanel identifiers = fields();
            row(identifiers,0,"Barcode",BarcodeGenerationHelper.field(dialog, barcode));
            extraBarcodes.setFont(barcode.getFont());
            row(identifiers,1,"More barcodes",BarcodeGenerationHelper.area(dialog, extraBarcodes, multiline(extraBarcodes, 46)));
            row(identifiers,2,"Price",price);
            JPanel stock = fields();
            row(stock,0,"Quantity",quantity);
            row(stock,1,"Reorder",reorder);
            JPanel status = fields();
            row(status,0,"",active);

            JPanel template = fields();
            row(template,0,"Template width",templateWidth);
            row(template,1,"Template height",templateHeight);
            row(template,2,"Template unit",templateUnit);
            JPanel sizing = new JPanel(new GridLayout(0, 1, 0, 2));
            sizing.setOpaque(false);
            sizing.add(new JLabel("Area design size"));
            for (JRadioButton choice : List.of(inheritSizing, orderSizing, templateSizing)) {
                choice.setOpaque(false);
                sizing.add(choice);
            }
            row(template,3,"",new JLabel("<html>Blank dimensions inherit<br>from the parent item.</html>"));
            return new Section[]{
                    new Section("Details", "Variant Details", columns(identity, identifiers), null),
                    new Section("Template & photos", "Template / Design", columns(template, sizing), topAligned(image)),
                    new Section("Pricing & stock", columns(stock, status))
            };
        }

        private void load(LanCustomOrderCatalogAdminService.Variant variant) {
            variantId = variant.variantId();
            variantName.setText(variant.name());
            variantSize.setText(variant.size());
            variantColor.setText(variant.color());
            variantBrand.setText(variant.brand());
            barcode.setText(variant.barcode());
            extraBarcodes.setText(String.join("\n", variant.extraBarcodes()));
            price.setText(money(variant.fixedPrice()));
            quantity.setText(money(variant.quantity()));
            reorder.setText(money(variant.reorderLevel()));
            templateWidth.setText(money(variant.templateWidth()));
            templateHeight.setText(money(variant.templateHeight()));
            templateUnit.setSelectedItem(variant.templateUnit() == null ? "IN" : variant.templateUnit());
            if (variant.templateUsesOrderArea() == null) inheritSizing.setSelected(true);
            else if (variant.templateUsesOrderArea()) orderSizing.setSelected(true);
            else templateSizing.setSelected(true);
            image.setImageUrl(variant.imageUrl());
            image.setAdditionalImageUrls(variant.additionalImageUrls());
            active.setSelected(variant.active());
        }

        private void reset() {
            variantId = null;
            for (JTextField field : List.of(variantName, variantSize, variantColor, variantBrand, barcode,
                    price, templateWidth, templateHeight)) field.setText("");
            extraBarcodes.setText("");
            templateUnit.setSelectedItem("IN");
            inheritSizing.setSelected(true);
            quantity.setText("0");
            reorder.setText("0");
            image.setImageUrl("");
            image.setAdditionalImageUrls(List.of());
            active.setSelected(true);
            dialog.rename(addTitle());
            dialog.markSaved();
            updateActions();
            dialog.showDetails();
            variantName.requestFocusInWindow();
        }

        private List<?> draft() {
            return java.util.Arrays.asList(variantName.getText(), variantSize.getText(), variantColor.getText(), variantBrand.getText(),
                    barcode.getText(), extraBarcodes.getText(), price.getText(), quantity.getText(), reorder.getText(),
                    templateWidth.getText(), templateHeight.getText(), templateUnit.getSelectedIndex(),
                    areaSizing(), active.isSelected(), image.getImageUrl(), image.getAdditionalImageUrls());
        }

        private Boolean areaSizing() { return inheritSizing.isSelected() ? null : orderSizing.isSelected(); }

        private void phonePhoto() {
            Long target = variantId;
            var previous = state.items().stream().filter(item -> item.itemId() == parent.itemId())
                    .flatMap(item -> item.variants().stream()).filter(value -> java.util.Objects.equals(value.variantId(), target))
                    .findFirst().orElse(null);
            if (previous == null) return;
            showPhonePhoto("variant", target, dialog, loaded -> loaded.items().stream()
                    .filter(item -> item.itemId() == parent.itemId()).flatMap(item -> item.variants().stream())
                    .filter(value -> value.variantId() == target && java.util.Objects.equals(target, variantId)).findFirst()
                    .ifPresent(updated -> dialog.receiveSavedChanges(() -> mergePhonePhoto(image, previous.imageUrl(),
                            previous.additionalImageUrls(), updated.imageUrl(), updated.additionalImageUrls()))));
        }

        private void updateActions() {
            save.setEnabled(!variantName.getText().isBlank());
            deactivate.setEnabled(variantId != null && active.isSelected());
            photo.setEnabled(variantId != null);
        }

        private void save() {
            try {
                Long id = variantId;
                String variantName = this.variantName.getText(), size = variantSize.getText(), color = variantColor.getText(),
                        brand = variantBrand.getText(), primaryBarcode = barcode.getText(), sourceImage = image.getImageUrl();
                List<String> sourceExtras = image.getAdditionalImageUrls();
                List<String> extraCodes = java.util.Arrays.stream(extraBarcodes.getText().split("\\R"))
                        .filter(code -> !code.isBlank()).distinct().toList();
                BigDecimal amount = decimal(price, "price", false), qty = decimal(quantity, "quantity", true),
                        reorderLevel = decimal(reorder, "reorder level", true),
                        width = decimal(templateWidth, "template width", false), height = decimal(templateHeight, "template height", false);
                boolean enabled = active.isSelected();
                String unit = width == null ? null : (String) templateUnit.getSelectedItem();
                Boolean useArea = areaSizing();
                var parentItem = parent;
                var naming = new ProductImageHelper.ProductImageNaming(parentItem.name(), parentItem.brand(),
                        parentItem.itemType(),"",variantName);
                dialog.setBusy(true);
                UiTaskRunner.submit(dialog, "custom-catalog.save-variant", () -> {
                    String url = ProductImageHelper.uploadLocalImageIfNeeded(sourceImage, naming, "CUSTOM_VARIANT");
                    List<String> extras = ProductImageHelper.uploadAdditionalImages(sourceExtras, naming, "CUSTOM_VARIANT");
                    var request = new LanCustomOrderCatalogAdminService.VariantSave(id, parent.itemId(), variantName,
                            size, color, brand, primaryBarcode, url, amount, qty, reorderLevel, enabled, extraCodes,
                            extras, width, height, unit, useArea);
                    return mutate("SAVE_VARIANT", "variant", request);
                }, saved -> reloadAfterSaved(dialog, () -> {
                    reload.run();
                    dialog.setBusy(false);
                    reset();
                    JOptionPane.showMessageDialog(dialog, "Variant saved. You can add another variant now.");
                }), failure -> failed("Variant was not saved", failure));
            } catch (Exception failure) {
                catalogError(dialog, "Variant was not saved", failure);
            }
        }

        private void deactivate() {
            if (variantId == null || !dialog.confirmDiscard()) return;
            if (JOptionPane.showConfirmDialog(dialog, "Deactivate this variant?", "Confirm",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            JsonObject body = new JsonObject();
            body.addProperty("variantId", variantId);
            dialog.setBusy(true);
            UiTaskRunner.submit(dialog, "custom-catalog.deactivate-variant",
                    () -> mutate("DEACTIVATE_VARIANT", body), saved -> reloadAfterSaved(dialog, () -> {
                        reload.run();
                        dialog.setBusy(false);
                        dialog.dispose();
                    }), failure -> failed("The variant was not deactivated", failure));
        }

        private void failed(String title, Throwable failure) {
            dialog.setBusy(false);
            catalogError(dialog, title, failure);
        }
    }
    private void selectMaterial(){materialId=selectedId(materialTable,materialModel);if(materialId==null)return;var x=state.materials().stream().filter(v->v.materialId()==materialId).findFirst().orElse(null);if(x!=null){materialName.setText(x.name());materialDescription.setText(x.description());materialColors.setText(services.PrintMaterialColors.format(x.availableColors()));materialActive.setSelected(x.active());clearPreset();renderPresets(materialId);}}
    private void saveMaterial(){try{var request=new LanCustomOrderCatalogAdminService.MaterialSave(materialId,materialName.getText(),materialDescription.getText(),materialActive.isSelected(),services.PrintMaterialColors.parse(materialColors.getText()));mutateAsync("save-material","SAVE_MATERIAL","material",request,id->{materialId=id;refresh();info("Print material saved.");},"Print material was not saved");}catch(Exception e){error("Print material was not saved",e);}}
    private void clearMaterial(){materialId=null;materialTable.clearSelection();materialName.setText("");materialDescription.setText("");materialColors.setText("");materialActive.setSelected(true);clearPreset();renderPresets(null);}
    private void selectPreset(){presetId=selectedId(presetTable,presetModel);if(presetId==null)return;var x=state.presets().stream().filter(v->v.presetId()==presetId).findFirst().orElse(null);if(x!=null){presetName.setText(x.name());presetMode.setSelectedItem(x.pricingMode());presetPrice.setText(money(x.price()));presetWidth.setText(x.widthIn()==null?"":x.widthIn().stripTrailingZeros().toPlainString());presetHeight.setText(x.heightIn()==null?"":x.heightIn().stripTrailingZeros().toPlainString());presetActive.setSelected(x.active());}}
    private void savePreset(){try{if(materialId==null)throw new IllegalStateException("Select a print material first.");Long selectedMaterial=materialId;var request=new LanCustomOrderCatalogAdminService.PresetSave(presetId,selectedMaterial,presetName.getText(),(String)presetMode.getSelectedItem(),decimal(presetPrice,"price",true),presetActive.isSelected(),decimal(presetWidth,"guide width",false),decimal(presetHeight,"guide height",false));mutateAsync("save-preset","SAVE_PRESET","preset",request,id->{presetId=id;refresh();info("Print preset saved.");},"Print preset was not saved");}catch(Exception e){error("Print preset was not saved",e);}}
    private void clearPreset(){presetId=null;presetTable.clearSelection();presetName.setText("");presetPrice.setText("");presetWidth.setText("");presetHeight.setText("");presetMode.setSelectedItem("FIXED_PRESET");presetActive.setSelected(true);}
    private void selectPlacement(){placementId=selectedId(placementTable,placementModel);if(placementId==null)return;var x=state.placements().stream().filter(v->v.placementId()==placementId).findFirst().orElse(null);if(x!=null){placementName.setText(x.name());placementOrder.setText(String.valueOf(x.sortOrder()));placementActive.setSelected(x.active());}}
    private void savePlacement(){try{var request=new LanCustomOrderCatalogAdminService.PlacementSave(placementId,placementName.getText(),integer(placementOrder,"sort order"),placementActive.isSelected());mutateAsync("save-placement","SAVE_PLACEMENT","placement",request,id->{placementId=id;refresh();info("Design placement saved.");},"Design placement was not saved");}catch(Exception e){error("Design placement was not saved",e);}}
    private void clearPlacement(){placementId=null;placementTable.clearSelection();placementName.setText("");placementOrder.setText("0");placementActive.setSelected(true);}
    private void deactivate(String action,String field,Long id,String label){if(id==null){info("Select a "+label+" first.");return;}if(JOptionPane.showConfirmDialog(this,"Deactivate this "+label+"?","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;JsonObject body=new JsonObject();body.addProperty(field,id);UiTaskRunner.submit(this,"custom-catalog.deactivate-"+label,()->LanApiClient.updateCustomCatalogAdmin(action,body,UUID.randomUUID().toString()),ignored->refresh(),failure->error("The "+label+" was not deactivated",asException(failure)));}
    private void mutateAsync(String key,String action,String field,Object value,Consumer<Long> success,String failureMessage){UiTaskRunner.submit(this,"custom-catalog."+key,()->mutate(action,field,value),success,failure->error(failureMessage,asException(failure)));}
    private long mutate(String action,String field,Object value)throws Exception{JsonObject b=new JsonObject();b.add(field,GSON.toJsonTree(value));return mutate(action,b);}
    private long mutate(String action,JsonObject body)throws Exception{long saved=catalog.update(action,body,UUID.randomUUID().toString());SessionDataCache.invalidate("custom-catalog:admin");SessionDataCache.invalidate("custom-orders:catalog");SessionDataCache.invalidate("custom-orders:variants:");SessionDataCache.invalidate("custom-orders:presets:");return saved;}

    private static void mergePhonePhoto(ProductImageHelper.ImageSelector selector, String previousPrimary,
                                       List<String> previousPhotos, String savedPrimary, List<String> savedPhotos) {
        String primary = selector.getImageUrl();
        List<String> current = selector.getAdditionalImageUrls();
        if (primary.equals(java.util.Objects.toString(previousPrimary, "")) && current.equals(previousPhotos)) {
            selector.setImageUrl(savedPrimary);
            selector.setAdditionalImageUrls(savedPhotos);
            return;
        }
        var combined = new LinkedHashSet<>(current);
        if (savedPrimary != null && !savedPrimary.isBlank() && !savedPrimary.equals(previousPrimary)) combined.add(savedPrimary);
        for (String photo : savedPhotos) if (!previousPhotos.contains(photo)) combined.add(photo);
        combined.remove(primary);
        selector.setAdditionalImageUrls(List.copyOf(combined));
    }
    private void showPhonePhoto(String targetType, Long targetId, Editor owner,
                                Consumer<LanCustomOrderCatalogAdminService.State> onPhoto) {
        showPhonePhoto(targetType, targetId, owner, owner::setBusy, onPhoto);
    }

    private void showPhonePhoto(String targetType, Long targetId, Window owner, Consumer<Boolean> busy,
                                Consumer<LanCustomOrderCatalogAdminService.State> onPhoto) {
        if (targetId == null) return;
        busy.accept(true);
        UiTaskRunner.submit(owner, "custom-catalog.phone-photo-create",
                () -> catalog.createPhonePhoto(targetType, targetId), handoff -> {
                    busy.accept(false);
                    showPhonePhotoDialog(owner, handoff, onPhoto);
                }, failure -> {
                    busy.accept(false);
                    catalogError(owner, "The phone photo link could not be created", failure);
                });
    }

    private void showPhonePhotoDialog(Window owner, LanApiClient.MobileItemPhotoHandoff handoff,
                                      Consumer<LanCustomOrderCatalogAdminService.State> onPhoto) {
        JDialog dialog = new JDialog(owner, "Photo from Phone", Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        JLabel heading = new JLabel("Scan with your phone", SwingConstants.CENTER);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(new JLabel(new ImageIcon(qrCode(handoff.url(), 330)), SwingConstants.CENTER));
        JLabel status = new JLabel("<html><center>Photo for <b>" + html(handoff.targetName())
                + "</b><br>This link expires in 10 minutes.</center></html>", SwingConstants.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        resizeDialog(dialog, 440, 500);

        Timer timer = new Timer(1800, null);
        boolean[] checking = {false};
        timer.addActionListener(event -> {
            if (checking[0] || !dialog.isDisplayable()) return;
            checking[0] = true;
            UiTaskRunner.submit(dialog, "custom-catalog.phone-photo-status",
                    () -> catalog.phonePhotoStatus(handoff.handoffId()), result -> {
                        checking[0] = false;
                        if (result.uploaded()) {
                            timer.stop();
                            status.setText("Photo uploaded. Refreshing the gallery…");
                            SessionDataCache.invalidate("custom-catalog:");
                            reloadPhonePhoto(owner, onPhoto, status);
                        } else if (result.expired()) {
                            timer.stop();
                            status.setText("This link has expired. Close this window to start again.");
                        }
                    }, failure -> checking[0] = false);
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { timer.stop(); }
        });
        timer.start();
        open(dialog);
    }

    private void reloadPhonePhoto(Window owner, Consumer<LanCustomOrderCatalogAdminService.State> onPhoto, JLabel status) {
        UiTaskRunner.submit(owner, "custom-catalog.phone-photo-refresh", catalog::load, loaded -> {
            SessionDataCache.put("custom-catalog:admin", loaded);
            applyCatalogState(loaded);
            onPhoto.accept(loaded);
            status.setText("Photo uploaded successfully. You may close this window.");
        }, failure -> status.setText("Photo uploaded. Refresh the catalog to load it."));
    }
    private static java.awt.image.BufferedImage qrCode(String value,int size){try{var matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(value,com.google.zxing.BarcodeFormat.QR_CODE,size,size);var image=new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int y=0;y<size;y++)for(int x=0;x<size;x++)image.setRGB(x,y,matrix.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB());return image;}catch(Exception e){throw new IllegalArgumentException("The phone photo QR code could not be created.",e);}}
    private static void catalogError(Component owner, String title, Throwable failure) {
        String message = failure.getMessage();
        JOptionPane.showMessageDialog(owner, title + ".\n\n" + (message == null ? failure.getClass().getSimpleName() : message),
                "Custom Order Catalog", JOptionPane.ERROR_MESSAGE);
    }
    private static String html(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static Exception asException(Throwable failure){return failure instanceof Exception exception?exception:new Exception(failure);}

    private static DefaultTableModel model(String...columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private static JPanel form(){JPanel p=new JPanel(new GridBagLayout());p.setBorder(new EmptyBorder(8,8,8,8));return p;}

    private static void row(JPanel panel, int index, String text, Component field) {
        GridBagConstraints position = new GridBagConstraints();
        position.insets = new Insets(4, 4, 4, 4);
        position.gridy = index;
        position.gridx = 0;
        position.anchor = GridBagConstraints.WEST;
        if (!text.isBlank()) {
            JLabel label = new JLabel(text);
            label.setLabelFor(field);
            panel.add(label, position);
        }
        position.gridx = 1;
        position.weightx = 1;
        position.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, position);
    }
    private static JPanel buttons(Runnable save,Runnable clear,Runnable off){JPanel p=new JPanel(new GridLayout(1,3,6,6));JButton s=new JButton("Save"),c=new JButton("Clear"),d=new JButton("Deactivate");s.addActionListener(e->save.run());c.addActionListener(e->clear.run());d.addActionListener(e->off.run());p.add(s);p.add(c);p.add(d);return p;}
    private static Long selectedId(JTable t,DefaultTableModel m){int r=t.getSelectedRow();if(r<0)return null;r=t.convertRowIndexToModel(r);return Long.valueOf(m.getValueAt(r,0).toString());}private static void selectRow(JTable t,DefaultTableModel m,Long id){if(id==null)return;for(int r=0;r<m.getRowCount();r++)if(id.toString().equals(String.valueOf(m.getValueAt(r,0)))){int v=t.convertRowIndexToView(r);if(v>=0)t.setRowSelectionInterval(v,v);return;}}
    private static String money(BigDecimal v){return v==null?"":v.stripTrailingZeros().toPlainString();}private static BigDecimal decimal(JTextField f,String label,boolean required){String v=f.getText().trim();if(v.isBlank()){if(required)throw new IllegalArgumentException("Enter "+label+".");return null;}try{return new BigDecimal(v);}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+".");}}private static int integer(JTextField f,String label){try{return Integer.parseInt(f.getText().trim());}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+".");}}
    private static DocumentListener listener(Runnable r){return new DocumentListener(){public void insertUpdate(DocumentEvent e){r.run();}public void removeUpdate(DocumentEvent e){r.run();}public void changedUpdate(DocumentEvent e){r.run();}};}private void info(String m){JOptionPane.showMessageDialog(this,m);}private void error(String m,Exception e){JOptionPane.showMessageDialog(this,m+".\n\n"+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),"Custom Order Catalog",JOptionPane.ERROR_MESSAGE);}
    private record ItemMutationResult(long id,String imageUrl,List<String> additionalImageUrls){}
}
