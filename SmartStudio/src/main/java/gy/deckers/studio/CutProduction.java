package gy.deckers.studio;

import publishing.*;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.*;
import java.awt.print.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

/** Production tasks reuse the editor/ribbon. Device state is local to this workstation. */
final class CutProduction {
    private final Component owner;
    private final Supplier<Publication> source;
    private final IntSupplier selectedPage;
    private final boolean vinyl;
    private CutJob.Settings settings=new CutJob.Settings();
    private CutterDevice.Profile profile=new CutterDevice.Profile();
    private volatile boolean busy;
    private JDialog production;
    final JCheckBox enabled=new JCheckBox("Print & Cut labels");
    private final JLabel status=new JLabel("Create a frozen job to print and cut matching geometry.");
    private final Path setupFile;
    CutProduction(Component owner,Supplier<Publication> source,IntSupplier page,boolean vinyl){
        this.owner=owner;this.source=source;selectedPage=page;this.vinyl=vinyl;
        settings.vinyl=vinyl;if(vinyl){settings.width=609.6;settings.bleed=0;settings.rotateLoading=false;settings.contour=CutJob.Contour.TEXT;}
        setupFile=Path.of(System.getProperty("user.home"),"cut-production",vinyl?"vinyl-setup.json":"label-setup.json");
        enabled.setSelected(vinyl);enabled.setOpaque(false);
        try{if(Files.isRegularFile(setupFile)){if(Files.size(setupFile)>200000)throw new IllegalArgumentException("Saved cut setup is too large.");SavedSetup saved=Publication.JSON.fromJson(Files.readString(setupFile),SavedSetup.class);saved.media.validate();settings=saved.media;settings.vinyl=vinyl;profile=saved.cutter;profile.protocolVerified=false;}}
        catch(Exception ex){status.setText("Saved cut setup could not be loaded; review settings before use.");}
    }
    boolean busy(){return busy;}
    void close(){if(production!=null)production.dispose();}
    void install(StudioWorkspaceShell shell){
        if(!vinyl)shell.ribbon().add("Print Setup","Labels",enabled);
        shell.ribbon().add("Print Setup","Production",StudioRibbon.button("Roll / sheet setup",()->safe(this::media)));
        shell.ribbon().add("Print Setup","Production",StudioRibbon.button("Production preview",this::open));
        shell.ribbon().add("Cut Setup","Cutter",StudioRibbon.button("B-870 setup",()->safe(this::device)));
        shell.ribbon().add("Cut Setup","Cutter",StudioRibbon.button("Measured pen calibration",()->safe(this::calibrate)));
        shell.ribbon().add("Cut Setup","Job",StudioRibbon.button("Create cut job",this::open));
        shell.ribbon().add("Cut Setup","Job",StudioRibbon.button("Open saved cut job",()->safe(this::loadJob)));
        shell.ribbon().add("Cut Setup","Help",StudioRibbon.button("Setup guide",()->{
            JTextArea guide=new JTextArea("1. Verify B-870 nameplate, COM driver, baud, HPGL units/mm and axes.\n2. Set material speed, force and blade offset on the cutter.\n3. Choose media size, loading rotation and grip edges.\n4. Create a frozen job; print at actual size or export PDF.\n5. Load with the shown orientation, retain grip allowance, set origin on the panel.\n6. Align three targets using coordinates from one common origin.\n7. Send a pen proof, inspect it, then confirm and send the blade cut.\n\nOn interruption, stop at the cutter panel. No automatic retry or resume.\nFull report: SmartStudio/docs/print-and-cut-design-and-setup.md",12,58);guide.setEditable(false);StudioRibbon.showTask(owner,"Print & Cut setup guide",new JScrollPane(guide));
        }));
    }
    void open(){safe(()->{if(busy)throw new IllegalStateException("Wait for the active production operation.");if(production!=null&&production.isDisplayable()){production.toFront();return;}Publication captured=source.get();int page=selectedPage.getAsInt();CutJob.Settings media=settings.copy();background(()->new CutJob(captured,page,media),this::show);});}
    private interface Action {void run()throws Exception;}
    private void safe(Action action){try{action.run();}catch(Exception ex){error(ex);}}
    private void error(Throwable ex){JOptionPane.showMessageDialog(owner,ex.getMessage(),"Print & Cut",JOptionPane.ERROR_MESSAGE);}
    private static JSpinner number(double value,double min,double max,double step){JSpinner s=new JSpinner(new SpinnerNumberModel(value,min,max,step));s.setEditor(new JSpinner.NumberEditor(s,"0.###"));return s;}
    private static double value(JSpinner s){try{s.commitEdit();}catch(java.text.ParseException e){throw new IllegalArgumentException("Enter a valid number.");}return ((Number)s.getValue()).doubleValue();}
    private static JPanel fields(){JPanel p=DeckersSwing.panel();p.setLayout(new GridLayout(0,2,10,8));return p;}
    private static void field(JPanel p,String name,JComponent control){JLabel label=new JLabel(name);label.setLabelFor(control);control.getAccessibleContext().setAccessibleName(name);p.add(label);p.add(control);}
    private boolean accept(String title,JComponent panel){
        StudioVisuals.styleTree(panel);JScrollPane scroll=new JScrollPane(panel);
        Rectangle bounds=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();Dimension size=panel.getPreferredSize();
        scroll.setPreferredSize(new Dimension(Math.min(size.width+30,Math.max(300,bounds.width-100)),Math.min(size.height+12,Math.max(200,bounds.height-180))));
        return JOptionPane.showConfirmDialog(owner,scroll,title,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION;
    }
    private void media()throws Exception {
        if(busy)throw new IllegalStateException("Wait for the cutter operation.");
        JPanel form=fields();JComboBox<String> type=new JComboBox<>(new String[]{"Roll","Sheet"});type.setSelectedIndex(settings.roll?0:1);field(form,"Media type",type);
        JSpinner width=number(settings.width/25.4,.1,200,.1),length=number(settings.length/25.4,.1,200,.1),gap=number(settings.gap/25.4,0,10,.01),bleed=number(settings.bleed/25.4,0,10,.01),margin=number(settings.margin/25.4,0,10,.01),grip=number(settings.grip/25.4,0,10,.01),copies=number(settings.copies,1,10000,1),radius=number(settings.radius/25.4,0,10,.01);
        field(form,"Printed width (in)",width);field(form,"Sheet / fixed job length (in)",length);
        JCheckBox auto=new JCheckBox("Calculate roll job length",settings.autoLength);field(form,"Roll length",auto);field(form,"Copies of selected design page",copies);field(form,"Label spacing (in)",gap);field(form,"Bleed (in)",bleed);field(form,"Printer margins, all edges (in)",margin);field(form,"Roller grip allowance (in)",grip);
        JPanel edges=new JPanel(new GridLayout(2,2));EnumMap<CutJob.Edge,JCheckBox> checks=new EnumMap<>(CutJob.Edge.class);
        for(CutJob.Edge e:CutJob.Edge.values()){JCheckBox c=new JCheckBox(e.toString(),settings.gripEdges.contains(e));checks.put(e,c);edges.add(c);}field(form,"Grip edges on the printed sheet",edges);
        JCheckBox rotate=new JCheckBox("Rotate label artwork 90°",settings.rotateItems),loading=new JCheckBox("Rotate sheet 90° clockwise for cutter",settings.rotateLoading),weed=new JCheckBox("Add weeding border (2 mm clearance)",settings.weeding);
        field(form,"Label layout",rotate);field(form,"Cutter loading orientation",loading);field(form,"Vinyl waste",weed);
        JComboBox<CutJob.Contour> contour=new JComboBox<>(CutJob.Contour.values());contour.setSelectedItem(settings.contour);field(form,"Explicit cut contour",contour);field(form,"Rounded corner radius (in)",radius);
        JTextArea custom=new JTextArea(settings.custom,3,30);custom.setLineWrap(true);field(form,"Custom polygon: x,y mm; x,y mm",new JScrollPane(custom));
        if(!accept("Roll / sheet and contour setup — inches",form))return;
        CutJob.Settings next=settings.copy();next.roll=type.getSelectedIndex()==0;next.width=value(width)*25.4;next.length=value(length)*25.4;next.autoLength=auto.isSelected();next.copies=(int)value(copies);next.gap=value(gap)*25.4;next.bleed=value(bleed)*25.4;next.margin=value(margin)*25.4;next.grip=value(grip)*25.4;next.gripEdges.clear();checks.forEach((e,c)->{if(c.isSelected())next.gripEdges.add(e);});next.rotateItems=rotate.isSelected();next.rotateLoading=loading.isSelected();next.weeding=weed.isSelected();next.contour=(CutJob.Contour)contour.getSelectedItem();next.radius=value(radius)*25.4;next.custom=custom.getText();next.validate();settings=next;saveSetup();
    }
    private void device()throws Exception {
        if(busy)throw new IllegalStateException("Wait for the cutter operation.");
        JPanel form=fields();JCheckBox usb=new JCheckBox("Direct Windows USBPRINT (not COM)",profile.usb);field(form,"Connection type",usb);JTextField port=new JTextField(profile.port,18);field(form,"USB interface path / serial port",port);
        JButton ports=StudioRibbon.button("List connected interfaces",()->{
            background(()->{List<String> found=new ArrayList<>(CutterUsb.devices());found.addAll(Arrays.stream(com.fazecast.jSerialComm.SerialPort.getCommPorts()).map(p->p.getSystemPortName()+" — "+p.getDescriptivePortName()).toList());return found;},found->{JTextArea list=new JTextArea(String.join("\n",found),8,55);list.setEditable(false);JOptionPane.showMessageDialog(owner,new JScrollPane(list),"Detected interfaces — copy the verified cutter path",JOptionPane.INFORMATION_MESSAGE);});
        });field(form,"Local connected devices",ports);
        JSpinner baud=number(profile.baud,300,115200,1),units=number(profile.unitsPerMm,0,10000,.1),feed=number(profile.feedWidth/25.4,.1,200,.1),cut=number(profile.cutWidth/25.4,.1,200,.1),length=number(profile.feedLength/25.4,.1,3937,.1);
        field(form,"Baud — match cutter / installed driver",baud);field(form,"Verified HPGL units per mm (0 = unknown)",units);field(form,"Maximum material width (in)",feed);field(form,"Maximum cutting width (in)",cut);field(form,"Tested feed length (in)",length);
        JCheckBox swap=new JCheckBox("Swap HPGL X/Y",profile.swapAxes),flipX=new JCheckBox("Negative HPGL X",profile.xSign<0),flipY=new JCheckBox("Negative HPGL Y",profile.ySign<0),verified=new JCheckBox("Confirmed model, HPGL, 8N1, flow control, scale and axes",profile.protocolVerified);
        field(form,"Axes",swap);field(form,"Direction",flipX);field(form,"Direction",flipY);
        JComboBox<String> flow=new JComboBox<>(new String[]{"None","XON/XOFF","RTS/CTS"});flow.setSelectedIndex(profile.flow);field(form,"Verified flow control",flow);
        JTextField material=new JTextField(profile.material,24);JTextArea notes=new JTextArea(profile.notes,3,30);notes.setLineWrap(true);field(form,"Material preset name",material);field(form,"Tested speed / force / blade offset notes",new JScrollPane(notes));field(form,"Enable motion after equipment verification",verified);
        if(!accept("VEVOR B-870 — workstation setup",form))return;
        CutterDevice.Profile p=new CutterDevice.Profile();p.usb=usb.isSelected();p.port=port.getText().trim();p.baud=(int)value(baud);p.unitsPerMm=value(units);p.feedWidth=value(feed)*25.4;p.cutWidth=value(cut)*25.4;p.feedLength=value(length)*25.4;p.swapAxes=swap.isSelected();p.xSign=flipX.isSelected()?-1:1;p.ySign=flipY.isSelected()?-1:1;p.flow=flow.getSelectedIndex();p.protocolVerified=verified.isSelected();p.material=material.getText();p.notes=notes.getText();if(p.protocolVerified)p.requireVerified();profile=p;saveSetup();
    }
    private void calibrate()throws Exception {
        if(busy)throw new IllegalStateException("Wait for the cutter operation.");
        if(profile.unitsPerMm<=0)throw new IllegalArgumentException("First enter the documented starting units/mm in B-870 setup, then draw a pen calibration square.");
        JPanel form=fields();JTextField measuredX=new JTextField(12),measuredY=new JTextField(12);
        field(form,"Requested pen square",new JLabel("10 × 10 mm — measure the actual pen drawing"));
        field(form,"Current units/mm",new JLabel(String.format(Locale.ROOT,"%.6f",profile.unitsPerMm)));
        field(form,"Measured horizontal side (mm)",measuredX);field(form,"Measured vertical side (mm)",measuredY);
        if(!accept("Calibrate from the measured pen square",form))return;
        double corrected=CutCalibration.correctedUnits(profile.unitsPerMm,10,CutCalibration.measurement(measuredX.getText()),CutCalibration.measurement(measuredY.getText()));
        if(JOptionPane.showConfirmDialog(owner,String.format(Locale.ROOT,"Change units/mm from %.6f to %.6f?\nDraw and measure another pen square, then repeat the job's pen proof before blade cutting.",profile.unitsPerMm,corrected),"Apply measured scale",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        profile.unitsPerMm=corrected;saveSetup();status.setText("Measured scale saved. Repeat the pen square and production proof with this scale.");
    }
    private static final class SavedSetup {CutJob.Settings media;CutterDevice.Profile cutter;}
    private void saveSetup()throws Exception {SavedSetup saved=new SavedSetup();saved.media=settings;saved.cutter=profile;Files.createDirectories(setupFile.getParent());Path temp=Files.createTempFile(setupFile.getParent(),"setup-",".json");try{Files.writeString(temp,Publication.JSON.toJson(saved));Files.move(temp,setupFile,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    private Path choose(boolean save,String extension){JFileChooser f=new JFileChooser();f.setFileFilter(new FileNameExtensionFilter(extension.toUpperCase(Locale.ROOT),extension));int result=save?f.showSaveDialog(owner):f.showOpenDialog(owner);if(result!=JFileChooser.APPROVE_OPTION)return null;Path path=f.getSelectedFile().toPath();if(save&&!path.toString().toLowerCase(Locale.ROOT).endsWith("."+extension))path=path.resolveSibling(path.getFileName()+"."+extension);if(save&&Files.exists(path)&&JOptionPane.showConfirmDialog(owner,"Replace "+path.getFileName()+"?","Save",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;return path;}
    private void loadJob()throws Exception {if(busy)throw new IllegalStateException("Wait for the cutter operation.");Path file=choose(false,"sscut");if(file!=null){CutJob job=CutJob.load(file);if(job.vinyl()!=vinyl)throw new IllegalArgumentException("Open this job in the matching Labels or Vinyl Cut workspace.");if(production!=null)production.dispose();show(job);}}
    private <T> void background(java.util.concurrent.Callable<T> task,Consumer<T> done){
        if(busy){error(new IllegalStateException("Wait for the current production operation."));return;}busy=true;
        new SwingWorker<T,Void>(){protected T doInBackground()throws Exception{return task.call();}protected void done(){busy=false;try{done.accept(get());}catch(Exception ex){error(ex.getCause()==null?ex:ex.getCause());}}}.execute();
    }
    private void show(CutJob job){
        production=new JDialog(SwingUtilities.getWindowAncestor(owner),vinyl?"Vinyl Cut — frozen job":"Print & Cut — frozen job",Dialog.ModalityType.MODELESS);
        JDialog dialog=production;dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);dialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){if(!busy)dialog.dispose();}});
        JPanel root=new JPanel(new BorderLayout(8,8));root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JLabel summary=new JLabel(String.format(Locale.ROOT,"%d copies • Print %.3f × %.3f in • Load %.3f in across × %.3f in feed",job.copies(),job.width()/25.4,job.height()/25.4,job.loadedWidth()/25.4,job.loadedLength()/25.4));root.add(summary,BorderLayout.NORTH);
        JTabbedPane previews=new JTabbedPane();previews.addTab("Print orientation",new Preview(job,false));previews.addTab("Cutter loading / feed ↓",new Preview(job,true));root.add(previews);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,6,6));JPanel bottom=new JPanel(new BorderLayout());bottom.add(actions);status.setText("Frozen job "+job.id.substring(0,8)+" • Purple = cut/grip preview only. Edits require a new job and print.");bottom.add(status,BorderLayout.SOUTH);root.add(bottom,BorderLayout.SOUTH);
        final boolean[] printed={job.vinyl()},penSent={false},penVerified={false};final String[] penProfile={""};final AffineTransform[] alignment={job.loading()};final boolean[] registered={job.vinyl()};
        actions.add(StudioRibbon.button("Save job",()->safe(()->{Path file=choose(true,"sscut");if(file!=null)background(()->{job.save(file);return file;},saved->status.setText("Saved frozen job "+saved.getFileName()+". Reopened jobs require fresh alignment."));})));
        if(!job.vinyl()){
            actions.add(StudioRibbon.button("Print",()->safe(()->{
                if(busy)throw new IllegalStateException("Wait for the production operation.");
                PrinterJob printer=PrinterJob.getPrinterJob();printer.setJobName("SmartStudio labels "+job.id.substring(0,8));PageFormat desired=CutJobOutput.format(job);PageFormat accepted=printer.pageDialog(desired);CutJobOutput.requireFormat(job,accepted);printer.setPrintable(CutJobOutput.printable(job),accepted);
                if(!printer.printDialog())return;CutJobOutput.requireFormat(job,printer.validatePage(accepted));
                background(()->{printer.print();return true;},ok->{printed[0]=true;status.setText("Print submitted. Measure scale, load sheet, then align targets.");});
            })));
            actions.add(StudioRibbon.button("Export PDF",()->safe(()->{Path file=choose(true,"pdf");if(file!=null)background(()->{CutJobOutput.pdf(job,file);return file;},saved->status.setText("Saved "+saved.getFileName()+". Print at 100%; confirm the physical print before cutting."));})));
            actions.add(StudioRibbon.button("Confirm physical print",()->{if(JOptionPane.showConfirmDialog(dialog,"Is this frozen job printed at 100%, with correct dimensions and all three targets visible?","Confirm printed material",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)printed[0]=true;}));
            actions.add(StudioRibbon.button("Align 3 targets",()->safe(()->{
                if(busy)throw new IllegalStateException("Wait for the cutter operation.");if(!printed[0])throw new IllegalArgumentException("Print this job before alignment.");
                JPanel fields=fields();List<JTextField> xs=new ArrayList<>(),ys=new ArrayList<>();List<Point2D.Double> marks=job.marks();
                for(int i=0;i<3;i++){JTextField x=new JTextField(12),y=new JTextField(12);xs.add(x);ys.add(y);field(fields,"Target "+(i+1)+" measured cutter X (mm)",x);field(fields,"Target "+(i+1)+" measured cutter Y (mm)",y);}
                field(fields,"Keep one common panel origin",new JLabel("Jog to each printed cross; do not reset origin between marks."));
                if(!accept("Manual registration — cutter coordinates in mm",fields))return;
                List<Point2D.Double> measured=new ArrayList<>();for(int i=0;i<3;i++)measured.add(new Point2D.Double(CutCalibration.measurement(xs.get(i).getText()),CutCalibration.measurement(ys.get(i).getText())));
                AffineTransform transform=CutJob.register(marks,measured);job.requireFits(profile.feedWidth,profile.cutWidth,profile.feedLength,transform);alignment[0]=transform;registered[0]=true;penSent[0]=penVerified[0]=false;status.setText("Three targets aligned. Send and inspect a pen proof before using the blade.");
            })));
        }
        actions.add(StudioRibbon.button("Cutter / material",()->safe(()->{device();registered[0]=job.vinyl();alignment[0]=job.loading();penSent[0]=penVerified[0]=false;})));
        actions.add(StudioRibbon.button("Connection test",()->background(()->{try(var ignored=CutterDevice.connection(profile).open()){return true;}},ok->status.setText("Cutter connection opened; no motion sent. This does not verify cutter response."))));
        actions.add(StudioRibbon.button("Jog / test square",()->safe(()->jog(job,()->{registered[0]=job.vinyl();penSent[0]=penVerified[0]=false;}))));
        actions.add(StudioRibbon.button("Send pen proof",()->safe(()->{
            requireReady(job,registered[0],alignment[0]);
            if(JOptionPane.showConfirmDialog(dialog,"Install the pen, load the shown sheet orientation and set the same verified panel origin. Send the frozen cut paths with the pen?","Pen proof",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            var commands=CutterDevice.commands(job.cuts(),alignment[0],profile);penSent[0]=penVerified[0]=false;String testedProfile=Publication.JSON.toJson(profile);
            background(()->{CutterDevice.send(CutterDevice.connection(profile),commands);return true;},ok->{penProfile[0]=testedProfile;penSent[0]=true;status.setText("Pen commands sent. Wait for motion to finish and inspect alignment before confirming.");});
        })));
        actions.add(StudioRibbon.button("Confirm pen proof",()->safe(()->{if(busy||!penSent[0])throw new IllegalStateException("Send the pen proof and wait for it to finish first.");if(JOptionPane.showConfirmDialog(dialog,"Has the pen proof finished and aligned with every contour, including the last row, without slipping?","Inspect pen proof",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)penVerified[0]=true;})));
        actions.add(StudioRibbon.button("Send blade cut",()->safe(()->{
            requireReady(job,registered[0],alignment[0]);if(!printed[0]||!penVerified[0]||!penProfile[0].equals(Publication.JSON.toJson(profile)))throw new IllegalStateException("Print, align and verify a pen proof with the current cutter settings before blade cutting.");
            if(JOptionPane.showConfirmDialog(dialog,"Install the tested blade/force for "+profile.material+". Preserve the material position and common origin. Send this job once?","Blade cut",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            var commands=CutterDevice.commands(job.cuts(),alignment[0],profile);penVerified[0]=penSent[0]=false;
            background(()->{CutterDevice.send(CutterDevice.connection(profile),commands);return true;},ok->status.setText("Commands sent; cutter completion is not reported by HPGL. Inspect cuts at the machine."));
        })));
        // Wrapping buttons remain reachable at smaller display budgets.
        actions.setLayout(new GridLayout(0,3,6,6));StudioVisuals.styleTree(root);dialog.setContentPane(root);dialog.setSize(1000,740);Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();dialog.setSize(Math.min(1000,screen.width),Math.min(740,screen.height));dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }
    private void requireReady(CutJob job,boolean registered,AffineTransform alignment){if(busy)throw new IllegalStateException("Wait for the production operation.");profile.requireVerified();if(!registered)throw new IllegalStateException("Align the three printed registration targets first.");job.requireFits(profile.feedWidth,profile.cutWidth,profile.feedLength,alignment);}
    private void jog(CutJob job,Runnable invalidate){
        profile.requireVerified();JPanel form=fields();JSpinner x=number(10,0,profile.cutWidth,1),y=number(10,0,profile.feedLength,1);field(form,"Absolute X from panel origin (mm)",x);field(form,"Absolute feed Y from panel origin (mm)",y);JCheckBox square=new JCheckBox("Draw a 10 mm square with the pen");field(form,"Pen calibration",square);JCheckBox origin=new JCheckBox("Origin set on cutter panel; pen installed");field(form,"Common origin",origin);
        if(!accept("Bounded pen-up jog / calibration square",form))return;if(!origin.isSelected())throw new IllegalArgumentException("Set the common origin on the cutter panel and install the pen first.");
        double px=value(x),py=value(y);Path2D move=new Path2D.Double();move.moveTo(px,py);if(square.isSelected()){move.lineTo(px+10,py);move.lineTo(px+10,py+10);move.lineTo(px,py+10);move.closePath();}
        var commands=CutterDevice.commands(List.of(move),new AffineTransform(),profile);invalidate.run();background(()->{CutterDevice.send(CutterDevice.connection(profile),commands);return true;},ok->status.setText(square.isSelected()?"Measure the 10 mm square; correct units/mm before production.":"Pen-up move sent. Re-align targets before production cutting."));
    }
    static final class Preview extends JPanel {
        private final CutJob job;private final boolean loaded;
        Preview(CutJob job,boolean loaded){this.job=job;this.loaded=loaded;setBackground(StudioVisuals.BACKGROUND);getAccessibleContext().setAccessibleName(loaded?"Cutter loading diagram":"Combined print and cut preview");}
        @Override protected void paintComponent(Graphics original){super.paintComponent(original);Graphics2D g=(Graphics2D)original.create();try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);double w=loaded?job.loadedWidth():job.width(),h=loaded?job.loadedLength():job.height();double scale=Math.max(.01,Math.min((getWidth()-64)/w,(getHeight()-70)/h));g.translate(32,40);g.scale(scale,scale);if(loaded)g.transform(job.loading());job.paint(g,true);g.setColor(StudioVisuals.INK);g.setStroke(new BasicStroke((float)(1/scale)));g.draw(new Rectangle2D.Double(0,0,job.width(),job.height()));
            g.setTransform(original instanceof Graphics2D gg?gg.getTransform():new AffineTransform());g.setColor(StudioVisuals.INK);g.setFont(StudioVisuals.font(Font.PLAIN,13));g.drawString(loaded?"Origin: top left • Feed ↓ • Grip edges: "+job.settings().gripEdges.stream().map(job::loadedEdge).toList():"Purple shading: roller grip • Purple outline: cut path • Black crosses: registration",12,20);
            if(!loaded){List<Point2D.Double> marks=job.marks();for(int i=0;i<marks.size();i++)g.drawString(""+(i+1),(int)(32+marks.get(i).x*scale+4),(int)(40+marks.get(i).y*scale-4));}
        }finally{g.dispose();}}
    }
}
