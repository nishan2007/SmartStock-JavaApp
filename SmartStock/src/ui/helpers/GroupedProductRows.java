package ui.helpers;

import services.LanApiClient;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

/** A view projection: negative IDs are non-sellable headers, never product records. */
public final class GroupedProductRows {
    private final JTable table;
    private final DefaultTableModel model;
    private final int idColumn,nameColumn,priceColumn,quantityColumn;
    private List<Object[]> source=List.of();
    private List<LanApiClient.ProductGroup> groups=List.of();
    private final Map<Integer,LanApiClient.ProductGroup> headers=new HashMap<>();
    private final Map<Integer,LanApiClient.ProductGroup> membership=new HashMap<>();
    private final Set<String> expanded=new HashSet<>();
    private int sortColumn=-1;private boolean descending;

    public GroupedProductRows(JTable table,int idColumn,int nameColumn,int priceColumn,int quantityColumn) {
        this.table=table;this.model=(DefaultTableModel)table.getModel();this.idColumn=idColumn;this.nameColumn=nameColumn;this.priceColumn=priceColumn;this.quantityColumn=quantityColumn;
        table.setRowSorter(null);
        var nameTableColumn=table.getColumnModel().getColumn(table.convertColumnIndexToView(nameColumn));
        var original=nameTableColumn.getCellRenderer()==null?table.getDefaultRenderer(Object.class):nameTableColumn.getCellRenderer();
        nameTableColumn.setCellRenderer((t,value,selected,focus,row,column)->{
            boolean parent=isParent(row);Object text=parent?value:(membership.containsKey(id(row))?"    ":"")+displayName(id(row),String.valueOf(value));
            var component=original.getTableCellRendererComponent(t,text,selected,focus,row,column);
            component.setFont(parent?t.getFont().deriveFont(java.awt.Font.BOLD):t.getFont());return component;
        });
        table.getColumnModel().getColumn(table.convertColumnIndexToView(idColumn)).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer(){
            @Override protected void setValue(Object value){super.setValue(value instanceof Number n&&n.intValue()<0?"":value);}
        });
        table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){int row=table.rowAtPoint(e.getPoint());if(row>=0&&table.columnAtPoint(e.getPoint())==table.convertColumnIndexToView(nameColumn)&&isParent(row)&&e.getClickCount()==1)toggle(row);}});
        table.getTableHeader().addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){int column=table.columnAtPoint(e.getPoint());if(column<0)return;column=table.convertColumnIndexToModel(column);descending=column==sortColumn&&!descending;sortColumn=column;render();}});
        bind(KeyEvent.VK_RIGHT,"variants-expand",true);bind(KeyEvent.VK_LEFT,"variants-collapse",false);
    }
    private void bind(int key,String action,boolean open){table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key,0),action);table.getActionMap().put(action,new AbstractAction(){public void actionPerformed(ActionEvent e){int row=table.getSelectedRow();var group=groupAt(row);if(group!=null){if(open)expanded.add(group.groupId());else expanded.remove(group.groupId());render();}}});}
    public boolean isParent(int viewRow){return viewRow>=0&&viewRow<table.getRowCount()&&id(viewRow)<0;}
    private int id(int row){Object value=model.getValueAt(table.convertRowIndexToModel(row),idColumn);return value instanceof Number n?n.intValue():0;}
    public LanApiClient.ProductGroup groupAt(int row){if(row<0||row>=table.getRowCount())return null;int id=id(row);return id<0?headers.get(id):membership.get(id);}
    public boolean expandSelected(){int row=table.getSelectedRow();if(!isParent(row))return false;var group=groupAt(row);expanded.add(group.groupId());render();return true;}
    private void toggle(int row){var group=groupAt(row);if(!expanded.remove(group.groupId()))expanded.add(group.groupId());render();}
    public String displayName(int productId,String name) {
        var group=membership.get(productId);if(group==null)return name;
        var member=group.members().stream().filter(m->m.productId()==productId).findFirst().orElse(null);
        if(member==null)return name;
        String options=String.join(" · ",group.optionNames().stream().map(key->member.options().getOrDefault(key,"")).toList());
        return name.endsWith(options)?name:name+" — "+options;
    }
    public List<Integer> selectedGroupMembers(){var group=groupAt(table.getSelectedRow());return group==null?List.of():group.members().stream().map(LanApiClient.VariantMember::productId).toList();}
    public List<TableImageHoverPreview.Picture> pictures(int row){
        var group=groupAt(row);if(group==null)return List.of();
        if(isParent(row))return group.members().stream().filter(LanApiClient.VariantMember::active).map(m->new TableImageHoverPreview.Picture(m.imageUrl(),String.join(" · ",group.optionNames().stream().map(key->m.options().getOrDefault(key,"")).toList()))).toList();
        int product=id(row);return group.members().stream().filter(m->m.productId()==product).map(m->new TableImageHoverPreview.Picture(m.imageUrl(),String.join(" · ",group.optionNames().stream().map(key->m.options().getOrDefault(key,"")).toList()))).toList();
    }
    public void capture(List<LanApiClient.ProductGroup> groups,String query){
        this.groups=groups;List<Object[]> rows=new ArrayList<>();for(int r=0;r<model.getRowCount();r++){Object[] row=new Object[model.getColumnCount()];for(int c=0;c<row.length;c++)row[c]=model.getValueAt(r,c);if(((Number)row[idColumn]).intValue()>0)rows.add(row);}source=rows;
        membership.clear();for(var group:groups)for(var member:group.members())membership.put(member.productId(),group);
        if(query!=null&&!query.isBlank())for(Object[] row:source){var group=membership.get(((Number)row[idColumn]).intValue());if(group!=null)expanded.add(group.groupId());}
        render();
    }
    private void render(){
        int selectedId=table.getSelectedRow()<0?0:id(table.getSelectedRow());
        var selectedGroup=groupAt(table.getSelectedRow());
        // Retain selection checkboxes and inline changes through expansion and sorting.
        Map<Integer,Object[]> visible=new HashMap<>();for(int r=0;r<model.getRowCount();r++){Object value=model.getValueAt(r,idColumn);if(value instanceof Number n&&n.intValue()>0){Object[] row=new Object[model.getColumnCount()];for(int c=0;c<row.length;c++)row[c]=model.getValueAt(r,c);visible.put(n.intValue(),row);}}
        for(int i=0;i<source.size();i++){int id=((Number)source.get(i)[idColumn]).intValue();if(visible.containsKey(id)){Object[] row=visible.get(id);row[nameColumn]=source.get(i)[nameColumn];source.set(i,row);}}
        Map<String,List<Object[]>> grouped=new LinkedHashMap<>();List<List<Object[]>> blocks=new ArrayList<>();headers.clear();int synthetic=-1;
        for(Object[] row:source){var group=membership.get(((Number)row[idColumn]).intValue());if(group==null)blocks.add(new ArrayList<>(Collections.singletonList(row)));else grouped.computeIfAbsent(group.groupId(),key->new ArrayList<>()).add(row);}
        for(var group:groups){var children=grouped.get(group.groupId());if(children==null)continue;Object[] parent=new Object[model.getColumnCount()];Arrays.fill(parent,"");if(idColumn==1)parent[0]=false;parent[idColumn]=synthetic;headers.put(synthetic--,group);
            long active=group.members().stream().filter(LanApiClient.VariantMember::active).count();String count=children.size()==active?children.size()+" options":children.size()+" of "+active+" options shown";
            parent[nameColumn]=(expanded.contains(group.groupId())?"▾ ":"▸ ")+group.name()+" ("+count+")";
            long quantity=0;BigDecimal min=null,max=null;for(var member:group.members()){if(!member.active())continue;quantity+=member.quantityOnHand();BigDecimal price=member.price()==null?BigDecimal.ZERO:member.price();if(min==null||price.compareTo(min)<0)min=price;if(max==null||price.compareTo(max)>0)max=price;}
            if(min==null){min=BigDecimal.ZERO;max=BigDecimal.ZERO;}
            parent[quantityColumn]=quantity;parent[priceColumn]=min.compareTo(max)==0?utils.CurrencyFormatter.format(min):utils.CurrencyFormatter.format(min)+" – "+utils.CurrencyFormatter.format(max);
            List<Object[]> block=new ArrayList<>();block.add(parent);
            if(expanded.contains(group.groupId()))for(Object[] child:children)block.add(child.clone());blocks.add(block);
        }
        if(sortColumn>=0){Comparator<List<Object[]>> comparator=(a,b)->compare(a.get(0)[sortColumn],b.get(0)[sortColumn]);blocks.sort(descending?comparator.reversed():comparator);}
        model.setRowCount(0);for(var block:blocks)for(Object[] row:block)model.addRow(row);
        int restore=-1;for(int r=0;r<model.getRowCount();r++){int current=((Number)model.getValueAt(r,idColumn)).intValue();if(current==selectedId){restore=r;break;}if(current<0&&selectedGroup!=null&&headers.get(current).groupId().equals(selectedGroup.groupId()))restore=r;}
        if(restore>=0)table.setRowSelectionInterval(restore,restore);
    }
    private static BigDecimal money(Object value){if(value instanceof BigDecimal d)return d;try{return new BigDecimal(String.valueOf(value).split(" – ",2)[0].replaceAll("[^0-9.\\-]",""));}catch(Exception e){return BigDecimal.ZERO;}}
    private int compare(Object a,Object b){
        if(sortColumn==idColumn||sortColumn==quantityColumn)return number(a).compareTo(number(b));
        if(sortColumn==priceColumn)return money(a).compareTo(money(b));
        return String.valueOf(a).replaceFirst("^[▾▸] ","").compareToIgnoreCase(String.valueOf(b).replaceFirst("^[▾▸] ",""));
    }
    private static Long number(Object value){try{return Long.valueOf(String.valueOf(value).replaceAll("[^0-9-]",""));}catch(Exception e){return 0L;}}
}
