package ui.screens.customorders;

import com.google.gson.JsonObject;
import managers.PermissionManager;
import services.*;
import services.CustomOrderDataService.PrintAddonRequest;
import services.CustomOrderPricingService.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Edits prices of saved units without replacing production lines or their attachments. */
final class CustomOrderPricingDialog {
    private CustomOrderPricingDialog() {}
    static JsonObject prompt(Component parent,long id,PricingDetails details) {
        var gson=LanJson.create();
        DefaultTableModel model=new DefaultTableModel(new String[]{"Item / unit","Width","Length","Price / rate","Discount %","Discount reason","Price override reason"},0) {
            public boolean isCellEditable(int row,int column){return column>0;}
        };
        List<List<PrintAddonRequest>> addons=new ArrayList<>();
        for(var line:details.lines()) {
            model.addRow(new Object[]{line.itemName()+(line.variantName()==null?"":" / "+line.variantName()),text(line.width()),text(line.length()),text(line.configuredRate()==null?line.rate():line.configuredRate()),"0","",""});
            addons.add(new ArrayList<>(line.addons()));
        }
        JTable table=new JTable(model);table.setRowHeight(28);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(ui.design.DeckersPalette.fieldBackground());table.setForeground(ui.design.DeckersPalette.text());
        JButton editAddons=new JButton("Edit selected unit's add-ons");
        editAddons.addActionListener(e->{int row=table.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(parent,"Select an item first.");return;}editAddons(parent,addons.get(row),details);});
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.setBackground(ui.design.DeckersPalette.surface());
        JTextArea instructions=new JTextArea("Set the final price for each saved unit. Area items need width and length; the configured material width is used for billing. Edit add-on charges and print line counts separately. Deposits received: $"+details.paid()+". Automatic customer discount: "+details.customerDiscountPercent()+"%.");
        instructions.setEditable(false);instructions.setLineWrap(true);instructions.setWrapStyleWord(true);instructions.setOpaque(false);instructions.setRows(3);panel.add(instructions,BorderLayout.NORTH);
        panel.add(new JScrollPane(table),BorderLayout.CENTER);panel.add(editAddons,BorderLayout.SOUTH);panel.setPreferredSize(new Dimension(1000,440));
        while(JOptionPane.showConfirmDialog(parent,panel,"Finalize price",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION) {
            try {
                if(table.isEditing()&&!table.getCellEditor().stopCellEditing())continue;
                List<PricingLine> inputs=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
                for(int i=0;i<details.lines().size();i++) {
                    var saved=details.lines().get(i);
                    var input=new PricingLine(saved.lineId(),number(model,i,1,true),number(model,i,2,true),number(model,i,3,false),List.copyOf(addons.get(i)),number(model,i,4,false),value(model,i,5),null,value(model,i,6),null);
                    total=total.add(CustomOrderPricingService.calculate(saved,input,details.customerDiscountPercent(),details.roundToNearestTwenty()).total());inputs.add(input);
                }
                BigDecimal balance=CustomOrderPricingService.finalBalance(total,details.paid());
                if(JOptionPane.showConfirmDialog(parent,"Final total: $"+total+"\nDeposits: $"+details.paid()+"\nBalance due: $"+balance,"Confirm final price",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)continue;
                List<PricingLine> approved=new ArrayList<>();boolean cancelled=false;
                for(int i=0;i<inputs.size();i++) {
                    var input=inputs.get(i);var saved=details.lines().get(i);String priceToken=null,discountToken=null,priceReason=input.priceOverrideReason(),discountReason=input.discountReason();
                    if(saved.configuredRate()!=null&&input.rate().compareTo(saved.configuredRate())!=0) {
                        if(priceReason.isBlank())throw new IllegalArgumentException("Enter a price override reason for "+saved.itemName()+".");
                        if(!PermissionManager.hasPermission("CUSTOM_ORDER_PRICE_OVERRIDE")&&!PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
                            var approval=ManagerApprovalService.requestApproval(parent,"CUSTOM_ORDER_PRICE_OVERRIDE","Custom Order Price Override","Reason for price override:");if(approval==null){cancelled=true;break;}priceToken=approval.lanApprovalToken();priceReason=approval.reason();
                        }
                    }
                    if(input.discountPercent().signum()>0) {
                        if(discountReason.isBlank())throw new IllegalArgumentException("Enter a discount reason for "+saved.itemName()+".");
                        if(!PermissionManager.hasPermission("CUSTOM_ORDER_LINE_DISCOUNT")&&!PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
                            var approval=ManagerApprovalService.requestApproval(parent,"CUSTOM_ORDER_LINE_DISCOUNT","Custom Order Line Discount Override","Reason for discount override:");if(approval==null){cancelled=true;break;}discountToken=approval.lanApprovalToken();discountReason=approval.reason();
                        }
                    }
                    approved.add(new PricingLine(input.lineId(),input.width(),input.length(),input.rate(),input.addons(),input.discountPercent(),discountReason,discountToken,priceReason,priceToken));
                }
                if(cancelled)continue;
                LanApiClient.ChargeAuthorization authorization=null;
                if(balance.signum()>0&&details.requireChargeAuthorization()){authorization=ui.helpers.CustomerChargeAuthorizationDialog.capture(parent,details.customerName());if(authorization==null)continue;}
                JsonObject body=new JsonObject();body.addProperty("action","FINALIZE_PRICE");body.addProperty("orderId",id);body.add("lines",gson.toJsonTree(approved));body.add("authorization",gson.toJsonTree(authorization));return body;
            } catch(Exception ex){JOptionPane.showMessageDialog(parent,ex.getMessage(),"Final price",JOptionPane.ERROR_MESSAGE);}
        }
        return null;
    }
    private static void editAddons(Component parent,List<PrintAddonRequest> addons,PricingDetails details) {
        DefaultTableModel model=new DefaultTableModel(new String[]{"Material","Description","Print lines","Charge"},0){public boolean isCellEditable(int r,int c){return c>0;}};
        for(var a:addons)model.addRow(new Object[]{a.materialName(),a.printDescription(),a.printLineCount(),a.printCharge()});
        JTable table=new JTable(model);table.setRowHeight(28);table.setBackground(ui.design.DeckersPalette.fieldBackground());table.setForeground(ui.design.DeckersPalette.text());
        List<PrintAddonRequest> originals=new ArrayList<>(addons);
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.add(new JScrollPane(table),BorderLayout.CENTER);
        JButton add=new JButton("Add manual charge");add.setEnabled(PermissionManager.hasPermission("MANUAL_CUSTOM_ORDER_ENTRY"));
        add.addActionListener(e->{String name=JOptionPane.showInputDialog(parent,"Manual material / charge name:");if(name==null||name.isBlank())return;originals.add(new PrintAddonRequest(null,name.trim(),null,"Manual","FIXED_PRESET","",1,BigDecimal.ZERO));model.addRow(new Object[]{name.trim(),"",1,"0"});});JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));buttons.add(add);
        JButton catalog=new JButton("Add catalog material");buttons.add(catalog);
        catalog.addActionListener(e->{
            JComboBox<CustomOrderDataService.PrintMaterialOption> material=new JComboBox<>(details.materials().toArray(CustomOrderDataService.PrintMaterialOption[]::new));
            JComboBox<CustomOrderDataService.PrintSizePresetOption> preset=new JComboBox<>();
            Runnable load=()->{preset.removeAllItems();preset.addItem(new CustomOrderDataService.PrintSizePresetOption(null,null,"Custom print charge","FIXED_PRESET",null));var selected=(CustomOrderDataService.PrintMaterialOption)material.getSelectedItem();if(selected!=null)details.presets().stream().filter(p->p.printMaterialId().equals(selected.printMaterialId())).forEach(preset::addItem);};
            material.addActionListener(event->load.run());load.run();JPanel form=new JPanel(new GridLayout(2,2,8,8));form.add(new JLabel("Material"));form.add(material);form.add(new JLabel("Preset"));form.add(preset);
            if(JOptionPane.showConfirmDialog(parent,form,"Print material",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            var m=(CustomOrderDataService.PrintMaterialOption)material.getSelectedItem();var p=(CustomOrderDataService.PrintSizePresetOption)preset.getSelectedItem();if(m==null||p==null)return;
            BigDecimal charge=p.fixedPrice()==null?BigDecimal.ZERO:p.fixedPrice();originals.add(new PrintAddonRequest(m.printMaterialId(),m.materialName(),p.printSizePresetId(),p.presetName(),p.pricingMode(),"",1,charge));model.addRow(new Object[]{m.materialName(),"",1,charge});
        });
        panel.add(buttons,BorderLayout.SOUTH);panel.setPreferredSize(new Dimension(660,300));
        while(JOptionPane.showConfirmDialog(parent,panel,"Final add-on charges",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try {
            if(table.isEditing()&&!table.getCellEditor().stopCellEditing())continue;List<PrintAddonRequest> changed=new ArrayList<>();
            for(int i=0;i<originals.size();i++){var a=originals.get(i);int count=Integer.parseInt(value(model,i,2));BigDecimal charge=number(model,i,3,false);if(count<1||charge.signum()<0)throw new IllegalArgumentException("Enter positive line counts and non-negative charges.");changed.add(new PrintAddonRequest(a.printMaterialId(),a.materialName(),a.printSizePresetId(),a.printSizeName(),a.pricingMode(),value(model,i,1),count,charge));}
            addons.clear();addons.addAll(changed);return;
        }catch(Exception ex){JOptionPane.showMessageDialog(parent,ex.getMessage());}
    }
    private static String value(DefaultTableModel m,int row,int col){return String.valueOf(m.getValueAt(row,col)).trim();}
    private static BigDecimal number(DefaultTableModel m,int row,int col,boolean optional){String text=value(m,row,col);if(optional&&text.isBlank())return null;try{return new BigDecimal(text);}catch(Exception ex){throw new IllegalArgumentException("Enter a valid "+m.getColumnName(col)+" for "+m.getValueAt(row,0)+".");}}
    private static String text(BigDecimal value){return value==null?"":value.stripTrailingZeros().toPlainString();}
}
