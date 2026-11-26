package gy.deckers.studio;



import publishing.*;

import ui.design.*;

import javax.swing.*;

import javax.swing.table.*;

import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.*;

import java.awt.geom.Rectangle2D;

import java.awt.print.*;

import java.nio.file.*;

import java.util.*;

import java.util.List;



/** Business cards share Publisher documents, rendering and physical sheet packing. */

final class BusinessCardsPanel extends JPanel {

    private TextFormattingToolbar textFormatting;
    private final StudioWorkspaceShell shell;
    private CutProduction cutProduction;
    boolean productionBusy(){return cutProduction!=null&&cutProduction.busy();}
    void closeProduction(){if(cutProduction!=null)cutProduction.close();}

    private Publication document = builtin();
    private Publication previewDocument;
    private PublicationRenderer previewRenderer;
    private PublicationRenderer canvasRenderer(){
        if(previewDocument!=document){previewDocument=document;previewRenderer=new PublicationRenderer(document,banners?2048:0);}
        return previewRenderer;
    }

    private boolean syncingLayerSelection;
    private boolean updatingLayerRows;
    private Runnable refreshLayerFormatting=()->{};
    private final Deque<Publication> undoHistory=new ArrayDeque<>(),redoHistory=new ArrayDeque<>();

    private Publication historyState;

    private boolean restoringHistory;

    private void resetHistory(){undoHistory.clear();redoHistory.clear();historyState=document.copy();}

    private boolean sameDesign(Publication a,Publication b){return Publication.JSON.toJson(a).equals(Publication.JSON.toJson(b))&&a.assets.equals(b.assets)&&a.assetFiles.equals(b.assetFiles);}

    private void recordHistory(){if(restoringHistory||design.gestureActive())return;if(historyState==null){resetHistory();return;}if(sameDesign(historyState,document))return;undoHistory.addLast(historyState);while(undoHistory.size()>30)undoHistory.removeFirst();redoHistory.clear();historyState=document.copy();}

    void historyAction(boolean redo){

        commit();recordHistory();var source=redo?redoHistory:undoHistory;var target=redo?undoHistory:redoHistory;if(source.isEmpty())return;

        target.addLast(historyState);while(target.size()>30)target.removeFirst();historyState=source.removeLast();restoringHistory=true;

        try{document=historyState.copy();pageIndex=Math.min(pageIndex,document.pages.size()-1);refreshPages();configureDesign();changed();}finally{restoringHistory=false;}

    }

