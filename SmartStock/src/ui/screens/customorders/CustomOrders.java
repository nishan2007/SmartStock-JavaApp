package ui.screens.customorders;

import Receipt.CustomOrderLabelPrinter;
import Receipt.CustomOrderSlipBuilder;
import Receipt.CustomOrderSlipData;
import Receipt.CustomOrderSlipPrinter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import managers.PermissionManager;
import managers.SessionManager;
import managers.CompanyCustomizationManager;
import services.CustomOrderDataService;
import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.CustomerOption;
import services.CustomOrderDataService.OrderLineRequest;
import services.CustomOrderDataService.OrderSaveRequest;
import services.CustomOrderDataService.PrintAddonRequest;
import services.CustomOrderDataService.PrintMaterialOption;
import services.CustomOrderDataService.PrintSizePresetOption;
import services.CustomOrderDataService.VariantOption;
import services.EmailOutboxService;
import services.LanApiClient;
import services.LanCustomOrderWorkflowService;
import services.LanJson;
import services.ManagerApprovalService;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;
import ui.screens.CustomOrderSlipPreview;
import utils.CurrencyFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Register-side custom-order workflow. All persistence is performed by the store service. */
public class CustomOrders extends JFrame {
    private static final Gson GSON=LanJson.create();
    private final List<CartLine>lines=new ArrayList<>();
    private final DefaultTableModel myModel=model(CustomOrderListColumns.headings());private final JTable myTable=new JTable(myModel);private final JTextArea myDetails=details();private final JTextField mySearch=new JTextField();private final JComboBox<String>myStatus=new JComboBox<>(new String[]{"All","ASSIGNED","IN_PROGRESS","READY","COMPLETED","DELIVERED","CANCELLED"});private final TableRowSorter<DefaultTableModel>mySorter=new TableRowSorter<>(myModel);
    private CustomOrdersLookupTabPanel lookup, allOrders;
    private JTabbedPane orderTabs;
    private boolean tabsShowingAllOrders(){return orderTabs!=null&&orderTabs.getSelectedComponent()==allOrders;}
    private CustomOrdersNewOrderTabPanel guidedOrder;
    private String selectedPaymentMethod;
    private boolean loadingCatalog;
    private BigDecimal minimumDepositPercent=BigDecimal.ZERO;
    private long customOrderFileLimitBytes=104857600L;
    private boolean roundToNearestTwenty=true;
    private boolean alwaysPrintOrderSlip;
    private Map<Long,List<VariantOption>> variantsByItem=Map.of();
    private Map<Long,List<PrintSizePresetOption>> presetsByMaterial=Map.of();
    private Long pendingVariantSelectionId;
    private int editingLine = -1;
    private String editingSetId;
    private String buildingSetId;
    private boolean buildingSet;
    private TableRowSorter<DefaultTableModel> cartSorter;
    private final List<String> summarySetIds=new ArrayList<>();
    private final CustomOrderSubmission<SaveOutcome> orderSubmission = new CustomOrderSubmission<>();
    private final List<Path> draftFiles = new ArrayList<>();
    private final LoadingStatePanel loadingState=new LoadingStatePanel();
    private final Map<JsonObject,String> mutationKeys=new java.util.IdentityHashMap<>();

