package ui.helpers;

import ui.design.DeckersPalette;
import utils.ImageCacheManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Delayed, non-focusable product-image preview shared by catalog result tables. */
public final class TableImageHoverPreview {
    private TableImageHoverPreview() { }

    public static void install(Window owner, JTable table, int imageModelColumn, Color accent) {
        Preview preview = new Preview(owner, table, imageModelColumn, accent);
        table.addMouseMotionListener(preview);
        table.addMouseListener(preview);
    }

    public record Picture(String url,String label) { }
    public static void install(Window owner,JTable table,java.util.function.IntFunction<java.util.List<Picture>> pictures,Color accent) {
        JWindow popup=new JWindow(owner);popup.setFocusableWindowState(false);
        class GridPreview extends MouseAdapter {
            int row=-1;long generation;Timer timer;SwingWorker<java.util.List<ImageIcon>,Void> worker;
            void hide(){generation++;row=-1;if(timer!=null)timer.stop();if(worker!=null)worker.cancel(true);popup.setVisible(false);}
            @Override public void mouseExited(MouseEvent e){hide();}
            @Override public void mouseMoved(MouseEvent e){
                int next=table.rowAtPoint(e.getPoint());if(next==row)return;hide();if(next<0)return;
                row=next;long expected=generation;Point anchor=e.getLocationOnScreen();
                timer=new Timer(300,event->{
                    java.util.List<Picture> items=java.util.List.copyOf(pictures.apply(next));
                    if(items.isEmpty())return;
                    int count=Math.min(9,items.size()),edge=count==1?200:110;
                    worker=new SwingWorker<>(){
                        protected java.util.List<ImageIcon> doInBackground(){
                            java.util.List<ImageIcon> icons=new java.util.ArrayList<>();
                            for(Picture item:items.subList(0,count)) {
                                if(isCancelled())break;
                                ImageIcon icon=null;
                                try {
                                    Image raw=item.url()==null||item.url().isBlank()?null:ImageCacheManager.loadImage(item.url());
                                    if(raw!=null&&raw.getWidth(null)>0&&raw.getHeight(null)>0){
                                        double scale=Math.min((double)edge/raw.getWidth(null),(double)edge/raw.getHeight(null));
                                        icon=new ImageIcon(raw.getScaledInstance(Math.max(1,(int)(raw.getWidth(null)*scale)),Math.max(1,(int)(raw.getHeight(null)*scale)),Image.SCALE_SMOOTH));
                                    }
                                }catch(Exception ignored){ }
                                icons.add(icon);
                            }
                            return icons;
                        }
                        protected void done(){
                            if(isCancelled()||generation!=expected||row!=next||!table.isShowing())return;
                            java.util.List<ImageIcon> icons;try{icons=get();}catch(Exception ignored){return;}
                            JPanel content=new JPanel(new BorderLayout(5,5));content.setBackground(DeckersPalette.surface());
                            content.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(accent),BorderFactory.createEmptyBorder(6,6,6,6)));
                            JPanel grid=new JPanel(new GridLayout(0,count==1?1:Math.min(3,count),6,6));grid.setOpaque(false);
                            for(int i=0;i<count;i++){
                                Picture item=items.get(i);JPanel cell=new JPanel(new BorderLayout());cell.setOpaque(false);
                                JLabel image=new JLabel(icons.get(i));image.setHorizontalAlignment(SwingConstants.CENTER);image.setPreferredSize(new Dimension(edge,edge));
                                if(icons.get(i)==null)image.setText(item.url()==null||item.url().isBlank()?"No Image":"Image unavailable");
                                cell.add(image);JLabel label=new JLabel(item.label(),SwingConstants.CENTER);label.setPreferredSize(new Dimension(edge,22));label.setForeground(DeckersPalette.muted());cell.add(label,BorderLayout.SOUTH);grid.add(cell);
                            }
                            content.add(grid);if(items.size()>9)content.add(new JLabel("+"+(items.size()-9)+" more - expand the product to see all options"),BorderLayout.SOUTH);
                            popup.setContentPane(content);popup.pack();Rectangle bounds=table.getGraphicsConfiguration().getBounds();
                            popup.setLocation(Math.max(bounds.x,Math.min(anchor.x+18,bounds.x+bounds.width-popup.getWidth())),Math.max(bounds.y,Math.min(anchor.y,bounds.y+bounds.height-popup.getHeight())));popup.setVisible(true);
                        }
                    };worker.execute();
                });timer.setRepeats(false);timer.start();
            }
        }
        GridPreview listener=new GridPreview();table.addMouseListener(listener);table.addMouseMotionListener(listener);
        table.getModel().addTableModelListener(e->listener.hide());
        table.addHierarchyListener(e->{if(!table.isShowing())listener.hide();});
        owner.addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){listener.hide();popup.dispose();}});
    }

    private static final class Preview extends MouseAdapter implements MouseMotionListener {
        private final JTable table;
        private final int imageColumn;
        private final JWindow window;
        private final JLabel label;
        private Timer timer;
        private SwingWorker<ImageIcon, Void> worker;
        private int row = -1;
        private long generation;

        private Preview(Window owner, JTable table, int imageColumn, Color accent) {
            this.table = table;
            this.imageColumn = imageColumn;
            window = new JWindow(owner);
            window.setFocusableWindowState(false);
            label = new JLabel("", SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(210, 210));
            label.setOpaque(true);
            label.setBackground(DeckersPalette.surface());
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(DeckersPalette.sectionBorder(accent)),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)));
            window.setContentPane(label);
        }

        @Override public void mouseMoved(MouseEvent event) {
            int next = table.rowAtPoint(event.getPoint());
            if (next == row) return;
            hide();
            if (next < 0) return;
            row = next;
            long expected = generation;
            Point anchor = event.getLocationOnScreen();
            timer = new Timer(300, ignored -> load(next, anchor, expected));
            timer.setRepeats(false);
            timer.start();
        }

        @Override public void mouseExited(MouseEvent event) { hide(); }

        private void load(int viewRow, Point anchor, long expected) {
            if (!current(viewRow, expected)) return;
            int modelRow = table.convertRowIndexToModel(viewRow);
            Object value = table.getModel().getValueAt(modelRow, imageColumn);
            String url = value == null ? "" : String.valueOf(value).trim();
            if (url.isBlank()) { show(null, "No Image", anchor, viewRow, expected); return; }
            worker = new SwingWorker<>() {
                @Override protected ImageIcon doInBackground() {
                    Image image = ImageCacheManager.loadImage(url);
                    if (image == null || image.getWidth(null) <= 0 || image.getHeight(null) <= 0) return null;
                    double scale = Math.min(200d / image.getWidth(null), 200d / image.getHeight(null));
                    int width = Math.max(1, (int)Math.round(image.getWidth(null) * scale));
                    int height = Math.max(1, (int)Math.round(image.getHeight(null) * scale));
                    return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
                }
                @Override protected void done() {
                    if (!current(viewRow, expected)) return;
                    ImageIcon icon = null;
                    try { if (!isCancelled()) icon = get(); } catch (Exception ignored) { }
                    show(icon, icon == null ? "Image unavailable" : "", anchor, viewRow, expected);
                }
            };
            worker.execute();
        }

        private boolean current(int viewRow, long expected) {
            return generation == expected && row == viewRow && table.isShowing();
        }

        private void show(ImageIcon icon, String text, Point anchor, int viewRow, long expected) {
            if (!current(viewRow, expected)) return;
            label.setIcon(icon); label.setText(icon == null ? text : "");
            label.setForeground(DeckersPalette.muted()); window.pack();
            Rectangle screen = table.getGraphicsConfiguration().getBounds();
            int x = anchor.x + 18;
            if (x + window.getWidth() > screen.x + screen.width) x = anchor.x - window.getWidth() - 18;
            int y = Math.max(screen.y, Math.min(anchor.y - window.getHeight() / 2,
                    screen.y + screen.height - window.getHeight()));
            window.setLocation(Math.max(screen.x, x), y); window.setVisible(true);
        }

        private void hide() {
            generation++; row = -1;
            if (timer != null) timer.stop();
            if (worker != null) worker.cancel(true);
            window.setVisible(false);
        }
    }
}
