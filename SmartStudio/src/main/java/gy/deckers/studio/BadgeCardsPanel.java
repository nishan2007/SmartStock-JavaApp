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
    private String permitNumber;
    private com.google.gson.JsonObject pendingPermit;
    private boolean savingPermit;
    private final JLabel message=DeckersSwing.metaLabel("Templates use one library folder. Cross-store server sync is not connected yet.");
    private BadgeTemplateLibrary library=new BadgeTemplateLibrary(savedFolder());
    private final JComboBox<Path> libraryTemplates=new JComboBox<>();
    private boolean updatingLibrary;
    private String currentFingerprint;
    private final TemplateWorkspace preview=new TemplateWorkspace();
    private final StudioWorkspaceShell shell;
    private final LayerModel layers=new LayerModel();
    private BufferedImage photo;
    private final Map<String,BufferedImage> personalPhotos=new LinkedHashMap<>();
    private final Map<String,PhotoFraming> photoFrames=new LinkedHashMap<>();
    private final JComboBox<String> badgePage=new JComboBox<>(new String[]{"Front","Back"});
    private final JComboBox<String> printSide=new JComboBox<>(new String[]{"Front","Back"});
    private final JCheckBox manualDuplex=new JCheckBox("Magicard 300: front + back (manual flip)");
    private final Timer refresh;
    private JDialog layersWindow;
    private StudioLayersPanel floatingLayers;
    private StudioFileView fileView;
    private final JComboBox<String> orientation=new JComboBox<>(new String[]{"Portrait","Landscape"});
    private boolean updatingOrientation;
    private final JSpinner layoutWidth=new JSpinner(new SpinnerNumberModel(3.37,.001,1000.,.01)),layoutHeight=new JSpinner(new SpinnerNumberModel(2.126,.001,1000.,.01));
    private final JComboBox<String> layoutUnits=new JComboBox<>(new String[]{"IN","MM"});
    private final JCheckBox scaleLayout=new JCheckBox("Scale artwork",true);
    private long generation;
    private boolean dirty;
    private Path currentFile;
    private final JTable table=new JTable(layers);
    private final java.util.Deque<String> undoHistory=new java.util.ArrayDeque<>(),redoHistory=new java.util.ArrayDeque<>();
    private final com.google.gson.Gson historyJson=new com.google.gson.Gson();
    private String historyState,savedState;
    private boolean restoringHistory;
    private TextFormattingToolbar textFormatting;
    private boolean updatingLayerRows;
    private Runnable refreshLayerFormatting=()->{};
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
        shell.closeRecords();
        sharedSessionGeneration++;sharedActive=!offline;migrationStatus="";
        SharedBadgeLibrary candidate=new SharedBadgeLibrary();
        if(shared==null||!shared.folder().equals(candidate.folder())){shared=candidate;library=new BadgeTemplateLibrary(shared.folder());currentFile=null;currentFingerprint=null;}
        shared.stores.resetView();shared.online=false;sharedStatus.setText("OFFLINE — cached templates only; changes cannot be saved.");message.setText("Existing badge templates will migrate automatically after connecting to the shared server library.");
        try{refreshLibrary();}catch(Exception ignored){}
        if(syncTimer==null){syncTimer=new Timer(30000,e->syncShared());}
        if(!offline){syncTimer.start();syncShared();}else syncTimer.stop();
    }
    void stopSharedLibrary(){shell.closeRecords();sharedSessionGeneration++;sharedActive=false;if(syncTimer!=null)syncTimer.stop();if(shared!=null)shared.online=false;}
    private void syncShared(){
        if(shared==null||syncing||sharedSave)return;syncing=true;SharedBadgeLibrary active=shared;long session=sharedSessionGeneration;Path legacyFolder=savedFolder();
        sharedActive=true;if(syncTimer!=null)syncTimer.start();
        new SwingWorker<Void,Void>(){
            private String migrationMessage;
            protected Void doInBackground()throws Exception{
                active.refresh();
                if(sharedActive&&session==sharedSessionGeneration&&!active.smartDrive){
                    try{var migrated=active.migrateLegacy(legacyFolder,()->sharedActive&&session==sharedSessionGeneration);migrationMessage=migrated.message();if(migrated.imported()>0)active.refresh();}
                    catch(Exception ex){migrationMessage="Existing badge migration pending. Reconnect with permission to save templates; it will retry.";}
                }
                return null;
            }
            protected void done(){syncing=false;if(active!=shared||session!=sharedSessionGeneration){if(sharedActive)syncShared();return;}if(!sharedActive)active.online=false;sharedStatus.setText(active.online||active.smartDrive?active.status:"OFFLINE — cached templates only; changes cannot be saved.");if(migrationMessage!=null&&!migrationMessage.equals(migrationStatus)){migrationStatus=migrationMessage;message.setText(migrationMessage);}try{refreshLibrary();}catch(Exception ignored){}}}.execute();
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
        printTools.add(new JLabel("Print side"));printTools.add(printSide);printTools.add(manualDuplex);
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
            var layer=template.pageElements().get(row);
            if("IMAGE".equals(layer.type)){
                try{return editArtwork(layer);}
                catch(Exception ex){error(ex);return true;}
            }
            if(!"PHOTO".equals(layer.type))return false;
            try{if(OreallaRidersPermit.enabled(template)&&layer.personalPhoto!=null)frameSelectedPhoto();else choosePhoto();}
            catch(Exception ex){error(ex);}return true;
        });
        preview.setFieldEditor((key,value)->{JTextField field=recordFields.get(key);if(field!=null&&field.isEditable())field.setText(value);});
        preview.configure(() -> template, this::values, () -> photo, row -> {table.getSelectionModel().setValueIsAdjusting(true);try{table.clearSelection();for(int i:preview.selectedIndices())table.addRowSelectionInterval(i,i);}finally{table.getSelectionModel().setValueIsAdjusting(false);}}, () -> {dirty=true;int row=table.getSelectedRow();if(row>=0)layers.fireTableRowsUpdated(row,row);});
        table.getSelectionModel().addListSelectionListener(e -> {if(!updatingLayerRows&&!e.getValueIsAdjusting())preview.selectIndices(table.getSelectedRows());});
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
        JPanel editor=DeckersSwing.panel();editor.setLayout(new BorderLayout(8,8));editor.add(DeckersSwing.metaLabel("Drag to move • Arrow keys: ~0.004 in; Shift + arrow: ~0.039 in • double-click a cell to edit"),BorderLayout.NORTH);editor.add(edits);

        JButton textCommand=commandButton("Add text",() -> {commitCell();BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Text";e.text="Text";e.width=Math.min(30,template.widthMm);e.height=6;template.pageElements().add(e);rebuildFields(true);layers.fireTableDataChanged();refresh.restart();dirty=true;});
        JButton textFieldCommand=commandButton("Add text field…",()->configureTextBinding(false));
        JButton barcodeCommand=commandButton("Add barcode…",()->generateCode(false));
        JButton qrCommand=commandButton("Add QR code…",()->generateCode(true));
        JButton photoCommand=commandButton("Add photo field",() -> {commitCell();BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Photo";e.type="PHOTO";e.width=Math.min(18,template.widthMm);e.height=Math.min(24,template.heightMm);template.pageElements().add(e);layers.fireTableDataChanged();refresh.restart();dirty=true;});
        JButton imageCommand=commandButton("Insert image…",this::insertImageFile);
        StudioLayerClipboard.bind(preview,this::copyLayers,this::pasteLayers);
        StudioLayerClipboard.bind(table,this::copyLayers,this::pasteLayers);
        // The page owns the center. Data and advanced layers are opened on demand.
        card.remove(detailsScroll);
        card.remove(previewTools);
        shell=new StudioWorkspaceShell(card);
        shell.useTaskWindows();
        shell.title("Badge Cards");
        JPanel templateActions=DeckersSwing.panel();templateActions.setLayout(new BoxLayout(templateActions,BoxLayout.Y_AXIS));
        StudioWorkspaceShell.stack(libraryTools);StudioWorkspaceShell.stack(fileTools);
        templateActions.add(libraryTools);templateActions.add(fileTools);
        shell.addTools(StudioWorkspaceShell.menu("Template",templateActions));
        shell.addTextAndImage(textCommand,imageCommand);
        shell.ribbon().add(StudioCommand.TEXT_FIELD,textFieldCommand);
        shell.ribbon().add(StudioCommand.PHOTO_FIELD,photoCommand);
        shell.ribbon().add(StudioCommand.BARCODE,barcodeCommand);
        shell.ribbon().add(StudioCommand.QR,qrCommand);
        shell.ribbon().add(StudioCommand.WORDART,commandButton("WordArt…",this::insertWordArt));
        shell.ribbon().add(StudioCommand.TABLE,commandButton("Insert table…",this::insertTable));
        shell.ribbon().add("Home","Picture",commandButton("Edit artwork…",this::editSelectedArtwork));
        for(var shape:java.util.List.of(StudioCommand.RECTANGLE,StudioCommand.ELLIPSE,StudioCommand.TRIANGLE,StudioCommand.LINE))
            shell.ribbon().add(shape,commandButton(shape.label,()->addShape(shape.label)));
        shell.ribbon().add(StudioCommand.COPY,commandButton("Copy layers",this::copyLayers));
        shell.ribbon().add(StudioCommand.PASTE,commandButton("Paste",this::pasteLayers));
        shell.ribbon().add(StudioCommand.DELETE,commandButton("Remove selected layers",()->{commitCell();preview.deleteSelection();}));
        shell.ribbon().add("Home","Picture",commandButton("Replace selected image / photo",this::replaceArtwork));
        shell.ribbon().add("Home","Picture",commandButton("Crop / frame selected photo…",this::frameSelectedPhoto));
        shell.ribbon().add("Home","Picture",commandButton("Clear selected photo",this::clearSelectedPhoto));
        shell.ribbon().add("Data","Fields",commandButton("Bind selected text…",()->configureTextBinding(true)));
        shell.ribbon().add(StudioCommand.CHANGE_TEMPLATE,this::chooseTemplate);
        shell.addPrimaryAction("New template",this::newTemplate);
        shell.addTools(StudioWorkspaceShell.button("Save",()->{try{save(false);}catch(Exception ex){error(ex);}}));
        StudioWorkspaceShell.stack(outputTools);StudioWorkspaceShell.stack(printTools);
        JPanel outputActions=DeckersSwing.panel();outputActions.setLayout(new BoxLayout(outputActions,BoxLayout.Y_AXIS));outputActions.add(outputTools);outputActions.add(printTools);
        shell.addTools(StudioWorkspaceShell.menu("Output",outputActions));
        badgePage.getAccessibleContext().setAccessibleName("Badge page");
        badgePage.addActionListener(e->{if(template==null)return;try{commitCell();template.editingBack=badgePage.getSelectedIndex()==1;table.clearSelection();preview.select(-1);layers.fireTableDataChanged();rebuildFields(true);preview.repaint();}catch(Exception ex){error(ex);}});
        JPanel badgePages=DeckersSwing.panel();badgePages.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        badgePages.getAccessibleContext().setAccessibleName("Badge pages");
        badgePages.add(new JLabel("Page"));badgePages.add(badgePage);
        addButton(badgePages,"Edit back",()->badgePage.setSelectedIndex(1));
        card.add(badgePages,BorderLayout.NORTH);
        shell.ribbon().add("View","Pages",StudioWorkspaceShell.button("Front",()->badgePage.setSelectedIndex(0)));
        shell.ribbon().add("View","Pages",StudioWorkspaceShell.button("Back",()->badgePage.setSelectedIndex(1)));
        shell.ribbon().add(StudioCommand.UNDO,undoButton);shell.ribbon().add(StudioCommand.REDO,redoButton);
        textFormatting=new TextFormattingToolbar(this::selectedTextStyle,this::applyTextStyle,()->template.defaultTextAlignment(selectedText()));
        ImageFormattingToolbar imageFormatting=new ImageFormattingToolbar(this::selectedPicture,this::applyPictureSize,this::recolourPicture);imageFormatting.setTransparencyEditor(this::applyPictureTransparency);imageFormatting.setRotationEditor(this::applyPictureRotation);
        preview.setAspectRatioPolicy(i->Set.of("IMAGE","PHOTO").contains(template.pageElements().get(i).type)?imageFormatting.locked.isSelected():null);
        CardLayout contextLayout=new CardLayout();JPanel contextTools=new JPanel(contextLayout);contextTools.setOpaque(false);
        contextTools.add(textFormatting,"text");contextTools.add(imageFormatting,"image");shell.addTextTools(contextTools,this::changeLayerOrder);
        refreshLayerFormatting=()->{updateLayoutSize();updatingOrientation=true;try{orientation.setEnabled(template!=null);if(template!=null)orientation.setSelectedIndex(template.widthMm>template.heightMm?1:0);}finally{updatingOrientation=false;}textFormatting.refresh();imageFormatting.refresh();contextLayout.show(contextTools,selectedPicture()==null?"text":"image");};
        table.getSelectionModel().addListSelectionListener(event->{if(!updatingLayerRows&&!event.getValueIsAdjusting())refreshLayerFormatting.run();});
        table.getModel().addTableModelListener(event->{if(!updatingLayerRows)refreshLayerFormatting.run();});
        orientation.setEnabled(template!=null);orientation.setSelectedIndex(template!=null&&template.widthMm>template.heightMm?1:0);orientation.getAccessibleContext().setAccessibleName("Template orientation");
        orientation.addActionListener(e->{if(updatingOrientation||template==null)return;try{commitCell();BadgeTemplate next=template.snapshot();for(int i=0;i<next.allElements().size();i++){next.allElements().get(i).personalPhoto=template.allElements().get(i).personalPhoto;next.allElements().get(i).photoFraming=template.allElements().get(i).photoFraming;}if(TemplateOrientation.turn(next,orientation.getSelectedIndex()==0)){template=next;recordHistory();layers.fireTableDataChanged();dirty=true;preview.revalidate();preview.repaint();refresh.restart();}}catch(Exception ex){error(ex);}});
        JPanel orientationTools=new JPanel(new FlowLayout(FlowLayout.LEFT));orientationTools.setOpaque(false);orientationTools.add(new JLabel("Orientation"));orientationTools.add(orientation);shell.ribbon().add("Layout","Template",orientationTools);
        layoutUnits.addActionListener(e->updateLayoutSize());
        JPanel layout=new JPanel();layout.setOpaque(false);layout.add(StudioCommand.LAYERS.button(this::showLayers));
        layout.add(StudioWorkspaceShell.alignmentMenu(table::getSelectedRowCount,this::arrangeLayers));
        layout.add(StudioRibbon.field("Orientation",orientation,120));
        layout.add(StudioRibbon.field("Width",layoutWidth,86));layout.add(StudioRibbon.field("Height",layoutHeight,86));layout.add(StudioRibbon.field("Units",layoutUnits,70));layout.add(scaleLayout);
        layout.add(StudioRibbon.button("Apply size",()->{try{layoutWidth.commitEdit();layoutHeight.commitEdit();double factor=layoutUnits.getSelectedIndex()==0?25.4:1;resizeTemplate(((Number)layoutWidth.getValue()).doubleValue()*factor,((Number)layoutHeight.getValue()).doubleValue()*factor,scaleLayout.isSelected());}catch(Exception ex){error(ex);}}));
        shell.ribbon().inlineSettings("Layout",layout);
        JPanel print=new JPanel();print.setOpaque(false);for(Component control:printTools.getComponents())print.add(control);
        print.add(StudioRibbon.button("Save template setup",()->{try{save(false);}catch(Exception ex){error(ex);}}));shell.ribbon().inlineSettings("Print Setup",print);
        shell.ribbon().add(StudioCommand.BROWSE_RECORDS,commandButton("Browse records",this::openPrintRecords));
        shell.ribbon().task("Data","Records","Edit records",detailsScroll);
        shell.addZoom(()->preview.zoomBy(1/1.15),()->{preview.setZoom(1);previewScroll.getViewport().setViewPosition(new Point());},()->preview.zoomBy(1.15));
        shell.addSection("Properties",StudioWorkspaceShell.properties(table));
        shell.addArrangement(this::changeLayerOrder,()->arrangeSelection(false),()->arrangeSelection(true));
        shell.ribbon().retain(editor);

        shell.ribbon().add(StudioCommand.LAYERS,this::showLayers);
        layers.addTableModelListener(e->{if(floatingLayers!=null)floatingLayers.refresh();});
        table.getSelectionModel().addListSelectionListener(e->{if(floatingLayers!=null&&!e.getValueIsAdjusting())floatingLayers.refresh();});
        addHierarchyListener(e->{if((e.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)!=0&&!isShowing()&&layersWindow!=null)layersWindow.dispose();});
        JPanel footer=DeckersSwing.panel();footer.setLayout(new BorderLayout());footer.add(message);footer.add(sharedStatus,BorderLayout.NORTH);footer.add(zoomTools,BorderLayout.SOUTH);
        installFileView(shell);add(shell);add(footer,BorderLayout.SOUTH);shell.ribbon().addPropertyChangeListener("fileOpen",e->footer.setVisible(!Boolean.TRUE.equals(e.getNewValue())));
        name.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){recordHistory();}public void removeUpdate(DocumentEvent e){recordHistory();}public void changedUpdate(DocumentEvent e){recordHistory();}});
        try {template=BadgeTemplate.builtin();changedTemplate();}catch(IOException ex){template=new BadgeTemplate();changedTemplate();error(ex);}
        try{refreshLibrary();}catch(IOException ex){message.setText("Template folder unavailable. Choose Template folder to change it.");}
    }

    void clearFileHistory(){if(fileView!=null)fileView.clearRecent();}
    void resetFields(){shell.closeRecords();submissions.clear();permitNumber=null;pendingPermit=null;recordFields.values().forEach(f->f.setText(""));photo=null;personalPhotos.clear();photoFrames.clear();if(template!=null)template.allElements().forEach(e->{e.personalPhoto=null;e.photoFraming=null;});generation++;preview.repaint();refresh.restart();}
    boolean discard(){
        if(!dirty)return true;
        Object[] choices={"Save template","Discard changes","Cancel"};
        int choice=JOptionPane.showOptionDialog(this,"This badge template has unsaved changes.","Badge Cards",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,choices,choices[0]);
        if(choice==1)return true;
        if(choice==0){try{save(false);return !dirty;}catch(Exception ex){error(ex);}}
        return false;
    }
    private void changedTemplate(){template.editingBack=false;badgePage.setSelectedIndex(0);restoringHistory=true;try{name.setText(template.name);name.setCaretPosition(0);rebuildFields(false);layers.fireTableDataChanged();}finally{restoringHistory=false;}undoHistory.clear();redoHistory.clear();historyState=historyJson.toJson(template);savedState=historyState;updateHistoryButtons();dirty=false;refresh.restart();}
    private ImageFormattingToolbar.Picture selectedPicture(){if(table.getSelectedRowCount()!=1)return null;int row=table.getSelectedRow();if(row<0||row>=template.pageElements().size())return null;var e=template.pageElements().get(row);return Set.of("IMAGE","PHOTO").contains(e.type)?new ImageFormattingToolbar.Picture(e,e.width,e.height,e.transparency,e.rotation):null;}
    private void recolourPicture(){try{
        commitCell();if(selectedPicture()==null)return;int row=table.getSelectedRow();var element=template.pageElements().get(row);
        if(!element.type.equals("IMAGE"))throw new IOException("Select a template artwork image to recolour. Personal photo fields keep their original photo colours.");
        var result=ImageRecolourDialog.edit(this,element.artwork());if(result!=null)applyPictureRecolour(result);
    }catch(Exception ex){error(ex);}}
    void applyPictureRecolour(BufferedImage result)throws Exception{
        commitCell();if(selectedPicture()==null)throw new IOException("Select one artwork image.");int row=table.getSelectedRow();var element=template.pageElements().get(row);if(!element.type.equals("IMAGE"))throw new IOException("Select a template artwork image.");
        String old=element.image;element.image=java.util.Base64.getEncoder().encodeToString(ImageRecolour.png(result));
        try{template.validate();if(historyJson.toJson(template).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>BadgeTemplate.MAX_BYTES)throw new IOException("Recoloured template exceeds the 8 MB limit.");}catch(Exception ex){element.image=old;throw ex;}
        recordHistory();layers.fireTableDataChanged();table.setRowSelectionInterval(row,row);dirty=true;preview.repaint();refresh.restart();
    }
    private void applyPictureTransparency(int value){
        try{commitCell();if(selectedPicture()==null)return;int row=table.getSelectedRow();var element=template.pageElements().get(row);
            element.transparency=value;recordHistory();dirty=true;preview.repaint();refresh.restart();
        }catch(Exception ex){error(ex);}
    }
    void applyPictureRotation(double degrees){
        try{commitCell();if(selectedPicture()==null)return;int row=table.getSelectedRow();template.pageElements().get(row).rotation=ImageFormattingToolbar.normalized(degrees);template.validate();recordHistory();layers.fireTableDataChanged();table.setRowSelectionInterval(row,row);dirty=true;preview.repaint();refresh.restart();}catch(Exception ex){error(ex);}
    }
    private void applyPictureSize(ImageFormattingToolbar.Size size){
        try{commitCell();if(selectedPicture()==null)return;int row=table.getSelectedRow();var e=template.pageElements().get(row);double oldWidth=e.width,oldHeight=e.height;
            e.width=size.width();e.height=size.height();try{template.validate();}catch(Exception ex){e.width=oldWidth;e.height=oldHeight;throw ex;}
            recordHistory();layers.fireTableDataChanged();table.setRowSelectionInterval(row,row);dirty=true;preview.repaint();refresh.restart();
        }catch(Exception ex){error(ex);}
    }

    private BadgeTemplate.Element selectedText(){if(table.getSelectedRowCount()!=1)return null;int row=table.getSelectedRow();return row>=0&&row<template.pageElements().size()&&template.pageElements().get(row).type.equals("TEXT")?template.pageElements().get(row):null;}
    private void arrangeSelection(boolean distribute){
        try{
            commitCell();int[] selected=table.getSelectedRows();
            if(selected.length<(distribute?3:2))throw new IllegalArgumentException(distribute?"Select at least three objects to distribute.":"Select at least two objects to align.");
            String[] labels=distribute?new String[]{"Horizontal gaps","Vertical gaps"}:new String[]{"Left","Centre","Right","Top","Middle","Bottom"};
            String choice=(String)JOptionPane.showInputDialog(this,distribute?"Equal spacing between selected objects":"Align selected objects to their combined bounds",distribute?"Distribute":"Align",JOptionPane.PLAIN_MESSAGE,null,labels,labels[0]);
            if(choice!=null)arrangeLayers(StudioArrange.Operation.values()[java.util.Arrays.asList(labels).indexOf(choice)+(distribute?6:0)]);
        }catch(Exception ex){error(ex);}
    }

    void arrangeLayers(StudioArrange.Operation operation){
        try{
            commitCell();int[] selected=table.getSelectedRows();
            var chosen=java.util.Arrays.stream(selected).mapToObj(row->template.pageElements().get(row)).toList();
            double[][] boxes=chosen.stream().map(layer->StudioArrange.bounds(
                    new java.awt.geom.Rectangle2D.Double(layer.x,layer.y,layer.width,layer.height),layer.rotation)).toArray(double[][]::new);
            double[][] positions=StudioArrange.positions(boxes,operation,template.widthMm,template.heightMm);
            if(java.util.Arrays.deepEquals(boxes,positions))return;
            double[][] originals=chosen.stream().map(layer->new double[]{layer.x,layer.y}).toArray(double[][]::new);
            for(int i=0;i<chosen.size();i++){
                var layer=chosen.get(i);layer.x+=positions[i][0]-boxes[i][0];layer.y+=positions[i][1]-boxes[i][1];
            }
            try{template.validate();}catch(RuntimeException ex){
                for(int i=0;i<chosen.size();i++){chosen.get(i).x=originals[i][0];chosen.get(i).y=originals[i][1];}
                throw ex;
            }
            recordHistory();refreshLayerRows(selected);dirty=true;preview.repaint();refresh.restart();
        }catch(Exception ex){error(ex);}
    }

    private TextFormattingToolbar.Style selectedTextStyle(){var e=selectedText();return e==null?null:new TextFormattingToolbar.Style(e.font,e.fontMm*72/25.4,e.bold,e.italic,e.underline,e.strike,e.color,e.textOutline,e.textOutlineColor,e.textOutlineWidth,e.textAlignment);}
    private void applyTextStyle(TextFormattingToolbar.Style style){try{commitCell();var e=selectedText();if(e==null)return;int selected=table.getSelectedRow();e.font=style.font();e.fontMm=style.size()*25.4/72;e.bold=style.bold();e.italic=style.italic();e.underline=style.underline();e.strike=style.strike();e.color=style.color();e.textOutline=style.outline();e.textOutlineColor=style.outlineColor();e.textOutlineWidth=style.outlineWidth();e.textAlignment=style.alignment();template.validate();recordHistory();layers.fireTableDataChanged();if(selected>=0&&selected<table.getRowCount())table.setRowSelectionInterval(selected,selected);dirty=true;preview.repaint();refresh.restart();}catch(Exception ex){error(ex);}}
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
            try{template=historyJson.fromJson(historyState,BadgeTemplate.class);template.editingBack=badgePage.getSelectedIndex()==1;template.allElements().forEach(e->{e.personalPhoto=personalPhotos.get(e.photoId);e.photoFraming=photoFrames.get(e.photoId);});name.setText(template.name);rebuildFields(true);layers.fireTableDataChanged();
                if(selected>=0 && !template.pageElements().isEmpty()){int row=Math.min(selected,template.pageElements().size()-1);table.setRowSelectionInterval(row,row);}
            }finally{restoringHistory=false;}
            dirty=!historyState.equals(savedState);updateHistoryButtons();refresh.restart();preview.repaint();
        }catch(Exception ex){error(ex);}
    }
    private void commitCell() throws IOException {if(!preview.finishText(true))throw new IOException("Finish editing the text.");if(table.isEditing()&&!table.getCellEditor().stopCellEditing())throw new IOException("Finish editing the current design cell.");}
    private void addShape(String shape) throws IOException {
        commitCell();if(template.pageElements().size()>=64)throw new IOException("A template supports up to 64 elements.");
        BadgeTemplate.Element e=new BadgeTemplate.Element();e.label=shape;e.type=switch(shape){case "Ellipse"->"ELLIPSE";case "Triangle"->"TRIANGLE";case "Line"->"LINE";default->"RECT";};
        e.x=5;e.y=5;e.width=Math.min(25,template.widthMm);e.height="LINE".equals(e.type)?.5:Math.min(15,template.heightMm);e.color=String.format("#%06x",DeckersPalette.PURPLE.getRGB()&0xffffff);
        template.pageElements().add(e);layers.fireTableDataChanged();int row=template.pageElements().size()-1;table.setRowSelectionInterval(row,row);dirty=true;preview.repaint();preview.requestFocusInWindow();
        recordHistory();
    }
    java.util.List<publishing.Publication.Element> layerEntries(){
        if(template==null)return java.util.List.of();
        return template.pageElements().stream().map(e->{var row=new publishing.Publication.Element();row.type=e.type.equals("PHOTO")?"IMAGE":e.type;row.text=e.label==null||e.label.isBlank()?(e.text.isBlank()?e.field:e.text):e.label;row.hidden=e.hidden;return row;}).toList();
    }
    StudioLayersPanel createLayersPanel(Runnable close){
        return new StudioLayersPanel(this::layerEntries,table::getSelectedRow,index->{if(index>=0&&index<table.getRowCount())table.setRowSelectionInterval(index,index);},index->{try{toggleLayerVisibility(index);}catch(Exception ex){error(ex);}},move->{try{reorderLayer(move==StudioLayerOrder.Move.FORWARD?1:-1);}catch(Exception ex){error(ex);}},()->{try{commitCell();preview.deleteSelection();}catch(Exception ex){error(ex);}},close);
    }
    void toggleLayerVisibility(int index)throws IOException{
        commitCell();if(template==null||index<0||index>=template.pageElements().size())return;
        var element=template.pageElements().get(index);element.hidden=!element.hidden;recordHistory();layers.fireTableDataChanged();table.setRowSelectionInterval(index,index);dirty=true;preview.repaint();refresh.restart();
    }
    private void showLayers(){
        if(GraphicsEnvironment.isHeadless())return;
        if(layersWindow!=null&&layersWindow.isDisplayable()){floatingLayers.refresh();layersWindow.setVisible(true);layersWindow.toFront();return;}
        Window owner=SwingUtilities.getWindowAncestor(this);layersWindow=new JDialog(owner,"Layers",Dialog.ModalityType.MODELESS);
        layersWindow.setUndecorated(true);
        if(layersWindow.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT))layersWindow.setBackground(new Color(0,0,0,0));
        floatingLayers=createLayersPanel(()->layersWindow.dispose());
        layersWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);layersWindow.setContentPane(floatingLayers);
        layersWindow.getRootPane().registerKeyboardAction(e->layersWindow.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if(owner!=null){Rectangle screen=owner.getGraphicsConfiguration().getBounds();Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(owner.getGraphicsConfiguration());usable=new Rectangle(screen.x+insets.left,screen.y+insets.top,screen.width-insets.left-insets.right,screen.height-insets.top-insets.bottom);}
        layersWindow.setSize(Math.min(280,usable.width-32),Math.min(370,usable.height-48));layersWindow.setMinimumSize(new Dimension(260,220));
        Point anchor=isShowing()?getLocationOnScreen():new Point(usable.x+16,usable.y+16);
        layersWindow.setLocation(Math.max(usable.x,Math.min(anchor.x+24,usable.x+usable.width-layersWindow.getWidth())),Math.max(usable.y,Math.min(anchor.y+120,usable.y+usable.height-layersWindow.getHeight())));
        layersWindow.setVisible(true);
    }

    private void updateLayoutSize(){
        boolean available=template!=null;layoutWidth.setEnabled(available);layoutHeight.setEnabled(available);
        if(available){double factor=layoutUnits.getSelectedIndex()==0?25.4:1;layoutWidth.setValue(template.widthMm/factor);layoutHeight.setValue(template.heightMm/factor);}
    }
    void resizeTemplate(double width,double height,boolean scale)throws IOException{
        commitCell();if(template==null)return;BadgeTemplate next=template.snapshot();double sx=width/template.widthMm,sy=height/template.heightMm;
        for(int i=0;i<next.allElements().size();i++){var e=next.allElements().get(i);var old=template.allElements().get(i);e.personalPhoto=old.personalPhoto;e.photoFraming=old.photoFraming;if(scale){e.x*=sx;e.y*=sy;e.width*=sx;e.height*=sy;e.fontMm*=Math.min(sx,sy);}}
        next.widthMm=width;next.heightMm=height;next.validate();template=next;recordHistory();layers.fireTableDataChanged();dirty=true;preview.revalidate();preview.repaint();refresh.restart();
    }
    private void reorderLayer(int direction)throws IOException{reorderLayers(direction>0?StudioLayerOrder.Move.FORWARD:StudioLayerOrder.Move.BACKWARD);}
    private void changeLayerOrder(StudioLayerOrder.Move move){try{reorderLayers(move);}catch(Exception ex){error(ex);}}
    private void reorderLayers(StudioLayerOrder.Move move)throws IOException{
        commitCell();if(template==null)return;var result=StudioLayerOrder.move(template.pageElements(),table.getSelectedRows(),move,e->false);if(!result.changed())return;
        refreshLayerRows(result.selection());dirty=true;recordHistory();preview.repaint();refresh.restart();
    }
    private void refreshLayerRows(int[] selected){
        updatingLayerRows=true;
        try{layers.fireTableDataChanged();for(int row:selected)table.addRowSelectionInterval(row,row);}
        finally{updatingLayerRows=false;}
        preview.selectIndices(selected);refreshLayerFormatting.run();
    }
    private Map<String,String> values(){Map<String,String> result=new LinkedHashMap<>();recordFields.forEach((key,field)->{if(!field.getText().isBlank())result.put(key,field.getText());});if(permitNumber!=null)result.put(OreallaRidersPermit.NUMBER,permitNumber);return result;}
    private void rebuildFields(boolean preserve){
        Map<String,String> old=preserve?values():Map.of();details.removeAll();recordFields.clear();details.add(DeckersSwing.metaLabel("Template name"));details.add(name);
        boolean permit=OreallaRidersPermit.enabled(template);
        if(!permit){
            JCheckBox keep=new JCheckBox("Save print records (off by default)",template.savePrintRecords);
            keep.addActionListener(e->{template.savePrintRecords=keep.isSelected();dirty=true;recordHistory();});details.add(keep);
            addButton(details,"Enter card details…",this::editRecordDetails);
        }
        addButton(details,permit?"Permit records (admin)…":"Print records (admin)…",this::openPrintRecords);
        if(template.savePrintRecords){
            addButton(details,permit?"Create / rotate website invitation…":"Create / rotate organization link…",submissions::link);
            addButton(details,permit?"Copy website invitation…":"Copy organization link…",submissions::copyLink);
            addButton(details,"Reset organization access code…",submissions::resetCode);
            addButton(details,"Revoke organization link…",submissions::revoke);
        }
        if(template.savePrintRecords&&!permit){
            addButton(details,"Submission queue…",()->submissions.open(0));
            addButton(details,"Release current review",submissions::release);
            addButton(details,"Reject current submission…",submissions::reject);
        }
        for(BadgeTemplate.Element element:template.allElements()){if(!"TEXT".equals(element.type)||element.field.isEmpty()||recordFields.containsKey(element.field))continue;
            JTextField field=new JTextField(old.getOrDefault(element.field,""),8);DeckersSwing.styleField(field);field.setEditable(!permit);recordFields.put(element.field,field);details.add(DeckersSwing.metaLabel(element.label));details.add(field);
            field.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){refresh.restart();}public void removeUpdate(DocumentEvent e){refresh.restart();}public void changedUpdate(DocumentEvent e){refresh.restart();}});
        }
        addButton(details,permit?"Upload photo for new permit…":"Choose / replace selected photo",this::choosePhoto);
        addButton(details,permit?"Adjust permit photo for printing…":"Edit photo / crop / background…",this::frameSelectedPhoto);
        addButton(details,"New person / clear details",()->{if(!savingPermit)resetFields();});details.setPreferredSize(new Dimension(280,Math.max(220,((details.getComponentCount()+1)/2)*44)));details.revalidate();details.repaint();
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
        var files=shared!=null&&shared.smartDrive?java.util.List.<Path>of():library.templates();if(shared!=null)files=files.stream().filter(shared.stores::visible).toList();updatingLibrary=true;
        try{libraryTemplates.removeAllItems();for(Path file:files)libraryTemplates.addItem(file);libraryTemplates.setSelectedItem(library.contains(currentFile)?currentFile:null);libraryTemplates.setToolTipText(library.folder().toString());}finally{updatingLibrary=false;}
        firePropertyChange("sharedLibraryChanged",false,true);
    }
    private void loadTemplate(Path file)throws IOException{
        String fingerprint=BadgeTemplateLibrary.fingerprint(file);BadgeTemplate loaded=BadgeTemplate.load(file);if(!fingerprint.equals(BadgeTemplateLibrary.fingerprint(file)))throw new IOException("This template changed while opening. Wait for file synchronization to finish, then reopen it.");template=loaded;currentFile=library.contains(file)?file:null;currentFingerprint=currentFile==null?null:fingerprint;resetFields();changedTemplate();dirty=currentFile==null;
        message.setText(currentFile==null?"Imported design. Save template to add it to your library.":"Opened library template: "+file.getFileName());
    }

    void openDocument(Path file)throws IOException{
        String fingerprint=BadgeTemplateLibrary.fingerprint(file);
        loadTemplate(file);
        if(!fingerprint.equals(BadgeTemplateLibrary.fingerprint(file)))
            throw new IOException("The design changed while opening. Reopen the file.");
        currentFile=file.toAbsolutePath().normalize();
        currentFingerprint=fingerprint;
        dirty=false;
        message.setText("Opened: "+currentFile.getFileName());
    }

    private void renderPreview(){
        preview.repaint();
    }

    boolean chooseTemplate() {
        if(sharedActive)try {
            var selected=SmartDriveTemplatePicker.choose(this,"BADGE");
            if(selected.enabled()) {
                if(selected.current())return true;
                if(selected.file()==null || !discard())return false;
                openDocument(selected.file());template.smartDrive=selected.identity();return true;
            }
        }catch(Exception ex){error(ex);return false;}
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
    private void installFileView(StudioWorkspaceShell shell){
        fileView=shell.installFile(new StudioFileView.Actions(){
            public StudioFileView.Info info(){return new StudioFileView.Info("Badge Cards",currentFile!=null&&(shared==null||!shared.contains(currentFile))?currentFile.getFileName().toString().replaceFirst("(?i)\\.sstbadge$",""):template==null?"New badge":template.name,".sstbadge",currentFile==null?"Unsaved draft":shared!=null&&shared.contains(currentFile)?"Shared template library":currentFile.getParent().toString(),template==null?"":String.format(java.util.Locale.ROOT,"%.3f × %.3f inches",template.widthMm/25.4,template.heightMm/25.4),2,dirty,sharedSave||syncing,shared!=null&&shared.online,currentFile!=null,currentFile!=null&&(shared==null||!shared.contains(currentFile))?currentFile:null);}
            public void create(){dirty=false;newTemplate();shell.ribbon().select("Home");}
            public void open(Path path)throws Exception{BadgeTemplate before=template;boolean changed=dirty;dirty=false;try{if(path==null)chooseTemplate();else loadTemplate(path);}finally{if(template==before)dirty=changed;}shell.ribbon().select("Home");}
            public void save()throws Exception{BadgeCardsPanel.this.save(false);}
            public void saveAs(String name,Path folder,boolean store)throws Exception{saveAsFile(name,folder,store);}
            public void rename(String name)throws Exception{renameFile(name);}
            public void importArtwork()throws Exception{insertImageFile();}
            public String[] exportFormats(){return new String[]{"PDF document (.pdf)","PNG image (.png)","Portable template (.sstbadge)"};}
            public void export(String format)throws Exception{if(format.startsWith("Portable"))BadgeCardsPanel.this.save(true);else BadgeCardsPanel.this.export(format.startsWith("PDF"));}
            public void print()throws Exception{printCard();}
            public void printSetup(){shell.ribbon().select("Print Setup");}
            public void close(){dirty=false;newTemplate();firePropertyChange("closeWorkspace",false,true);}
        });
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control shift S"),"saveAsFile");getActionMap().put("saveAsFile",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){shell.ribbon().select("File");fileView.show("save-as");}});
    }
    void saveAsFile(String requested,Path folder,boolean store)throws Exception{
        commitCell();if(sharedSave||syncing)throw new IOException("Wait for the current operation.");String clean=StudioFileNames.clean(requested,".sstbadge");BadgeTemplate snapshot=template.snapshot();snapshot.name=clean;snapshot.smartDrive=null;
        if(store){if(shared==null||!shared.online)throw new IOException("Reconnect to save shared templates.");shared.requireUniqueName(clean,null);var active=shared;long session=generation;sharedSave=true;new SwingWorker<Path,Void>(){protected Path doInBackground()throws Exception{return active.save(snapshot,null,"");}protected void done(){sharedSave=false;try{Path saved=get();if(session!=generation)return;currentFile=saved;currentFingerprint=BadgeTemplateLibrary.fingerprint(saved);template.name=clean;name.setText(clean);recordHistory();savedState=historyJson.toJson(snapshot);dirty=!historyState.equals(savedState);refreshLibrary();}catch(Exception ex){error(ex);}}}.execute();}
        else{Path target=StudioFileNames.target(folder,clean,".sstbadge");if(currentFile!=null&&target.equals(currentFile.toAbsolutePath().normalize()))throw new IOException("Choose a different name for the new copy.");String expected=StudioDocumentSave.observe(target);if(!replace(target))return;snapshot.smartDrive=publishing.StudioDriveIdentity.forNewFile(target,"BADGE");String savedHash=StudioDocumentSave.write(target,expected,snapshot::save);currentFile=target;currentFingerprint=savedHash;template.name=clean;template.smartDrive=snapshot.smartDrive;name.setText(clean);recordHistory();savedState=historyState;dirty=false;refreshLibrary();}
    }
    void renameFile(String requested)throws Exception{
        commitCell();if(sharedSave||syncing)throw new IOException("Wait for the current operation.");String clean=StudioFileNames.clean(requested,".sstbadge");
        if(shared!=null&&shared.contains(currentFile)){
            if(!shared.online)throw new IOException("Reconnect to rename shared templates.");shared.requireUniqueName(clean,currentFile);
            BadgeTemplate saved=BadgeTemplate.load(currentFile);saved.name=clean;
            var active=shared;Path original=currentFile;String expected=currentFingerprint;long session=generation;sharedSave=true;
            new SwingWorker<Path,Void>(){
                protected Path doInBackground()throws Exception{return active.save(saved,original,expected);}
                protected void done(){sharedSave=false;try{Path target=get();if(session!=generation)return;
                    currentFile=target;currentFingerprint=BadgeTemplateLibrary.fingerprint(target);template.name=clean;name.setText(clean);recordHistory();
                    savedState=historyJson.toJson(saved);dirty=!historyState.equals(savedState);refreshLibrary();message.setText("Renamed template: "+clean);
                }catch(Exception ex){error(ex);}}
            }.execute();return;
        }
        BadgeTemplate saved=BadgeTemplate.load(currentFile);saved.name=clean;
        Path target=StudioFileNames.renameWithContent(currentFile,clean,".sstbadge",currentFingerprint,saved::save);
        currentFile=target;currentFingerprint=BadgeTemplateLibrary.fingerprint(target);template.name=clean;name.setText(clean);recordHistory();
        savedState=historyJson.toJson(saved);dirty=!historyState.equals(savedState);refreshLibrary();message.setText("Renamed template: "+clean);
    }
    private void save(boolean export)throws Exception{
        commitCell();template.name=name.getText().trim();template.validate();
        boolean drive=shared!=null&&shared.smartDrive;
        if(drive&&!export&&currentFile!=null&&shared.contains(currentFile))throw new IOException("This is a legacy cached template. Reopen the design from SmartDrive or use Save As to preserve your changes in a new file.");
        boolean chooseDestination=export||(drive&&currentFile==null);
        if(shared!=null&&!drive&&!shared.online&&(currentFile==null||shared.contains(currentFile))&&!export)throw new IOException("Offline mode: changes cannot be saved. Reconnect to the store server and refresh first.");
        if(shared!=null&&!drive&&!export&&(currentFile==null||shared.contains(currentFile))){
            if(!shared.online)throw new IOException("Offline mode: changes cannot be saved. Reconnect to the store server and refresh first.");
            if(sharedSave||syncing)throw new IOException("Wait for library synchronization to finish.");
            recordHistory();String saving=historyState;BadgeTemplate snapshot=template.snapshot();Path previous=currentFile;String expected=currentFingerprint;long session=generation;sharedSave=true;
            new SwingWorker<Path,Void>(){protected Path doInBackground()throws Exception{return shared.save(snapshot,previous,expected);}
                protected void done(){sharedSave=false;sharedStatus.setText(shared.status);try{Path file=get();if(session!=generation)return;currentFile=file;currentFingerprint=BadgeTemplateLibrary.fingerprint(file);savedState=saving;dirty=!historyState.equals(savedState);refreshLibrary();message.setText("Saved to server; OneDrive backup queued.");}catch(Exception ex){error(ex.getCause() instanceof Exception cause?cause:ex);}}}.execute();return;
        }
        Path target=currentFile!=null&&(shared==null||!shared.contains(currentFile))?currentFile:library.fileFor(template.name);
        String observed=null;
        if(chooseDestination){JFileChooser chooser=chooser("SmartStudio badge template","sstbadge");chooser.setSelectedFile(new java.io.File(StudioFileNames.clean(template.name,".sstbadge")+".sstbadge"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;target=extension(chooser.getSelectedFile().toPath(),"sstbadge");observed=StudioDocumentSave.observe(target);if(!replace(target))return;}
        else if(currentFile==null){observed=StudioDocumentSave.observe(target);if(!replace(target))return;}
        else if(!Files.exists(target)||!BadgeTemplateLibrary.fingerprint(target).equals(currentFingerprint))throw new IOException("This template changed on disk. Export your draft to a new file, then reopen the updated library template before editing it.");
        BadgeTemplate writing=template;
        if(chooseDestination){writing=template.snapshot();if(currentFile==null||!target.toAbsolutePath().normalize().equals(currentFile.toAbsolutePath().normalize()))writing.smartDrive=publishing.StudioDriveIdentity.forNewFile(target,"BADGE");}
        StudioDocumentSave.write(target,!export&&currentFile!=null?currentFingerprint:observed,writing::save);if(!export)template.smartDrive=writing.smartDrive;recordHistory();if(!export){currentFile=target;currentFingerprint=BadgeTemplateLibrary.fingerprint(target);savedState=historyState;dirty=false;refreshLibrary();}
        message.setText((export?"Exported template: ":"Saved in template folder: ")+target.getFileName());message.setToolTipText(target.toString());
    }
    private void configureTextBinding(boolean selected)throws Exception{
        commitCell();int row=table.getSelectedRow();
        if(selected&&(row<0||!"TEXT".equals(template.pageElements().get(row).type)))throw new IOException("Select a text layer to bind first.");
        var element=selected?template.pageElements().get(row):null;
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
        if(row>=0&&!"TEXT".equals(template.pageElements().get(row).type))throw new IllegalArgumentException("Select a text layer.");
        if(row<0&&template.pageElements().size()>=64)throw new IllegalArgumentException("A template supports up to 64 elements.");
        BadgeTemplate.Element element=row<0?new BadgeTemplate.Element():template.pageElements().get(row);
        element.label=label.trim();element.field=binding;element.text="";
        if(row<0){element.width=Math.min(30,template.widthMm);element.height=Math.min(6,template.heightMm);template.pageElements().add(element);row=template.pageElements().size()-1;}
        rebuildFields(true);layers.fireTableDataChanged();table.setRowSelectionInterval(row,row);dirty=true;recordHistory();refresh.restart();
    }
    private String recordFieldLabel(String binding){
        return template.allElements().stream().filter(e->"TEXT".equals(e.type)&&binding.equals(e.field)).map(e->e.label).findFirst().orElse(binding);
    }
    private void editRecordDetails(){
        JPanel form=new JPanel(new GridLayout(0,2,8,8));Map<String,JTextField> edits=new LinkedHashMap<>();
        recordFields.forEach((key,value)->{form.add(new JLabel(recordFieldLabel(key)));JTextField edit=new JTextField(value.getText(),20);edits.put(key,edit);form.add(edit);});
        if(edits.isEmpty()){JOptionPane.showMessageDialog(this,"Bind text layers to Data fields such as fullName and idNumber to enter card details.");return;}
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(form),"Card details",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)edits.forEach((key,value)->recordFields.get(key).setText(value.getText()));
    }
    private com.google.gson.JsonObject recordPayload()throws Exception{
        var payload=new com.google.gson.JsonObject();payload.add("values",historyJson.toJsonTree(values()));var photos=new com.google.gson.JsonObject();
        for(var e:template.allElements())if("PHOTO".equals(e.type)&&e.personalPhoto!=null){
            var frame=e.photoFraming==null?PhotoFraming.initial(e.personalPhoto):e.photoFraming;
            photos.add(e.photoId,recordPhoto(frame));
        }
        payload.add("photos",photos);return payload;
    }
    private static com.google.gson.JsonObject recordPhoto(PhotoFraming frame)throws IOException{
        var item=new com.google.gson.JsonObject();
        var bytes=new java.io.ByteArrayOutputStream();
        ImageIO.write(frame.source(),"png",bytes);
        item.addProperty("image",java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
        item.addProperty("zoom",frame.zoom());item.addProperty("offsetX",frame.offsetX());
        item.addProperty("offsetY",frame.offsetY());item.addProperty("turns",frame.turns());
        return item;
    }
    private void openPrintRecords()throws Exception{
        if(shell.recordsOpen()){shell.closeRecords();return;}
        if(shared==null)throw new IOException("Connect to the store server to open print records.");
        Path target=currentFile;long session=generation;var active=shared;var identity=template.smartDrive;
        if(target==null)throw new IOException("Open a shared badge template to view its records.");
        commitCell();
        var design=template.snapshot();
        var columns=new LinkedHashMap<String,String>();
        if(OreallaRidersPermit.enabled(template)){
            columns.put(OreallaRidersPermit.NUMBER,"Permit number");
            for(String key:java.util.List.of("name","village","issueDate","expiryDate"))
                if(recordFields.containsKey(key))columns.put(key,recordFieldLabel(key));
        }
        recordFields.keySet().forEach(key->columns.put(key,recordFieldLabel(key)));
        columns.put("_savedAt","Saved at");
        var browser=new StudioRecordBrowser<String>(design.name,columns,row->{
            var body=new com.google.gson.JsonObject();
            body.addProperty("recordId",row.id());
            var previewDesign=design.snapshot();
            var record=readPrintRecord(active.record(target,identity,"RECORD_GET",body),previewDesign);
            BufferedImage image=null;
            try{
                for(var element:previewDesign.allElements()){
                    var frame=record.photos().get(element.photoId);
                    if("PHOTO".equals(element.type)&&frame!=null){
                        element.personalPhoto=frame.source();
                        element.photoFraming=frame;
                    }
                }
                image=previewDesign.renderSide(false,record.values(),null,150);
            }catch(IOException ignored){/* The record itself can still be loaded. */}
            return new StudioRecordBrowser.Details(image,()->{
                shell.closeRecords();
                if(session==generation&&active==shared&&java.util.Objects.equals(target,currentFile))applyPrintRecord(record);
            });
        },shell::closeRecords);
        shell.showRecords(browser,()->active.printRecords(target,identity));
    }
    void loadPrintRecord(com.google.gson.JsonObject payload)throws Exception{
        applyPrintRecord(readPrintRecord(payload,template));
    }
    private record PrintRecord(Map<String,String> values,Map<String,PhotoFraming> photos){}
    private static PrintRecord readPrintRecord(com.google.gson.JsonObject payload,BadgeTemplate design)throws IOException{
        var values=new LinkedHashMap<String,String>();
        payload.getAsJsonObject("values").entrySet().forEach(entry->values.put(entry.getKey(),entry.getValue().getAsString()));
        var frames=new LinkedHashMap<String,PhotoFraming>();
        var photos=payload.getAsJsonObject("photos");
        for(var element:design.allElements())if("PHOTO".equals(element.type)&&photos.has(element.photoId))
            frames.put(element.photoId,decodeRecordPhoto(photos.getAsJsonObject(element.photoId)));
        return new PrintRecord(values,frames);
    }
    private void applyPrintRecord(PrintRecord record){
        resetFields();
        if(OreallaRidersPermit.enabled(template))permitNumber=record.values().get(OreallaRidersPermit.NUMBER);
        recordFields.forEach((key,field)->field.setText(record.values().getOrDefault(key,"")));
        for(var element:template.allElements())if("PHOTO".equals(element.type)){
            var frame=record.photos().get(element.photoId);
            if(frame!=null)usePhotoFrame(element,frame);
        }
        refresh.restart();message.setText(permitNumber==null?"Record loaded into current format. Review details, then Print."
                :"Permit "+permitNumber+" loaded. Review both sides before printing.");
    }
    static PhotoFraming decodeRecordPhoto(com.google.gson.JsonObject item)throws IOException{
        var image=BadgeTemplate.decode(item.get("image").getAsString());
        var frame=new PhotoFraming(image,item.get("zoom").getAsDouble(),item.get("offsetX").getAsDouble(),item.get("offsetY").getAsDouble(),item.get("turns").getAsInt(),
                item.has("background")?new Color(item.get("background").getAsInt()):null,
                item.has("outline")?new Color(item.get("outline").getAsInt()):null);
        if(!item.has("adjustments"))return frame;
        var values=item.getAsJsonObject("adjustments");
        return frame.adjusted(new PhotoAdjustments(photoAdjustment(values,"brightness"),photoAdjustment(values,"contrast"),
                photoAdjustment(values,"saturation"),photoAdjustment(values,"warmth")));
    }
    private static int photoAdjustment(com.google.gson.JsonObject values,String key){
        return values.has(key)?values.get(key).getAsInt():0;
    }
    private BadgeTemplate.Element permitPhotoSlot()throws IOException{
        return template.allElements().stream().filter(e->"PHOTO".equals(e.type)).findFirst()
                .orElseThrow(()->new IOException("This permit needs a photo field."));
    }
    private void choosePhoto()throws Exception{
        boolean permit=OreallaRidersPermit.enabled(template);
        if(permit){
            if(savingPermit)return;
            if(shared==null||currentFile==null)throw new IOException("Open the shared Orealla permit template while connected to the store server.");
            if(pendingPermit!=null){savePermit();return;}
        }
        int row=table.getSelectedRow();
        BadgeTemplate.Element slot;
        if(permit){
            slot=permitPhotoSlot();
        }else{
            if(row<0||!"PHOTO".equals(template.pageElements().get(row).type)){
                var slots=template.pageElements().stream().filter(e->"PHOTO".equals(e.type)).toList();
                if(slots.size()!=1)throw new IOException("Select the photo field you want to fill on the canvas or in Layers first.");
                row=template.pageElements().indexOf(slots.get(0));table.setRowSelectionInterval(row,row);
            }
            slot=template.pageElements().get(row);
        }
        BufferedImage loaded=StudioPhotoPicker.photo(this,()->{
            JFileChooser chooser=chooser("PNG and JPEG photos","png","jpg","jpeg");if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return null;
            return StudioImageFile.loadPhoto(chooser.getSelectedFile().toPath()).image();
        });
        if(loaded==null)return;
        PhotoFraming framed=PhotoFramingDialog.edit(this,slot.stylePhoto(PhotoFraming.initial(loaded).fit(slot.width,slot.height)),slot.width,slot.height);
        if(framed==null)return;
        if(permit){collectPermit(slot,framed);return;}
        if(!submissions.reviewing())recordFields.values().forEach(field->field.setText(""));
        applyPhotoFrame(slot,framed);editRecordDetails();
    }
    private void collectPermit(BadgeTemplate.Element slot,PhotoFraming frame)throws IOException{
        JPanel form=new JPanel(new GridLayout(0,2,8,8));
        Map<String,JTextField> edits=new LinkedHashMap<>();
        recordFields.forEach((key,unused)->{
            form.add(new JLabel(recordFieldLabel(key)));JTextField input=new JTextField(20);
            edits.put(key,input);form.add(input);
        });
        while(JOptionPane.showConfirmDialog(this,new JScrollPane(form),"New permit details",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
            if(edits.values().stream().anyMatch(input->input.getText().isBlank()||input.getText().strip().length()>1000)){
                JOptionPane.showMessageDialog(this,"Complete every permit field using up to 1,000 characters per field.");
                continue;
            }
            var data=new com.google.gson.JsonObject();
            edits.forEach((key,input)->data.addProperty(key,input.getText().strip()));
            var photos=new com.google.gson.JsonObject();
            BufferedImage image=permitPhoto(frame.source());
            var savedFrame=new PhotoFraming(image,frame.zoom(),frame.offsetX(),frame.offsetY(),frame.turns(),frame.background(),frame.outline());
            var item=recordPhoto(savedFrame);
            if(!frame.adjustments().equals(PhotoAdjustments.NONE))item.add("adjustments",historyJson.toJsonTree(frame.adjustments()));
            if(savedFrame.background()!=null)item.addProperty("background",savedFrame.background().getRGB()&0xffffff);
            if(savedFrame.outline()!=null)item.addProperty("outline",savedFrame.outline().getRGB()&0xffffff);
            photos.add(slot.photoId,item);
            var payload=new com.google.gson.JsonObject();payload.add("values",data);payload.add("photos",photos);
            pendingPermit=new com.google.gson.JsonObject();
            pendingPermit.addProperty("recordId",java.util.UUID.randomUUID().toString());
            pendingPermit.add("payload",payload);
            savePermit();return;
        }
    }
    private static BufferedImage permitPhoto(BufferedImage source){
        double scale=1400.0/Math.max(source.getWidth(),source.getHeight());
        if(scale>=1)return source;
        var image=new BufferedImage(Math.max(1,(int)Math.round(source.getWidth()*scale)),
                Math.max(1,(int)Math.round(source.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics();
        try{
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source,0,0,image.getWidth(),image.getHeight(),null);
        }finally{graphics.dispose();}
        return image;
    }
    private void savePermit(){
        var request=pendingPermit;var active=shared;Path target=currentFile;long session=generation;var identity=template.smartDrive;
        savingPermit=true;
        message.setText("Saving permit and assigning its QR number…");
        new SwingWorker<com.google.gson.JsonObject,Void>(){
            protected com.google.gson.JsonObject doInBackground()throws Exception{return active.record(target,identity,"RECORD_CREATE",request);}
            protected void done(){
                savingPermit=false;
                if(session!=generation||active!=shared||!java.util.Objects.equals(target,currentFile))return;
                try{
                    String number=get().get(OreallaRidersPermit.NUMBER).getAsString();
                    var payload=request.getAsJsonObject("payload").deepCopy();
                    payload.getAsJsonObject("values").addProperty(OreallaRidersPermit.NUMBER,number);
                    loadPrintRecord(payload);
                    badgePage.setSelectedIndex(0);
                }catch(Exception ex){
                    message.setText("Permit save unconfirmed. Choose Upload photo for new permit to retry.");
                    error(ex);
                }
            }
        }.execute();
    }
    private void frameSelectedPhoto()throws Exception{
        if(OreallaRidersPermit.enabled(template)){
            if(savingPermit||pendingPermit!=null)throw new IOException("Finish saving the permit before adjusting its photo.");
            var slot=permitPhotoSlot();
            if(slot.personalPhoto==null){choosePhoto();return;}
            var current=slot.photoFraming==null?PhotoFraming.initial(slot.personalPhoto):slot.photoFraming;
            var framed=PhotoFramingDialog.edit(this,current,slot.width,slot.height);
            if(framed!=null){
                usePhotoFrame(slot,framed);refresh.restart();preview.repaint();
                message.setText("Photo updated for this print. The saved record is unchanged.");
            }
            return;
        }
        commitCell();int row=table.getSelectedRow();if(row<0||!"PHOTO".equals(template.pageElements().get(row).type))throw new IOException("Select a photo field first.");
        BadgeTemplate.Element slot=template.pageElements().get(row);if(slot.personalPhoto==null){choosePhoto();return;}
        PhotoFraming current=slot.photoFraming==null?PhotoFraming.initial(slot.personalPhoto):slot.photoFraming;
        PhotoFraming framed=PhotoFramingDialog.edit(this,slot.stylePhoto(current),slot.width,slot.height);if(framed!=null)applyPhotoFrame(slot,framed);
    }
    private void applyPhotoFrame(BadgeTemplate.Element slot,PhotoFraming framed){
        slot.photoBackgroundEnabled=framed.background()!=null;slot.photoOutlineEnabled=framed.outline()!=null;
        if(framed.background()!=null)slot.photoBackgroundColor=String.format("#%06x",framed.background().getRGB()&0xffffff);
        if(framed.outline()!=null)slot.photoOutlineColor=String.format("#%06x",framed.outline().getRGB()&0xffffff);
        usePhotoFrame(slot,framed);refresh.restart();preview.repaint();
        recordHistory();
    }
    private void usePhotoFrame(BadgeTemplate.Element slot,PhotoFraming framed){
        slot.personalPhoto=framed.source();slot.photoFraming=framed;
        personalPhotos.put(slot.photoId,slot.personalPhoto);photoFrames.put(slot.photoId,framed);
    }
    private void insertImageFile(){
        JFileChooser file=chooser("PNG and JPEG images","png","jpg","jpeg");if(file.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        loadImageLayer(()->StudioImageFile.load(file.getSelectedFile().toPath()));
    }

    private void copyLayers(){try{commitCell();int[] rows=preview.selectedIndices();if(rows.length>0)StudioLayerClipboard.put(StudioLayerClipboard.badge(template,rows));}catch(Exception ex){error(ex);}}
    private void pasteLayers(){try{
        commitCell();var data=StudioLayerClipboard.contents();if(data==null)return;int first=template.pageElements().size();
        if(data.isDataFlavorSupported(StudioLayerClipboard.FLAVOR))template=StudioLayerClipboard.paste(template,(StudioLayerClipboard.Payload)data.getTransferData(StudioLayerClipboard.FLAVOR));
        else if(StudioImageTransfer.supports(data)){pasteImageLayer();return;}
        else if(data.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)){
            BadgeTemplate next=template.snapshot();var e=new BadgeTemplate.Element();e.label="Text";e.text=(String)data.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);e.width=Math.min(30,next.widthMm);e.height=Math.min(6,next.heightMm);next.pageElements().add(e);next.validate();template.pageElements().add(e);
        }else throw new IllegalArgumentException("Copy layers, text, or an image first.");
        rebuildFields(true);layers.fireTableDataChanged();table.setRowSelectionInterval(first,template.pageElements().size()-1);dirty=true;recordHistory();refresh.restart();preview.requestFocusInWindow();
    }catch(Exception ex){error(ex);}}
    private void pasteImageLayer(){try{loadImageLayer(CanvasImageInsert.clipboard());}catch(Exception ex){error(ex);}}
    private void loadImageLayer(java.util.concurrent.Callable<StudioImageFile.Loaded> loader){
        BadgeTemplate target=template;long session=generation;boolean backPage=template.editingBack;
        CanvasImageInsert.load(loader,image->{if(template!=target||generation!=session||template.editingBack!=backPage)return;try{insertImageLayer(image);}catch(Exception ex){error(ex);}},this::error);
    }
    private void generateCode(boolean qr)throws Exception{
        var code=BarcodeEditor.chooseBadge(this,qr);
        if(code!=null)insertImageLayer(BadgeEditableArtwork.code(code));
    }

    private void insertWordArt()throws Exception{
        commitCell();
        var object=publishing.WordArtEditor.choose(this,null);
        if(object!=null)insertImageLayer(BadgeEditableArtwork.wordArt(object));
    }

    private void insertTable()throws Exception{
        commitCell();
        var grid=StudioTables.create(3,2,Math.min(60,template.widthMm*.8),Math.min(24,template.heightMm*.8));
        grid.cells.forEach(cell->cell.fontSize=7);
        var edited=TableEditor.choose(this,grid,true);
        if(edited!=null)insertImageLayer(BadgeEditableArtwork.table(edited));
    }

    private void editSelectedArtwork()throws Exception{
        int row=table.getSelectedRow();
        if(row<0||!editArtwork(template.pageElements().get(row)))
            throw new IOException("Select a generated barcode, QR code, WordArt or table first.");
    }

    private boolean editArtwork(BadgeTemplate.Element layer)throws Exception{
        commitCell();
        var artwork=BadgeEditableArtwork.read(layer);
        if(artwork==null)return false;
        StudioImageFile.Loaded replacement=switch(artwork.kind()){
            case CODE -> {
                var code=BarcodeEditor.editArtwork(this,artwork.objects().get(0));
                yield code==null?null:BadgeEditableArtwork.code(code);
            }
            case WORD_ART -> {
                var text=publishing.WordArtEditor.choose(this,artwork.objects().get(0));
                yield text==null?null:BadgeEditableArtwork.wordArt(text);
            }
            case TABLE -> {
                var grid=TableEditor.choose(this,BadgeEditableArtwork.table(artwork),false);
                yield grid==null?null:BadgeEditableArtwork.table(grid);
            }
        };
        if(replacement!=null){
            if(artwork.kind()==BadgeEditableArtwork.Kind.CODE){
                double side=Math.max(layer.width,layer.height);
                double scale=Math.min(Math.min(side,template.widthMm)/replacement.sourceWidth(),
                        Math.min(side,template.heightMm)/replacement.sourceHeight());
                double width=replacement.sourceWidth()*scale,height=replacement.sourceHeight()*scale;
                layer.x+=(layer.width-width)/2;
                layer.y+=(layer.height-height)/2;
                layer.width=width;layer.height=height;
            }
            layer.image=java.util.Base64.getEncoder().encodeToString(replacement.bytes());
            layers.fireTableDataChanged();
            int row=template.pageElements().indexOf(layer);
            if(row>=0)table.setRowSelectionInterval(row,row);
            dirty=true;recordHistory();refresh.restart();preview.repaint();
        }
        return true;
    }
    void insertImageLayer(StudioImageFile.Loaded image)throws Exception{
        commitCell();if(template.pageElements().size()>=64)throw new IOException("A template supports up to 64 elements.");
        BadgeTemplate.Element element=new BadgeTemplate.Element();element.type="IMAGE";element.label=image.name();
        element.image=java.util.Base64.getEncoder().encodeToString(image.bytes());
        double[] size=CanvasImageInsert.fit(image.image().getWidth(),image.image().getHeight(),template.widthMm,template.heightMm);
        element.width=size[0];element.height=size[1];element.x=(template.widthMm-element.width)/2;element.y=(template.heightMm-element.height)/2;
        template.pageElements().add(element);try{template.validate();}catch(Exception ex){template.pageElements().remove(element);throw ex;}
        layers.fireTableDataChanged();int row=template.pageElements().size()-1;table.setRowSelectionInterval(row,row);dirty=true;recordHistory();refresh.restart();preview.requestFocusInWindow();
    }
    void receiveArtwork(StudioImageFile.Loaded image)throws Exception{
        if(sharedSave||syncing)throw new IOException("Wait for the template operation to finish.");
        insertImageLayer(CanvasImageInsert.limit(image,1200));
        shell.closeRecords();
        shell.ribbon().select("Home");
    }
    private void replaceArtwork()throws Exception{
        commitCell();int row=table.getSelectedRow();if(row>=0&&"PHOTO".equals(template.pageElements().get(row).type)){choosePhoto();return;}if(row<0||!"IMAGE".equals(template.pageElements().get(row).type))throw new IOException("Select an artwork image or photo field first. Artwork is a logo or graphic saved with the template; photo fields hold personal photos for the current card.");
        JFileChooser chooser=chooser("PNG and JPEG artwork","png","jpg","jpeg");if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        template.pageElements().get(row).image=java.util.Base64.getEncoder().encodeToString(StudioImageFile.load(chooser.getSelectedFile().toPath()).bytes());dirty=true;refresh.restart();
        recordHistory();
    }
    void printCard() {
        try {
            commitCell();template.name=name.getText().trim();template.validate();
            requireSavedPermit();
            BufferedImage back=null;
            if(manualDuplex.isSelected()) {
                back=template.renderSide(true,values(),photo,300);
                JLabel backPreview=new JLabel(new ImageIcon(back.getScaledInstance(428,270,Image.SCALE_SMOOTH)));
                JPanel confirmation=new JPanel(new BorderLayout(8,8));
                confirmation.add(new JLabel("One card: Front then Back. Check back details below."),BorderLayout.NORTH);
                confirmation.add(backPreview);
                confirmation.add(new JLabel("Choose Magicard 300 / card paper in the print dialog. Enable Hand Feed Mode on the printer."),BorderLayout.SOUTH);
                if(JOptionPane.showConfirmDialog(this,confirmation,"Print front and back",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            }
            var record=template.savePrintRecords&&!OreallaRidersPermit.enabled(template)?recordPayload():null;Path recordTemplate=currentFile;
            if(record!=null&&shared==null)throw new IOException("Connect to the shared server library before printing with record saving enabled.");
            var hooks=submissions.printing(recordTemplate,record);
            StudioPrinting.print(this,template.renderSide(!manualDuplex.isSelected()&&printSide.getSelectedIndex()==1,values(),photo,300),back,template.name,
                    template.widthMm*72/25.4,template.heightMm*72/25.4,message::setText,()->{
                        if(hooks!=null){hooks.before().run();return;}
                        if(record==null)return;var body=new com.google.gson.JsonObject();body.addProperty("recordId",java.util.UUID.randomUUID().toString());body.add("payload",record);
                        try{shared.record(recordTemplate,"RECORD_SAVE",body);}catch(Exception ex){throw new IllegalStateException("Print was not sent because its record could not be saved: "+ex.getMessage(),ex);}
                    },()->{if(hooks!=null)hooks.sent().run();});
        } catch(Exception ex){error(ex);}
    }

    private void clearSelectedPhoto()throws IOException{
        int row=table.getSelectedRow();
        if(row<0||!"PHOTO".equals(template.pageElements().get(row).type))throw new IOException("Select a photo field first.");
        var element=template.pageElements().get(row);
        element.personalPhoto=null;element.photoFraming=null;
        personalPhotos.remove(element.photoId);photoFrames.remove(element.photoId);preview.repaint();
    }

    private void export(boolean pdf)throws Exception{
        commitCell();template.name=name.getText().trim();template.validate();requireSavedPermit();String ext=pdf?"pdf":"png";JFileChooser chooser=chooser(pdf?"PDF badge":"PNG badge",ext);
        Path outputs=Path.of(System.getProperty("smartstudio.register.home",System.getProperty("user.home")),"Documents","SmartStudio","Badge Output");Files.createDirectories(outputs);chooser.setCurrentDirectory(outputs.toFile());
        chooser.setSelectedFile(new java.io.File("badge."+ext));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path target=extension(chooser.getSelectedFile().toPath(),ext);if(!replace(target))return;
        if(pdf){try(PDDocument document=new PDDocument()){
            for(boolean back:new boolean[]{false,true}){
                BufferedImage result=template.renderSide(back,values(),photo,300);
                PDPage page=new PDPage(new PDRectangle((float)(template.widthMm*72/25.4),(float)(template.heightMm*72/25.4)));document.addPage(page);
                try(PDPageContentStream content=new PDPageContentStream(document,page)){content.drawImage(LosslessFactory.createFromImage(document,result),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}
            }
            document.save(target.toFile());
        }}else ImageIO.write(template.render(values(),photo,300),"png",target.toFile());
        message.setText(pdf?"Exported Front and Back as a two-page PDF at 300 DPI.":"Exported "+badgePage.getSelectedItem()+" at 300 DPI.");
    }

    private void requireSavedPermit()throws IOException {
        if(OreallaRidersPermit.enabled(template)&&(permitNumber==null||savingPermit||pendingPermit!=null))
            throw new IOException("Upload a photo and save the permit details before printing or exporting.");
    }
    private static Path extension(Path path,String ext){return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("."+ext)?path:path.resolveSibling(path.getFileName()+"."+ext);}
    private boolean replace(Path target){return !Files.exists(target)||JOptionPane.showConfirmDialog(this,"Replace "+target.getFileName()+"?","Save",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private JFileChooser chooser(String label,String... extensions){Path root=shared!=null&&shared.smartDrive?SmartDriveTemplatePicker.suggestedRoot():null;JFileChooser chooser=new JFileChooser((root==null?library.folder():root).toFile());chooser.setFileFilter(new FileNameExtensionFilter(label,extensions));return chooser;}
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Badge Cards",JOptionPane.ERROR_MESSAGE);}
    @FunctionalInterface private interface Action{void run()throws Exception;}
    private void addButton(JPanel panel,String label,Action action){panel.add(commandButton(label,action));}
    private JButton commandButton(String label,Action action){
        JButton button=new JButton(label);DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);
        button.addActionListener(e->{try{action.run();recordHistory();}catch(Exception ex){error(ex);}});
        return button;
    }

    private final class LayerModel extends AbstractTableModel{
        private final String[] columns={"Layer","Type","Text / placeholder","Data field","X in","Y in","Width in","Height in","Font pt","Font","Bold","Colour"};
        public int getRowCount(){return template==null?0:template.pageElements().size();}public int getColumnCount(){return columns.length;}public String getColumnName(int c){return columns[c];}
        public Class<?> getColumnClass(int c){return c==10?Boolean.class:String.class;}
        public boolean isCellEditable(int r,int c){return c!=1;}
        public Object getValueAt(int r,int c){BadgeTemplate.Element e=template.pageElements().get(r);return switch(c){case 0->e.label;case 1->e.type;case 2->e.text;case 3->e.field;case 4->e.x/25.4;case 5->e.y/25.4;case 6->e.width/25.4;case 7->e.height/25.4;case 8->e.fontMm*72/25.4;case 9->e.font;case 10->e.bold;default->e.color;};}
        public void setValueAt(Object value,int r,int c){
            BadgeTemplate.Element e=template.pageElements().get(r);Object old=getValueAt(r,c);
            try{assign(e,c,value);template.validate();if(c==3||c==0)rebuildFields(true);dirty=true;refresh.restart();recordHistory();}catch(Exception ex){assign(e,c,old);error(ex);}fireTableCellUpdated(r,c);
        }
        private void assign(BadgeTemplate.Element e,int c,Object v){String s=String.valueOf(v);switch(c){case 0->e.label=s;case 2->e.text=s;case 3->e.field=s;case 4->e.x=Double.parseDouble(s)*25.4;case 5->e.y=Double.parseDouble(s)*25.4;case 6->e.width=Double.parseDouble(s)*25.4;case 7->e.height=Double.parseDouble(s)*25.4;case 8->e.fontMm=Double.parseDouble(s)*25.4/72;case 9->e.font=s;case 10->e.bold=Boolean.parseBoolean(s);case 11->e.color=s;default->{}}}
    }
}
