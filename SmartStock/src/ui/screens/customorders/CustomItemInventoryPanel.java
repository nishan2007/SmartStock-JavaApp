package ui.screens.customorders;

import com.google.gson.*;
import services.*;
import services.LanCustomOrderCatalogAdminService.*;
import ui.helpers.*;
import ui.design.DeckersPalette;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/** Inline catalog editing with explicit variant identity and staged changes. */
final class CustomItemInventoryPanel extends JPanel {
    private static final Gson JSON=LanJson.create();
    private final Map<String,JsonObject> originals=new LinkedHashMap<>(),drafts=new LinkedHashMap<>();
    private final Set<Long> expanded=new HashSet<>();
    private final List<String> rowKeys=new ArrayList<>();
    private State state;
    private boolean rendering,saving;
    final JTextField search=new JTextField();
    final JCheckBox showCreated=new JCheckBox("Show date added");
    final JCheckBox showTemplate=new JCheckBox("Show template size");
    final JCheckBox businessCardsOnly=new JCheckBox("Business Cards only");
    final JCheckBox largeFormatPrintingOnly=new JCheckBox("Large Format Printing only");
    final JCheckBox laserOnly=new JCheckBox("Laser only");
    final JCheckBox sublimationOnly=new JCheckBox("Sublimation only");
    final JCheckBox shirtsOnly=new JCheckBox("Shirts only");
    final JCheckBox archivedOnly=new JCheckBox("Archived items");
    final JComboBox<String> dateSort=new JComboBox<>(new String[]{"Default order","Newest first","Oldest first"});
    private final JLabel status=new JLabel("Edit cells, then press Enter or Save Changes.");
    private final JButton save=new JButton("Save Changes");
    private final Runnable refresh;
    private final BiConsumer<String,JsonObject> writer;
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Item / Variant","Size","Colour","Brand","Barcode","Price / Rate","Qty","Reorder","Active","Pricing","Image","Date Added","Sell in POS","Description","Template Size","Category","Shirt setup","Shirt style","Enable designer","Fabric","Shirt type","Neckline"},0){
        public boolean isCellEditable(int row,int col){
            if(archivedOnly.isSelected())return false;
            if(col>=18&&col<=21){if(saving||rowKeys.get(row).startsWith("V:"))return false;JsonObject item=parentRow(row);return flag(item,"isShirt")&&(col!=21||!"COLLARED".equals(text(shirtConfig(item),"shirtType")));}
            if(col==12||col==13||col==15)return !saving&&!rowKeys.get(row).startsWith("V:");
            return !saving&&!rowKeys.get(row).startsWith("G:")&&col<9
                    && !((col==6||col==7)&&"SERVICE".equals(originals.get(rowKeys.get(row)).get("productType")==null?"":originals.get(rowKeys.get(row)).get("productType").getAsString()));
        }
        public Class<?> getColumnClass(int column){return column==8||column==12||column==18?Boolean.class:Object.class;}
    };
    final JTable table=new JTable(model);
    CustomItemInventoryPanel(Window owner,Runnable refresh,BiConsumer<String,JsonObject> writer,java.util.function.Consumer<Long> editItem){
        super(new BorderLayout(8,8));this.refresh=refresh;this.writer=writer;
        setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel top=new JPanel(new BorderLayout(8,0));top.add(new JLabel("Search:"),BorderLayout.WEST);top.add(search);
        JButton reload=new JButton("Refresh / Discard");JPanel actions=new JPanel();JButton editParent=new JButton("Edit Item Details");actions.add(editParent);actions.add(save);actions.add(reload);editParent.addActionListener(e->{if(saving)return;if(!drafts.isEmpty()){JOptionPane.showMessageDialog(this,"Save or discard table changes before opening Item Details.");return;}int row=table.getSelectedRow();if(row<0)return;String key=rowKeys.get(row);JsonObject value=key.startsWith("G:")?originals.get("I:"+key.substring(2)):originals.get(key);editItem.accept(value.get("itemId").getAsLong());});top.add(actions,BorderLayout.EAST);add(top,BorderLayout.NORTH);
        table.setRowHeight(30);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<model.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==0?300:120);
        table.getColumnModel().getColumn(10).setMinWidth(0);table.getColumnModel().getColumn(10).setMaxWidth(0);
        actions.add(showCreated);actions.add(dateSort);
        actions.add(archivedOnly);
        JButton archive=new JButton("Archive / Restore");actions.add(archive);
        JPanel options=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        options.add(sublimationOnly);sublimationOnly.addActionListener(e->render());options.add(shirtsOnly);shirtsOnly.addActionListener(e->render());options.add(showCreated);options.add(showTemplate);options.add(dateSort);options.add(archivedOnly);options.add(archive);
        JPanel serviceOptions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        for(JCheckBox filter:List.of(businessCardsOnly,largeFormatPrintingOnly,laserOnly)){serviceOptions.add(filter);filter.addActionListener(e->render());}
        JPanel filterRows=new JPanel(new GridLayout(0,1,0,6));filterRows.add(options);filterRows.add(serviceOptions);
        JPanel header=new JPanel(new BorderLayout(0,8));header.add(top,BorderLayout.NORTH);header.add(filterRows,BorderLayout.SOUTH);add(header,BorderLayout.NORTH);
        archivedOnly.addActionListener(e->render());
        archive.addActionListener(e->{
            if(saving)return;
            stopEditing();
            if(!drafts.isEmpty()){JOptionPane.showMessageDialog(this,"Save or discard pending edits before archiving or restoring.");return;}
            int selected=table.getSelectedRow();if(selected<0)return;
            String key=rowKeys.get(selected);
            JsonObject selectedItem=key.startsWith("G:")?originals.get("I:"+key.substring(2)):originals.get(key);
            long parentId=selectedItem.get("itemId").getAsLong();
            JsonObject item=originals.get("I:"+parentId).deepCopy();
            String action=item.get("archived").getAsBoolean()?"RESTORE_ITEM":"ARCHIVE_ITEM";
            saving=true;save.setEnabled(false);search.setEnabled(false);
            UiTaskRunner.submit(owner,"custom-catalog.archive",()->{writer.accept(action,item);return true;},ignored->{
                saving=false;save.setEnabled(true);search.setEnabled(true);refresh.run();
            },failure->{saving=false;save.setEnabled(true);search.setEnabled(true);JOptionPane.showMessageDialog(this,"Could not archive or restore: "+failure.getMessage());});
        });
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int column=18;column<=21;column++)table.getColumnModel().getColumn(column).setPreferredWidth(140);
        table.getColumnModel().getColumn(19).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Normal","Dry-fit"})));
        table.getColumnModel().getColumn(20).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"T-shirt","Polo"})));
        table.getColumnModel().getColumn(21).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Round neck","V-neck"})));
        table.getColumnModel().getColumn(15).setCellEditor(new DefaultCellEditor(new JComboBox<>(Arrays.stream(CustomItemCategory.values()).map(Object::toString).toArray(String[]::new))));
        table.getColumnModel().getColumn(15).setPreferredWidth(180);
        setDateVisible(false);
        table.moveColumn(12,9);
        for(int column=18;column<=21;column++)table.moveColumn(table.convertColumnIndexToView(column),table.convertColumnIndexToView(16));
        table.moveColumn(table.convertColumnIndexToView(13),1);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        setTemplateVisible(false);
        showTemplate.addActionListener(e->setTemplateVisible(showTemplate.isSelected()));
        showCreated.addActionListener(e->setDateVisible(showCreated.isSelected()));
        dateSort.addActionListener(e->render());
        add(new JScrollPane(table),BorderLayout.CENTER);add(status,BorderLayout.SOUTH);
        installEnterSave(table,()->save(owner));
        model.addTableModelListener(e->{
            if(!rendering&&e.getFirstRow()>=0&&e.getColumn()>=18&&e.getColumn()<=21){stageShirt(e.getFirstRow(),e.getColumn());return;}
            if(rendering||e.getType()!=javax.swing.event.TableModelEvent.UPDATE||e.getFirstRow()<0||e.getColumn()<0||(e.getColumn()>=9&&e.getColumn()!=12&&e.getColumn()!=13&&e.getColumn()!=15))return;
            int row=e.getFirstRow();String key=rowKeys.get(row);if(key.startsWith("G:")){if(e.getColumn()!=12&&e.getColumn()!=13&&e.getColumn()!=15)return;key="I:"+key.substring(2);}
            JsonObject draft=drafts.computeIfAbsent(key,k->originals.get(k).deepCopy());
            String field=e.getColumn()==15?"category":e.getColumn()==13?"description":e.getColumn()==12?"sellInPos":new String[]{"name","size","color","brand","barcode","fixedPrice","quantity","reorderLevel","active"}[e.getColumn()];
            Object value=model.getValueAt(row,e.getColumn());if(field.equals("name"))value=String.valueOf(value).stripLeading();if(field.equals("category"))CustomItemCategory.parse(value).apply(draft);else draft.add(field,JSON.toJsonTree(value));status.setText(drafts.size()+" row(s) changed.");
            if(key.startsWith("V:")&&List.of("size","color","brand").contains(field)){
                long parentId=draft.get("itemId").getAsLong();
                Item parent=state.items().stream().filter(item->item.itemId()==parentId).findFirst().orElse(null);
                int parentRow=rowKeys.indexOf("G:"+parentId);
                if(parent!=null&&parentRow>=0){
                    rendering=true;
                    try{model.setValueAt(variantSummary(parent,field),parentRow,switch(field){case "size"->1;case "color"->2;default->3;});}
                    finally{rendering=false;}
                }
            }
            if(field.equals("category")){rendering=true;try{refreshShirtCells();}finally{rendering=false;}}
            if(List.of("isSublimation","isBusinessCards","isLargeFormatPrinting","isLaser").contains(field)||field.equals("sellInPos")||field.equals("description")||field.equals("category")){
                rendering=true;
                try{for(int child=0;child<rowKeys.size();child++){
                    String childKey=rowKeys.get(child);
                    if(childKey.startsWith("V:")&&originals.get(childKey).get("itemId").getAsLong()==draft.get("itemId").getAsLong())model.setValueAt(value,child,e.getColumn());
                }}finally{rendering=false;}
            }
        });
        table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            int row=table.rowAtPoint(e.getPoint());if(row<0||table.columnAtPoint(e.getPoint())!=0)return;
            String key=rowKeys.get(row);if(!key.startsWith("G:"))return;
            stopEditing();long id=Long.parseLong(key.substring(2));if(!expanded.remove(id))expanded.add(id);render();
        }});
        if(owner!=null)TableImageHoverPreview.install(owner,table,row->{
            if(row<0||row>=rowKeys.size())return List.of();String key=rowKeys.get(row);
            if(key.startsWith("G:")){
                long id=Long.parseLong(key.substring(2));
                return state.items().stream().filter(i->i.itemId()==id).findFirst().map(CustomItemPictures::forItem).orElse(List.of());
            }
            JsonObject value=drafts.getOrDefault(key,originals.get(key));
            return List.of(new TableImageHoverPreview.Picture(imageUrl(value),text(value,"name")));
        },DeckersPalette.ORANGE);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){render();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){render();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){render();}
        });
        reload.addActionListener(e->{if(saving)return;if(!drafts.isEmpty()&&JOptionPane.showConfirmDialog(this,"Discard unsaved changes?","Refresh",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;drafts.clear();refresh.run();});
        save.addActionListener(e->save(owner));
    }
    void setState(State loaded){
        if(!drafts.isEmpty()||saving)return;
        state=loaded;originals.clear();
        for(Item item:state.items()){originals.put("I:"+item.itemId(),JSON.toJsonTree(item).getAsJsonObject());
            for(Variant variant:item.variants()){JsonObject row=JSON.toJsonTree(variant).getAsJsonObject();
                row.addProperty("pricingType",item.pricingType());row.addProperty("productType",item.productType());

                originals.put("V:"+variant.variantId(),row);}}
        render();
    }
    JsonObject staged(String key){return drafts.get(key)==null?null:drafts.get(key).deepCopy();}
    private void stopEditing(){if(table.isEditing())table.getCellEditor().stopCellEditing();}
    static void installEnterSave(JTable table,Runnable saveChanges){
        String key="custom-inventory.save-on-enter";
        Action action=new AbstractAction(){public void actionPerformed(ActionEvent event){
            if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;
            saveChanges.run();
        }};
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),key);
        table.getActionMap().put(key,action);
        if(table.getDefaultEditor(Object.class) instanceof DefaultCellEditor editor && editor.getComponent() instanceof JTextField field){
            field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),key);
            field.getActionMap().put(key,action);
        }
    }
    private void render(){
        if(state==null||saving)return;stopEditing();rendering=true;
        try{model.setRowCount(0);rowKeys.clear();String query=search.getText().trim().toLowerCase(Locale.ROOT);
            for(Item item:sortedItems()){
                if(item.archived()!=archivedOnly.isSelected())continue;
                String itemKey="I:"+item.itemId();JsonObject itemData=drafts.getOrDefault(itemKey,originals.get(itemKey));
                if(businessCardsOnly.isSelected()&&!flag(itemData,"isBusinessCards"))continue;
                if(largeFormatPrintingOnly.isSelected()&&!flag(itemData,"isLargeFormatPrinting"))continue;
                if(laserOnly.isSelected()&&!flag(itemData,"isLaser"))continue;
                if(sublimationOnly.isSelected()&&!flag(itemData,"isSublimation"))continue;
                if(shirtsOnly.isSelected()&&(!itemData.has("isShirt")||!itemData.get("isShirt").getAsBoolean()))continue;
                boolean match=matchesSearch(itemData,query);
                List<Variant> variants=item.variants().stream().filter(v->match||matchesSearch(drafts.getOrDefault("V:"+v.variantId(),originals.get("V:"+v.variantId())),query,itemData)).toList();
                if(!match&&variants.isEmpty())continue;
                if(item.hasVariants()){
                    rowKeys.add("G:"+item.itemId());model.addRow(new Object[]{(expanded.contains(item.itemId())||!query.isBlank()?"▾ ":"▸ ")+text(itemData,"name"),variantSummary(item,"size"),variantSummary(item,"color"),variantSummary(item,"brand"),"",null,item.quantity(),item.reorderLevel(),item.active(),item.pricingType(),item.imageUrl(),displayDate(item.createdAt()),itemData.has("sellInPos")&&itemData.get("sellInPos").getAsBoolean(),text(itemData,"description"),templateSummary(item,itemData),CustomItemCategory.from(itemData).toString(),item.shirtReadiness(),item.isShirt()?ShirtDesignerService.styleLabel(item.shirtDesignerConfig()):""});
                    if(expanded.contains(item.itemId())||!query.isBlank())for(Variant v:variants)add("V:"+v.variantId(),true);
                }else add(itemKey,false);
            }
            refreshShirtCells();
        }finally{rendering=false;}
    }
    private static boolean matchesSearch(JsonObject row,String query,JsonObject... parents){
        StringBuilder value=new StringBuilder();
        for(String field:List.of("name","description","size","color","brand","sku","barcode")){
            value.append(' ').append(text(row,field));
            for(JsonObject parent:parents)value.append(' ').append(text(parent,field));
        }
        if(row.has("extraBarcodes")&&row.get("extraBarcodes").isJsonArray())
            for(JsonElement barcode:row.getAsJsonArray("extraBarcodes"))if(barcode.isJsonPrimitive())value.append(' ').append(barcode.getAsString());
        if(flag(row,"isSublimation")||Arrays.stream(parents).anyMatch(parent->flag(parent,"isSublimation")))value.append(" sublimation");
        if(flag(row,"isBusinessCards")||Arrays.stream(parents).anyMatch(parent->flag(parent,"isBusinessCards")))value.append(" business cards");
        if(flag(row,"isLargeFormatPrinting")||Arrays.stream(parents).anyMatch(parent->flag(parent,"isLargeFormatPrinting")))value.append(" large format printing");
        if(flag(row,"isLaser")||Arrays.stream(parents).anyMatch(parent->flag(parent,"isLaser")))value.append(" laser");
        value.append(' ').append(ShirtDesignerService.searchLabels(flag(row,"isShirt"),shirtConfig(row)));
        for(JsonObject parent:parents)value.append(' ').append(ShirtDesignerService.searchLabels(flag(parent,"isShirt"),shirtConfig(parent)));
        List<String> identifiers=new ArrayList<>();
        for(JsonObject source:java.util.stream.Stream.concat(java.util.stream.Stream.of(row),Arrays.stream(parents)).toList()){
            identifiers.add(text(source,"sku"));identifiers.add(text(source,"barcode"));
            if(source.has("extraBarcodes")&&source.get("extraBarcodes").isJsonArray())
                for(JsonElement barcode:source.getAsJsonArray("extraBarcodes"))if(barcode.isJsonPrimitive())identifiers.add(barcode.getAsString());
        }
        return CustomCatalogSearch.matches(value.toString(),identifiers,query);
    }
    private String variantSummary(Item item,String field){
        JsonObject parent=drafts.getOrDefault("I:"+item.itemId(),originals.get("I:"+item.itemId()));
        Map<String,String> values=new LinkedHashMap<>();
        for(Variant variant:item.variants()){
            String key="V:"+variant.variantId();
            String value=text(drafts.getOrDefault(key,originals.get(key)),field).trim();
            if(value.isBlank())value=text(parent,field).trim();
            if(!value.isBlank())values.putIfAbsent(value.toLowerCase(Locale.ROOT),value);
        }
        return values.isEmpty()?text(parent,field):String.join(", ",values.values());
    }
    private void add(String key,boolean variant){
        JsonObject row=drafts.getOrDefault(key,originals.get(key));rowKeys.add(key);
        JsonObject parent=variant?drafts.getOrDefault("I:"+row.get("itemId").getAsLong(),originals.get("I:"+row.get("itemId").getAsLong())):row;
        model.addRow(new Object[]{(variant?"    ":"")+text(row,"name"),text(row,"size"),text(row,"color"),text(row,"brand"),text(row,"barcode"),text(row,"fixedPrice"),text(row,"quantity"),text(row,"reorderLevel"),row.get("active").getAsBoolean(),text(row,"pricingType"),text(row,"imageUrl"),displayDate(text(row,"createdAt")),parent.has("sellInPos")&&parent.get("sellInPos").getAsBoolean(),text(parent,"description"),templateSize(row,parent),CustomItemCategory.from(parent).toString(),text(parent,"shirtReadiness"),parent.get("isShirt").getAsBoolean()?ShirtDesignerService.styleLabel(parent.has("shirtDesignerConfig")&&!parent.get("shirtDesignerConfig").isJsonNull()?parent.getAsJsonObject("shirtDesignerConfig"):null):""});
    }
    private static boolean flag(JsonObject value,String key){return value!=null&&value.has(key)&&!value.get(key).isJsonNull()&&value.get(key).getAsBoolean();}
    private JsonObject parentRow(int row){String key=rowKeys.get(row);if(key.startsWith("G:"))key="I:"+key.substring(2);else if(key.startsWith("V:"))key="I:"+originals.get(key).get("itemId").getAsLong();return drafts.getOrDefault(key,originals.get(key));}
    private static JsonObject shirtConfig(JsonObject item){return item.has("shirtDesignerConfig")&&item.get("shirtDesignerConfig").isJsonObject()&&item.getAsJsonObject("shirtDesignerConfig").size()>0?item.getAsJsonObject("shirtDesignerConfig").deepCopy():ShirtDesignerService.defaults();}
    private void refreshShirtCells(){for(int row=0;row<rowKeys.size();row++){JsonObject item=parentRow(row),config=shirtConfig(item);model.setValueAt(CustomItemCategory.from(item).toString(),row,15);boolean shirt=flag(item,"isShirt"),polo="COLLARED".equals(text(config,"shirtType"));model.setValueAt(shirt&&flag(config,"websiteEnabled"),row,18);model.setValueAt(shirt?("DRY_FIT".equals(text(config,"fabric"))?"Dry-fit":"Normal"):"",row,19);model.setValueAt(shirt?(polo?"Polo":"T-shirt"):"",row,20);model.setValueAt(shirt&&!polo?("V".equals(text(config,"neckline"))?"V-neck":"Round neck"):"",row,21);model.setValueAt(shirt?ShirtDesignerService.styleLabel(config):"",row,17);}}
    private void stageShirt(int row,int column){
        if(row>=rowKeys.size()||rowKeys.get(row).startsWith("V:"))return;
        String key=rowKeys.get(row);if(key.startsWith("G:"))key="I:"+key.substring(2);
        JsonObject draft=drafts.computeIfAbsent(key,k->originals.get(k).deepCopy()),config=shirtConfig(draft);Object value=model.getValueAt(row,column);
        if(column==18)config.addProperty("websiteEnabled",Boolean.TRUE.equals(value));
        if(column==19)config.addProperty("fabric","Dry-fit".equals(value)?"DRY_FIT":"NORMAL");
        if(column==20){boolean polo="Polo".equals(value);config.addProperty("shirtType",polo?"COLLARED":"TEE");config.addProperty("neckline",polo?"COLLAR":"ROUND");}
        if(column==21)config.addProperty("neckline","V-neck".equals(value)?"V":"ROUND");
        if(column==20||column==21){String type=text(config,"shirtType"),neck=text(config,"neckline");config.addProperty("frontMockup",ShirtDesignerService.defaultMockup(type,neck,false));config.addProperty("backMockup",ShirtDesignerService.defaultMockup(type,neck,true));}
        draft.add("shirtDesignerConfig",config);rendering=true;try{refreshShirtCells();}finally{rendering=false;}status.setText(drafts.size()+" row(s) changed. Save Changes to apply.");
    }
    private void setDateVisible(boolean visible){
        TableColumn column=table.getColumnModel().getColumn(table.convertColumnIndexToView(11));
        column.setMinWidth(visible?120:0);column.setMaxWidth(visible?Integer.MAX_VALUE:0);
        column.setPreferredWidth(visible?180:0);
    }
    private void setTemplateVisible(boolean visible){
        TableColumn column=table.getColumnModel().getColumn(table.convertColumnIndexToView(14));
        column.setMinWidth(visible?120:0);column.setMaxWidth(visible?Integer.MAX_VALUE:0);
        column.setPreferredWidth(visible?180:0);
    }
    private String templateSummary(Item item,JsonObject parent){
        Set<String> values=new LinkedHashSet<>();
        for(Variant variant:item.variants()){
            String key="V:"+variant.variantId();String value=templateSize(drafts.getOrDefault(key,originals.get(key)),parent);
            if(!value.isBlank())values.add(value);
        }
        return values.isEmpty()?templateSize(parent,parent):String.join(", ",values);
    }
    private static String templateSize(JsonObject row,JsonObject parent){
        String area=text(row,"templateUsesOrderArea");
        if(area.isBlank())area=text(parent,"templateUsesOrderArea");
        if(area.equals("true"))return "Order measurements";
        JsonObject dimensions=text(row,"templateWidth").isBlank()&&text(row,"templateHeight").isBlank()?parent:row;
        String width=text(dimensions,"templateWidth"),height=text(dimensions,"templateHeight");
        if(width.isBlank()||height.isBlank())return "";
        width=new BigDecimal(width).stripTrailingZeros().toPlainString();height=new BigDecimal(height).stripTrailingZeros().toPlainString();
        return width+" × "+height+" "+text(dimensions,"templateUnit");
    }
    private List<Item> sortedItems(){
        if(dateSort.getSelectedIndex()==0)return state.items();
        Comparator<java.time.Instant> order=dateSort.getSelectedIndex()==1?Comparator.reverseOrder():Comparator.naturalOrder();
        Comparator<Item> byDate=Comparator.comparing(item->parseDate(item.createdAt()),Comparator.nullsLast(order));
        return state.items().stream().sorted(byDate.thenComparingLong(Item::itemId)).toList();
    }
    private static java.time.Instant parseDate(String date){
        try{return java.time.Instant.parse(date);}catch(Exception ignored){return null;}
    }
    private static String displayDate(String date){
        var instant=parseDate(date);return instant==null?"":java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(instant);
    }
    static void validateShirtSetup(JsonObject item){
        if(item.has("isShirt")&&!flag(item,"isShirt"))return;
        if(!item.has("shirtDesignerConfig")||!item.get("shirtDesignerConfig").isJsonObject())return;
        JsonObject config=item.getAsJsonObject("shirtDesignerConfig");
        if(!flag(config,"websiteEnabled"))return;
        try{ShirtDesignerService.configuration(config,true);}
        catch(IllegalArgumentException failure){throw new IllegalArgumentException(text(item,"name")+": "+failure.getMessage()+" Open Edit Item Details > Shirt setup > Print areas, pricing and colors to finish setup.",failure);}
    }
    private void save(Window owner){
        if(saving)return;
        stopEditing();if(drafts.isEmpty())return;
        Map<String,JsonObject> pending=new LinkedHashMap<>();
        try{for(var entry:drafts.entrySet()){
            JsonObject value=entry.getValue().deepCopy();
            if(text(value,"name").isBlank())throw new IllegalArgumentException("Item / variant name is required.");
            for(String field:List.of("fixedPrice","quantity","reorderLevel")){
                String raw=text(value,field).trim();
                if(raw.isBlank()&&field.equals("fixedPrice")){value.add(field,JsonNull.INSTANCE);continue;}
                BigDecimal number=new BigDecimal(raw);
                if(number.signum()<0)throw new IllegalArgumentException("Price, quantity and reorder level cannot be negative.");
                value.addProperty(field,number);
            }
            if(!entry.getKey().startsWith("V:"))validateShirtSetup(value);
            pending.put(entry.getKey(),value);
        }}catch(Exception failure){JOptionPane.showMessageDialog(this,"Check the edited values: "+failure.getMessage());return;}
        saving=true;save.setEnabled(false);search.setEnabled(false);
        UiTaskRunner.submit(owner,"custom-catalog.inline-save",()->{
            List<String> saved=new ArrayList<>();
            Exception failure=null;
            for(var entry:pending.entrySet())try{writer.accept(entry.getKey().startsWith("V:")?"SAVE_VARIANT":"SAVE_ITEM",entry.getValue());saved.add(entry.getKey());}catch(Exception e){failure=e;break;}
            return new SaveResult(saved,failure);
        },result->{
            for(String key:result.saved()){originals.put(key,pending.get(key));drafts.remove(key);}
            saving=false;save.setEnabled(true);search.setEnabled(true);
            if(result.failure()!=null){status.setText("Saved "+result.saved().size()+" rows; remaining edits retained.");JOptionPane.showMessageDialog(this,"Some changes could not be saved: "+result.failure().getMessage());render();}
            else{status.setText("Changes saved.");refresh.run();}
        },failure->{saving=false;save.setEnabled(true);search.setEnabled(true);JOptionPane.showMessageDialog(this,"Save failed: "+failure.getMessage());});
    }
    private String imageUrl(JsonObject row){
        String url=text(row,"imageUrl");
        if(!url.isBlank()||state==null)return url;
        long id=row.get("itemId").getAsLong();
        return state.items().stream().filter(item->item.itemId()==id).map(item->item.imageUrl()==null?"":item.imageUrl()).findFirst().orElse("");
    }
    private record SaveResult(List<String> saved,Exception failure){}
    private static String text(JsonObject row,String field){return row.has(field)&&!row.get(field).isJsonNull()?row.get(field).getAsString():"";}
}
