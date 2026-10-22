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

    private Publication document = builtin();
    private Publication previewDocument;
    private PublicationRenderer previewRenderer;
    private PublicationRenderer canvasRenderer(){
        if(previewDocument!=document){previewDocument=document;previewRenderer=new PublicationRenderer(document,banners?2048:0);}
        return previewRenderer;
    }

    private boolean syncingLayerSelection;
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
    private final JComboBox<String> billBookOutput=new JComboBox<>(new String[]{"Full template + numbers","Numbers only (preprinted books)","Template only (no numbering)"});
    private final JTextField billBookNumberFields=new JTextField("number, num, serial, page1_number",24);
    private SheetNumberingControls numberingControls;

    private final JLabel templateSize=new JLabel();

    private int pageIndex; private boolean updatingPages;

    private final JComboBox<String> pages = new JComboBox<>();

    private final PublicationPages pagePreviews=new PublicationPages(index->pages.setSelectedIndex(index));

    private final javax.swing.Timer thumbnailRefresh=new javax.swing.Timer(350,event->pagePreviews.update(document,pageIndex));

    private final JComboBox<String> pageMode = new JComboBox<>(new String[]{"Each page on separate sheets", "Mix pages on the same sheet"});

    private final TemplateWorkspace design = new TemplateWorkspace();

    private final DefaultTableModel records = new DefaultTableModel(new String[]{"name","company","title","phone","email","quantity"},0);

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

    private final JSpinner leftMargin = new JSpinner(new SpinnerNumberModel(0.0,0,50,0.5));
    private final JSpinner rightMargin = new JSpinner(new SpinnerNumberModel(0.0,0,50,0.5));
    private final JSpinner topMargin = new JSpinner(new SpinnerNumberModel(0.0,0,50,0.5));
    private final JSpinner bottomMargin = new JSpinner(new SpinnerNumberModel(0.0,0,50,0.5));

    private final JSpinner gap = new JSpinner(new SpinnerNumberModel(0.0,0,20,0.5));

    private final JSpinner copies = new JSpinner(new SpinnerNumberModel(10,1,100000,1));

    private final JSpinner sheet = new JSpinner(new SpinnerNumberModel(1,1,100000,1));

    private final JCheckBox rotate = new JCheckBox("Allow rotation",true), marks = new JCheckBox("Cut marks",true);

    private Path excel; private String worksheet; private boolean dirty, working; private int sessionGeneration;

    private final javax.swing.Timer refresh = new javax.swing.Timer(220,e -> previewSheet());



    BusinessCardsPanel() {this(false);}

    BusinessCardsPanel(boolean labels) {this(labels,false);}

    BusinessCardsPanel(boolean labels,boolean banners) {this(labels,banners,false);}
    BusinessCardsPanel(boolean labels,boolean banners,boolean billBooks) {

        super(new BorderLayout(8,8));

        this.labels=labels;this.banners=banners;this.billBooks=billBooks;if(banners)document=BannerDocuments.create(20,4,"FT");else if(billBooks)document=BillBookDocuments.create();else if(labels)document=labelTemplate(); setBackground(DeckersPalette.background());

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

        addButton(tools,"Undo (Ctrl+Z)",()->historyAction(false));addButton(tools,"Redo (Ctrl+Y)",()->historyAction(true));

        addButton(tools,"Save (Ctrl+S)",this::saveChanges);addButton(tools,"Export template file…",this::saveTemplate);addButton(tools,"Save to template library…",this::saveToLibrary); tools.add(pages);addButton(tools,"Add page",this::addPage);addButton(tools,"Delete page",this::deletePage);addButton(tools,"Page number field",this::pageNumberField);

        addButton(tools,"Add text",() -> {Publication.Element e=new Publication.Element();e.width=Math.min(78,document.width);e.height=Math.min(10,document.height);e.text=banners?"Banner text":"{{name}}";if(banners){e.x=document.width*.05;e.y=document.height*.05;e.width=document.width*.8;e.height=document.height*.25;e.fontSize=Math.min(10000,document.height*.18*72/25.4);}currentPage().objects.add(e);changed();});

        addButton(tools,"Insert image…",this::artwork);

        addButton(tools,"Copy layers",this::copyLayers);
        addButton(tools,"Paste",this::pasteLayers);

        addButton(tools,"Add WordArt…",()->{commit();var e=publishing.WordArtEditor.choose(this,null);if(e!=null){e.width=Math.min(e.width,document.width);e.height=Math.min(e.height,document.height);e.x=e.y=0;currentPage().objects.add(e);changed();}});

        addButton(tools,"Edit WordArt…",()->{commit();int row=layerTable.getSelectedRow();if(row<0||!currentPage().objects.get(row).type.equals("TEXT"))throw new IllegalArgumentException("Select a text layer first.");var e=publishing.WordArtEditor.choose(this,currentPage().objects.get(row));if(e!=null){currentPage().objects.set(row,e);changed();}});

        addButton(tools,"Generate barcode / QR…",()->generateCode(false));
        addButton(tools,"Generate QR code…",()->generateCode(true));

        addButton(tools,"Edit barcode / QR…",()->{commit();int row=layerTable.getSelectedRow();if(row<0||!Set.of("BARCODE","QR").contains(currentPage().objects.get(row).type))throw new IllegalArgumentException("Select a barcode or QR layer first.");var code=BarcodeEditor.choose(this,currentPage().objects.get(row));if(code!=null){currentPage().objects.set(row,code);changed();}});

        addButton(tools,"Replace image with barcode…",()->{commit();int row=layerTable.getSelectedRow();if(row<0||!currentPage().objects.get(row).type.equals("IMAGE"))throw new IllegalArgumentException("Select the barcode picture in Layers first.");var old=currentPage().objects.get(row);var code=BarcodeEditor.choose(this,null);if(code!=null){code.x=old.x;code.y=old.y;code.width=old.width;code.height=old.height;currentPage().objects.set(row,code);changed();}});

        StudioLayerClipboard.bind(design,this::copyLayers,this::pasteLayers);
        StudioLayerClipboard.bind(layerTable,this::copyLayers,this::pasteLayers);

        addButton(tools,"Remove selected layers",() -> {commit();design.deleteSelection();});

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

        horizontalMargins.add(new JLabel("Left margin mm"));horizontalMargins.add(leftMargin);horizontalMargins.add(new JLabel("Right margin mm"));horizontalMargins.add(rightMargin);
        verticalMargins.add(new JLabel("Top margin mm"));verticalMargins.add(topMargin);verticalMargins.add(new JLabel("Bottom margin mm"));verticalMargins.add(bottomMargin);
        spacingRow.add(new JLabel("Gap mm"));spacingRow.add(gap);spacingRow.add(rotate);spacingRow.add(marks);

        countRow.add(new JLabel("Copies (no merge)"));countRow.add(copies);countRow.add(new JLabel("Sheet"));countRow.add(sheet);

        JPanel outputButtons=DeckersSwing.panel();outputButtons.setLayout(new FlowLayout(FlowLayout.LEFT));addButton(outputButtons,"Preview",this::previewSheet);addButton(outputButtons,"Print",this::printCards);addButton(outputButtons,"Export PDF",this::exportPdf);
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
        addButton(mergeTools,"Fill number series",this::fillSeries);addButton(mergeTools,"Add field",this::addField);addButton(mergeTools,"Connect Excel",this::connectExcel);

        addButton(mergeTools,"Reload Excel",() -> {if(excel==null)throw new IllegalStateException("Connect an Excel file first.");loadExcel(excel,worksheet);});

        addButton(mergeTools,"Clear table",() -> {if(JOptionPane.showConfirmDialog(this,"Clear all merge records?","Business Cards",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){records.setRowCount(0);excel=null;source.setText("Built-in table");}});

        mergeTools.add(source);JScrollPane mergeToolbar=new JScrollPane(mergeTools,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);mergeToolbar.setPreferredSize(new Dimension(700,60));merge.add(mergeToolbar,BorderLayout.NORTH);table.setRowHeight(25);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);merge.add(new JScrollPane(table));

        merge.setPreferredSize(new Dimension(900,160));merge.setMinimumSize(new Dimension(220,100));

        editor.remove(layerScroll);

        JSplitPane pageLayout=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,pagePreviews,editor);pageLayout.setResizeWeight(0);pageLayout.setDividerLocation(180);pageLayout.setContinuousLayout(true);pageLayout.setBorder(null);
        pagePreviews.addPropertyChangeListener("preferredSize",e->pageLayout.setDividerLocation(pagePreviews.getPreferredSize().width));

        StudioWorkspaceShell shell=new StudioWorkspaceShell(pageLayout);

        remove(toolsScroll);

        StudioWorkspaceShell.stack(tools);

        shell.addTools(StudioWorkspaceShell.menu("Template & insert",tools));
        shell.addPrimaryAction("New template",()->{if(!working)newCard();});

        shell.addTools(StudioWorkspaceShell.button("Save",()->{try{saveChanges();}catch(Exception ex){error(ex);}}));

        shell.addTools(StudioWorkspaceShell.button("Print",()->{try{printCards();}catch(Exception ex){error(ex);}}));

        shell.addTools(StudioWorkspaceShell.button("Export PDF",()->{try{exportPdf();}catch(Exception ex){error(ex);}}));
        JPanel risoTools=DeckersSwing.panel();risoTools.setLayout(new BoxLayout(risoTools,BoxLayout.Y_AXIS));risoTools.add(StudioWorkspaceShell.button("Automatic colour separation…",this::separateRiso));risoTools.add(StudioWorkspaceShell.button("Linked Riso sub-pages…",()->organiseRiso(pageIndex,LinkedRiso.MAIN)));shell.addTools(StudioWorkspaceShell.menu("Riso",risoTools));
        pagePreviews.setRisoSelection(this::organiseRiso);

        textFormatting=new TextFormattingToolbar(this::selectedTextStyle,this::applyTextStyle);if(banners)textFormatting.setMaximumFontSize(10000);
        BarcodeFormattingToolbar barcodeFormatting=new BarcodeFormattingToolbar(this::selectedBarcode,this::applyBarcodeSettings);
        ImageFormattingToolbar imageFormatting=new ImageFormattingToolbar(this::selectedPicture,this::applyPictureSize,this::recolourPicture);imageFormatting.setTransparencyEditor(this::applyPictureTransparency);if(banners)imageFormatting.setMaximumSizeMm(BannerDocuments.MAX_MM);
        design.setAspectRatioPolicy(i->currentPage().objects.get(i).type.equals("IMAGE")?imageFormatting.locked.isSelected():null);
        CardLayout contextLayout=new CardLayout();JPanel contextTools=new JPanel(contextLayout);contextTools.setOpaque(false);
        contextTools.add(textFormatting,"text");contextTools.add(barcodeFormatting,"barcode");contextTools.add(imageFormatting,"image");shell.addTextTools(contextTools);
        Runnable refreshContext=()->{textFormatting.refresh();barcodeFormatting.refresh();imageFormatting.refresh();contextLayout.show(contextTools,selectedPicture()!=null?"image":selectedBarcode()==null?"text":"barcode");};
        layerTable.getSelectionModel().addListSelectionListener(event->refreshContext.run());layerTable.getModel().addTableModelListener(event->refreshContext.run());

        shell.addTools(pages);updateTemplateSize();

        shell.addTools(StudioWorkspaceShell.button("−",()->design.zoomBy(1/1.15)));

        shell.addTools(StudioWorkspaceShell.button("Fit",()->{design.setZoom(1);design.scrollRectToVisible(new Rectangle(0,0,1,1));}));

        shell.addTools(StudioWorkspaceShell.button("+",()->design.zoomBy(1.15)));

        JPanel layerPanel=DeckersSwing.panel();layerPanel.setLayout(new BorderLayout(4,4));
        JPanel orderTools=DeckersSwing.panel();orderTools.setLayout(new GridLayout(2,2,4,4));
        String[] orderNames={"Bring forward","Send backward","Bring to front","Send to back"};
        StudioLayerOrder.Move[] orderMoves=StudioLayerOrder.Move.values();
        JPopupMenu orderMenu=new JPopupMenu();
        for(int i=0;i<orderNames.length;i++){
            var move=orderMoves[i];String title=orderNames[i];
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
        shell.addSection("Layers",layerPanel);

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

        if(!banners)shell.addSection("Data",merge);

        if(!banners)shell.addSection("Print setup",output);

        add(shell);

        JPanel footer=DeckersSwing.panel();footer.setLayout(new BorderLayout(0,4));footer.add(templateSize,BorderLayout.NORTH);footer.add(status);

        if(billBooks){sharedLibraryStatus.setText("Shared bill book library — sign in to synchronize");status.setText("Use Number series to fill {{number}}. Check imported fonts and every page before printing.");}else if(banners){sharedLibraryStatus.setText("Banner templates on this computer • Export .sspub to reuse elsewhere");status.setText("Ctrl+wheel zoom • middle-drag / scrollbars pan • Fit resets view • PDF at actual size.");}else if(labels)status.setText("Use {{field}} in text to merge table columns. Sheet size is set in Print setup.");

        add(footer,BorderLayout.SOUTH);

        records.addTableModelListener(e -> {dirty=true;design.repaint();refresh.restart();});

        table.getSelectionModel().addListSelectionListener(e -> design.repaint());

        pages.addActionListener(e -> {if(!updatingPages){commit();pageIndex=pages.getSelectedIndex();pagePreviews.select(pageIndex);configureDesign();layers.fireTableDataChanged();design.repaint();}});

        pageMode.addActionListener(e -> {dirty=true;refresh.restart();});paper.addActionListener(e -> {boolean custom="Custom".equals(paper.getSelectedItem());customWidth.setEnabled(custom);customHeight.setEnabled(custom);dirty=true;refresh.restart();});

        for(JSpinner spinner:List.of(leftMargin,rightMargin,topMargin,bottomMargin,gap,copies,sheet,customWidth,customHeight))spinner.addChangeListener(e -> refresh.restart());

        rotate.addActionListener(e -> refresh.restart());marks.addActionListener(e -> refresh.restart());

        paper.setSelectedItem("Letter");if(billBooks)restoreSheetSize(document.print);dirty=false;refresh.restart();

    }

    private void newCard(){if(discard()){Publication banner=banners?newBanner():null;if(banners&&banner==null)return;currentLocalFile=null;currentLocalHash="";currentSharedFile=null;currentSharedHash="";document=billBooks?BillBookDocuments.create():banners?banner:labels?labelTemplate():builtin();if(billBooks)restoreSheetSize(document.print);pageIndex=0;refreshPages();records.setRowCount(0);excel=null;source.setText("Built-in table");configureDesign();resetHistory();changed();dirty=false;}}



    boolean chooseTemplate(){

        if(working){status.setText("Wait for the current operation to finish.");return false;}

        try{

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
        JComboBox<String> units=new JComboBox<>(new String[]{"FT","IN","CM","MM"});
        JTextField width=new JTextField("20"),height=new JTextField("4");
        JPanel form=new JPanel(new GridLayout(0,2,8,8));form.add(new JLabel("Units"));form.add(units);form.add(new JLabel("Width"));form.add(width);form.add(new JLabel("Height"));form.add(height);
        form.add(new JLabel("Maximum 1,000,000 mm per side"));form.add(new JLabel("PDF export keeps physical dimensions"));
        while(JOptionPane.showConfirmDialog(this,form,"New banner template",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{return BannerDocuments.create(Double.parseDouble(width.getText().trim()),Double.parseDouble(height.getText().trim()),units.getSelectedItem().toString());}
            catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Banner size",JOptionPane.ERROR_MESSAGE);}
        }return null;
    }

    static Publication labelTemplate(){Publication p=new Publication();p.title="Label";p.width=50.8;p.height=25.4;p.template=true;return p;}

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

    private static String sizeDescription(Publication p){return String.format(Locale.ROOT,"%.3f × %.3f %s (%.2f × %.2f mm)",Publication.fromMm(p.width,p.displayUnit),Publication.fromMm(p.height,p.displayUnit),p.displayUnit,p.width,p.height);}

    private void updateTemplateSize(){templateSize.setText(sizeDescription(document));}

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

    private TextFormattingToolbar.Style selectedTextStyle(){var e=selectedText();return e==null?null:new TextFormattingToolbar.Style(e.font,e.fontSize,e.bold,e.italic,e.underline,e.strike,e.color,e.wordArt.equals("NONE")?e.textOutline:e.wordArtOutlineWidth>0,e.wordArt.equals("NONE")?e.textOutlineColor:e.wordArtOutline,e.wordArt.equals("NONE")?e.textOutlineWidth:e.wordArtOutlineWidth);}

    private void applyTextStyle(TextFormattingToolbar.Style style){commit();var e=selectedText();if(e==null)return;int selected=layerTable.getSelectedRow();e.font=style.font();if(e.fontSize!=style.size()){if(e.wordArt.equals("NONE"))e.fitText=false;else{double ratio=style.size()/e.fontSize;e.width*=ratio;e.height*=ratio;}}e.fontSize=style.size();e.bold=style.bold();e.italic=style.italic();e.underline=style.underline();e.strike=style.strike();e.color=style.color();e.textOutline=style.outline();e.textOutlineColor=style.outlineColor();e.textOutlineWidth=style.outlineWidth();if(!e.wordArt.equals("NONE")){e.wordArtOutline=style.outlineColor();e.wordArtOutlineWidth=style.outline()?style.outlineWidth():0;}document.validate();changed();if(selected>=0&&selected<layerTable.getRowCount())layerTable.setRowSelectionInterval(selected,selected);}

    private ImageFormattingToolbar.Picture selectedPicture(){if(layerTable.getSelectedRowCount()!=1)return null;int row=layerTable.getSelectedRow();if(row<0||row>=currentPage().objects.size())return null;var e=currentPage().objects.get(row);return e.type.equals("IMAGE")?new ImageFormattingToolbar.Picture(e.id,e.width,e.height,e.transparency):null;}
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

    private Publication.Element selectedBarcode(){if(layerTable.getSelectedRowCount()!=1)return null;int row=layerTable.getSelectedRow();return row>=0&&row<currentPage().objects.size()&&Set.of("BARCODE","QR").contains(currentPage().objects.get(row).type)?currentPage().objects.get(row):null;}
    private void applyBarcodeSettings(BarcodeFormattingToolbar.Settings settings){
        commit();var old=selectedBarcode();if(old==null)return;int selected=layerTable.getSelectedRow();
        var next=Publication.JSON.fromJson(Publication.JSON.toJson(old),Publication.Element.class);
        next.type=settings.format().equals("QR_CODE")?"QR":"BARCODE";next.barcodeFormat=settings.format();next.text=settings.value();next.barcodeText=settings.caption();next.width=settings.width();next.height=settings.height();next.rotation=settings.rotation();next.asset="";
        Publication check=document.copy();check.pages.get(pageIndex).objects.set(selected,next);check.validate();
        currentPage().objects.set(selected,next);changed();layerTable.setRowSelectionInterval(selected,selected);
    }

    private Publication.Page currentPage(){return document.pages.get(pageIndex);}

    private void refreshPages(){updatingPages=true;try{pages.removeAllItems();for(int i=0;i<document.pages.size();i++)pages.addItem((i+1)+" · "+document.pages.get(i).name);pages.setSelectedIndex(pageIndex);}finally{updatingPages=false;}pagePreviews.update(document,pageIndex);}

    private void addPage()throws Exception{

        commit();if(document.pages.size()>=500)throw new IllegalArgumentException("At most 500 template pages.");

        Publication.Page page=new Publication.Page();page.name="Page "+(document.pages.size()+1);document.pages.add(page);pageIndex=document.pages.size()-1;

        refreshPages();configureDesign();pageNumberField();changed();

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

        JTextField start=new JTextField("1"),step=new JTextField("1");

        JSpinner count=new JSpinner(new SpinnerNumberModel(Math.max(1,records.getRowCount()),1,10000,1));

        JSpinner firstRow=new JSpinner(new SpinnerNumberModel(Math.max(1,table.getSelectedRow()+1),1,10000,1));

        JSpinner digits=new JSpinner(new SpinnerNumberModel(0,0,40,1));

        JPanel form=new JPanel(new GridLayout(0,2,8,8));

        form.add(new JLabel("Column (existing or new)"));form.add(column);form.add(new JLabel("Starting number"));form.add(start);form.add(new JLabel("Step"));form.add(step);form.add(new JLabel("Number of values"));form.add(count);form.add(new JLabel("Start at table row"));form.add(firstRow);form.add(new JLabel("Minimum digits (zero padding)"));form.add(digits);

        if(JOptionPane.showConfirmDialog(this,form,"Fill number series",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;

        String key=column.getSelectedItem().toString().trim();int offset=(int)firstRow.getValue()-1;

        List<String> values=BusinessCardSeries.values(start.getText(),step.getText(),(int)count.getValue(),(int)digits.getValue());

        if(offset+values.size()>10000)throw new IllegalArgumentException("The series exceeds 10000 table rows.");

        int target=-1;for(int c=0;c<records.getColumnCount();c++)if(records.getColumnName(c).equals(key))target=c;

        if(target>=0){boolean overwrite=false;for(int r=offset;r<Math.min(records.getRowCount(),offset+values.size());r++){Object value=records.getValueAt(r,target);if(value!=null&&!value.toString().isBlank())overwrite=true;}

            if(overwrite&&JOptionPane.showConfirmDialog(this,"Replace existing numbers in this column and row range? Other columns are preserved.","Fill number series",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;}

        target=ensureColumn(key);while(records.getRowCount()<offset+values.size())records.addRow(new Object[records.getColumnCount()]);

        for(int i=0;i<values.size();i++)records.setValueAt(values.get(i),offset+i,target);

        table.setRowSelectionInterval(offset,offset);table.setColumnSelectionInterval(target,target);status.setText("Filled "+values.size()+" values in "+key+" • use {{"+key+"}} in this page's text.");

    }

    private void syncCanvasSelection(){syncingLayerSelection=true;try{layerTable.clearSelection();for(int row:design.selectedIndices())if(row<layers.getRowCount())layerTable.addRowSelectionInterval(row,row);}finally{syncingLayerSelection=false;}}

    void reorderLayers(StudioLayerOrder.Move move){
        commit();
        var result=StudioLayerOrder.move(currentPage().objects,layerTable.getSelectedRows(),move,e->e.locked);
        if(!result.changed())return;
        changed();
        syncingLayerSelection=true;
        try{layerTable.clearSelection();for(int row:result.selection())layerTable.addRowSelectionInterval(row,row);}
        finally{syncingLayerSelection=false;}
        design.selectIndices(result.selection());design.repaint();
        status.setText("Layer order updated. Ctrl+Z to undo.");
    }

    private void configureDesign(){

        design.configure(new TemplateWorkspace.Design(){
            public double maximumSize(){return banners?BannerDocuments.MAX_MM:1000;}
            public boolean constrainSizeToPage(){return !banners;}

            public double width(){return document.width;}public double height(){return document.height;}public int size(){return currentPage().objects.size();}

            public Rectangle2D bounds(int i){var e=currentPage().objects.get(i);return new Rectangle2D.Double(e.x,e.y,e.width,e.height);}

            public void move(int i,double x,double y){var e=currentPage().objects.get(i);e.x=x;e.y=y;}

            public boolean canResize(){return true;}

            public void resize(int i,Rectangle2D b){var e=currentPage().objects.get(i);e.x=b.getX();e.y=b.getY();e.width=b.getWidth();e.height=b.getHeight();}

            public void remove(int i){currentPage().objects.remove(i);}
            public boolean locked(int i){return currentPage().objects.get(i).locked;}

            public String text(int i){var e=currentPage().objects.get(i);return "TEXT".equals(e.type)?e.text:null;}

            public boolean setText(int i,String value){var e=currentPage().objects.get(i);String old=e.text;e.text=value;try{document.validate();return true;}catch(Exception ex){e.text=old;return false;}}

            public void paint(Graphics2D g,double scale){Graphics2D draw=(Graphics2D)g.create();try{draw.scale(scale,scale);canvasRenderer().page(draw,pageIndex,selectedRecord());}finally{draw.dispose();}}

        },r -> syncCanvasSelection(),this::changed);

    }

    private Map<String,String> selectedRecord(){int r=table.getSelectedRow();if(r<0 && records.getRowCount()>0)r=0;return r<0?Map.of():row(r);}

    private Map<String,String> row(int r){Map<String,String> values=new LinkedHashMap<>();for(int c=0;c<records.getColumnCount();c++){Object v=records.getValueAt(r,c);String s=v==null?"":v.toString();if(records.getColumnName(c).equals("quantity") && s.isBlank())s="1";values.put(records.getColumnName(c),s);}return values;}

    private void commit(){if(!design.finishText(true))throw new IllegalStateException("Finish editing the text.");for(JTable t:List.of(table,layerTable))if(t.isEditing()&&!t.getCellEditor().stopCellEditing())throw new IllegalStateException("Finish editing the current cell.");}

    Publication output(){

        commit();Publication p=document.copy();if(banners){p.mergeRows.clear();p.validate();return p;}p.mergeRows.clear();

        for(int r=0;r<records.getRowCount();r++){Map<String,String> values=row(r);if(values.entrySet().stream().anyMatch(e -> !e.getKey().equals("quantity")&&!e.getValue().isBlank()))p.mergeRows.add(values);}

        double[] size=BusinessCardPaper.size(paper.getSelectedItem().toString(),((Number)customWidth.getValue()).doubleValue(),((Number)customHeight.getValue()).doubleValue());p.print.width=size[0];p.print.height=size[1];

        p.print.left=((Number)leftMargin.getValue()).doubleValue();p.print.right=((Number)rightMargin.getValue()).doubleValue();
        p.print.top=((Number)topMargin.getValue()).doubleValue();p.print.bottom=((Number)bottomMargin.getValue()).doubleValue();

        p.print.gapX=p.print.gapY=((Number)gap.getValue()).doubleValue();p.print.quantity=(int)copies.getValue();

        p.print.maximize=true;p.print.rotate=rotate.isSelected();p.print.cropMarks=marks.isSelected();p.print.duplex=false;p.print.booklet=false;

        p.print.mixPages=pageMode.getSelectedIndex()==1; p.print.skip=0;p.print.offsetX=p.print.offsetY=0;p.print.bleed=0;
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

    private void changed(){LinkedRiso.prune(document);thumbnailRefresh.restart();recordHistory();updateTemplateSize();design.revalidate();dirty=true;int[] selected=layerTable.getSelectedRows();syncingLayerSelection=true;try{layers.fireTableDataChanged();for(int row:selected)if(row<layerTable.getRowCount())layerTable.addRowSelectionInterval(row,row);}finally{syncingLayerSelection=false;}design.selectIndices(layerTable.getSelectedRows());design.repaint();refresh.restart();}

    private void previewSheet(){

        if(banners){status.setText(sizeDescription(document)+" • Ctrl+wheel zoom • scrollbars / middle-drag pan • Fit resets view • Export PDF for your print shop.");return;}
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

        String[] openedHash={null};

        Path converter=null;if(!path.toString().toLowerCase(Locale.ROOT).endsWith(".sspub")){converter=PublisherConverter.find();if(converter==null)converter=choose(false,"LibreOffice soffice executable","exe","com");if(converter==null)return;PublisherConverter.remember(converter);}final Path executable=converter;

        run("Opening template…",() -> {if(Files.size(path)>(banners?PublicationLargeFiles.MAX_BYTES:PublicationFiles.MAX_BYTES))throw new java.io.IOException(banners?"Template exceeds 5 GiB.":"Template exceeds 48 MB.");String before=BadgeTemplateLibrary.fingerprint(path);Publication p=readTemplate(path,executable,labels,banners);if(!before.equals(BadgeTemplateLibrary.fingerprint(path)))throw new java.io.IOException("Template changed while opening; refresh and reopen it.");openedHash[0]=before;

            if(p.pages.stream().anyMatch(pg -> pg.reference))throw new java.io.IOException("Choose printable pages; remove reference pages in Publisher first.");return p;},p -> {currentSharedFile=shared!=null&&shared.contains(path)?path:null;currentSharedHash=currentSharedFile==null?"":openedHash[0];currentLocalFile=currentSharedFile==null&&path.toString().toLowerCase(Locale.ROOT).endsWith(".sspub")?path:null;currentLocalHash=currentLocalFile==null?"":openedHash[0];document=p;pageIndex=0;refreshPages();restoreSheetSize(p.print);pageMode.setSelectedIndex(p.print.mixPages?1:0);loadRows(p.mergeRows);excel=null;source.setText("Built-in table");configureDesign();resetHistory();changed();dirty=false;if(path.toString().toLowerCase(Locale.ROOT).endsWith(".pub"))JOptionPane.showMessageDialog(this,"Publisher objects and text fields were imported separately.\nReview fonts, colours, rotated text and every page against the original before printing.","Publisher import",JOptionPane.INFORMATION_MESSAGE);});

    }

    void restoreSheetSize(SheetLayout.Settings settings){

        // The sheet is a workspace choice, independent of imported artwork dimensions.

        if(billBooks){
            customWidth.setValue(settings.width/25.4);customHeight.setValue(settings.height/25.4);
            paper.setSelectedItem("Custom");
            leftMargin.setValue(settings.left);rightMargin.setValue(settings.right);
            topMargin.setValue(settings.top);bottomMargin.setValue(settings.bottom);gap.setValue(settings.gapX);
            copies.setValue(Math.max(1,settings.quantity));rotate.setSelected(settings.rotate);marks.setSelected(settings.cropMarks);
            if(numberingControls!=null)numberingControls.restore(settings);
        }else paper.setSelectedItem("Letter");

    }

    private boolean showPrintPreview(Publication p){

        int count=new PublicationRenderer(p).sheets();

        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),billBooks?"Bill books — full sheet preview":"Business cards — full sheet preview",Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);dialog.setLayout(new BorderLayout(8,8));

        SheetPreview fullSheet=new SheetPreview();dialog.add(fullSheet);

        JLabel details=new JLabel(String.format(Locale.ROOT,"%.2f × %.2f inches • %d sheets • %s",p.print.width/25.4,p.print.height/25.4,count,p.print.duplex?"Front + back ("+(PublicationPrintOptions.sides(p)==javax.print.attribute.standard.Sides.TWO_SIDED_SHORT_EDGE?"short":"long")+" edge)":p.print.mixPages?"Mixed pages • single-sided":"Separate pages • single-sided"));

        JPanel top=DeckersSwing.panel();top.setLayout(new FlowLayout(FlowLayout.LEFT));top.add(details);top.add(new JLabel("Sheet"));

        JSpinner number=new JSpinner(new SpinnerNumberModel(1,1,count,1));top.add(number);dialog.add(top,BorderLayout.NORTH);

        JPanel actions=DeckersSwing.panel();actions.setLayout(new FlowLayout(FlowLayout.RIGHT));JLabel note=new JLabel("Rendering…");actions.add(note);

        JButton cancel=new JButton("Cancel"),print=new JButton("Continue to printer…");DeckersSwing.styleUtilityButton(cancel,DeckersPalette.PURPLE);DeckersSwing.styleUtilityButton(print,DeckersPalette.PURPLE);actions.add(cancel);actions.add(print);dialog.add(actions,BorderLayout.SOUTH);

        boolean[] accepted={false};int[] generation={0};

        Runnable render=()->{int current=++generation[0],index=(int)number.getValue()-1;print.setEnabled(false);fullSheet.setImage(null);note.setText("Rendering sheet "+(index+1)+"…");

            new SwingWorker<java.awt.image.BufferedImage,Void>(){

                protected java.awt.image.BufferedImage doInBackground(){return new PublicationRenderer(p).raster(index,96);}

                protected void done(){if(current!=generation[0]||!dialog.isDisplayable())return;try{fullSheet.setImage(get());note.setText("Sheet "+(index+1)+" of "+count);print.setEnabled(true);}catch(Exception ex){note.setText("Unable to render sheet.");}}

            }.execute();};

        number.addChangeListener(e->render.run());cancel.addActionListener(e->dialog.dispose());print.addActionListener(e->{accepted[0]=true;dialog.dispose();});

        dialog.setSize(850,760);dialog.setMinimumSize(new Dimension(560,440));dialog.setLocationRelativeTo(this);dialog.addNotify();render.run();dialog.setVisible(true);generation[0]++;return accepted[0];

    }

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

    private void saveLocal(Publication p,Path target,String expected){
        Publication snapshot=p.copy();p.template=true;p.mergeRows.clear();
        run("Saving template…",()->writeTemplate(target,p,expected,banners),hash->{currentLocalFile=target;currentLocalHash=hash;currentSharedFile=null;currentSharedHash="";savedState(snapshot);status.setText("Saved: "+target.getFileName()+" • Merge records are excluded.");});
    }

    static String writeTemplate(Path target,Publication p,String expected)throws Exception{
        return writeTemplate(target,p,expected,!p.assetFiles.isEmpty());
    }
    static String writeTemplate(Path target,Publication p,String expected,boolean large)throws Exception{
        if(expected!=null&&(!Files.exists(target)||!expected.equals(BadgeTemplateLibrary.fingerprint(target))))throw new java.io.IOException("This template changed on disk. Export your draft to a new file, then reopen the updated template.");
        if(large)PublicationLargeFiles.write(target,p);else PublicationFiles.writeAtomic(target,PublicationFiles.archive(p));return BadgeTemplateLibrary.fingerprint(target);
    }

    private void saveShared(Publication p)throws Exception{
        if(shared==null||!sharedActive||!shared.online)throw new java.io.IOException("Offline mode: reconnect and sign in to save a shared template.");
        Publication snapshot=p.copy();var active=shared;Path current=currentSharedFile;String expected=currentSharedHash;long generation=sharedGeneration;
        run("Saving template on the store server…",()->active.save(p,current,expected,()->sharedActive&&generation==sharedGeneration),saved->{currentSharedFile=saved.file();currentSharedHash=saved.sha256();currentLocalFile=null;currentLocalHash="";document.title=p.title;snapshot.title=p.title;savedState(snapshot);sharedLibraryStatus.setText(active.status);status.setText("Saved on server. Other computers refresh automatically; OneDrive backup is queued.");});
    }

    private void saveTemplate()throws Exception{Publication p=output();Path path=choose(true,"Publisher template","sspub");if(path==null)return;saveLocal(p,path,null);}

    private void saveToLibrary()throws Exception{

        Publication p=output();String name=JOptionPane.showInputDialog(this,"Template name",p.title);if(name==null)return;name=name.strip();if(name.isBlank()||name.length()>120||name.matches(".*[<>:\"/\\\\|?*].*"))throw new IllegalArgumentException("Use a template name without path characters.");

        if(shared!=null){

            if(!sharedActive||!shared.online)throw new java.io.IOException("Offline mode: reconnect and sign in to save a shared template.");

            p.title=name;saveShared(p);return;

        }

        Path target=(billBooks?BillBookDocuments.folder():banners?BannerDocuments.folder():PublicationTemplateLibrary.folder(labels)).resolve(name+".sspub");if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"Replace the existing library template?","Save template",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;

        document.title=name;p.title=name;saveLocal(p,target,null);

    }

    private void artwork()throws Exception{Path path=choose(false,"Image","png","jpg","jpeg");if(path!=null)loadImageLayer(()->banners?StudioImageFile.loadArtwork(path):StudioImageFile.load(path));}


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

    }

    private void addField()throws Exception{String key=JOptionPane.showInputDialog(this,"Field name (use {{field}} in template text):");if(key==null)return;List<String> names=new ArrayList<>();for(int c=0;c<records.getColumnCount();c++)names.add(records.getColumnName(c));BusinessCardMerge.validateHeader(key.trim(),names);records.addColumn(key.trim());}

    private void connectExcel()throws Exception{

        Path path=choose(false,"Excel merge source","xlsx","xls");if(path==null)return;

        run("Reading worksheets…",() -> BusinessCardMerge.sheets(path),names -> {String selected=(String)JOptionPane.showInputDialog(this,"Choose worksheet. First row contains field names.","Excel merge",JOptionPane.PLAIN_MESSAGE,null,names.toArray(),names.isEmpty()?null:names.get(0));if(selected!=null)loadExcel(path,selected);});

    }

    private void loadExcel(Path path,String name){

        if(records.getRowCount()>0 && JOptionPane.showConfirmDialog(this,"Replace the current table with Excel records?","Excel merge",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;

        run("Loading Excel records…",() -> BusinessCardMerge.read(path,name),data -> {records.setDataVector(new Object[0][0],data.headers().toArray());for(Map<String,String> row:data.rows())records.addRow(data.headers().stream().map(row::get).toArray());excel=path;worksheet=name;source.setText(path.getFileName()+" · "+name+" (Reload to refresh)");source.setToolTipText(path.toString());dirty=true;refresh.restart();});

    }

    private void loadRows(List<Map<String,String>> rows){records.setRowCount(0);if(rows.isEmpty())return;LinkedHashSet<String> keys=new LinkedHashSet<>();rows.forEach(r -> keys.addAll(r.keySet()));records.setDataVector(new Object[0][0],keys.toArray());rows.forEach(r -> records.addRow(keys.stream().map(r::get).toArray()));}

    void resetFields(){sessionGeneration++;currentLocalFile=null;currentLocalHash="";currentSharedFile=null;currentSharedHash="";records.setRowCount(0);excel=null;worksheet=null;source.setText("Built-in table");source.setToolTipText(null);document.mergeRows.clear();sheetImage.setImage(null);design.repaint();}

    boolean discard(){return !working && (!dirty || JOptionPane.showConfirmDialog(this,"Discard unsaved "+(billBooks?"bill-book":banners?"banner":labels?"label":"business-card")+" changes and merge records?",billBooks?"Bill Books":banners?"Banners":labels?"Labels":"Business Cards",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION);}

    void printCards(){
        if(banners){JOptionPane.showMessageDialog(this,"Export the banner PDF and print through your large-format RIP at actual size.\nCheck the PDF dimensions and UserUnit support with your print shop.\nRoll width, maximum job length and tiling depend on the printer/driver.","Banner printing",JOptionPane.INFORMATION_MESSAGE);return;}

        try{if(working)throw new IllegalStateException("Wait for the current operation.");Publication p=PublicationPrintOptions.choose(this,printOutput(),pageIndex);if(p==null)return;PublicationPreflight.requireMergeData(p);PublicationRenderer renderer=new PublicationRenderer(p);int count=renderer.sheets();

            if(!showPrintPreview(p))return;

            PrinterJob job=PrinterJob.getPrinterJob();job.setJobName(p.title);PageFormat format=job.defaultPage();Paper stock=new Paper();double scale=72/25.4;

            stock.setSize(p.print.width*scale,p.print.height*scale);stock.setImageableArea(0,0,stock.getWidth(),stock.getHeight());format.setPaper(stock);

            javax.print.attribute.PrintRequestAttributeSet settings=new javax.print.attribute.HashPrintRequestAttributeSet();



            javax.print.attribute.standard.MediaSizeName media=switch(paper.getSelectedItem().toString()){case "A4"->javax.print.attribute.standard.MediaSizeName.ISO_A4;case "Legal"->javax.print.attribute.standard.MediaSizeName.NA_LEGAL;case "11 × 17"->javax.print.attribute.standard.MediaSizeName.TABLOID;case "Custom"->null;default->javax.print.attribute.standard.MediaSizeName.NA_LETTER;};if(media!=null)settings.add(media);

            settings.add(PublicationPrintOptions.sides(p));
            settings.add(PublicationPrinting.requestedArea(p,job.getPrintService(),settings));

            job.setPrintable((g,pf,index)->Printable.NO_SUCH_PAGE,format);

            if(!job.printDialog(settings))return;

            settings.add(PublicationPrintOptions.sides(p));

            if(p.print.duplex&&!job.getPrintService().isAttributeValueSupported(PublicationPrintOptions.sides(p),null,settings))throw new IllegalStateException("This printer does not support the selected two-sided mode. Choose single-sided or a duplex printer.");

            settings.remove(javax.print.attribute.standard.MediaPrintableArea.class);
            settings.add(PublicationPrinting.requestedArea(p,job.getPrintService(),settings));
            PageFormat actual=job.getPageFormat(settings);

            PublicationPrinting.requireFits(p,actual);

            job.setPrintable(PublicationPrinting.printable(p),actual);

            run(billBooks?"Sending bill books to printer…":"Sending business cards to printer…",() -> {job.print(settings);return true;},ignored -> status.setText("Print job sent • Verify physical size and cut alignment."));

        }catch(Exception ex){error(ex);}

    }

    private void separateRiso(){try{if(working)throw new IllegalStateException("Wait for the current operation.");Publication p=PublicationPrintOptions.chooseRiso(this,printOutput(),pageIndex);if(p!=null){p.print.duplex=false;RisoSeparationDialog.open(this,p);}}catch(Exception ex){error(ex);}}
    private void organiseRiso(int mainPage,String subPage){try{if(working)throw new IllegalStateException("Wait for the current operation.");var result=RisoSubPagesDialog.edit(this,output(),mainPage,subPage);if(result!=null){document=result.document();pageIndex=result.page();refreshPages();configureDesign();changed();status.setText("Linked Riso sub-pages updated • Save the template to keep these assignments.");if(result.saveRequested())saveChanges();}else pagePreviews.select(pageIndex);}catch(Exception ex){error(ex);}}

    private void exportPdf()throws Exception{if(banners){Publication banner=output();Path file=choose(true,"Banner at actual size","pdf");if(file!=null)run("Exporting banner PDF…",()->{BannerDocuments.pdf(banner,file);return file;},saved->status.setText("Saved "+saved.getFileName()+" • "+sizeDescription(banner)+" • Actual size; confirm UserUnit support with printer."));return;}Publication p=printOutput();PublicationPreflight.requireMergeData(p);int count=new PublicationRenderer(p).sheets();Path path=choose(true,billBooks?"Bill book sheets":labels?"Label sheets":"Business card sheets","pdf");if(path!=null)run("Exporting PDF…",() -> {PublicationFiles.pdf(p,path,0,count-1);return path;},saved -> status.setText("Saved "+saved.getFileName()+" • Print at actual size / 100%."));}

    private Path choose(boolean save,String title,String... extensions){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(title);chooser.setFileFilter(new FileNameExtensionFilter(title,extensions));if((save?chooser.showSaveDialog(this):chooser.showOpenDialog(this))!=JFileChooser.APPROVE_OPTION)return null;Path path=chooser.getSelectedFile().toPath();if(save&&!path.toString().toLowerCase(Locale.ROOT).endsWith("."+extensions[0]))path=Path.of(path+"."+extensions[0]);if(save&&Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace "+path.getFileName()+"?",title,JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;return path;}

    private interface Action{void call()throws Exception;}

    private void addButton(JPanel panel,String text,Action action){JButton button=new JButton(text);DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);button.addActionListener(e -> {try{if(working)throw new IllegalStateException("Wait for the current operation.");action.call();}catch(Exception ex){error(ex);}});panel.add(button);}

    private <T> void run(String message,java.util.concurrent.Callable<T> task,java.util.function.Consumer<T> done){if(working)return;working=true;int generation=sessionGeneration;status.setText(message);new SwingWorker<T,Void>(){protected T doInBackground()throws Exception{return task.call();}protected void done(){working=false;if(generation!=sessionGeneration)return;try{done.accept(get());}catch(Exception ex){error(ex.getCause()==null?ex:ex.getCause());}finally{if(previewPending){previewPending=false;refresh.restart();}}}}.execute();}

    private void error(Throwable ex){status.setText(ex.getMessage());JOptionPane.showMessageDialog(this,ex.getMessage(),billBooks?"Bill Books":banners?"Banners":labels?"Labels":"Business Cards",JOptionPane.ERROR_MESSAGE);}

    private final class LayerModel extends AbstractTableModel{

        final String[] names={"Text / {{field}}","X mm","Y mm","Width mm","Height mm","Font pt","Bold","Colour","Font family","Rotation °","Fill","Stroke","Type"};

        public int getRowCount(){return currentPage().objects.size();}public int getColumnCount(){return names.length;}public String getColumnName(int c){return names[c];}public boolean isCellEditable(int r,int c){return c!=12;}

        public Object getValueAt(int r,int c){var e=currentPage().objects.get(r);return switch(c){case 0->e.text;case 1->e.x;case 2->e.y;case 3->e.width;case 4->e.height;case 5->e.fontSize;case 6->e.bold;case 7->e.color;case 8->e.font;case 9->e.rotation;case 10->e.fill;case 11->e.stroke;default->e.sourceTable.isBlank()?e.type:"CELL R"+(e.sourceRow+1)+" C"+(e.sourceColumn+1);};}

        public void setValueAt(Object value,int r,int c){Publication old=document.copy();try{var e=currentPage().objects.get(r);String s=value.toString();switch(c){case 0->e.text=s;case 1->e.x=Double.parseDouble(s);case 2->e.y=Double.parseDouble(s);case 3->e.width=Double.parseDouble(s);case 4->e.height=Double.parseDouble(s);case 5->e.fontSize=Double.parseDouble(s);case 6->e.bold=Boolean.parseBoolean(s);case 7->e.color=s;case 8->e.font=s;case 9->e.rotation=Double.parseDouble(s);case 10->e.fill=s;case 11->e.stroke=s;}document.validate();changed();}catch(Exception ex){document=old;fireTableDataChanged();error(ex);}}

    }

}

