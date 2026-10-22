package gy.deckers.studio;

import publishing.*;
import ui.design.DeckersSwing;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;

/** Non-destructive print job; no template or shared-library data is rewritten. */
final class RisoSeparationDialog extends JDialog {
    private final Publication publication;
    private final PublicationRenderer renderer;
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Use","Ink name","Colour"},0){
        public Class<?> getColumnClass(int c){return c==0?Boolean.class:c==2?Color.class:String.class;}
        public boolean isCellEditable(int r,int c){return c!=2;}
    };
    private final JTable table=new JTable(model);
    private final JSpinner inkCount=new JSpinner(new SpinnerNumberModel(4,1,8,1));
    private final JSpinner sheet;
    private final JComboBox<String> view=new JComboBox<>();
    private final JCheckBox marks=new JCheckBox("Registration marks (5 mm from paper edges)");
    private final JLabel image=new JLabel("Detect inks to begin",SwingConstants.CENTER);
    private final JLabel status=new JLabel("Print each ink sheet on your normal printer, then scan it on the Riso. Use actual size / 100%.");
    private final List<JButton> buttons=new ArrayList<>();
    private final Map<String,Integer> imageAssignments=new LinkedHashMap<>();
    private boolean busy;
    private boolean updating;

    static void open(Component parent,Publication p){new RisoSeparationDialog(parent,p).setVisible(true);}
    private RisoSeparationDialog(Component parent,Publication p){
        super(SwingUtilities.getWindowAncestor(parent),"Risograph • colour separations",ModalityType.APPLICATION_MODAL);
        publication=p.copy();publication.print.duplex=false;
        PublicationPreflight.requireMergeData(publication);
        renderer=new PublicationRenderer(publication);
        if(renderer.sheets()>1000)throw new IllegalArgumentException("Choose at most 1000 sheets.");
        sheet=new JSpinner(new SpinnerNumberModel(1,1,Math.max(1,renderer.sheets()),1));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setLayout(new BorderLayout(8,8));
        JPanel tools=DeckersSwing.panel();tools.add(new JLabel("Suggest inks:"));tools.add(inkCount);
        button(tools,"Detect inks",this::detect);button(tools,"Add ink",()->{if(model.getRowCount()<8){model.addRow(new Object[]{true,"New ink",Color.BLACK});refreshViews();}});
        button(tools,"Remove ink",()->{int row=table.getSelectedRow();if(row>=0){if(imageAssignments.containsValue(row))throw new IllegalArgumentException("Pictures are assigned to this ink. Change their Picture inks settings first.");imageAssignments.replaceAll((key,value)->value>row?value-1:value);model.removeRow(row);refreshViews();}});
        button(tools,"Change colour",()->{int row=table.getSelectedRow();if(row>=0){Color c=JColorChooser.showDialog(this,"Ink colour",(Color)model.getValueAt(row,2));if(c!=null)model.setValueAt(c,row,2);}});
        button(tools,"Picture inks…",this::assignPictures);
        add(tools,BorderLayout.NORTH);
        table.setDefaultRenderer(Color.class,(t,value,selected,focus,row,col)->{JLabel swatch=new JLabel(String.format("#%06X",((Color)value).getRGB()&0xffffff),SwingConstants.CENTER);swatch.setOpaque(true);swatch.setBackground((Color)value);swatch.setForeground(((Color)value).getRed()+((Color)value).getGreen()+((Color)value).getBlue()>380?Color.BLACK:Color.WHITE);return swatch;});
        table.setRowHeight(27);
        JPanel left=DeckersSwing.panel();left.setLayout(new BorderLayout());left.add(new JScrollPane(table),BorderLayout.CENTER);
        JTextArea help=new JTextArea("Select a row to change its ink colour.\nRename or disable inks; remove shades to merge them into the remaining inks.\n\nWhite stays unprinted. Photos use the closest ink and grayscale coverage. This is a simplified preview, not a calibrated Riso proof.\n\nThe Riso drum supplies the colour. These paper originals are grayscale; no screen-print halftone or white base is added.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setOpaque(false);help.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));left.add(help,BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(310,480));
        JPanel right=DeckersSwing.panel();right.setLayout(new BorderLayout());JPanel selection=DeckersSwing.panel();selection.add(new JLabel("Sheet:"));selection.add(sheet);selection.add(view);button(selection,"Update preview",this::preview);JPanel previewTools=DeckersSwing.panel();previewTools.setLayout(new GridLayout(0,1));previewTools.add(selection);previewTools.add(marks);right.add(previewTools,BorderLayout.NORTH);right.add(new JScrollPane(image),BorderLayout.CENTER);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,right);split.setResizeWeight(.3);add(split,BorderLayout.CENTER);
        JPanel bottom=DeckersSwing.panel();bottom.setLayout(new BorderLayout());bottom.add(status,BorderLayout.NORTH);JPanel actions=DeckersSwing.panel();button(actions,"Export enabled inks PDF",this::export);button(actions,"Print selected ink…",this::print);JButton close=new JButton("Close");close.addActionListener(e->dispose());actions.add(close);bottom.add(actions,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        model.addTableModelListener(e->{if(!updating)refreshViews();});
        view.addActionListener(e->{if(!updating&&!busy&&model.getRowCount()>0)try{preview();}catch(Exception ex){failure(ex);}});
        setSize(1100,760);setLocationRelativeTo(parent);SwingUtilities.invokeLater(this::detect);
    }
    private void button(JPanel panel,String name,Runnable action){JButton b=StudioWorkspaceShell.button(name,()->{try{if(!busy)action.run();}catch(Exception ex){failure(ex);}});buttons.add(b);panel.add(b);}
    private List<RisoSeparation.Ink> inks(){
        if(table.isEditing())table.getCellEditor().stopCellEditing();
        List<RisoSeparation.Ink> result=new ArrayList<>();for(int i=0;i<model.getRowCount();i++)result.add(new RisoSeparation.Ink(String.valueOf(model.getValueAt(i,1)),(Color)model.getValueAt(i,2),Boolean.TRUE.equals(model.getValueAt(i,0))));
        if(result.stream().noneMatch(RisoSeparation.Ink::enabled))throw new IllegalArgumentException("Enable at least one ink.");return List.copyOf(result);
    }
    private void refreshViews(){updating=true;int selected=view.getSelectedIndex();view.removeAllItems();view.addItem("Combined preview");for(int i=0;i<model.getRowCount();i++)view.addItem((i+1)+" • "+model.getValueAt(i,1));view.setSelectedIndex(Math.max(0,Math.min(selected,view.getItemCount()-1)));updating=false;image.setIcon(null);image.setText("Click Update preview after changing inks.");}
    private void assignPictures(){var inks=inks();if(RisoImageAssignments.targets(publication).isEmpty())throw new IllegalArgumentException("This print job has no picture objects to assign.");var panel=new RisoImageAssignmentsPanel(publication,inks,imageAssignments);while(JOptionPane.showConfirmDialog(this,panel,"Picture ink assignments",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){try{var result=panel.assignments();RisoImageAssignments.resolve(publication,inks,result);imageAssignments.clear();imageAssignments.putAll(result);preview();return;}catch(IllegalArgumentException ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Picture inks",JOptionPane.WARNING_MESSAGE);}}}
    private void detect(){if(!imageAssignments.isEmpty()&&JOptionPane.showConfirmDialog(this,"Detecting a new palette resets picture ink assignments. Continue?","Detect inks",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;int count=(Integer)inkCount.getValue();work(()->{Iterable<BufferedImage> samples=()->java.util.stream.IntStream.range(0,renderer.sheets()).mapToObj(i->renderer.raster(i,25)).iterator();return RisoSeparation.detect(samples,count);},inks->{imageAssignments.clear();updating=true;model.setRowCount(0);for(var ink:inks)model.addRow(new Object[]{ink.enabled(),ink.name(),ink.color()});updating=false;refreshViews();preview();});}
    private BufferedImage plate(int index,int dpi,List<RisoSeparation.Ink> inks,int ink,boolean registration,Map<String,Integer> assignments){return RisoOutput.plate(publication,index,dpi,inks,ink,registration,assignments);}
    private Map<String,Integer> assignments(List<RisoSeparation.Ink> inks){var result=Map.copyOf(imageAssignments);RisoImageAssignments.resolve(publication,inks,result);return result;}
    private void preview(){var inks=inks();var assigned=assignments(inks);int index=(Integer)sheet.getValue()-1,ink=view.getSelectedIndex()-1;boolean registration=marks.isSelected();work(()->plate(index,65,inks,ink,registration,assigned),result->{image.setText(null);image.setIcon(new ImageIcon(result));});}
    private void export(){var inks=inks();var assigned=assignments(inks);boolean registration=marks.isSelected();JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("Risograph separations.pdf"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path target=chooser.getSelectedFile().toPath();if(!target.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))target=Path.of(target+".pdf");if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"Replace "+target.getFileName()+"?","Export",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;Path destination=target;
        work(()->{return RisoOutput.pdf(publication,inks,registration,destination,assigned);},saved->status.setText("Saved "+saved.getFileName()+" • Ink order follows the table; each ink includes all chosen sheets. Print at 100%."));
    }
    private void print(){var inks=inks();var assigned=assignments(inks);int ink=view.getSelectedIndex()-1;if(ink<0||!inks.get(ink).enabled())throw new IllegalArgumentException("Choose an enabled ink in the preview list first.");boolean registration=marks.isSelected();
        try{PrinterJob job=PrinterJob.getPrinterJob();job.setJobName(publication.title+" • "+inks.get(ink).name());PageFormat format=job.defaultPage();Paper paper=new Paper();paper.setSize(publication.print.width*72/25.4,publication.print.height*72/25.4);paper.setImageableArea(0,0,paper.getWidth(),paper.getHeight());format.setPaper(paper);job.setPrintable((g,p,i)->Printable.NO_SUCH_PAGE,format);
            PrintRequestAttributeSet settings=new HashPrintRequestAttributeSet();settings.add(Sides.ONE_SIDED);settings.add(Chromaticity.MONOCHROME);settings.add(PublicationPrinting.requestedArea(publication,job.getPrintService(),settings));if(!job.printDialog(settings))return;settings.add(Sides.ONE_SIDED);settings.remove(MediaPrintableArea.class);settings.add(PublicationPrinting.requestedArea(publication,job.getPrintService(),settings));PageFormat actual=job.getPageFormat(settings);PublicationPrinting.requireFits(publication,actual);
            if(registration&&(actual.getImageableX()>3*72/25.4||actual.getImageableY()>3*72/25.4||actual.getImageableX()+actual.getImageableWidth()<(publication.print.width-3)*72/25.4||actual.getImageableY()+actual.getImageableHeight()<(publication.print.height-3)*72/25.4))throw new PrinterException("This printer would clip registration marks. Disable marks or use a larger printable area.");
            job.setPrintable(new Printable(){int cached=-1;BufferedImage raster;public int print(Graphics graphics,PageFormat pf,int index)throws PrinterException{if(index<0||index>=renderer.sheets())return NO_SUCH_PAGE;try{if(cached!=index){raster=plate(index,300,inks,ink,registration,assigned);cached=index;}Graphics2D g=(Graphics2D)graphics.create();try{g.drawImage(raster,java.awt.geom.AffineTransform.getScaleInstance(pf.getWidth()/raster.getWidth(),pf.getHeight()/raster.getHeight()),null);}finally{g.dispose();}return PAGE_EXISTS;}catch(RuntimeException ex){throw new PrinterException(ex.getMessage());}}},actual);
            work(()->{job.print(settings);return true;},done->status.setText("Sent "+inks.get(ink).name()+" • Check size and alignment on paper before making Riso masters."));
        }catch(Exception ex){failure(ex);}
    }
    private <T> void work(java.util.concurrent.Callable<T> task,java.util.function.Consumer<T> done){busy=true;buttons.forEach(b->b.setEnabled(false));table.setEnabled(false);status.setText("Preparing separations…");new SwingWorker<T,Void>(){protected T doInBackground()throws Exception{return task.call();}protected void done(){busy=false;buttons.forEach(b->b.setEnabled(true));table.setEnabled(true);if(!isDisplayable())return;try{T result=get();status.setText("Ready • Preview every ink before printing. Single-sided, actual size / 100%.");done.accept(result);}catch(Exception ex){failure(ex.getCause()==null?ex:ex.getCause());}}}.execute();}
    private void failure(Throwable ex){status.setText("Could not prepare separations.");JOptionPane.showMessageDialog(this,ex.getMessage(),"Risograph separations",JOptionPane.ERROR_MESSAGE);}
}
