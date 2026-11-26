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

/** Finalizes existing and staged new units while preserving saved lines and attachments. */
final class CustomOrderPricingDialog {
    private CustomOrderPricingDialog() {}
    static JsonObject prompt(Component parent,long id,PricingDetails details) {
        var gson=LanJson.create();
        DefaultTableModel model=new DefaultTableModel(new String[]{"Item / quantity","Width","Length","Unit price / rate","Discount %","Discount reason","Price override reason"},0) {
            public boolean isCellEditable(int row,int column){return column>0;}
        };
        List<Snapshot> savedLines=new ArrayList<>(details.lines());
        List<AddedLine> addedLines=new ArrayList<>();
        long[] nextTemporaryId={-1};
        List<List<Integer>> groups=groups(savedLines);
        List<List<PrintAddonRequest>> addons=new ArrayList<>();
        for(var group:groups) {
            var line=savedLines.get(group.get(0));
            model.addRow(new Object[]{label(line,group.size()),text(line.width()),text(line.length()),text(line.configuredRate()==null?line.rate():line.configuredRate()),"0","",""});
            addons.add(new ArrayList<>(line.addons()));
        }
        JTable table=new JTable(model);table.setRowHeight(28);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(ui.design.DeckersPalette.fieldBackground());table.setForeground(ui.design.DeckersPalette.text());
        JButton editAddons=new JButton("Edit selected group's add-ons");
        editAddons.addActionListener(e->{int row=table.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(parent,"Select an item group first.");return;}editAddons(parent,addons.get(row),details);});
        JButton split=new JButton("Edit selected group individually");
        split.addActionListener(e->{
            if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;
            int row=table.getSelectedRow();if(row<0)return;
            var members=groups.get(row);if(members.size()<2)return;
            Object[] values=new Object[model.getColumnCount()];for(int col=0;col<values.length;col++)values[col]=model.getValueAt(row,col);
            var shared=new ArrayList<>(addons.get(row));
            groups.remove(row);addons.remove(row);model.removeRow(row);
            for(int i=0;i<members.size();i++){
                int index=members.get(i);groups.add(row+i,List.of(index));addons.add(row+i,new ArrayList<>(shared));
                Object[] copy=values.clone();copy[0]=label(savedLines.get(index),1)+" / unit "+(index+1);model.insertRow(row+i,copy);
            }
        });
        JButton addItem=new JButton("Add item line");
        addItem.addActionListener(e->{
            if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;
            List<Object> choices=new ArrayList<>();choices.add("Manual item");
            if(details.additionOptions()!=null)choices.addAll(details.additionOptions());
            JComboBox<Object> item=new JComboBox<>(choices.toArray());
            JTextField description=new JTextField(30),size=new JTextField(12),color=new JTextField(12);
            JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,500,1));
            JTextArea lineNotes=new JTextArea(3,30);lineNotes.setLineWrap(true);lineNotes.setWrapStyleWord(true);
            JPanel form=new JPanel(new GridLayout(0,2,8,8));
            form.add(new JLabel("Catalogue item or manual:"));form.add(item);
            form.add(new JLabel("Manual description:"));form.add(description);
            form.add(new JLabel("Manual size:"));form.add(size);form.add(new JLabel("Manual colour:"));form.add(color);
            form.add(new JLabel("Quantity:"));form.add(quantity);form.add(new JLabel("Item instructions:"));form.add(new JScrollPane(lineNotes));
            item.addActionListener(event->{boolean manual=item.getSelectedIndex()==0;description.setEnabled(manual);size.setEnabled(manual);color.setEnabled(manual);});
            while(JOptionPane.showConfirmDialog(parent,form,"Add item to final price",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
                boolean manual=item.getSelectedIndex()==0;
                if(manual&&!PermissionManager.hasPermission("MANUAL_CUSTOM_ORDER_ENTRY")){JOptionPane.showMessageDialog(parent,"You need Manual Custom Order Entry permission.");continue;}
                if(manual&&description.getText().isBlank()){JOptionPane.showMessageDialog(parent,"Enter the item description.");continue;}
                try{quantity.commitEdit();}catch(java.text.ParseException invalid){JOptionPane.showMessageDialog(parent,"Enter a valid quantity.");continue;}
                int count=(Integer)quantity.getValue();if(addedLines.size()+count>500){JOptionPane.showMessageDialog(parent,"Add no more than 500 new units.");continue;}
                AdditionOption option=manual?new AdditionOption(null,null,description.getText().trim(),null,size.getText().trim(),color.getText().trim(),"VARIABLE",null,null,null,null):(AdditionOption)item.getSelectedItem();
                List<Integer> members=new ArrayList<>();
                for(int n=0;n<count;n++){
                    long temporaryId=nextTemporaryId[0]--;
                    members.add(savedLines.size());savedLines.add(option.snapshot(temporaryId));
                    addedLines.add(new AddedLine(temporaryId,option.itemId(),option.variantId(),option.name(),option.size(),option.color(),lineNotes.getText(),option.productId()));
                }
                groups.add(members);addons.add(new ArrayList<>());
                model.addRow(new Object[]{label(option.snapshot(-1),count),"","",text(option.rate()==null?BigDecimal.ZERO:option.rate()),"0","",""});
                table.setRowSelectionInterval(model.getRowCount()-1,model.getRowCount()-1);break;
            }
        });
        JTextArea orderNotes=new JTextArea(details.orderNotes()==null?"":details.orderNotes(),5,45);orderNotes.setLineWrap(true);orderNotes.setWrapStyleWord(true);
        JButton removeNew=new JButton("Remove new group");
        removeNew.addActionListener(e->{
            if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;
            int row=table.getSelectedRow();if(row<0)return;
            var members=new ArrayList<>(groups.get(row));
            if(members.stream().anyMatch(index->index<details.lines().size())){JOptionPane.showMessageDialog(parent,"Only newly added lines can be removed here. Use the return workflow for saved items.");return;}
            var removedIds=members.stream().map(index->savedLines.get(index).lineId()).toList();
            addedLines.removeIf(line->removedIds.contains(line.temporaryId()));groups.remove(row);addons.remove(row);model.removeRow(row);
            members.stream().sorted(java.util.Comparator.reverseOrder()).forEach(index->savedLines.remove((int)index));
            for(int i=0;i<groups.size();i++)groups.set(i,groups.get(i).stream().map(index->index-(int)members.stream().filter(removed->removed<index).count()).toList());
        });
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.setOpaque(false);actions.add(addItem);actions.add(removeNew);actions.add(editAddons);actions.add(split);
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.setBackground(ui.design.DeckersPalette.surface());
        JTextArea instructions=new JTextArea("Matching units are grouped. Prices, dimensions, discounts and add-ons apply to EVERY unit in the selected group. Split a group to edit individual units. Area items need width and length; the configured material width is used for billing. Edit add-on charges and print line counts separately. Deposits received: $"+details.paid()+". Automatic customer discount: "+details.customerDiscountPercent()+"%.");
        instructions.setEditable(false);instructions.setLineWrap(true);instructions.setWrapStyleWord(true);instructions.setOpaque(false);instructions.setRows(4);panel.add(instructions,BorderLayout.NORTH);
        JTabbedPane editTabs=new JTabbedPane();editTabs.addTab("Item lines & pricing",new JScrollPane(table));editTabs.addTab("Order notes",new JScrollPane(orderNotes));panel.add(editTabs,BorderLayout.CENTER);panel.add(actions,BorderLayout.SOUTH);panel.setPreferredSize(new Dimension(1000,440));
        while(JOptionPane.showConfirmDialog(parent,panel,"Finalize price",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION) {
            try {
                if(table.isEditing()&&!table.getCellEditor().stopCellEditing())continue;
                List<PricingLine> inputs=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
                for(int i=0;i<savedLines.size();i++) {
                    var saved=savedLines.get(i);
                    int row=rowForUnit(groups,i);
                    var input=new PricingLine(saved.lineId(),number(model,row,1,true),number(model,row,2,true),number(model,row,3,false),List.copyOf(addons.get(row)),number(model,row,4,false),value(model,row,5),null,value(model,row,6),null);
                    total=total.add(CustomOrderPricingService.calculate(saved,input,details.customerDiscountPercent(),details.roundToNearestTwenty()).total());inputs.add(input);
                }
                total=utils.CurrencyFormatter.customOrderTotal(total,details.roundToNearestTwenty());
                BigDecimal balance=CustomOrderPricingService.finalBalance(total,details.paid());
                if(JOptionPane.showConfirmDialog(parent,"Final total: $"+total+"\nDeposits: $"+details.paid()+"\nBalance due: $"+balance,"Confirm final price",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)continue;
                List<PricingLine> approved=new ArrayList<>();boolean cancelled=false;
                for(int i=0;i<inputs.size();i++) {
                    var input=inputs.get(i);var saved=savedLines.get(i);String priceToken=null,discountToken=null,priceReason=input.priceOverrideReason(),discountReason=input.discountReason();
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
                JsonObject body=new JsonObject();body.addProperty("action","FINALIZE_PRICE");body.addProperty("orderId",id);body.add("lines",gson.toJsonTree(approved));body.add("addedLines",gson.toJsonTree(addedLines));body.addProperty("orderNotes",orderNotes.getText());body.addProperty("originalOrderNotes",details.orderNotes()==null?"":details.orderNotes());body.add("authorization",gson.toJsonTree(authorization));return body;
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
    // Group only units with the same complete pricing snapshot; preserve distinct add-on text.
    static List<List<Integer>> groups(List<Snapshot> lines) {
        var grouped=new java.util.LinkedHashMap<String,List<Integer>>();
        for(int i=0;i<lines.size();i++){
            var key=LanJson.create().toJsonTree(lines.get(i)).getAsJsonObject();key.remove("lineId");
            grouped.computeIfAbsent(key.toString(),unused->new ArrayList<>()).add(i);
        }
        return new ArrayList<>(grouped.values());
    }
    static int rowForUnit(List<List<Integer>> groups,int index){
        for(int row=0;row<groups.size();row++)if(groups.get(row).contains(index))return row;
        throw new IllegalArgumentException("Saved unit is missing from the pricing groups.");
    }
    private static String label(Snapshot line,int quantity){return quantity+" × "+line.itemName()+(line.variantName()==null?"":" / "+line.variantName());}
    private static String value(DefaultTableModel m,int row,int col){return String.valueOf(m.getValueAt(row,col)).trim();}
    private static BigDecimal number(DefaultTableModel m,int row,int col,boolean optional){String text=value(m,row,col);if(optional&&text.isBlank())return null;try{return new BigDecimal(text);}catch(Exception ex){throw new IllegalArgumentException("Enter a valid "+m.getColumnName(col)+" for "+m.getValueAt(row,0)+".");}}
    private static String text(BigDecimal value){return value==null?"":value.stripTrailingZeros().toPlainString();}
}
