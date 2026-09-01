package ui.screens.companyprefs;

import managers.CompanyCustomizationManager;
import services.WalletBadgeTemplate;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/** Wallet-native field editor, deliberately not a free-position printed-card canvas. */
public final class WalletTemplateEditor extends JDialog {
    private final JTextField company = new JTextField(22), title = new JTextField(22);
    private String background = "#003760", foreground = "#FFFFFF", labels = "#FFB570";
    private String logo = "", thumbnail = "";
    private String artwork = "", signature = "";
    private final JSpinner signatureSize = new JSpinner(new SpinnerNumberModel(100,20,100,5));
    private final JSpinner logoSize = new JSpinner(new SpinnerNumberModel(100,20,100,5));
    private final JSpinner thumbnailSize = new JSpinner(new SpinnerNumberModel(100,20,100,5));
    private final JSpinner zoom = new JSpinner(new SpinnerNumberModel(100,60,160,10));
    private final JCheckBox photo = new JCheckBox("Use employee photo instead of extra image");
    private final JLabel status = new JLabel("Loading Wallet template…");
    private final JLabel preview = new JLabel();
    private final JTextArea details = new JTextArea(5,24);
    private final JButton save = new JButton("Save Wallet template");
    private boolean loading = true;
    private boolean saving;
    private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Show", "Position", "Information", "Label", "Custom text"},0) {
        @Override public Class<?> getColumnClass(int c) { return c==0?Boolean.class:String.class; }
    };
    private final JTable fields = new JTable(rows);
    private static final String[] POSITIONS = {"Primary", "Header", "Secondary", "Auxiliary", "Details"};
    private static final String[] SOURCES = {"NAME","FIRST_NAME","LAST_NAME","USERNAME","ROLE","LOCATION","EMAIL","PHONE","COMPANY","ISSUED","TEXT"};
    private final CompanyCustomizationManager.BadgeTemplateSettings badge;
    private final services.WalletCompanyBranding.Info companyInfo;

    public WalletTemplateEditor(Window owner, CompanyCustomizationManager.BadgeTemplateSettings badge,
                                CompanyCustomizationManager.ReceiptSettings companySettings) {
        super(owner, "Apple Wallet Badge Template", ModalityType.APPLICATION_MODAL);
        this.badge = badge;
        this.companyInfo = services.WalletCompanyBranding.from(companySettings);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        root.setBackground(new Color(248,250,252));
        setContentPane(root);
        JLabel help = new JLabel("<html><b>Apple Wallet Employee Badge</b><br>Apple controls card dimensions and text sizing. Resize images within their slots; zoom changes only this preview.<br>Changes apply to newly issued badges at this store. Existing Wallet badges must be replaced.</html>");
        root.add(help, BorderLayout.NORTH);
        JPanel settings = new JPanel(); settings.setOpaque(false); settings.setLayout(new BoxLayout(settings,BoxLayout.Y_AXIS));
        settings.add(line("Company", company)); settings.add(line("Description", title));
        JPanel companyActions = new JPanel(new FlowLayout(FlowLayout.LEFT));companyActions.setOpaque(false);
        addButton(companyActions,"Pull existing company info",this::pullCompanyInfo);settings.add(companyActions);
        JPanel colors = new JPanel(new FlowLayout(FlowLayout.LEFT)); colors.setOpaque(false);
        for (String name : List.of("Background", "Text", "Labels")) {
            JButton button = new JButton(name + " color");
            button.addActionListener(e -> {
                Color selected = JColorChooser.showDialog(this, name, Color.decode(name.equals("Background")?background:name.equals("Text")?foreground:labels));
                if (selected != null) {
                    String hex = String.format("#%06X", selected.getRGB() & 0xFFFFFF);
                    if(name.equals("Background"))background=hex;else if(name.equals("Text"))foreground=hex;else labels=hex;
                    refresh();
                }
            }); colors.add(button);
        }
        settings.add(colors);
        fields.setRowHeight(28); fields.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        fields.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(POSITIONS)));
        fields.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(SOURCES)));
        fields.getColumnModel().getColumn(0).setMaxWidth(55);
        JScrollPane table = new JScrollPane(fields); table.setPreferredSize(new Dimension(600,230)); settings.add(table);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT)); buttons.setOpaque(false);
        addButton(buttons,"Add text field",()->{stopEditing();rows.addRow(new Object[]{true,"Details","TEXT","NOTE",""});});
        addButton(buttons,"Remove",()->{int i=fields.getSelectedRow();stopEditing();if(i>=0)rows.removeRow(i);});
        addButton(buttons,"Move up",()->move(-1)); addButton(buttons,"Move down",()->move(1));
        addButton(buttons,"Import badge branding/text",this::importBadge);
        settings.add(buttons);
        settings.add(new JLabel("TEXT uses Custom text. Other sources use the employee’s current badge information."));
        settings.add(imageControls("Logo (160 × 50 pt)",true,logoSize));
        settings.add(imageControls("Extra image (90 × 90 pt)",false,thumbnailSize));
        settings.add(photo);
        JPanel posterControls=line("Poster background (iOS 27+)",new JLabel("Older devices keep the standard layout"));
        addButton(posterControls,"Choose background",this::chooseBackground);
        addButton(posterControls,"Remove background",()->{artwork="";refresh();});settings.add(posterControls);
        JPanel signatureControls=line("Bottom-right signature size %",signatureSize);
        addButton(signatureControls,"Use employee badge CEO signature",this::importSignature);
        addButton(signatureControls,"Remove signature",()->{signature="";refresh();});settings.add(signatureControls);
        settings.add(new JLabel("Signature sits above the reserved QR/footer area, not below the native QR. Verify placement on iPhone."));
        settings.add(new JLabel("Poster mode uses a single footer summary; secondary/auxiliary details remain in Details. Thumbnail is standard-layout only."));
        settings.add(new JLabel("One logo and one thumbnail slot. Apple Watch may omit images. QR login cannot be hidden."));
        JPanel previewPane = new JPanel(new BorderLayout(4,8)); previewPane.setOpaque(false);
        previewPane.add(line("Approximate preview zoom %",zoom),BorderLayout.NORTH);
        preview.setVerticalAlignment(SwingConstants.TOP);
        previewPane.add(new JScrollPane(preview),BorderLayout.CENTER);
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        previewPane.add(new JScrollPane(details),BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(settings),previewPane);
        split.setResizeWeight(.62);root.add(split,BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());footer.setOpaque(false);footer.add(status,BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);
        JButton close = new JButton("Close");close.addActionListener(e->dispose());actions.add(close);actions.add(save);footer.add(actions,BorderLayout.EAST);root.add(footer,BorderLayout.SOUTH);
        save.setEnabled(false);save.addActionListener(e->save());
        rows.addTableModelListener(e->refresh()); photo.addActionListener(e->refresh());
        logoSize.addChangeListener(e->refresh());thumbnailSize.addChangeListener(e->refresh());zoom.addChangeListener(e->refresh());
        signatureSize.addChangeListener(e->refresh());
        DocumentListener changed = new DocumentListener(){public void insertUpdate(DocumentEvent e){refresh();}public void removeUpdate(DocumentEvent e){refresh();}public void changedUpdate(DocumentEvent e){refresh();}};
        company.getDocument().addDocumentListener(changed);title.getDocument().addDocumentListener(changed);
        setSize(1120,760);setLocationRelativeTo(owner);
        UiTaskRunner.submit(this,"wallet-template.load",()->services.WalletCompanyBranding.initial(
                        CompanyCustomizationManager.loadWalletTemplate(),companyInfo,utils.ImageCacheManager::loadImage),
                value->{populate(value);loading=false;refresh();},ex->{status.setText("Could not load Wallet template. Close and retry.");});
    }
    private static JPanel line(String label,JComponent control){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT));p.setOpaque(false);p.add(new JLabel(label));p.add(control);return p;}
    private void chooseBackground(){
        if(loading||saving)return;
        JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images","png","jpg","jpeg"));
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        var path=chooser.getSelectedFile().toPath();
        UiTaskRunner.submit(this,"wallet-background.import",()->{
            if(Files.size(path)>10_000_000)throw new IllegalArgumentException("Choose an image below 10 MB.");
            BufferedImage image=WalletBadgeTemplate.readImage(Files.readAllBytes(path));
            double ratio=Math.min(1074.0/image.getWidth(),1344.0/image.getHeight());
            String encoded=Base64.getEncoder().encodeToString(WalletBadgeTemplate.image(image,Math.max(1,(int)(image.getWidth()*ratio)),Math.max(1,(int)(image.getHeight()*ratio)),100));
            WalletBadgeTemplate.decodeArtwork(encoded);return encoded;
        },encoded->{artwork=encoded;refresh();},ex->JOptionPane.showMessageDialog(this,ex.getMessage(),"Background could not be loaded",JOptionPane.ERROR_MESSAGE));
    }
    private void importSignature(){
        if(loading||saving||badge==null)return;
        String path=services.BadgePrintService.signatureImagePath(badge);
        if(path.isBlank()){JOptionPane.showMessageDialog(this,"No signature image is configured in the current employee badge template.");return;}
        UiTaskRunner.submit(this,"wallet-signature.import",()->{
            BufferedImage image=utils.ImageCacheManager.loadImage(path);
            if(image==null)throw new IllegalArgumentException("The employee badge signature could not be loaded.");
            return Base64.getEncoder().encodeToString(WalletBadgeTemplate.image(image,396,168,100));
        },encoded->{signature=encoded;refresh();},ex->JOptionPane.showMessageDialog(this,ex.getMessage(),"Signature could not be loaded",JOptionPane.ERROR_MESSAGE));
    }
    private static void addButton(JPanel panel,String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->action.run());panel.add(b);}
    private void stopEditing(){if(fields.isEditing())fields.getCellEditor().stopCellEditing();}
    private void pullCompanyInfo(){
        if(loading||saving)return;
        stopEditing();
        if(JOptionPane.showConfirmDialog(this,"Use the company name/logo from Company Preferences and add missing contact fields? Your layout and other fields will be kept.","Pull company info",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try {
            var draft=value();loading=true;save.setEnabled(false);status.setText("Loading company info…");
            UiTaskRunner.submit(this,"wallet-company.import",()->services.WalletCompanyBranding.apply(draft,companyInfo,utils.ImageCacheManager::loadImage),
                    updated->{populate(updated);loading=false;refresh();status.setText("Company info loaded. Select contact fields to show, then save the Wallet template.");},
                    ex->{loading=false;refresh();status.setText("Company info could not be loaded: "+ex.getMessage());});
        }catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Check template",JOptionPane.WARNING_MESSAGE);}
    }
    private void move(int direction){int i=fields.getSelectedRow();stopEditing();int next=i+direction;if(i>=0&&next>=0&&next<rows.getRowCount()){rows.moveRow(i,i,next);fields.setRowSelectionInterval(next,next);}}
    private JPanel imageControls(String label,boolean isLogo,JSpinner size){
        JPanel p=line(label,size);
        addButton(p,"Choose image",()->{
            JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images (PNG/JPEG)","png","jpg","jpeg"));
            if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            var path=chooser.getSelectedFile().toPath();
            UiTaskRunner.submit(this,"wallet-image.import",()->{
                if(Files.size(path)>10_000_000)throw new IllegalArgumentException("Choose an image below 10 MB.");
                BufferedImage image=WalletBadgeTemplate.readImage(Files.readAllBytes(path));
                return Base64.getEncoder().encodeToString(WalletBadgeTemplate.image(image,isLogo?480:270,isLogo?150:270,100));
            },encoded->{if(isLogo)logo=encoded;else{thumbnail=encoded;photo.setSelected(false);}refresh();},ex->JOptionPane.showMessageDialog(this,ex.getMessage(),"Image could not be loaded",JOptionPane.ERROR_MESSAGE));
        });
        addButton(p,"Remove",()->{if(isLogo)logo="";else thumbnail="";refresh();});return p;
    }
    private void populate(WalletBadgeTemplate t){
        artwork=t.poster().backgroundPng();signature=t.poster().signaturePng();signatureSize.setValue(t.poster().signaturePercent());
        company.setText(t.company());title.setText(t.title());background=t.background();foreground=t.foreground();labels=t.labelColor();logo=t.logoPng();thumbnail=t.thumbnailPng();logoSize.setValue(t.logoScale());thumbnailSize.setValue(t.thumbnailScale());photo.setSelected(t.employeePhoto());
        rows.setRowCount(0);for(var f:t.fields())rows.addRow(new Object[]{f.visible(),POSITIONS[WalletBadgeTemplate.SECTIONS.indexOf(f.section())],f.source(),f.label(),f.text()});
    }
    private WalletBadgeTemplate value(){
        List<WalletBadgeTemplate.Field> list=new ArrayList<>();
        for(int i=0;i<rows.getRowCount();i++)list.add(new WalletBadgeTemplate.Field(Boolean.TRUE.equals(rows.getValueAt(i,0)),WalletBadgeTemplate.SECTIONS.get(Arrays.asList(POSITIONS).indexOf(rows.getValueAt(i,1))),String.valueOf(rows.getValueAt(i,2)),String.valueOf(rows.getValueAt(i,3)),String.valueOf(rows.getValueAt(i,4))));
        return new WalletBadgeTemplate(company.getText(),title.getText(),background,foreground,labels,list,logo,thumbnail,(int)logoSize.getValue(),(int)thumbnailSize.getValue(),photo.isSelected(),new WalletBadgeTemplate.Poster(artwork,signature,(int)signatureSize.getValue()));
    }
    private void refresh(){
        if(loading)return;
        try{var t=value();preview.setIcon(new ImageIcon(WalletTemplatePreview.render(t,(int)zoom.getValue())));details.setText(WalletTemplatePreview.details(t));status.setText("Preview only — Apple controls final layout.");save.setEnabled(!saving);}
        catch(Exception ex){status.setText(ex.getMessage());save.setEnabled(false);}
    }
    private void importBadge(){
        if(badge==null)return;
        if(JOptionPane.showConfirmDialog(this,"Replace the company name and append the badge quote/instructions?","Import badge",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        company.setText(badge.companyName());
        rows.addRow(new Object[]{badge.showQuote(),"Details","TEXT","QUOTE",badge.quoteLine()});
        rows.addRow(new Object[]{true,"Details","TEXT","INSTRUCTIONS",badge.backInstructions()});
        for(String id:List.of("front.custom1","front.custom2","back.custom1","back.custom2")) {
            String text=services.BadgePrintService.customText(badge,id);
            if(!text.isBlank())rows.addRow(new Object[]{true,"Details","TEXT","NOTE",text});
        }
        UiTaskRunner.submit(this,"wallet-badge-logo",()->{
            BufferedImage image=utils.ImageCacheManager.loadImage(badge.logoPath());
            return image==null?"":Base64.getEncoder().encodeToString(WalletBadgeTemplate.image(image,480,150,100));
        },encoded->{if(!encoded.isEmpty())logo=encoded;refresh();},ex->status.setText("Text imported; badge logo could not be loaded."));
    }
    private void save(){
        stopEditing();
        try{
            var t=value();saving=true;save.setEnabled(false);status.setText("Saving…");
            UiTaskRunner.submit(this,"wallet-template.save",()->{CompanyCustomizationManager.saveWalletTemplate(t);return true;},
                    done->{saving=false;refresh();status.setText("Saved submitted template. Newly issued badges will use it.");},
                    ex->{saving=false;refresh();status.setText("Save failed: "+ex.getMessage());});
        }catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Check template",JOptionPane.WARNING_MESSAGE);}
    }
}
