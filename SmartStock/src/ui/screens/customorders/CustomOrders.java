package ui.screens.customorders;

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
import ui.helpers.WindowHelper;
import ui.screens.CustomOrderSlipPreview;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Register-side custom-order workflow. All persistence is performed by the store service. */
public class CustomOrders extends JFrame {
    private static final Gson GSON=LanJson.create();
    private final List<CartLine>lines=new ArrayList<>();
    private final DefaultTableModel allModel=model("ID","Order #","Status","Customer","Phone","Due","Total","Paid","Balance","Payment","Reference","Assigned","Taken By","Created"),myModel=model("ID","Order #","Status","Customer","Phone","Due","Total","Paid","Balance","Payment","Reference","Created");private final JTable allTable=new JTable(allModel),myTable=new JTable(myModel);private final JTextArea allDetails=details(),myDetails=details();private final JTextField allSearch=new JTextField(),mySearch=new JTextField();private final JComboBox<String>allStatus=new JComboBox<>(new String[]{"All","NEW","ASSIGNED","IN_PROGRESS","READY","COMPLETED","DELIVERED","CANCELLED"}),myStatus=new JComboBox<>(new String[]{"All","ASSIGNED","IN_PROGRESS","READY","COMPLETED","DELIVERED","CANCELLED"});private final TableRowSorter<DefaultTableModel>allSorter=new TableRowSorter<>(allModel),mySorter=new TableRowSorter<>(myModel);
    private CustomOrdersLookupTabPanel lookup;
    private CustomOrdersNewOrderTabPanel guidedOrder;
    private String selectedPaymentMethod;
    private boolean loadingCatalog;
    private BigDecimal minimumDepositPercent=BigDecimal.ZERO;

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
            if(can("VIEW_ASSIGNED_CUSTOM_ORDERS")||can("MANAGE_CUSTOM_ORDERS")){
                tabs.addTab("My Orders",ordersTab(false));
            }
            if(can("MANAGE_CUSTOM_ORDERS")){
                tabs.addTab("All Orders",ordersTab(true));
            }
        }

        if(tabs.getTabCount()==0){
            add(new JLabel("You do not have permission to access this screen.",SwingConstants.CENTER));
        }else{
            add(tabs);
        }
        WindowHelper.showPosWindow(this);
        if(!orderManagementMode){
            loadCatalog();
        }else{
            loadOrders();
        }
    }

    private JPanel newOrderTab(){
        guidedOrder=new CustomOrdersNewOrderTabPanel(new CustomOrdersNewOrderTabPanel.Handler(){
            public void orderLookup(){lookupOrderItem();}
            public void orderItemChanged(){if(!loadingCatalog){loadVariants();applyPrice();}}
            public void variantChanged(){applyPrice();}
            public void printMaterialChanged(){if(!loadingCatalog)loadPresets();}
            public void printPresetChanged(){applyPrintPresetPrice();}
            public Runnable printLineCountChanged(){return CustomOrders.this::applyPrintPresetPrice;}
            public void addPrintAddon(){CustomOrders.this.addPrintAddon();}
            public void removePrintAddon(){CustomOrders.this.removePrintAddon();}
            public Runnable areaChanged(){return CustomOrders.this::updateAreaPreview;}
            public void addPlacement(){CustomOrders.this.addPlacement();}
            public void addOrderLine(){addLine();}
            public void removeOrderLine(){removeLine();}
            public void editLineDiscount(){editDiscount();}
            public void cartSelectionChanged(){}
            public void selectPaymentMethod(String method){selectedPaymentMethod=method;updatePaymentPreview();}
            public Runnable upfrontChanged(){return CustomOrders.this::updatePaymentPreview;}
            public boolean canLeaveStep(int step){return validateStep(step);}
            public void enterStep(int step){refreshStep(step);}
            public void saveOrder(){CustomOrders.this.saveOrder();}
            public void clearOrder(){CustomOrders.this.clearOrder();}
        });
        return guidedOrder;
    }
    private JPanel lookupTab(){lookup=new CustomOrdersLookupTabPanel(handler());return lookup;}
    private JPanel ordersTab(boolean all){DefaultTableModel m=all?allModel:myModel;JTable t=all?allTable:myTable;JTextArea d=all?allDetails:myDetails;JTextField s=all?allSearch:mySearch;JComboBox<String>status=all?allStatus:myStatus;TableRowSorter<DefaultTableModel>sorter=all?allSorter:mySorter;t.setRowSorter(sorter);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);t.setRowHeight(26);t.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){Long id=selected(t,m);if(id!=null)loadDetails(id,d);}});Runnable filter=()->filter(sorter,s,status);s.getDocument().addDocumentListener(listener(filter));status.addActionListener(e->filter.run());JButton refresh=new JButton("Refresh"),preview=new JButton("Preview Slip"),print=new JButton("Print Slip");refresh.addActionListener(e->loadOrders());preview.addActionListener(e->{Long id=selected(t,m);String number=selectedNumber(t,m);if(id!=null&&number!=null)try{new CustomOrderSlipPreview(number);}catch(Exception ex){error(ex);}});print.addActionListener(e->{String number=selectedNumber(t,m);if(number!=null)try{CustomOrderSlipPrinter.print(number);}catch(Exception ex){error(ex);}});JPanel top=new JPanel(new BorderLayout(8,0));top.add(s);JPanel controls=new JPanel();controls.add(status);controls.add(refresh);controls.add(preview);controls.add(print);top.add(controls,BorderLayout.EAST);JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(t),new JScrollPane(d));split.setResizeWeight(.7);JPanel p=new JPanel(new BorderLayout(8,8));p.setBorder(new EmptyBorder(12,12,12,12));p.add(top,BorderLayout.NORTH);p.add(split);return p;}

    private void loadCatalog(){
        if(guidedOrder==null)return;
        new SwingWorker<Catalog,Void>(){
            protected Catalog doInBackground()throws Exception{BigDecimal deposit=CompanyCustomizationManager.loadCustomOrderSettings().minimumDepositPercent();return new Catalog(CustomOrderDataService.listActiveItems(),CustomOrderDataService.listActivePrintMaterials(),CustomOrderDataService.listActiveDesignPlacements(),deposit==null?BigDecimal.ZERO:deposit);}
            protected void done(){loadingCatalog=true;try{Catalog c=get();minimumDepositPercent=c.minimumDepositPercent();guidedOrder.orderItemBox.removeAllItems();c.items().forEach(guidedOrder.orderItemBox::addItem);guidedOrder.printMaterialBox.removeAllItems();guidedOrder.printMaterialBox.addItem(new PrintMaterialOption(null,"No Print"));c.materials().forEach(guidedOrder.printMaterialBox::addItem);guidedOrder.designPlacementBox.removeAllItems();c.placements().forEach(guidedOrder.designPlacementBox::addItem);if(guidedOrder.designPlacementBox.getItemCount()==0)for(String x:List.of("Front","Back","Left Chest","Right Chest","Left Sleeve","Right Sleeve"))guidedOrder.designPlacementBox.addItem(x);}catch(Exception e){error(e);}finally{loadingCatalog=false;}loadVariants();loadPresets();applyPrice();updateTotal();}
        }.execute();
    }
    private void loadVariants(){if(guidedOrder==null)return;guidedOrder.variantBox.removeAllItems();CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();if(item==null||item.customItemId()==null)return;try{for(var x:CustomOrderDataService.listActiveVariants(item.customItemId()))guidedOrder.variantBox.addItem(x);guidedOrder.variantBox.setEnabled(item.hasVariants());applyPrice();}catch(Exception e){error(e);}}
    private void loadPresets(){if(guidedOrder==null)return;guidedOrder.printSizePresetBox.removeAllItems();guidedOrder.printSizePresetBox.addItem(new PrintSizePresetOption(null,null,"Custom Print Price","FIXED_PRESET",null));PrintMaterialOption m=(PrintMaterialOption)guidedOrder.printMaterialBox.getSelectedItem();boolean active=m!=null&&m.printMaterialId()!=null;guidedOrder.printSizePresetBox.setEnabled(active);guidedOrder.printChargeField.setEnabled(active);if(!active){guidedOrder.printChargeField.setText("0");return;}try{for(var x:CustomOrderDataService.listActivePrintSizePresets(m.printMaterialId()))guidedOrder.printSizePresetBox.addItem(x);applyPrintPresetPrice();}catch(Exception e){error(e);}}
    private void applyPrice(){if(guidedOrder==null)return;CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();VariantOption variant=(VariantOption)guidedOrder.variantBox.getSelectedItem();BigDecimal p=variant!=null&&variant.fixedPrice()!=null?variant.fixedPrice():item==null?null:("AREA".equals(item.pricingType())?item.areaPrice():item.fixedPrice());if(p!=null)guidedOrder.linePriceField.setText(p.stripTrailingZeros().toPlainString());guidedOrder.priceRateUnitLabel.setText(item!=null&&"AREA".equals(item.pricingType())?"per "+item.areaPriceUnit():"per item");boolean area=item!=null&&"AREA".equals(item.pricingType());guidedOrder.areaLineComponents.forEach(c->c.setVisible(area));updateAreaPreview();}
    private void lookupOrderItem(){try{var r=CustomOrderDataService.lookupCustomItem(guidedOrder.itemLookupField.getText().trim());if(r==null||r.customItemId()==null){JOptionPane.showMessageDialog(this,"No custom item or variant matched that search.");return;}selectItem(guidedOrder.orderItemBox,r.customItemId());loadVariants();if(r.customVariantId()!=null)selectVariant(guidedOrder.variantBox,r.customVariantId());applyPrice();}catch(Exception e){error(e);}}
    private void applyPrintPresetPrice(){if(guidedOrder==null)return;PrintSizePresetOption p=(PrintSizePresetOption)guidedOrder.printSizePresetBox.getSelectedItem();if(p!=null&&p.fixedPrice()!=null){BigDecimal v=p.fixedPrice();if("PER_LINE".equals(p.pricingMode()))v=v.multiply(BigDecimal.valueOf(integer(guidedOrder.printLineCountField,"print line count",1)));guidedOrder.printChargeField.setText(v.stripTrailingZeros().toPlainString());}}
    private void addPrintAddon(){try{PrintMaterialOption m=(PrintMaterialOption)guidedOrder.printMaterialBox.getSelectedItem();if(m==null||m.printMaterialId()==null)throw new IllegalArgumentException("Select a print material.");PrintSizePresetOption p=(PrintSizePresetOption)guidedOrder.printSizePresetBox.getSelectedItem();BigDecimal charge=decimal(guidedOrder.printChargeField,"print charge",true);int count="PER_LINE".equals(p==null?null:p.pricingMode())?integer(guidedOrder.printLineCountField,"print line count",1):1;if((p==null||p.printSizePresetId()==null)&&guidedOrder.printDescriptionField.getText().isBlank())throw new IllegalArgumentException("Enter a print description for a custom print price.");guidedOrder.printAddonModel.addRow(new Object[]{m.printMaterialId(),m.materialName(),p==null?null:p.printSizePresetId(),p==null?"Custom":p.presetName(),p==null?"FIXED_PRESET":p.pricingMode(),guidedOrder.printDescriptionField.getText().trim(),count,money(charge)});guidedOrder.printMaterialBox.setSelectedIndex(0);guidedOrder.printDescriptionField.setText("");guidedOrder.printChargeField.setText("0");}catch(Exception e){error(e);}}
    private void removePrintAddon(){int r=guidedOrder.printAddonTable.getSelectedRow();if(r>=0)guidedOrder.printAddonModel.removeRow(guidedOrder.printAddonTable.convertRowIndexToModel(r));}
    private void addPlacement(){String text=guidedOrder.designPlacementField.getText().trim();if(text.isBlank()){JOptionPane.showMessageDialog(this,"Enter the design or text for this placement.");return;}String placement=String.valueOf(guidedOrder.designPlacementBox.getSelectedItem());if(!guidedOrder.lineNotesArea.getText().isBlank())guidedOrder.lineNotesArea.append("\n");guidedOrder.lineNotesArea.append(placement+": "+text);guidedOrder.designPlacementField.setText("");}
    private void editDiscount(){String v=JOptionPane.showInputDialog(this,"Discount percentage:",guidedOrder.lineDiscountPercentField.getText());if(v==null)return;guidedOrder.lineDiscountPercentField.setText(v.trim());String reason=JOptionPane.showInputDialog(this,"Discount reason:",guidedOrder.lineDiscountReasonField.getText());if(reason!=null)guidedOrder.lineDiscountReasonField.setText(reason.trim());}
    private void addLine(){try{CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();if(item==null)throw new IllegalArgumentException("Select an item.");VariantOption variant=(VariantOption)guidedOrder.variantBox.getSelectedItem();if(item.hasVariants()&&variant==null)throw new IllegalArgumentException("Select a size or variant.");int qty=integer(guidedOrder.lineQuantityField,"quantity",1);BigDecimal entered=decimal(guidedOrder.linePriceField,"line price",true),base=entered,area=null,w=nullable(guidedOrder.widthField),l=nullable(guidedOrder.lengthField);if("AREA".equals(item.pricingType())){if(w==null||l==null||w.signum()<=0||l.signum()<=0)throw new IllegalArgumentException("Enter width and length for area pricing.");area=areaInPricingUnit(w,l,item.dimensionUnit(),item.areaPriceUnit());base=area.multiply(entered).setScale(2,RoundingMode.HALF_UP);}BigDecimal configured=variant!=null&&variant.fixedPrice()!=null?variant.fixedPrice():("AREA".equals(item.pricingType())?item.areaPrice():item.fixedPrice());String overrideReason=null,overrideToken=null;if(configured!=null&&entered.compareTo(configured)!=0){overrideReason=guidedOrder.priceOverrideReasonField.getText().trim();if(overrideReason.isBlank())throw new IllegalArgumentException("Enter a price override reason.");if(!can("CUSTOM_ORDER_PRICE_OVERRIDE")&&!can("CUSTOM_ORDER_OVERRIDES")){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_PRICE_OVERRIDE","Custom Order Price Override","Reason for custom order price override:");if(approval==null)return;overrideToken=approval.lanApprovalToken();if(overrideReason.isBlank())overrideReason=approval.reason();}}
            List<PrintAddonRequest>addons=printAddons();BigDecimal addOn=addons.stream().map(PrintAddonRequest::printCharge).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal original=base.add(addOn);BigDecimal pct=decimal(guidedOrder.lineDiscountPercentField,"discount",false);if(pct==null)pct=BigDecimal.ZERO;if(pct.signum()<0||pct.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("Discount must be between 0 and 100%.");String reason=guidedOrder.lineDiscountReasonField.getText().trim(),approvalToken=null;if(pct.signum()>0&&!can("CUSTOM_ORDER_LINE_DISCOUNT")&&!can("CUSTOM_ORDER_OVERRIDES")){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_LINE_DISCOUNT","Custom Order Line Discount Override","Reason for custom order line discount override:");if(approval==null)return;approvalToken=approval.lanApprovalToken();if(reason.isBlank())reason=approval.reason();}if(pct.signum()>0&&reason.isBlank())throw new IllegalArgumentException("Enter a discount reason.");BigDecimal reduction=original.multiply(pct).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP),lineTotal=original.subtract(reduction).max(BigDecimal.ZERO);String details=area==null?"":"Area: "+w+" x "+l+" "+item.dimensionUnit()+" = "+area+" "+item.areaPriceUnit();for(int i=0;i<qty;i++){OrderLineRequest request=new OrderLineRequest(item.customItemId(),variant==null?null:variant.variantId(),item.name(),variant==null?null:variant.name(),item.pricingType(),lineTotal,details,guidedOrder.lineNotesArea.getText(),w,l,item.dimensionUnit(),area,item.areaPriceUnit(),entered,base,null,null,null,null,addOn,addons.stream().mapToInt(PrintAddonRequest::printLineCount).sum(),original,pct,reduction,null,null,reason,BigDecimal.ZERO,configured,overrideReason==null?null:entered,overrideReason,null,null,addons,approvalToken,overrideToken);lines.add(new CartLine(request));guidedOrder.orderLineModel.addRow(new Object[]{item.customItemId(),variant==null?null:variant.variantId(),item.name(),variant==null?"":variant.name(),item.pricingType(),money(lineTotal),details,guidedOrder.lineNotesArea.getText(),w,l,item.dimensionUnit(),area,item.areaPriceUnit(),entered,null,"",null,"",money(addOn),money(base),addons.stream().mapToInt(PrintAddonRequest::printLineCount).sum(),printSummary(addons),"",money(original),money(pct),money(reduction),reason,"0",configured,overrideReason==null?null:entered,overrideReason});}updateTotal();clearLine();}catch(Exception e){error(e);}}
    private List<PrintAddonRequest>printAddons(){List<PrintAddonRequest>out=new ArrayList<>();for(int r=0;r<guidedOrder.printAddonModel.getRowCount();r++)out.add(new PrintAddonRequest(longValue(guidedOrder.printAddonModel.getValueAt(r,0)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,1)),longValue(guidedOrder.printAddonModel.getValueAt(r,2)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,3)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,4)),String.valueOf(guidedOrder.printAddonModel.getValueAt(r,5)),Integer.parseInt(String.valueOf(guidedOrder.printAddonModel.getValueAt(r,6))),new BigDecimal(String.valueOf(guidedOrder.printAddonModel.getValueAt(r,7)))));return out;}
    private void removeLine(){int r=guidedOrder.orderLineTable.getSelectedRow();if(r<0)return;r=guidedOrder.orderLineTable.convertRowIndexToModel(r);lines.remove(r);guidedOrder.orderLineModel.removeRow(r);updateTotal();}
    private void clearLine(){guidedOrder.lineQuantityField.setText("1");guidedOrder.lineNotesArea.setText("");guidedOrder.widthField.setText("");guidedOrder.lengthField.setText("");guidedOrder.lineDiscountPercentField.setText("0");guidedOrder.lineDiscountReasonField.setText("");guidedOrder.priceOverrideReasonField.setText("");guidedOrder.printAddonModel.setRowCount(0);}
    private void updateTotal(){if(guidedOrder==null)return;BigDecimal t=total(),d=minimumDeposit();guidedOrder.lineCountLabel.setText("<html><b>Lines</b><br>"+lines.size()+"</html>");guidedOrder.orderTotalLabel.setText("<html><b>Order Total</b><br>$"+money(t)+"</html>");guidedOrder.minimumDepositLabel.setText("<html><b>Minimum Deposit</b><br>$"+money(d)+"</html>");updatePaymentPreview();}
    private BigDecimal total(){return lines.stream().map(x->x.request().unitPrice()).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);}
    private void saveOrder(){
        try{
            if(lines.isEmpty())throw new IllegalArgumentException("Add at least one order line.");
            String customer=guidedOrder.customerInfoPanel.getCustomerName().trim();if(customer.isBlank())throw new IllegalArgumentException("Enter a customer name.");
            String phone=guidedOrder.customerInfoPanel.getCustomerPhone().trim();if(phone.isBlank())throw new IllegalArgumentException("Enter a customer phone number.");
            LocalDate due=guidedOrder.dueDateEnabledBox.isSelected()?guidedOrder.dueDateField.getSelectedDate():null;
            BigDecimal total=total(),paid=decimal(guidedOrder.upfrontPaymentField,"upfront payment",true);
            if(paid.signum()<0||paid.compareTo(total)>0)throw new IllegalArgumentException("Upfront payment must be between zero and the total.");
            String method=paid.signum()==0?selectedPaymentMethod:selectedPaymentMethod;if(paid.signum()>0&&(method==null||method.isBlank()))throw new IllegalArgumentException("Select a payment method.");
            if("ACCOUNT".equals(method)&&paid.signum()>0)throw new IllegalArgumentException("Account charges use the unpaid balance. Leave upfront payment at 0.");
            if(paid.signum()>0&&List.of("CARD","CHEQUE","MMG").contains(method)&&guidedOrder.paymentReferenceField.getText().isBlank())throw new IllegalArgumentException("Enter a payment reference.");
            BigDecimal minimumPercent=CompanyCustomizationManager.loadCustomOrderSettings().minimumDepositPercent();
            if(minimumPercent==null)minimumPercent=BigDecimal.ZERO;
            BigDecimal required=total.multiply(minimumPercent).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
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
            List<OrderLineRequest>requests=lines.stream().map(CartLine::request).toList();
            OrderSaveRequest request=new OrderSaveRequest(selected,customer,phone,due,total,paid,total.subtract(paid),method,guidedOrder.paymentReferenceField.getText(),paid.signum()==0?"UNPAID":paid.compareTo(total)==0?"PAID":"PARTIAL",SessionManager.getCurrentUserId(),SessionManager.getCurrentUserDisplayName(),SessionManager.getCurrentLocationId(),SessionManager.getCurrentLocationName(),SessionManager.getCurrentDeviceId(),"",required,depositReason,null,null,guidedOrder.orderNotesArea.getText(),requests,depositToken);
            String number=CustomOrderDataService.saveCustomOrder(request);
            JOptionPane.showMessageDialog(this,"Custom order "+number+" saved.");
            try{EmailOutboxService.queueCustomOrderConfirmation(number,true);}catch(Exception ignored){}
            if(JOptionPane.showConfirmDialog(this,"Print the order slip now?","Order Saved",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)CustomOrderSlipPrinter.print(number);
            clearOrder();loadOrders();
        }catch(Exception e){error(e);}
    }
    private void clearOrder(){lines.clear();guidedOrder.orderLineModel.setRowCount(0);guidedOrder.customerInfoPanel.clear();guidedOrder.dueDateEnabledBox.setSelected(false);guidedOrder.dueDateField.setText("");guidedOrder.dueDateField.setEnabled(false);guidedOrder.orderNotesArea.setText("");guidedOrder.upfrontPaymentField.setText("0");guidedOrder.paymentReferenceField.setText("");guidedOrder.depositOverrideReasonField.setText("");selectedPaymentMethod=null;guidedOrder.paymentMethodGroup.clearSelection();guidedOrder.showLinesStep();clearLine();updateTotal();}

    private boolean validateStep(int step){
        if(step==0&&lines.isEmpty()){JOptionPane.showMessageDialog(this,"Add at least one custom order line before review.");return false;}
        if(step==1&&guidedOrder.dueDateEnabledBox.isSelected())try{guidedOrder.dueDateField.getSelectedDate();}catch(Exception e){JOptionPane.showMessageDialog(this,"Due date must use YYYY-MM-DD.");return false;}
        if(step==2){if(guidedOrder.customerInfoPanel.getCustomerName().isBlank()){JOptionPane.showMessageDialog(this,"Customer name is required before payment.");return false;}if(guidedOrder.customerInfoPanel.getCustomerPhone().isBlank()){JOptionPane.showMessageDialog(this,"Customer phone number is required before payment.");return false;}}
        return true;
    }
    private void refreshStep(int step){if(step==1){guidedOrder.reviewLineCountLabel.setText("Lines: "+lines.size());guidedOrder.reviewOrderTotalLabel.setText("Order Total: $"+money(total()));guidedOrder.reviewMinimumDepositLabel.setText("Minimum Deposit Required: $"+money(minimumDeposit()));guidedOrder.reviewLineModel.clear();for(CartLine line:lines){OrderLineRequest r=line.request();guidedOrder.reviewLineModel.addElement(r.itemName()+(r.variantName()==null||r.variantName().isBlank()?"":" / "+r.variantName())+" - $"+money(r.unitPrice())+(r.orderInstructions()==null||r.orderInstructions().isBlank()?"":" | "+r.orderInstructions().replace("\n"," / ")));}}if(step==3){guidedOrder.customerSummaryLabel.setText("Customer: "+guidedOrder.customerInfoPanel.getCustomerName()+" / "+guidedOrder.customerInfoPanel.getCustomerPhone());updatePaymentPreview();}}
    private BigDecimal minimumDeposit(){return total().multiply(minimumDepositPercent).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);}
    private void updatePaymentPreview(){if(guidedOrder==null)return;BigDecimal paid;try{paid=new BigDecimal(guidedOrder.upfrontPaymentField.getText().trim().isBlank()?"0":guidedOrder.upfrontPaymentField.getText().trim());}catch(Exception e){paid=BigDecimal.ZERO;}BigDecimal required=minimumDeposit();guidedOrder.paymentMinimumDepositLabel.setText("Minimum Deposit Required: $"+money(required));guidedOrder.balanceDueLabel.setText("Balance Due: $"+money(total().subtract(paid).max(BigDecimal.ZERO)));guidedOrder.depositOverrideNoticeLabel.setText(paid.compareTo(required)<0?"A deposit override and reason are required.":" ");boolean ref=List.of("CARD","CHEQUE","MMG").contains(selectedPaymentMethod);guidedOrder.paymentReferenceField.setEnabled(ref);if(!ref)guidedOrder.paymentReferenceField.setText("");}
    private void updateAreaPreview(){if(guidedOrder==null)return;CustomItemOption item=(CustomItemOption)guidedOrder.orderItemBox.getSelectedItem();if(item==null||!"AREA".equals(item.pricingType())){guidedOrder.areaCalculationLabel.setText(" ");return;}try{BigDecimal w=nullable(guidedOrder.widthField),l=nullable(guidedOrder.lengthField),rate=decimal(guidedOrder.linePriceField,"line price",false);if(w==null||l==null||rate==null){guidedOrder.areaCalculationLabel.setText("Enter width and length to calculate area.");return;}BigDecimal area=areaInPricingUnit(w,l,item.dimensionUnit(),item.areaPriceUnit());guidedOrder.areaCalculationLabel.setText(area.stripTrailingZeros().toPlainString()+" "+item.areaPriceUnit()+" = $"+money(area.multiply(rate)));}catch(Exception e){guidedOrder.areaCalculationLabel.setText("Enter valid dimensions.");}}
    private String printSummary(List<PrintAddonRequest>a){StringBuilder s=new StringBuilder();for(PrintAddonRequest x:a){if(s.length()>0)s.append("; ");s.append(x.materialName()).append(" / ").append(x.printSizeName()).append(" $").append(money(x.printCharge()));}return s.toString();}
    private static Long longValue(Object v){if(v==null||String.valueOf(v).isBlank()||"null".equals(String.valueOf(v)))return null;return Long.valueOf(String.valueOf(v));}
    private static void selectItem(JComboBox<CustomItemOption>b,Long id){for(int i=0;i<b.getItemCount();i++)if(java.util.Objects.equals(id,b.getItemAt(i).customItemId())){b.setSelectedIndex(i);return;}}
    private static void selectVariant(JComboBox<VariantOption>b,Long id){for(int i=0;i<b.getItemCount();i++)if(java.util.Objects.equals(id,b.getItemAt(i).variantId())){b.setSelectedIndex(i);return;}}

    private void loadOrders(){try{renderOrders(allModel,workflow("ALL",""),true);renderOrders(myModel,workflow("MINE",""),false);if(lookup!=null)lookup.load(handler());}catch(Exception e){error(e);}}
    private List<LanCustomOrderWorkflowService.OrderRow>workflow(String action,String search)throws Exception{return GSON.fromJson(LanApiClient.customOrderWorkflowRead(action,null,search).get("orders"),new TypeToken<List<LanCustomOrderWorkflowService.OrderRow>>(){}.getType());}
    private void renderOrders(DefaultTableModel m,List<LanCustomOrderWorkflowService.OrderRow>rows,boolean all){m.setRowCount(0);for(var x:rows){List<Object>v=new ArrayList<>(List.of(x.orderId(),x.orderNumber(),x.status(),x.customer(),x.phone(),x.dueDate()==null?"":x.dueDate(),money(x.total()),money(x.paid()),money(x.balance()),payment(x),x.paymentReference()));if(all){v.add(x.assignedTo());v.add(x.takenBy());}v.add(new Date(x.createdAtEpochMillis()));m.addRow(v.toArray());}}
    private void loadDetails(Long id,JTextArea area){try{area.setText(LanApiClient.customOrderWorkflowRead("DETAILS",id,null).get("details").getAsString());area.setCaretPosition(0);}catch(Exception e){error(e);}}
    private CustomOrdersLookupTabPanel.Handler handler(){return new CustomOrdersLookupTabPanel.Handler(){public void loadLookupOrders(DefaultTableModel m,String s){try{renderOrders(m,workflow("LOOKUP",s),false);}catch(Exception e){error(e);}}public Long selectedLookupOrderId(JTable t,DefaultTableModel m){return selected(t,m);}public void loadOrderDetails(Long id,JTextArea a){loadDetails(id,a);}public List<CustomOrdersLookupTabPanel.LineReturnOption>loadReturnableLines(Long id){try{var j=LanApiClient.customOrderWorkflowRead("RETURNS",id,null);List<LanCustomOrderWorkflowService.ReturnLine>r=GSON.fromJson(j.get("lines"),new TypeToken<List<LanCustomOrderWorkflowService.ReturnLine>>(){}.getType());return r.stream().map(x->new CustomOrdersLookupTabPanel.LineReturnOption(x.lineId(),x.item(),x.variant(),x.lineTotal(),x.returned(),x.remaining())).toList();}catch(Exception e){error(e);return List.of();}}public List<CustomOrdersLookupTabPanel.LineDeliveryOption>loadDeliverableLines(Long id){try{var j=LanApiClient.customOrderWorkflowRead("DELIVERIES",id,null);List<LanCustomOrderWorkflowService.DeliveryLine>r=GSON.fromJson(j.get("lines"),new TypeToken<List<LanCustomOrderWorkflowService.DeliveryLine>>(){}.getType());return r.stream().map(x->new CustomOrdersLookupTabPanel.LineDeliveryOption(x.lineId(),x.item(),x.variant(),x.deliveryStatus(),x.returnStatus())).toList();}catch(Exception e){error(e);return List.of();}}public List<CustomOrdersLookupTabPanel.ProductionLineOption>loadProductionLines(Long id){try{var j=LanApiClient.customOrderWorkflowRead("PRODUCTION",id,null);List<LanCustomOrderWorkflowService.ProductionLine>r=GSON.fromJson(j.get("lines"),new TypeToken<List<LanCustomOrderWorkflowService.ProductionLine>>(){}.getType());return r.stream().map(x->new CustomOrdersLookupTabPanel.ProductionLineOption(x.lineId(),x.item(),x.variant(),productionLabel(x.productionStatus()),x.deliveryStatus())).toList();}catch(Exception e){error(e);return List.of();}}public BigDecimal parseNullableMoneyValue(Object v){try{return new BigDecimal(String.valueOf(v).replace(",",""));}catch(Exception e){return null;}}public boolean applyLookupPayment(Long id,String a,String m,String ref,Component p){JsonObject b=mutation("PAYMENT",id);try{b.addProperty("amount",new BigDecimal(a));b.addProperty("method",m);b.addProperty("reference",ref);return execute(b,"Payment applied.");}catch(Exception e){error(e);return false;}}public boolean applyLookupRefund(Long id,String a,String m,String ref,String reason,Component p){return false;}public boolean applyLookupLineRefund(Long id,List<CustomOrdersLookupTabPanel.LineReturnRequest>r,String m,String ref,String reason,Component p){try{List<LanCustomOrderWorkflowService.ReturnRequest>returns=r.stream().map(x->new LanCustomOrderWorkflowService.ReturnRequest(x.lineId(),x.refundAmount(),x.partial(),x.restockAction())).toList();JsonObject b=mutation("LINE_RETURN",id);b.add("returns",GSON.toJsonTree(returns));b.addProperty("method",m);b.addProperty("reference",ref);b.addProperty("reason",reason);return executeRefundWithApproval(b);}catch(Exception e){error(e);return false;}}public boolean markLookupOrderDelivered(Long id,Component p){return execute(mutation("DELIVER_ORDER",id),"Order marked delivered.");}public boolean markLookupLinesDelivered(Long id,List<Long>ids,String notes,Component p){JsonObject b=mutation("DELIVER_LINES",id);b.add("lineIds",GSON.toJsonTree(ids));b.addProperty("notes",notes);return execute(b,"Selected lines delivered.");}public boolean updateProductionLines(Long id,List<Long>ids,String status,String notes,Component p){JsonObject b=mutation("PRODUCTION",id);b.add("lineIds",GSON.toJsonTree(ids));b.addProperty("status",status);b.addProperty("notes",notes);return execute(b,"Production updated.");}public boolean canRefundPayments(){return can("CUSTOM_ORDER_LINE_RETURNS")||can("CUSTOM_ORDER_REFUNDS")||can("CUSTOM_ORDER_OVERRIDES");}public boolean canDeliverOrderLines(){return can("CUSTOM_ORDER_LINE_DELIVERY")||can("CUSTOM_ORDER_OVERRIDES");}public boolean canUpdateProduction(){return can("CUSTOM_ORDER_PRODUCTION_STEPS")||can("CUSTOM_ORDER_OVERRIDES");}public void refreshRelatedOrders(){loadOrders();}};}
    private boolean execute(JsonObject b,String message){try{LanApiClient.customOrderWorkflowMutation(b,UUID.randomUUID().toString());JOptionPane.showMessageDialog(this,message);loadOrders();return true;}catch(Exception e){error(e);return false;}}
    private boolean executeRefundWithApproval(JsonObject body){
        try{
            LanApiClient.customOrderWorkflowMutation(body,UUID.randomUUID().toString());
        }catch(LanApiClient.LanApiException e){
            if(!"APPROVAL_REQUIRED".equals(e.code())){error(e);return false;}
            ManagerApprovalService.ApprovalResult approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_REFUND_APPROVAL","Custom Order Refund Approval","Reason for custom order refund approval:");
            if(approval==null)return false;body.addProperty("approvalToken",approval.lanApprovalToken());body.addProperty("approvalReason",approval.reason());
            try{LanApiClient.customOrderWorkflowMutation(body,UUID.randomUUID().toString());}catch(Exception retry){error(retry);return false;}
        }catch(Exception e){error(e);return false;}
        JOptionPane.showMessageDialog(this,"Line return recorded.");loadOrders();return true;
    }
    private static JsonObject mutation(String action,long order){JsonObject b=new JsonObject();b.addProperty("action",action);b.addProperty("orderId",order);return b;}

    private static BigDecimal areaInPricingUnit(BigDecimal width,BigDecimal length,String dimensionUnit,String areaUnit){
        String d=dimensionUnit==null?"":dimensionUnit.trim().toUpperCase();String a=areaUnit==null?"":areaUnit.trim().toUpperCase();
        if(a.isBlank())return width.multiply(length);
        BigDecimal metres=switch(d){case"IN","INCH","INCHES"->new BigDecimal("0.0254");case"FT","FOOT","FEET"->new BigDecimal("0.3048");case"CM"->new BigDecimal("0.01");case"MM"->new BigDecimal("0.001");default->BigDecimal.ONE;};
        BigDecimal squareMetres=width.multiply(metres).multiply(length.multiply(metres));
        BigDecimal squareUnit=switch(a){case"SQ_IN","SQUARE_INCH","SQUARE_INCHES"->new BigDecimal("0.00064516");case"SQ_FT","SQUARE_FOOT","SQUARE_FEET"->new BigDecimal("0.09290304");case"SQ_CM","SQUARE_CENTIMETRE","SQUARE_CENTIMETERS"->new BigDecimal("0.0001");case"SQ_MM"->new BigDecimal("0.000001");default->BigDecimal.ONE;};
        return squareMetres.divide(squareUnit,6,RoundingMode.HALF_UP);
    }
    private static boolean can(String permission){return PermissionManager.hasPermission(permission);}private static DefaultTableModel model(String...c){return new DefaultTableModel(c,0){public boolean isCellEditable(int r,int c){return false;}};}private static JTextArea details(){JTextArea a=new JTextArea();a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);return a;}private static JPanel form(){JPanel p=new JPanel(new GridBagLayout());p.setBorder(new EmptyBorder(8,8,8,8));return p;}private static void row(JPanel p,int y,String label,Component c){GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(4,4,4,4);g.gridy=y;g.gridx=0;g.anchor=GridBagConstraints.WEST;p.add(new JLabel(label),g);g.gridx=1;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;p.add(c,g);}private static String money(BigDecimal v){return v==null?"0.00":v.setScale(2,RoundingMode.HALF_UP).toPlainString();}private static BigDecimal decimal(JTextField f,String label,boolean required){String v=f.getText().trim();if(v.isBlank()){if(required)throw new IllegalArgumentException("Enter "+label+".");return null;}try{return new BigDecimal(v);}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+".");}}private static BigDecimal nullable(JTextField f){return decimal(f,"value",false);}private static int integer(JTextField f,String label,int min){try{int v=Integer.parseInt(f.getText().trim());if(v<min)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException("Enter a valid "+label+" of at least "+min+".");}}private static Long selected(JTable t,DefaultTableModel m){int r=t.getSelectedRow();return r<0?null:Long.valueOf(m.getValueAt(t.convertRowIndexToModel(r),0).toString());}private static String selectedNumber(JTable t,DefaultTableModel m){int r=t.getSelectedRow();return r<0?null:String.valueOf(m.getValueAt(t.convertRowIndexToModel(r),1));}private static String payment(LanCustomOrderWorkflowService.OrderRow x){return x.paymentMethod()+(x.paymentStatus().isBlank()?"":" / "+x.paymentStatus());}private static String productionLabel(String v){return switch(v){case"DESIGN_APPROVED"->"Design Approved";case"PRINTED"->"Printed";case"FINISHED"->"Finished";case"QUALITY_CHECKED"->"Quality Checked";case"READY"->"Ready";default->"Not Started";};}private static void filter(TableRowSorter<DefaultTableModel>s,JTextField q,JComboBox<String>status){List<RowFilter<Object,Object>>filters=new ArrayList<>();if(!q.getText().isBlank())filters.add(RowFilter.regexFilter("(?i)"+Pattern.quote(q.getText().trim())));if(!"All".equals(status.getSelectedItem()))filters.add(RowFilter.regexFilter("^"+Pattern.quote(String.valueOf(status.getSelectedItem()))+"$",2));s.setRowFilter(filters.isEmpty()?null:RowFilter.andFilter(filters));}private static javax.swing.event.DocumentListener listener(Runnable r){return new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){r.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){r.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){r.run();}};}private void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),getTitle(),JOptionPane.ERROR_MESSAGE);}
    private record CartLine(OrderLineRequest request){}
    private record Catalog(List<CustomItemOption>items,List<PrintMaterialOption>materials,List<String>placements,BigDecimal minimumDepositPercent){}
}