    public CustomOrders(){this(false);}
    protected CustomOrders(boolean orderManagementMode){
        setTitle(orderManagementMode?"Orders":"Custom Orders");
        setSize(1320,800);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this,orderManagementMode?"Orders":"CustomOrders"));

        JTabbedPane tabs=new JTabbedPane();
        if(!orderManagementMode&&can("CREATE_CUSTOM_ORDER")){
            tabs.addTab("New Order",newOrderTab());
        }
        if(orderManagementMode){
            tabs.addTab("Order Lookup",lookupTab());
            allOrders = new CustomOrdersLookupTabPanel(handler());
            allOrders.table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(allOrders.model.findColumn("Created"),SortOrder.DESCENDING),new RowSorter.SortKey(0,SortOrder.DESCENDING)));
            tabs.addTab("All Orders",allOrders);
            if(can("VIEW_ASSIGNED_CUSTOM_ORDERS")||can("MANAGE_CUSTOM_ORDERS")){
                tabs.addTab("My Orders",myOrdersTab());
            }
        }

        tabs.addChangeListener(e->{if(allOrders!=null&&tabs.getSelectedComponent()==allOrders)allOrders.load(handler());});
        orderTabs=tabs;
        if(tabs.getTabCount()==0){
            add(new JLabel("You do not have permission to access this screen.",SwingConstants.CENTER));
        }else{
            add(tabs,BorderLayout.CENTER);
        }
        JPanel footer=new JPanel(new BorderLayout());footer.add(loadingState,BorderLayout.CENTER);
        if(can("RECORD_CUSTOM_ORDER_SPOILS")||can("REVERSE_CUSTOM_ORDER_SPOILS")||can("MANAGE_CUSTOM_ORDERS")){
            JButton phone=new JButton("Custom Order Spoils / Phone QR");
            phone.addActionListener(e->new ui.screens.MobileItemWebDialog(this,"spoils=1").setVisible(true));
            JPanel actions=new JPanel();actions.add(phone);
            JButton spoilHistory=new JButton("Internal Spoil History");
            spoilHistory.addActionListener(e->{Long id=tabs.getSelectedComponent()==allOrders&&allOrders!=null?selected(allOrders.table,allOrders.model):tabs.getSelectedComponent()==lookup&&lookup!=null?selected(lookup.table,lookup.model):selected(myTable,myModel);if(id==null){JOptionPane.showMessageDialog(this,"Select a saved order in Order Lookup or My Orders.");return;}CustomOrderSpoilDialog.show(this,id);});
            actions.add(spoilHistory);footer.add(actions,BorderLayout.EAST);
        }
        add(footer,BorderLayout.SOUTH);
        WindowHelper.configurePosWindow(this);
        if(!orderManagementMode){
            loadCatalog();
        }else{
            loadOrders();
        }
    }

    private JPanel newOrderTab(){
        guidedOrder=new CustomOrdersNewOrderTabPanel(new CustomOrdersNewOrderTabPanel.Handler(){
            public void inventoryItem(){selectInventoryItem();}
            public void customerItem(){editCustomerItem();}
            public void pendingPricingChanged() {
                if(!lines.isEmpty()){guidedOrder.pricePendingBox.setSelected(!guidedOrder.pricePendingBox.isSelected());JOptionPane.showMessageDialog(CustomOrders.this,"Choose pending or final pricing before adding items. Clear the order to change pricing mode.");}
                updateTotal();
            }
            public void manualEntry(boolean addon){editManualEntry(addon);}
            public void orderLookup(){lookupOrderItem();}
            public void orderItemSelected(CustomOrderDataService.ItemSearchOption option){selectOrderItem(option.customItemId(),option.customVariantId());}
            public void orderItemChanged(){if(!loadingCatalog){inventoryProductId=inventoryOptions.get(guidedOrder.orderItemBox.getSelectedItem());inventoryProductId=null;customerItem=null;refreshCustomerItem();loadVariants();applyPrice();}}
            public void variantChanged(){if(!loadingCatalog)applyPrice();}
            public void printMaterialChanged(){if(!loadingCatalog)loadPresets();}
            public void printPresetChanged(){applyPrintPresetPrice();}
            public Runnable printLineCountChanged(){return CustomOrders.this::applyPrintPresetPrice;}
            public void addPrintAddon(){CustomOrders.this.addPrintAddon();}
            public void removePrintAddon(){CustomOrders.this.removePrintAddon();}
            public void editPrintAddonText(){CustomOrders.this.editPrintAddonText();}
            public Runnable areaChanged(){return CustomOrders.this::updateAreaPreview;}
            public void addPlacement(){CustomOrders.this.addPlacement();}
            public void addOrderLine(){addLine();}
            public void editOrderLine(){editLine();}
            public void cancelLineEdit(){clearLine();}
            public void selectLineFiles(){CustomOrders.this.selectLineFiles();}
            public void editLineFiles(){CustomOrders.this.editLineFiles();}
            public void removeOrderLine(){removeLine();}
            public void editLineDiscount(){editDiscount();}
            public void cartSelectionChanged(){}
            public void selectPaymentMethod(String method){selectedPaymentMethod=method;updatePaymentPreview();}
            public Runnable upfrontChanged(){return CustomOrders.this::updatePaymentPreview;}
            public boolean canLeaveStep(int step){return validateStep(step);}
            public void enterStep(int step){refreshStep(step);}
            public void saveOrder(boolean printOrderSlip){CustomOrders.this.saveOrder(printOrderSlip);}
            public void clearOrder(){CustomOrders.this.clearOrder();}
            public void holdCart(){CustomOrders.this.holdCart();}
            public void resumeCart(){CustomOrders.this.resumeHeldCart();}
        });
        guidedOrder.sizeSetEditor.onChanged(() -> {
            boolean set = !guidedOrder.sizeSetEditor.selections().isEmpty();
            guidedOrder.lineQuantityField.setEnabled(!set && editingLine < 0);
            guidedOrder.linePriceField.setEnabled(!set);
        });
        cartSorter = new TableRowSorter<>(guidedOrder.orderLineModel);
        for (int col=0; col<guidedOrder.orderLineModel.getColumnCount(); col++) cartSorter.setSortable(col,false);
        guidedOrder.orderLineTable.setRowSorter(cartSorter);
        guidedOrder.orderLineTable.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){
                if(e.getClickCount()!=2)return;
                int view=guidedOrder.orderLineTable.rowAtPoint(e.getPoint());if(view<0)return;
                guidedOrder.orderLineTable.setRowSelectionInterval(view,view);editLine();
            }
        });
        guidedOrder.quantitySummaryList.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){
                if(e.getClickCount()!=2)return;int row=guidedOrder.quantitySummaryList.locationToIndex(e.getPoint());
                if(row<0||row>=summarySetIds.size())return;String id=summarySetIds.get(row);
                if(id!=null){rebuildCartDisplay();guidedOrder.showCartLines();
                    for(int i=0;i<lines.size();i++)if(id.equals(lines.get(i).setId())){int view=guidedOrder.orderLineTable.convertRowIndexToView(i);guidedOrder.orderLineTable.setRowSelectionInterval(view,view);break;}
                }
            }
        });
        return guidedOrder;
    }
    private JPanel lookupTab(){lookup=new CustomOrdersLookupTabPanel(handler());return lookup;}
    private JPanel myOrdersTab(){DefaultTableModel m=myModel;JTable t=myTable;JTextArea d=myDetails;JTextField s=mySearch;JComboBox<String>status=myStatus;TableRowSorter<DefaultTableModel>sorter=mySorter;CustomOrderListColumns.installDateRenderers(t);CustomOrderListColumns.installSorting(t,sorter);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);t.setRowHeight(26);t.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){Long id=selected(t,m);if(id!=null)loadDetails(id,d);}});Runnable filter=()->filter(sorter,s,status);s.getDocument().addDocumentListener(listener(filter));status.addActionListener(e->filter.run());JButton refresh=new JButton("Refresh");refresh.addActionListener(e->loadOrders());JPanel top=new JPanel(new BorderLayout(8,0));top.add(s);JPanel controls=new JPanel();controls.add(status);controls.add(refresh);if(can("COMPANY_PREFERENCES")){JButton portfolio=new JButton("Add to Made at Deckers");portfolio.addActionListener(e->addSelectedOrderToPortfolio());controls.add(portfolio);}top.add(controls,BorderLayout.EAST);JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(t),new JScrollPane(d));split.setResizeWeight(.7);JPanel p=new JPanel(new BorderLayout(8,8));p.setBorder(new EmptyBorder(12,12,12,12));p.add(top,BorderLayout.NORTH);p.add(split);return p;}
    private void addSelectedOrderToPortfolio(){
        Long id=selected(myTable,myModel);if(id==null){JOptionPane.showMessageDialog(this,"Select a completed order first.");return;}
        int row=myTable.convertRowIndexToModel(myTable.getSelectedRow());String state=String.valueOf(myModel.getValueAt(row,2));
        if(!"COMPLETED".equals(state)&&!"DELIVERED".equals(state)){JOptionPane.showMessageDialog(this,"Complete the custom order before creating a project draft.");return;}
        JsonObject request=new JsonObject();request.addProperty("action","PROJECT_DRAFT");request.addProperty("sourceOrderId",id);
        UiTaskRunner.submit(this,"custom-orders.project-draft",()->LanApiClient.storefrontAdmin(request,UUID.randomUUID().toString()),
            result->JOptionPane.showMessageDialog(this,"Private project draft created. Add approved details and photos in Company Preferences → Made at Deckers."),
            failure->JOptionPane.showMessageDialog(this,message(failure),"Made at Deckers",JOptionPane.ERROR_MESSAGE));
    }

    private services.CustomerSuppliedItem customerItem;
    private Integer inventoryProductId;
    private final java.util.Map<CustomItemOption,Integer> inventoryOptions = new java.util.HashMap<>();
    private void selectInventoryItem() {
        CustomOrderInventorySearch.show(this,product->{
            customerItem=null;
            CustomItemOption item=new CustomItemOption(null,product.name(),product.size(),product.color(),product.sku(),"INVENTORY","FIXED",product.price(),false,null,null,null,null,null);
            inventoryOptions.put(item,product.productId());
            loadingCatalog=true;
            try { guidedOrder.orderItemBox.addItem(item);guidedOrder.orderItemBox.setSelectedItem(item); }
            finally { loadingCatalog=false; }
            inventoryProductId=product.productId();
            guidedOrder.variantBox.removeAllItems();
            guidedOrder.itemLookupField.setText("");
            guidedOrder.priceOverrideReasonField.setText("");
            refreshCustomerItem();applyPrice();
        });
    }


    private void editCustomerItem() {
        JTextField type = new JTextField(customerItem == null ? "" : customerItem.itemType(), 22);
        JTextField color = new JTextField(customerItem == null ? "" : customerItem.color(), 22);
        JTextField size = new JTextField(customerItem == null ? "" : customerItem.size(), 22);
        JTextField brand = new JTextField(customerItem == null ? "" : customerItem.brand(), 22);
        type.setToolTipText("For example: Shirt, Book, Cap");
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        form.setBackground(ui.design.DeckersPalette.surface());
        String[] labels = {"Item Type * (Shirt, Book, etc.)", "Color (optional)", "Size (optional)", "Brand (optional)"};
        JTextField[] fields = {type, color, size, brand};
        for (int i = 0; i < fields.length; i++) {
            JLabel label = new JLabel(labels[i]);
            label.setForeground(ui.design.DeckersPalette.text());
            fields[i].setBackground(ui.design.DeckersPalette.fieldBackground());
            fields[i].setForeground(ui.design.DeckersPalette.text());
            form.add(label); form.add(fields[i]);
        }
        while (JOptionPane.showOptionDialog(this, form, "Customer Item", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new String[]{"Continue", "Cancel"}, "Continue") == 0) {
            try {
                var description = new services.CustomerSuppliedItem(type.getText(), color.getText(), size.getText(), brand.getText());
                loadingCatalog = true;
                try { guidedOrder.orderItemBox.setSelectedItem(null); guidedOrder.variantBox.removeAllItems(); }
                finally { loadingCatalog = false; }
                inventoryProductId=null;
                customerItem = description;
                refreshCustomerItem(); applyPrice();
                return;
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
                type.requestFocusInWindow();
            }
        }
    }

    private void editManualEntry(boolean addon) {
        if (!can("MANUAL_CUSTOM_ORDER_ENTRY")) { error(new IllegalArgumentException("Manual custom order entry permission is required.")); return; }
        JTextField name = new JTextField(22), color = new JTextField(22), size = new JTextField(22), price = new JTextField("0",22);
        JPanel form = new JPanel(new GridLayout(0,2,8,8));
        form.setBackground(ui.design.DeckersPalette.surface());
        String[] labels = {"Name *", "Color", "Size", "Price *"};
        JTextField[] fields = {name,color,size,price};
        for (int i=0;i<fields.length;i++) {
            JLabel label = new JLabel(labels[i]); label.setForeground(ui.design.DeckersPalette.text());
            fields[i].setBackground(ui.design.DeckersPalette.fieldBackground()); fields[i].setForeground(ui.design.DeckersPalette.text());
            form.add(label); form.add(fields[i]);
        }
        while (JOptionPane.showConfirmDialog(this,form,addon?"Manual Print Material / Add-on":"Manual Custom Item",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION) {
            try {
                if (name.getText().isBlank() || name.getText().trim().length()>200) throw new IllegalArgumentException("Enter a name of up to 200 characters.");
                BigDecimal amount=wholeDollar(price,"price",true);
                if (amount.signum()<0) throw new IllegalArgumentException("Price cannot be negative.");
                String details="Manual entry | Color: "+color.getText().trim()+" | Size: "+size.getText().trim();
                if (addon) {
                    guidedOrder.printAddonModel.addRow(new Object[]{null,name.getText().trim(),null,size.getText().trim(),"FIXED_PRESET",details,1,money(amount)});
                    updatePrintAddonSummary();
                } else {
                    customerItem=null;
                    CustomItemOption item=new CustomItemOption(null,name.getText().trim(),size.getText().trim(),color.getText().trim(),null,"SERVICE","FIXED",null,false,null,null,null,null,null);
                    guidedOrder.orderItemBox.addItem(item); guidedOrder.orderItemBox.setSelectedItem(item);
                    guidedOrder.linePriceField.setText(amount.toPlainString()); guidedOrder.priceOverrideReasonField.setText("");
                }
                return;
            } catch (Exception ex) { error(ex); }
        }
    }

    private void refreshCustomerItem() {
        if (guidedOrder == null) return;
        boolean supplied = customerItem != null;
        guidedOrder.customerItemSummary.setText(supplied ? customerItem.details() : inventoryProductId!=null ? "From inventory  -  selling price plus print add-ons" : " ");
        guidedOrder.editCustomerItemButton.setVisible(supplied);
        guidedOrder.linePriceField.setEnabled(!supplied);
        guidedOrder.priceOverrideReasonField.setEnabled(!supplied);
        guidedOrder.variantBox.setEnabled(!supplied);
        if (supplied) guidedOrder.priceOverrideReasonField.setText("");
    }

    private String websiteShirtRequestId;
    public void importShirtQuote(JsonObject quote,List<Path> files){
        if(guidedOrder==null){JOptionPane.showMessageDialog(this,"Create Custom Order permission is required.");return;}
        UiTaskRunner.submit(this,"custom-orders.import-shirt",()->{
            JsonObject context=quote.getAsJsonObject("projectContext"),design=context.getAsJsonObject("shirtDesign"),estimate=context.getAsJsonObject("shirtEstimate");
            long itemId=design.get("itemId").getAsLong();
            CustomItemOption item=CustomOrderDataService.listActiveItems().stream().filter(i->java.util.Objects.equals(i.customItemId(),itemId)).findFirst().orElseThrow(()->new IllegalArgumentException("The shirt item is no longer active."));
            List<VariantOption> variants=CustomOrderDataService.listActiveVariants(itemId);
            List<CustomerOption> customers=CustomOrderDataService.searchCustomers(quote.get("email").getAsString());
            CustomerOption customer=customers.stream().filter(c->quote.get("email").getAsString().equalsIgnoreCase(c.email())).findFirst().orElseThrow(()->new IllegalArgumentException("Link this website customer to a store customer account first."));
            List<PrintAddonRequest> addons=new ArrayList<>();
            for(var value:estimate.getAsJsonArray("charges")){var charge=value.getAsJsonObject();addons.add(new PrintAddonRequest(charge.get("materialId").getAsLong(),charge.get("material").getAsString(),charge.get("id").getAsLong(),charge.get("name").getAsString(),charge.has("pricingMode")?charge.get("pricingMode").getAsString():"FIXED_PRESET",charge.get("area").getAsString()+" / "+charge.get("mode").getAsString()+(charge.has("description")?" / "+charge.get("description").getAsString():""),1,charge.get("price").getAsBigDecimal()));}
            List<CartLine> imported=new ArrayList<>();
            for(var value:estimate.getAsJsonArray("lines")){var group=value.getAsJsonObject();long variantId=group.get("id").getAsLong();VariantOption variant=variantId==0?null:variants.stream().filter(v->v.variantId()==variantId).findFirst().orElseThrow(()->new IllegalArgumentException("A shirt variant is no longer active."));
                JsonObject r=new JsonObject();r.addProperty("customItemId",itemId);if(variant!=null)r.addProperty("customVariantId",variantId);r.addProperty("itemName",item.name());r.addProperty("variantName",variant==null?"":variant.name());r.addProperty("pricingType","FIXED");
                BigDecimal base=group.get("price").getAsBigDecimal(),prints=group.get("printPrice").getAsBigDecimal(),unit=base.add(prints);
                r.addProperty("unitPrice",group.has("unitTotal")?group.get("unitTotal").getAsBigDecimal():unit);r.addProperty("originalLineTotal",unit);for(String k:List.of("baseItemPrice","originalBasePrice"))r.addProperty(k,base);r.addProperty("printCharge",prints);r.addProperty("printLineCount",addons.size());r.add("printAddons",GSON.toJsonTree(addons));for(String k:List.of("lineDiscountPercent","lineDiscountAmount","minimumDepositPercent"))r.addProperty(k,0);
                r.addProperty("customizationDetails","Website shirt request "+quote.get("requestId").getAsString()+"; "+(estimate.has("shirtStyle")?estimate.get("shirtStyle").getAsString():"Shirt")+"; customer design retained with linked request; staff must confirm production method.");r.addProperty("orderInstructions",quote.get("description").getAsString());r.addProperty("itemSize",group.get("size").getAsString());r.addProperty("itemColor",group.get("color").getAsString());OrderLineRequest line=GSON.fromJson(r,OrderLineRequest.class);
                for(int n=0;n<group.get("quantity").getAsInt();n++)imported.add(new CartLine(line,new ArrayList<>(imported.isEmpty()?files:List.of()),item,variant));
            }
            return new ShirtImport(customer,imported,quote);
        },result->{
            if(!lines.isEmpty()){JOptionPane.showMessageDialog(this,"Clear this order before importing the website request.");return;}
            websiteShirtRequestId=result.quote().get("requestId").getAsString();CustomerOption c=result.customer();guidedOrder.customerInfoPanel.restoreDraft(new CustomerInfoPanel.Draft(c,c.name(),result.quote().has("contactPhone")&&!result.quote().get("contactPhone").isJsonNull()&&!result.quote().get("contactPhone").getAsString().isBlank()?result.quote().get("contactPhone").getAsString():c.phone(),c.accountNumber(),c.email(),false));
            guidedOrder.orderNotesArea.setText("Website shirt request "+websiteShirtRequestId+" — estimated pricing; review before saving.\n"+result.quote().get("description").getAsString());
            if(result.quote().has("desiredDate")&&!result.quote().get("desiredDate").isJsonNull()){guidedOrder.dueDateEnabledBox.setSelected(true);guidedOrder.dueDateField.setEnabled(true);guidedOrder.dueDateField.setText(result.quote().get("desiredDate").getAsString());}
            lines.addAll(result.lines());guidedOrder.orderLineModel.setRowCount(0);lines.forEach(line->guidedOrder.orderLineModel.addRow(cartRow(line)));updateTotal();
            JOptionPane.showMessageDialog(this,"Shirt quantities, print add-ons, artwork, and previews imported. Confirm final prices, customer details, and deposit before saving. The saved order will be linked to the website request.");
        },failure->error(failure instanceof Exception ex?ex:new Exception(failure)));
    }
    private record ShirtImport(CustomerOption customer,List<CartLine> lines,JsonObject quote){}
    private void loadCatalog(){
        if(guidedOrder==null)return;
        CachedUiLoader.load(this,"custom-orders:catalog",Catalog.class,SessionDataCache.REFERENCE_TTL,loadingState,CustomOrders::fetchCatalog,c->{loadingCatalog=true;try{minimumDepositPercent=c.minimumDepositPercent();customOrderFileLimitBytes=c.fileLimitBytes();roundToNearestTwenty=c.roundToNearestTwenty();alwaysPrintOrderSlip=c.alwaysPrintOrderSlip();variantsByItem=new ConcurrentHashMap<>();presetsByMaterial=new ConcurrentHashMap<>();guidedOrder.customerInfoPanel.preloadCustomers(c.initialCustomers());guidedOrder.setAlwaysPrintOrderSlip(alwaysPrintOrderSlip);guidedOrder.orderItemBox.removeAllItems();c.items().forEach(guidedOrder.orderItemBox::addItem);guidedOrder.printMaterialBox.removeAllItems();guidedOrder.printMaterialBox.addItem(new PrintMaterialOption(null,"No Print"));c.materials().forEach(guidedOrder.printMaterialBox::addItem);guidedOrder.designPlacementBox.removeAllItems();c.placements().forEach(guidedOrder.designPlacementBox::addItem);if(guidedOrder.designPlacementBox.getItemCount()==0)for(String x:List.of("Front","Back","Left Chest","Right Chest","Left Sleeve","Right Sleeve"))guidedOrder.designPlacementBox.addItem(x);}finally{loadingCatalog=false;}loadVariants();loadPresets();prefetchFirstAddOn(c.materials());applyPrice();updateTotal();});
    }
    private void loadVariants(){if(guidedOrder==null)return;CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();guidedOrder.variantBox.removeAllItems();if(item==null||item.customItemId()==null)return;long itemId=item.customItemId();if(variantsByItem.containsKey(itemId)){renderVariants(itemId,variantsByItem.get(itemId));return;}CachedUiLoader.loadIfStale(this,"custom-orders.variants","custom-orders:variants:"+itemId,VariantSnapshot.class,SessionDataCache.REFERENCE_TTL,loadingState,()->new VariantSnapshot(itemId,CustomOrderDataService.listActiveVariants(itemId)),snapshot->{variantsByItem.put(snapshot.itemId(),snapshot.variants());renderVariants(snapshot.itemId(),snapshot.variants());});}
    private void renderVariants(long itemId,List<VariantOption>variants){CustomItemOption current=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();if(current==null||!java.util.Objects.equals(current.customItemId(),itemId))return;loadingCatalog=true;try{guidedOrder.variantBox.removeAllItems();variants.forEach(guidedOrder.variantBox::addItem);guidedOrder.variantBox.setEnabled(current.hasVariants());if(pendingVariantSelectionId!=null){selectVariant(guidedOrder.variantBox,pendingVariantSelectionId);pendingVariantSelectionId=null;}}finally{loadingCatalog=false;}applyPrice();}
    private void loadPresets(){if(guidedOrder==null)return;guidedOrder.printSizePresetBox.removeAllItems();guidedOrder.printSizePresetBox.addItem(new PrintSizePresetOption(null,null,"Custom Print Price","FIXED_PRESET",null));PrintMaterialOption m=(PrintMaterialOption)guidedOrder.printMaterialBox.getSelectedItem();boolean active=m!=null&&m.printMaterialId()!=null;guidedOrder.printSizePresetBox.setEnabled(active);guidedOrder.printChargeField.setEnabled(active);if(!active){guidedOrder.printChargeField.setText("0");return;}long materialId=m.printMaterialId();if(presetsByMaterial.containsKey(materialId)){renderPresets(materialId,presetsByMaterial.get(materialId));return;}CachedUiLoader.loadIfStale(this,"custom-orders.presets","custom-orders:presets:"+materialId,PresetSnapshot.class,SessionDataCache.REFERENCE_TTL,loadingState,()->new PresetSnapshot(materialId,CustomOrderDataService.listActivePrintSizePresets(materialId)),snapshot->{presetsByMaterial.put(snapshot.materialId(),snapshot.presets());renderPresets(snapshot.materialId(),snapshot.presets());});}
    private void renderPresets(long materialId,List<PrintSizePresetOption>presets){PrintMaterialOption current=(PrintMaterialOption)guidedOrder.printMaterialBox.getSelectedItem();if(current==null||!java.util.Objects.equals(current.printMaterialId(),materialId))return;loadingCatalog=true;try{guidedOrder.printSizePresetBox.removeAllItems();guidedOrder.printSizePresetBox.addItem(new PrintSizePresetOption(null,null,"Custom Print Price","FIXED_PRESET",null));presets.forEach(guidedOrder.printSizePresetBox::addItem);}finally{loadingCatalog=false;}applyPrintPresetPrice();}
    private void prefetchFirstAddOn(List<PrintMaterialOption>materials){if(materials==null||materials.isEmpty())return;long materialId=materials.get(0).printMaterialId();UiTaskRunner.submit(this,"custom-orders.prefetch-addon",()->new PresetSnapshot(materialId,CustomOrderDataService.listActivePrintSizePresets(materialId)),snapshot->{presetsByMaterial.put(snapshot.materialId(),snapshot.presets());SessionDataCache.put("custom-orders:presets:"+snapshot.materialId(),snapshot);},failure->{});}
    private void applyPrice(){if(guidedOrder==null)return;if(customerItem!=null){guidedOrder.linePriceField.setText("0");guidedOrder.areaLineComponents.forEach(c->c.setVisible(false));guidedOrder.priceRateUnitLabel.setText("customer supplied");configureSizeSet();return;}CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();VariantOption variant=(VariantOption)guidedOrder.variantBox.getSelectedItem();BigDecimal p=variant!=null&&variant.fixedPrice()!=null?variant.fixedPrice():item==null?null:("AREA".equals(item.pricingType())?item.areaPrice():item.fixedPrice());if(p!=null)guidedOrder.linePriceField.setText(p.stripTrailingZeros().toPlainString());guidedOrder.priceRateUnitLabel.setText(item!=null&&"AREA".equals(item.pricingType())?"per "+item.areaPriceUnit():"per item");boolean area=item!=null&&"AREA".equals(item.pricingType());guidedOrder.areaLineComponents.forEach(c->c.setVisible(area));updateAreaPreview();configureSizeSet();}
    private void lookupOrderItem(){String search=guidedOrder.itemLookupField.getText().trim();CachedUiLoader.loadIfStale(this,"custom-orders.item-lookup","custom-orders:item-lookup:"+search,ItemLookupSnapshot.class,SessionDataCache.SCREEN_TTL,loadingState,()->new ItemLookupSnapshot(CustomOrderDataService.lookupCustomItem(search)),snapshot->{if(!search.equals(guidedOrder.itemLookupField.getText().trim()))return;var result=snapshot.result();if(result==null||result.customItemId()==null){JOptionPane.showMessageDialog(this,"No custom item or variant matched that search.");return;}selectOrderItem(result.customItemId(),result.customVariantId());});}
    private void selectOrderItem(Long itemId,Long variantId){
        if(itemId==null)return;
        boolean available=false;
        for(int i=0;i<guidedOrder.orderItemBox.getItemCount();i++)
            if(java.util.Objects.equals(itemId,guidedOrder.orderItemBox.getItemAt(i).customItemId())){available=true;break;}
        if(!available){
            String selectedText=guidedOrder.itemLookupField.getText();
            UiTaskRunner.submit(this,"custom-orders.selected-item",CustomOrderDataService::listActiveItems,items->{
                if(!selectedText.equals(guidedOrder.itemLookupField.getText()))return;
                CustomItemOption item=items.stream().filter(x->java.util.Objects.equals(x.customItemId(),itemId)).findFirst().orElse(null);
                if(item==null){JOptionPane.showMessageDialog(this,"That custom item is no longer available. Search for another item.");return;}
                loadingCatalog=true;try{guidedOrder.orderItemBox.addItem(item);}finally{loadingCatalog=false;}
                selectOrderItem(itemId,variantId);
            },failure->{if(selectedText.equals(guidedOrder.itemLookupField.getText()))JOptionPane.showMessageDialog(this,"Unable to load the selected item: "+message(failure));});
            return;
        }
        pendingVariantSelectionId=variantId;
        loadingCatalog=true;
        try{selectItem(guidedOrder.orderItemBox,itemId);}finally{loadingCatalog=false;}
        inventoryProductId=null;customerItem=null;refreshCustomerItem();loadVariants();applyPrice();
    }
    private void applyPrintPresetPrice(){if(guidedOrder==null)return;PrintSizePresetOption p=(PrintSizePresetOption)guidedOrder.printSizePresetBox.getSelectedItem();if(p!=null&&p.fixedPrice()!=null){BigDecimal v=p.fixedPrice();if("PER_LINE".equals(p.pricingMode()))v=v.multiply(BigDecimal.valueOf(integer(guidedOrder.printLineCountField,"print line count",1)));guidedOrder.printChargeField.setText(v.stripTrailingZeros().toPlainString());}}
    private void addPrintAddon(){try{PrintMaterialOption m=(PrintMaterialOption)guidedOrder.printMaterialBox.getSelectedItem();if(m==null||m.printMaterialId()==null)throw new IllegalArgumentException("Select a print material.");PrintSizePresetOption p=(PrintSizePresetOption)guidedOrder.printSizePresetBox.getSelectedItem();BigDecimal charge=decimal(guidedOrder.printChargeField,"print charge",!pricePending());if(charge==null)charge=BigDecimal.ZERO;int count="PER_LINE".equals(p==null?null:p.pricingMode())?integer(guidedOrder.printLineCountField,"print line count",1):1;if((p==null||p.printSizePresetId()==null)&&guidedOrder.printDescriptionField.getText().isBlank())throw new IllegalArgumentException("Enter a print description for a custom print price.");guidedOrder.printAddonModel.addRow(new Object[]{m.printMaterialId(),m.materialName(),p==null?null:p.printSizePresetId(),p==null?"Custom":p.presetName(),p==null?"FIXED_PRESET":p.pricingMode(),guidedOrder.printAddonDescription(),count,money(charge)});guidedOrder.printAddonTable.setRowSelectionInterval(guidedOrder.printAddonModel.getRowCount()-1,guidedOrder.printAddonModel.getRowCount()-1);if(guidedOrder.individualPrintTextBox.isSelected())editPrintAddonText();guidedOrder.individualPrintTextBox.setSelected(false);guidedOrder.printMaterialBox.setSelectedIndex(0);guidedOrder.printDescriptionField.setText("");guidedOrder.printColorField.setText("");guidedOrder.printChargeField.setText("0");updatePrintAddonSummary();}catch(Exception e){error(e);}}
    private void removePrintAddon(){int r=guidedOrder.printAddonTable.getSelectedRow();if(r>=0){guidedOrder.printAddonModel.removeRow(guidedOrder.printAddonTable.convertRowIndexToModel(r));updatePrintAddonSummary();}}
    private void addPlacement(){String text=guidedOrder.designPlacementField.getText().trim();if(text.isBlank()){JOptionPane.showMessageDialog(this,"Enter the design or text for this placement.");return;}String placement=String.valueOf(guidedOrder.designPlacementBox.getSelectedItem());if(!guidedOrder.lineNotesArea.getText().isBlank())guidedOrder.lineNotesArea.append("\n");guidedOrder.lineNotesArea.append(placement+": "+text);guidedOrder.designPlacementField.setText("");}
    private void editDiscount(){String v=JOptionPane.showInputDialog(this,"Discount percentage:",guidedOrder.lineDiscountPercentField.getText());if(v==null)return;guidedOrder.lineDiscountPercentField.setText(v.trim());String reason=JOptionPane.showInputDialog(this,"Discount reason:",guidedOrder.lineDiscountReasonField.getText());if(reason!=null)guidedOrder.lineDiscountReasonField.setText(reason.trim());}
    private void configureSizeSet(){
        if(guidedOrder==null||buildingSet)return;
        CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();
        VariantOption selected=(VariantOption)guidedOrder.variantBox.getSelectedItem();
        guidedOrder.sizeSetEditor.configure(customerItem==null?item:null,
            item==null||item.customItemId()==null?List.of():variantsByItem.getOrDefault(item.customItemId(),List.of()),selected,this::sizeUnitPrice);
    }
    private BigDecimal sizeUnitPrice(VariantOption variant){
        if(pricePending())return null;
        CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();
        BigDecimal base=variant.fixedPrice()==null?item.fixedPrice():variant.fixedPrice();
        if(base==null)throw new IllegalArgumentException("This size has no catalogue price.");
        BigDecimal original=base.add(printAddons().stream().map(PrintAddonRequest::printCharge).reduce(BigDecimal.ZERO,BigDecimal::add));
        BigDecimal pct=decimal(guidedOrder.lineDiscountPercentField,"discount",false);if(pct==null)pct=BigDecimal.ZERO;
        return CustomOrderSizeSets.unitPrice(original,pct,roundToNearestTwenty);
    }
    private List<CustomOrderShirtText.Slot> shirtSlots(){
        var variant=(VariantOption)guidedOrder.variantBox.getSelectedItem();
        var item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();
        String size=customerItem!=null?customerItem.size():variant!=null?variant.effectiveSize():item==null?null:item.size();
        return CustomOrderShirtText.slots(guidedOrder.sizeSetEditor.selections(),variant==null?null:variant.variantId(),size,
            editingLine>=0&&editingSetId==null?1:integer(guidedOrder.lineQuantityField,"quantity",1));
    }
    private List<Map<String,String>> shirtTextEntries(){
        List<Map<String,String>> result=new ArrayList<>();
        for(int row=0;row<guidedOrder.printAddonModel.getRowCount();row++)result.add(CustomOrderShirtText.values(guidedOrder.printAddonModel.getValueAt(row,8)));
        return result;
    }
    private void editPrintAddonText(){
        int row=guidedOrder.printAddonTable.getSelectedRow();
        if(row<0){JOptionPane.showMessageDialog(this,"Select a print add-on first.");return;}
        row=guidedOrder.printAddonTable.convertRowIndexToModel(row);
        try{
            var editor=CustomOrderShirtText.editor(shirtSlots(),CustomOrderShirtText.values(guidedOrder.printAddonModel.getValueAt(row,8)));
            JScrollPane scroll=new JScrollPane(editor.panel());scroll.setPreferredSize(new Dimension(560,340));
            JPanel panel=new JPanel(new BorderLayout(8,8));
            panel.add(new JLabel("Optional. Blank fields use the shared description. Set sizes and quantities first."),BorderLayout.NORTH);panel.add(scroll,BorderLayout.CENTER);
            if(JOptionPane.showConfirmDialog(guidedOrder.printAddonTable,panel,"Names / per-shirt text — "+guidedOrder.printAddonModel.getValueAt(row,1),JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
                var values=editor.values();
                if(values.values().stream().anyMatch(text->text.length()>500))throw new IllegalArgumentException("Per-shirt text must be 500 characters or fewer.");
                guidedOrder.printAddonModel.setValueAt(values,row,8);guidedOrder.printAddonModel.setValueAt(values.isEmpty()?"Shared":values.size()+" entered",row,9);
            }
        }catch(Exception e){error(e);}
    }
    private void restoreShirtText(CartLine selected){
        if(selected.personalization()==null)return;
        var members=selected.setId()==null?List.of(selected):lines.stream().filter(line->selected.setId().equals(line.setId())).toList();
        Map<Long,Integer> numbers=new java.util.HashMap<>();
        for(var member:members){
            int number=numbers.merge(member.request().customVariantId(),1,Integer::sum);
            var saved=member.personalization();if(saved==null)continue;
            for(int addon=0;addon<saved.texts().size()&&addon<guidedOrder.printAddonModel.getRowCount();addon++){
                String text=saved.texts().get(addon);if(text==null||text.isBlank())continue;
                var values=new java.util.LinkedHashMap<>(CustomOrderShirtText.values(guidedOrder.printAddonModel.getValueAt(addon,8)));
                values.put(CustomOrderShirtText.key(member.request().customVariantId(),number),text);
                guidedOrder.printAddonModel.setValueAt(values,addon,8);guidedOrder.printAddonModel.setValueAt(values.size()+" entered",addon,9);
            }
        }
    }
    private void addLine(){
        var sizes=guidedOrder.sizeSetEditor.selections();
        if(sizes.isEmpty()){if(editingSetId!=null){JOptionPane.showMessageDialog(this,"Choose sizes for the set, or cancel editing.");return;}addSingleLine();return;}
        List<CartLine> before=new ArrayList<>(lines);int oldEdit=editingLine;String oldSet=editingSetId;
        VariantOption oldVariant=(VariantOption)guidedOrder.variantBox.getSelectedItem();String oldPrice=guidedOrder.linePriceField.getText();
        String oldQuantity=guidedOrder.lineQuantityField.getText();boolean success=false;
        buildingSet=true;buildingSetId=oldSet==null?UUID.randomUUID().toString():oldSet;editingLine=-1;
        try{
            var slots=shirtSlots();var entries=shirtTextEntries();CustomOrderShirtText.validate(slots,entries);
            CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();
            var allowed=CustomOrderSizeSetEditor.eligible(item,variantsByItem.getOrDefault(item.customItemId(),List.of()),sizes.get(0).variant());
            if(sizes.stream().anyMatch(size->!allowed.contains(size.variant())))throw new IllegalArgumentException("A selected size is no longer available. Choose sizes again.");
            var first=sizes.get(0).variant();guidedOrder.variantBox.setSelectedItem(first);
            BigDecimal base=first.fixedPrice()==null?item.fixedPrice():first.fixedPrice();
            guidedOrder.linePriceField.setText(base==null?"":decimalText(base));guidedOrder.lineQuantityField.setText("1");
            if(!addSingleLine())return;
            var units=CustomOrderSizeSets.expand(item,lines.get(before.size()).request(),sizes,pricePending(),roundToNearestTwenty);
            List<CartLine> added=new ArrayList<>();
            for(int index=0;index<units.size();index++){
                var unit=units.get(index);var applied=CustomOrderShirtText.apply(unit.request(),slots.get(index),entries);
                added.add(new CartLine(applied.request(),new ArrayList<>(draftFiles),item,unit.variant(),buildingSetId,applied.saved()));
            }
            var updated=CustomOrderSetCart.replace(before,added,oldEdit,oldSet,CartLine::setId);
            lines.clear();lines.addAll(updated);success=true;
        }catch(Exception e){error(e);}
        finally{
            buildingSet=false;buildingSetId=null;
            if(success)clearLine();else{
                lines.clear();lines.addAll(before);editingLine=oldEdit;editingSetId=oldSet;
                loadingCatalog=true;try{guidedOrder.variantBox.setSelectedItem(oldVariant);}finally{loadingCatalog=false;}
                guidedOrder.linePriceField.setText(oldPrice);guidedOrder.lineQuantityField.setText(oldQuantity);
            }
            rebuildCartDisplay();updateTotal();
        }
    }
    private boolean addSingleLine(){try{CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();if(customerItem!=null)item=new CustomItemOption(null,customerItem.name(),customerItem.size(),customerItem.color(),null,"SERVICE","FIXED",BigDecimal.ZERO,false,null,null,null,null,null);if(item==null)throw new IllegalArgumentException("Select an item.");VariantOption variant=(VariantOption)guidedOrder.variantBox.getSelectedItem();if(item.hasVariants()&&variant==null)throw new IllegalArgumentException("Select a size or variant.");int qty=editingLine>=0?1:integer(guidedOrder.lineQuantityField,"quantity",1);BigDecimal entered=decimal(guidedOrder.linePriceField,"line price",!pricePending()),base=entered,area=null,w=nullable(guidedOrder.widthField),l=nullable(guidedOrder.lengthField);if(entered==null)entered=BigDecimal.ZERO;base=entered;if(!pricePending()&&"AREA".equals(item.pricingType())){if(w==null||l==null||w.signum()<=0||l.signum()<=0)throw new IllegalArgumentException("Enter width and length for area pricing.");if(guidedOrder.designOrientationBox.getSelectedIndex()==0)throw new IllegalArgumentException("Select portrait or landscape for the design.");area=billableArea(w,l,item.maxWidth(),item.dimensionUnit(),item.areaPriceUnit());base=area.multiply(entered).setScale(2,RoundingMode.HALF_UP);}BigDecimal configured=variant!=null&&variant.fixedPrice()!=null?variant.fixedPrice():("AREA".equals(item.pricingType())?item.areaPrice():item.fixedPrice());String overrideReason=null,overrideToken=null;if(!pricePending()&&configured!=null&&entered.compareTo(configured)!=0){overrideReason=guidedOrder.priceOverrideReasonField.getText().trim();if(overrideReason.isBlank())throw new IllegalArgumentException("Enter a price override reason.");if(!can("CUSTOM_ORDER_PRICE_OVERRIDE")&&!can("CUSTOM_ORDER_OVERRIDES")){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_PRICE_OVERRIDE","Custom Order Price Override","Reason for custom order price override:");if(approval==null)return false;overrideToken=approval.lanApprovalToken();overrideReason=approval.reason();}}
            List<PrintAddonRequest>addons=printAddons();if(!pricePending()&&customerItem!=null&&addons.isEmpty())throw new IllegalArgumentException("Add at least one add-on to the customer item.");BigDecimal addOn=addons.stream().map(PrintAddonRequest::printCharge).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal original=base.add(addOn);BigDecimal pct=pricePending()?BigDecimal.ZERO:decimal(guidedOrder.lineDiscountPercentField,"discount",false);if(pct==null)pct=BigDecimal.ZERO;if(pct.signum()<0||pct.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("Discount must be between 0 and 100%.");String reason=guidedOrder.lineDiscountReasonField.getText().trim(),approvalToken=null;if(pct.signum()>0&&!can("CUSTOM_ORDER_LINE_DISCOUNT")&&!can("CUSTOM_ORDER_OVERRIDES")){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_LINE_DISCOUNT","Custom Order Line Discount Override","Reason for custom order line discount override:");if(approval==null)return false;approvalToken=approval.lanApprovalToken();reason=approval.reason();}if(pct.signum()>0&&reason.isBlank())throw new IllegalArgumentException("Enter a discount reason.");BigDecimal reduction=original.multiply(pct).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP),unrounded=CurrencyFormatter.normalize(original.subtract(reduction).max(BigDecimal.ZERO)),lineTotal=unrounded;String details=customerItem!=null?customerItem.details():inventoryProductId!=null?"Inventory item | SKU: "+item.sku():item.customItemId()==null?"Manual entry | Color: "+item.color()+" | Size: "+item.size():area==null?"":"Orientation: "+guidedOrder.designOrientationBox.getSelectedItem()+" | Design: "+w+" x "+l+" "+item.dimensionUnit()+" | Charged area: "+billingWidth(w,item.maxWidth())+" x "+l+" "+item.dimensionUnit()+" = "+area+" "+item.areaPriceUnit();var textSlots=buildingSet?List.<CustomOrderShirtText.Slot>of():shirtSlots();var textEntries=shirtTextEntries();if(!buildingSet)CustomOrderShirtText.validate(textSlots,textEntries);String entrySetId=buildingSetId!=null?buildingSetId:qty>1&&CustomOrderSizeSetEditor.supportsSizes(item)&&variant!=null&&variant.effectiveSize()!=null&&!variant.effectiveSize().isBlank()?UUID.randomUUID().toString():null;for(int i=0;i<qty;i++){OrderLineRequest request=new OrderLineRequest(item.customItemId(),variant==null?null:variant.variantId(),item.name(),variant==null?null:variant.name(),item.pricingType(),lineTotal,details,guidedOrder.lineNotesArea.getText(),w,l,item.dimensionUnit(),area,item.areaPriceUnit(),entered,base,null,null,null,null,addOn,addons.stream().mapToInt(PrintAddonRequest::printLineCount).sum(),original,pct,reduction,null,null,reason,BigDecimal.ZERO,configured,overrideReason==null?null:entered,overrideReason,null,null,addons,approvalToken,overrideToken,customerItem==null?(variant==null?item.size():variant.effectiveSize()):customerItem.size(),customerItem==null?(variant==null?item.color():variant.effectiveColor()):customerItem.color(),customerItem,inventoryProductId);var applied=buildingSet?new CustomOrderShirtText.Applied(request,null):CustomOrderShirtText.apply(request,textSlots.get(i),textEntries);CartLine updated=new CartLine(applied.request(),new ArrayList<>(draftFiles),item,variant,entrySetId,applied.saved());if(editingLine>=0)lines.set(editingLine,updated);else lines.add(updated);Object[] rowValues=new Object[]{item.customItemId(),variant==null?null:variant.variantId(),item.name(),variant==null?"":variant.name(),item.pricingType(),pricePending()?"Price pending":money(lineTotal),details,guidedOrder.lineNotesArea.getText(),w,l,item.dimensionUnit(),area,item.areaPriceUnit(),entered,null,"",null,"",money(addOn),money(base),addons.stream().mapToInt(PrintAddonRequest::printLineCount).sum(),printSummary(addons),"",money(original),decimalText(pct),money(reduction),reason,"0",configured,overrideReason==null?null:entered,overrideReason,draftFiles.size()};if(editingLine>=0){for(int column=0;column<rowValues.length;column++)guidedOrder.orderLineModel.setValueAt(rowValues[column],editingLine,column);}else guidedOrder.orderLineModel.addRow(rowValues);}updateTotal();clearLine();return true;}catch(Exception e){error(e);return false;}}
    private List<PrintAddonRequest>printAddons(){List<PrintAddonRequest>out=new ArrayList<>();for(int r=0;r<guidedOrder.printAddonModel.getRowCount();r++)out.add(new PrintAddonRequest(longValue(guidedOrder.printAddonModel.getValueAt(r,0)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,1)),longValue(guidedOrder.printAddonModel.getValueAt(r,2)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,3)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,4)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,5)),Integer.parseInt(String.valueOf(guidedOrder.printAddonModel.getValueAt(r,6))),new BigDecimal(String.valueOf(guidedOrder.printAddonModel.getValueAt(r,7)))));return out;}
    private void editLine(){
        int row=guidedOrder.orderLineTable.getSelectedRow();
        if(row<0){JOptionPane.showMessageDialog(this,"Select a cart line to edit.");return;}
        row=guidedOrder.orderLineTable.convertRowIndexToModel(row);
        CartLine line=lines.get(row);OrderLineRequest r=line.request();
        clearLine();editingLine=row;editingSetId=line.setId();
        loadingCatalog=true;
        try{
            if(line.item()!=null){
                if(r.inventoryProductId()!=null)inventoryOptions.put(line.item(),r.inventoryProductId());
                boolean found=false;for(int i=0;i<guidedOrder.orderItemBox.getItemCount();i++)if(guidedOrder.orderItemBox.getItemAt(i).equals(line.item())){found=true;break;}
                if(!found)guidedOrder.orderItemBox.addItem(line.item());
                guidedOrder.orderItemBox.setSelectedItem(line.item());
                guidedOrder.itemLookupField.setText(line.item().name());
            }
            guidedOrder.variantBox.removeAllItems();
            if(r.customItemId()!=null)for(VariantOption v:variantsByItem.getOrDefault(r.customItemId(),List.of()))guidedOrder.variantBox.addItem(v);
            if(line.variant()!=null){guidedOrder.variantBox.setSelectedItem(line.variant());if(guidedOrder.variantBox.getSelectedItem()!=line.variant()){guidedOrder.variantBox.addItem(line.variant());guidedOrder.variantBox.setSelectedItem(line.variant());}}
            inventoryProductId=r.inventoryProductId();customerItem=r.customerItem();refreshCustomerItem();applyPrice();
            guidedOrder.linePriceField.setText(decimalText(r.areaPrice()));
            guidedOrder.widthField.setText(decimalText(r.widthValue()));guidedOrder.lengthField.setText(decimalText(r.lengthValue()));
            guidedOrder.designOrientationBox.setSelectedItem(designOrientation(r.customizationDetails()));
            guidedOrder.lineNotesArea.setText(r.orderInstructions());
            guidedOrder.lineDiscountPercentField.setText(decimalText(r.lineDiscountPercent()));
            guidedOrder.lineDiscountReasonField.setText(r.lineDiscountReason());guidedOrder.priceOverrideReasonField.setText(r.priceOverrideReason());
            guidedOrder.printAddonModel.setRowCount(0);
            if(r.printAddons()!=null)for(PrintAddonRequest a:line.personalization()==null?r.printAddons():line.personalization().sharedAddons())guidedOrder.printAddonModel.addRow(new Object[]{a.printMaterialId(),a.materialName(),a.printSizePresetId(),a.printSizeName(),a.pricingMode(),a.printDescription(),a.printLineCount(),money(a.printCharge())});
            draftFiles.addAll(line.pendingFiles());guidedOrder.fileSummaryLabel.setText(draftFiles.isEmpty()?"No files attached.":draftFiles.size()+" file(s) attached.");
            updatePrintAddonSummary();guidedOrder.setEditingLine(true);updateAreaPreview();
            configureSizeSet();
            if(editingSetId!=null)guidedOrder.sizeSetEditor.restore(setSizes(editingSetId));restoreShirtText(line);
        }finally{loadingCatalog=false;}
    }
    private void removeLine(){int view=guidedOrder.orderLineTable.getSelectedRow();if(view<0)return;
        int row=guidedOrder.orderLineTable.convertRowIndexToModel(view);
        var remaining=CustomOrderSetCart.remove(lines,row,CartLine::setId);lines.clear();lines.addAll(remaining);
        clearLine();rebuildCartDisplay();updateTotal();}
    private void selectLineFiles(){
        editPendingFiles(draftFiles,"Files for New Line");
        guidedOrder.fileSummaryLabel.setText(draftFiles.isEmpty()?"No files attached.":draftFiles.size()+" file(s) attached.");
    }
    private void editLineFiles(){
        int row=guidedOrder.orderLineTable.getSelectedRow();
        if(row<0){JOptionPane.showMessageDialog(this,"Select a cart line to edit its files.");return;}
        row=guidedOrder.orderLineTable.convertRowIndexToModel(row);
        List<Path> files=lines.get(row).pendingFiles();editPendingFiles(files,"Cart Line Files");
        String id=lines.get(row).setId();if(id!=null)for(CartLine line:lines)if(id.equals(line.setId())&&line.pendingFiles()!=files){line.pendingFiles().clear();line.pendingFiles().addAll(files);}
        rebuildCartDisplay();refreshQuantitySummary();
    }
    private void editPendingFiles(List<Path> files,String title){
        DefaultListModel<Path> model=new DefaultListModel<>();files.forEach(model::addElement);
        JList<Path> list=new JList<>(model);list.setVisibleRowCount(8);
        JPanel panel=new JPanel(new BorderLayout(8,8));
        JLabel dropStatus = new JLabel("Drop files, paste photos or files (Ctrl+V), or click Add Files. Files upload when the order is saved.");
        panel.add(dropStatus,BorderLayout.NORTH);
        CustomOrderFileDropHandler dropHandler=new CustomOrderFileDropHandler(model,text->JOptionPane.showMessageDialog(panel,text));
        dropHandler.setStatus(dropStatus::setText);
        list.setTransferHandler(dropHandler);panel.setTransferHandler(dropHandler);
        JScrollPane fileScroll=new JScrollPane(list);fileScroll.setTransferHandler(dropHandler);fileScroll.getViewport().setTransferHandler(dropHandler);
        panel.add(fileScroll,BorderLayout.CENTER);
        JPanel actions=new JPanel();JButton add=new JButton("Add Files..."),remove=new JButton("Remove Selected");
        JButton paste=new JButton("Paste");paste.addActionListener(e->dropHandler.paste(panel));actions.add(add);actions.add(paste);actions.add(remove);panel.add(actions,BorderLayout.SOUTH);
        add.addActionListener(e->{
            JFileChooser picker=new JFileChooser();picker.setMultiSelectionEnabled(true);
            CustomOrderFileDropHandler.installPicker(picker,text->JOptionPane.showMessageDialog(picker,text));
            if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            List<Path> chosen=new ArrayList<>();
            for(var file:picker.getSelectedFiles())chosen.add(file.toPath());
            dropHandler.addFiles(chosen);
        });
        remove.addActionListener(e->{for(Path path:list.getSelectedValuesList())model.removeElement(path);});
        CustomOrderFileDropHandler.install(panel, dropHandler);
        CustomOrderFileDropHandler.installPaste(panel, dropHandler);
        CustomOrderFileDropHandler.installPaste(list, dropHandler);
        if(JOptionPane.showConfirmDialog(this,panel,title,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            files.clear();for(int i=0;i<model.size();i++)files.add(model.get(i));
        }
    }
    private void clearLine(){if(buildingSet)return;
        inventoryProductId=null;
        loadingCatalog=true;
        try { for(var option:inventoryOptions.keySet())guidedOrder.orderItemBox.removeItem(option);inventoryOptions.clear(); }
        finally { loadingCatalog=false; }
editingSetId=null;guidedOrder.sizeSetEditor.reset();editingLine=-1;guidedOrder.setEditingLine(false);draftFiles.clear();guidedOrder.individualPrintTextBox.setSelected(false);guidedOrder.printColorField.setText("");guidedOrder.fileSummaryLabel.setText("No files attached.");customerItem=null;refreshCustomerItem();applyPrice();guidedOrder.lineQuantityField.setText("1");guidedOrder.lineNotesArea.setText("");guidedOrder.widthField.setText("");guidedOrder.lengthField.setText("");guidedOrder.designOrientationBox.setSelectedIndex(0);guidedOrder.lineDiscountPercentField.setText("0");guidedOrder.lineDiscountReasonField.setText("");guidedOrder.priceOverrideReasonField.setText("");guidedOrder.printAddonModel.setRowCount(0);updatePrintAddonSummary();}
    private Map<CartLine,Integer> cartQuantityGroups(){
        Map<CartLine,Integer> groups=new java.util.LinkedHashMap<>();
        for(CartLine line:lines)groups.merge(line,1,Integer::sum);
        return groups;
    }
    private void refreshQuantitySummary(){
        guidedOrder.quantitySummaryModel.clear();summarySetIds.clear();java.util.Set<String> seen=new java.util.HashSet<>();
        for(var entry:cartQuantityGroups().entrySet()){
            String id=entry.getKey().setId();
            if(id!=null){
                var request=entry.getKey().request();
                if(!seen.add(id+":"+request.customVariantId()))continue;
                var members=lines.stream().filter(line->id.equals(line.setId())&&java.util.Objects.equals(request.customVariantId(),line.request().customVariantId())).toList();
                BigDecimal sum=members.stream().map(line->line.request().unitPrice()).reduce(BigDecimal.ZERO,BigDecimal::add);
                String individual=members.stream().filter(line->line.personalization()!=null)
                    .map(line->String.join(", ",line.personalization().texts().stream().filter(text->!text.isBlank()).toList()))
                    .filter(text->!text.isBlank()).collect(java.util.stream.Collectors.joining("; "));
                var shared=entry.getKey().personalization()==null?request.printAddons():entry.getKey().personalization().sharedAddons();
                guidedOrder.quantitySummaryModel.addElement("▸ "+members.size()+" × "+request.itemName()+" / "+request.itemSize()
                    +(request.itemColor()==null||request.itemColor().isBlank()?"":" / "+request.itemColor())
                    +(pricePending()?" — Price pending":" — $"+money(sum))
                    +(printSummary(shared).isBlank()?"":" | "+printSummary(shared))
                    +(individual.isBlank()?"":" | Names / text: "+individual)
                    +(request.orderInstructions()==null||request.orderInstructions().isBlank()?"":" | "+request.orderInstructions().replace("\n"," / ")));
                summarySetIds.add(id);continue;
            }
            summarySetIds.add(null);
            OrderLineRequest r=entry.getKey().request();int quantity=entry.getValue();
            String dimensions=java.util.stream.Stream.of(r.variantName(),r.itemSize(),r.itemColor())
                    .filter(v->v!=null&&!v.isBlank()).collect(java.util.stream.Collectors.joining(" / "));
            guidedOrder.quantitySummaryModel.addElement(quantity+" × "+r.itemName()
                    +(dimensions.isBlank()?"":" / "+dimensions)+(pricePending()?" — Price pending":" — $"+money(r.unitPrice().multiply(BigDecimal.valueOf(quantity))))
                    +(r.customizationDetails()==null||r.customizationDetails().isBlank()?"":" | "+r.customizationDetails())
                    +(printSummary(r.printAddons()).isBlank()?"":" | "+printSummary(r.printAddons()))
                    +(r.orderInstructions()==null||r.orderInstructions().isBlank()?"":" | "+r.orderInstructions().replace("\n"," / ")));
        }
    }
    private void updateTotal(){if(guidedOrder==null||buildingSet)return;rebuildCartDisplay();refreshQuantitySummary();BigDecimal t=total(),d=minimumDeposit();guidedOrder.lineCountLabel.setText("<html><b>Items</b><br>"+lines.size()+"</html>");guidedOrder.orderTotalLabel.setText("<html><b>Order Total</b><br>"+(pricePending()?"Price pending":"$"+money(t))+"</html>");guidedOrder.minimumDepositLabel.setText("<html><b>Minimum Deposit</b><br>"+(pricePending()?"Optional deposit":"$"+money(d))+"</html>");updatePaymentPreview();}
    private boolean pricePending(){return guidedOrder!=null&&guidedOrder.pricePendingBox.isSelected();}
    private BigDecimal total(){CustomerOption customer=guidedOrder==null?null:guidedOrder.customerInfoPanel.getSelectedCustomer();BigDecimal rate=customer!=null&&guidedOrder.customerInfoPanel.applyCustomerDiscount()&&customer.customOrderDiscountEnabled()?customer.customOrderDiscountPercent():BigDecimal.ZERO;return CurrencyFormatter.customOrderTotal(lines.stream().map(x->{OrderLineRequest line=x.request();BigDecimal manual=line.lineDiscountPercent()==null?BigDecimal.ZERO:line.lineDiscountPercent();BigDecimal effective=rate==null?manual:rate.max(manual);BigDecimal original=line.originalLineTotal()==null?line.unitPrice():line.originalLineTotal();return CurrencyFormatter.normalize(original.subtract(original.multiply(effective).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP)).max(BigDecimal.ZERO));}).reduce(BigDecimal.ZERO,BigDecimal::add),roundToNearestTwenty);}
    private void saveOrder(boolean printOrderSlip){
        if(pendingHoldId!=null||pendingResumeId!=null){JOptionPane.showMessageDialog(this,"Retry the pending hold or resume first.");return;}
        if(!orderSubmission.begin())return;
        if(orderSubmission.pending()!=null){submitOrder(printOrderSlip);return;}
        try{prepareOrder(printOrderSlip);}finally{if(orderSubmission.pending()==null)orderSubmission.failed();}
    }
    private List<OrderLineRequest> approvedCartRequests(){
        List<OrderLineRequest> requests=new ArrayList<>();Map<String,String> approvals=new java.util.HashMap<>(),approvedReasons=new java.util.HashMap<>();
        for(CartLine cart:lines){
            var line=cart.request();JsonObject json=GSON.toJsonTree(line).getAsJsonObject();
            if(!pricePending()&&line.lineDiscountPercent()!=null&&line.lineDiscountPercent().signum()>0&&!can("CUSTOM_ORDER_LINE_DISCOUNT")&&!can("CUSTOM_ORDER_OVERRIDES")&&(line.lineDiscountApprovalToken()==null||line.lineDiscountApprovalToken().isBlank())){
                String key="discount:"+line.lineDiscountReason(),token=approvals.get(key);
                if(token==null){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_LINE_DISCOUNT","Custom Order Line Discount Override","Approve the resumed cart's discount: "+line.lineDiscountReason());if(approval==null)return null;token=approval.lanApprovalToken();approvals.put(key,token);approvedReasons.put(key,approval.reason());}
                json.addProperty("lineDiscountApprovalToken",token);json.addProperty("lineDiscountReason",approvedReasons.get(key));
            }
            if(!pricePending()&&(line.priceOverridePrice()!=null||(line.priceOverrideReason()!=null&&!line.priceOverrideReason().isBlank()))&&!can("CUSTOM_ORDER_PRICE_OVERRIDE")&&!can("CUSTOM_ORDER_OVERRIDES")&&(line.priceOverrideApprovalToken()==null||line.priceOverrideApprovalToken().isBlank())){
                String key="price:"+line.priceOverrideReason(),token=approvals.get(key);
                if(token==null){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_PRICE_OVERRIDE","Custom Order Price Override","Approve the resumed cart's price override: "+line.priceOverrideReason());if(approval==null)return null;token=approval.lanApprovalToken();approvals.put(key,token);approvedReasons.put(key,approval.reason());}
                json.addProperty("priceOverrideApprovalToken",token);json.addProperty("priceOverrideReason",approvedReasons.get(key));
            }
            requests.add(GSON.fromJson(json,OrderLineRequest.class));
        }return requests;
    }
    private void prepareOrder(boolean printOrderSlip){
        if(editingLine>=0){JOptionPane.showMessageDialog(this,"Update the edited line or cancel editing before saving.");return;}
        try{
            if(lines.isEmpty())throw new IllegalArgumentException("Add at least one order line.");
            String customer=guidedOrder.customerInfoPanel.getCustomerName().trim();if(customer.isBlank())throw new IllegalArgumentException("Enter a customer name.");
            String phone=guidedOrder.customerInfoPanel.getCustomerPhone().trim();if(phone.isBlank())throw new IllegalArgumentException("Enter a customer phone number.");
            LocalDate due=guidedOrder.dueDateEnabledBox.isSelected()?guidedOrder.dueDateField.getSelectedDate():null;
            BigDecimal total=total(),paid=wholeDollar(guidedOrder.upfrontPaymentField,"upfront payment",true);
            if(paid.signum()<0||(!pricePending()&&paid.compareTo(total)>0))throw new IllegalArgumentException("Upfront payment must be between zero and the total.");
            String method=paid.signum()==0?selectedPaymentMethod:selectedPaymentMethod;if(paid.signum()>0&&(method==null||method.isBlank()))throw new IllegalArgumentException("Select a payment method.");
            if("ACCOUNT".equals(method)&&paid.signum()>0)throw new IllegalArgumentException("Account charges use the unpaid balance. Leave upfront payment at 0.");
            if(paid.signum()>0&&requiresPaymentReference(method)&&guidedOrder.paymentReferenceField.getText().isBlank())throw new IllegalArgumentException("Enter a payment reference.");
            BigDecimal required=minimumDeposit();
            String deferReason=null,deferToken=null;
            if(pricePending()) {
                if(can("CUSTOM_ORDER_DEFER_PRICING")) {
                    deferReason=JOptionPane.showInputDialog(this,"Reason for accepting this order before its price is known:");
                    if(deferReason==null)return;
                } else {
                    var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_DEFER_PRICING","Defer Custom Order Pricing","Reason for accepting an order before final pricing:");
                    if(approval==null)return;deferReason=approval.reason();deferToken=approval.lanApprovalToken();
                }
                if(deferReason.isBlank())throw new IllegalArgumentException("Enter a pricing deferral reason.");
                required=BigDecimal.ZERO;
            }
            String depositReason=null,depositToken=null;
            if(paid.compareTo(required)<0){
                if(can("CUSTOM_ORDER_DEPOSIT_OVERRIDE")||can("CUSTOM_ORDER_OVERRIDES")){
                    depositReason=guidedOrder.depositOverrideReasonField.getText().trim();if(depositReason.isBlank())depositReason=JOptionPane.showInputDialog(this,"Reason for accepting less than the required deposit:","Deposit Override",JOptionPane.QUESTION_MESSAGE);
                    if(depositReason==null)return;
                }else{
                    ManagerApprovalService.ApprovalResult approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_DEPOSIT_OVERRIDE","Custom Order Deposit Override","Reason for custom order deposit override:");
                    if(approval==null)return;depositReason=approval.reason();depositToken=approval.lanApprovalToken();
                }
                if(depositReason==null||depositReason.isBlank())throw new IllegalArgumentException("Enter a deposit override reason.");
            }
            CustomerOption selected=guidedOrder.customerInfoPanel.getSelectedCustomer();
            LanApiClient.ChargeAuthorization authorization=null;if(!pricePending()&&total.subtract(paid).signum()>0&&selected!=null&&selected.requireChargeAuthorization()){authorization=ui.helpers.CustomerChargeAuthorizationDialog.capture(this,selected.name());if(authorization==null)return;}
            List<OrderLineRequest>requests=approvedCartRequests();if(requests==null)return;
            List<CartLine> pendingLines=List.copyOf(lines);
            List<List<FileUploadApproval>> pendingApprovals=new ArrayList<>();
            for(CartLine pendingLine:pendingLines){
                List<FileUploadApproval> approvals=new ArrayList<>();
                for(Path path:pendingLine.pendingFiles()){
                    String token=null,reason=null;
                    if(Files.size(path)>customOrderFileLimitBytes){
                        if(can("CUSTOM_ORDER_FILE_SIZE_OVERRIDE")){
                            reason=JOptionPane.showInputDialog(this,"Reason for uploading "+path.getFileName()+" above the company limit:");
                            if(reason==null)return;
                        }else{
                            var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_FILE_SIZE_OVERRIDE","Custom Order File Size Override","Reason for uploading "+path.getFileName()+" above the company limit:");
                            if(approval==null)return;
                            token=approval.lanApprovalToken();reason=approval.reason();
                        }
                        if(reason==null||reason.isBlank())throw new IllegalArgumentException("Enter a file size override reason.");
                    }
                    approvals.add(new FileUploadApproval(path,token,reason));
                }
                pendingApprovals.add(approvals);
            }
            String sourceWebsiteShirtRequestId=websiteShirtRequestId;
            OrderSaveRequest request=new OrderSaveRequest(selected,customer,phone,due,total,paid,total.subtract(paid),method,guidedOrder.paymentReferenceField.getText(),paid.signum()==0?"UNPAID":paid.compareTo(total)==0?"PAID":"PARTIAL",SessionManager.getCurrentUserId(),SessionManager.getCurrentUserDisplayName(),SessionManager.getCurrentLocationId(),SessionManager.getCurrentLocationName(),SessionManager.getCurrentDeviceId(),"",required,depositReason,null,null,guidedOrder.orderNotesArea.getText(),requests,depositToken,authorization,guidedOrder.customerInfoPanel.applyCustomerDiscount(),pricePending()?"PENDING":"FINAL",deferReason,deferToken);
            String submissionKey=orderSubmission.key();
            orderSubmission.prepare(()->{
                String number=CustomOrderDataService.saveCustomOrder(request,submissionKey);
                try{EmailOutboxService.queueCustomOrderConfirmation(number,true);}catch(Exception ignored){}
                List<String> fileErrors=new ArrayList<>();
                if(pendingLines.stream().anyMatch(line->!line.pendingFiles().isEmpty()))try{
                    var orders=LanApiClient.customOrderWorkflowRead("LOOKUP",null,number).getAsJsonArray("orders");Long savedId=null;
                    for(var item:orders){JsonObject order=item.getAsJsonObject();if(number.equals(order.get("orderNumber").getAsString())){savedId=order.get("orderId").getAsLong();break;}}
                    if(savedId==null)throw new IllegalStateException("Saved order could not be found for file upload.");
                    var savedLines=LanApiClient.customOrderWorkflowRead("PRODUCTION",savedId,null).getAsJsonArray("lines");
                    if(savedLines.size()!=pendingLines.size())throw new IllegalStateException("Saved order lines did not match the selected files.");
                    for(int i=0;i<pendingLines.size();i++){long lineId=savedLines.get(i).getAsJsonObject().get("lineId").getAsLong();for(FileUploadApproval upload:pendingApprovals.get(i))try{CustomOrderMediaDialog.uploadOne(savedId,lineId,"ATTACHMENT",upload.path(),upload.token(),upload.reason(),null);}catch(Exception uploadFailure){fileErrors.add(upload.path().getFileName()+": "+uploadFailure.getMessage());}}
                }catch(Exception uploadFailure){fileErrors.add(uploadFailure.getMessage());}
                if(sourceWebsiteShirtRequestId!=null)try{
                    var found=LanApiClient.customOrderWorkflowRead("LOOKUP",null,number).getAsJsonArray("orders");Long orderId=null;for(var e:found){var o=e.getAsJsonObject();if(number.equals(o.get("orderNumber").getAsString()))orderId=o.get("orderId").getAsLong();}
                    if(orderId==null)throw new IllegalStateException("Saved order ID unavailable.");JsonObject link=new JsonObject();link.addProperty("action","QUOTE_LINK_ORDER");link.addProperty("requestId",sourceWebsiteShirtRequestId);link.addProperty("customOrderId",orderId);LanApiClient.storefrontAdmin(link,UUID.randomUUID().toString());
                }catch(Exception e){fileErrors.add("Website request link: "+e.getMessage()+". Use Company Preferences → Custom requests → Link SmartStock order.");}
                return new SaveOutcome(number,fileErrors);
            });
            submitOrder(printOrderSlip);
        }catch(Exception e){error(e);}
    }
    private void submitOrder(boolean printOrderSlip){
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this,"custom-orders.save",orderSubmission.pending(),outcome->{
                loadingState.ready(java.time.Instant.now());
                JOptionPane.showMessageDialog(this,"Custom order "+outcome.orderNumber()+" saved."+(outcome.fileErrors().isEmpty()?"":"\nSome attachments need to be added from Files & Design Approval:\n"+String.join("\n",outcome.fileErrors())));
                orderSubmission.completed();
                SessionDataCache.invalidate("custom-orders:");clearOrder();loadOrders();
                if(printOrderSlip || alwaysPrintOrderSlip)promptAndPrintSlip(outcome.orderNumber());
            },failure->{if(failure instanceof LanApiClient.LanApiException api && java.util.Set.of("VALIDATION_ERROR","CUSTOM_ORDER_CASH_DRAWER_REQUIRED","CUSTOM_ORDER_DEVICE_NOT_ALLOWED","APPROVAL_REQUIRED","APPROVAL_INVALID","PERMISSION_DENIED").contains(api.code())){orderSubmission.completed();loadingState.failed(failure.getMessage(),false,()->saveOrder(printOrderSlip));return;}orderSubmission.failed();loadingState.failed("Order save could not be confirmed. Retry checks the original order: "+failure.getMessage(),false,()->saveOrder(printOrderSlip));});
    }
    private CustomOrderHoldTransfer.Prepared pendingHold;
    private String pendingHoldId,pendingResumeId,pendingResumeKey;
    private CustomOrderHoldTransfer.Restored pendingResume;
    private final Map<Component,Boolean> holdEnabled=new java.util.IdentityHashMap<>();
    private void lockHeldDraft(Component component){
        holdEnabled.putIfAbsent(component,component.isEnabled());
        if(component instanceof JButton button&&"Hold Cart".equals(button.getText()))return;
        component.setEnabled(false);if(component instanceof Container container)for(Component child:container.getComponents())lockHeldDraft(child);
    }
    private void unlockHeldDraft(){holdEnabled.forEach(Component::setEnabled);holdEnabled.clear();setDefaultCloseOperation(DISPOSE_ON_CLOSE);}
    private <T> void holdTask(String text,java.util.concurrent.Callable<T> work,Consumer<T> success){
        JDialog dialog=new JDialog(this,text,true);dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JProgressBar progress=new JProgressBar();progress.setIndeterminate(true);JPanel panel=new JPanel(new BorderLayout(12,12));panel.setBorder(new EmptyBorder(20,20,20,20));panel.add(new JLabel(text),BorderLayout.NORTH);panel.add(progress);dialog.add(panel);dialog.pack();dialog.setLocationRelativeTo(this);
        new SwingWorker<T,Void>(){
            protected T doInBackground()throws Exception{return work.call();}
            protected void done(){dialog.dispose();try{success.accept(get());}catch(Exception e){Throwable cause=e instanceof java.util.concurrent.ExecutionException?e.getCause():e;if(cause instanceof LanApiClient.LanApiException api&&"CUSTOM_HOLD_UNAVAILABLE".equals(api.code())&&pendingResumeId!=null){pendingResume=null;pendingResumeId=null;pendingResumeKey=null;unlockHeldDraft();JOptionPane.showMessageDialog(CustomOrders.this,"This held cart has already been resumed on another register.");return;}JOptionPane.showMessageDialog(CustomOrders.this,message(cause)+"\nYour cart has been kept. Retry the same action to confirm it.","Custom Order Hold",JOptionPane.ERROR_MESSAGE);}}
        }.execute();dialog.setVisible(true);
    }
    private void holdCart(){
        if(orderSubmission.unresolved()){JOptionPane.showMessageDialog(this,"Confirm the pending order by retrying Save first.");return;}
        if(pendingResumeId!=null){JOptionPane.showMessageDialog(this,"Retry Resume Held Cart to confirm the pending resume first.");return;}
        if(pendingHoldId!=null){int choice=JOptionPane.showOptionDialog(this,"The previous hold has not been confirmed.","Pending Hold",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,new String[]{"Retry Hold","Cancel Pending Hold","Keep Cart Open"},"Retry Hold");if(choice==1){holdTask("Checking pending hold…",()->CustomOrderHoldTransfer.call("ABANDON",pendingHoldId),result->{if("HELD".equals(result.get("status").getAsString())){heldSuccessfully();}else if("RESUMED".equals(result.get("status").getAsString())){pendingHold=null;pendingHoldId=null;unlockHeldDraft();clearOrder();JOptionPane.showMessageDialog(this,"This cart was already resumed on another register.");}else{pendingHold=null;pendingHoldId=null;unlockHeldDraft();}});return;}if(choice!=0)return;holdTask("Confirming held cart…",()->{if(pendingHold==null)throw new IllegalStateException("Retry could not prepare the draft.");CustomOrderHoldTransfer.hold(pendingHold);return true;},ignored->heldSuccessfully());return;}
        if(lines.isEmpty()){JOptionPane.showMessageDialog(this,"Add at least one item before holding the cart.");return;}
        if(!guidedOrder.sizeSetEditor.selections().isEmpty()||editingLine>=0||customerItem!=null||inventoryProductId!=null||!draftFiles.isEmpty()||guidedOrder.printAddonModel.getRowCount()>0||!guidedOrder.lineNotesArea.getText().isBlank()||!guidedOrder.widthField.getText().isBlank()||!guidedOrder.lengthField.getText().isBlank()){
            JOptionPane.showMessageDialog(this,"Add or save the item you are working on before holding the cart.");return;
        }
        String name=JOptionPane.showInputDialog(this,"Name for this held cart:",guidedOrder.customerInfoPanel.getCustomerName().isBlank()?"Custom order":guidedOrder.customerInfoPanel.getCustomerName());if(name==null)return;name=name.trim();if(name.isBlank()||name.length()>200){JOptionPane.showMessageDialog(this,"Enter a hold name of up to 200 characters.");return;}
        Map<Path,String> ids=new java.util.LinkedHashMap<>();Map<String,Path> files=new java.util.LinkedHashMap<>();List<CustomOrderHeldDraft.Line> saved=new ArrayList<>();
        for(var line:lines){List<String> attached=new ArrayList<>();for(Path path:line.pendingFiles()){String id=ids.computeIfAbsent(path.toAbsolutePath().normalize(),p->UUID.randomUUID().toString());files.put(id,path);attached.add(id);}saved.add(new CustomOrderHeldDraft.Line(line.request(),line.item(),line.variant(),attached,line.setId(),line.personalization()));}
        var draft=new CustomOrderHeldDraft(guidedOrder.customerInfoPanel.draft(),pricePending(),guidedOrder.dueDateEnabledBox.isSelected(),guidedOrder.dueDateField.getText(),guidedOrder.orderNotesArea.getText(),selectedPaymentMethod,guidedOrder.paymentReferenceField.getText(),guidedOrder.upfrontPaymentField.getText(),guidedOrder.depositOverrideReasonField.getText(),saved,List.of());
        String holdName=name,id=UUID.randomUUID().toString();
        holdTask("Saving held cart and files…",()->{
            var prepared=CustomOrderHoldTransfer.prepare(id,holdName,draft,files);
            pendingHold=prepared;pendingHoldId=id;CustomOrderHoldTransfer.hold(prepared);return true;
        },ignored->heldSuccessfully());
        if(pendingHoldId!=null){lockHeldDraft(guidedOrder);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);JOptionPane.showMessageDialog(this,"Hold could not be confirmed. Click Hold Cart again to retry safely before changing or closing this cart.");}
    }
    private void heldSuccessfully(){pendingHold=null;pendingHoldId=null;unlockHeldDraft();clearOrder();JOptionPane.showMessageDialog(this,"Cart held. It can be resumed on any register in this store.");}
    private void resumeHeldCart(){
        if(pendingHoldId!=null){JOptionPane.showMessageDialog(this,"Retry Hold Cart to confirm the pending hold first.");return;}
        if(orderSubmission.unresolved()||!lines.isEmpty()||editingLine>=0||!draftFiles.isEmpty()){
            JOptionPane.showMessageDialog(this,"Hold or clear the current cart before resuming another one.");return;
        }
        if(pendingResumeId!=null){claimHeldCart();return;}
        holdTask("Loading held carts…",()->CustomOrderHoldTransfer.call("LIST",null).getAsJsonArray("holds"),holds->{
            if(holds.isEmpty()){JOptionPane.showMessageDialog(this,"There are no held custom-order carts in this store.");return;}
            String[] labels=new String[holds.size()];for(int i=0;i<labels.length;i++){var row=holds.get(i).getAsJsonObject();labels[i]=row.get("name").getAsString()+" — "+row.get("items").getAsInt()+" items — "+row.get("createdAt").getAsString();}
            JComboBox<String> selector=new JComboBox<>(labels);if(JOptionPane.showConfirmDialog(this,selector,"Resume Held Custom Cart",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            String id=holds.get(selector.getSelectedIndex()).getAsJsonObject().get("id").getAsString();
            holdTask("Restoring held cart and files…",()->CustomOrderHoldTransfer.download(id),restored->{
                // Validate and construct all rows before claiming the hold.
                for(var line:restored.draft().lines())cartRow(new CartLine(line.request(),line.files().stream().map(restored.files()::get).toList(),line.item(),line.variant(),line.setId(),line.personalization()));
                pendingResume=restored;pendingResumeId=id;pendingResumeKey=UUID.randomUUID().toString();claimHeldCart();
            });
        });
    }
    private void claimHeldCart(){
        holdTask("Claiming held cart…",()->LanApiClient.customOrderHold(CustomOrderHoldTransfer.request("RESUME",pendingResumeId),pendingResumeKey),result->{
            unlockHeldDraft();restoreHeldDraft(pendingResume);pendingResume=null;pendingResumeId=null;pendingResumeKey=null;
        });
        if(pendingResumeId!=null){lockHeldDraft(guidedOrder);for(var entry:holdEnabled.entrySet())if(entry.getKey() instanceof JButton button&&"Resume Held Cart".equals(button.getText()))button.setEnabled(true);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);}
    }
    private void restoreHeldDraft(CustomOrderHoldTransfer.Restored restored){
        var draft=restored.draft();guidedOrder.pricePendingBox.setSelected(draft.pricePending());guidedOrder.customerInfoPanel.restoreDraft(draft.customer());guidedOrder.dueDateEnabledBox.setSelected(draft.dueEnabled());guidedOrder.dueDateField.setText(draft.dueDate());guidedOrder.dueDateField.setEnabled(draft.dueEnabled());guidedOrder.orderNotesArea.setText(draft.notes());guidedOrder.paymentReferenceField.setText(draft.paymentReference());guidedOrder.upfrontPaymentField.setText(draft.deposit());guidedOrder.depositOverrideReasonField.setText(draft.depositOverrideReason());selectedPaymentMethod=draft.paymentMethod();guidedOrder.paymentMethodGroup.clearSelection();
        if(selectedPaymentMethod!=null)switch(selectedPaymentMethod){case "CASH"->guidedOrder.cashPaymentButton.setSelected(true);case "CARD"->guidedOrder.cardPaymentButton.setSelected(true);case "CHEQUE"->guidedOrder.chequePaymentButton.setSelected(true);case "MMG"->guidedOrder.mmgPaymentButton.setSelected(true);case "ACCOUNT"->guidedOrder.accountPaymentButton.setSelected(true);default->{}}
        lines.clear();guidedOrder.orderLineModel.setRowCount(0);for(var line:draft.lines()){CartLine cart=new CartLine(line.request(),line.files().stream().map(restored.files()::get).toList(),line.item(),line.variant(),line.setId(),line.personalization());lines.add(cart);guidedOrder.orderLineModel.addRow(cartRow(cart));}clearLine();updateTotal();guidedOrder.showLinesStep();
    }
    private List<CustomOrderSizeSetEditor.SizeQuantity> setSizes(String id){
        Map<VariantOption,Integer> quantities=new java.util.LinkedHashMap<>();
        for(CartLine line:lines)if(id.equals(line.setId()))quantities.merge(line.variant(),1,Integer::sum);
        return quantities.entrySet().stream().map(e->new CustomOrderSizeSetEditor.SizeQuantity(e.getKey(),e.getValue())).toList();
    }
    private void rebuildCartDisplay(){
        if(cartSorter==null||buildingSet)return;
        cartSorter.setRowFilter(null);guidedOrder.orderLineModel.setRowCount(0);
        Map<String,Integer> numbers=new java.util.HashMap<>();
        for(int index=0;index<lines.size();index++){
            CartLine line=lines.get(index);Object[] row=cartRow(line);String id=line.setId();
            row[34]=numbers.merge((id==null?"line:"+index:id)+":"+line.request().customVariantId(),1,Integer::sum);
            guidedOrder.orderLineModel.addRow(row);
        }
    }
    private Object[] cartRow(CartLine line){
        var r=line.request();return new Object[]{r.customItemId(),r.customVariantId(),r.itemName(),r.variantName()==null?"":r.variantName(),r.pricingType(),pricePending()?"Price pending":money(r.unitPrice()),r.customizationDetails(),r.orderInstructions(),r.widthValue(),r.lengthValue(),r.dimensionUnit(),r.areaValue(),r.areaUnit(),r.areaPrice(),null,"",null,"",money(r.printCharge()),money(r.baseItemPrice()),r.printLineCount(),printSummary(r.printAddons()),"",money(r.originalLineTotal()),decimalText(r.lineDiscountPercent()),money(r.lineDiscountAmount()),r.lineDiscountReason(),"0",r.originalBasePrice(),r.priceOverridePrice(),r.priceOverrideReason(),line.pendingFiles().size(),r.itemSize(),r.itemColor(),1};
    }
    private void clearOrder(){if(pendingHoldId!=null||pendingResumeId!=null){JOptionPane.showMessageDialog(this,"Retry the pending hold or resume first.");return;}if(orderSubmission.unresolved()){JOptionPane.showMessageDialog(this,"Confirm the pending order by retrying Save before clearing it.");return;}websiteShirtRequestId=null;lines.clear();guidedOrder.pricePendingBox.setSelected(false);guidedOrder.orderLineModel.setRowCount(0);guidedOrder.customerInfoPanel.clear();guidedOrder.dueDateEnabledBox.setSelected(false);guidedOrder.dueDateField.setText("");guidedOrder.dueDateField.setEnabled(false);guidedOrder.orderNotesArea.setText("");guidedOrder.upfrontPaymentField.setText("0");guidedOrder.paymentReferenceField.setText("");guidedOrder.depositOverrideReasonField.setText("");selectedPaymentMethod=null;guidedOrder.paymentMethodGroup.clearSelection();guidedOrder.showLinesStep();clearLine();updateTotal();}

    private boolean validateStep(int step){
        if(editingLine>=0){JOptionPane.showMessageDialog(this,"Update the edited line or cancel editing before continuing.");return false;}
        if(step==0&&lines.isEmpty()){JOptionPane.showMessageDialog(this,"Add at least one custom order line before review.");return false;}
        if(step==1&&guidedOrder.dueDateEnabledBox.isSelected())try{guidedOrder.dueDateField.getSelectedDate();}catch(Exception e){JOptionPane.showMessageDialog(this,"Due date must use YYYY-MM-DD.");return false;}
        if(step==2){if(guidedOrder.customerInfoPanel.getCustomerName().isBlank()){JOptionPane.showMessageDialog(this,"Customer name is required before payment.");return false;}if(guidedOrder.customerInfoPanel.getCustomerPhone().isBlank()){JOptionPane.showMessageDialog(this,"Customer phone number is required before payment.");return false;}}
        return true;
    }
    private void refreshStep(int step){updateTotal();if(step==1){guidedOrder.reviewLineCountLabel.setText("Items: "+lines.size());guidedOrder.reviewOrderTotalLabel.setText(pricePending()?"Order Total: Price pending":"Order Total: $"+money(total()));guidedOrder.reviewMinimumDepositLabel.setText(pricePending()?"Optional deposit":"Minimum Deposit Required: $"+money(minimumDeposit()));guidedOrder.reviewLineModel.clear();for(var group:cartQuantityGroups().entrySet()){CartLine line=group.getKey();int quantity=group.getValue();OrderLineRequest r=line.request();String addOns=printSummary(r.printAddons());guidedOrder.reviewLineModel.addElement(quantity+" × "+r.itemName()+(r.variantName()==null||r.variantName().isBlank()?"":" / "+r.variantName())+(r.customizationDetails()==null||r.customizationDetails().isBlank()?"":" | "+r.customizationDetails())+(pricePending()?" - Price pending":" - $"+money(r.unitPrice().multiply(BigDecimal.valueOf(quantity))))+(addOns.isBlank()?"":" | Add-ons: "+addOns)+(r.orderInstructions()==null||r.orderInstructions().isBlank()?"":" | "+r.orderInstructions().replace("\n"," / ")));}}if(step==3){String account=guidedOrder.customerInfoPanel.getCustomerAccountNumber(),email=guidedOrder.customerInfoPanel.getCustomerEmail();guidedOrder.customerSummaryLabel.setText("Customer: "+guidedOrder.customerInfoPanel.getCustomerName()+" / "+guidedOrder.customerInfoPanel.getCustomerPhone()+(account.isBlank()?"":" / Account # "+account)+(email.isBlank()?"":" / "+email));updatePaymentPreview();}}
    private boolean customerDepositExempt(){var customer=guidedOrder.customerInfoPanel.getSelectedCustomer();return customer!=null&&customer.customerId()!=null&&customer.customOrderDepositExempt();}
    private BigDecimal minimumDeposit(){if(pricePending()||customerDepositExempt())return BigDecimal.ZERO;return CurrencyFormatter.normalize(total().multiply(minimumDepositPercent).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP));}
    private void updatePaymentPreview(){if(guidedOrder==null)return;BigDecimal paid;try{paid=new BigDecimal(guidedOrder.upfrontPaymentField.getText().trim().isBlank()?"0":guidedOrder.upfrontPaymentField.getText().trim());}catch(Exception e){paid=BigDecimal.ZERO;}BigDecimal required=minimumDeposit();guidedOrder.paymentMinimumDepositLabel.setText(pricePending()?"Optional deposit — final price is pending":customerDepositExempt()?"No deposit required — customer account setting":"Minimum Deposit Required: $"+money(required));guidedOrder.balanceDueLabel.setText(pricePending()?"Balance Due: Price pending":"Balance Due: $"+money(total().subtract(paid).max(BigDecimal.ZERO)));guidedOrder.depositOverrideNoticeLabel.setText(paid.compareTo(required)<0?"A deposit override and reason are required.":" ");boolean ref=requiresPaymentReference(selectedPaymentMethod);guidedOrder.paymentReferenceField.setEnabled(ref);if(!ref)guidedOrder.paymentReferenceField.setText("");}
    static boolean requiresPaymentReference(String paymentMethod){return "CARD".equals(paymentMethod)||"CHEQUE".equals(paymentMethod)||"MMG".equals(paymentMethod);}
    private void updateAreaPreview(){if(guidedOrder==null)return;CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();if(item==null||!"AREA".equals(item.pricingType())){guidedOrder.areaCalculationLabel.setText(" ");return;}try{BigDecimal w=nullable(guidedOrder.widthField),l=nullable(guidedOrder.lengthField),rate=decimal(guidedOrder.linePriceField,"line price",false);if(w==null||l==null||rate==null){guidedOrder.areaCalculationLabel.setText("Enter width and length to calculate area.");return;}BigDecimal area=billableArea(w,l,item.maxWidth(),item.dimensionUnit(),item.areaPriceUnit());guidedOrder.areaCalculationLabel.setText("Charged: "+decimalText(billingWidth(w,item.maxWidth()))+" x "+decimalText(l)+" "+item.dimensionUnit()+" = "+area.stripTrailingZeros().toPlainString()+" "+item.areaPriceUnit()+" = $"+money(area.multiply(rate)));}catch(Exception e){guidedOrder.areaCalculationLabel.setText("Enter valid dimensions.");}}
    private String printSummary(List<PrintAddonRequest>a){StringBuilder s=new StringBuilder();for(PrintAddonRequest x:a){if(s.length()>0)s.append("; ");s.append(x.materialName()).append(" / ").append(x.printSizeName()).append(x.printDescription()==null||x.printDescription().isBlank()?"":" / "+x.printDescription()).append(pricePending()?" · Price pending":" $"+money(x.printCharge()));}return s.toString();}
    private void updatePrintAddonSummary(){if(guidedOrder==null)return;List<PrintAddonRequest>addOns=printAddons();String summary=printSummary(addOns);guidedOrder.printAddonSummaryLabel.setText(addOns.isEmpty()?"No print add-ons added.":"Print add-ons ("+addOns.size()+"): "+summary);guidedOrder.printAddonSummaryLabel.setToolTipText(addOns.isEmpty()?null:summary);}
    private static Long longValue(Object v){if(v==null||String.valueOf(v).isBlank()||"null".equals(String.valueOf(v)))return null;return Long.valueOf(String.valueOf(v));}
    private static void selectItem(JComboBox<CustomItemOption>b,Long id){for(int i=0;i<b.getItemCount();i++)if(java.util.Objects.equals(id,b.getItemAt(i).customItemId())){b.setSelectedIndex(i);return;}}
    private static void selectVariant(JComboBox<VariantOption>b,Long id){for(int i=0;i<b.getItemCount();i++)if(java.util.Objects.equals(id,b.getItemAt(i).variantId())){b.setSelectedIndex(i);return;}}

    private static Catalog fetchCatalog() throws Exception {
var snapshot=UiTaskRunner.supplyAsync(CustomOrderDataService::loadCatalogSnapshot);var settings=UiTaskRunner.supplyAsync(CompanyCustomizationManager::loadCustomOrderSettings);var slip=UiTaskRunner.supplyAsync(CompanyCustomizationManager::loadCustomOrderSlipSettings);var catalog=snapshot.join();var orderSettings=settings.join();BigDecimal deposit=orderSettings.minimumDepositPercent();var slipSettings=slip.join();return new Catalog(catalog.items(),catalog.materials(),catalog.placements(),catalog.initialCustomers(),deposit==null?BigDecimal.ZERO:deposit,orderSettings.fileLimitBytes(),orderSettings.roundToNearestTwenty(),slipSettings.enabled()&&slipSettings.autoPrint());
    }

    /** Background login warmup; historical order requests are deliberately excluded. */
    public static void warmAfterLogin() {
        long generation=SessionDataCache.generation();
        if(can("CREATE_CUSTOM_ORDER")) warmValue(generation,"custom-orders:catalog",CustomOrders::fetchCatalog);
        if(can("CUSTOM_ORDER_LOOKUP")||can("MANAGE_CUSTOM_ORDERS")||can("CREATE_CUSTOM_ORDER")||can("CUSTOM_ORDER_OVERRIDES")) {
            warmValue(generation,"custom-orders:LOOKUP:",()->new LookupSnapshot(readWorkflow("LOOKUP","")));
            warmValue(generation,"custom-orders:orders",()->new OrderSnapshot(readWorkflow("CURRENT_MINE","")));
        }
    }
    private static <T> void warmValue(long generation,String key,java.util.concurrent.Callable<T> load) {
        UiTaskRunner.supplyAsync(()->{
            if(generation!=SessionDataCache.generation())return null;
            T value=load.call();
            SessionDataCache.putIfGeneration(generation,key,value);
            return value;
        }).exceptionally(failure->null);
    }
    private static List<LanCustomOrderWorkflowService.OrderRow> readWorkflow(String action,String search)throws Exception {
        return GSON.fromJson(LanApiClient.customOrderWorkflowRead(action,null,search).get("orders"),
                new TypeToken<List<LanCustomOrderWorkflowService.OrderRow>>(){}.getType());
    }
    private void loadOrders(){CachedUiLoader.load(this,"custom-orders:orders",OrderSnapshot.class,SessionDataCache.SCREEN_TTL,loadingState,()->new OrderSnapshot(workflow("CURRENT_MINE","")),snapshot->{renderOrders(myModel,snapshot.mine(),false);if(lookup!=null)lookup.load(handler());if(allOrders!=null&&tabsShowingAllOrders())allOrders.load(handler());});}
    private List<LanCustomOrderWorkflowService.OrderRow>workflow(String action,String search)throws Exception{return GSON.fromJson(LanApiClient.customOrderWorkflowRead(action,null,search).get("orders"),new TypeToken<List<LanCustomOrderWorkflowService.OrderRow>>(){}.getType());}
    private void renderOrders(DefaultTableModel m,List<LanCustomOrderWorkflowService.OrderRow>rows,boolean all){m.setRowCount(0);for(var x:rows)m.addRow(CustomOrderListColumns.values(x,all));}
    private void loadLookupOrdersAsync(DefaultTableModel model,String search) {
        String query=search==null?"":search.trim();
        CustomOrdersLookupTabPanel target=allOrders!=null&&model==allOrders.model?allOrders:lookup;
        boolean all=target==allOrders;
        String action=all?"ALL":"LOOKUP";
        CachedUiLoader.load(this,"custom-orders."+action,"custom-orders:"+action+":"+query,
                LookupSnapshot.class,SessionDataCache.SCREEN_TTL,loadingState,
                ()->new LookupSnapshot(workflow(action,query)),snapshot->{
            if(!query.equals(target.searchField.getText().trim()))return;
            Long selectedId=selected(target.table,model);
            renderOrders(model,snapshot.rows(),false);
            if(selectedId!=null)for(int row=0;row<model.getRowCount();row++)
                if(selectedId.toString().equals(String.valueOf(model.getValueAt(row,0)))){
                    int view=target.table.convertRowIndexToView(row);
                    if(view>=0)target.table.setRowSelectionInterval(view,view);
                    break;
                }
        });
    }
    private void loadDetails(Long id, JTextArea area) {
        area.setText("Loading order details...");
        UiTaskRunner.submit(this, "custom-orders.details", () ->
                        LanApiClient.customOrderWorkflowRead("DETAILS", id, null)
                                .get("details").getAsString(),
                details -> {
                    area.setText(details);
                    area.setCaretPosition(0);
                }, failure -> area.setText("Unable to load order details. Select the order to retry."));
    }
    private void promptAndPrintSlip(String orderNumber) {
        JSpinner receipts = new JSpinner(new SpinnerNumberModel(2, 0, 100, 1));
        JSpinner labels = new JSpinner(new SpinnerNumberModel(1, 0, CustomOrderLabelPrinter.MAX_LABEL_COUNT, 1));
        JPanel panel = form();
        row(panel, 0, "Receipts to print:", receipts);
        row(panel, 1, "Labels to print:", labels);
        row(panel, 2, "Use 0 to skip either type.", new JLabel(""));
        while (JOptionPane.showConfirmDialog(this, panel, "Print Custom Order", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                receipts.commitEdit();
                labels.commitEdit();
                int receiptCount = (Integer) receipts.getValue();
                int labelCount = (Integer) labels.getValue();
                if (receiptCount > 0 || labelCount > 0) printSlipAndLabelsAsync(orderNumber, receiptCount, labelCount);
                return;
            } catch (java.text.ParseException ex) {
                JOptionPane.showMessageDialog(this, "Enter whole numbers from 0 to 100.", "Print Custom Order", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void printSlipAndLabelsAsync(String orderNumber, int receiptCount, int count) {
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this, "custom-orders.print-slip", () -> {
            CustomOrderSlipData data = CustomOrderSlipBuilder.buildFromOrderNumber(orderNumber);
            int printed = 0;
            try {
                for (; printed < receiptCount; printed++) CustomOrderSlipPrinter.print(data);
            } catch (Exception ex) {
                return new OrderPrintResult(data, ex, null, printed);
            }
            if (count == 0) return new OrderPrintResult(data, null, null, printed);
            try {
                CustomOrderLabelPrinter.print(data, count);
                return new OrderPrintResult(data, null, null, printed);
            } catch (Exception ex) {
                return new OrderPrintResult(data, null, ex, printed);
            }
        }, result -> {
            if (result.slipFailure() != null) {
                loadingState.failed("Order " + orderNumber + " was saved, but its slip did not print: " + message(result.slipFailure()), false,
                        () -> printSlipAndLabelsAsync(orderNumber, receiptCount - result.slipsPrinted(), count));
            } else if (result.labelFailure() != null) {
                loadingState.failed("The order slip printed, but the order label did not: " + message(result.labelFailure()), false,
                        () -> printLabelsAsync(orderNumber, count));
            } else {
                loadingState.ready(java.time.Instant.now());
                if (count == 0) {
                    JOptionPane.showMessageDialog(this, receiptCount + " receipt copies sent to the printer. Order labels were skipped.");
                } else {
                    JOptionPane.showMessageDialog(this, receiptCount + " receipt copies and " + count + " label" + (count == 1 ? "" : "s") + " sent to the printers.");
                }
            }
        }, failure -> loadingState.failed("Unable to load order " + orderNumber + " for printing: " + message(failure), false,
                () -> printSlipAndLabelsAsync(orderNumber, receiptCount, count)));
    }

    private void promptAndPrintLabels(String orderNumber) {
        Integer count = CustomOrderLabelPrinter.promptLabelCount(this);
        if (count != null) printLabelsAsync(orderNumber, count);
    }

    private void printLabelsAsync(String orderNumber, int count) {
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this, "custom-orders.print-labels", () -> {
            CustomOrderSlipData data = CustomOrderSlipBuilder.buildFromOrderNumber(orderNumber);
            CustomOrderLabelPrinter.print(data, count);
            return data;
        }, ignored -> {
            loadingState.ready(java.time.Instant.now());
            JOptionPane.showMessageDialog(this, count + " order label" + (count == 1 ? "" : "s") + " sent to the label printer.");
        }, failure -> loadingState.failed("Order labels did not print: " + message(failure), false,
                () -> printLabelsAsync(orderNumber, count)));
    }
    private CustomOrdersLookupTabPanel.Handler handler() {
        return new CustomOrdersLookupTabPanel.Handler() {
            public void loadLookupOrders(DefaultTableModel model, String search) { loadLookupOrdersAsync(model, search); }
            public Long selectedLookupOrderId(JTable table, DefaultTableModel model) { return selected(table, model); }
            public void loadOrderDetails(Long id, JTextArea area) { loadDetails(id, area); }
            public void loadStructuredOrderDetails(Long id, CustomOrderDetailsPanel panel) {
                long token=panel.loading();
                UiTaskRunner.submit(CustomOrders.this,"custom-orders.structured-details",()->LanApiClient.customOrderWorkflowRead("DETAILS_V2",id,null).getAsJsonObject("orderDetails"),data->panel.showDetails(token,data),failure->panel.failed(token,message(failure)));
            }
            public void loadReturnableLines(Long id, Consumer<List<CustomOrdersLookupTabPanel.LineReturnOption>> completion) { loadReturnableLinesAsync(id, completion); }
            public void loadDeliverableLines(Long id, Consumer<List<CustomOrdersLookupTabPanel.LineDeliveryOption>> completion) { loadDeliverableLinesAsync(id, completion); }
            public void loadProductionLines(Long id, Consumer<List<CustomOrdersLookupTabPanel.ProductionLineOption>> completion) { loadProductionLinesAsync(id, completion); }
            public BigDecimal parseNullableMoneyValue(Object value) { try { return new BigDecimal(String.valueOf(value).replace(",", "")); } catch (Exception ex) { return null; } }
            public void applyLookupPayment(Long id, String amount, String method, String reference, Component parent, Consumer<Boolean> completion) {
                try {
                    JsonObject body = mutation("PAYMENT", id);
                    body.addProperty("amount", parseOrderPaymentAmount(amount));
                    body.addProperty("method", method);
                    body.addProperty("reference", reference);
                    executeAsync(body, "Payment applied.", completion);
                } catch (Exception ex) { error(ex); completion.accept(false); }
            }
            public void applyLookupLineRefund(Long id, List<CustomOrdersLookupTabPanel.LineReturnRequest> requests, String method, String reference, String reason, Component parent, Consumer<Boolean> completion) {
                List<LanCustomOrderWorkflowService.ReturnRequest> returns = requests.stream()
                        .map(request -> new LanCustomOrderWorkflowService.ReturnRequest(request.lineId(), request.refundAmount(), request.partial(), request.restockAction()))
                        .toList();
                JsonObject body = mutation("LINE_RETURN", id);
                body.add("returns", GSON.toJsonTree(returns));
                body.addProperty("method", method);
                body.addProperty("reference", reference);
                body.addProperty("reason", reason);
                executeRefundWithApprovalAsync(body, completion);
            }
            public void updateLookupLineDelivery(Long id, List<Long> ids, String status, String notes, Component parent, Consumer<Boolean> completion) {
                if ("DELIVERED".equals(status)) { markLookupLinesDelivered(id,ids,notes,parent,completion); return; }
                JsonObject body=mutation("CALL_LINES",id);
                body.add("lineIds",GSON.toJsonTree(ids));
                body.addProperty("status",status);
                body.addProperty("notes",notes);
                executeAsync(body,"Call status recorded.",completion);
            }
            public void markLookupLinesDelivered(Long id, List<Long> ids, String notes, Component parent, Consumer<Boolean> completion) {
                JsonObject body = mutation("DELIVER_LINES", id);
                body.add("lineIds", GSON.toJsonTree(ids));
                body.addProperty("notes", notes);
                UiTaskRunner.submit(CustomOrders.this,"custom-orders.delivery-pricing",()->LanApiClient.customOrderWorkflowRead("DETAILS_V2",id,null),details->{
                    JsonObject summary=details.getAsJsonObject("orderDetails").getAsJsonObject("summary");
                    if(summary.has("pricing_state")&&"PENDING".equals(summary.get("pricing_state").getAsString())) {
                        var approval=ManagerApprovalService.requestApproval(CustomOrders.this,"CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE","Deliver Custom Order Before Pricing","Reason for delivering this order before final pricing:");
                        if(approval==null){completion.accept(false);return;}body.addProperty("unpricedDeliveryReason",approval.reason());body.addProperty("unpricedDeliveryApprovalToken",approval.lanApprovalToken());
                    }
                    executeAsync(body,"Selected lines delivered.",completion);
                },failure->{error(new Exception(message(failure)));completion.accept(false);});
            }
            public void updateProductionLines(Long id, List<Long> ids, String status, String notes, Component parent, Consumer<Boolean> completion) {
                JsonObject body = mutation("PRODUCTION", id);
                body.add("lineIds", GSON.toJsonTree(ids));
                body.addProperty("status", status);
                body.addProperty("notes", notes);
                executeAsync(body, "Production updated.", completion);
            }
            public boolean canFinalizePrice(){return can("MANAGE_CUSTOM_ORDERS");}
            public void finalizePrice(Long id,Consumer<Boolean> completion) {
                UiTaskRunner.submit(CustomOrders.this,"custom-orders.pricing",()->GSON.fromJson(LanApiClient.customOrderWorkflowRead("PRICING",id,null).get("pricing"),services.CustomOrderPricingService.PricingDetails.class),details->{
                    JsonObject body=CustomOrderPricingDialog.prompt(CustomOrders.this,id,details);if(body==null){completion.accept(false);return;}executeAsync(body,"Final price saved.",completion);
                },failure->{error(new Exception(message(failure)));completion.accept(false);});
            }
            public void refundPendingDeposit(Long id,String method,String reference,Consumer<Boolean> completion) {
                String amount=JOptionPane.showInputDialog(CustomOrders.this,"Deposit amount to refund:");if(amount==null){completion.accept(false);return;}
                String reason=JOptionPane.showInputDialog(CustomOrders.this,"Reason for refunding the deposit:");if(reason==null){completion.accept(false);return;}
                try{JsonObject body=mutation("DEPOSIT_REFUND",id);body.addProperty("amount",parseOrderPaymentAmount(amount));body.addProperty("method",method);body.addProperty("reference",reference);body.addProperty("reason",reason);executeRefundWithApprovalAsync(body,completion);}catch(Exception ex){error(ex);completion.accept(false);}
            }
            public boolean canCancelOrders() { return can("CUSTOM_ORDER_OVERRIDES"); }
            public void loadCancellationLines(Long id,Consumer<List<CustomOrdersLookupTabPanel.LineReturnOption>> completion) {
                UiTaskRunner.submit(CustomOrders.this,"custom-orders.cancellation-lines",()->{
                    var json=LanApiClient.customOrderWorkflowRead("CANCELLATION_LINES",id,null);
                    List<LanCustomOrderWorkflowService.ReturnLine> rows=GSON.fromJson(json.get("lines"),new TypeToken<List<LanCustomOrderWorkflowService.ReturnLine>>(){}.getType());
                    return rows.stream().map(r->new CustomOrdersLookupTabPanel.LineReturnOption(r.lineId(),r.item(),r.variant(),r.lineTotal(),r.returned(),r.remaining())).toList();
                },completion,failure->{error(new Exception(message(failure)));completion.accept(null);});
            }
            public void cancelOrder(Long id,CustomOrdersLookupTabPanel.CancellationRequest request,Consumer<Boolean> completion) {
                JsonObject body=mutation("CANCEL_ORDER",id);body.addProperty("reason",request.reason());
                body.addProperty("method",request.method());body.addProperty("reference",request.reference());
                body.add("returns",GSON.toJsonTree(request.lines().stream().map(r->new LanCustomOrderWorkflowService.ReturnRequest(r.lineId(),r.refundAmount(),false,r.restockAction())).toList()));
                executeAsync(body,"Order cancelled. Returns, refunds and stock handling recorded.",completion);
            }
            public boolean canRefundPayments() { return can("CUSTOM_ORDER_LINE_RETURNS") || can("CUSTOM_ORDER_REFUNDS") || can("CUSTOM_ORDER_OVERRIDES"); }
            public boolean canDeliverOrderLines() { return can("CUSTOM_ORDER_LINE_DELIVERY") || can("CUSTOM_ORDER_OVERRIDES"); }
            public boolean canUpdateProduction() { return can("CUSTOM_ORDER_PRODUCTION_STEPS") || can("CUSTOM_ORDER_OVERRIDES"); }
            public void previewOrderSlip(String orderNumber) { try { WindowHelper.showPosWindow(new CustomOrderSlipPreview(orderNumber), CustomOrders.this); } catch (Exception ex) { error(ex); } }
            public void printOrderSlip(String orderNumber) { promptAndPrintSlip(orderNumber); }
            public void openMedia(Long orderId, Component parent) { CustomOrderMediaDialog.show(parent,orderId); }
            public void reprintOrderLabels(String orderNumber) { promptAndPrintLabels(orderNumber); }
            public void refreshRelatedOrders() { loadOrders(); }
        };
    }

    private void loadReturnableLinesAsync(Long id, Consumer<List<CustomOrdersLookupTabPanel.LineReturnOption>> completion) {
        UiTaskRunner.submit(this, "custom-orders.return-lines", () -> {
            var json = LanApiClient.customOrderWorkflowRead("RETURNS", id, null);
            List<LanCustomOrderWorkflowService.ReturnLine> rows = GSON.fromJson(json.get("lines"), new TypeToken<List<LanCustomOrderWorkflowService.ReturnLine>>() { }.getType());
            return rows.stream().map(row -> new CustomOrdersLookupTabPanel.LineReturnOption(row.lineId(), row.item(), row.variant(), row.lineTotal(), row.returned(), row.remaining())).toList();
        }, completion, failure -> failInline("Could not load refundable lines.", failure, () -> loadReturnableLinesAsync(id, completion), completion));
    }

    private void loadDeliverableLinesAsync(Long id, Consumer<List<CustomOrdersLookupTabPanel.LineDeliveryOption>> completion) {
        UiTaskRunner.submit(this, "custom-orders.delivery-lines", () -> {
            var json = LanApiClient.customOrderWorkflowRead("DELIVERIES", id, null);
            List<LanCustomOrderWorkflowService.DeliveryLine> rows = GSON.fromJson(json.get("lines"), new TypeToken<List<LanCustomOrderWorkflowService.DeliveryLine>>() { }.getType());
            return rows.stream().map(row -> new CustomOrdersLookupTabPanel.LineDeliveryOption(row.lineId(), row.item(), row.variant(), row.deliveryStatus(), row.returnStatus())).toList();
        }, completion, failure -> failInline("Could not load deliverable lines.", failure, () -> loadDeliverableLinesAsync(id, completion), completion));
    }

    private void loadProductionLinesAsync(Long id, Consumer<List<CustomOrdersLookupTabPanel.ProductionLineOption>> completion) {
        UiTaskRunner.submit(this, "custom-orders.production-lines", () -> {
            var json = LanApiClient.customOrderWorkflowRead("PRODUCTION", id, null);
            List<LanCustomOrderWorkflowService.ProductionLine> rows = GSON.fromJson(json.get("lines"), new TypeToken<List<LanCustomOrderWorkflowService.ProductionLine>>() { }.getType());
            return rows.stream().map(row -> new CustomOrdersLookupTabPanel.ProductionLineOption(row.lineId(), row.item(), row.variant(), productionLabel(row.productionStatus()), row.deliveryStatus())).toList();
        }, completion, failure -> failInline("Could not load production lines.", failure, () -> loadProductionLinesAsync(id, completion), completion));
    }

    private <T> void failInline(String message, Throwable failure, Runnable retry, Consumer<T> completion) {
        loadingState.failed(message + " " + failure.getMessage(), false, retry);
        completion.accept(null);
    }

    static BigDecimal parseOrderPaymentAmount(String text) {
        if(text==null||text.isBlank())throw new IllegalArgumentException("Enter a payment amount.");
        BigDecimal amount;
        try{amount=new BigDecimal(text.trim().replace(",",""));}
        catch(NumberFormatException e){throw new IllegalArgumentException("Enter a valid payment amount.");}
        if(amount.signum()<=0)throw new IllegalArgumentException("Payment amount must be greater than zero.");
        return amount;
    }
    private void executeAsync(JsonObject body, String message, Consumer<Boolean> completion) {
        String action = body.get("action").getAsString().toLowerCase();
        String requestKey=mutationKeys.computeIfAbsent(body,ignored->UUID.randomUUID().toString());
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this, "custom-orders.mutation." + action, () -> {
            LanApiClient.customOrderWorkflowMutation(body, requestKey);
            SessionDataCache.invalidate("custom-orders:orders");
            SessionDataCache.invalidate("custom-orders:LOOKUP:");
            SessionDataCache.invalidate("custom-orders:ALL:");
            return Boolean.TRUE;
        }, ignored -> {
            loadingState.ready(java.time.Instant.now());
            JOptionPane.showMessageDialog(this, message);
            completion.accept(true);
        }, failure -> {
            loadingState.failed(failure.getMessage(), false, () -> executeAsync(body, message, completion));
            JOptionPane.showMessageDialog(this, "The order change could not be saved: " + message(failure), "Custom Order", JOptionPane.ERROR_MESSAGE);
            completion.accept(false);
        });
    }

    private void executeRefundWithApprovalAsync(JsonObject body, Consumer<Boolean> completion) {
        String requestKey=mutationKeys.computeIfAbsent(body,ignored->UUID.randomUUID().toString());
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this, "custom-orders.mutation.line-return", () -> {
            LanApiClient.customOrderWorkflowMutation(body, requestKey);
            SessionDataCache.invalidate("custom-orders:orders");
            SessionDataCache.invalidate("custom-orders:LOOKUP:");
            SessionDataCache.invalidate("custom-orders:ALL:");
            return Boolean.TRUE;
        }, ignored -> finishRefund(completion), failure -> {
            if (!(failure instanceof LanApiClient.LanApiException apiFailure) || !"APPROVAL_REQUIRED".equals(apiFailure.code())) {
                loadingState.failed(failure.getMessage(), false, () -> executeRefundWithApprovalAsync(body, completion));
                completion.accept(false);
                return;
            }
            try {
                ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(this,
                        "CUSTOM_ORDER_REFUND_APPROVAL", "Custom Order Refund Approval",
                        "Reason for custom order refund approval:");
                if (approval == null) { completion.accept(false); return; }
                body.addProperty("approvalToken", approval.lanApprovalToken());
                body.addProperty("approvalReason", approval.reason());
                executeAsync(body, "Refund recorded.", completion);
            } catch (Exception ex) {
                error(ex);
                completion.accept(false);
            }
        });
    }

    private void finishRefund(Consumer<Boolean> completion) {
        loadingState.ready(java.time.Instant.now());
        JOptionPane.showMessageDialog(this, "Refund recorded.");
        completion.accept(true);
    }
    private static JsonObject mutation(String action,long order){JsonObject b=new JsonObject();b.addProperty("action",action);b.addProperty("orderId",order);return b;}

    static String designOrientation(String details) {
        if (details != null) {
            for (String part : details.split(" \\| ")) {
                if (part.equals("Orientation: Portrait")) return "Portrait";
                if (part.equals("Orientation: Landscape")) return "Landscape";
            }
        }
        return "Select orientation";
    }

    static BigDecimal billingWidth(BigDecimal designWidth, BigDecimal maximumWidth) {
        return maximumWidth != null && maximumWidth.signum() > 0 ? maximumWidth : designWidth;
    }

    static BigDecimal billableArea(BigDecimal designWidth, BigDecimal length, BigDecimal maximumWidth,
                                   String dimensionUnit, String areaUnit) {
        return areaInPricingUnit(billingWidth(designWidth, maximumWidth), length, dimensionUnit, areaUnit);
    }

    private static BigDecimal areaInPricingUnit(BigDecimal width,BigDecimal length,String dimensionUnit,String areaUnit){
        String d=dimensionUnit==null?"":dimensionUnit.trim().toUpperCase();String a=areaUnit==null?"":areaUnit.trim().toUpperCase();
        if(a.isBlank())return width.multiply(length);
        BigDecimal metres=switch(d){case"IN","INCH","INCHES"->new BigDecimal("0.0254");case"FT","FOOT","FEET"->new BigDecimal("0.3048");case"CM"->new BigDecimal("0.01");case"MM"->new BigDecimal("0.001");default->BigDecimal.ONE;};
        BigDecimal squareMetres=width.multiply(metres).multiply(length.multiply(metres));
        BigDecimal squareUnit=switch(a){case"SQ_IN","SQUARE_INCH","SQUARE_INCHES"->new BigDecimal("0.00064516");case"SQ_FT","SQUARE_FOOT","SQUARE_FEET"->new BigDecimal("0.09290304");case"SQ_CM","SQUARE_CENTIMETRE","SQUARE_CENTIMETERS"->new BigDecimal("0.0001");case"SQ_MM"->new BigDecimal("0.000001");default->BigDecimal.ONE;};
        return squareMetres.divide(squareUnit,6,RoundingMode.HALF_UP);
    }
    private static boolean can(String permission){return PermissionManager.hasPermission(permission);}private static DefaultTableModel model(String...c){return new DefaultTableModel(c,0){public boolean isCellEditable(int r,int c){return false;}};}private static JTextArea details(){JTextArea a=new JTextArea();a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);return a;}private static JPanel form(){JPanel p=new JPanel(new GridBagLayout());p.setBorder(new EmptyBorder(8,8,8,8));return p;}private static void row(JPanel p,int y,String label,Component c){GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(4,4,4,4);g.gridy=y;g.gridx=0;g.anchor=GridBagConstraints.WEST;p.add(new JLabel(label),g);g.gridx=1;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;p.add(c,g);}static String money(BigDecimal v){return CurrencyFormatter.normalize(v).toPlainString();}private static String decimalText(BigDecimal v){return v==null?"":v.stripTrailingZeros().toPlainString();}private static BigDecimal decimal(JTextField f,String label,boolean required){String v=f.getText().trim();if(v.isBlank()){if(required)throw new IllegalArgumentException("Enter "+label+".");return null;}try{return new BigDecimal(v);}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+".");}}static BigDecimal wholeDollar(JTextField f,String label,boolean required){BigDecimal v=decimal(f,label,required);if(v==null)return null;BigDecimal whole=CurrencyFormatter.normalize(v);if(v.compareTo(whole)!=0)throw new IllegalArgumentException("Enter "+label+" in whole dollars; cents are not used.");return whole;}private static BigDecimal nullable(JTextField f){return decimal(f,"value",false);}private static int integer(JTextField f,String label,int min){try{int v=Integer.parseInt(f.getText().trim());if(v<min)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+" of at least "+min+".");}}private static Long selected(JTable t,DefaultTableModel m){int r=t.getSelectedRow();return r<0?null:Long.valueOf(m.getValueAt(t.convertRowIndexToModel(r),0).toString());}private static String selectedNumber(JTable t,DefaultTableModel m){int r=t.getSelectedRow();return r<0?null:String.valueOf(m.getValueAt(t.convertRowIndexToModel(r),1));}private static String payment(LanCustomOrderWorkflowService.OrderRow x){return x.paymentMethod()+(x.paymentStatus().isBlank()?"":" / "+x.paymentStatus());}private static String productionLabel(String v){return switch(v){case"STARTED"->"Started";case"WAITING_FOR_APPROVAL"->"Waiting for approval";case"DESIGN_APPROVED"->"Design Approved";case"PRINTED"->"Printed";case"FINISHED"->"Finished";case"QUALITY_CHECKED"->"Quality Checked";case"READY"->"Ready";default->"Not Started";};}private static void filter(TableRowSorter<DefaultTableModel>s,JTextField q,JComboBox<String>status){List<RowFilter<Object,Object>>filters=new ArrayList<>();if(!q.getText().isBlank())filters.add(RowFilter.regexFilter("(?i)"+Pattern.quote(q.getText().trim())));if(!"All".equals(status.getSelectedItem()))filters.add(RowFilter.regexFilter("^"+Pattern.quote(String.valueOf(status.getSelectedItem()))+"$",2));s.setRowFilter(filters.isEmpty()?null:RowFilter.andFilter(filters));}private static javax.swing.event.DocumentListener listener(Runnable r){return new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){r.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){r.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){r.run();}};}private static String message(Throwable e){return e==null?"Unknown error":e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),getTitle(),JOptionPane.ERROR_MESSAGE);}
    private record CartLine(OrderLineRequest request,List<Path> pendingFiles,CustomItemOption item,VariantOption variant,String setId,CustomOrderShirtText.Saved personalization){
        CartLine(OrderLineRequest request,List<Path> files,CustomItemOption item,VariantOption variant,String setId){this(request,files,item,variant,setId,null);}
        CartLine(OrderLineRequest request,List<Path> files,CustomItemOption item,VariantOption variant){this(request,files,item,variant,null);}
        CartLine(OrderLineRequest request){this(request,new ArrayList<>(),null,null,null);}
    }
    private record FileUploadApproval(Path path,String token,String reason){}
    private record SaveOutcome(String orderNumber,List<String> fileErrors){}
    private record Catalog(List<CustomItemOption>items,List<PrintMaterialOption>materials,
                           List<String>placements,List<CustomerOption>initialCustomers,
                           BigDecimal minimumDepositPercent,long fileLimitBytes,boolean roundToNearestTwenty,
                           boolean alwaysPrintOrderSlip){}
    private record OrderSnapshot(List<LanCustomOrderWorkflowService.OrderRow>mine){}
    private record OrderPrintResult(CustomOrderSlipData data,Exception slipFailure,Exception labelFailure,int slipsPrinted){}
    private record LookupSnapshot(List<LanCustomOrderWorkflowService.OrderRow>rows){}
    private record VariantSnapshot(long itemId,List<VariantOption>variants){}
    private record PresetSnapshot(long materialId,List<PrintSizePresetOption>presets){}
    private record ItemLookupSnapshot(CustomOrderDataService.LookupResult result){}
}
