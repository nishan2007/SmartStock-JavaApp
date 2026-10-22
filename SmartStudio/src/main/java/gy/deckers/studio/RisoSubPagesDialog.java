package gy.deckers.studio;

import publishing.*;
import ui.design.*;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Transactional editor: every view edits/references the same source objects. */
final class RisoSubPagesDialog extends JDialog {
    record Result(Publication document,int page,boolean saveRequested){Result(Publication document,int page){this(document,page,false);}}
    record View(String id,String name){public String toString(){return name;}}
    private Publication document;
    private int page;
    private String view=LinkedRiso.MAIN;
    private Result result;
    private final JComboBox<String> mainPages=new JComboBox<>();
    private final DefaultListModel<View> views=new DefaultListModel<>();
    private final JList<View> list=new JList<>(views);
    private final JSlider thumbnailSize=new JSlider(80,220,130);
    private final JComboBox<View> destination=new JComboBox<>();
    private final TemplateWorkspace canvas=new TemplateWorkspace();
    private List<Publication.Element> visible=List.of();
    private final JTable objects;
    private final JLabel status=new JLabel("Edit the main design; sub-pages remain linked. Apply to template, then save normally.");
    private final JLabel help=new JLabel(" ");
    private final Map<String,BufferedImage> thumbnails=new HashMap<>();
    private BufferedImage canvasImage;
    private boolean updating,syncing,busy;
    private int revision;
    private SwingWorker<Map<String,BufferedImage>,Void> previewWorker;
    private final javax.swing.Timer refresh=new javax.swing.Timer(160,e->renderPreviews());
    private final List<JButton> buttons=new ArrayList<>();
    private final Deque<Publication> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private Publication history;
    private final AbstractTableModel model=new AbstractTableModel(){
        public int getRowCount(){return visible.size();}public int getColumnCount(){return 2;}public String getColumnName(int c){return c==0?"Object":"Print on";}
        public Object getValueAt(int row,int col){var e=visible.get(row);return col==0?e.type+" • "+e.text.replace('\n',' ').substring(0,Math.min(40,e.text.replace('\n',' ').length())):name(LinkedRiso.assignment(document,page,e.id));}
    };

