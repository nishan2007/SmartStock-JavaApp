package gy.deckers.studio;

import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.design.DeckersLogoManager;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.prefs.Preferences;

/** Initial badge workspace: editable reconstruction, portable templates and single-card output. */
final class BadgeCardsPanel extends JPanel {
    private BadgeTemplate template;
    private final JTextField name=new JTextField();
    private final JPanel details=DeckersSwing.panel();
    private final Map<String,JTextField> recordFields=new LinkedHashMap<>();
    private final JLabel message=DeckersSwing.metaLabel("Templates use one library folder. Cross-store server sync is not connected yet.");
    private BadgeTemplateLibrary library=new BadgeTemplateLibrary(savedFolder());
    private final JComboBox<Path> libraryTemplates=new JComboBox<>();
    private boolean updatingLibrary;
    private String currentFingerprint;
    private final TemplateWorkspace preview=new TemplateWorkspace();
    private final LayerModel layers=new LayerModel();
    private BufferedImage photo;
    private final Map<String,BufferedImage> personalPhotos=new LinkedHashMap<>();
    private final Map<String,PhotoFraming> photoFrames=new LinkedHashMap<>();
    private BadgeTemplate backTemplate;
    private final JCheckBox manualDuplex=new JCheckBox("Magicard 300: front + back (manual flip)");
    private final Timer refresh;
    private long generation;
    private boolean dirty;
    private Path currentFile;
    private final JTable table=new JTable(layers);
    private final java.util.Deque<String> undoHistory=new java.util.ArrayDeque<>(),redoHistory=new java.util.ArrayDeque<>();
    private final com.google.gson.Gson historyJson=new com.google.gson.Gson();
    private String historyState,savedState;
    private boolean restoringHistory;
    private TextFormattingToolbar textFormatting;
    private final JButton undoButton=new JButton("Undo (Ctrl+Z)"),redoButton=new JButton("Redo (Ctrl+Y)");
    private SharedBadgeLibrary shared;
    private final BadgeSubmissionWorkflow submissions=new BadgeSubmissionWorkflow(this,()->shared,()->currentFile,()->currentFingerprint,payload->{try{loadPrintRecord(payload);}catch(Exception ex){throw new IllegalStateException(ex);}},message::setText);
    private boolean syncing,sharedSave;
    private volatile boolean sharedActive;
    private volatile long sharedSessionGeneration;
    private String migrationStatus="";
    private final JLabel sharedStatus=DeckersSwing.metaLabel("Local template folder");
    private Timer syncTimer;

    void useSharedLibrary(boolean offline){
        sharedSessionGeneration++;sharedActive=!offline;migrationStatus="";
        SharedBadgeLibrary candidate=new SharedBadgeLibrary();
        if(shared==null||!shared.folder().equals(candidate.folder())){shared=candidate;library=new BadgeTemplateLibrary(shared.folder());currentFile=null;currentFingerprint=null;}
        shared.stores.resetView();shared.online=false;sharedStatus.setText("OFFLINE — cached templates only; changes cannot be saved.");message.setText("Existing badge templates will migrate automatically after connecting to the shared server library.");
        try{refreshLibrary();}catch(Exception ignored){}
        if(syncTimer==null){syncTimer=new Timer(30000,e->syncShared());}
        if(!offline){syncTimer.start();syncShared();}else syncTimer.stop();
    }
    void stopSharedLibrary(){sharedSessionGeneration++;sharedActive=false;if(syncTimer!=null)syncTimer.stop();if(shared!=null)shared.online=false;}
    private void syncShared(){
        if(shared==null||syncing||sharedSave)return;syncing=true;SharedBadgeLibrary active=shared;long session=sharedSessionGeneration;Path legacyFolder=savedFolder();
        sharedActive=true;if(syncTimer!=null)syncTimer.start();
        new SwingWorker<Void,Void>(){
            private String migrationMessage;
            protected Void doInBackground()throws Exception{
                active.refresh();
                if(sharedActive&&session==sharedSessionGeneration){
                    try{var migrated=active.migrateLegacy(legacyFolder,()->sharedActive&&session==sharedSessionGeneration);migrationMessage=migrated.message();if(migrated.imported()>0)active.refresh();}
                    catch(Exception ex){migrationMessage="Existing badge migration pending. Reconnect with permission to save templates; it will retry.";}
                }
                return null;
            }
            protected void done(){syncing=false;if(active!=shared||session!=sharedSessionGeneration){if(sharedActive)syncShared();return;}if(!sharedActive)active.online=false;sharedStatus.setText(active.online?active.status:"OFFLINE — cached templates only; changes cannot be saved.");if(migrationMessage!=null&&!migrationMessage.equals(migrationStatus)){migrationStatus=migrationMessage;message.setText(migrationMessage);}try{refreshLibrary();}catch(Exception ignored){}}}.execute();
    }

