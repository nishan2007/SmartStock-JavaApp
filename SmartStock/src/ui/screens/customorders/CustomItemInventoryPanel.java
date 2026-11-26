package ui.screens.customorders;

import com.google.gson.*;
import services.*;
import services.LanCustomOrderCatalogAdminService.*;
import ui.helpers.*;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static ui.screens.customorders.CustomCatalogDesign.*;

/** Inline catalog editing with explicit variant identity and staged changes. */
final class CustomItemInventoryPanel extends JPanel {
    private static final Gson JSON=LanJson.create();
    private final Map<String,JsonObject> originals=new LinkedHashMap<>(),drafts=new LinkedHashMap<>();
    private final Set<Long> expanded=new HashSet<>();
    private final List<String> rowKeys=new ArrayList<>();
    private State state;
    private boolean rendering,saving;
    private int sortColumn=-1;
    private boolean ascending=true;
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
    final JComboBox<String> itemTypes = new JComboBox<>(new String[]{"All Item Types"});
    final JComboBox<String> categories = new JComboBox<>(new String[]{"All Categories"});
    final JComboBox<String> brands = new JComboBox<>(new String[]{"All Brands"});
    private final JLabel status=new JLabel("Edit cells, then press Enter or Save Changes.");
    private final JLabel count = new JLabel();
    private final JButton save=new JButton("Save Changes");
    private final Runnable refresh;
    private final BiConsumer<String,JsonObject> writer;
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Item / Variant","Size","Colour","Brand","Barcode","Price / Rate","Qty","Reorder","Active","Pricing","Image","Date Added","Sell in POS","Description","Template Size","Category","Shirt setup","Shirt style","Enable designer","Fabric","Shirt type","Neckline","Template Width","Template Height","Item Type"},0){
        public boolean isCellEditable(int row,int col){
            if(archivedOnly.isSelected())return false;
            if(col>=18&&col<=21){if(saving||rowKeys.get(row).startsWith("V:"))return false;JsonObject item=parentRow(row);return flag(item,"isShirt")&&(col!=21||!"COLLARED".equals(text(shirtConfig(item),"shirtType")));}
            if(col==12||col==13||col==15||col==24)return !saving&&!rowKeys.get(row).startsWith("V:");
            return !saving&&!rowKeys.get(row).startsWith("G:")&&col<9
                    && !((col==6||col==7)&&"SERVICE".equals(originals.get(rowKeys.get(row)).get("productType")==null?"":originals.get(rowKeys.get(row)).get("productType").getAsString()));
        }
        public Class<?> getColumnClass(int column){return column==8||column==12||column==18?Boolean.class:Object.class;}
    };
    final JTable table=new JTable(model);
    CustomItemInventoryPanel(Window owner,Runnable refresh,BiConsumer<String,JsonObject> writer,Consumer<Long> editItem){
        this(owner, refresh, writer, editItem, () -> { });
    }

    CustomItemInventoryPanel(Window owner,Runnable refresh,BiConsumer<String,JsonObject> writer,Consumer<Long> editItem,Runnable addItem){
        super(new BorderLayout(8,8));this.refresh=refresh;this.writer=writer;
        setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        setBackground(DeckersPalette.background());
        putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        search.putClientProperty("JTextField.placeholderText", "Find item or barcode");
        search.getAccessibleContext().setAccessibleName("Search custom inventory");
        DeckersSwing.styleField(search);
        CustomCatalogDesign.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {242, 70, 85, 112, 120, 98, 60, 75, 70, 92, 0, 112, 87, 205, 105, 180, 120, 120, 130, 110, 115, 110, 124, 124, 124};
        for(int i=0;i<model.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(10).setMinWidth(0);table.getColumnModel().getColumn(10).setMaxWidth(0);
        JButton add = addButton("Add Item", () -> { if (canOpenEditor()) addItem.run(); });
        JButton edit = button("Edit Item Details", () -> {
            if (!canOpenEditor()) return;
            int selected = table.getSelectedRow();
            if (selected >= 0) editItem.accept(parentRow(selected).get("itemId").getAsLong());
        });
        edit.setEnabled(false);
        JButton reload = new JButton("Refresh");
        JButton discard = new JButton("Discard");
        JButton archive = new JButton("Archive");
        JButton restore = new JButton("Restore");
        archive.setEnabled(false);
        restore.setEnabled(false);
        Runnable updateSelection = () -> {
            boolean selected = table.getSelectedRow() >= 0;
            edit.setEnabled(selected);
            archive.setEnabled(selected && !archivedOnly.isSelected());
            restore.setEnabled(selected && archivedOnly.isSelected());
        };
        table.getSelectionModel().addListSelectionListener(event -> updateSelection.run());

        JPanel actions = surface(new FlowLayout(FlowLayout.LEFT, 7, 0));
        for (JButton action : List.of(add, edit, save, reload, discard, archive, restore)) actions.add(action);
        JPanel displayOptions = surface(new FlowLayout(FlowLayout.LEFT, 8, 0));
        displayOptions.add(new JLabel("Date order"));
        displayOptions.add(dateSort);
        displayOptions.add(showCreated);
        displayOptions.add(showTemplate);
        displayOptions.add(archivedOnly);
        JPanel searchRow = surface(new FlowLayout(FlowLayout.LEFT, 8, 0));
        search.setColumns(25);
        JLabel searchLabel = new JLabel("Search", OrdersIcons.icon("search", DeckersPalette.muted(), 13), SwingConstants.LEFT);
        searchLabel.setLabelFor(search);
        searchRow.add(searchLabel);
        searchRow.add(search);
        searchRow.add(displayOptions);

        JPanel serviceOptions = surface(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (JCheckBox filter : List.of(sublimationOnly, shirtsOnly, businessCardsOnly, largeFormatPrintingOnly, laserOnly)) {
            serviceOptions.add(filter);
            filter.addActionListener(event -> render());
        }
        JPanel filterRows = surface(new GridLayout(0, 1, 0, 6));
        filterRows.add(actions);
        filterRows.add(searchRow);
        JPanel classificationFilters = surface(new FlowLayout(FlowLayout.LEFT, 8, 0));
        classificationFilters.add(new JLabel("Filter"));
        itemTypes.getAccessibleContext().setAccessibleName("Filter by item type");
        categories.getAccessibleContext().setAccessibleName("Filter by category");
        brands.getAccessibleContext().setAccessibleName("Filter by brand");
        for (JComboBox<String> filter : List.of(itemTypes, categories, brands)) {
            classificationFilters.add(filter);
            filter.addActionListener(event -> { if (!rendering) render(); });
        }
        filterRows.add(classificationFilters);
        filterRows.add(serviceOptions);

        JButton reviewTemplates=new JButton("Review missing template sizes");
        reviewTemplates.addActionListener(e->{
            if(saving||state==null)return;
            stopEditing();
            if(!drafts.isEmpty()){JOptionPane.showMessageDialog(this,"Save or discard table changes before reviewing template sizes.");return;}
            new TemplateSizeReviewDialog(owner,JSON.toJsonTree(state).getAsJsonObject(),writer,refresh).setVisible(true);
        });
        JPanel statusRow = surface(new BorderLayout(8, 0));
        DeckersSwing.styleBand(statusRow, DeckersPalette.PURPLE, new Insets(4, 8, 4, 8));
        statusRow.add(status);
        statusRow.add(reviewTemplates, BorderLayout.EAST);
        JPanel toolbar = surface(new BorderLayout(0, 8));
        toolbar.add(filterRows);
        toolbar.add(statusRow, BorderLayout.SOUTH);
        JPanel header = surface(new BorderLayout(0, 12));
        header.add(heading("Inventory", "Items, variants and product settings"), BorderLayout.NORTH);
        header.add(toolbar);
        add(header, BorderLayout.NORTH);
        archivedOnly.addActionListener(e->{render();updateSelection.run();});
        java.awt.event.ActionListener changeArchive = e->{
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
        };
        archive.addActionListener(changeArchive);
        restore.addActionListener(changeArchive);
        table.getColumnModel().getColumn(19).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Normal","Dry-fit"})));
        table.getColumnModel().getColumn(20).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"T-shirt","Polo"})));
        table.getColumnModel().getColumn(21).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Round neck","V-neck"})));
        table.getColumnModel().getColumn(15).setCellEditor(new DefaultCellEditor(new JComboBox<>(Arrays.stream(CustomItemCategory.values()).map(Object::toString).toArray(String[]::new))));
        setDateVisible(false);
        int[] order = {0, 13, 1, 2, 24, 3, 4, 5, 6, 7, 8, 12, 9, 15, 14, 22, 23, 11, 16, 17, 18, 19, 20, 21, 10};
        for (int view = 0; view < order.length; view++) table.moveColumn(table.convertColumnIndexToView(order[view]), view);
        setTemplateVisible(false);
        showTemplate.addActionListener(e->setTemplateVisible(showTemplate.isSelected()));
        showCreated.addActionListener(e->setDateVisible(showCreated.isSelected()));
        installHeaderSorting();
        dateSort.addActionListener(e->{
            int choice=dateSort.getSelectedIndex();
            if(choice==0&&sortColumn!=11)return;
            sortColumn=choice==0?-1:11;
            ascending=choice!=1;
            render();
        });
        add(scroll(table),BorderLayout.CENTER);
        JPanel foot = surface(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        foot.add(count);
        add(foot, BorderLayout.SOUTH);
        installEnterSave(table,()->save(owner));
        model.addTableModelListener(e->{
            if(!rendering&&e.getFirstRow()>=0&&e.getColumn()>=18&&e.getColumn()<=21){stageShirt(e.getFirstRow(),e.getColumn());return;}
            if(rendering||e.getType()!=javax.swing.event.TableModelEvent.UPDATE||e.getFirstRow()<0||e.getColumn()<0||(e.getColumn()>=9&&e.getColumn()!=12&&e.getColumn()!=13&&e.getColumn()!=15&&e.getColumn()!=24))return;
            int row=e.getFirstRow();String key=rowKeys.get(row);
            if(e.getColumn()==24&&key.startsWith("V:")){rendering=true;try{model.setValueAt(text(parentRow(row),"itemType"),row,24);}finally{rendering=false;}return;}if(key.startsWith("G:")){if(e.getColumn()!=12&&e.getColumn()!=13&&e.getColumn()!=15&&e.getColumn()!=24)return;key="I:"+key.substring(2);}
            JsonObject draft=drafts.computeIfAbsent(key,k->originals.get(k).deepCopy());
            String field=e.getColumn()==24?"itemType":e.getColumn()==15?"category":e.getColumn()==13?"description":e.getColumn()==12?"sellInPos":new String[]{"name","size","color","brand","barcode","fixedPrice","quantity","reorderLevel","active"}[e.getColumn()];
            Object value=model.getValueAt(row,e.getColumn());if(field.equals("name"))value=String.valueOf(value).stripLeading();if(field.equals("category"))services.CustomItemCategories.apply(value,draft);else draft.add(field,JSON.toJsonTree(value));status.setText(drafts.size()+" row(s) changed.");
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
            if(List.of("isSublimation","isBusinessCards","isLargeFormatPrinting","isLaser").contains(field)||field.equals("sellInPos")||field.equals("description")||field.equals("category")||field.equals("itemType")){
                rendering=true;
                try{for(int child=0;child<rowKeys.size();child++){
                    String childKey=rowKeys.get(child);
                    if(childKey.startsWith("V:")&&originals.get(childKey).get("itemId").getAsLong()==draft.get("itemId").getAsLong())model.setValueAt(value,child,e.getColumn());
                }}finally{rendering=false;}
            }
        });
        table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            int row=table.rowAtPoint(e.getPoint()),column=table.columnAtPoint(e.getPoint());
            if(row<0||column<0||table.convertColumnIndexToModel(column)!=0)return;
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
        reload.addActionListener(e -> { if (discardEdits()) refresh.run(); });
        discard.addActionListener(e -> discardEdits());
        save.addActionListener(e->save(owner));
    }

    boolean canOpenEditor() {
        if (saving || state == null) return false;
        stopEditing();
        if (drafts.isEmpty()) return true;
        JOptionPane.showMessageDialog(this, "Save or discard table changes before opening the item editor.");
        return false;
    }

    private boolean discardEdits() {
        if (saving) return false;
        stopEditing();
        if (!drafts.isEmpty() && JOptionPane.showConfirmDialog(this, "Discard unsaved changes?", "Custom Inventory",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
        drafts.clear();
        status.setText("Edit cells, then press Enter or Save Changes.");
        render();
        return true;
    }
    private void installHeaderSorting(){
        JTableHeader header=table.getTableHeader();
        TableCellRenderer renderer=header.getDefaultRenderer();
        header.setDefaultRenderer((source,value,selected,focused,row,column)->{
            Component component=renderer.getTableCellRendererComponent(source,value,selected,focused,row,column);
            if(component instanceof JLabel label){
                boolean sorted=column>=0&&table.convertColumnIndexToModel(column)==sortColumn;
                label.setIcon(sorted?UIManager.getIcon(ascending?"Table.ascendingSortIcon":"Table.descendingSortIcon"):null);
            }
            return component;
        });
        header.setToolTipText("Click a heading to sort. Click again to reverse the order.");
        header.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent event){
                int viewColumn=header.columnAtPoint(event.getPoint());
                if(!SwingUtilities.isLeftMouseButton(event)||viewColumn<0||saving)return;
                int column=table.convertColumnIndexToModel(viewColumn);
                ascending=sortColumn!=column||!ascending;
                sortColumn=column;
                if(column==11){
                    dateSort.setSelectedIndex(ascending?2:1);
                }else{
                    dateSort.setSelectedIndex(0);
                    render();
                }
            }
        });
    }
    void setState(State loaded){
        table.getColumnModel().getColumn(table.convertColumnIndexToView(15)).setCellEditor(new DefaultCellEditor(new JComboBox<>(services.CustomItemCategories.choices(loaded.customCategories()))));
        if(!drafts.isEmpty()||saving)return;
        state=loaded;originals.clear();
        for(Item item:state.items()){originals.put("I:"+item.itemId(),JSON.toJsonTree(item).getAsJsonObject());
            for(Variant variant:item.variants()){JsonObject row=JSON.toJsonTree(variant).getAsJsonObject();
                row.addProperty("pricingType",item.pricingType());row.addProperty("productType",item.productType());

                originals.put("V:"+variant.variantId(),row);}}
        rendering = true;
        try {
            updateFilter(itemTypes, loaded.items().stream().map(Item::itemType));
            updateFilter(categories, loaded.items().stream().map(item -> categoryLabel(originals.get("I:" + item.itemId()))));
            updateFilter(brands, loaded.items().stream().flatMap(item -> Stream.concat(
                    Stream.of(item.brand()), item.variants().stream().map(Variant::brand))));
        } finally {
            rendering = false;
        }
        render();
    }

    private String categoryLabel(JsonObject item) {
        return Objects.toString(CustomItemCategories.displayChoice(item, state.customCategories()), "");
    }

    private static void updateFilter(JComboBox<String> filter, Stream<String> values) {
        String selected = (String) filter.getSelectedItem();
        var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        values.filter(Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty()).forEach(names::add);
        String defaultChoice = filter.getItemAt(0);
        var choices = new DefaultComboBoxModel<String>();
        choices.addElement(defaultChoice);
        for (String name : names) choices.addElement(name);
        filter.setModel(choices);
        if (selected != null && names.contains(selected)) filter.setSelectedItem(names.ceiling(selected));
    }

    private static boolean selectedFilterMatches(JComboBox<String> filter, String value) {
        return filter.getSelectedIndex() <= 0 || filter.getSelectedItem().toString().equalsIgnoreCase(value.strip());
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
        table.getTableHeader().repaint();
        if(state==null||saving)return;stopEditing();rendering=true;
        int selectedRow=table.getSelectedRow();
        String selectedKey=selectedRow<0?null:rowKeys.get(selectedRow);
        try{model.setRowCount(0);rowKeys.clear();String query=search.getText().trim().toLowerCase(Locale.ROOT);
            for(Item item:sortedItems()){
                if(item.archived()!=archivedOnly.isSelected())continue;
                String itemKey="I:"+item.itemId();JsonObject itemData=drafts.getOrDefault(itemKey,originals.get(itemKey));
                if (!selectedFilterMatches(itemTypes, text(itemData, "itemType"))
                        || !selectedFilterMatches(categories, categoryLabel(itemData))) continue;
                if(businessCardsOnly.isSelected()&&!flag(itemData,"isBusinessCards"))continue;
                if(largeFormatPrintingOnly.isSelected()&&!flag(itemData,"isLargeFormatPrinting"))continue;
                if(laserOnly.isSelected()&&!flag(itemData,"isLaser"))continue;
                if(sublimationOnly.isSelected()&&!flag(itemData,"isSublimation"))continue;
                if(shirtsOnly.isSelected()&&(!itemData.has("isShirt")||!itemData.get("isShirt").getAsBoolean()))continue;
                boolean itemTextMatches=matchesSearch(itemData,query);
                String parentBrand = text(itemData, "brand");
                boolean match=itemTextMatches && selectedFilterMatches(brands, parentBrand);
                List<Variant> variants=item.variants().stream().filter(variant -> {
                    String key = "V:" + variant.variantId();
                    JsonObject data = drafts.getOrDefault(key, originals.get(key));
                    String variantBrand = text(data, "brand");
                    return selectedFilterMatches(brands, variantBrand.isBlank() ? parentBrand : variantBrand)
                            && (itemTextMatches || matchesSearch(data, query, itemData));
                }).toList();
                if(!match&&variants.isEmpty())continue;
                if(item.hasVariants()){
                    boolean showVariants = expanded.contains(item.itemId()) || !query.isBlank() || brands.getSelectedIndex() > 0;
                    rowKeys.add("G:"+item.itemId());model.addRow(new Object[]{(showVariants?"▾ ":"▸ ")+text(itemData,"name"),variantSummary(item,"size"),variantSummary(item,"color"),variantSummary(item,"brand"),"",null,item.quantity(),item.reorderLevel(),item.active(),item.pricingType(),item.imageUrl(),displayDate(item.createdAt()),itemData.has("sellInPos")&&itemData.get("sellInPos").getAsBoolean(),text(itemData,"description"),templateSummary(item,itemData),services.CustomItemCategories.displayChoice(itemData,state.customCategories()),item.shirtReadiness(),item.isShirt()?ShirtDesignerService.styleLabel(item.shirtDesignerConfig()):""});
                    if(showVariants)for(Variant v:variants)add("V:"+v.variantId(),true);
                }else add(itemKey,false);
            }
            refreshShirtCells();
            for(int row=0;row<rowKeys.size();row++){
                String key=rowKeys.get(row);JsonObject parent=parentRow(row);
                JsonObject value=key.startsWith("G:")?parent:drafts.getOrDefault(key,originals.get(key));
                model.setValueAt(text(parent,"itemType"),row,24);
                model.setValueAt(templateDimension(value,parent,"templateWidth"),row,22);
                model.setValueAt(templateDimension(value,parent,"templateHeight"),row,23);
            }
            if(sortColumn>=0&&sortColumn!=11)sortItemGroups();
            int selection=rowKeys.indexOf(selectedKey);
            if(selection>=0)table.setRowSelectionInterval(selection,selection);
            long items = rowKeys.stream().filter(key -> !key.startsWith("V:")).count();
            long variants = rowKeys.size() - items;
            count.setText(items + (items == 1 ? " item" : " items") + "  ·  "
                    + variants + (variants == 1 ? " visible variant" : " visible variants"));
        }finally{rendering=false;}
    }
    private void sortItemGroups(){
        var rows=model.getDataVector();
        List<ItemRows> groups=new ArrayList<>();
        for(int start=0;start<rowKeys.size();){
            int end=start+1;
            while(end<rowKeys.size()&&rowKeys.get(end).startsWith("V:"))end++;
            String value=sortColumn==0?text(parentRow(start),"name"):Objects.toString(model.getValueAt(start,sortColumn),"");
            groups.add(new ItemRows(start,end,value));
            start=end;
        }
        Comparator<ItemRows> order=Comparator.comparing(ItemRows::value,String.CASE_INSENSITIVE_ORDER);
        if(sortColumn>=5&&sortColumn<=7){
            Comparator<BigDecimal> numbers=ascending?Comparator.naturalOrder():Comparator.reverseOrder();
            order=Comparator.comparing(group->sortNumber(group.value()),Comparator.nullsLast(numbers));
        }else if(!ascending){
            order=order.reversed();
        }
        groups.sort(order);
        var sortedRows=new ArrayList<>(rows);
        var sortedKeys=new ArrayList<>(rowKeys);
        int destination=0;
        for(ItemRows group:groups){
            for(int row=group.start();row<group.end();row++){
                rows.set(destination,sortedRows.get(row));
                rowKeys.set(destination,sortedKeys.get(row));
                destination++;
            }
        }
        model.fireTableDataChanged();
    }
    private record ItemRows(int start,int end,String value){}
    private static BigDecimal sortNumber(String value){
        try{return new BigDecimal(value.strip());}catch(NumberFormatException ignored){return null;}
    }
    private static boolean matchesSearch(JsonObject row,String query,JsonObject... parents){
        StringBuilder value=new StringBuilder();
        for(String field:List.of("name","description","size","color","brand","itemType","sku","barcode")){
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
        model.addRow(new Object[]{(variant?"    ":"")+text(row,"name"),text(row,"size"),text(row,"color"),text(row,"brand"),text(row,"barcode"),text(row,"fixedPrice"),text(row,"quantity"),text(row,"reorderLevel"),row.get("active").getAsBoolean(),text(row,"pricingType"),text(row,"imageUrl"),displayDate(text(row,"createdAt")),parent.has("sellInPos")&&parent.get("sellInPos").getAsBoolean(),text(parent,"description"),templateSize(row,parent),services.CustomItemCategories.displayChoice(parent,state.customCategories()),text(parent,"shirtReadiness"),parent.get("isShirt").getAsBoolean()?ShirtDesignerService.styleLabel(parent.has("shirtDesignerConfig")&&!parent.get("shirtDesignerConfig").isJsonNull()?parent.getAsJsonObject("shirtDesignerConfig"):null):""});
    }
    private static boolean flag(JsonObject value,String key){return value!=null&&value.has(key)&&!value.get(key).isJsonNull()&&value.get(key).getAsBoolean();}
    private JsonObject parentRow(int row){String key=rowKeys.get(row);if(key.startsWith("G:"))key="I:"+key.substring(2);else if(key.startsWith("V:"))key="I:"+originals.get(key).get("itemId").getAsLong();return drafts.getOrDefault(key,originals.get(key));}
    private static JsonObject shirtConfig(JsonObject item){return item.has("shirtDesignerConfig")&&item.get("shirtDesignerConfig").isJsonObject()&&item.getAsJsonObject("shirtDesignerConfig").size()>0?item.getAsJsonObject("shirtDesignerConfig").deepCopy():ShirtDesignerService.defaults();}
    private void refreshShirtCells(){for(int row=0;row<rowKeys.size();row++){JsonObject item=parentRow(row),config=shirtConfig(item);model.setValueAt(services.CustomItemCategories.displayChoice(item,state.customCategories()),row,15);boolean shirt=flag(item,"isShirt"),polo="COLLARED".equals(text(config,"shirtType"));model.setValueAt(shirt&&flag(config,"websiteEnabled"),row,18);model.setValueAt(shirt?("DRY_FIT".equals(text(config,"fabric"))?"Dry-fit":"Normal"):"",row,19);model.setValueAt(shirt?(polo?"Polo":"T-shirt"):"",row,20);model.setValueAt(shirt&&!polo?("V".equals(text(config,"neckline"))?"V-neck":"Round neck"):"",row,21);model.setValueAt(shirt?ShirtDesignerService.styleLabel(config):"",row,17);}}
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
        setColumnVisible(11,visible);
    }
    private void setTemplateVisible(boolean visible){
        setColumnVisible(14,visible);
    }
    private void setColumnVisible(int modelColumn,boolean visible){
        TableColumn column=table.getColumnModel().getColumn(table.convertColumnIndexToView(modelColumn));
        if(visible)column.setMaxWidth(Integer.MAX_VALUE);
        column.setMinWidth(visible?120:0);
        if(!visible)column.setMaxWidth(0);
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
    private static String templateDimension(JsonObject row,JsonObject parent,String field){
        String area=text(row,"templateUsesOrderArea");
        if(area.isBlank())area=text(parent,"templateUsesOrderArea");
        if("true".equals(area))return "Order measurements";
        JsonObject dimensions=text(row,"templateWidth").isBlank()&&text(row,"templateHeight").isBlank()?parent:row;
        String value=text(dimensions,field);
        return value.isBlank()?"":new BigDecimal(value).stripTrailingZeros().toPlainString()+" "+text(dimensions,"templateUnit");
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
