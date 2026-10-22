package ui.helpers;

import ui.design.DeckersPalette;
import utils.ImageCacheManager;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Bounded variant preview; remote images load off the Swing event thread. */
final class VariantPhotoGrid extends JPanel {
    private long generation;
    private SwingWorker<List<ImageIcon>,Void> worker;
    void setPictures(List<TableImageHoverPreview.Picture> pictures) {
        long request=++generation;
        if(worker!=null)worker.cancel(true);
        removeAll();setOpaque(false);
        int count=Math.min(9,pictures.size());
        setLayout(new GridLayout(0,Math.max(1,Math.min(3,count)),6,6));
        List<JLabel> previews=new ArrayList<>();
        for(var picture:pictures.subList(0,count)){
            JPanel cell=new JPanel(new BorderLayout(0,3));cell.setOpaque(false);
            JLabel preview=new JLabel("Loading…",SwingConstants.CENTER);
            preview.setPreferredSize(new Dimension(80,80));preview.setForeground(DeckersPalette.muted());
            JLabel caption=new JLabel(picture.label(),SwingConstants.CENTER);caption.setForeground(DeckersPalette.muted());
            caption.setPreferredSize(new Dimension(80,20));caption.setToolTipText(picture.label());
            cell.add(preview);cell.add(caption,BorderLayout.SOUTH);add(cell);previews.add(preview);
        }
        setToolTipText(pictures.size()>9?"Showing 9 of "+pictures.size()+" variant photos":null);
        revalidate();repaint();
        if(count==0)return;
        worker=new SwingWorker<>(){
            protected List<ImageIcon> doInBackground(){
                List<ImageIcon> icons=new ArrayList<>();
                for(var picture:pictures.subList(0,count)){
                    if(isCancelled())break;
                    ImageIcon icon=null;
                    try{
                        Image raw=ImageCacheManager.loadImage(picture.url());
                        if(raw!=null&&raw.getWidth(null)>0&&raw.getHeight(null)>0){
                            double scale=Math.min(80.0/raw.getWidth(null),80.0/raw.getHeight(null));
                            icon=new ImageIcon(raw.getScaledInstance(Math.max(1,(int)(raw.getWidth(null)*scale)),Math.max(1,(int)(raw.getHeight(null)*scale)),Image.SCALE_SMOOTH));
                        }
                    }catch(Exception ignored){ }
                    icons.add(icon);
                }
                return icons;
            }
            protected void done(){
                if(isCancelled()||request!=generation)return;
                try{var icons=get();for(int i=0;i<icons.size();i++){previews.get(i).setIcon(icons.get(i));previews.get(i).setText(icons.get(i)==null?"Unavailable":"");}}
                catch(Exception ignored){ }
            }
        };worker.execute();
    }
    @Override public void removeNotify(){generation++;if(worker!=null)worker.cancel(true);super.removeNotify();}
}