    static Result edit(Component parent,Publication source,int page,String subPage){var dialog=new RisoSubPagesDialog(parent,source,page,subPage);dialog.setVisible(true);return dialog.result;}
    private RisoSubPagesDialog(Component parent,Publication source,int page,String subPage){
        super(SwingUtilities.getWindowAncestor(parent),"Linked Riso sub-pages",ModalityType.APPLICATION_MODAL);
        this.document=source.copy();this.page=page;this.view=subPage==null?LinkedRiso.MAIN:subPage;history=document.copy();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setLayout(new BorderLayout(6,6));refresh.setRepeats(false);
        JPanel top=DeckersSwing.panel();top.add(new JLabel("Main page:"));for(int i=0;i<source.pages.size();i++)mainPages.addItem((i+1)+" • "+source.pages.get(i).name);mainPages.setSelectedIndex(page);top.add(mainPages);
        button(top,"Add sub-page",()->{finish();var ids=selectedIds();var sub=LinkedRiso.add(document,this.page);if(document.pages.get(this.page).risoPages.size()==2)sub.color="#008000";if(!ids.isEmpty())LinkedRiso.assign(document,this.page,ids,sub.id);view=sub.id;record();rebuild();});
        button(top,"Rename",()->{finish();var sub=current();String value=JOptionPane.showInputDialog(this,"Sub-page name",sub.name);if(value!=null){value=value.trim();if(value.isEmpty()||value.length()>100)throw new IllegalArgumentException("Enter a name of 1–100 characters.");sub.name=value;record();rebuild();}});
        button(top,"Ink colour",()->{finish();var sub=current();Color color=JColorChooser.showDialog(this,"Sub-page ink colour",Color.decode(sub.color));if(color!=null){sub.color=String.format("#%06x",color.getRGB()&0xffffff);record();rebuild();}});
        button(top,"Remove sub-page",()->{finish();LinkedRiso.remove(document,this.page,current().id);view=LinkedRiso.UNASSIGNED;record();rebuild();});
        button(top,"Undo",()->history(false));button(top,"Redo",()->history(true));var topScroll=new JScrollPane(top,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);topScroll.setPreferredSize(new Dimension(900,58));topScroll.setBorder(null);add(topScroll,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(155);list.setCellRenderer((items,item,index,selected,focus)->{var label=new JLabel(item.name(),SwingConstants.CENTER);label.setOpaque(true);label.setBackground(selected?DeckersPalette.PURPLE:DeckersPalette.background());label.setForeground(selected?Color.WHITE:Color.BLACK);label.setHorizontalTextPosition(SwingConstants.CENTER);label.setVerticalTextPosition(SwingConstants.BOTTOM);var image=thumbnails.get(item.id());if(image!=null){double factor=Math.min(Math.max(50,Math.min(thumbnailSize.getValue(),list.getWidth()-20))/(double)image.getWidth(),thumbnailSize.getValue()/(double)image.getHeight());label.setIcon(new ImageIcon(image.getScaledInstance(Math.max(1,(int)(image.getWidth()*factor)),Math.max(1,(int)(image.getHeight()*factor)),Image.SCALE_SMOOTH)));}return label;});
        JPanel left=DeckersSwing.panel();left.setLayout(new BorderLayout());left.setPreferredSize(new Dimension(200,450));left.add(new JScrollPane(list));thumbnailSize.setToolTipText("Sub-page thumbnail size");thumbnailSize.addChangeListener(e->{list.setFixedCellHeight(thumbnailSize.getValue()+25);list.repaint();});left.add(thumbnailSize,BorderLayout.SOUTH);
        canvas.setPageFocus(true);objects=new JTable(model);objects.setRowHeight(26);objects.getColumnModel().getColumn(0).setPreferredWidth(230);objects.getColumnModel().getColumn(1).setPreferredWidth(150);
        JPanel assign=DeckersSwing.panel();assign.setLayout(new BoxLayout(assign,BoxLayout.Y_AXIS));assign.add(new JLabel("Move selected to:"));assign.add(destination);button(assign,"Move objects",()->{finish();var target=(View)destination.getSelectedItem();if(target==null)throw new IllegalArgumentException("Add a sub-page first.");List<String> ids=selectedIds();if(ids.isEmpty())throw new IllegalArgumentException("Select objects on the canvas or in the list first.");LinkedRiso.assign(document,this.page,ids,target.id());record();rebuild();});
        JPanel objectPanel=DeckersSwing.panel();objectPanel.setLayout(new BorderLayout());objectPanel.add(assign,BorderLayout.NORTH);objectPanel.add(new JScrollPane(objects));objectPanel.add(new JLabel("<html>Ctrl/Shift-click to select several.<br>Drag list rows onto a sub-page.</html>"),BorderLayout.SOUTH);objectPanel.setMinimumSize(new Dimension(260,150));
        JSplitPane content=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(canvas),objectPanel);content.setResizeWeight(.7);content.setDividerLocation(650);objectPanel.setPreferredSize(new Dimension(390,400));
        JPanel center=DeckersSwing.panel();center.setLayout(new BorderLayout());center.add(help,BorderLayout.NORTH);center.add(content);JSplitPane layout=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,center);layout.setDividerLocation(200);layout.setResizeWeight(0);add(layout);
        JPanel bottom=DeckersSwing.panel();bottom.setLayout(new BorderLayout());bottom.add(status,BorderLayout.NORTH);JPanel actions=DeckersSwing.panel();button(actions,"Print sub-pages…",()->output(false));button(actions,"Export sub-pages PDF…",()->output(true));button(actions,"Apply to template",()->{finish();LinkedRiso.prune(document);document.validate();result=new Result(document.copy(),this.page);dispose();});JButton cancel=StudioWorkspaceShell.button("Cancel",this::dispose);actions.add(cancel);bottom.add(actions,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        mainPages.addActionListener(e->{if(!updating){finish();this.page=mainPages.getSelectedIndex();view=LinkedRiso.MAIN;rebuild();}});
        list.addListSelectionListener(e->{if(!updating&&!e.getValueIsAdjusting()&&list.getSelectedValue()!=null){finish();view=list.getSelectedValue().id();configure();}});
        objects.getSelectionModel().addListSelectionListener(e->{if(!syncing&&!e.getValueIsAdjusting())canvas.selectIndices(objects.getSelectedRows());});
        canvas.addPropertyChangeListener("editCompleted",e->{record();queuePreviews();});
        canvas.addPropertyChangeListener("layerDeleted",e->{record();rebuild();});
        installDragAndDrop();
        JPanel saveActions=DeckersSwing.panel();
        Runnable save=()->{finish();LinkedRiso.prune(document);document.validate();result=new Result(document.copy(),this.page,true);dispose();};
        button(saveActions,"Save template (Ctrl+S)",save);
        bottom.add(saveActions,BorderLayout.CENTER);
        getRootPane().getActionMap().put("saveTemplate",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(!busy)try{save.run();}catch(Exception ex){error(ex);}}});
        getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control S"),"saveTemplate");
        if(!GraphicsEnvironment.isHeadless())getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),"saveTemplate");
        for(var key:new String[]{"control Z","control Y","control shift Z"}){getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),key);getRootPane().getActionMap().put(key,new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(!busy)history(!key.equals("control Z"));}});}
        setMinimumSize(new Dimension(900,600));setSize(1250,800);setLocationRelativeTo(parent);rebuild();
    }
    private String name(String id){if(id.equals(LinkedRiso.MAIN))return "Main design";if(id.equals(LinkedRiso.UNASSIGNED))return "Unassigned";if(id.equals(LinkedRiso.EXCLUDED))return "Not printing";return LinkedRiso.subPage(document,page,id).name;}
    private Publication.RisoPage current(){if(view.startsWith("@")||view.isEmpty())throw new IllegalArgumentException("Select a Riso sub-page first.");return LinkedRiso.subPage(document,page,view);}
    private void button(JPanel panel,String label,Runnable action){var button=StudioWorkspaceShell.button(label,()->{try{if(!busy)action.run();}catch(Exception ex){error(ex);}});buttons.add(button);panel.add(button);}
    private void finish(){if(!canvas.finishText(true))throw new IllegalStateException("Finish editing the text first.");}
    private List<String> selectedIds(){return Arrays.stream(canvas.selectedIndices()).filter(i->i>=0&&i<visible.size()).mapToObj(i->visible.get(i).id).toList();}
    private void record(){if(canvas.gestureActive())return;LinkedRiso.prune(document);document.validate();if(!Publication.JSON.toJson(history).equals(Publication.JSON.toJson(document))||!history.assets.equals(document.assets)||!history.assetFiles.equals(document.assetFiles)){undo.addLast(history);while(undo.size()>30)undo.removeFirst();redo.clear();history=document.copy();}}
    private void history(boolean forward){finish();record();var from=forward?redo:undo;var to=forward?undo:redo;if(from.isEmpty())return;to.addLast(history);document=from.removeLast().copy();history=document.copy();rebuild();}
    private void rebuild(){
        LinkedRiso.prune(document);updating=true;try{
            views.clear();views.addElement(new View(LinkedRiso.MAIN,"Main design"));views.addElement(new View(LinkedRiso.UNASSIGNED,"Unassigned ("+LinkedRiso.visible(document,page,LinkedRiso.UNASSIGNED).size()+")"));
            for(var sub:document.pages.get(page).risoPages)views.addElement(new View(sub.id,sub.name+" ("+LinkedRiso.visible(document,page,sub.id).size()+")"));
            views.addElement(new View(LinkedRiso.EXCLUDED,"Not printing ("+LinkedRiso.visible(document,page,LinkedRiso.EXCLUDED).size()+")"));
            if(java.util.stream.IntStream.range(0,views.size()).noneMatch(i->views.get(i).id().equals(view)))view=LinkedRiso.MAIN;
            destination.removeAllItems();for(int i=0;i<views.size();i++){View item=views.get(i);if(!item.id().equals(LinkedRiso.MAIN))destination.addItem(item);if(item.id().equals(view))list.setSelectedIndex(i);}
            if(!document.pages.get(page).risoPages.isEmpty())destination.setSelectedItem(view.isEmpty()||view.startsWith("@")?views.get(2):list.getSelectedValue());
            mainPages.setSelectedIndex(page);
        }finally{updating=false;}configure();
    }
    private void configure(){
        finish();visible=new ArrayList<>(LinkedRiso.visible(document,page,view));syncing=true;try{model.fireTableDataChanged();}finally{syncing=false;}
        canvas.configure(new TemplateWorkspace.Design(){
            public double width(){return document.width;}public double height(){return document.height;}public int size(){return visible.size();}public Rectangle2D bounds(int i){var e=visible.get(i);return new Rectangle2D.Double(e.x,e.y,e.width,e.height);}public boolean locked(int i){return visible.get(i).locked;}public boolean canResize(){return true;}
            public void move(int i,double x,double y){var e=visible.get(i);e.x=x;e.y=y;}public void resize(int i,Rectangle2D b){var e=visible.get(i);e.x=b.getX();e.y=b.getY();e.width=b.getWidth();e.height=b.getHeight();}
            public void remove(int i){var e=visible.get(i);if(view.equals(LinkedRiso.MAIN))LinkedRiso.deleteSource(document,e);else if(!view.equals(LinkedRiso.UNASSIGNED))LinkedRiso.assign(document,page,List.of(e.id),LinkedRiso.UNASSIGNED);}
            public String text(int i){var e=visible.get(i);return e.type.equals("TEXT")?e.text:null;}public boolean setText(int i,String value){var e=visible.get(i);String old=e.text;e.text=value;try{document.validate();return true;}catch(Exception ex){e.text=old;return false;}}
            public void paint(Graphics2D g,double scale){if(canvasImage!=null)g.drawImage(canvasImage,0,0,(int)Math.round(document.width*scale),(int)Math.round(document.height*scale),null);}
        },row->{syncing=true;try{objects.clearSelection();for(int i:canvas.selectedIndices())if(i<objects.getRowCount())objects.addRowSelectionInterval(i,i);}finally{syncing=false;}},()->{LinkedRiso.prune(document);model.fireTableRowsUpdated(0,Math.max(0,visible.size()-1));queuePreviews();});
        help.setText(view.equals(LinkedRiso.MAIN)?"Main design • Move/edit objects here; every linked sub-page follows. Delete removes the original object.":"Linked view • Moving/editing an object also updates its original. Delete removes it from this sub-page.");canvasImage=null;queuePreviews();canvas.repaint();
    }
    private void queuePreviews(){revision++;refresh.restart();}
    private void renderPreviews(){
        if(previewWorker!=null)previewWorker.cancel(true);var snapshot=document.copy();int selectedPage=page,stamp=revision;String active=view;List<View> rows=Collections.list(views.elements());
        previewWorker=new SwingWorker<>(){
            protected Map<String,BufferedImage> doInBackground(){Map<String,BufferedImage> result=new HashMap<>();result.put("@canvas",LinkedRiso.preview(snapshot,selectedPage,active,900));for(var item:rows){if(isCancelled())break;result.put(item.id(),LinkedRiso.preview(snapshot,selectedPage,item.id(),220));}return result;}
            protected void done(){if(stamp!=revision||!isDisplayable())return;try{var result=get();canvasImage=result.remove("@canvas");thumbnails.clear();thumbnails.putAll(result);canvas.repaint();list.repaint();}catch(java.util.concurrent.CancellationException ignored){}catch(Exception ex){status.setText("Could not preview sub-pages: "+ex.getMessage());}}
        };previewWorker.execute();
    }
    private void installDragAndDrop(){
        objects.setTransferHandler(new TransferHandler(){protected Transferable createTransferable(JComponent c){return new StringSelection(String.join("\n",selectedIds()));}public int getSourceActions(JComponent c){return MOVE;}});
        if(!GraphicsEnvironment.isHeadless())objects.setDragEnabled(true);list.setDropMode(DropMode.ON);list.setTransferHandler(new TransferHandler(){
            public boolean canImport(TransferSupport support){return !busy&&support.isDrop()&&support.isDataFlavorSupported(DataFlavor.stringFlavor)&&((JList.DropLocation)support.getDropLocation()).getIndex()>=0;}
            public boolean importData(TransferSupport support){if(!canImport(support))return false;try{View target=views.get(((JList.DropLocation)support.getDropLocation()).getIndex());if(target.id().equals(LinkedRiso.MAIN))return false;String value=(String)support.getTransferable().getTransferData(DataFlavor.stringFlavor);if(value.length()>2100000)return false;finish();LinkedRiso.assign(document,page,Arrays.asList(value.split("\n")),target.id());record();rebuild();return true;}catch(Exception ex){error(ex);return false;}}
        });
    }
    private List<String> chooseOutput(){
        JPanel panel=new JPanel(new GridLayout(0,1));List<JCheckBox> checks=new ArrayList<>();for(var sub:document.pages.get(page).risoPages){JCheckBox check=new JCheckBox(sub.name,view.equals(LinkedRiso.MAIN)||view.startsWith("@")||view.equals(sub.id));checks.add(check);panel.add(check);}
        if(checks.isEmpty())throw new IllegalArgumentException("Add a Riso sub-page and assign objects first.");
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(panel),"Choose Riso sub-pages • single-sided",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        List<String> result=new ArrayList<>();for(int i=0;i<checks.size();i++)if(checks.get(i).isSelected())result.add(document.pages.get(page).risoPages.get(i).id);return result;
    }
    private void output(boolean pdf){try{
        finish();List<String> chosen=chooseOutput();if(chosen==null)return;var job=LinkedRisoOutput.job(document,page,chosen);
        if(pdf){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("Riso sub-pages.pdf"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path path=chooser.getSelectedFile().toPath();if(!path.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))path=Path.of(path+".pdf");if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace "+path.getFileName()+"?","PDF",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;Path target=path;work(()->LinkedRisoOutput.pdf(job,target));}
        else if(RisoPrintPreview.confirm(this,job)){var printer=LinkedRisoOutput.printer(this,job);if(printer!=null)work(()->{printer.print();return "Print job sent • verify actual size and alignment on paper.";});}
    }catch(Exception ex){error(ex);}}
    private void work(java.util.concurrent.Callable<?> task){busy=true;buttons.forEach(b->b.setEnabled(false));status.setText("Preparing linked Riso output…");new SwingWorker<Object,Void>(){protected Object doInBackground()throws Exception{return task.call();}protected void done(){busy=false;buttons.forEach(b->b.setEnabled(true));if(!isDisplayable())return;try{status.setText("Completed: "+get()+" • Print at actual size / 100%.");}catch(Exception ex){error(ex.getCause()==null?ex:ex.getCause());}}}.execute();}
    private void error(Throwable ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Riso sub-pages",JOptionPane.ERROR_MESSAGE);}
    @Override public void dispose(){refresh.stop();revision++;if(previewWorker!=null)previewWorker.cancel(true);super.dispose();}
}
