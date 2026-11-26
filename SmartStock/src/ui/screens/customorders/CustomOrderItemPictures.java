package ui.screens.customorders;

import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.VariantOption;
import ui.design.DeckersPalette;
import utils.ImageCacheManager;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/** Read-only catalogue pictures for the item currently being entered. */
final class CustomOrderItemPictures extends JPanel {
    record Picture(String url,String caption) {}
    private final JPanel images=new JPanel(new GridLayout(0,3,8,8));
    private List<Picture> current=List.of();
    private long generation;
    private SwingWorker<Void,Void> worker;
    CustomOrderItemPictures(){
        super(new BorderLayout(6,6));setOpaque(false);images.setOpaque(false);
        var title=new JLabel("Item pictures");title.setForeground(DeckersPalette.muted());add(title,BorderLayout.NORTH);
        var scroll=new JScrollPane(images);scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(DeckersPalette.surface());scroll.setPreferredSize(new Dimension(330,190));
        scroll.setMinimumSize(new Dimension(120,110));add(scroll,BorderLayout.CENTER);
        showPictures(null,null,List.of(),false);
    }
    static List<Picture> pictures(CustomItemOption item,VariantOption selected,List<CustomOrderSizeSetEditor.SizeQuantity> sizes){
        if(item==null)return List.of();
        if(!sizes.isEmpty())return sizes.stream().map(s->picture(item,s.variant(),s.quantity())).toList();
        return List.of(picture(item,selected,0));
    }
    private static Picture picture(CustomItemOption item,VariantOption variant,int quantity){
        String url=variant!=null&&variant.imageUrl()!=null&&!variant.imageUrl().isBlank()?variant.imageUrl():item.imageUrl();
        String size=variant==null?item.size():variant.effectiveSize();
        String color=variant==null?item.color():variant.effectiveColor();
        String caption=String.join(" / ",java.util.stream.Stream.of(size,color).filter(s->s!=null&&!s.isBlank()).toList());
        if(caption.isBlank())caption=item.name();
        if(quantity>0)caption+=" × "+quantity;
        return new Picture(url,caption);
    }
    void showPictures(CustomItemOption item,VariantOption selected,List<CustomOrderSizeSetEditor.SizeQuantity> sizes,boolean supplied){
        List<Picture> next=pictures(item,selected,sizes);
        if(!next.isEmpty()&&next.equals(current)&&images.getComponentCount()>0)return;
        current=next;long expected=++generation;if(worker!=null)worker.cancel(true);images.removeAll();
        images.setLayout(new GridLayout(0,next.size()<2?1:Math.min(3,next.size()),8,8));
        if(next.isEmpty()){
            var empty=new JLabel(supplied?"Customer-supplied item — no catalogue picture":"Select an item to see its picture.",SwingConstants.CENTER);
            empty.setForeground(DeckersPalette.muted());images.add(empty);revalidate();repaint();return;
        }
        List<JLabel> labels=new ArrayList<>();
        for(var picture:next){
            JPanel cell=new JPanel(new BorderLayout(4,4));cell.setOpaque(false);
            JLabel image=new JLabel(picture.url()==null||picture.url().isBlank()?"No picture":"Loading…",SwingConstants.CENTER);
            image.setForeground(DeckersPalette.muted());image.setPreferredSize(new Dimension(next.size()==1?160:100,next.size()==1?156:118));labels.add(image);cell.add(image);
            JLabel caption=new JLabel(picture.caption(),SwingConstants.CENTER);caption.setForeground(DeckersPalette.text());
            caption.setToolTipText(picture.caption());cell.add(caption,BorderLayout.SOUTH);images.add(cell);
        }
        revalidate();repaint();
        worker=new SwingWorker<>(){
            protected Void doInBackground(){
                for(int i=0;i<next.size()&&!isCancelled();i++){
                    var picture=next.get(i);var raw=ImageCacheManager.loadImage(picture.url());
                    ImageIcon icon=null;
                    if(raw!=null&&raw.getWidth()>0&&raw.getHeight()>0){
                        int edge=next.size()==1?150:110;double scale=Math.min((double)edge/raw.getWidth(),(double)edge/raw.getHeight());
                        icon=new ImageIcon(raw.getScaledInstance(Math.max(1,(int)(raw.getWidth()*scale)),Math.max(1,(int)(raw.getHeight()*scale)),Image.SCALE_SMOOTH));
                    }
                    final ImageIcon loaded=icon;final JLabel label=labels.get(i);
                    SwingUtilities.invokeLater(()->{if(generation!=expected)return;label.setIcon(loaded);label.setText(loaded==null?"No picture":"");});
                }
                return null;
            }
        };worker.execute();
    }
    @Override public void removeNotify(){generation++;if(worker!=null)worker.cancel(true);current=List.of();super.removeNotify();}
}