    BadgeCardsPanel() {
        super(new BorderLayout(12,12)); setOpaque(false);
        preview.setPageFocus(true);
        JPanel content=DeckersSwing.panel();content.setLayout(new BorderLayout(12,12));content.setPreferredSize(new Dimension(720,640));
        refresh=new Timer(180,e -> renderPreview()); refresh.setRepeats(false);
        JPanel toolbar=DeckersSwing.panel(); toolbar.setLayout(new GridLayout(3,1));
        JPanel libraryTools=DeckersSwing.panel();libraryTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        libraryTools.add(DeckersSwing.metaLabel("Templates"));libraryTemplates.setPreferredSize(new Dimension(260,32));
        libraryTemplates.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){return super.getListCellRendererComponent(list,value instanceof Path p?(shared==null?p.getFileName().toString().replaceFirst("(?i)\\.sstbadge$",""):shared.label(p)):"Choose a saved template",index,selected,focus);}});
        libraryTemplates.addActionListener(e->{if(updatingLibrary)return;Path file=(Path)libraryTemplates.getSelectedItem();if(file==null)return;try{if(discard())loadTemplate(file);else{updatingLibrary=true;libraryTemplates.setSelectedItem(library.contains(currentFile)?currentFile:null);updatingLibrary=false;}}catch(Exception ex){error(ex);}});
        libraryTools.add(libraryTemplates);addButton(libraryTools,"Refresh",()->{refreshLibrary();syncShared();});addButton(libraryTools,"Import template folder…",this::chooseFolder);toolbar.add(libraryTools);
        JPanel fileTools=DeckersSwing.panel();fileTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        JPanel outputTools=DeckersSwing.panel();outputTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        toolbar.add(fileTools);toolbar.add(outputTools);
        addButton(fileTools,"Skeldon Primary",() -> { if(discard()) { template=BadgeTemplate.builtin(); currentFile=null; resetFields(); changedTemplate(); } });
        addButton(fileTools,"New blank",() -> newTemplate());
        addButton(fileTools,"Open / import",this::open);
        addButton(fileTools,"Save template",() -> save(false));
        addButton(outputTools,"Export template",() -> save(true));
        addButton(outputTools,"Export PNG",() -> export(false));
        addButton(outputTools,"Export PDF",() -> export(true));
        addButton(outputTools,"Print (Ctrl+P)",this::printCard);
        manualDuplex.setOpaque(false);manualDuplex.setForeground(DeckersPalette.text());
        JPanel printTools=DeckersSwing.panel();printTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        printTools.add(manualDuplex);addButton(printTools,"Choose back template",this::chooseBackTemplate);
        toolbar.setLayout(new GridLayout(4,1));toolbar.add(printTools);
        content.add(toolbar,BorderLayout.NORTH);

        details.setLayout(new GridLayout(0,2,8,8));
        JScrollPane detailsScroll=new JScrollPane(details);detailsScroll.setPreferredSize(new Dimension(310,220));detailsScroll.setBorder(null);detailsScroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel card=DeckersSwing.panel();card.setLayout(new BorderLayout(8,8));card.add(detailsScroll,BorderLayout.WEST);
        preview.setPreferredSize(new Dimension(700,450));JScrollPane previewScroll=new JScrollPane(preview);previewScroll.setWheelScrollingEnabled(false);card.add(previewScroll);
        JPanel zoomTools=DeckersSwing.panel();zoomTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,0));
        addButton(zoomTools,"Zoom out",()->preview.zoomBy(1/1.15));
        JLabel zoomLabel=DeckersSwing.metaLabel("100%");zoomTools.add(zoomLabel);
        addButton(zoomTools,"Zoom in",()->preview.zoomBy(1.15));
        addButton(zoomTools,"Fit",()->{preview.setZoom(1);previewScroll.getViewport().setViewPosition(new Point());});
        zoomTools.add(DeckersSwing.metaLabel("Ctrl + mouse wheel to zoom"));
        preview.addPropertyChangeListener("zoom",e->zoomLabel.setText(Math.round(preview.zoom()*100)+"%"));
        card.add(zoomTools,BorderLayout.NORTH);
        JPanel historyTools=DeckersSwing.panel();historyTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,0));
        for(JButton button:new JButton[]{undoButton,redoButton}){DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);historyTools.add(button);}
        JPanel previewTools=DeckersSwing.panel();previewTools.setLayout(new GridLayout(2,1,0,4));previewTools.add(zoomTools);previewTools.add(historyTools);card.add(previewTools,BorderLayout.NORTH);
        undoButton.addActionListener(e->historyAction(false));redoButton.addActionListener(e->historyAction(true));
        bindHistory("control Z","undo",false);bindHistory("control Y","redo",true);bindHistory("control shift Z","redoAlternate",true);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control S"),"saveTemplateChanges");
        getActionMap().put("saveTemplateChanges",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent event){try{save(false);}catch(Exception ex){error(ex);}}});
        preview.addPropertyChangeListener("editCompleted",e->recordHistory());
        preview.addPropertyChangeListener("layerDeleted",e->{rebuildFields(true);layers.fireTableDataChanged();});
        preview.setLayerActivation(row->{
            if(!"PHOTO".equals(template.elements.get(row).type))return false;
            try{choosePhoto();}catch(Exception ex){error(ex);}return true;
        });
        preview.setFieldEditor((key,value)->{JTextField field=recordFields.get(key);if(field!=null)field.setText(value);});
        preview.configure(() -> template, this::values, () -> photo, row -> {table.getSelectionModel().setValueIsAdjusting(true);try{table.clearSelection();for(int i:preview.selectedIndices())table.addRowSelectionInterval(i,i);}finally{table.getSelectionModel().setValueIsAdjusting(false);}}, () -> {dirty=true;int row=table.getSelectedRow();if(row>=0)layers.fireTableRowsUpdated(row,row);});
        table.getSelectionModel().addListSelectionListener(e -> {if(!e.getValueIsAdjusting())preview.selectIndices(table.getSelectedRows());});
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"),"deleteLayer");
        table.getActionMap().put("deleteLayer",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(!table.isEditing())preview.deleteSelection();}});
        preview.addComponentListener(new java.awt.event.ComponentAdapter(){public void componentResized(java.awt.event.ComponentEvent e){refresh.restart();}});
        name.setColumns(8);DeckersSwing.styleField(name);
        DeckersSwing.styleTable(table,DeckersPalette.PURPLE);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setRowHeight(28);
        table.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        int[] widths={140,65,170,90,65,65,65,65,80,130,65,90};
        for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane edits=new JScrollPane(table);edits.setPreferredSize(new Dimension(500,200));
        JPanel editor=DeckersSwing.panel();editor.setLayout(new BorderLayout(8,8));editor.add(DeckersSwing.metaLabel("Drag to move • Arrow keys: 0.1 mm; Shift + arrow: 1 mm • double-click a cell to edit"),BorderLayout.NORTH);editor.add(edits);
        JPanel layerTools=DeckersSwing.panel();layerTools.setLayout(new FlowLayout(FlowLayout.LEFT));
        addButton(layerTools,"Add text",() -> {commitCell();BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Text";e.text="Text";e.width=Math.min(30,template.widthMm);e.height=6;template.elements.add(e);rebuildFields(true);layers.fireTableDataChanged();refresh.restart();dirty=true;});
        addButton(layerTools,"Add text field…",()->configureTextBinding(false));
        addButton(layerTools,"Bind selected text…",()->configureTextBinding(true));
        addButton(layerTools,"Add photo field",() -> {commitCell();BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Photo";e.type="PHOTO";e.width=Math.min(18,template.widthMm);e.height=Math.min(24,template.heightMm);template.elements.add(e);layers.fireTableDataChanged();refresh.restart();dirty=true;});
        JComboBox<String> shapes=new JComboBox<>(new String[]{"Rectangle","Ellipse","Triangle","Line"});layerTools.add(shapes);
        addButton(layerTools,"Add shape",() -> addShape((String)shapes.getSelectedItem()));
        addButton(layerTools,"Bring forward",() -> reorderLayer(1));
        addButton(layerTools,"Send backward",() -> reorderLayer(-1));
        addButton(layerTools,"Replace selected image / photo",this::replaceArtwork);
        addButton(layerTools,"Crop / frame selected photo…",this::frameSelectedPhoto);
        addButton(layerTools,"Insert image…",this::insertImageFile);
        addButton(layerTools,"Copy layers",this::copyLayers);
        addButton(layerTools,"Paste",this::pasteLayers);
        StudioLayerClipboard.bind(preview,this::copyLayers,this::pasteLayers);
        StudioLayerClipboard.bind(table,this::copyLayers,this::pasteLayers);
        addButton(layerTools,"Clear selected photo",()->{int row=table.getSelectedRow();if(row<0||!"PHOTO".equals(template.elements.get(row).type))throw new IOException("Select a photo field first.");var element=template.elements.get(row);element.personalPhoto=null;element.photoFraming=null;personalPhotos.remove(element.photoId);photoFrames.remove(element.photoId);preview.repaint();});
        addButton(layerTools,"Remove selected layers",() -> {commitCell();preview.deleteSelection();});
        JScrollPane layerToolsScroll=new JScrollPane(layerTools,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);layerToolsScroll.setBorder(null);layerToolsScroll.setPreferredSize(new Dimension(500,60));editor.add(layerToolsScroll,BorderLayout.SOUTH);
        // The page owns the center. Data and advanced layers are opened on demand.
        card.remove(detailsScroll);
        card.remove(previewTools);
        StudioWorkspaceShell shell=new StudioWorkspaceShell(card);
        JPanel templateActions=DeckersSwing.panel();templateActions.setLayout(new BoxLayout(templateActions,BoxLayout.Y_AXIS));
        StudioWorkspaceShell.stack(libraryTools);StudioWorkspaceShell.stack(fileTools);
        templateActions.add(libraryTools);templateActions.add(fileTools);
        shell.addTools(StudioWorkspaceShell.menu("Template",templateActions));
        shell.addPrimaryAction("New template",this::newTemplate);
        shell.addTools(StudioWorkspaceShell.button("Save",()->{try{save(false);}catch(Exception ex){error(ex);}}));
        StudioWorkspaceShell.stack(outputTools);StudioWorkspaceShell.stack(printTools);
        JPanel outputActions=DeckersSwing.panel();outputActions.setLayout(new BoxLayout(outputActions,BoxLayout.Y_AXIS));outputActions.add(outputTools);outputActions.add(printTools);
        shell.addTools(StudioWorkspaceShell.menu("Output",outputActions));
        shell.addTools(undoButton);shell.addTools(redoButton);
        textFormatting=new TextFormattingToolbar(this::selectedTextStyle,this::applyTextStyle);
        ImageFormattingToolbar imageFormatting=new ImageFormattingToolbar(this::selectedPicture,this::applyPictureSize,this::recolourPicture);imageFormatting.setTransparencyEditor(this::applyPictureTransparency);
        preview.setAspectRatioPolicy(i->Set.of("IMAGE","PHOTO").contains(template.elements.get(i).type)?imageFormatting.locked.isSelected():null);
        CardLayout contextLayout=new CardLayout();JPanel contextTools=new JPanel(contextLayout);contextTools.setOpaque(false);
        contextTools.add(textFormatting,"text");contextTools.add(imageFormatting,"image");shell.addTextTools(contextTools);
        Runnable refreshContext=()->{textFormatting.refresh();imageFormatting.refresh();contextLayout.show(contextTools,selectedPicture()==null?"text":"image");};
        table.getSelectionModel().addListSelectionListener(event->refreshContext.run());table.getModel().addTableModelListener(event->refreshContext.run());
        shell.addSection("Details",detailsScroll);
        shell.addSection("Properties",StudioWorkspaceShell.properties(table));
        StudioWorkspaceShell.stack(layerTools);
        layerToolsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        layerToolsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        layerToolsScroll.setPreferredSize(new Dimension(280,220));
        editor.remove(layerToolsScroll);
        shell.addSection("Insert",layerToolsScroll);
        shell.addSection("Layers",editor);
        JPanel footer=DeckersSwing.panel();footer.setLayout(new BorderLayout());footer.add(message);footer.add(sharedStatus,BorderLayout.NORTH);footer.add(zoomTools,BorderLayout.SOUTH);
        add(shell);add(footer,BorderLayout.SOUTH);
        name.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){recordHistory();}public void removeUpdate(DocumentEvent e){recordHistory();}public void changedUpdate(DocumentEvent e){recordHistory();}});
        try {template=BadgeTemplate.builtin();changedTemplate();}catch(IOException ex){template=new BadgeTemplate();changedTemplate();error(ex);}
        try{refreshLibrary();}catch(IOException ex){message.setText("Template folder unavailable. Choose Template folder to change it.");}
    }

    void resetFields(){submissions.clear();recordFields.values().forEach(f->f.setText(""));photo=null;personalPhotos.clear();photoFrames.clear();if(template!=null)template.elements.forEach(e->{e.personalPhoto=null;e.photoFraming=null;});generation++;preview.repaint();refresh.restart();}
    boolean discard(){
        if(!dirty)return true;
        Object[] choices={"Save template","Discard changes","Cancel"};
        int choice=JOptionPane.showOptionDialog(this,"This badge template has unsaved changes.","Badge Cards",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,choices,choices[0]);
        if(choice==1)return true;
        if(choice==0){try{save(false);return !dirty;}catch(Exception ex){error(ex);}}
        return false;
    }
    private void changedTemplate(){restoringHistory=true;try{name.setText(template.name);name.setCaretPosition(0);rebuildFields(false);layers.fireTableDataChanged();}finally{restoringHistory=false;}undoHistory.clear();redoHistory.clear();historyState=historyJson.toJson(template);savedState=historyState;updateHistoryButtons();dirty=false;refresh.restart();}
    private ImageFormattingToolbar.Picture selectedPicture(){if(table.getSelectedRowCount()!=1)return null;int row=table.getSelectedRow();if(row<0||row>=template.elements.size())return null;var e=template.elements.get(row);return Set.of("IMAGE","PHOTO").contains(e.type)?new ImageFormattingToolbar.Picture(e,e.width,e.height,e.transparency):null;}
    private void recolourPicture(){try{
        commitCell();if(selectedPicture()==null)return;int row=table.getSelectedRow();var element=template.elements.get(row);
        if(!element.type.equals("IMAGE"))throw new IOException("Select a template artwork image to recolour. Personal photo fields keep their original photo colours.");
        var result=ImageRecolourDialog.edit(this,element.artwork());if(result!=null)applyPictureRecolour(result);
    }catch(Exception ex){error(ex);}}
    void applyPictureRecolour(BufferedImage result)throws Exception{
        commitCell();if(selectedPicture()==null)throw new IOException("Select one artwork image.");int row=table.getSelectedRow();var element=template.elements.get(row);if(!element.type.equals("IMAGE"))throw new IOException("Select a template artwork image.");
        String old=element.image;element.image=java.util.Base64.getEncoder().encodeToString(ImageRecolour.png(result));
        try{template.validate();if(historyJson.toJson(template).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>BadgeTemplate.MAX_BYTES)throw new IOException("Recoloured template exceeds the 8 MB limit.");}catch(Exception ex){element.image=old;throw ex;}
        recordHistory();layers.fireTableDataChanged();table.setRowSelectionInterval(row,row);dirty=true;preview.repaint();refresh.restart();
    }
    private void applyPictureTransparency(int value){
        try{commitCell();if(selectedPicture()==null)return;int row=table.getSelectedRow();var element=template.elements.get(row);
            element.transparency=value;recordHistory();dirty=true;preview.repaint();refresh.restart();
        }catch(Exception ex){error(ex);}
    }
    private void applyPictureSize(ImageFormattingToolbar.Size size){
        try{commitCell();if(selectedPicture()==null)return;int row=table.getSelectedRow();var e=template.elements.get(row);double oldWidth=e.width,oldHeight=e.height;
            e.width=size.width();e.height=size.height();try{template.validate();}catch(Exception ex){e.width=oldWidth;e.height=oldHeight;throw ex;}
            recordHistory();layers.fireTableDataChanged();table.setRowSelectionInterval(row,row);dirty=true;preview.repaint();refresh.restart();
        }catch(Exception ex){error(ex);}
    }

    private BadgeTemplate.Element selectedText(){if(table.getSelectedRowCount()!=1)return null;int row=table.getSelectedRow();return row>=0&&row<template.elements.size()&&template.elements.get(row).type.equals("TEXT")?template.elements.get(row):null;}
    private TextFormattingToolbar.Style selectedTextStyle(){var e=selectedText();return e==null?null:new TextFormattingToolbar.Style(e.font,e.fontMm*72/25.4,e.bold,e.italic,e.underline,e.strike,e.color,e.textOutline,e.textOutlineColor,e.textOutlineWidth);}
    private void applyTextStyle(TextFormattingToolbar.Style style){try{commitCell();var e=selectedText();if(e==null)return;int selected=table.getSelectedRow();e.font=style.font();e.fontMm=style.size()*25.4/72;e.bold=style.bold();e.italic=style.italic();e.underline=style.underline();e.strike=style.strike();e.color=style.color();e.textOutline=style.outline();e.textOutlineColor=style.outlineColor();e.textOutlineWidth=style.outlineWidth();template.validate();recordHistory();layers.fireTableDataChanged();if(selected>=0&&selected<table.getRowCount())table.setRowSelectionInterval(selected,selected);dirty=true;preview.repaint();refresh.restart();}catch(Exception ex){error(ex);}}
    private void bindHistory(String key,String action,boolean redo){
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),action);
        getActionMap().put(action,new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){historyAction(redo);}});
    }
    private void recordHistory(){
        if(restoringHistory || template==null || historyState==null)return;
        template.name=name.getText();String next=historyJson.toJson(template);
        if(next.equals(historyState))return;
        undoHistory.addLast(historyState);trimHistory(undoHistory);redoHistory.clear();historyState=next;
        dirty=!historyState.equals(savedState);updateHistoryButtons();
    }
    private void trimHistory(java.util.Deque<String> history){
        long bytes=history.stream().mapToLong(s->2L*s.length()).sum();
        while(history.size()>50 || (bytes>32L*1024*1024 && history.size()>1))bytes-=2L*history.removeFirst().length();
    }
    private void updateHistoryButtons(){undoButton.setEnabled(!undoHistory.isEmpty());redoButton.setEnabled(!redoHistory.isEmpty());}
    private void historyAction(boolean redo){
        try{
            commitCell();recordHistory();var source=redo?redoHistory:undoHistory;var target=redo?undoHistory:redoHistory;
            if(source.isEmpty())return;
            int selected=table.getSelectedRow();target.addLast(historyState);trimHistory(target);historyState=source.removeLast();
            restoringHistory=true;
            try{template=historyJson.fromJson(historyState,BadgeTemplate.class);template.elements.forEach(e->{e.personalPhoto=personalPhotos.get(e.photoId);e.photoFraming=photoFrames.get(e.photoId);});name.setText(template.name);rebuildFields(true);layers.fireTableDataChanged();
                if(selected>=0 && !template.elements.isEmpty()){int row=Math.min(selected,template.elements.size()-1);table.setRowSelectionInterval(row,row);}
            }finally{restoringHistory=false;}
            dirty=!historyState.equals(savedState);updateHistoryButtons();refresh.restart();preview.repaint();
        }catch(Exception ex){error(ex);}
    }
    private void commitCell() throws IOException {if(!preview.finishText(true))throw new IOException("Finish editing the text.");if(table.isEditing()&&!table.getCellEditor().stopCellEditing())throw new IOException("Finish editing the current design cell.");}
    private void addShape(String shape) throws IOException {
        commitCell();if(template.elements.size()>=64)throw new IOException("A template supports up to 64 elements.");
        BadgeTemplate.Element e=new BadgeTemplate.Element();e.label=shape;e.type=switch(shape){case "Ellipse"->"ELLIPSE";case "Triangle"->"TRIANGLE";case "Line"->"LINE";default->"RECT";};
        e.x=5;e.y=5;e.width=Math.min(25,template.widthMm);e.height="LINE".equals(e.type)?.5:Math.min(15,template.heightMm);e.color=String.format("#%06x",DeckersPalette.PURPLE.getRGB()&0xffffff);
        template.elements.add(e);layers.fireTableDataChanged();int row=template.elements.size()-1;table.setRowSelectionInterval(row,row);dirty=true;preview.repaint();preview.requestFocusInWindow();
        recordHistory();
    }
    private void reorderLayer(int direction) throws IOException {
        commitCell();int row=table.getSelectedRow(),target=row+direction;
        if(row<0||target<0||target>=template.elements.size())return;
        java.util.Collections.swap(template.elements,row,target);layers.fireTableDataChanged();table.setRowSelectionInterval(target,target);dirty=true;preview.repaint();preview.requestFocusInWindow();
        recordHistory();
    }
    private Map<String,String> values(){Map<String,String> result=new LinkedHashMap<>();recordFields.forEach((key,field)->{if(!field.getText().isBlank())result.put(key,field.getText());});return result;}
    private void rebuildFields(boolean preserve){
        Map<String,String> old=preserve?values():Map.of();details.removeAll();recordFields.clear();details.add(DeckersSwing.metaLabel("Template name"));details.add(name);
        JCheckBox keep=new JCheckBox("Save print records (off by default)",template.savePrintRecords);
        keep.addActionListener(e->{template.savePrintRecords=keep.isSelected();dirty=true;recordHistory();});details.add(keep);
        addButton(details,"Print records (admin)…",()->openPrintRecords(0));
        addButton(details,"Enter card details…",this::editRecordDetails);
        if(template.savePrintRecords){
            addButton(details,"Create / rotate organization link…",submissions::link);
            addButton(details,"Copy organization link…",submissions::copyLink);
            addButton(details,"Reset organization access code…",submissions::resetCode);
            addButton(details,"Revoke organization link…",submissions::revoke);
            addButton(details,"Submission queue…",()->submissions.open(0));
            addButton(details,"Release current review",submissions::release);
            addButton(details,"Reject current submission…",submissions::reject);
        }
        for(BadgeTemplate.Element element:template.elements){if(!"TEXT".equals(element.type)||element.field.isEmpty()||recordFields.containsKey(element.field))continue;
            JTextField field=new JTextField(old.getOrDefault(element.field,""),8);DeckersSwing.styleField(field);recordFields.put(element.field,field);details.add(DeckersSwing.metaLabel(element.label));details.add(field);
            field.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){refresh.restart();}public void removeUpdate(DocumentEvent e){refresh.restart();}public void changedUpdate(DocumentEvent e){refresh.restart();}});
        }
        addButton(details,"Choose / replace selected photo",this::choosePhoto);addButton(details,"Edit photo / crop / background…",this::frameSelectedPhoto);addButton(details,"New person / clear details",()->{resetFields();refresh.restart();});details.setPreferredSize(new Dimension(280,Math.max(220,((details.getComponentCount()+1)/2)*44)));details.revalidate();details.repaint();
    }
    private static Path savedFolder(){
        String fallback=Path.of(System.getProperty("smartstudio.register.home",System.getProperty("user.home")),"Documents","SmartStudio","Badge Templates").toString();
        try{return Path.of(System.getProperty("smartstudio.badge.folder",Preferences.userNodeForPackage(BadgeCardsPanel.class).get("badgeTemplateFolder",fallback)));}catch(RuntimeException ex){return Path.of(fallback);}
    }
    private void chooseFolder()throws IOException{
        JFileChooser chooser=new JFileChooser();chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setDialogTitle("Choose the one Badge Templates folder");chooser.setCurrentDirectory(library.folder().toFile());
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path chosen=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if(shared!=null){importSharedFolder(chosen);return;}
        Files.createDirectories(chosen);library=new BadgeTemplateLibrary(chosen);
        Preferences.userNodeForPackage(BadgeCardsPanel.class).put("badgeTemplateFolder",chosen.toString());
        if(!library.contains(currentFile)){currentFile=null;currentFingerprint=null;}
        refreshLibrary();message.setText("Template folder: "+chosen.getFileName()+" • OneDrive file sharing is handled by Windows.");message.setToolTipText(chosen.toString());
    }
    private void importSharedFolder(Path folder)throws IOException{
        if(!shared.online)throw new IOException("Offline mode: reconnect and refresh before importing templates.");
        if(sharedSave||syncing)throw new IOException("Wait for library synchronization to finish.");
        sharedSave=true;new SwingWorker<Integer,Void>(){protected Integer doInBackground()throws Exception{
            int count=0;for(Path path:new BadgeTemplateLibrary(folder).templates()){BadgeTemplate imported=BadgeTemplate.load(path);shared.save(imported,null,"");count++;}return count;
        }protected void done(){sharedSave=false;try{message.setText("Uploaded "+get()+" templates to the shared server library.");}catch(Exception ex){error(ex);}syncShared();}}.execute();
    }
    private void refreshLibrary()throws IOException{
        var files=library.templates();if(shared!=null)files=files.stream().filter(shared.stores::visible).toList();updatingLibrary=true;
        try{libraryTemplates.removeAllItems();for(Path file:files)libraryTemplates.addItem(file);libraryTemplates.setSelectedItem(library.contains(currentFile)?currentFile:null);libraryTemplates.setToolTipText(library.folder().toString());}finally{updatingLibrary=false;}
        firePropertyChange("sharedLibraryChanged",false,true);
    }
    private void loadTemplate(Path file)throws IOException{
        String fingerprint=BadgeTemplateLibrary.fingerprint(file);BadgeTemplate loaded=BadgeTemplate.load(file);if(!fingerprint.equals(BadgeTemplateLibrary.fingerprint(file)))throw new IOException("This template changed while opening. Wait for file synchronization to finish, then reopen it.");template=loaded;currentFile=library.contains(file)?file:null;currentFingerprint=currentFile==null?null:fingerprint;resetFields();changedTemplate();dirty=currentFile==null;
        message.setText(currentFile==null?"Imported design. Save template to add it to your library.":"Opened library template: "+file.getFileName());
    }

    private void renderPreview(){
        preview.repaint();
    }

    boolean chooseTemplate() {
        for(;;){
        try {
            try{refreshLibrary();}catch(IOException ex){message.setText("Template folder unavailable. Use Template folder to choose another location.");}
            var choices=templateChoices();
            JLabel artwork=new JLabel();artwork.setPreferredSize(new Dimension(380,240));artwork.setHorizontalAlignment(SwingConstants.CENTER);
            BadgeTemplatePicker picker=new BadgeTemplatePicker(choices,artwork,choice->{
                artwork.setIcon(null);
                if(choice==null){artwork.setText("No template selected");return;}
                if(choice.kind()==BadgeTemplatePicker.Kind.IMPORT){artwork.setText("Browse for a .sstbadge template file");return;}
                try{BadgeTemplate candidate=switch(choice.kind()){case FILE->BadgeTemplate.load(choice.file());case BUILTIN->BadgeTemplate.builtin();case BLANK->new BadgeTemplate();default->template;};
                BufferedImage image=candidate.render(Map.of(),null,100);artwork.setText("");artwork.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(image,340,214)));
            }catch(Exception ex){artwork.setText("Preview unavailable");}});
            JPanel location=DeckersSwing.panel();location.setLayout(new BorderLayout(8,0));
            JLabel folder=new JLabel(shared==null?"Library: "+library.folder().getFileName():shared.status);folder.setToolTipText(library.folder().toString());location.add(folder);
            if(shared!=null){JPanel storeTools=DeckersSwing.panel();storeTools.setLayout(new FlowLayout(FlowLayout.LEFT));storeTools.add(shared.stores.selector(()->{try{refreshLibrary();picker.replaceChoices(templateChoices());}catch(Exception ex){error(ex);}}));JButton assign=StudioWorkspaceShell.button("Store ownership / sharing…",()->{});assign.addActionListener(e->{var selected=picker.selection();if(selected==null||selected.file()==null)return;var body=shared.stores.assignment(this,selected.file());if(body==null)return;var active=shared;new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{active.assign(body);return null;}protected void done(){try{get();refreshLibrary();}catch(Exception ex){error(ex);}}}.execute();});storeTools.add(assign);location.add(storeTools,BorderLayout.NORTH);}
            JButton changeFolder=StudioWorkspaceShell.button("Choose template folder…",()->{});location.add(changeFolder,BorderLayout.EAST);
            JPanel content=DeckersSwing.panel();content.setLayout(new BorderLayout(8,8));content.add(picker);content.add(location,BorderLayout.SOUTH);
            JOptionPane pane=new JOptionPane(content,JOptionPane.PLAIN_MESSAGE,JOptionPane.OK_CANCEL_OPTION);
            JButton accept=StudioWorkspaceShell.button("Open selected",()->pane.setValue(JOptionPane.OK_OPTION));
            JButton cancel=StudioWorkspaceShell.button("Cancel",()->pane.setValue(JOptionPane.CANCEL_OPTION));
            final int createValue=11;
            JButton create=StudioWorkspaceShell.button("Create new badge template",()->pane.setValue(createValue));
            JButton delete=StudioWorkspaceShell.button("Delete selected…",()->{});
            delete.setEnabled(shared!=null);
            delete.addActionListener(e->{var selection=picker.selection();if(selection==null||selection.file()==null)return;deleteSharedTemplate(selection.file(),()->picker.replaceChoices(templateChoices()));});
            pane.setOptions(new Object[]{create,accept,delete,cancel});accept.setEnabled(picker.selection()!=null);
            picker.addPropertyChangeListener("selectionAvailable",e->accept.setEnabled(picker.selection()!=null));
            final int chooseFolderValue=10;changeFolder.addActionListener(e->pane.setValue(chooseFolderValue));
            JDialog dialog=pane.createDialog(this,"Choose badge template");dialog.setResizable(true);dialog.getRootPane().setDefaultButton(accept);
            java.beans.PropertyChangeListener libraryChange=e->{picker.replaceChoices(templateChoices());folder.setText(shared==null?"Local library":shared.status);};
            addPropertyChangeListener("sharedLibraryChanged",libraryChange);
            dialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowOpened(java.awt.event.WindowEvent e){picker.focusSearch();}});
            try{dialog.setVisible(true);}finally{removePropertyChangeListener("sharedLibraryChanged",libraryChange);dialog.dispose();}
            if(Integer.valueOf(chooseFolderValue).equals(pane.getValue())){chooseFolder();continue;}
            if(Integer.valueOf(createValue).equals(pane.getValue()))return newTemplate();
            if(!Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()))return false;
            BadgeTemplatePicker.Choice selected=picker.selection();if(selected==null)return false;
            if(selected.kind()==BadgeTemplatePicker.Kind.CURRENT)return true;
            if(selected.kind()==BadgeTemplatePicker.Kind.IMPORT){open();return true;}
            if(!discard())return false;
            if(selected.kind()==BadgeTemplatePicker.Kind.FILE)loadTemplate(selected.file());
            else{template=selected.kind()==BadgeTemplatePicker.Kind.BUILTIN?BadgeTemplate.builtin():new BadgeTemplate();currentFile=null;resetFields();changedTemplate();}
            return true;
        }catch(Exception ex){error(ex);return false;}
        }
    }
    private void deleteSharedTemplate(Path file,Runnable refreshed){
        if(shared==null)return;
        if(JOptionPane.showConfirmDialog(this,"Delete this shared template from every store where it is shared?\nAlready-open designs and saved print records are retained.","Delete template",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
        var active=shared;
        new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{active.delete(file);return null;}
            protected void done(){try{get();if(shared!=active)return;refreshLibrary();refreshed.run();message.setText("Shared template deleted. Already-open designs were retained.");}catch(Exception ex){error(ex);}}
        }.execute();
    }
    private boolean newTemplate(){
        if(!discard())return false;
        template=new BadgeTemplate();currentFile=null;resetFields();changedTemplate();
        message.setText("New badge template • Add text, photo fields or artwork, set its name under Template, then Save to the library.");
        return true;
    }
    private java.util.List<BadgeTemplatePicker.Choice> templateChoices(){
        var choices=new java.util.ArrayList<BadgeTemplatePicker.Choice>();
        choices.add(new BadgeTemplatePicker.Choice("Continue current design",BadgeTemplatePicker.Kind.CURRENT,null));choices.add(new BadgeTemplatePicker.Choice("Skeldon Primary",BadgeTemplatePicker.Kind.BUILTIN,null));choices.add(new BadgeTemplatePicker.Choice("New blank badge",BadgeTemplatePicker.Kind.BLANK,null));choices.add(new BadgeTemplatePicker.Choice("Open / import file…",BadgeTemplatePicker.Kind.IMPORT,null));
        for(int i=0;i<libraryTemplates.getItemCount();i++){Path file=libraryTemplates.getItemAt(i);choices.add(new BadgeTemplatePicker.Choice(shared==null?file.getFileName().toString().replaceFirst("(?i)\\.sstbadge$",""):shared.label(file),BadgeTemplatePicker.Kind.FILE,file));}
        return choices;
    }

    private void open()throws Exception{
        if(!discard())return;JFileChooser chooser=chooser("SmartStudio badge template","sstbadge");
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        loadTemplate(chooser.getSelectedFile().toPath());
    }
    private void save(boolean export)throws Exception{
        commitCell();template.name=name.getText().trim();template.validate();
        if(shared!=null&&!shared.online)throw new IOException("Offline mode: changes cannot be saved. Reconnect to the store server and refresh first.");
        if(shared!=null&&!export){
            if(!shared.online)throw new IOException("Offline mode: changes cannot be saved. Reconnect to the store server and refresh first.");
            if(sharedSave||syncing)throw new IOException("Wait for library synchronization to finish.");
            recordHistory();String saving=historyState;BadgeTemplate snapshot=template.snapshot();Path previous=currentFile;String expected=currentFingerprint;long session=generation;sharedSave=true;
            new SwingWorker<Path,Void>(){protected Path doInBackground()throws Exception{return shared.save(snapshot,previous,expected);}
                protected void done(){sharedSave=false;sharedStatus.setText(shared.status);try{Path file=get();if(session!=generation)return;currentFile=file;currentFingerprint=BadgeTemplateLibrary.fingerprint(file);savedState=saving;dirty=!historyState.equals(savedState);refreshLibrary();message.setText("Saved to server; OneDrive backup queued.");}catch(Exception ex){error(ex.getCause() instanceof Exception cause?cause:ex);}}}.execute();return;
        }
        Path target=library.contains(currentFile)?currentFile:library.fileFor(template.name);
        if(export){JFileChooser chooser=chooser("SmartStudio badge template","sstbadge");chooser.setSelectedFile(library.fileFor(template.name).toFile());if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;target=extension(chooser.getSelectedFile().toPath(),"sstbadge");if(!replace(target))return;}
        else if(currentFile==null){if(!replace(target))return;}
        else if(!Files.exists(target)||!BadgeTemplateLibrary.fingerprint(target).equals(currentFingerprint))throw new IOException("This template changed on disk. Export your draft to a new file, then reopen the updated library template before editing it.");
        template.save(target);recordHistory();if(!export){currentFile=target;currentFingerprint=BadgeTemplateLibrary.fingerprint(target);savedState=historyState;dirty=false;refreshLibrary();}
        message.setText((export?"Exported template: ":"Saved in template folder: ")+target.getFileName());message.setToolTipText(target.toString());
    }
    private void configureTextBinding(boolean selected)throws Exception{
        commitCell();int row=table.getSelectedRow();
        if(selected&&(row<0||!"TEXT".equals(template.elements.get(row).type)))throw new IOException("Select a text layer to bind first.");
        var element=selected?template.elements.get(row):null;
        JComboBox<String> binding=new JComboBox<>(new String[]{"fullName","idNumber","grade","phone","department"});binding.setEditable(true);
        binding.setSelectedItem(element!=null&&!element.field.isEmpty()?element.field:"fullName");
        JTextField label=new JTextField(element==null?"Full name":element.label,20);
        if(element==null)binding.addActionListener(e->{String key=String.valueOf(binding.getSelectedItem());label.setText(switch(key){case "fullName"->"Full name";case "idNumber"->"ID number";case "phone"->"Phone";case "grade"->"Grade";case "department"->"Department";default->key;});});
        JPanel form=new JPanel(new GridLayout(0,2,8,8));form.add(new JLabel("Field label"));form.add(label);form.add(new JLabel("Binding (or type a custom name)"));form.add(binding);
        form.add(new JLabel("Examples: fullName, idNumber, className"));form.add(new JLabel("Values clear for each new person."));
        if(JOptionPane.showConfirmDialog(this,form,selected?"Bind selected text":"Add text field",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String key=String.valueOf(binding.getEditor().getItem()).trim();
        if(!key.matches("[A-Za-z][A-Za-z0-9_]{0,39}"))throw new IOException("Use a binding starting with a letter, followed by letters, numbers or underscores (up to 40 characters).");
        bindTextField(selected?row:-1,label.getText(),key);
    }
    void bindTextField(int row,String label,String binding){
        if(label==null||label.isBlank()||label.length()>160||binding==null||!binding.matches("[A-Za-z][A-Za-z0-9_]{0,39}"))throw new IllegalArgumentException("Enter a field label and valid binding.");
        if(row>=0&&!"TEXT".equals(template.elements.get(row).type))throw new IllegalArgumentException("Select a text layer.");
        if(row<0&&template.elements.size()>=64)throw new IllegalArgumentException("A template supports up to 64 elements.");
        BadgeTemplate.Element element=row<0?new BadgeTemplate.Element():template.elements.get(row);
        element.label=label.trim();element.field=binding;element.text="";
        if(row<0){element.width=Math.min(30,template.widthMm);element.height=Math.min(6,template.heightMm);template.elements.add(element);row=template.elements.size()-1;}
        rebuildFields(true);layers.fireTableDataChanged();table.setRowSelectionInterval(row,row);dirty=true;recordHistory();refresh.restart();
    }
    private String recordFieldLabel(String binding){
        return template.elements.stream().filter(e->"TEXT".equals(e.type)&&binding.equals(e.field)).map(e->e.label).findFirst().orElse(binding);
    }
    private void editRecordDetails(){
        JPanel form=new JPanel(new GridLayout(0,2,8,8));Map<String,JTextField> edits=new LinkedHashMap<>();
        recordFields.forEach((key,value)->{form.add(new JLabel(recordFieldLabel(key)));JTextField edit=new JTextField(value.getText(),20);edits.put(key,edit);form.add(edit);});
        if(edits.isEmpty()){JOptionPane.showMessageDialog(this,"Bind text layers to Data fields such as fullName and idNumber to enter card details.");return;}
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(form),"Card details",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)edits.forEach((key,value)->recordFields.get(key).setText(value.getText()));
    }
    private com.google.gson.JsonObject recordPayload()throws Exception{
        var payload=new com.google.gson.JsonObject();payload.add("values",historyJson.toJsonTree(values()));var photos=new com.google.gson.JsonObject();
        for(var e:template.elements)if("PHOTO".equals(e.type)&&e.personalPhoto!=null){
            var frame=e.photoFraming==null?PhotoFraming.initial(e.personalPhoto):e.photoFraming;var item=new com.google.gson.JsonObject();
            var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(e.personalPhoto,"png",bytes);item.addProperty("image",java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
            item.addProperty("zoom",frame.zoom());item.addProperty("offsetX",frame.offsetX());item.addProperty("offsetY",frame.offsetY());item.addProperty("turns",frame.turns());photos.add(e.photoId,item);
        }
        payload.add("photos",photos);return payload;
    }
    private void openPrintRecords(int offset)throws Exception{
        if(shared==null)throw new IOException("Connect to the store server to open print records.");
        Path target=currentFile;long session=generation;var query=new com.google.gson.JsonObject();query.addProperty("offset",offset);
        new SwingWorker<com.google.gson.JsonObject,Void>(){
            protected com.google.gson.JsonObject doInBackground()throws Exception{return shared.record(target,"RECORD_LIST",query);}
            protected void done(){try{var result=get();if(session!=generation||!java.util.Objects.equals(target,currentFile))return;
                var rows=result.getAsJsonArray("records");if(rows.isEmpty()){JOptionPane.showMessageDialog(BadgeCardsPanel.this,"No more print records for this template.");return;}
                String[] labels=new String[rows.size()+1];for(int i=0;i<rows.size();i++){var row=rows.get(i).getAsJsonObject();labels[i]=row.get("createdAt").getAsString()+"  "+row.get("values");}labels[rows.size()]="Next 100 records…";
                String choice=(String)JOptionPane.showInputDialog(BadgeCardsPanel.this,"Choose details to load into the CURRENT template format.","Print records (admin)",JOptionPane.PLAIN_MESSAGE,null,labels,labels[0]);if(choice==null)return;
                int index=java.util.Arrays.asList(labels).indexOf(choice);if(index==rows.size()){openPrintRecords(offset+100);return;}
                var body=new com.google.gson.JsonObject();body.add("recordId",rows.get(index).getAsJsonObject().get("recordId"));
                new SwingWorker<com.google.gson.JsonObject,Void>(){
                    protected com.google.gson.JsonObject doInBackground()throws Exception{return shared.record(target,"RECORD_GET",body);}
                    protected void done(){try{var payload=get();if(session!=generation||!java.util.Objects.equals(target,currentFile))return;loadPrintRecord(payload);
                    }catch(Exception ex){error(ex);}}
                }.execute();
            }catch(Exception ex){error(ex);}}
        }.execute();
    }
    void loadPrintRecord(com.google.gson.JsonObject payload)throws Exception{
        resetFields();
        var data=payload.getAsJsonObject("values");
        recordFields.forEach((key,field)->{if(data.has(key))field.setText(data.get(key).getAsString());});
        var photos=payload.getAsJsonObject("photos");
        for(var element:template.elements)if("PHOTO".equals(element.type)&&photos.has(element.photoId)){
            var item=photos.getAsJsonObject(element.photoId);
            var image=BadgeTemplate.decode(item.get("image").getAsString());
            var frame=new PhotoFraming(image,item.get("zoom").getAsDouble(),item.get("offsetX").getAsDouble(),item.get("offsetY").getAsDouble(),item.get("turns").getAsInt());
            element.personalPhoto=image;element.photoFraming=frame;
            personalPhotos.put(element.photoId,image);photoFrames.put(element.photoId,frame);
        }
        refresh.restart();message.setText("Record loaded into current format. Review details, then Print.");
    }
    private void choosePhoto()throws Exception{
        int row=table.getSelectedRow();
        if(row<0||!"PHOTO".equals(template.elements.get(row).type)){
            var slots=template.elements.stream().filter(e->"PHOTO".equals(e.type)).toList();
            if(slots.size()!=1)throw new IOException("Select the photo field you want to fill on the canvas or in Layers first.");
            row=template.elements.indexOf(slots.get(0));table.setRowSelectionInterval(row,row);
        }
        BadgeTemplate.Element slot=template.elements.get(row);
        JFileChooser chooser=chooser("PNG and JPEG photos","png","jpg","jpeg");if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        BufferedImage loaded=StudioImageFile.loadPhoto(chooser.getSelectedFile().toPath()).image();
        PhotoFraming framed=PhotoFramingDialog.edit(this,slot.stylePhoto(PhotoFraming.initial(loaded).fit(slot.width,slot.height)),slot.width,slot.height);
        if(framed!=null){if(!submissions.reviewing())recordFields.values().forEach(field->field.setText(""));applyPhotoFrame(slot,framed);editRecordDetails();}
    }
    private void frameSelectedPhoto()throws Exception{
        commitCell();int row=table.getSelectedRow();if(row<0||!"PHOTO".equals(template.elements.get(row).type))throw new IOException("Select a photo field first.");
        BadgeTemplate.Element slot=template.elements.get(row);if(slot.personalPhoto==null){choosePhoto();return;}
        PhotoFraming current=slot.photoFraming==null?PhotoFraming.initial(slot.personalPhoto):slot.photoFraming;
        PhotoFraming framed=PhotoFramingDialog.edit(this,slot.stylePhoto(current),slot.width,slot.height);if(framed!=null)applyPhotoFrame(slot,framed);
    }
    private void applyPhotoFrame(BadgeTemplate.Element slot,PhotoFraming framed){
        slot.photoBackgroundEnabled=framed.background()!=null;slot.photoOutlineEnabled=framed.outline()!=null;
        if(framed.background()!=null)slot.photoBackgroundColor=String.format("#%06x",framed.background().getRGB()&0xffffff);
        if(framed.outline()!=null)slot.photoOutlineColor=String.format("#%06x",framed.outline().getRGB()&0xffffff);
        slot.personalPhoto=framed.source();slot.photoFraming=framed;personalPhotos.put(slot.photoId,slot.personalPhoto);photoFrames.put(slot.photoId,framed);refresh.restart();preview.repaint();
        recordHistory();
    }
    private void insertImageFile(){
        JFileChooser file=chooser("PNG and JPEG images","png","jpg","jpeg");if(file.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        loadImageLayer(()->StudioImageFile.load(file.getSelectedFile().toPath()));
    }

    private void copyLayers(){try{commitCell();int[] rows=preview.selectedIndices();if(rows.length>0)StudioLayerClipboard.put(StudioLayerClipboard.badge(template,rows));}catch(Exception ex){error(ex);}}
    private void pasteLayers(){try{
        commitCell();var data=StudioLayerClipboard.contents();if(data==null)return;int first=template.elements.size();
        if(data.isDataFlavorSupported(StudioLayerClipboard.FLAVOR))template=StudioLayerClipboard.paste(template,(StudioLayerClipboard.Payload)data.getTransferData(StudioLayerClipboard.FLAVOR));
        else if(StudioImageTransfer.supports(data)){pasteImageLayer();return;}
        else if(data.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)){
            BadgeTemplate next=template.snapshot();var e=new BadgeTemplate.Element();e.label="Text";e.text=(String)data.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);e.width=Math.min(30,next.widthMm);e.height=Math.min(6,next.heightMm);next.elements.add(e);next.validate();template.elements.add(e);
        }else throw new IllegalArgumentException("Copy layers, text, or an image first.");
        rebuildFields(true);layers.fireTableDataChanged();table.setRowSelectionInterval(first,template.elements.size()-1);dirty=true;recordHistory();refresh.restart();preview.requestFocusInWindow();
    }catch(Exception ex){error(ex);}}
    private void pasteImageLayer(){try{loadImageLayer(CanvasImageInsert.clipboard());}catch(Exception ex){error(ex);}}
    private void loadImageLayer(java.util.concurrent.Callable<StudioImageFile.Loaded> loader){
        BadgeTemplate target=template;long session=generation;
        CanvasImageInsert.load(loader,image->{if(template!=target||generation!=session)return;try{insertImageLayer(image);}catch(Exception ex){error(ex);}},this::error);
    }
    void insertImageLayer(StudioImageFile.Loaded image)throws Exception{
        commitCell();if(template.elements.size()>=64)throw new IOException("A template supports up to 64 elements.");
        BadgeTemplate.Element element=new BadgeTemplate.Element();element.type="IMAGE";element.label=image.name();
        element.image=java.util.Base64.getEncoder().encodeToString(image.bytes());
        double[] size=CanvasImageInsert.fit(image.image().getWidth(),image.image().getHeight(),template.widthMm,template.heightMm);
        element.width=size[0];element.height=size[1];element.x=(template.widthMm-element.width)/2;element.y=(template.heightMm-element.height)/2;
        template.elements.add(element);try{template.validate();}catch(Exception ex){template.elements.remove(element);throw ex;}
        layers.fireTableDataChanged();int row=template.elements.size()-1;table.setRowSelectionInterval(row,row);dirty=true;recordHistory();refresh.restart();preview.requestFocusInWindow();
    }
    private void replaceArtwork()throws Exception{
        commitCell();int row=table.getSelectedRow();if(row>=0&&"PHOTO".equals(template.elements.get(row).type)){choosePhoto();return;}if(row<0||!"IMAGE".equals(template.elements.get(row).type))throw new IOException("Select an artwork image or photo field first. Artwork is a logo or graphic saved with the template; photo fields hold personal photos for the current card.");
        JFileChooser chooser=chooser("PNG and JPEG artwork","png","jpg","jpeg");if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        template.elements.get(row).image=java.util.Base64.getEncoder().encodeToString(StudioImageFile.load(chooser.getSelectedFile().toPath()).bytes());dirty=true;refresh.restart();
        recordHistory();
    }
    void printCard() {
        try {
            commitCell();template.name=name.getText().trim();template.validate();
            BufferedImage back=null;
            if(manualDuplex.isSelected()) {
                if(backTemplate==null)throw new IOException("Choose a back template before printing both sides.");
                if(Math.abs(backTemplate.widthMm-template.widthMm)>.01||Math.abs(backTemplate.heightMm-template.heightMm)>.01)
                    throw new IOException("Front and back templates must have the same card dimensions and orientation.");
                back=backTemplate.render(values(),photo,300);
                JLabel backPreview=new JLabel(new ImageIcon(back.getScaledInstance(428,270,Image.SCALE_SMOOTH)));
                JPanel confirmation=new JPanel(new BorderLayout(8,8));
                confirmation.add(new JLabel("One card: current design on front; "+backTemplate.name+" on back. Check back details below."),BorderLayout.NORTH);
                confirmation.add(backPreview);
                confirmation.add(new JLabel("Choose Magicard 300 / card paper in the print dialog. Enable Hand Feed Mode on the printer."),BorderLayout.SOUTH);
                if(JOptionPane.showConfirmDialog(this,confirmation,"Print front and back",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            }
            var record=template.savePrintRecords?recordPayload():null;Path recordTemplate=currentFile;
            if(record!=null&&shared==null)throw new IOException("Connect to the shared server library before printing with record saving enabled.");
            var hooks=submissions.printing(recordTemplate,record);
            StudioPrinting.print(this,template.render(values(),photo,300),back,template.name,
                    template.widthMm*72/25.4,template.heightMm*72/25.4,message::setText,()->{
                        if(hooks!=null){hooks.before().run();return;}
                        if(record==null)return;var body=new com.google.gson.JsonObject();body.addProperty("recordId",java.util.UUID.randomUUID().toString());body.add("payload",record);
                        try{shared.record(recordTemplate,"RECORD_SAVE",body);}catch(Exception ex){throw new IllegalStateException("Print was not sent because its record could not be saved: "+ex.getMessage(),ex);}
                    },()->{if(hooks!=null)hooks.sent().run();});
        } catch(Exception ex){error(ex);}
    }

    private void chooseBackTemplate() throws IOException {
        JFileChooser chooser=chooser("Back badge template","sstbadge");
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        backTemplate=BadgeTemplate.load(chooser.getSelectedFile().toPath());
        manualDuplex.setSelected(true);manualDuplex.setToolTipText("Back: "+backTemplate.name);
        message.setText("Back template: "+backTemplate.name+" • Matching data fields use the current card details.");
    }

    private void export(boolean pdf)throws Exception{
        commitCell();template.name=name.getText().trim();template.validate();String ext=pdf?"pdf":"png";JFileChooser chooser=chooser(pdf?"PDF badge":"PNG badge",ext);
        Path outputs=Path.of(System.getProperty("smartstudio.register.home",System.getProperty("user.home")),"Documents","SmartStudio","Badge Output");Files.createDirectories(outputs);chooser.setCurrentDirectory(outputs.toFile());
        chooser.setSelectedFile(new java.io.File("badge."+ext));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path target=extension(chooser.getSelectedFile().toPath(),ext);if(!replace(target))return;
        BufferedImage result=template.render(values(),photo,300);
        if(pdf){try(PDDocument document=new PDDocument()){PDPage page=new PDPage(new PDRectangle((float)(template.widthMm*72/25.4),(float)(template.heightMm*72/25.4)));document.addPage(page);try(PDPageContentStream content=new PDPageContentStream(document,page)){content.drawImage(LosslessFactory.createFromImage(document,result),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}document.save(target.toFile());}}
        else ImageIO.write(result,"png",target.toFile());message.setText("Exported card at 300 DPI: "+target.getFileName()+" • Print at actual size.");
    }
    private static Path extension(Path path,String ext){return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("."+ext)?path:path.resolveSibling(path.getFileName()+"."+ext);}
    private boolean replace(Path target){return !Files.exists(target)||JOptionPane.showConfirmDialog(this,"Replace "+target.getFileName()+"?","Save",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private JFileChooser chooser(String label,String... extensions){JFileChooser chooser=new JFileChooser(library.folder().toFile());chooser.setFileFilter(new FileNameExtensionFilter(label,extensions));return chooser;}
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Badge Cards",JOptionPane.ERROR_MESSAGE);}
    @FunctionalInterface private interface Action{void run()throws Exception;}
    private void addButton(JPanel panel,String label,Action action){JButton button=new JButton(label);DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);button.addActionListener(e->{try{action.run();recordHistory();}catch(Exception ex){error(ex);}});panel.add(button);}

    private final class LayerModel extends AbstractTableModel{
        private final String[] columns={"Layer","Type","Text / placeholder","Data field","X mm","Y mm","Width mm","Height mm","Font mm","Font","Bold","Colour"};
        public int getRowCount(){return template==null?0:template.elements.size();}public int getColumnCount(){return columns.length;}public String getColumnName(int c){return columns[c];}
        public Class<?> getColumnClass(int c){return c==10?Boolean.class:String.class;}
        public boolean isCellEditable(int r,int c){return c!=1;}
        public Object getValueAt(int r,int c){BadgeTemplate.Element e=template.elements.get(r);return switch(c){case 0->e.label;case 1->e.type;case 2->e.text;case 3->e.field;case 4->e.x;case 5->e.y;case 6->e.width;case 7->e.height;case 8->e.fontMm;case 9->e.font;case 10->e.bold;default->e.color;};}
        public void setValueAt(Object value,int r,int c){
            BadgeTemplate.Element e=template.elements.get(r);Object old=getValueAt(r,c);
            try{assign(e,c,value);template.validate();if(c==3||c==0)rebuildFields(true);dirty=true;refresh.restart();recordHistory();}catch(Exception ex){assign(e,c,old);error(ex);}fireTableCellUpdated(r,c);
        }
        private void assign(BadgeTemplate.Element e,int c,Object v){String s=String.valueOf(v);switch(c){case 0->e.label=s;case 2->e.text=s;case 3->e.field=s;case 4->e.x=Double.parseDouble(s);case 5->e.y=Double.parseDouble(s);case 6->e.width=Double.parseDouble(s);case 7->e.height=Double.parseDouble(s);case 8->e.fontMm=Double.parseDouble(s);case 9->e.font=s;case 10->e.bold=Boolean.parseBoolean(s);case 11->e.color=s;default->{}}}
    }
}
