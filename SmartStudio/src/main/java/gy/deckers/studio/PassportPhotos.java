package gy.deckers.studio;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.util.List;

/** Physical dimensions are independent of both preview zoom and printer resolution. */
final class PassportPhotos {
    static final double WIDTH = 288, HEIGHT = 432;
    static final double BLEED = 2 * 72 / 25.4;
    private static final double MARK = 4;
    enum Template {
        GUYANA("Guyana — 35 × 45 mm · 4 photos", 35 * 72 / 25.4, 45 * 72 / 25.4, 2, 2),
        US("US — 2 × 2 inches · 2 photos", 144, 144, 1, 2);
        final String label;
        final double width, height;
        final int columns, rows;
        Template(String label, double width, double height, int columns, int rows) {
            this.label=label; this.width=width; this.height=height; this.columns=columns; this.rows=rows;
        }
        public String toString(){return label;}
        List<Rectangle2D.Double> boxes() {
            return boxes(0);
        }
        List<Rectangle2D.Double> boxes(double bleed) {
            double gap=Math.max(12,2*(bleed+MARK)), left=(WIDTH-columns*width-(columns-1)*gap)/2,
                    top=(HEIGHT-rows*height-(rows-1)*gap)/2;
            var boxes=new java.util.ArrayList<Rectangle2D.Double>();
            for(int row=0;row<rows;row++)for(int col=0;col<columns;col++)
                boxes.add(new Rectangle2D.Double(left+col*(width+gap),top+row*(height+gap),width,height));
            return List.copyOf(boxes);
        }
    }
    static void paint(Graphics2D graphics, Template template, PhotoFraming photo) {
        paint(graphics,template,photo,0);
    }
    static void paint(Graphics2D graphics, Template template, PhotoFraming photo,double bleed) {
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setColor(Color.WHITE);g.fill(new Rectangle2D.Double(0,0,WIDTH,HEIGHT));
            for(var box:template.boxes(bleed)) {
                if(photo!=null)photo.styled(photo.background()==null?Color.WHITE:photo.background(),null).paint(g,box.x,box.y,box.width,box.height,bleed);
                else {g.setColor(new Color(230,230,230));g.fill(new Rectangle2D.Double(box.x-bleed,box.y-bleed,box.width+2*bleed,box.height+2*bleed));}
                if(bleed>0){
                    g.setColor(Color.BLACK);g.setStroke(new BasicStroke(.25f));
                    for(double x:new double[]{box.x,box.getMaxX()})for(double y:new double[]{box.y,box.getMaxY()}){
                        double sx=x==box.x?-1:1,sy=y==box.y?-1:1;
                        g.draw(new java.awt.geom.Line2D.Double(x+sx*(bleed+1),y,x+sx*(bleed+MARK),y));
                        g.draw(new java.awt.geom.Line2D.Double(x,y+sy*(bleed+1),x,y+sy*(bleed+MARK)));
                    }
                }
            }
        } finally {g.dispose();}
    }
    static BufferedImage render(Template template, PhotoFraming photo) {
        return render(template,photo,0);
    }
    static BufferedImage render(Template template, PhotoFraming photo,double bleed) {
        BufferedImage image=new BufferedImage(1200,1800,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try {g.scale(300./72,300./72);paint(g,template,photo,bleed);}finally{g.dispose();}
        return image;
    }
    static Printable printable(Template template, PhotoFraming photo) {
        return printable(template,photo,0);
    }
    static Printable printable(Template template, PhotoFraming photo,double bleed) {
        return (graphics,page,index)->{
            if(index!=0)return Printable.NO_SUCH_PAGE;
            boolean landscape=Math.abs(page.getWidth()-HEIGHT)<=2&&Math.abs(page.getHeight()-WIDTH)<=2;
            if(!landscape&&(Math.abs(page.getWidth()-WIDTH)>2||Math.abs(page.getHeight()-HEIGHT)>2))
                throw new PrinterException("Select 4 × 6 inch paper in printer properties. Passport photos print at actual size.");
            Graphics2D draw=(Graphics2D)graphics.create();
            try {
                Rectangle2D area=new Rectangle2D.Double(page.getImageableX(),page.getImageableY(),page.getImageableWidth(),page.getImageableHeight());
                if(landscape){
                    draw.translate(HEIGHT,0);draw.rotate(Math.PI/2);
                    area=new Rectangle2D.Double(page.getImageableY(),HEIGHT-page.getImageableX()-page.getImageableWidth(),page.getImageableHeight(),page.getImageableWidth());
                }
                Rectangle2D bounds=null;
                for(var box:template.boxes(bleed)){
                    double extra=bleed>0?bleed+MARK:0;
                    var outer=new Rectangle2D.Double(box.x-extra,box.y-extra,box.width+2*extra,box.height+2*extra);
                    if(bounds==null)bounds=outer;else bounds.add(outer);
                }
                if(bounds.getWidth()>area.getWidth()+.5||bounds.getHeight()>area.getHeight()+.5)
                    throw new PrinterException("The printer reports too little printable space for these actual-size photos. Select 4 × 6 photo paper and the correct tray in printer properties.");
                // Move the group only as far as needed; never shrink passport photos.
                double dx=Math.max(area.getMinX()-bounds.getMinX(),Math.min(0,area.getMaxX()-bounds.getMaxX()));
                double dy=Math.max(area.getMinY()-bounds.getMinY(),Math.min(0,area.getMaxY()-bounds.getMaxY()));
                draw.translate(dx,dy);paint(draw,template,photo,bleed);
            } finally {draw.dispose();}
            return Printable.PAGE_EXISTS;
        };
    }
}