    private void bindHistory(String key,String action,boolean redo){getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),action);getActionMap().put(action,new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent event){try{historyAction(redo);}catch(Exception ex){error(ex);}}});}



    private final boolean labels;
    private final boolean banners;
    private final boolean billBooks;
    private final boolean vinylWorkspace;
    private final JComboBox<String> billBookOutput=new JComboBox<>(new String[]{"Full template + numbers","Numbers only (preprinted books)","Template only (no numbering)"});
    private final JTextField billBookNumberFields=new JTextField("number, num, serial, page1_number",24);
    private SheetNumberingControls numberingControls;

    private final JLabel templateSize=new JLabel();
    private JButton addPageCommand,deletePageCommand;
    private StudioLayersPanel floatingLayers;
    private StudioFileView fileView;
    private JDialog layersWindow;
    private final JComboBox<String> orientation=new JComboBox<>(new String[]{"Portrait","Landscape"});
    private final JSpinner layoutWidth=new JSpinner(new SpinnerNumberModel(1.,.001,1000000.,.1)),layoutHeight=new JSpinner(new SpinnerNumberModel(1.,.001,1000000.,.1));
    private final JComboBox<String> layoutUnits=new JComboBox<>(new String[]{"MM","CM","IN","FT"});
    private final JCheckBox scaleLayout=new JCheckBox("Scale artwork");
    private boolean updatingLayout;
    private String previousLayoutUnit="MM";

    private int pageIndex; private boolean updatingPages;

    private final JComboBox<String> pages = new JComboBox<>();

    private final PublicationPages pagePreviews=new PublicationPages(index->pages.setSelectedIndex(index));

    private final javax.swing.Timer thumbnailRefresh=new javax.swing.Timer(350,event->pagePreviews.update(document,pageIndex));

    private final JComboBox<String> pageMode = new JComboBox<>(new String[]{"Separate page sheets", "Mixed pages per sheet"});

    private final TemplateWorkspace design = new TemplateWorkspace();

    private final DefaultTableModel records = new DefaultTableModel(new String[0],0);

    private final JTable table = new JTable(records){

        @Override public void createDefaultColumnsFromModel(){super.createDefaultColumnsFromModel();for(int c=0;c<getColumnCount();c++)getColumnModel().getColumn(c).setPreferredWidth(125);}

    };

    private final LayerModel layers = new LayerModel();

    private SharedPublicationLibrary shared;

    private volatile boolean sharedActive;

    private boolean sharedSyncing;

    private volatile long sharedGeneration;

    private javax.swing.Timer sharedTimer;

    private Path currentSharedFile;
    private Path currentLocalFile;
    private String currentLocalHash="";

    private String currentSharedHash="",migrationStatus="";

    private final JLabel sharedLibraryStatus=new JLabel("Local template library");

    void useSharedLibrary(boolean offline){

        if(banners){sharedLibraryStatus.setText("Banner templates on this computer • Export .sspub to reuse elsewhere");return;}
        stopSharedLibrary();SharedPublicationLibrary next=new SharedPublicationLibrary(labels,billBooks);

        if(shared==null||!shared.folder().equals(next.folder())){shared=next;currentSharedFile=null;currentSharedHash="";}

        shared.stores.resetView();shared.online=false;sharedLibraryStatus.setText("OFFLINE — cached server templates");sharedActive=!offline;migrationStatus="";

        if(sharedTimer==null)sharedTimer=new javax.swing.Timer(30000,e->syncLibrary());

        if(!offline){sharedTimer.start();syncLibrary();}

    }

    void stopSharedLibrary(){sharedGeneration++;sharedActive=false;if(sharedTimer!=null)sharedTimer.stop();if(shared!=null)shared.online=false;}

    private void syncLibrary(){

        if(shared==null||!sharedActive||sharedSyncing||working)return;sharedSyncing=true;

        var active=shared;long generation=sharedGeneration;

        new SwingWorker<String,Void>(){

            protected String doInBackground()throws Exception{

                active.refresh();if(!sharedActive||generation!=sharedGeneration)return null;

                return active.migrate(billBooks?BillBookDocuments.files():PublicationTemplateLibrary.files(labels),()->sharedActive&&generation==sharedGeneration,billBooks);

            }

            protected void done(){sharedSyncing=false;if(active!=shared||generation!=sharedGeneration){if(sharedActive)syncLibrary();return;}

                sharedLibraryStatus.setText(active.status);try{String message=get();if(message!=null&&!message.equals(migrationStatus)){migrationStatus=message;status.setText(message);}}catch(Exception ex){status.setText(active.online?"Shared library online; local template migration is pending. Check local files and save permission.":active.status);}

            }

        }.execute();

    }

    private final JTable layerTable = new JTable(layers);

    private final JLabel status = new JLabel("3.5 × 2 inches • Use {{field}} in text to merge table columns.");

    private final JLabel source = new JLabel("Built-in table");

    private final SheetPreview sheetImage = new SheetPreview();

    private final JComboBox<String> paper = new JComboBox<>(new String[]{"Letter", "A4", "Legal", "11 × 17", "Custom"});

    private final JSpinner customWidth=new JSpinner(new SpinnerNumberModel(8.5,0.1,78.74,0.1)), customHeight=new JSpinner(new SpinnerNumberModel(11.0,0.1,78.74,0.1));

    private boolean previewPending;

    private final JSpinner leftMargin = new JSpinner(new SpinnerNumberModel(0.0,0.,50/25.4,.01));
    private final JSpinner rightMargin = new JSpinner(new SpinnerNumberModel(0.0,0.,50/25.4,.01));
    private final JSpinner topMargin = new JSpinner(new SpinnerNumberModel(0.0,0.,50/25.4,.01));
    private final JSpinner bottomMargin = new JSpinner(new SpinnerNumberModel(0.0,0.,50/25.4,.01));

    private final JSpinner verticalGap = new JSpinner(new SpinnerNumberModel(0.0,-2000/25.4,20/25.4,.01));
    private final JSpinner gap = new JSpinner(new SpinnerNumberModel(0.0,-2000/25.4,20/25.4,.01));

    private final JSpinner copies = new JSpinner(new SpinnerNumberModel(10,1,100000,1));

    private final JSpinner sheet = new JSpinner(new SpinnerNumberModel(1,1,100000,1));

    private final JCheckBox rotate = new JCheckBox("Allow rotation",true), marks = new JCheckBox("Cut marks",true);

    private Path excel; private String worksheet; private boolean dirty, working; private int sessionGeneration;

    private final javax.swing.Timer refresh = new javax.swing.Timer(220,e -> previewSheet());



    BusinessCardsPanel() {this(false);}

    BusinessCardsPanel(boolean labels) {this(labels,false);}

    BusinessCardsPanel(boolean labels,boolean banners) {this(labels,banners,false);}
    BusinessCardsPanel(boolean labels,boolean banners,boolean billBooks) {
        this(labels,banners,billBooks,false);
    }
    BusinessCardsPanel(boolean labels,boolean banners,boolean billBooks,boolean vinylWorkspace) {

        super(new BorderLayout(8,8));

        this.labels=labels;this.banners=banners;this.billBooks=billBooks;this.vinylWorkspace=vinylWorkspace;if(banners)document=BannerDocuments.create(20,4,"FT");else if(billBooks)document=BillBookDocuments.create();else if(labels)document=labelTemplate();document.displayUnit="IN"; setBackground(DeckersPalette.background());

        resetHistory();bindHistory("control Z","undoDesign",false);bindHistory("control Y","redoDesign",true);bindHistory("control shift Z","redoDesignAlternate",true);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control S"),"saveTemplateChanges");
        if(!GraphicsEnvironment.isHeadless())getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),"saveTemplateChanges");
        getActionMap().put("saveTemplateChanges",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent event){try{saveChanges();}catch(Exception ex){error(ex);}}});

        design.setPageFocus(true);design.addPropertyChangeListener("editCompleted",event->recordHistory());

        refresh.setRepeats(false);thumbnailRefresh.setRepeats(false);

        JPanel tools = DeckersSwing.panel(); tools.setLayout(new FlowLayout(FlowLayout.LEFT));

        addButton(tools,billBooks?"New bill book":banners?"New banner":labels?"New label":"New card",this::newCard);

        addButton(tools,"Template library…",()->chooseTemplate());

        addButton(tools,"Template size…",this::changeTemplateSize);

        addButton(tools,"Open Publisher template",this::openTemplate);

        JButton undoCommand=addButton(tools,"Undo (Ctrl+Z)",()->historyAction(false));JButton redoCommand=addButton(tools,"Redo (Ctrl+Y)",()->historyAction(true));

        addButton(tools,"Save (Ctrl+S)",this::saveChanges);addButton(tools,"Export template file…",this::saveTemplate);addButton(tools,"Save to template library…",this::saveToLibrary); tools.add(pages);addPageCommand=addButton(tools,"Add page",this::addPage);deletePageCommand=addButton(tools,"Delete page",this::deletePage);pagePreviews.setPageActions(addPageCommand,deletePageCommand);JButton pageNumberCommand=addButton(tools,"Page number field",this::pageNumberField);

        JButton textCommand=addButton(tools,"Add text",() -> {Publication.Element e=new Publication.Element();e.width=Math.min(78,document.width);e.height=Math.min(10,document.height);e.text=vinylWorkspace?"Vinyl":banners?"Banner text":"{{name}}";if(banners){e.x=document.width*.05;e.y=document.height*.05;e.width=document.width*.8;e.height=document.height*.25;e.fontSize=Math.min(10000,document.height*.18*72/25.4);}currentPage().objects.add(e);changed();});

        JButton imageCommand=addButton(tools,"Insert image…",this::artwork);
        JButton tableCommand=addButton(tools,"Insert table…",()->editTable(true));
        JButton editTable=addButton(tools,"Edit table…",()->editTable(false));

        JButton copyCommand=addButton(tools,"Copy layers",this::copyLayers);
        JButton pasteCommand=addButton(tools,"Paste",this::pasteLayers);

        JButton wordArtCommand=addButton(tools,"Add WordArt…",()->{commit();var e=publishing.WordArtEditor.choose(this,null);if(e!=null){e.width=Math.min(e.width,document.width);e.height=Math.min(e.height,document.height);e.x=e.y=0;currentPage().objects.add(e);changed();}});

        JButton editWordArt=addButton(tools,"Edit WordArt…",()->{commit();int row=layerTable.getSelectedRow();if(row<0||!currentPage().objects.get(row).type.equals("TEXT"))throw new IllegalArgumentException("Select a text layer first.");var e=publishing.WordArtEditor.choose(this,currentPage().objects.get(row));if(e!=null){currentPage().objects.set(row,e);changed();}});

        JButton barcodeCommand=addButton(tools,"Add barcode…",()->generateCode(false));
        JButton qrCommand=addButton(tools,"Add QR code…",()->generateCode(true));

        JButton editCode=addButton(tools,"Edit barcode / QR…",()->{commit();int row=layerTable.getSelectedRow();if(row<0||!Set.of("BARCODE","QR").contains(currentPage().objects.get(row).type))throw new IllegalArgumentException("Select a barcode or QR layer first.");var code=BarcodeEditor.choose(this,currentPage().objects.get(row));if(code!=null){currentPage().objects.set(row,code);changed();}});

        JButton replaceWithCode=addButton(tools,"Replace image with barcode…",()->{commit();int row=layerTable.getSelectedRow();if(row<0||!currentPage().objects.get(row).type.equals("IMAGE"))throw new IllegalArgumentException("Select the barcode picture in Layers first.");var old=currentPage().objects.get(row);var code=BarcodeEditor.choose(this,null);if(code!=null){code.x=old.x;code.y=old.y;code.width=old.width;code.height=old.height;currentPage().objects.set(row,code);changed();}});

        StudioLayerClipboard.bind(design,this::copyLayers,this::pasteLayers);
        StudioLayerClipboard.bind(layerTable,this::copyLayers,this::pasteLayers);

        JButton deleteLayersCommand=addButton(tools,"Remove selected layers",() -> {commit();design.deleteSelection();});

        JScrollPane toolsScroll=new JScrollPane(tools,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);toolsScroll.setPreferredSize(new Dimension(700,58));add(toolsScroll,BorderLayout.NORTH);

        refreshPages();configureDesign();

        layerTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);layerTable.setRowHeight(25);

        layerTable.getColumnModel().getColumn(0).setPreferredWidth(180);

        layerTable.getSelectionModel().addListSelectionListener(e -> {if(!syncingLayerSelection&&!e.getValueIsAdjusting())design.selectIndices(layerTable.getSelectedRows());});

        layerTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"),"deleteLayer");

        layerTable.getActionMap().put("deleteLayer",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(!layerTable.isEditing())design.deleteSelection();}});

        JPanel editor = DeckersSwing.panel();editor.setLayout(new BorderLayout());editor.add(new JScrollPane(design));

        JScrollPane layerScroll = new JScrollPane(layerTable);layerScroll.setPreferredSize(new Dimension(450,155));editor.add(layerScroll,BorderLayout.SOUTH);

        JPanel output=DeckersSwing.panel();output.setLayout(new BorderLayout());

        JPanel setup=DeckersSwing.panel();setup.setLayout(new GridLayout(0,1));

        JPanel sizeRow=setupRow(setup),customRow=setupRow(setup),horizontalMargins=setupRow(setup),verticalMargins=setupRow(setup),spacingRow=setupRow(setup),countRow=setupRow(setup);

        sizeRow.add(pageMode);sizeRow.add(paper);

        customRow.add(new JLabel("Custom width in"));customRow.add(customWidth);customRow.add(new JLabel("Height in"));customRow.add(customHeight);customWidth.setEnabled(false);customHeight.setEnabled(false);

        horizontalMargins.add(new JLabel("Left margin in"));horizontalMargins.add(leftMargin);horizontalMargins.add(new JLabel("Right margin in"));horizontalMargins.add(rightMargin);
        verticalMargins.add(new JLabel("Top margin in"));verticalMargins.add(topMargin);verticalMargins.add(new JLabel("Bottom margin in"));verticalMargins.add(bottomMargin);
        spacingRow.add(new JLabel("Horizontal gap in"));spacingRow.add(gap);spacingRow.add(new JLabel("Vertical gap in"));spacingRow.add(verticalGap);spacingRow.add(rotate);spacingRow.add(marks);

        countRow.add(new JLabel("Copies (no merge)"));countRow.add(copies);countRow.add(new JLabel("Sheet"));countRow.add(sheet);

        JPanel outputButtons=DeckersSwing.panel();outputButtons.setLayout(new FlowLayout(FlowLayout.LEFT));addButton(outputButtons,"Preview",this::previewPrintSetup);addButton(outputButtons,"Print",this::printCards);addButton(outputButtons,"Export PDF",this::exportPdf);
        if(billBooks){
            JPanel numbering=DeckersSwing.panel();numbering.setLayout(new FlowLayout(FlowLayout.LEFT));
            numbering.add(new JLabel("Print content"));numbering.add(billBookOutput);
            numbering.add(new JLabel("Number columns"));numbering.add(billBookNumberFields);
            billBookNumberFields.setToolTipText("Comma-separated numbering fields, for example number, page1_number. Used to print only numbers or omit numbering.");
            setup.add(numbering);
            billBookOutput.addActionListener(e->refresh.restart());
            billBookNumberFields.addActionListener(e->refresh.restart());
            numberingControls=new SheetNumberingControls(this::output,()->{dirty=true;refresh.restart();});
            for(Component row:numberingControls.getComponents())setup.add(row);
        }

        JScrollPane setupScroll=new JScrollPane(setup,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);setupScroll.setPreferredSize(new Dimension(400,Math.min(280,Math.max(150,setup.getPreferredSize().height+22))));JPanel outputHeader=DeckersSwing.panel();outputHeader.setLayout(new BorderLayout());outputHeader.add(setupScroll);outputHeader.add(outputButtons,BorderLayout.SOUTH);output.add(outputHeader,BorderLayout.NORTH);

        output.add(sheetImage);

        editor.setMinimumSize(new Dimension(220,180));output.setMinimumSize(new Dimension(220,180));

        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,editor,output);split.setResizeWeight(.55);split.setPreferredSize(new Dimension(900,400));

        JPanel merge=DeckersSwing.panel();merge.setLayout(new BorderLayout());

        JPanel mergeTools=DeckersSwing.panel();mergeTools.setLayout(new FlowLayout(FlowLayout.LEFT));

        addButton(mergeTools,"Add row",() -> records.addRow(new Object[records.getColumnCount()]));

        addButton(mergeTools,"Remove row",() -> {int r=table.getSelectedRow();if(r>=0)records.removeRow(r);});

        if(billBooks)addButton(mergeTools,"Add number field",this::pageNumberField);
        addButton(mergeTools,"Fill number series",this::fillSeries);addButton(mergeTools,"Add field",this::addField);addButton(mergeTools,"Delete field",this::deleteField);addButton(mergeTools,"Connect Excel",this::connectExcel);

        addButton(mergeTools,"Reload Excel",() -> {if(excel==null)throw new IllegalStateException("Connect an Excel file first.");loadExcel(excel,worksheet);});

        addButton(mergeTools,"Clear table",() -> {if(JOptionPane.showConfirmDialog(this,"Clear all merge records?","Business Cards",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){records.setRowCount(0);excel=null;source.setText("Built-in table");}});

        mergeTools.add(source);JScrollPane mergeToolbar=new JScrollPane(mergeTools,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);mergeToolbar.setPreferredSize(new Dimension(700,60));merge.add(mergeToolbar,BorderLayout.NORTH);table.setRowHeight(25);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);merge.add(new JScrollPane(table));

        merge.setPreferredSize(new Dimension(900,160));merge.setMinimumSize(new Dimension(220,100));
        merge.putClientProperty("studio.task.fill",Boolean.TRUE);

        editor.remove(layerScroll);

        JSplitPane pageLayout=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,pagePreviews,editor);pageLayout.setResizeWeight(0);pageLayout.setDividerLocation(180);pageLayout.setContinuousLayout(true);pageLayout.setBorder(null);
        pagePreviews.addPropertyChangeListener("preferredSize",e->pageLayout.setDividerLocation(pagePreviews.getPreferredSize().width));

        shell=new StudioWorkspaceShell(pageLayout);
        shell.useTaskWindows();
        shell.title(vinylWorkspace?"Vinyl Cut":billBooks?"Bill Books":banners?"Banners":labels?"Labels":"Business Cards");

        remove(toolsScroll);

        StudioWorkspaceShell.stack(tools);

        for(JButton editingCommand:new JButton[]{textCommand,imageCommand,undoCommand,redoCommand,copyCommand,pasteCommand,
                deleteLayersCommand,wordArtCommand,tableCommand,barcodeCommand,qrCommand,pageNumberCommand,addPageCommand,
                deletePageCommand,editWordArt,editCode,replaceWithCode,editTable})tools.remove(editingCommand);
        shell.addTools(StudioWorkspaceShell.menu("Template",tools));
        shell.ribbon().add(StudioCommand.CHANGE_TEMPLATE,this::chooseTemplate);
        shell.addTextAndImage(textCommand,imageCommand);
        shell.ribbon().add(StudioCommand.UNDO,undoCommand);
        shell.ribbon().add(StudioCommand.REDO,redoCommand);
        shell.ribbon().add(StudioCommand.COPY,copyCommand);
        shell.ribbon().add(StudioCommand.PASTE,pasteCommand);
        shell.ribbon().add(StudioCommand.DELETE,deleteLayersCommand);
        shell.ribbon().add(StudioCommand.WORDART,wordArtCommand);
        shell.ribbon().add(StudioCommand.TABLE,tableCommand);
        shell.ribbon().add(StudioCommand.BARCODE,barcodeCommand);
        shell.ribbon().add(StudioCommand.QR,qrCommand);
        shell.ribbon().add(StudioCommand.PAGE_NUMBER,pageNumberCommand);
        shell.ribbon().add(StudioCommand.ADD_PAGE,addPageCommand);
        shell.ribbon().add(StudioCommand.DELETE_PAGE,deletePageCommand);
        shell.ribbon().add(StudioCommand.RECTANGLE,()->insertShape("RECT"));
        shell.ribbon().add(StudioCommand.ELLIPSE,()->insertShape("ELLIPSE"));
        shell.ribbon().add(StudioCommand.LINE,()->insertShape("LINE"));
        if(!banners&&!billBooks&&!vinylWorkspace){
            shell.ribbon().add(StudioCommand.PHOTO_FIELD,this::insertPhotoPlaceholder);
            shell.ribbon().add("Home","Picture",StudioRibbon.button("Replace photo…",this::replaceSelectedPhoto));
            shell.ribbon().add("Home","Picture",StudioRibbon.button("Frame photo…",this::frameSelectedPhoto));
        }
        shell.ribbon().add("Table","Edit",editTable);
        shell.ribbon().add("Home","Text",editWordArt);
        shell.ribbon().add("Code format","Selection",editCode);
        shell.ribbon().add("Home","Picture",replaceWithCode);
        shell.ribbon().add("Table","Selection",StudioWorkspaceShell.button("Select whole table",()->{try{selectTable();}catch(Exception ex){error(ex);}}));
        shell.addPrimaryAction("New template",()->{if(!working)newCard();});

        shell.addTools(StudioWorkspaceShell.button("Save",()->{try{saveChanges();}catch(Exception ex){error(ex);}}));

        shell.addTools(StudioWorkspaceShell.button("Print",()->{try{printCards();}catch(Exception ex){error(ex);}}));

        shell.addTools(StudioWorkspaceShell.button("Export PDF",()->{try{exportPdf();}catch(Exception ex){error(ex);}}));
        JPanel risoTools=DeckersSwing.panel();risoTools.setLayout(new BoxLayout(risoTools,BoxLayout.Y_AXIS));risoTools.add(StudioWorkspaceShell.button("Automatic colour separation…",this::separateRiso));risoTools.add(StudioWorkspaceShell.button("Linked Riso sub-pages…",()->organiseRiso(pageIndex,LinkedRiso.MAIN)));shell.addTools(StudioWorkspaceShell.menu("Riso",risoTools));
        pagePreviews.setRisoSelection(this::organiseRiso);

        textFormatting=new TextFormattingToolbar(this::selectedTextStyle,this::applyTextStyle,()->publishing.TextAlignment.LEFT);if(banners)textFormatting.setMaximumFontSize(10000);
        BarcodeFormattingToolbar barcodeFormatting=new BarcodeFormattingToolbar(this::selectedBarcode,this::applyBarcodeSettings);
        ImageFormattingToolbar imageFormatting=new ImageFormattingToolbar(this::selectedPicture,this::applyPictureSize,this::recolourPicture);imageFormatting.setTransparencyEditor(this::applyPictureTransparency);imageFormatting.setRotationEditor(this::applyPictureRotation);if(banners)imageFormatting.setMaximumSizeMm(BannerDocuments.MAX_MM);
        design.setAspectRatioPolicy(i->currentPage().objects.get(i).type.equals("IMAGE")?imageFormatting.locked.isSelected():null);
        design.setLayerActivation(row->{
            if(StudioTables.isTable(currentPage().objects.get(row))){try{editTable(false);}catch(Exception ex){error(ex);}return true;}
            if(!"IMAGE".equals(currentPage().objects.get(row).type))return false;
            try{replacePhoto(row);}catch(Exception ex){error(ex);}return true;
        });
        CardLayout contextLayout=new CardLayout();JPanel contextTools=new JPanel(contextLayout);contextTools.setOpaque(false);
        contextTools.add(textFormatting,"text");contextTools.add(barcodeFormatting,"barcode");contextTools.add(imageFormatting,"image");shell.addTextTools(contextTools,this::changeLayerOrder);
        refreshLayerFormatting=()->{textFormatting.refresh();barcodeFormatting.refresh();imageFormatting.refresh();contextLayout.show(contextTools,selectedPicture()!=null?"image":selectedBarcode()==null?"text":"barcode");};
        layerTable.getSelectionModel().addListSelectionListener(event->{if(!updatingLayerRows&&!event.getValueIsAdjusting())refreshLayerFormatting.run();});
        layerTable.getModel().addTableModelListener(event->{if(!updatingLayerRows)refreshLayerFormatting.run();});

        shell.addTools(pages);updateTemplateSize();

        shell.addZoom(()->design.zoomBy(1/1.15),()->{design.setZoom(1);design.scrollRectToVisible(new Rectangle(0,0,1,1));},()->design.zoomBy(1.15));

        JPanel layerPanel=DeckersSwing.panel();layerPanel.setLayout(new BorderLayout(4,4));
        JPanel orderTools=DeckersSwing.panel();orderTools.setLayout(new GridLayout(2,2,4,4));
        StudioLayerOrder.Move[] orderMoves=StudioLayerOrder.Move.values();
        JPopupMenu orderMenu=new JPopupMenu();
        for(var move:orderMoves){
            String title=StudioCommand.layerOrder(move).label;
            Runnable action=()->{try{reorderLayers(move);}catch(Exception ex){error(ex);}};
            orderTools.add(StudioWorkspaceShell.button(title,action));
            JMenuItem item=new JMenuItem(title);item.addActionListener(e->action.run());orderMenu.add(item);
        }
        design.setComponentPopupMenu(orderMenu);layerTable.setComponentPopupMenu(orderMenu);
        String[] orderKeys={"control CLOSE_BRACKET","control OPEN_BRACKET","control shift CLOSE_BRACKET","control shift OPEN_BRACKET"};
        for(int i=0;i<orderKeys.length;i++){
            var move=orderMoves[i];String key="layerOrder"+i;
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(orderKeys[i]),key);
            getActionMap().put(key,new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){try{reorderLayers(move);}catch(Exception ex){error(ex);}}});
        }
        layerPanel.add(orderTools,BorderLayout.NORTH);layerPanel.add(layerScroll);
        shell.addArrangement(this::changeLayerOrder,()->arrangeSelection(false),()->arrangeSelection(true));
        shell.ribbon().retain(layerPanel);
        shell.ribbon().add(StudioCommand.LAYERS,this::showLayers);
        layers.addTableModelListener(e->{if(floatingLayers!=null)floatingLayers.refresh();});
        layerTable.getSelectionModel().addListSelectionListener(e->{if(floatingLayers!=null&&!e.getValueIsAdjusting())floatingLayers.refresh();});
        shell.addSection(billBooks?"Bill book library":banners?"Banner library":"Shared library",sharedLibraryStatus);

        JCheckBox barcodeCaption=new JCheckBox("Show text/numbers below barcode");
        barcodeCaption.setOpaque(false);barcodeCaption.setToolTipText("Select a generated barcode. Uncheck to hide its caption without changing the encoded data.");
        Runnable refreshBarcodeCaption=()->{int index=layerTable.getSelectedRow();var e=index>=0&&index<currentPage().objects.size()?currentPage().objects.get(index):null;
            boolean barcode=e!=null&&e.type.equals("BARCODE");barcodeCaption.setVisible(barcode);barcodeCaption.setEnabled(barcode);barcodeCaption.setSelected(barcode&&e.barcodeText);};
        layerTable.getSelectionModel().addListSelectionListener(e->refreshBarcodeCaption.run());
        layerTable.getModel().addTableModelListener(e->refreshBarcodeCaption.run());
        barcodeCaption.addActionListener(event->{try{commit();int index=layerTable.getSelectedRow();if(index<0||!currentPage().objects.get(index).type.equals("BARCODE"))return;
            currentPage().objects.get(index).barcodeText=barcodeCaption.isSelected();changed();}catch(Exception ex){error(ex);}});
        JPanel properties=DeckersSwing.panel();properties.setLayout(new BorderLayout(4,4));properties.add(barcodeCaption,BorderLayout.NORTH);properties.add(StudioWorkspaceShell.properties(layerTable));
        refreshBarcodeCaption.run();shell.addSection("Properties",properties);

        if(!banners){
            shell.addSection("Data",merge);
            shell.ribbon().add(StudioCommand.BROWSE_RECORDS,this::browseMergeRecords);
            shell.ribbon().task("Data","Records","Edit records",merge);
            records.addTableModelListener(event->shell.closeRecords());
            for(Component control:mergeTools.getComponents())if(control instanceof JButton command)
                shell.ribbon().add("Data","Merge",StudioRibbon.shortcut(command,command.getText()));
            shell.ribbon().add(StudioCommand.MERGE_FIELD,this::insertMergeField);
        }

        if(labels){cutProduction=new CutProduction(this,()->{commit();return document.copy();},()->pageIndex,vinylWorkspace);cutProduction.install(shell);}
        installLayoutRibbon(shell);
        shell.ribbon().retain(output);
        if(billBooks){
            JPanel numberingRibbon=new JPanel();numberingRibbon.setOpaque(false);
            numberingRibbon.add(ribbonField("Print content",billBookOutput,270));
            numberingRibbon.add(ribbonField("Number fields",billBookNumberFields,180));
            numberingRibbon.add(ribbonField("Numbering order",numberingControls.order,300));
            numberingRibbon.add(ribbonField("Custom positions",numberingControls.custom,200));
            numberingRibbon.add(numberingControls.map);
            numberingControls.cutStack.setText("Cut and stack");numberingRibbon.add(numberingControls.cutStack);
            numberingRibbon.add(StudioRibbon.button("Preview",this::previewPrintSetup));
            for(Component component:numberingRibbon.getComponents())if(component instanceof JPanel field){Dimension size=field.getPreferredSize();field.setPreferredSize(new Dimension(size.width,58));}
            shell.ribbon().inlineSettings("Numbering",numberingRibbon);
        }

        installFileView(shell);
        add(shell);

        JPanel footer=DeckersSwing.panel();footer.setLayout(new BorderLayout(16,0));footer.add(templateSize,BorderLayout.WEST);footer.add(status);

        if(billBooks){sharedLibraryStatus.setText("Shared bill book library — sign in to synchronize");status.setText("Use Number series to fill {{number}}. Check imported fonts and every page before printing.");}else if(banners){sharedLibraryStatus.setText("Banner templates on this computer • Export .sspub to reuse elsewhere");status.setText("Ctrl+wheel zoom • middle-drag / scrollbars pan • Fit resets view • PDF at actual size.");}else if(labels)status.setText("Use {{field}} in text to merge table columns. Sheet size is set in Print setup.");

        add(footer,BorderLayout.SOUTH);shell.ribbon().addPropertyChangeListener("fileOpen",e->footer.setVisible(!Boolean.TRUE.equals(e.getNewValue())));

        records.addTableModelListener(e -> {dirty=true;design.repaint();refresh.restart();});

        table.getSelectionModel().addListSelectionListener(e -> design.repaint());

        pages.addActionListener(e -> {if(!updatingPages){commit();pageIndex=pages.getSelectedIndex();pagePreviews.select(pageIndex);configureDesign();layers.fireTableDataChanged();design.repaint();}});

        pageMode.addActionListener(e -> {dirty=true;refresh.restart();});paper.addActionListener(e -> {boolean custom="Custom".equals(paper.getSelectedItem());customWidth.setEnabled(custom);customHeight.setEnabled(custom);dirty=true;refresh.restart();});

        for(JSpinner spinner:List.of(leftMargin,rightMargin,topMargin,bottomMargin,gap,verticalGap,copies,sheet,customWidth,customHeight))spinner.addChangeListener(e -> {if(spinner!=sheet)dirty=true;refresh.restart();});

        rotate.addActionListener(e -> {dirty=true;refresh.restart();});marks.addActionListener(e -> {dirty=true;refresh.restart();});

        if(!billBooks){document.print.width=215.9;document.print.height=279.4;}restoreSheetSize(document.print);dirty=false;refresh.restart();

    }

    private void newCard(){if(discard()){Publication banner=banners?newBanner():null;if(banners&&banner==null)return;currentLocalFile=null;currentLocalHash="";currentSharedFile=null;currentSharedHash="";document=billBooks?BillBookDocuments.create():banners?banner:labels?labelTemplate():builtin();document.displayUnit="IN";if(!billBooks){document.print.width=215.9;document.print.height=279.4;}restoreSheetSize(document.print);pageIndex=0;refreshPages();records.setDataVector(new Object[0][0],new String[0]);excel=null;source.setText("Built-in table");configureDesign();resetHistory();changed();dirty=false;}}



    boolean chooseTemplate(){

        if(working){status.setText("Wait for the current operation to finish.");return false;}

        try{
            if(sharedActive&&!banners&&!vinylWorkspace){
                var selected=SmartDriveTemplatePicker.choose(this,billBooks?"BILL_BOOKS":labels?"LABELS":"BUSINESS_CARDS");
                if(selected.enabled()){
                    if(selected.current())return true;
                    if(selected.file()==null||!discard())return false;
                    openDocument(selected.file(),selected.identity());return true;
                }
            }
            var choice=PublicationTemplateLibrary.choose(this,labels,shared,banners,billBooks);if(choice==null)return false;

            switch(choice.kind()){

                case CURRENT->{return true;}

                case BLANK->{newCard();return true;}

                case IMPORT->{openTemplate();return true;}

                case FILE->{if(discard()){openTemplate(choice.file());return true;}return false;}

                default->{return false;}

            }

        }catch(Exception ex){error(ex);return false;}

    }



    private Publication newBanner(){
        JComboBox<String> units=new JComboBox<>(new String[]{"IN","FT","CM","MM"});
        JTextField width=new JTextField("240"),height=new JTextField("48");
        JPanel form=new JPanel(new GridLayout(0,2,8,8));form.add(new JLabel("Units"));form.add(units);form.add(new JLabel("Width"));form.add(width);form.add(new JLabel("Height"));form.add(height);
        form.add(new JLabel("Maximum 1,000,000 mm per side"));form.add(new JLabel("PDF export keeps physical dimensions"));
        while(JOptionPane.showConfirmDialog(this,form,"New banner template",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{return BannerDocuments.create(Double.parseDouble(width.getText().trim()),Double.parseDouble(height.getText().trim()),units.getSelectedItem().toString());}
            catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Banner size",JOptionPane.ERROR_MESSAGE);}
        }return null;
    }

    static Publication labelTemplate(){Publication p=new Publication();p.title="Label";p.displayUnit="IN";p.width=50.8;p.height=25.4;p.template=true;return p;}

    static Publication readTemplate(Path path,Path executable,boolean labels)throws Exception{return readTemplate(path,executable,labels,false); }
    static Publication readTemplate(Path path,Path executable,boolean labels,boolean banners)throws Exception{

        Publication p;

        if(!path.toString().toLowerCase(Locale.ROOT).endsWith(".sspub")){

            p=EditablePublicationImport.read(path,executable);

        }else p=banners?PublicationLargeFiles.read(path,PublicationLargeFiles.cache()):PublicationFiles.read(Files.readAllBytes(path));

        p.validate();return p;

    }

    // The generic importer pairs incomplete editable reconstructions with complete reference pages.

    // Cards must print the complete design once, rather than reject it or lose unsupported artwork.

    static void preserveConvertedArtwork(Publication p){

        List<Publication.Page> printable=new ArrayList<>();

        for(var page:p.pages){

            if(page.reference){

                if(printable.isEmpty())throw new IllegalArgumentException("Converted artwork has no matching page.");

                var reconstructed=printable.get(printable.size()-1);

                page.name=reconstructed.name;page.reference=false;

                printable.set(printable.size()-1,page);

            }else printable.add(page);

        }

        p.pages=printable;

        Set<String> used=new HashSet<>();for(var page:p.allPages())for(var element:page.objects)if(element.asset!=null)used.add(element.asset);

        p.assets.keySet().retainAll(used);p.assetFiles.keySet().retainAll(used);

    }

    private static String sizeDescription(Publication p){return String.format(Locale.ROOT,"%.3f × %.3f %s",Publication.fromMm(p.width,p.displayUnit),Publication.fromMm(p.height,p.displayUnit),p.displayUnit);}

    private void updateTemplateSize(){
        templateSize.setText(sizeDescription(document));updatingLayout=true;
        try{orientation.setSelectedIndex(document.width>document.height?1:0);layoutUnits.setSelectedItem(document.displayUnit);previousLayoutUnit=document.displayUnit;layoutWidth.setValue(Publication.fromMm(document.width,document.displayUnit));layoutHeight.setValue(Publication.fromMm(document.height,document.displayUnit));}
        finally{updatingLayout=false;}
    }

    private static JPanel ribbonField(String label,JComponent control,int width){
        return StudioRibbon.field(label,control,width);
    }

    private void installLayoutRibbon(StudioWorkspaceShell shell){
        for(JSpinner measurement:List.of(leftMargin,rightMargin,topMargin,bottomMargin,gap,verticalGap))measurement.setEditor(new JSpinner.NumberEditor(measurement,"0.###"));
        JPanel layout=new JPanel();layout.setOpaque(false);layout.add(StudioCommand.LAYERS.button(this::showLayers));
        layout.add(ribbonField("Orientation",orientation,120));orientation.addActionListener(e->{if(!updatingLayout)try{orientTemplate(orientation.getSelectedIndex()==0);}catch(Exception ex){error(ex);}});
        layout.add(ribbonField("Width",layoutWidth,86));layout.add(ribbonField("Height",layoutHeight,86));layout.add(ribbonField("Units",layoutUnits,70));layout.add(scaleLayout);
        layoutUnits.addActionListener(e->{if(updatingLayout)return;String unit=layoutUnits.getSelectedItem().toString();
            layoutWidth.setValue(Publication.fromMm(Publication.toMm(((Number)layoutWidth.getValue()).doubleValue(),previousLayoutUnit),unit));
            layoutHeight.setValue(Publication.fromMm(Publication.toMm(((Number)layoutHeight.getValue()).doubleValue(),previousLayoutUnit),unit));previousLayoutUnit=unit;});
        layout.add(StudioRibbon.button("Apply size",()->{try{layoutWidth.commitEdit();layoutHeight.commitEdit();String unit=layoutUnits.getSelectedItem().toString();double width=Publication.toMm(((Number)layoutWidth.getValue()).doubleValue(),unit),height=Publication.toMm(((Number)layoutHeight.getValue()).doubleValue(),unit);resizeTemplate(width,height,scaleLayout.isSelected());document.displayUnit=unit;updateTemplateSize();}catch(Exception ex){error(ex);}}));
        if(!banners){
            layout.add(ribbonField("Paper",paper,105));layout.add(ribbonField("Paper W (in)",customWidth,105));layout.add(ribbonField("Paper H (in)",customHeight,105));layout.add(ribbonField("Page layout",pageMode,220));
            JPanel print=new JPanel();print.setOpaque(false);
            print.add(ribbonField("Left (in)",leftMargin,86));print.add(ribbonField("Right (in)",rightMargin,86));print.add(ribbonField("Top (in)",topMargin,86));print.add(ribbonField("Bottom in",bottomMargin,86));
            print.add(ribbonField("Gap X (in)",gap,92));print.add(ribbonField("Gap Y (in)",verticalGap,92));print.add(ribbonField("Copies",copies,80));print.add(ribbonField("Sheet",sheet,80));print.add(rotate);print.add(marks);
            print.add(StudioRibbon.button("Preview",()->{try{previewPrintSetup();}catch(Exception ex){error(ex);}}));
            print.add(StudioRibbon.button("Copy print details",()->{try{java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(PublicationPrintDiagnostics.latestReport()),null);status.setText("Print details copied • Paste them into the support chat.");}catch(Exception ex){error(ex);}}));
            print.add(StudioRibbon.button("Save template setup",()->{try{saveChanges();}catch(Exception ex){error(ex);}}));
            shell.ribbon().inlineSettings("Print Setup",print);
        }else{
            JPanel print=new JPanel();print.setOpaque(false);print.add(new JLabel("Actual size · Set banner dimensions in Layout"));
            print.add(StudioRibbon.button("Print…",()->{try{printCards();}catch(Exception ex){error(ex);}}));
            print.add(StudioRibbon.button("Export PDF",()->{try{exportPdf();}catch(Exception ex){error(ex);}}));
            print.add(StudioRibbon.button("Save template setup",()->{try{saveChanges();}catch(Exception ex){error(ex);}}));
            shell.ribbon().inlineSettings("Print Setup",print);
        }
        layout.add(StudioWorkspaceShell.alignmentMenu(()->arrangementTargets(currentPage()).size(),this::arrangeLayers),1);
        shell.ribbon().inlineSettings("Layout",layout);updateTemplateSize();
    }

    void orientTemplate(boolean portrait){commit();Publication next=document.copy();if(TemplateOrientation.turn(next,portrait)){document=next;changed();}}

    void applyPictureRotation(double degrees){
        try{commit();if(selectedPicture()==null)return;int row=layerTable.getSelectedRow();var next=document.copy();var element=next.pages.get(pageIndex).objects.get(row);
            if(element.locked)throw new IllegalStateException("Unlock this picture before rotating it.");
            element.rotation=ImageFormattingToolbar.normalized(degrees);next.validate();document=next;changed();layerTable.setRowSelectionInterval(row,row);
        }catch(Exception ex){error(ex);}
    }
    void resizeTemplate(double width,double height,boolean scale){commit();Publication next=document.copy();next.resize(width,height,scale);next.validate();document=next;changed();}

    private void changeTemplateSize()throws Exception{

        commit();JComboBox<String> units=new JComboBox<>(new String[]{"MM","CM","IN","FT"});units.setSelectedItem(document.displayUnit);

        JSpinner width=new JSpinner(new SpinnerNumberModel(Publication.fromMm(document.width,document.displayUnit),.001,banners?BannerDocuments.MAX_MM:2000.,.1));

        JSpinner height=new JSpinner(new SpinnerNumberModel(Publication.fromMm(document.height,document.displayUnit),.001,banners?BannerDocuments.MAX_MM:2000.,.1));

        String[] previous={document.displayUnit};units.addActionListener(e->{String unit=units.getSelectedItem().toString();width.setValue(Publication.fromMm(Publication.toMm(((Number)width.getValue()).doubleValue(),previous[0]),unit));height.setValue(Publication.fromMm(Publication.toMm(((Number)height.getValue()).doubleValue(),previous[0]),unit));previous[0]=unit;});

        JCheckBox scale=new JCheckBox("Scale artwork to fit new size",false);JPanel form=new JPanel(new GridLayout(0,2,8,8));

        form.add(new JLabel("Units"));form.add(units);form.add(new JLabel("Width"));form.add(width);form.add(new JLabel("Height"));form.add(height);form.add(scale);form.add(new JLabel("Applies to every page in this template."));

        if(JOptionPane.showConfirmDialog(this,form,"Template size",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;

        width.commitEdit();height.commitEdit();String unit=units.getSelectedItem().toString();resizeTemplate(Publication.toMm(((Number)width.getValue()).doubleValue(),unit),Publication.toMm(((Number)height.getValue()).doubleValue(),unit),scale.isSelected());document.displayUnit=unit;

    }

    static Publication builtin() {

        Publication p=Publication.preset("Card");p.width=88.9;p.height=50.8;p.title="Business card";p.displayUnit="IN";

        p.pages.get(0).objects.clear();

        String[] fields={"company","name","title","phone","email"};

        for(int i=0;i<fields.length;i++){Publication.Element e=new Publication.Element();e.x=5;e.y=4+i*8;e.width=78.9;e.height=7;e.text="{{"+fields[i]+"}}";e.fontSize=i<2?14:10;e.bold=i<2;p.pages.get(0).objects.add(e);}

        return p;

    }

    private static JPanel setupRow(JPanel parent){JPanel row=DeckersSwing.panel();row.setLayout(new FlowLayout(FlowLayout.LEFT,5,3));parent.add(row);return row;}

    private Publication.Element selectedText(){if(layerTable.getSelectedRowCount()!=1)return null;int row=layerTable.getSelectedRow();return row>=0&&row<currentPage().objects.size()&&currentPage().objects.get(row).type.equals("TEXT")?currentPage().objects.get(row):null;}

    private void arrangeSelection(boolean distribute){
        try{
            commit();int[] selected=layerTable.getSelectedRows();
            if(selected.length<(distribute?3:2))throw new IllegalArgumentException(distribute?"Select at least three objects to distribute.":"Select at least two objects to align.");
            String[] labels=distribute?new String[]{"Horizontal gaps","Vertical gaps"}:new String[]{"Left","Centre","Right","Top","Middle","Bottom"};
            String choice=(String)JOptionPane.showInputDialog(this,distribute?"Equal spacing between selected objects":"Align selected objects to their combined bounds",distribute?"Distribute":"Align",JOptionPane.PLAIN_MESSAGE,null,labels,labels[0]);
            if(choice!=null)arrangeLayers(StudioArrange.Operation.values()[java.util.Arrays.asList(labels).indexOf(choice)+(distribute?6:0)]);
        }catch(Exception ex){error(ex);}
    }

    private List<Publication.Element> arrangementTargets(Publication.Page page){
        var groups=Arrays.stream(layerTable.getSelectedRows()).mapToObj(row->TableGeometry.members(page,page.objects.get(row)))
                .sorted(Comparator.<List<Publication.Element>>comparingInt(List::size).reversed()).toList();
        Set<Publication.Element> covered=Collections.newSetFromMap(new IdentityHashMap<>());
        var targets=new ArrayList<Publication.Element>();
        for(var group:groups){
            if(group.stream().noneMatch(covered::contains)){targets.add(group.get(0));covered.addAll(group);}
        }
        return targets;
    }

    void arrangeLayers(StudioArrange.Operation operation){
        try{
            commit();var page=currentPage();var targets=arrangementTargets(page);
            if(targets.stream().anyMatch(layer->TableGeometry.locked(page,layer)))
                throw new IllegalArgumentException("Unlock the selected layers before aligning or distributing them.");
            var raw=targets.stream().map(layer->TableGeometry.bounds(page,layer)).toList();
            double[][] boxes=new double[targets.size()][];
            for(int i=0;i<targets.size();i++)boxes[i]=StudioArrange.bounds(raw.get(i),targets.get(i).rotation);
            double[][] positions=StudioArrange.positions(boxes,operation,document.width,document.height);
            if(Arrays.deepEquals(boxes,positions))return;
            var originals=new IdentityHashMap<Publication.Element,double[]>();
            for(var target:targets)for(var layer:TableGeometry.members(page,target))originals.put(layer,new double[]{layer.x,layer.y});
            for(int i=0;i<targets.size();i++)TableGeometry.move(page,targets.get(i),
                    raw.get(i).getX()+positions[i][0]-boxes[i][0],raw.get(i).getY()+positions[i][1]-boxes[i][1]);
            try{document.validate();}catch(RuntimeException ex){
                originals.forEach((layer,position)->{layer.x=position[0];layer.y=position[1];});
                throw ex;
            }
            changed();
        }catch(Exception ex){error(ex);}
    }

    private TextFormattingToolbar.Style selectedTextStyle(){var e=selectedText();return e==null?null:new TextFormattingToolbar.Style(e.font,e.fontSize,e.bold,e.italic,e.underline,e.strike,e.color,e.wordArt.equals("NONE")?e.textOutline:e.wordArtOutlineWidth>0,e.wordArt.equals("NONE")?e.textOutlineColor:e.wordArtOutline,e.wordArt.equals("NONE")?e.textOutlineWidth:e.wordArtOutlineWidth,e.textAlignment);}

    private void applyTextStyle(TextFormattingToolbar.Style style){commit();var e=selectedText();if(e==null)return;int selected=layerTable.getSelectedRow();if(e.wordArt.equals("NONE")&&(!e.font.equals(style.font())||e.fontSize!=style.size()||e.bold!=style.bold()||e.italic!=style.italic()))e.fitText=false;e.font=style.font();if(e.fontSize!=style.size()){if(e.wordArt.equals("NONE"))e.fitText=false;else{double ratio=style.size()/e.fontSize;e.width*=ratio;e.height*=ratio;}}e.fontSize=style.size();e.bold=style.bold();e.italic=style.italic();e.underline=style.underline();e.strike=style.strike();e.color=style.color();e.textOutline=style.outline();e.textOutlineColor=style.outlineColor();e.textOutlineWidth=style.outlineWidth();e.textAlignment=style.alignment();if(!e.wordArt.equals("NONE")){e.wordArtOutline=style.outlineColor();e.wordArtOutlineWidth=style.outline()?style.outlineWidth():0;}document.validate();changed();if(selected>=0&&selected<layerTable.getRowCount())layerTable.setRowSelectionInterval(selected,selected);}

    private ImageFormattingToolbar.Picture selectedPicture(){if(layerTable.getSelectedRowCount()!=1)return null;int row=layerTable.getSelectedRow();if(row<0||row>=currentPage().objects.size())return null;var e=currentPage().objects.get(row);return e.type.equals("IMAGE")?new ImageFormattingToolbar.Picture(e.id,e.width,e.height,e.transparency,e.rotation):null;}
    private void applyPictureTransparency(int value){
        try{commit();if(selectedPicture()==null)return;int row=layerTable.getSelectedRow();var element=currentPage().objects.get(row);
            if(element.locked)throw new IllegalStateException("Unlock this image before editing it.");
            element.transparency=value;changed();layerTable.setRowSelectionInterval(row,row);
        }catch(Exception ex){error(ex);}
    }
    private void applyPictureSize(ImageFormattingToolbar.Size size){
        commit();int row=layerTable.getSelectedRow();if(selectedPicture()==null)return;
        var copy=document.copy();var next=copy.pages.get(pageIndex).objects.get(row);next.width=size.width();next.height=size.height();copy.validate();
        var e=currentPage().objects.get(row);e.width=size.width();e.height=size.height();changed();layerTable.setRowSelectionInterval(row,row);
    }
    private void recolourPicture(){try{
        if(working)throw new IllegalStateException("Wait for the current operation.");commit();if(selectedPicture()==null)return;
        int row=layerTable.getSelectedRow();var element=currentPage().objects.get(row);if(element.locked)throw new IllegalStateException("Unlock this picture before recolouring it.");
        byte[] bytes=document.assetBytes(element.asset);var original=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));if(original==null)throw new java.io.IOException("The picture could not be decoded.");
        var result=ImageRecolourDialog.edit(this,original);if(result!=null)applyPictureRecolour(result);
    }catch(Exception ex){error(ex);}}
    void applyPictureRecolour(java.awt.image.BufferedImage result)throws Exception{
        commit();if(selectedPicture()==null)throw new IllegalStateException("Select one picture to recolour.");int row=layerTable.getSelectedRow();var old=currentPage().objects.get(row);if(old.locked)throw new IllegalStateException("Unlock this picture before recolouring it.");
        var next=document.copy();String asset=UUID.randomUUID().toString();byte[] encoded=ImageRecolour.png(result);if(banners)next.assetFiles.put(asset,PublicationLargeFiles.store(new java.io.ByteArrayInputStream(encoded),PublicationLargeFiles.cache()));else next.assets.put(asset,Base64.getEncoder().encodeToString(encoded));next.pages.get(pageIndex).objects.get(row).asset=asset;
        boolean used=java.util.stream.Stream.concat(java.util.stream.Stream.concat(next.pages.stream(),next.masters.stream()).flatMap(p->p.objects.stream()),next.styles.values().stream()).anyMatch(e->old.asset.equals(e.asset));if(!used){next.assets.remove(old.asset);next.assetFiles.remove(old.asset);}
        next.validate();if(!banners)PublicationFiles.archive(next);document=next;changed();layerTable.setRowSelectionInterval(row,row);status.setText("Picture recoloured • Size, position and transparency preserved. Ctrl+Z to undo.");
    }

    private void generateCode(boolean qr){
        commit();var code=qr?BarcodeEditor.chooseQr(this):BarcodeEditor.choose(this,null);
        if(code==null)return;
        if(code.type.equals("QR")){double side=Math.min(code.width,Math.min(document.width,document.height));code.width=code.height=side;}
        else {code.width=Math.min(code.width,document.width);code.height=Math.min(code.height,document.height);}
        code.x=code.y=0;currentPage().objects.add(code);changed();
        layerTable.setRowSelectionInterval(layers.getRowCount()-1,layers.getRowCount()-1);
    }

    private void insertMergeField(){
        if(records.getColumnCount()==0){error(new IllegalArgumentException("Add a field in Data first."));return;}
        String[] names=new String[records.getColumnCount()];for(int i=0;i<names.length;i++)names[i]=records.getColumnName(i);
        String field=(String)JOptionPane.showInputDialog(this,"Choose an existing data field","Insert merge field",JOptionPane.PLAIN_MESSAGE,null,names,names[0]);
        if(field==null)return;
        commit();Publication.Element text=selectedText();
        if(text==null){text=new Publication.Element();text.text="";text.width=Math.min(78,document.width);text.height=Math.min(10,document.height);currentPage().objects.add(text);}
        text.text+="{{"+field+"}}";changed();
    }

    private Publication.Element selectedBarcode(){if(layerTable.getSelectedRowCount()!=1)return null;int row=layerTable.getSelectedRow();return row>=0&&row<currentPage().objects.size()&&Set.of("BARCODE","QR").contains(currentPage().objects.get(row).type)?currentPage().objects.get(row):null;}
    private void applyBarcodeSettings(BarcodeFormattingToolbar.Settings settings){
        commit();var old=selectedBarcode();if(old==null)return;int selected=layerTable.getSelectedRow();
        var next=Publication.JSON.fromJson(Publication.JSON.toJson(old),Publication.Element.class);
        next.type=settings.format().equals("QR_CODE")?"QR":"BARCODE";next.barcodeFormat=settings.format();next.text=settings.value();next.barcodeText=settings.caption();next.width=settings.width();next.height=settings.height();next.rotation=settings.rotation();next.asset="";
        Publication check=document.copy();check.pages.get(pageIndex).objects.set(selected,next);check.validate();
        currentPage().objects.set(selected,next);changed();layerTable.setRowSelectionInterval(selected,selected);
    }

    private Publication.Page currentPage(){return document.pages.get(pageIndex);}

    private void refreshPages(){addPageCommand.setEnabled(document.pages.size()<500);deletePageCommand.setEnabled(document.pages.size()>1);updatingPages=true;try{pages.removeAllItems();for(int i=0;i<document.pages.size();i++)pages.addItem((i+1)+" · "+document.pages.get(i).name);pages.setSelectedIndex(pageIndex);}finally{updatingPages=false;}pagePreviews.update(document,pageIndex);}

    private void addPage()throws Exception{

        commit();if(document.pages.size()>=500)throw new IllegalArgumentException("At most 500 template pages.");

        Publication.Page page=new Publication.Page();page.name="Page "+(document.pages.size()+1);document.pages.add(page);pageIndex=document.pages.size()-1;

        refreshPages();configureDesign();if(billBooks)pageNumberField();changed();

    }

    private void deletePage(){

        commit();if(document.pages.size()==1)throw new IllegalArgumentException("Keep at least one page.");

        if(JOptionPane.showConfirmDialog(this,"Delete the selected template page? Its table columns will remain.","Business Cards",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;

        document.pages.remove(pageIndex);pageIndex=Math.min(pageIndex,document.pages.size()-1);refreshPages();configureDesign();changed();

    }

    private String numberField(){

        for(var element:currentPage().objects)if(element.type.equals("TEXT")&&element.text.matches("\\{\\{page[0-9]+_number}}"))return element.text.substring(2,element.text.length()-2);

        int n=pageIndex+1;Set<String> used=new HashSet<>();for(var page:document.pages)for(var element:page.objects)used.add(element.text);

        while(used.contains("{{page"+n+"_number}}"))n++;

        return "page"+n+"_number";

    }

    private int ensureColumn(String key)throws Exception{

        for(int c=0;c<records.getColumnCount();c++)if(records.getColumnName(c).equals(key))return c;

        List<String> headers=new ArrayList<>();for(int c=0;c<records.getColumnCount();c++)headers.add(records.getColumnName(c));

        if(headers.size()>=200)throw new IllegalArgumentException("At most 200 table columns.");BusinessCardMerge.validateHeader(key,headers);records.addColumn(key);return records.getColumnCount()-1;

    }

    private void pageNumberField()throws Exception{

        commit();String field=numberField();ensureColumn(field);
        if(billBooks&&!Arrays.asList(billBookNumberFields.getText().split("\\s*,\\s*")).contains(field))billBookNumberFields.setText(billBookNumberFields.getText()+", "+field);

        if(currentPage().objects.stream().noneMatch(e -> e.text.equals("{{"+field+"}}"))){

            Publication.Element element=new Publication.Element();element.text="{{"+field+"}}";element.x=0;element.y=Math.max(0,document.height-8);element.width=Math.min(78,document.width);element.height=Math.min(7,document.height);element.fontSize=10;currentPage().objects.add(element);

        }

        changed();status.setText("This page uses {{"+field+"}}. Fill its table column with Fill number series.");

    }

    private void fillSeries()throws Exception{

        commit();String suggested=table.getSelectedColumn()<0?numberField():records.getColumnName(table.getSelectedColumn());

        JComboBox<String> column=new JComboBox<>();for(int c=0;c<records.getColumnCount();c++)column.addItem(records.getColumnName(c));column.setEditable(true);column.setSelectedItem(suggested);

        String initial="1";int existing=-1;for(int c=0;c<records.getColumnCount();c++)if(records.getColumnName(c).equals(suggested))existing=c;
        if(existing>=0&&records.getRowCount()>0){Object value=records.getValueAt(0,existing);if(value!=null&&!value.toString().isBlank())initial=value.toString();}
        JTextField start=new JTextField(initial),step=new JTextField("1");

        JSpinner count=new JSpinner(new SpinnerNumberModel(Math.max(1,records.getRowCount()),1,10000,1));

        JSpinner firstRow=new JSpinner(new SpinnerNumberModel(Math.max(1,table.getSelectedRow()+1),1,10000,1));

        JCheckBox pad=new JCheckBox("Add leading zeros",true);
        JSpinner digits=new JSpinner(new SpinnerNumberModel(BusinessCardSeries.savedWidth(initial),0,40,1));
        digits.setToolTipText("0 = match largest number; 3 = 001; 4 = 0001. Longer numbers are never cut off.");
        JLabel sample=new JLabel();
        Runnable preview=()->{
            digits.setEnabled(pad.isSelected());
            try{
                var run=pad.isSelected()?BusinessCardSeries.paddedValues(start.getText(),step.getText(),(int)count.getValue(),(int)digits.getValue()):BusinessCardSeries.values(start.getText(),step.getText(),(int)count.getValue(),0);
                sample.setText(run.get(0)+"  …  "+run.get(run.size()-1));
            }catch(IllegalArgumentException ex){sample.setText("Enter valid numbers to preview");}
        };
        javax.swing.event.DocumentListener updates=new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){preview.run();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){preview.run();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){preview.run();}
        };
        start.getDocument().addDocumentListener(updates);step.getDocument().addDocumentListener(updates);
        count.addChangeListener(e->preview.run());digits.addChangeListener(e->preview.run());pad.addActionListener(e->preview.run());preview.run();

        JPanel form=new JPanel(new GridLayout(0,2,8,8));

        form.add(new JLabel("Column (existing or new)"));form.add(column);form.add(new JLabel("Starting number"));form.add(start);form.add(new JLabel("Step"));form.add(step);form.add(new JLabel("Number of values"));form.add(count);form.add(new JLabel("Start at table row"));form.add(firstRow);form.add(new JLabel("Number format"));form.add(pad);form.add(new JLabel("Minimum digits (0 = automatic)"));form.add(digits);form.add(new JLabel("Preview"));form.add(sample);

        if(JOptionPane.showConfirmDialog(this,form,"Fill number series",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;

        String key=column.getSelectedItem().toString().trim();int offset=(int)firstRow.getValue()-1;

        List<String> values=pad.isSelected()?BusinessCardSeries.paddedValues(start.getText(),step.getText(),(int)count.getValue(),(int)digits.getValue()):BusinessCardSeries.values(start.getText(),step.getText(),(int)count.getValue(),0);

        if(offset+values.size()>10000)throw new IllegalArgumentException("The series exceeds 10000 table rows.");

        int target=-1;for(int c=0;c<records.getColumnCount();c++)if(records.getColumnName(c).equals(key))target=c;

        if(target>=0){boolean overwrite=false;for(int r=offset;r<Math.min(records.getRowCount(),offset+values.size());r++){Object value=records.getValueAt(r,target);if(value!=null&&!value.toString().isBlank())overwrite=true;}

            if(overwrite&&JOptionPane.showConfirmDialog(this,"Replace existing numbers in this column and row range? Other columns are preserved.","Fill number series",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;}

        target=ensureColumn(key);while(records.getRowCount()<offset+values.size())records.addRow(new Object[records.getColumnCount()]);

        for(int i=0;i<values.size();i++)records.setValueAt(values.get(i),offset+i,target);

        table.setRowSelectionInterval(offset,offset);table.setColumnSelectionInterval(target,target);status.setText("Filled "+values.size()+" values in "+key+" • use {{"+key+"}} in this page's text.");

    }

    private void syncCanvasSelection(){syncingLayerSelection=true;try{layerTable.clearSelection();for(int row:design.selectedIndices())if(row<layers.getRowCount())layerTable.addRowSelectionInterval(row,row);}finally{syncingLayerSelection=false;}}

    void toggleLayerVisibility(int index){
        commit();if(index<0||index>=currentPage().objects.size())return;
        var element=currentPage().objects.get(index);element.hidden=!element.hidden;changed();
        layerTable.setRowSelectionInterval(index,index);
        status.setText(element.hidden?"Layer hidden. Save the template to keep this change.":"Layer shown. Save the template to keep this change.");
    }

    private void showLayers(){
        if(GraphicsEnvironment.isHeadless())return;
        if(layersWindow!=null&&layersWindow.isDisplayable()){floatingLayers.refresh();layersWindow.setVisible(true);layersWindow.toFront();return;}
        Window owner=SwingUtilities.getWindowAncestor(this);layersWindow=new JDialog(owner,"Layers",Dialog.ModalityType.MODELESS);
        layersWindow.setUndecorated(true);
        if(layersWindow.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT))layersWindow.setBackground(new Color(0,0,0,0));
        floatingLayers=new StudioLayersPanel(()->currentPage().objects,()->layerTable.getSelectedRow(),index->{if(index>=0&&index<layerTable.getRowCount())layerTable.setRowSelectionInterval(index,index);},index->{try{toggleLayerVisibility(index);}catch(Exception ex){error(ex);}},move->{try{reorderLayers(move);}catch(Exception ex){error(ex);}},()->{try{commit();design.deleteSelection();}catch(Exception ex){error(ex);}},()->layersWindow.dispose());
        layersWindow.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);layersWindow.setContentPane(floatingLayers);
        layersWindow.getRootPane().registerKeyboardAction(e->layersWindow.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if(owner!=null){Rectangle screen=owner.getGraphicsConfiguration().getBounds();Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(owner.getGraphicsConfiguration());usable=new Rectangle(screen.x+insets.left,screen.y+insets.top,screen.width-insets.left-insets.right,screen.height-insets.top-insets.bottom);}
        layersWindow.setSize(Math.min(280,usable.width-32),Math.min(370,usable.height-48));layersWindow.setMinimumSize(new Dimension(260,220));
        Point anchor=isShowing()?getLocationOnScreen():new Point(usable.x+16,usable.y+16);
        layersWindow.setLocation(Math.max(usable.x,Math.min(anchor.x+24,usable.x+usable.width-layersWindow.getWidth())),Math.max(usable.y,Math.min(anchor.y+120,usable.y+usable.height-layersWindow.getHeight())));
        layersWindow.setVisible(true);
    }

    private void changeLayerOrder(StudioLayerOrder.Move move){try{reorderLayers(move);}catch(Exception ex){error(ex);}}
    void reorderLayers(StudioLayerOrder.Move move){
        commit();
        var result=StudioLayerOrder.move(currentPage().objects,layerTable.getSelectedRows(),move,e->e.locked);
        if(!result.changed())return;
        changed(result.selection());
        status.setText("Layer order updated. Ctrl+Z to undo.");
    }

    private void configureDesign(){

        design.configure(new TemplateWorkspace.Design(){
            public double maximumSize(){return banners?BannerDocuments.MAX_MM:1000;}
            public boolean constrainSizeToPage(){return !banners;}

            public double width(){return document.width;}public double height(){return document.height;}public int size(){return currentPage().objects.size();}

            public double rotation(int i){return currentPage().objects.get(i).rotation;}
            public Rectangle2D bounds(int i){return TableGeometry.bounds(currentPage(),currentPage().objects.get(i));}

            public void move(int i,double x,double y){TableGeometry.move(currentPage(),currentPage().objects.get(i),x,y);}

            public boolean canResize(){return true;}

            public void resize(int i,Rectangle2D b){TableGeometry.resize(currentPage(),currentPage().objects.get(i),b);}

            public void remove(int i){currentPage().objects.remove(i);}
            public boolean locked(int i){return TableGeometry.locked(currentPage(),currentPage().objects.get(i));}
            public boolean hidden(int i){return currentPage().objects.get(i).hidden;}

            public String text(int i){var e=currentPage().objects.get(i);return "TEXT".equals(e.type)?e.text:null;}

            public boolean setText(int i,String value){var e=currentPage().objects.get(i);String old=e.text;e.text=value;try{document.validate();return true;}catch(Exception ex){e.text=old;return false;}}

            public void paint(Graphics2D g,double scale){
                Graphics2D draw=(Graphics2D)g.create();
                try{
                    draw.scale(scale,scale);
                    canvasRenderer().page(draw,pageIndex,selectedRecord());
                    paintEmptyPhotoPlaceholders(draw,scale);
                }finally{draw.dispose();}
            }

        },r -> syncCanvasSelection(),this::changed);

    }

    private Publication.Element selectedTableElement(){
        int row=layerTable.getSelectedRow();if(row<0||row>=currentPage().objects.size()||!StudioTables.isTable(currentPage().objects.get(row)))throw new IllegalArgumentException("Select a table cell first, then choose Table > Edit table.");return currentPage().objects.get(row);
    }

    private void selectTable(){
        commit();var grid=StudioTables.read(currentPage(),selectedTableElement());
        int index=java.util.stream.IntStream.range(0,currentPage().objects.size()).filter(i->grid.replaced.contains(currentPage().objects.get(i).id)).findFirst().orElseThrow();design.select(index);syncCanvasSelection();
    }

    private void editTable(boolean creating){
        commit();StudioTables.Grid grid=creating?StudioTables.create(5,3,Math.max(.3,Math.min(120,document.width-10)),Math.max(.5,Math.min(50,document.height-10))):StudioTables.read(currentPage(),selectedTableElement());
        if(creating){grid.x=Math.max(0,Math.min(5,(document.width-grid.width())/2));grid.y=Math.max(0,Math.min(5,(document.height-grid.height())/2));}
        grid=TableEditor.choose(this,grid,creating);if(grid==null)return;
        Publication next=document.copy();int index=StudioTables.apply(next,pageIndex,grid);document=next;configureDesign();changed();design.selectIndices(new int[]{index});syncCanvasSelection();status.setText("Table updated • Drag to move; drag a handle to resize; double-click to edit cells. Ctrl+Z to undo.");
    }

    private void browseMergeRecords(){
        if(shell.recordsOpen()){shell.closeRecords();return;}
        try{
            commit();
            var design=document.copy();
            int previewPage=pageIndex;
            var columns=new LinkedHashMap<String,String>();
            for(int col=0;col<records.getColumnCount();col++)columns.put("field-"+col,records.getColumnName(col));
            var rows=new ArrayList<StudioRecordBrowser.Row<Integer>>();
            for(int row=0;row<records.getRowCount();row++){
                var values=new LinkedHashMap<String,String>();
                for(int col=0;col<records.getColumnCount();col++)values.put("field-"+col,Objects.toString(records.getValueAt(row,col),""));
                rows.add(new StudioRecordBrowser.Row<>(row,values));
            }
            var browser=new StudioRecordBrowser<Integer>(design.title,columns,record->{
                var values=new LinkedHashMap<String,String>();
                columns.forEach((key,label)->values.put(label,record.values().get(key)));
                double scale=460/Math.max(design.width,design.height);
                var image=new java.awt.image.BufferedImage(Math.max(1,(int)Math.ceil(design.width*scale)),
                        Math.max(1,(int)Math.ceil(design.height*scale)),java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D draw=image.createGraphics();
                try{
                    draw.scale(scale,scale);
                    new PublicationRenderer(design).page(draw,previewPage,values);
                }finally{draw.dispose();}
                return new StudioRecordBrowser.Details(image,()->{
                    shell.closeRecords();
                    int viewRow=table.convertRowIndexToView(record.id());
                    if(viewRow>=0){
                        table.setRowSelectionInterval(viewRow,viewRow);
                        if(table.getColumnCount()>0)table.scrollRectToVisible(table.getCellRect(viewRow,0,true));
                    }
                });
            },shell::closeRecords);
            shell.showRecords(browser,()->rows);
        }catch(Exception ex){error(ex);}
    }

    private Map<String,String> selectedRecord(){int r=table.getSelectedRow();if(r<0 && records.getRowCount()>0)r=0;return r<0?Map.of():row(r);}

    private Map<String,String> row(int r){Map<String,String> values=new LinkedHashMap<>();for(int c=0;c<records.getColumnCount();c++){Object v=records.getValueAt(r,c);String s=v==null?"":v.toString();values.put(records.getColumnName(c),s);}return values;}

    private void commit(){if(!design.finishText(true))throw new IllegalStateException("Finish editing the text.");for(JTable t:List.of(table,layerTable))if(t.isEditing()&&!t.getCellEditor().stopCellEditing())throw new IllegalStateException("Finish editing the current cell.");}

    Publication output(){

        commit();Publication p=document.copy();if(banners){p.mergeRows.clear();p.validate();return p;}p.mergeRows.clear();p.mergeColumns.clear();for(int c=0;c<records.getColumnCount();c++)p.mergeColumns.add(records.getColumnName(c));

        for(int r=0;r<records.getRowCount();r++)p.mergeRows.add(row(r));

        double[] size=BusinessCardPaper.size(paper.getSelectedItem().toString(),((Number)customWidth.getValue()).doubleValue(),((Number)customHeight.getValue()).doubleValue());p.print.width=size[0];p.print.height=size[1];

        p.print.left=((Number)leftMargin.getValue()).doubleValue()*25.4;p.print.right=((Number)rightMargin.getValue()).doubleValue()*25.4;
        p.print.top=((Number)topMargin.getValue()).doubleValue()*25.4;p.print.bottom=((Number)bottomMargin.getValue()).doubleValue()*25.4;

        p.print.gapX=((Number)gap.getValue()).doubleValue()*25.4;p.print.gapY=((Number)verticalGap.getValue()).doubleValue()*25.4;p.print.quantity=(int)copies.getValue();

        p.print.maximize=true;p.print.rotate=rotate.isSelected();p.print.cropMarks=marks.isSelected();

        p.print.mixPages=pageMode.getSelectedIndex()==1;
        if(numberingControls!=null)numberingControls.apply(p.print);

        p.validate();return p;

    }

    Publication printOutput(){
        Publication p=output();
        if(!billBooks)return p;
        return switch(billBookOutput.getSelectedIndex()){
            case 1 -> BillBookNumbering.numbersOnly(p,billBookNumberFields.getText());
            case 2 -> BillBookNumbering.templateOnly(p,billBookNumberFields.getText());
            default -> p;
        };
    }

    private void insertShape(String type){
        try{
            if(working)throw new IllegalStateException("Wait for the current operation.");
            commit();
            var shape=new Publication.Element();
            shape.type=type;
            shape.width=Math.min(25,document.width*.6);
            shape.height=Math.min(type.equals("LINE")?.5:15,document.height*.6);
            shape.x=(document.width-shape.width)/2;
            shape.y=(document.height-shape.height)/2;
            String colour=String.format("#%06x",DeckersPalette.PURPLE.getRGB()&0xffffff);
            shape.color=colour;
            shape.fill=type.equals("LINE")?"none":colour;
            shape.stroke=type.equals("LINE")?colour:"none";
            var objects=currentPage().objects;
            objects.add(shape);
            changed(new int[]{objects.size()-1});
        }catch(Exception ex){error(ex);}
    }

    private void changed(){changed(layerTable.getSelectedRows());}
    private void changed(int[] selected){
        LinkedRiso.prune(document);thumbnailRefresh.restart();recordHistory();updateTemplateSize();design.revalidate();dirty=true;
        syncingLayerSelection=true;updatingLayerRows=true;
        try{layers.fireTableDataChanged();for(int row:selected)if(row<layerTable.getRowCount())layerTable.addRowSelectionInterval(row,row);}
        finally{updatingLayerRows=false;syncingLayerSelection=false;}
        refreshLayerFormatting.run();
        design.selectIndices(layerTable.getSelectedRows());design.repaint();refresh.restart();
    }

    private void previewSheet(){

        if(banners){status.setText(sizeDescription(document)+" • Ctrl+wheel zoom • scrollbars / middle-drag pan • Fit resets view • Print through your installed driver or export PDF.");return;}
        if(working){previewPending=true;return;}

        try{Publication p=printOutput();int index=(int)sheet.getValue()-1;PublicationRenderer renderer=new PublicationRenderer(p);int count=renderer.sheets();

            if(index>=count){sheet.setValue(count);index=count-1;}final int page=index;

            run("Rendering sheet…",() -> renderer.raster(page,72),img -> {sheetImage.setImage(img);status.setText(SheetLayout.pack(p.width,p.height,p.print).capacity()+(labels?" labels / sheet • ":" cards / sheet • ")+count+" sheets • "+sizeDescription(p)+" • "+String.join("; ",renderer.warnings()));});

        }catch(Exception ex){sheetImage.setImage(null);status.setText(ex.getMessage());}

    }

    private void openTemplate()throws Exception{

        if(!discard())return;Path path=choose(false,"Publisher / drawing template","sspub","pub","pptx","pptm","odg");if(path==null)return;

        openTemplate(path);

    }

    private void openTemplate(Path path)throws Exception{
        openDocument(path);
    }

    void openDocument(Path path)throws Exception{
        openDocument(path,null);
    }

    private void openDocument(Path path,publishing.StudioDriveIdentity identity)throws Exception{

        String[] openedHash={null};

        Path converter=null;if(!path.toString().toLowerCase(Locale.ROOT).endsWith(".sspub")){converter=PublisherConverter.find();if(converter==null)converter=choose(false,"LibreOffice soffice executable","exe","com");if(converter==null)return;PublisherConverter.remember(converter);}final Path executable=converter;

        run("Opening template…",() -> {if(Files.size(path)>(banners?PublicationLargeFiles.MAX_BYTES:PublicationFiles.MAX_BYTES))throw new java.io.IOException(banners?"Template exceeds 5 GiB.":"Template exceeds 48 MB.");String before=BadgeTemplateLibrary.fingerprint(path);Publication p=readTemplate(path,executable,labels,banners);if(!before.equals(BadgeTemplateLibrary.fingerprint(path)))throw new java.io.IOException("Template changed while opening; refresh and reopen it.");openedHash[0]=before;

            var fileIdentity=publishing.StudioDriveIdentity.fromFile(path);
            if(fileIdentity!=null)p.smartDrive=fileIdentity;
            else if(identity!=null)p.smartDrive=identity;
            if(p.pages.stream().anyMatch(pg -> pg.reference))throw new java.io.IOException("Choose printable pages; remove reference pages in Publisher first.");return p;},p -> {currentSharedFile=shared!=null&&shared.contains(path)?path:null;currentSharedHash=currentSharedFile==null?"":openedHash[0];currentLocalFile=currentSharedFile==null&&path.toString().toLowerCase(Locale.ROOT).endsWith(".sspub")?path:null;currentLocalHash=currentLocalFile==null?"":openedHash[0];document=p;document.displayUnit="IN";pageIndex=0;refreshPages();restoreSheetSize(p.print);pageMode.setSelectedIndex(p.print.mixPages?1:0);loadRows(p.mergeColumns,p.mergeRows);excel=null;source.setText("Built-in table");configureDesign();resetHistory();changed();dirty=false;if(path.toString().toLowerCase(Locale.ROOT).endsWith(".pub"))JOptionPane.showMessageDialog(this,"Publisher objects and text fields were imported separately.\nReview fonts, colours, rotated text and every page against the original before printing.","Publisher import",JOptionPane.INFORMATION_MESSAGE);});

    }

    void restoreSheetSize(SheetLayout.Settings settings){
        customWidth.setValue(settings.width/25.4);customHeight.setValue(settings.height/25.4);
        String selected="Custom";
        for(String candidate:new String[]{"Letter","A4","Legal","11 × 17"}){
            double[] size=BusinessCardPaper.size(candidate,1,1);
            if(Math.abs(size[0]-settings.width)<.01&&Math.abs(size[1]-settings.height)<.01){selected=candidate;break;}
        }
        paper.setSelectedItem(selected);
        leftMargin.setValue(settings.left/25.4);rightMargin.setValue(settings.right/25.4);
        topMargin.setValue(settings.top/25.4);bottomMargin.setValue(settings.bottom/25.4);gap.setValue(settings.gapX/25.4);verticalGap.setValue(settings.gapY/25.4);
        copies.setValue(Math.max(1,settings.quantity));rotate.setSelected(settings.rotate);marks.setSelected(settings.cropMarks);
        pageMode.setSelectedIndex(settings.mixPages?1:0);
        if(numberingControls!=null)numberingControls.restore(settings);
    }

    void previewPrintSetup(){
        try{showPrintPreview(printOutput(),false);}catch(Exception ex){error(ex);}
    }

    private boolean showPrintPreview(Publication p){return showPrintPreview(p,true);}

    private boolean showPrintPreview(Publication p,boolean printing){

        int count=new PublicationRenderer(p).sheets();

        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),(billBooks?"Bill books":labels?"Labels":"Business cards")+" — "+(printing?"full sheet preview":"sheet preview"),Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);dialog.setLayout(new BorderLayout(8,8));

        SheetPreview fullSheet=new SheetPreview();dialog.add(fullSheet);

        JLabel details=new JLabel(String.format(Locale.ROOT,"%.2f × %.2f inches • %d sheets • %s",p.print.width/25.4,p.print.height/25.4,count,p.print.duplex?"Front + back ("+(PublicationPrintOptions.sides(p)==javax.print.attribute.standard.Sides.TWO_SIDED_SHORT_EDGE?"short":"long")+" edge)":p.print.mixPages?"Mixed pages • single-sided":"Separate pages • single-sided"));

        JPanel top=DeckersSwing.panel();top.setLayout(new FlowLayout(FlowLayout.LEFT));top.add(details);top.add(new JLabel("Sheet"));

        JSpinner number=new JSpinner(new SpinnerNumberModel(Math.max(1,Math.min(count,(int)sheet.getValue())),1,count,1));top.add(number);dialog.add(top,BorderLayout.NORTH);

        JPanel actions=DeckersSwing.panel();actions.setLayout(new FlowLayout(FlowLayout.RIGHT));JLabel note=new JLabel("Rendering…");actions.add(note);

        JButton cancel=new JButton(printing?"Cancel":"Close"),print=new JButton("Continue to printer…");DeckersSwing.styleUtilityButton(cancel,DeckersPalette.PURPLE);DeckersSwing.styleUtilityButton(print,DeckersPalette.PURPLE);actions.add(cancel);if(printing)actions.add(print);dialog.add(actions,BorderLayout.SOUTH);

        boolean[] accepted={false};int[] generation={0};

        Runnable render=()->{int current=++generation[0],index=(int)number.getValue()-1;print.setEnabled(false);fullSheet.setImage(null);note.setText("Rendering sheet "+(index+1)+"…");

            new SwingWorker<java.awt.image.BufferedImage,Void>(){

                protected java.awt.image.BufferedImage doInBackground(){return new PublicationRenderer(p).raster(index,96);}

                protected void done(){if(current!=generation[0]||!dialog.isDisplayable())return;try{fullSheet.setImage(get());note.setText("Sheet "+(index+1)+" of "+count);print.setEnabled(true);}catch(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();note.setText("Unable to render: "+cause.getMessage());note.setToolTipText(cause.getMessage());}}

            }.execute();};

        number.addChangeListener(e->render.run());cancel.addActionListener(e->dialog.dispose());print.addActionListener(e->{accepted[0]=true;dialog.dispose();});

        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Window owner=SwingUtilities.getWindowAncestor(this);
        if(owner!=null){Rectangle screen=owner.getGraphicsConfiguration().getBounds();Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(owner.getGraphicsConfiguration());usable=new Rectangle(screen.x+insets.left,screen.y+insets.top,screen.width-insets.left-insets.right,screen.height-insets.top-insets.bottom);}
        int width=Math.max(1,Math.min(850,usable.width-32)),height=Math.max(1,Math.min(760,usable.height-32));
        dialog.setSize(width,height);dialog.setMinimumSize(new Dimension(Math.min(560,width),Math.min(440,height)));
        StudioVisuals.styleTree(dialog.getContentPane());dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setLocationRelativeTo(this);dialog.setLocation(Math.max(usable.x,Math.min(dialog.getX(),usable.x+usable.width-width)),Math.max(usable.y,Math.min(dialog.getY(),usable.y+usable.height-height)));
        dialog.addNotify();render.run();dialog.setVisible(true);generation[0]++;return accepted[0];

    }

    private void installFileView(StudioWorkspaceShell shell){
        fileView=shell.installFile(new StudioFileView.Actions(){
            public StudioFileView.Info info(){return new StudioFileView.Info(billBooks?"Bill Books":banners?"Banners":labels?"Labels":"Business Cards",currentLocalFile==null?document.title:currentLocalFile.getFileName().toString().replaceFirst("(?i)\\.sspub$",""),".sspub",currentSharedFile!=null?"Shared template library":currentLocalFile!=null?currentLocalFile.getParent().toString():"Unsaved draft",sizeDescription(document),document.pages.size(),dirty,working||productionBusy(),shared!=null&&sharedActive&&shared.online,currentSharedFile!=null||currentLocalFile!=null,currentLocalFile);}
            public void create(){Publication before=document;boolean changed=dirty;dirty=false;newCard();if(document==before)dirty=changed;shell.ribbon().select("Home");}
            public void open(Path path)throws Exception{Publication before=document;boolean changed=dirty;dirty=false;try{if(path==null)chooseTemplate();else openTemplate(path);}finally{if(document==before)dirty=changed;}shell.ribbon().select("Home");}
            public void save()throws Exception{saveChanges();}
            public void saveAs(String name,Path folder,boolean store)throws Exception{saveAsFile(name,folder,store);}
            public void rename(String name)throws Exception{renameFile(name);}
            public void importArtwork()throws Exception{artwork();}
            public String[] exportFormats(){return new String[]{"PDF document (.pdf)","Portable template (.sspub)"};}
            public void export(String format)throws Exception{if(format.startsWith("PDF"))exportPdf();else saveTemplate();}
            public void print()throws Exception{printCards();}
            public void printSetup(){shell.ribbon().select("Print Setup");}
            public void close(){dirty=false;resetFields();document=new Publication();pageIndex=0;refreshPages();configureDesign();resetHistory();firePropertyChange("closeWorkspace",false,true);}
        });
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control shift S"),"saveAsFile");getActionMap().put("saveAsFile",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){shell.ribbon().select("File");fileView.show("save-as");}});
    }
    void saveAsFile(String name,Path folder,boolean store)throws Exception{
        if(working)throw new IllegalStateException("Wait for the current operation.");Publication next=output();next.title=StudioFileNames.clean(name,".sspub");next.smartDrive=null;
        if(store){if(shared==null||!sharedActive||!shared.online)throw new java.io.IOException("Reconnect to save shared templates.");shared.requireUniqueName(next.title,null);var active=shared;long generation=sharedGeneration;run("Saving new shared copy…",()->active.save(next,null,"",()->sharedActive&&generation==sharedGeneration),saved->{currentSharedFile=saved.file();currentSharedHash=saved.sha256();currentLocalFile=null;document.title=next.title;savedState(next);status.setText("Saved new shared copy.");});}
        else{Path target=StudioFileNames.target(folder,name,".sspub");if(currentLocalFile!=null&&target.equals(currentLocalFile.toAbsolutePath().normalize()))throw new java.io.IOException("Choose a different name for the new copy.");String expected=StudioDocumentSave.observe(target);if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"Replace "+target.getFileName()+"?","Save As",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;saveLocal(next,target,expected);}
    }
    void renameFile(String name)throws Exception{
        if(working)throw new IllegalStateException("Wait for the current operation.");String clean=StudioFileNames.clean(name,".sspub");
        if(currentSharedFile!=null){shared.requireUniqueName(clean,currentSharedFile);Publication next=PublicationFiles.read(Files.readAllBytes(currentSharedFile));next.title=clean;saveShared(next);return;}
        Path source=currentLocalFile;String expected=currentLocalHash;
        if(source==null)throw new java.io.IOException("Save this draft with Save As before renaming it.");
        run("Renaming template…",()->{
            Publication saved=banners?PublicationLargeFiles.read(source,PublicationLargeFiles.cache()):PublicationFiles.read(Files.readAllBytes(source));saved.title=clean;
            Path target=StudioFileNames.renameWithContent(source,clean,".sspub",expected,path->writeTemplate(path,saved,null,banners));
            return new LocalRename(target,BadgeTemplateLibrary.fingerprint(target),saved);
        },result->{currentLocalFile=result.file();currentLocalHash=result.hash();document.title=clean;recordHistory();savedState(result.saved());status.setText("Renamed template: "+clean);});
    }

    private record LocalRename(Path file,String hash,Publication saved){}
    void saveChanges()throws Exception{
        if(working)throw new IllegalStateException("Wait for the current operation.");
        if(currentSharedFile!=null){saveShared(output());return;}
        if(currentLocalFile==null){saveToLibrary();return;}
        Publication p=output();Path target=currentLocalFile;String expected=currentLocalHash;
        saveLocal(p,target,expected);
    }

    private void savedState(Publication snapshot){
        try{dirty=!sameDesign(output(),snapshot);}catch(Exception ex){dirty=true;}
    }

    private void saveLocal(Publication p,Path target,String expected)throws Exception{
        String revision=expected==null?StudioDocumentSave.observe(target):expected;
        if(currentLocalFile==null||!target.toAbsolutePath().normalize().equals(currentLocalFile.toAbsolutePath().normalize()))
            p.smartDrive=banners||vinylWorkspace?null:publishing.StudioDriveIdentity.forNewFile(target,billBooks?"BILL_BOOKS":labels?"LABELS":"BUSINESS_CARDS");
        Publication snapshot=p.copy();p.template=true;
        run("Saving template…",()->writeTemplate(target,p,revision,banners),hash->{currentLocalFile=target;currentLocalHash=hash;currentSharedFile=null;currentSharedHash="";document.title=p.title;document.smartDrive=p.smartDrive;savedState(snapshot);status.setText("Saved: "+target.getFileName()+" • Fields and records saved with this template.");});
    }

    static String writeTemplate(Path target,Publication p,String expected)throws Exception{
        return writeTemplate(target,p,expected,!p.assetFiles.isEmpty());
    }
    static String writeTemplate(Path target,Publication p,String expected,boolean large)throws Exception{
        return StudioDocumentSave.write(target,expected,path->{
            if(large)PublicationLargeFiles.write(path,p);
            else PublicationFiles.writeAtomic(path,PublicationFiles.archive(p));
        });
    }

    private void saveShared(Publication p)throws Exception{
        if(shared==null||!sharedActive||!shared.online)throw new java.io.IOException("Offline mode: reconnect and sign in to save a shared template.");
        Publication snapshot=p.copy();var active=shared;Path current=currentSharedFile;String expected=currentSharedHash;long generation=sharedGeneration;
        run("Saving template on the store server…",()->active.save(p,current,expected,()->sharedActive&&generation==sharedGeneration),saved->{currentSharedFile=saved.file();currentSharedHash=saved.sha256();currentLocalFile=null;currentLocalHash="";document.title=p.title;snapshot.title=p.title;savedState(snapshot);sharedLibraryStatus.setText(active.status);status.setText("Saved on server. Other computers refresh automatically; OneDrive backup is queued.");});
    }

    private void saveTemplate()throws Exception{
        Publication p=output();JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Publisher template");
        Path driveRoot=shared!=null&&shared.smartDrive?SmartDriveTemplatePicker.suggestedRoot():null;
        if(driveRoot!=null)chooser.setCurrentDirectory(driveRoot.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Publisher template","sspub"));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path path=chooser.getSelectedFile().toPath();if(!path.toString().toLowerCase(Locale.ROOT).endsWith(".sspub"))path=Path.of(path+".sspub");
        String expected=StudioDocumentSave.observe(path);
        if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace "+path.getFileName()+"?","Publisher template",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        saveLocal(p,path,expected);
    }

    private void saveToLibrary()throws Exception{
        if(shared!=null&&shared.smartDrive){saveTemplate();return;}

        Publication p=output();String name=JOptionPane.showInputDialog(this,"Template name",p.title);if(name==null)return;name=name.strip();if(name.isBlank()||name.length()>120||name.matches(".*[<>:\"/\\\\|?*].*"))throw new IllegalArgumentException("Use a template name without path characters.");

        if(shared!=null){

            if(!sharedActive||!shared.online)throw new java.io.IOException("Offline mode: reconnect and sign in to save a shared template.");

            p.title=name;saveShared(p);return;

        }

        Path target=(billBooks?BillBookDocuments.folder():banners?BannerDocuments.folder():PublicationTemplateLibrary.folder(labels)).resolve(name+".sspub");String expected=StudioDocumentSave.observe(target);if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"Replace the existing library template?","Save template",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;

        document.title=name;p.title=name;saveLocal(p,target,expected);

    }

    private void artwork()throws Exception{
        int source=StudioPhotoPicker.source(this);
        if(source==0){Path path=choose(false,"Image","png","jpg","jpeg");if(path!=null)loadImageLayer(()->banners?StudioImageFile.loadArtwork(path):StudioImageFile.load(path));}
        else if(source==1){var photo=StudioPhotoPicker.queued(this);if(photo!=null){var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(photo.image(),"png",bytes);insertImageLayer(new StudioImageFile.Loaded(bytes.toByteArray(),photo.image(),photo.name()));}}
    }

    private void insertPhotoPlaceholder(){
        try{
            if(working)throw new IllegalStateException("Wait for the current operation.");
            commit();
            var slot=new Publication.Element();
            slot.type="IMAGE";
            slot.text="Photo placeholder";
            slot.photoPlaceholder=true;
            slot.height=Math.min(27,document.height*.65);
            slot.width=Math.min(slot.height*.75,document.width*.65);
            slot.x=(document.width-slot.width)/2;
            slot.y=(document.height-slot.height)/2;
            var objects=currentPage().objects;
            objects.add(slot);
            changed(new int[]{objects.size()-1});
        }catch(Exception ex){error(ex);}
    }

    private void paintEmptyPhotoPlaceholders(Graphics2D graphics,double scale){
        for(var layer:currentPage().objects){
            if(!layer.photoPlaceholder||layer.hidden||!layer.asset.isBlank())continue;
            var draw=(Graphics2D)graphics.create();
            try{
                draw.translate(layer.x,layer.y);
                draw.rotate(Math.toRadians(layer.rotation),layer.width/2,layer.height/2);
                float pixel=(float)(1/scale);
                draw.setColor(DeckersPalette.PURPLE);
                draw.setStroke(new BasicStroke(pixel,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,1,
                        new float[]{4*pixel,3*pixel},0));
                draw.draw(new java.awt.geom.Rectangle2D.Double(0,0,layer.width,layer.height));
                String label="Photo";
                draw.setFont(StudioVisuals.font(Font.PLAIN,12).deriveFont(12*pixel));
                var metrics=draw.getFontMetrics();
                if(metrics.stringWidth(label)+4*pixel<layer.width)
                    draw.drawString(label,(float)(layer.width-metrics.stringWidth(label))/2,
                            (float)(layer.height+metrics.getAscent())/2);
            }finally{draw.dispose();}
        }
    }

    private int selectedPhotoPlaceholder(){
        int row=layerTable.getSelectedRow();
        if(layerTable.getSelectedRowCount()!=1||row<0||row>=currentPage().objects.size()
                ||!currentPage().objects.get(row).photoPlaceholder)
            throw new IllegalArgumentException("Select one photo placeholder first.");
        return row;
    }

    private void replaceSelectedPhoto(){
        try{replacePhoto(selectedPhotoPlaceholder());}catch(Exception ex){error(ex);}
    }

    private void frameSelectedPhoto(){
        try{
            if(working)throw new IllegalStateException("Wait for the current operation.");
            commit();
            int row=selectedPhotoPlaceholder();
            var slot=currentPage().objects.get(row);
            if(slot.locked)throw new IllegalArgumentException("Unlock the photo placeholder before framing it.");
            if(slot.asset.isBlank())throw new IllegalArgumentException("Add a photo to this placeholder first.");
            var source=PublicationPhotoFrame.source(document,slot);
            var frame=PublicationPhotoFrame.choose(this,slot,source);
            if(frame!=null){frame.apply(slot,source);changed(new int[]{row});}
        }catch(Exception ex){error(ex);}
    }

    private void replacePhoto(int row)throws Exception{
        if(working)throw new IllegalStateException("Wait for the current operation.");
        commit();Publication target=document;int targetPage=pageIndex;
        var selected=currentPage().objects.get(row);
        if(selected.photoPlaceholder&&selected.locked)throw new IllegalArgumentException("Unlock the photo placeholder before replacing its photo.");
        var image=StudioPhotoPicker.photo(this,()->{Path path=choose(false,"Image","png","jpg","jpeg");return path==null?null:StudioImageFile.loadPhoto(path).image();});
        if(image==null||document!=target||pageIndex!=targetPage)return;
        replacePhoto(row,image);
    }

    void replacePhoto(int row,java.awt.image.BufferedImage image)throws Exception{
        var original=currentPage().objects.get(row);
        var frame=PublicationPhotoFrame.CENTERED;
        if(original.photoPlaceholder&&!original.asset.isBlank())
            frame=PublicationPhotoFrame.from(original,PublicationPhotoFrame.source(document,original));
        Publication next=document.copy();var page=next.pages.get(pageIndex);var element=page.objects.get(row);
        PublicationImport.addImage(next,page,image,element.x,element.y,element.width,element.height);
        element.asset=page.objects.remove(page.objects.size()-1).asset;
        if(element.photoPlaceholder)frame.apply(element,image);
        next.validate();document=next;changed(new int[]{row});
    }


    private void copyLayers(){try{commit();int[] rows=design.selectedIndices();if(rows.length>0)StudioLayerClipboard.put(StudioLayerClipboard.publication(document,pageIndex,rows));}catch(Exception ex){error(ex);}}
    private void pasteLayers(){try{
        if(working)throw new IllegalStateException("Wait for the current operation.");
        commit();var data=StudioLayerClipboard.contents();if(data==null)return;int first=currentPage().objects.size();
        if(data.isDataFlavorSupported(StudioLayerClipboard.FLAVOR))document=StudioLayerClipboard.paste(document,pageIndex,(StudioLayerClipboard.Payload)data.getTransferData(StudioLayerClipboard.FLAVOR));
        else if(StudioImageTransfer.supports(data)){pasteImageLayer();return;}
        else if(data.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)){
            Publication next=document.copy();var e=new Publication.Element();e.text=(String)data.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);e.width=Math.min(60,next.width);e.height=Math.min(15,next.height);next.pages.get(pageIndex).objects.add(e);next.validate();document=next;
        }else throw new IllegalArgumentException("Copy layers, text, or an image first.");
        changed();layerTable.setRowSelectionInterval(first,currentPage().objects.size()-1);design.requestFocusInWindow();
    }catch(Exception ex){error(ex);}}
    private void pasteImageLayer(){try{if(working)throw new IllegalStateException("Wait for the current operation.");loadImageLayer(CanvasImageInsert.clipboard(banners));}catch(Exception ex){error(ex);}}

    private void loadImageLayer(java.util.concurrent.Callable<StudioImageFile.Loaded> loader){

        commit();Publication target=document;int targetPage=pageIndex;

        run("Loading image…",loader,image->{if(document!=target||pageIndex!=targetPage)return;try{insertImageLayer(image);}catch(Exception ex){error(ex);}});

    }

    void insertImageLayer(StudioImageFile.Loaded image)throws Exception{

        commit();Publication next=document.copy();double[] size=CanvasImageInsert.fit(image.sourceWidth(),image.sourceHeight(),next.width,next.height);

        if(banners)PublicationImport.addEncodedImage(next,next.pages.get(pageIndex),image.bytes(),(next.width-size[0])/2,(next.height-size[1])/2,size[0],size[1]);else PublicationImport.addImage(next,next.pages.get(pageIndex),image.image(),(next.width-size[0])/2,(next.height-size[1])/2,size[0],size[1]);next.validate();document=next;changed();

        int row=currentPage().objects.size()-1;layerTable.setRowSelectionInterval(row,row);design.requestFocusInWindow();
        if (banners) {
            double ppi = Math.min(image.sourceWidth() * 25.4 / size[0], image.sourceHeight() * 25.4 / size[1]);
            status.setText(String.format(java.util.Locale.ROOT, "Image inserted • %d × %d px • %.1f PPI at placed size",
                    image.sourceWidth(), image.sourceHeight(), ppi));
        }

    }

    void receiveArtwork(StudioImageFile.Loaded image)throws Exception{
        if(working||productionBusy())throw new IllegalStateException("Wait for the current operation.");
        insertImageLayer(image);
        shell.closeRecords();
        shell.ribbon().select("Home");
    }

    private void addField()throws Exception{String key=JOptionPane.showInputDialog(this,"Field name (use {{field}} in template text):");if(key==null)return;List<String> names=new ArrayList<>();for(int c=0;c<records.getColumnCount();c++)names.add(records.getColumnName(c));BusinessCardMerge.validateHeader(key.trim(),names);records.addColumn(key.trim());}

    private void deleteField(){
        if(records.getColumnCount()==0){status.setText("There are no fields to delete.");return;}
        String[] names=new String[records.getColumnCount()];for(int c=0;c<names.length;c++)names[c]=records.getColumnName(c);
        int selected=table.getSelectedColumn();
        String field=(String)JOptionPane.showInputDialog(this,"Choose the field to delete:","Delete field",JOptionPane.PLAIN_MESSAGE,null,names,names[selected<0?0:table.convertColumnIndexToModel(selected)]);
        if(field==null)return;
        if(JOptionPane.showConfirmDialog(this,"Delete field '"+field+"' and its values from every record?\nOther fields and artwork are preserved. Any {{"+field+"}} placeholders in the artwork will need updating.","Delete field",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION)deleteField(field);
    }

    void deleteField(String field){
        commit();int removed=-1;for(int c=0;c<records.getColumnCount();c++)if(records.getColumnName(c).equals(field)){removed=c;break;}
        if(removed<0)throw new IllegalArgumentException("This field no longer exists.");
        String[] names=new String[records.getColumnCount()-1];Object[][] values=new Object[records.getRowCount()][names.length];
        for(int c=0,next=0;c<records.getColumnCount();c++)if(c!=removed){names[next]=records.getColumnName(c);for(int r=0;r<values.length;r++)values[r][next]=records.getValueAt(r,c);next++;}
        records.setDataVector(values,names);status.setText("Deleted field '"+field+"'. Save the template to keep this change.");
    }

    private void connectExcel()throws Exception{

        Path path=choose(false,"Excel merge source","xlsx","xls");if(path==null)return;

        run("Reading worksheets…",() -> BusinessCardMerge.sheets(path),names -> {String selected=(String)JOptionPane.showInputDialog(this,"Choose worksheet. First row contains field names.","Excel merge",JOptionPane.PLAIN_MESSAGE,null,names.toArray(),names.isEmpty()?null:names.get(0));if(selected!=null)loadExcel(path,selected);});

    }

    private void loadExcel(Path path,String name){

        if(records.getRowCount()>0 && JOptionPane.showConfirmDialog(this,"Replace the current table with Excel records?","Excel merge",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;

        run("Loading Excel records…",() -> BusinessCardMerge.read(path,name),data -> {records.setDataVector(new Object[0][0],data.headers().toArray());for(Map<String,String> row:data.rows())records.addRow(data.headers().stream().map(row::get).toArray());excel=path;worksheet=name;source.setText(path.getFileName()+" · "+name+" (Reload to refresh)");source.setToolTipText(path.toString());dirty=true;refresh.restart();});

    }

    void loadRows(List<String> columns,List<Map<String,String>> rows){
        LinkedHashSet<String> keys=new LinkedHashSet<>(columns);rows.forEach(r->keys.addAll(r.keySet()));
        records.setDataVector(new Object[0][0],keys.toArray());
        rows.forEach(r->records.addRow(keys.stream().map(k->r.getOrDefault(k,"")).toArray()));
    }


    void clearFileHistory(){if(fileView!=null)fileView.clearRecent();}
    void resetFields(){closeProduction();sessionGeneration++;currentLocalFile=null;currentLocalHash="";currentSharedFile=null;currentSharedHash="";records.setDataVector(new Object[0][0],new String[0]);excel=null;worksheet=null;source.setText("Built-in table");source.setToolTipText(null);document.mergeRows.clear();document.mergeColumns.clear();sheetImage.setImage(null);design.repaint();}

    boolean discard(){return !productionBusy() && !working && (!dirty || JOptionPane.showConfirmDialog(this,"Discard unsaved "+(billBooks?"bill-book":banners?"banner":labels?"label":"business-card")+" changes and merge records?",billBooks?"Bill Books":banners?"Banners":labels?"Labels":"Business Cards",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION);}

    void printCards(){
        if(cutProduction!=null&&cutProduction.enabled.isSelected()){cutProduction.open();return;}
        if(banners){printBanner();return;}

        try{if(working)throw new IllegalStateException("Wait for the current operation.");Publication p=PublicationPrintOptions.choose(this,printOutput(),pageIndex);if(p==null)return;PublicationPreflight.requireMergeData(p);PublicationRenderer renderer=new PublicationRenderer(p);int count=renderer.sheets();

            if(!showPrintPreview(p))return;

            PrinterJob job=PrinterJob.getPrinterJob();job.setJobName(p.title);PageFormat format=job.defaultPage();Paper stock=new Paper();double scale=72/25.4;

            stock.setSize(p.print.width*scale,p.print.height*scale);stock.setImageableArea(0,0,stock.getWidth(),stock.getHeight());format.setPaper(stock);

            javax.print.attribute.PrintRequestAttributeSet settings=PublicationPrinting.settings(p,paper.getSelectedItem().toString());

            job.setPrintable((g,pf,index)->Printable.NO_SUCH_PAGE,format);

            if(!job.printDialog(settings))return;

            settings.add(PublicationPrintOptions.sides(p));

            if(p.print.duplex&&!job.getPrintService().isAttributeValueSupported(PublicationPrintOptions.sides(p),null,settings))throw new IllegalStateException("This printer does not support the selected two-sided mode. Choose single-sided or a duplex printer.");

            // Resolve hardware margins from the selected printer and media;
            // the dialog may have supplied Java's one-inch default clip.
            PublicationPrinting.selectedPrinterArea(p,job.getPrintService(),settings);
            PublicationPrinting.selectedPrinterQuality(job.getPrintService(),settings);
            PageFormat actual=job.getPageFormat(settings);

            PublicationPrinting.requireFits(p,actual);

            var diagnostics=PublicationPrintDiagnostics.start(p,job.getPrintService(),settings,actual);
            job.setPrintable(diagnostics.wrap(PublicationPrinting.printable(p)),actual);

            run(billBooks?"Sending bill books to printer…":"Sending business cards to printer…",() -> {try{job.print(settings);diagnostics.note("Job submitted");return true;}catch(Exception ex){diagnostics.note("Print failed: "+ex.getClass().getSimpleName());throw ex;}},ignored -> {status.setText("Print job sent • Diagnostics: "+diagnostics.path());status.setToolTipText(diagnostics.path().toString());});

        }catch(Exception ex){error(ex);}

    }

    private void printBanner(){
        try {
            if (working) throw new IllegalStateException("Wait for the current operation.");
            Publication banner = output();
            run("Checking banner image resolution…", () -> BannerImageResolution.belowTarget(banner),
                    pictures -> printCheckedBanner(banner, pictures));
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void printCheckedBanner(Publication p, List<BannerImageResolution.Picture> pictures) {
        BannerResolutionChoice choice = chooseBannerResolution(pictures);
        if (choice == BannerResolutionChoice.CANCEL) {
            status.setText("Banner print cancelled.");
            return;
        }
        try{
            Printable artwork=BannerPrinting.printable(p);
            PrinterJob job=PrinterJob.getPrinterJob();job.setJobName(p.title);
            PageFormat requested=BannerPrinting.format(p);
            job.setPageable(BannerPrinting.pages(p,artwork,requested));
            var settings=new javax.print.attribute.HashPrintRequestAttributeSet();
            BannerPrinting.configureDialog(settings);
            settings.add(javax.print.attribute.standard.Sides.ONE_SIDED);
            settings.add(p.width>p.height?javax.print.attribute.standard.OrientationRequested.LANDSCAPE:javax.print.attribute.standard.OrientationRequested.PORTRAIT);
            if(!BannerPrinting.showDialog(this,p,job,artwork,settings))return;
            // Keep the media selected in Printer Properties. A second page dialog
            // can reset roll/custom media to the driver's default sheet size.
            PageFormat actual=BannerPrinting.selectedFormat(p,job,settings);
            BannerPrinting.requireSize(p,actual);
            if(JOptionPane.showConfirmDialog(this,"Print "+sizeDescription(p)+" at actual size on "+job.getPrintService().getName()+"?\nConfirm roll/media settings in the driver. Artwork outside the printer margins will be clipped.","Print banner",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            run(choice == BannerResolutionChoice.RESAMPLE ? "Resampling banner and sending to printer…" : "Sending banner to printer…", () -> {
                Publication printSource = choice == BannerResolutionChoice.RESAMPLE
                        ? BannerImageResolution.resample(p, pictures) : p;
                try {
                Printable printArtwork = printSource == p ? artwork : BannerPrinting.printable(printSource);
                PublicationPrintDiagnostics diagnostics = PublicationPrintDiagnostics.start(
                        printSource, job.getPrintService(), settings, actual);
                job.setPageable(BannerPrinting.pages(printSource, diagnostics.wrap(printArtwork), actual));
                job.print(settings);
                return true;
                } finally { BannerImageResolution.cleanup(p,printSource); }
            }, ignored -> status.setText("Banner print job sent • Verify physical size and margins."));
        }catch(Exception ex){error(ex);}
    }
    private void separateRiso(){try{if(working)throw new IllegalStateException("Wait for the current operation.");Publication p=PublicationPrintOptions.chooseRiso(this,printOutput(),pageIndex);if(p!=null){p.print.duplex=false;RisoSeparationDialog.open(this,p);}}catch(Exception ex){error(ex);}}
    private void organiseRiso(int mainPage,String subPage){try{if(working)throw new IllegalStateException("Wait for the current operation.");var result=RisoSubPagesDialog.edit(this,output(),mainPage,subPage);if(result!=null){document=result.document();pageIndex=result.page();refreshPages();configureDesign();changed();status.setText("Linked Riso sub-pages updated • Save the template to keep these assignments.");if(result.saveRequested())saveChanges();}else pagePreviews.select(pageIndex);}catch(Exception ex){error(ex);}}

    private void exportPdf()throws Exception{if(cutProduction!=null&&cutProduction.enabled.isSelected()){cutProduction.open();return;}if(banners){exportBannerPdf();return;}Publication p=printOutput();PublicationPreflight.requireMergeData(p);int count=new PublicationRenderer(p).sheets();Path path=choose(true,billBooks?"Bill book sheets":labels?"Label sheets":"Business card sheets","pdf");if(path!=null)run("Exporting PDF…",() -> {PublicationPdf.write(p,path,0,count-1);return path;},saved -> status.setText("Saved "+saved.getFileName()+" • Print at actual size / 100%."));}

    private void exportBannerPdf() {
        if (working) return;
        Publication banner = output();
        run("Checking banner image resolution…", () -> BannerImageResolution.belowTarget(banner),
                pictures -> exportCheckedBanner(banner, pictures));
    }

    private enum BannerResolutionChoice { SOURCE, RESAMPLE, CANCEL }

    private BannerResolutionChoice chooseBannerResolution(List<BannerImageResolution.Picture> pictures) {
        if (pictures.isEmpty()) return BannerResolutionChoice.SOURCE;
        boolean canResample = pictures.stream().anyMatch(BannerImageResolution.Picture::canResample);
        var message = new StringBuilder("These images are below 300 pixels per inch at their placed size.\n\n");
        for (var picture : pictures) {
            message.append(picture.description());
            if (picture.canResample()) message.append(String.format(java.util.Locale.ROOT,
                    " • resample to approximately %.0f PPI", picture.outputPpi()));
            else message.append(" • keep source pixels (already above the resampling memory budget)");
            message.append('\n');
        }
        message.append(canResample
                ? "\nResampling adds pixels and can smooth edges. Large images use a lower PPI to fit available memory; banner dimensions stay unchanged. For more detail, use a higher-resolution original."
                : "\nThese source images already exceed the resampling memory budget. Keep their existing pixels to preserve detail.");
        var details = new JTextArea(message.toString(), 11, 60);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        Object[] choices = canResample
                ? new Object[]{"Resample (up to 300 PPI)", "Keep source pixels", "Cancel"}
                : new Object[]{"Keep source pixels", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, new JScrollPane(details), "Banner image resolution",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[choices.length - 1]);
        if (choice < 0 || choice == choices.length - 1) return BannerResolutionChoice.CANCEL;
        return canResample && choice == 0 ? BannerResolutionChoice.RESAMPLE : BannerResolutionChoice.SOURCE;
    }

    private void exportCheckedBanner(Publication banner, List<BannerImageResolution.Picture> pictures) {
        BannerResolutionChoice choice = chooseBannerResolution(pictures);
        if (choice == BannerResolutionChoice.CANCEL) {
            status.setText("Banner export cancelled.");
            return;
        }
        try {
            Path file = choose(true, "Banner at actual size", "pdf");
            if (file == null) {
                status.setText("Banner export cancelled.");
                return;
            }
            boolean use300Ppi = choice == BannerResolutionChoice.RESAMPLE;
            run(use300Ppi ? "Resampling and exporting banner PDF…" : "Exporting banner PDF…", () -> {
                Publication output=use300Ppi ? BannerImageResolution.resample(banner,pictures) : banner;
                try { BannerDocuments.pdf(output,file); }
                finally { BannerImageResolution.cleanup(banner,output); }
                return file;
            }, saved -> status.setText("Saved " + saved.getFileName() + " • " + sizeDescription(banner)
                    + (use300Ppi || pictures.isEmpty() ? " • Images at 300 PPI or higher" : " • Source resolution preserved")));
        } catch (Exception ex) {
            error(ex);
        }
    }

    private Path choose(boolean save,String title,String... extensions){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(title);chooser.setFileFilter(new FileNameExtensionFilter(title,extensions));if((save?chooser.showSaveDialog(this):chooser.showOpenDialog(this))!=JFileChooser.APPROVE_OPTION)return null;Path path=chooser.getSelectedFile().toPath();if(save&&!path.toString().toLowerCase(Locale.ROOT).endsWith("."+extensions[0]))path=Path.of(path+"."+extensions[0]);if(save&&Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace "+path.getFileName()+"?",title,JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;return path;}

    private interface Action{void call()throws Exception;}

    private JButton addButton(JPanel panel,String text,Action action){JButton button=new JButton(text);DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);button.addActionListener(e -> {try{if(working)throw new IllegalStateException("Wait for the current operation.");action.call();}catch(Exception ex){error(ex);}});panel.add(button);return button;}

    private <T> void run(String message,java.util.concurrent.Callable<T> task,java.util.function.Consumer<T> done){if(working)return;working=true;int generation=sessionGeneration;status.setText(message);new SwingWorker<T,Void>(){protected T doInBackground()throws Exception{return task.call();}protected void done(){working=false;if(generation!=sessionGeneration)return;try{done.accept(get());}catch(Exception ex){error(ex.getCause()==null?ex:ex.getCause());}finally{if(previewPending){previewPending=false;refresh.restart();}}}}.execute();}

    private void error(Throwable ex){status.setText(ex.getMessage());JOptionPane.showMessageDialog(this,ex.getMessage(),billBooks?"Bill Books":banners?"Banners":labels?"Labels":"Business Cards",JOptionPane.ERROR_MESSAGE);}

    private final class LayerModel extends AbstractTableModel{

        final String[] names={"Text / {{field}}","X in","Y in","Width in","Height in","Font pt","Bold","Colour","Font family","Rotation °","Fill","Stroke","Type"};

        public int getRowCount(){return currentPage().objects.size();}public int getColumnCount(){return names.length;}public String getColumnName(int c){return names[c];}public boolean isCellEditable(int r,int c){return c!=12;}

        public Object getValueAt(int r,int c){var e=currentPage().objects.get(r);return switch(c){case 0->e.text;case 1->e.x/25.4;case 2->e.y/25.4;case 3->e.width/25.4;case 4->e.height/25.4;case 5->e.fontSize;case 6->e.bold;case 7->e.color;case 8->e.font;case 9->e.rotation;case 10->e.fill;case 11->e.stroke;default->e.sourceTable.isBlank()?e.type:"CELL R"+(e.sourceRow+1)+" C"+(e.sourceColumn+1);};}

        public void setValueAt(Object value,int r,int c){Publication old=document.copy();try{var e=currentPage().objects.get(r);String s=value.toString();switch(c){case 0->e.text=s;case 1->e.x=Double.parseDouble(s)*25.4;case 2->e.y=Double.parseDouble(s)*25.4;case 3->e.width=Double.parseDouble(s)*25.4;case 4->e.height=Double.parseDouble(s)*25.4;case 5->e.fontSize=Double.parseDouble(s);case 6->e.bold=Boolean.parseBoolean(s);case 7->e.color=s;case 8->e.font=s;case 9->e.rotation=Double.parseDouble(s);case 10->e.fill=s;case 11->e.stroke=s;}document.validate();changed();}catch(Exception ex){document=old;fireTableDataChanged();error(ex);}}

    }

}

