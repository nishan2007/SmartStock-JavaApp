package gy.deckers.studio;

import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
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
    private final Timer refresh;
    private long generation;
    private boolean dirty;
    private Path currentFile;
    private final JTable table=new JTable(layers);

    BadgeCardsPanel() {
        super(new BorderLayout(12,12)); setOpaque(false);
        JPanel content=DeckersSwing.panel();content.setLayout(new BorderLayout(12,12));content.setPreferredSize(new Dimension(720,640));
        JScrollPane workspaceScroll=new JScrollPane(content);workspaceScroll.setBorder(null);workspaceScroll.getVerticalScrollBar().setUnitIncrement(24);add(workspaceScroll);
        refresh=new Timer(180,e -> renderPreview()); refresh.setRepeats(false);
        JPanel toolbar=DeckersSwing.panel(); toolbar.setLayout(new GridLayout(3,1));
        JPanel libraryTools=DeckersSwing.panel();libraryTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        libraryTools.add(DeckersSwing.metaLabel("Templates"));libraryTemplates.setPreferredSize(new Dimension(260,32));
        libraryTemplates.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){return super.getListCellRendererComponent(list,value instanceof Path p?p.getFileName().toString().replaceFirst("(?i)\\.sstbadge$",""):"Choose a saved template",index,selected,focus);}});
        libraryTemplates.addActionListener(e->{if(updatingLibrary)return;Path file=(Path)libraryTemplates.getSelectedItem();if(file==null)return;try{if(discard())loadTemplate(file);else{updatingLibrary=true;libraryTemplates.setSelectedItem(library.contains(currentFile)?currentFile:null);updatingLibrary=false;}}catch(Exception ex){error(ex);}});
        libraryTools.add(libraryTemplates);addButton(libraryTools,"Refresh",this::refreshLibrary);addButton(libraryTools,"Template folder",this::chooseFolder);toolbar.add(libraryTools);
        JPanel fileTools=DeckersSwing.panel();fileTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        JPanel outputTools=DeckersSwing.panel();outputTools.setLayout(new FlowLayout(FlowLayout.LEFT,8,4));
        toolbar.add(fileTools);toolbar.add(outputTools);
        addButton(fileTools,"Skeldon Primary",() -> { if(discard()) { template=BadgeTemplate.builtin(); currentFile=null; resetFields(); changedTemplate(); } });
        addButton(fileTools,"New blank",() -> { if(discard()) {template=new BadgeTemplate(); currentFile=null;resetFields();changedTemplate();} });
        addButton(fileTools,"Open / import",this::open);
        addButton(fileTools,"Save template",() -> save(false));
        addButton(outputTools,"Export template",() -> save(true));
        addButton(outputTools,"Export PNG",() -> export(false));
        addButton(outputTools,"Export PDF",() -> export(true));
        content.add(toolbar,BorderLayout.NORTH);

        details.setLayout(new GridLayout(0,2,8,8));
        JScrollPane detailsScroll=new JScrollPane(details);detailsScroll.setPreferredSize(new Dimension(310,220));detailsScroll.setBorder(null);
        JPanel card=DeckersSwing.panel();card.setLayout(new BorderLayout(8,8));card.add(detailsScroll,BorderLayout.WEST);
        preview.setPreferredSize(new Dimension(700,450));card.add(new JScrollPane(preview));
        preview.configure(() -> template, this::values, () -> photo, row -> table.setRowSelectionInterval(row,row), () -> {dirty=true;layers.fireTableDataChanged();});
        table.getSelectionModel().addListSelectionListener(e -> preview.select(table.getSelectedRow()));
        preview.addComponentListener(new java.awt.event.ComponentAdapter(){public void componentResized(java.awt.event.ComponentEvent e){refresh.restart();}});
        name.setColumns(8);DeckersSwing.styleField(name);
        DeckersSwing.styleTable(table,DeckersPalette.PURPLE);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setRowHeight(28);
        int[] widths={140,65,170,90,65,65,65,65,80,130,65,90};
        for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane edits=new JScrollPane(table);edits.setPreferredSize(new Dimension(500,200));
        JPanel editor=DeckersSwing.panel();editor.setLayout(new BorderLayout(8,8));editor.add(DeckersSwing.metaLabel("Drag layers onto or outside the white print area • double-click a cell to edit"),BorderLayout.NORTH);editor.add(edits);
        JPanel layerTools=DeckersSwing.panel();layerTools.setLayout(new FlowLayout(FlowLayout.LEFT));
        addButton(layerTools,"Add text",() -> {commitCell();BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Text";e.text="Text";e.width=Math.min(30,template.widthMm);e.height=6;template.elements.add(e);rebuildFields(true);layers.fireTableDataChanged();refresh.restart();dirty=true;});
        addButton(layerTools,"Add photo field",() -> {commitCell();BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Photo";e.type="PHOTO";e.width=Math.min(18,template.widthMm);e.height=Math.min(24,template.heightMm);template.elements.add(e);layers.fireTableDataChanged();refresh.restart();dirty=true;});
        addButton(layerTools,"Replace artwork",this::replaceArtwork);
        addButton(layerTools,"Remove layer",() -> {commitCell();int row=table.getSelectedRow();if(row>=0){template.elements.remove(row);rebuildFields(true);layers.fireTableDataChanged();refresh.restart();dirty=true;}});
        editor.add(layerTools,BorderLayout.SOUTH);
        JPanel body=DeckersSwing.panel();body.setLayout(new GridLayout(2,1,0,12));body.add(card);body.add(editor);content.add(body);
        content.add(message,BorderLayout.SOUTH);
        name.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){dirty=true;}public void removeUpdate(DocumentEvent e){dirty=true;}public void changedUpdate(DocumentEvent e){dirty=true;}});
        try {template=BadgeTemplate.builtin();changedTemplate();}catch(IOException ex){template=new BadgeTemplate();changedTemplate();error(ex);}
        try{refreshLibrary();}catch(IOException ex){message.setText("Template folder unavailable. Choose Template folder to change it.");}
    }

    void resetFields(){recordFields.values().forEach(f->f.setText(""));photo=null;generation++;preview.repaint();refresh.restart();}
    boolean discard(){return !dirty || JOptionPane.showConfirmDialog(this,"Discard unsaved template changes?","Badge Cards",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private void changedTemplate(){name.setText(template.name);name.setCaretPosition(0);rebuildFields(false);layers.fireTableDataChanged();dirty=false;refresh.restart();}
    private void commitCell() throws IOException {if(table.isEditing()&&!table.getCellEditor().stopCellEditing())throw new IOException("Finish editing the current design cell.");}
    private Map<String,String> values(){Map<String,String> result=new LinkedHashMap<>();recordFields.forEach((key,field)->{if(!field.getText().isBlank())result.put(key,field.getText());});return result;}
    private void rebuildFields(boolean preserve){
        Map<String,String> old=preserve?values():Map.of();details.removeAll();recordFields.clear();details.add(DeckersSwing.metaLabel("Template name"));details.add(name);
        for(BadgeTemplate.Element element:template.elements){if(!"TEXT".equals(element.type)||element.field.isEmpty()||recordFields.containsKey(element.field))continue;
            JTextField field=new JTextField(old.getOrDefault(element.field,""),8);DeckersSwing.styleField(field);recordFields.put(element.field,field);details.add(DeckersSwing.metaLabel(element.label));details.add(field);
            field.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){refresh.restart();}public void removeUpdate(DocumentEvent e){refresh.restart();}public void changedUpdate(DocumentEvent e){refresh.restart();}});
        }
        addButton(details,"Choose photo",this::choosePhoto);addButton(details,"Clear details",()->{resetFields();refresh.restart();});details.setPreferredSize(new Dimension(280,Math.max(220,((details.getComponentCount()+1)/2)*44)));details.revalidate();details.repaint();
    }
    private static Path savedFolder(){
        String fallback=Path.of(System.getProperty("smartstudio.register.home",System.getProperty("user.home")),"Documents","SmartStudio","Badge Templates").toString();
        try{return Path.of(System.getProperty("smartstudio.badge.folder",Preferences.userNodeForPackage(BadgeCardsPanel.class).get("badgeTemplateFolder",fallback)));}catch(RuntimeException ex){return Path.of(fallback);}
    }
    private void chooseFolder()throws IOException{
        JFileChooser chooser=new JFileChooser();chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setDialogTitle("Choose the one Badge Templates folder");chooser.setCurrentDirectory(library.folder().toFile());
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path chosen=chooser.getSelectedFile().toPath().toAbsolutePath().normalize();Files.createDirectories(chosen);library=new BadgeTemplateLibrary(chosen);
        Preferences.userNodeForPackage(BadgeCardsPanel.class).put("badgeTemplateFolder",chosen.toString());
        if(!library.contains(currentFile)){currentFile=null;currentFingerprint=null;}
        refreshLibrary();message.setText("Template folder: "+chosen.getFileName()+" • OneDrive file sharing is handled by Windows.");message.setToolTipText(chosen.toString());
    }
    private void refreshLibrary()throws IOException{
        var files=library.templates();updatingLibrary=true;
        try{libraryTemplates.removeAllItems();for(Path file:files)libraryTemplates.addItem(file);libraryTemplates.setSelectedItem(library.contains(currentFile)?currentFile:null);libraryTemplates.setToolTipText(library.folder().toString());}finally{updatingLibrary=false;}
    }
    private void loadTemplate(Path file)throws IOException{
        String fingerprint=BadgeTemplateLibrary.fingerprint(file);BadgeTemplate loaded=BadgeTemplate.load(file);if(!fingerprint.equals(BadgeTemplateLibrary.fingerprint(file)))throw new IOException("This template changed while opening. Wait for file synchronization to finish, then reopen it.");template=loaded;currentFile=library.contains(file)?file:null;currentFingerprint=currentFile==null?null:fingerprint;resetFields();changedTemplate();dirty=currentFile==null;
        message.setText(currentFile==null?"Imported design. Save template to add it to your library.":"Opened library template: "+file.getFileName());
    }

    private void renderPreview(){
        preview.repaint();
    }

    private void open()throws Exception{
        if(!discard())return;JFileChooser chooser=chooser("SmartStudio badge template","sstbadge");
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        loadTemplate(chooser.getSelectedFile().toPath());
    }
    private void save(boolean export)throws Exception{
        commitCell();template.name=name.getText().trim();template.validate();
        Path target=library.contains(currentFile)?currentFile:library.fileFor(template.name);
        if(export){JFileChooser chooser=chooser("SmartStudio badge template","sstbadge");chooser.setSelectedFile(library.fileFor(template.name).toFile());if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;target=extension(chooser.getSelectedFile().toPath(),"sstbadge");if(!replace(target))return;}
        else if(currentFile==null){if(!replace(target))return;}
        else if(!Files.exists(target)||!BadgeTemplateLibrary.fingerprint(target).equals(currentFingerprint))throw new IOException("This template changed on disk. Export your draft to a new file, then reopen the updated library template before editing it.");
        template.save(target);if(!export){currentFile=target;currentFingerprint=BadgeTemplateLibrary.fingerprint(target);dirty=false;refreshLibrary();}
        message.setText((export?"Exported template: ":"Saved in template folder: ")+target.getFileName());message.setToolTipText(target.toString());
    }
    private void choosePhoto()throws Exception{
        JFileChooser chooser=chooser("PNG and JPEG photos","png","jpg","jpeg");if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        photo=StudioImageFile.load(chooser.getSelectedFile().toPath()).image();refresh.restart();
    }
    private void replaceArtwork()throws Exception{
        commitCell();int row=table.getSelectedRow();if(row<0||!"IMAGE".equals(template.elements.get(row).type))throw new IOException("Select an artwork layer first.");
        JFileChooser chooser=chooser("PNG and JPEG artwork","png","jpg","jpeg");if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        template.elements.get(row).image=java.util.Base64.getEncoder().encodeToString(StudioImageFile.load(chooser.getSelectedFile().toPath()).bytes());dirty=true;refresh.restart();
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
    private void addButton(JPanel panel,String label,Action action){JButton button=new JButton(label);DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);button.addActionListener(e->{try{action.run();}catch(Exception ex){error(ex);}});panel.add(button);}

    private final class LayerModel extends AbstractTableModel{
        private final String[] columns={"Layer","Type","Text / placeholder","Data field","X mm","Y mm","Width mm","Height mm","Font mm","Font","Bold","Colour"};
        public int getRowCount(){return template==null?0:template.elements.size();}public int getColumnCount(){return columns.length;}public String getColumnName(int c){return columns[c];}
        public Class<?> getColumnClass(int c){return c==10?Boolean.class:String.class;}
        public boolean isCellEditable(int r,int c){return c!=1;}
        public Object getValueAt(int r,int c){BadgeTemplate.Element e=template.elements.get(r);return switch(c){case 0->e.label;case 1->e.type;case 2->e.text;case 3->e.field;case 4->e.x;case 5->e.y;case 6->e.width;case 7->e.height;case 8->e.fontMm;case 9->e.font;case 10->e.bold;default->e.color;};}
        public void setValueAt(Object value,int r,int c){
            BadgeTemplate.Element e=template.elements.get(r);Object old=getValueAt(r,c);
            try{assign(e,c,value);template.validate();if(c==3||c==0)rebuildFields(true);dirty=true;refresh.restart();}catch(Exception ex){assign(e,c,old);error(ex);}fireTableCellUpdated(r,c);
        }
        private void assign(BadgeTemplate.Element e,int c,Object v){String s=String.valueOf(v);switch(c){case 0->e.label=s;case 2->e.text=s;case 3->e.field=s;case 4->e.x=Double.parseDouble(s);case 5->e.y=Double.parseDouble(s);case 6->e.width=Double.parseDouble(s);case 7->e.height=Double.parseDouble(s);case 8->e.fontMm=Double.parseDouble(s);case 9->e.font=s;case 10->e.bold=Boolean.parseBoolean(s);case 11->e.color=s;default->{}}}
    }
}
