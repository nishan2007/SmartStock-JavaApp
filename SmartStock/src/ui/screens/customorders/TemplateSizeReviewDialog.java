package ui.screens.customorders;

import com.google.gson.JsonObject;
import services.TemplateSizeReview;
import services.TemplateSizeReview.Entry;
import utils.ImageCacheManager;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** One-at-a-time printable-area setup; skipped entries remain incomplete. */
final class TemplateSizeReviewDialog extends JDialog {
    private final BiConsumer<String,JsonObject> writer;
    private final DefaultListModel<Entry> model=new DefaultListModel<>();
    private final JList<Entry> items=new JList<>(model);
    private final JLabel photo=new JLabel("No photo",SwingConstants.CENTER),details=new JLabel(),status=new JLabel();
    private final JTextField width=new JTextField(8),height=new JTextField(8);
    private final JComboBox<String> unit=new JComboBox<>(new String[]{"Choose unit","IN","CM","MM"});
    private final JCheckBox sublimation=new JCheckBox("Sublimation only",true);
    private final JButton save=new JButton("Save & Next"),skip=new JButton("Skip for now");
    private final List<Entry> all;
    private int saved,skipped;
    private boolean busy;
    private long photoRevision;
    private final java.util.Set<Entry> deferred=new java.util.HashSet<>();
    TemplateSizeReviewDialog(Window owner,JsonObject state,BiConsumer<String,JsonObject> writer,Runnable refresh){
        super(owner,"Review missing template sizes",ModalityType.APPLICATION_MODAL);
        this.writer=writer;all=new ArrayList<>(TemplateSizeReview.pending(state,false));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){if(!busy)dispose();}});
        setLayout(new BorderLayout(12,12));
        JPanel heading=new JPanel(new GridLayout(0,1,0,6));heading.setBorder(BorderFactory.createEmptyBorder(12,12,0,12));
        heading.add(new JLabel("Enter the actual printable area, not the item's overall size or capacity."));heading.add(sublimation);add(heading,BorderLayout.NORTH);
        items.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);items.setFixedCellHeight(30);
        JScrollPane queue=new JScrollPane(items);queue.setPreferredSize(new Dimension(300,430));
        JPanel preview=new JPanel(new BorderLayout(8,8));preview.add(details,BorderLayout.NORTH);photo.setPreferredSize(new Dimension(330,290));preview.add(photo);
        JPanel fields=new JPanel(new GridLayout(0,2,10,8));fields.add(new JLabel("Printable width"));fields.add(width);fields.add(new JLabel("Printable height"));fields.add(height);fields.add(new JLabel("Units"));fields.add(unit);preview.add(fields,BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,queue,preview);split.setResizeWeight(.4);add(split);
        JPanel footer=new JPanel(new BorderLayout());footer.setBorder(BorderFactory.createEmptyBorder(0,12,12,12));footer.add(status);JPanel actions=new JPanel();JButton close=new JButton("Close");actions.add(skip);actions.add(save);actions.add(close);footer.add(actions,BorderLayout.SOUTH);add(footer,BorderLayout.SOUTH);
        items.addListSelectionListener(e->{if(!e.getValueIsAdjusting())showEntry();});sublimation.addActionListener(e->reload());
        close.addActionListener(e->{if(!busy)dispose();});skip.addActionListener(e->{Entry entry=items.getSelectedValue();if(entry!=null){deferred.add(entry);model.removeElement(entry);skipped++;advance();}});
        save.addActionListener(e->save());reload();setSize(850,650);setMinimumSize(new Dimension(650,500));setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){if(saved>0)refresh.run();}});
    }
    private void reload(){model.clear();for(Entry entry:all)if(!deferred.contains(entry)&&(!sublimation.isSelected()||entry.sublimation()))model.addElement(entry);advance();}
    private void advance(){if(!model.isEmpty())items.setSelectedIndex(0);else showEntry();status.setText(model.size()+" remaining · "+saved+" saved · "+skipped+" skipped");}
    private void showEntry(){
        Entry entry=items.getSelectedValue();save.setEnabled(entry!=null&&!busy);skip.setEnabled(entry!=null&&!busy);
        width.setText(entry==null||entry.width()==null?"":entry.width().toPlainString());height.setText(entry==null||entry.height()==null?"":entry.height().toPlainString());
        unit.setSelectedItem(entry!=null&&entry.unit()!=null&&List.of("IN","CM","MM").contains(entry.unit())?entry.unit():"Choose unit");
        details.setText(entry==null?"No more items in this review.":"<html>"+escape(entry.parent())+"<br>"+escape(entry.name())+"<br>Type: "+escape(entry.type())+" · Item #"+entry.itemId()+(entry.variantId()==null?"":" · Variant #"+entry.variantId())+"<br>Overall size: "+escape(entry.size())+"</html>");
        long revision=++photoRevision;photo.setIcon(null);photo.setText(entry==null||entry.photo().isBlank()?"No photo":"Loading photo…");
        if(entry!=null&&!entry.photo().isBlank())UiTaskRunner.submit(this,"template-review.photo",()->{
            Image image=ImageCacheManager.loadImage(entry.photo());if(image==null)return null;
            int w=image.getWidth(null),h=image.getHeight(null);if(w<=0||h<=0)return null;
            double scale=Math.min(330.0/w,290.0/h);return new ImageIcon(image.getScaledInstance(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),Image.SCALE_SMOOTH));
        },icon->{if(revision==photoRevision){photo.setIcon(icon);photo.setText(icon==null?"Photo unavailable":"");}},error->{if(revision==photoRevision)photo.setText("Photo unavailable");});
    }
    private void save(){
        Entry entry=items.getSelectedValue();if(entry==null||busy)return;
        BigDecimal w,h;String u=(String)unit.getSelectedItem();
        try{w=new BigDecimal(width.getText().trim());h=new BigDecimal(height.getText().trim());if(!TemplateSizeReview.complete(w,h,u)||w.scale()>3||h.scale()>3||w.doubleValue()>10000||h.doubleValue()>10000)throw new IllegalArgumentException();}
        catch(Exception e){status.setText("Enter positive width and height (up to 3 decimals) and choose units.");return;}
        JsonObject request=TemplateSizeReview.request(entry,w,h,u);busy=true;items.setEnabled(false);sublimation.setEnabled(false);save.setEnabled(false);skip.setEnabled(false);width.setEnabled(false);height.setEnabled(false);unit.setEnabled(false);status.setText("Saving template size…");
        UiTaskRunner.submit(this,"template-review.save",()->{writer.accept("SAVE_TEMPLATE_SIZE",request);return true;},ignored->{setIdle();all.remove(entry);model.removeElement(entry);saved++;advance();},failure->{setIdle();status.setText("Not saved: "+failure.getMessage());});
    }
    private void setIdle(){busy=false;items.setEnabled(true);sublimation.setEnabled(true);save.setEnabled(true);skip.setEnabled(true);width.setEnabled(true);height.setEnabled(true);unit.setEnabled(true);}
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
