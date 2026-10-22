package gy.deckers.studio;

import publishing.*;
import ui.design.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.BiConsumer;

/** Scrollable page navigation with bounded, background-rendered thumbnails. */
final class PublicationPages extends JPanel {
    private record Entry(int page,String subPage){}
    private final java.util.List<Entry> entries=new ArrayList<>();
    private BiConsumer<Integer,String> risoSelected=(page,sub)->{};
    void setRisoSelection(BiConsumer<Integer,String> listener){risoSelected=listener;}
    final JList<String> list=new JList<>(new DefaultListModel<>());
    private final JScrollPane scroll=new JScrollPane(list);
    private final Map<Integer,BufferedImage> images=new LinkedHashMap<>(){
        protected boolean removeEldestEntry(Map.Entry<Integer,BufferedImage> entry){return size()>64;}
    };
    private Publication document;
    private PublicationPages subPages;
    private int activePage=-1;
    private int generation;
    private boolean updating;
    private SwingWorker<Map<Integer,BufferedImage>,Void> worker;
    private final javax.swing.Timer refresh=new javax.swing.Timer(180,e->renderVisible());
    PublicationPages(IntConsumer selected){this(selected,true);}
    private PublicationPages(IntConsumer selected,boolean main){
        super(new BorderLayout(4,4));setOpaque(false);setMinimumSize(new Dimension(110,100));setPreferredSize(new Dimension(180,400));
        JPanel top=DeckersSwing.panel();top.setLayout(new BorderLayout());top.add(new JLabel(main?"Pages":"Riso sub-pages"),BorderLayout.NORTH);
        JSlider size=new JSlider(80,240,130);size.setToolTipText("Page preview size");top.add(size);add(top,BorderLayout.NORTH);add(scroll);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(166);list.setBackground(DeckersPalette.background());
        list.setCellRenderer((items,value,index,isSelected,focus)->{
            JLabel label=new JLabel(value,SwingConstants.CENTER);label.setHorizontalTextPosition(SwingConstants.CENTER);label.setVerticalTextPosition(SwingConstants.BOTTOM);label.setOpaque(true);label.setBackground(isSelected?DeckersPalette.PURPLE:DeckersPalette.background());label.setForeground(isSelected?Color.WHITE:Color.BLACK);
            BufferedImage image=images.get(index);if(image!=null){int bound=Math.max(50,Math.min(size.getValue(),list.getWidth()-20));double factor=Math.min(bound/(double)image.getWidth(),size.getValue()/(double)image.getHeight());label.setIcon(new ImageIcon(image.getScaledInstance(Math.max(1,(int)(image.getWidth()*factor)),Math.max(1,(int)(image.getHeight()*factor)),Image.SCALE_SMOOTH)));}
            label.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));return label;
        });
        refresh.setRepeats(false);scroll.getViewport().addChangeListener(e->refresh.restart());
        size.addChangeListener(e->{list.setFixedCellHeight(size.getValue()+36);list.repaint();refresh.restart();});
        list.addListSelectionListener(e->{if(!updating&&!e.getValueIsAdjusting()&&list.getSelectedIndex()>=0){var entry=entries.get(list.getSelectedIndex());if(entry.subPage()==null){showSubPages(entry.page());selected.accept(entry.page());}else risoSelected.accept(entry.page(),entry.subPage());}});
        if(main){
            JPanel mainColumn=new JPanel(new BorderLayout(4,4));mainColumn.setOpaque(false);remove(top);remove(scroll);mainColumn.add(top,BorderLayout.NORTH);mainColumn.add(scroll);
            add(mainColumn);
            subPages=new PublicationPages(i->{},false);subPages.risoSelected=(page,id)->risoSelected.accept(page,id);
            subPages.setPreferredSize(new Dimension(180,400));subPages.setVisible(false);add(subPages,BorderLayout.EAST);
        }
    }
    void update(Publication source,int selected){
        document=source.copy();generation++;images.clear();if(worker!=null)worker.cancel(true);
        updating=true;try{DefaultListModel<String> model=(DefaultListModel<String>)list.getModel();model.clear();entries.clear();for(int i=0;i<source.pages.size();i++){var page=source.pages.get(i);entries.add(new Entry(i,null));model.addElement((i+1)+" · "+page.name);}list.setSelectedIndex(mainRow(selected));}finally{updating=false;}
        activePage=-1;showSubPages(selected);
        setVisible(entries.size()>1||(subPages!=null&&subPages.isVisible()));list.repaint();refresh.restart();
    }
    private void showSubPages(int page){
        if(subPages==null||document==null||page<0||page>=document.pages.size()||activePage==page)return;
        activePage=page;var child=subPages;
        boolean samePage=!child.entries.isEmpty()&&child.entries.get(0).page()==page;
        Point position=samePage?child.scroll.getViewport().getViewPosition():new Point();
        int selection=samePage?child.list.getSelectedIndex():-1;
        child.document=document;child.generation++;child.images.clear();if(child.worker!=null)child.worker.cancel(true);
        child.updating=true;try{var model=(DefaultListModel<String>)child.list.getModel();model.clear();child.entries.clear();
            for(var sub:document.pages.get(page).risoPages){child.entries.add(new Entry(page,sub.id));model.addElement(sub.name);}
            if(!child.entries.isEmpty()){int count=LinkedRiso.visible(document,page,LinkedRiso.UNASSIGNED).size();if(count>0){child.entries.add(new Entry(page,LinkedRiso.UNASSIGNED));model.addElement("Unassigned ("+count+")");}}
            if(selection>=0&&selection<model.size())child.list.setSelectedIndex(selection);
        }finally{child.updating=false;}
        child.scroll.getViewport().setViewPosition(position);child.setVisible(!child.entries.isEmpty());
        setPreferredSize(new Dimension(child.isVisible()?364:180,400));revalidate();child.refresh.restart();
    }
    JList<String> subPageList(){return subPages.list;}
    private int mainRow(int page){for(int i=0;i<entries.size();i++)if(entries.get(i).page()==page&&entries.get(i).subPage()==null)return i;return -1;}
    void select(int index){updating=true;try{int row=mainRow(index);list.setSelectedIndex(row);if(row>=0)list.ensureIndexIsVisible(row);}finally{updating=false;}showSubPages(index);if(subPages!=null)subPages.list.clearSelection();refresh.restart();}
    private void renderVisible(){
        if(document==null||!isShowing()||(worker!=null&&!worker.isDone()))return;
        int first=Math.max(0,list.getFirstVisibleIndex()),last=list.getLastVisibleIndex();if(last<first)last=Math.min(first+3,entries.size()-1);
        java.util.List<Integer> pending=new ArrayList<>();for(int i=first;i<=last;i++)if(!images.containsKey(i))pending.add(i);if(pending.isEmpty())return;
        Publication snapshot=document;int revision=generation;var rows=java.util.List.copyOf(entries);
        worker=new SwingWorker<>(){
            protected Map<Integer,BufferedImage> doInBackground(){Map<Integer,BufferedImage> result=new LinkedHashMap<>();for(int index:pending){if(isCancelled())break;try{var row=rows.get(index);BufferedImage preview=row.subPage()==null?PublicationTemplateLibrary.preview(snapshot,row.page()):LinkedRiso.preview(snapshot,row.page(),row.subPage(),240);double scale=Math.min(240./preview.getWidth(),240./preview.getHeight());BufferedImage thumbnail=new BufferedImage(Math.max(1,(int)(preview.getWidth()*scale)),Math.max(1,(int)(preview.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);Graphics2D g=thumbnail.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(preview,0,0,thumbnail.getWidth(),thumbnail.getHeight(),null);}finally{g.dispose();preview.flush();}result.put(index,thumbnail);}catch(Exception ignored){}}return result;}
            protected void done(){if(revision!=generation)return;try{images.putAll(get());list.repaint();}catch(Exception ignored){} }
        };worker.execute();
    }
}
