package gy.deckers.studio;

import publishing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** One immutable physical layout for print, preview and cutting. All values are mm. */
final class CutJob {
    enum Edge { TOP, BOTTOM, LEFT, RIGHT }
    enum Contour { RECTANGLE, ROUNDED, CIRCLE, CUSTOM, TEXT }
    static final class Settings {
        int version=1;
        boolean roll=true, autoLength=true, rotateItems, rotateLoading=true, vinyl, weeding;
        double width=1117.6, length=609.6, gap=3, bleed=1, margin=5, grip=19.05, radius=3;
        int copies=10;
        EnumSet<Edge> gripEdges=EnumSet.of(Edge.BOTTOM);
        Contour contour=Contour.RECTANGLE;
        String custom="0,0; 50,0; 50,25; 0,25";
        Settings copy(){return Publication.JSON.fromJson(Publication.JSON.toJson(this),Settings.class);}
        void validate(){
            if(version!=1||gripEdges==null||contour==null)throw new IllegalArgumentException("Unsupported cut settings.");
            for(double n:new double[]{width,length})if(!Double.isFinite(n)||n<=0||n>5080)throw new IllegalArgumentException("Media dimensions must be between 0 and 200 inches.");
            for(double n:new double[]{gap,bleed,margin,grip,radius})if(!Double.isFinite(n)||n<0||n>500)throw new IllegalArgumentException("Invalid media spacing or allowance.");
            if(copies<1||copies>10000)throw new IllegalArgumentException("Choose 1–10,000 copies.");
            if(custom==null||custom.length()>100000)throw new IllegalArgumentException("Contour is too large.");
        }
    }
    private final Publication artwork;
    private final Settings settings;
    private final int page;
    private final List<AffineTransform> placements;
    private final List<Shape> cuts;
    private final List<Point2D.Double> marks;
    private final double width,height;
    private final Rectangle2D content;
    final String id=UUID.randomUUID().toString();

    CutJob(Publication source,int page,Settings input)throws java.io.IOException {
        input.validate();source.validate();
        if(page<0||page>=source.pages.size()||source.pages.get(page).reference)throw new IllegalArgumentException("Select a printable design page.");
        if(!source.mergeRows.isEmpty())throw new IllegalArgumentException("Clear merge records before creating a cut job. This job repeats the selected design page.");
        PublicationPreflight.requireMergeData(source);
        artwork=source.copy();this.page=page;settings=input.copy();
        // Freeze local assets too: later source-file edits cannot change an already printed job.
        long total=0;
        for(String asset:artwork.assetIds()){
            total+=artwork.assetSize(asset);if(total>100*1024*1024)throw new IllegalArgumentException("Cut job assets exceed 100 MiB.");
            artwork.assets.put(asset,Base64.getEncoder().encodeToString(artwork.assetBytes(asset)));
        }
        artwork.assetFiles.clear();
        width=settings.width;
        double left=inset(Edge.LEFT),right=inset(Edge.RIGHT),top=inset(Edge.TOP),bottom=inset(Edge.BOTTOM);
        // Registration crosses occupy their own band, outside bleed and artwork.
        double band=settings.vinyl?0:8;
        double w=settings.rotateItems?artwork.height:artwork.width,h=settings.rotateItems?artwork.width:artwork.height;
        double cellW=w+2*settings.bleed,cellH=h+2*settings.bleed;
        double available=width-left-right-2*band;
        int columns=(int)Math.floor((available+settings.gap+1e-7)/(cellW+settings.gap));
        if(columns<1)throw new IllegalArgumentException("No label fits across this media after margins, grip and registration allowance.");
        columns=Math.min(columns,settings.copies);
        int rows=(settings.copies+columns-1)/columns;
        double required=top+bottom+2*band+rows*cellH+(rows-1)*settings.gap;
        height=settings.roll&&settings.autoLength?required:settings.length;
        if(height>5080||height+1e-7<required)throw new IllegalArgumentException("Copies need "+String.format(Locale.ROOT,"%.3f",required/25.4)+" inches of media length. Increase length or reduce copies.");
        content=new Rectangle2D.Double(left,top,width-left-right,height-top-bottom);
        if(content.getWidth()<=2*band||content.getHeight()<=2*band)throw new IllegalArgumentException("Allowances leave no usable material.");
        Shape contour=contour(artwork,page,settings);
        Rectangle2D cb=contour.getBounds2D();
        if(cb.isEmpty()||cb.getMinX()<-.001||cb.getMinY()<-.001||cb.getMaxX()>artwork.width+.001||cb.getMaxY()>artwork.height+.001)throw new IllegalArgumentException("Cut contour must fit inside the design dimensions.");
        placements=new ArrayList<>();cuts=new ArrayList<>();
        for(int i=0;i<settings.copies;i++){
            AffineTransform t=AffineTransform.getTranslateInstance(left+band+settings.bleed+(i%columns)*(cellW+settings.gap),top+band+settings.bleed+(i/columns)*(cellH+settings.gap));
            if(settings.rotateItems){t.translate(artwork.height,0);t.rotate(Math.PI/2);}
            placements.add(t);cuts.add(t.createTransformedShape(contour));
        }
        if(settings.weeding){double pad=2;if(settings.bleed<pad)throw new IllegalArgumentException("A weeding border requires at least 2 mm bleed clearance.");
            Rectangle2D b=cuts.stream().map(Shape::getBounds2D).reduce((a,bounds)->a.createUnion(bounds)).orElseThrow();
            cuts.add(new Rectangle2D.Double(b.getX()-pad,b.getY()-pad,b.getWidth()+2*pad,b.getHeight()+2*pad));}
        marks=settings.vinyl?List.of():List.of(new Point2D.Double(left+3,top+3),new Point2D.Double(width-right-3,top+3),new Point2D.Double(left+3,height-bottom-3));
    }
    private double inset(Edge e){return settings.margin+(settings.gripEdges.contains(e)?settings.grip:0);}
    double width(){return width;} double height(){return height;} int copies(){return settings.copies;}
    boolean vinyl(){return settings.vinyl;}
    Settings settings(){return settings.copy();}
    List<Shape> cuts(){return cuts.stream().map(s->(Shape)new Path2D.Double(s)).toList();}
    List<Point2D.Double> marks(){return marks.stream().map(p->new Point2D.Double(p.x,p.y)).toList();}
    AffineTransform loading(){return settings.rotateLoading?new AffineTransform(0,1,-1,0,height,0):new AffineTransform();}
    double loadedWidth(){return settings.rotateLoading?height:width;}
    double loadedLength(){return settings.rotateLoading?width:height;}
    Edge loadedEdge(Edge edge){if(!settings.rotateLoading)return edge;return switch(edge){case TOP->Edge.RIGHT;case RIGHT->Edge.BOTTOM;case BOTTOM->Edge.LEFT;case LEFT->Edge.TOP;};}
    void requireFits(double feedWidth,double cuttingWidth,double feedLength,AffineTransform alignment){
        for(double n:new double[]{feedWidth,cuttingWidth,feedLength})if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Enter verified cutter limits.");
        if(loadedWidth()>feedWidth+.001||loadedLength()>feedLength+.001)throw new IllegalArgumentException("Loaded material exceeds the verified cutter feed limits. Change orientation or job length.");
        for(Shape cut:cuts){Rectangle2D b=alignment.createTransformedShape(cut).getBounds2D();
            if(b.getMinX()<-.001||b.getMaxX()>cuttingWidth+.001||b.getMinY()<-.001||b.getMaxY()>feedLength+.001)throw new IllegalArgumentException("Aligned cut paths exceed verified cutter bounds. Check origin, loading and registration.");}
        for(Point2D mark:marks){Point2D p=alignment.transform(mark,null);if(p.getX()<0||p.getX()>cuttingWidth||p.getY()<0||p.getY()>feedLength)throw new IllegalArgumentException("Registration targets exceed the cutter's reachable area. Use narrower media or change loading orientation.");}
    }
    Rectangle2D printBounds(){return (Rectangle2D)content.clone();}
    private static final class SavedJob {int version=1,page;Publication artwork;Settings media;Map<String,String> assets;}
    void save(java.nio.file.Path file)throws java.io.IOException {
        SavedJob saved=new SavedJob();saved.page=page;saved.artwork=artwork;saved.media=settings;saved.assets=artwork.assets;
        java.nio.file.Path temporary=java.nio.file.Files.createTempFile(file.toAbsolutePath().getParent(),"cut-job-",".json");
        try{java.nio.file.Files.writeString(temporary,Publication.JSON.toJson(saved));java.nio.file.Files.move(temporary,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{java.nio.file.Files.deleteIfExists(temporary);}
    }
    static CutJob load(java.nio.file.Path file)throws java.io.IOException {
        if(java.nio.file.Files.size(file)>145*1024*1024)throw new java.io.IOException("Cut job file exceeds 145 MiB.");
        try{
            SavedJob saved=Publication.JSON.fromJson(java.nio.file.Files.readString(file),SavedJob.class);
            if(saved==null||saved.version!=1||saved.artwork==null||saved.media==null||saved.assets==null)throw new IllegalArgumentException("Unsupported cut job file.");
            saved.artwork.assets=new LinkedHashMap<>(saved.assets);saved.artwork.assetFiles=new LinkedHashMap<>();return new CutJob(saved.artwork,saved.page,saved.media);
        }catch(RuntimeException ex){throw new java.io.IOException("Cannot open cut job: "+ex.getMessage(),ex);}
    }
    void paint(Graphics2D target,boolean preview){
        Graphics2D g=(Graphics2D)target.create();
        try{
            g.setColor(Color.WHITE);g.fill(new Rectangle2D.Double(0,0,width,height));
            if(preview){g.setColor(new Color(0xE8DCF8));
                for(Edge e:settings.gripEdges){double n=settings.grip;g.fill(switch(e){case LEFT->new Rectangle2D.Double(0,0,n,height);case RIGHT->new Rectangle2D.Double(width-n,0,n,height);case TOP->new Rectangle2D.Double(0,0,width,n);case BOTTOM->new Rectangle2D.Double(0,height-n,width,n);});}}
            PublicationRenderer renderer=new PublicationRenderer(artwork,preview?1024:0);
            if(!settings.vinyl)for(AffineTransform t:placements){Graphics2D a=(Graphics2D)g.create();try{
                a.transform(t);
                // Expanded artwork supplies only the outer bleed; trim artwork remains actual size.
                double b=settings.bleed;
                if(b>0){Graphics2D outer=(Graphics2D)a.create();try{outer.translate(-b,-b);outer.scale((artwork.width+2*b)/artwork.width,(artwork.height+2*b)/artwork.height);renderer.page(outer,page,Map.of());}finally{outer.dispose();}}
                renderer.page(a,page,Map.of());
            }finally{a.dispose();}}
            double screenScale=Math.sqrt(Math.abs(g.getTransform().getDeterminant()));
            g.setStroke(new BasicStroke(preview?(float)(1.2/Math.max(.001,screenScale)):.25f));g.setColor(Color.BLACK);
            for(Point2D p:marks){g.draw(new Line2D.Double(p.getX()-2,p.getY(),p.getX()+2,p.getY()));g.draw(new Line2D.Double(p.getX(),p.getY()-2,p.getX(),p.getY()+2));}
            if(preview){g.setColor(StudioVisuals.PURPLE);for(Shape s:cuts)g.draw(s);}
        }finally{g.dispose();}
    }
    static Shape contour(Publication p,int page,Settings s){return switch(s.contour){
        case RECTANGLE->new Rectangle2D.Double(0,0,p.width,p.height);
        case ROUNDED->new RoundRectangle2D.Double(0,0,p.width,p.height,Math.min(2*s.radius,p.width),Math.min(2*s.radius,p.height));
        case CIRCLE->{if(Math.abs(p.width-p.height)>.001)throw new IllegalArgumentException("A circle needs equal design width and height.");yield new Ellipse2D.Double(0,0,p.width,p.height);}
        case CUSTOM->polygon(s.custom);
        case TEXT->{
            Path2D all=new Path2D.Double();boolean found=false;
            if(!p.masters.isEmpty()&&!p.pages.get(page).master.isEmpty())throw new IllegalArgumentException("Text cutting does not include master-page text. Use an explicit contour.");
            for(Publication.Element e:p.pages.get(page).objects){if(e.hidden)continue;
                if(!e.type.equals("TEXT")||!e.wordArt.equals("NONE")||!e.story.isEmpty()||e.text.contains("{{")||e.text.contains("\n")||e.text.contains("<")||e.fitText||e.underline||e.strike||e.textRotation!=0)throw new IllegalArgumentException("Text outlines support plain, single-line text only. Use an explicit contour for other artwork.");
                Font f=PublicationFonts.create(e.font,(e.bold?Font.BOLD:0)|(e.italic?Font.ITALIC:0),(float)(e.fontSize*25.4/72));
                var glyph=f.createGlyphVector(new FontRenderContext(null,true,true),e.text);Shape outline=glyph.getOutline();Rectangle2D b=outline.getBounds2D();
                double offset=e.textAlignment==null?0:e.textAlignment.offset(e.width,glyph.getGlyphPosition(glyph.getNumGlyphs()).getX());
                AffineTransform t=AffineTransform.getRotateInstance(Math.toRadians(e.rotation),e.x+e.width/2,e.y+e.height/2);t.translate(e.x-b.getX()+offset,e.y-b.getY());all.append(t.createTransformedShape(outline),false);found=true;
            }
            if(!found)throw new IllegalArgumentException("Add text before creating an outlined-text cut job.");yield all;
        }
    };}
    static Path2D polygon(String text){
        Path2D path=new Path2D.Double();String[] points=text.trim().split(";");if(points.length<3||points.length>10000)throw new IllegalArgumentException("Enter 3–10,000 x,y points in mm, separated by semicolons.");
        for(int i=0;i<points.length;i++){String[] pair=points[i].trim().split(",");if(pair.length!=2)throw new IllegalArgumentException("Contour points must be x,y pairs in mm.");double x=Double.parseDouble(pair[0].trim()),y=Double.parseDouble(pair[1].trim());if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Contour coordinates must be finite.");if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.closePath();return path;
    }
    static AffineTransform register(List<? extends Point2D> printed,List<? extends Point2D> measured){
        if(printed.size()!=3||measured.size()!=3)throw new IllegalArgumentException("Capture three registration targets.");
        for(Point2D p:measured)if(!Double.isFinite(p.getX())||!Double.isFinite(p.getY()))throw new IllegalArgumentException("Registration measurements must be finite.");
        Point2D a=printed.get(0),b=printed.get(1),c=printed.get(2),x=measured.get(0),y=measured.get(1),z=measured.get(2);
        AffineTransform from=new AffineTransform(b.getX()-a.getX(),b.getY()-a.getY(),c.getX()-a.getX(),c.getY()-a.getY(),a.getX(),a.getY());
        AffineTransform to=new AffineTransform(y.getX()-x.getX(),y.getY()-x.getY(),z.getX()-x.getX(),z.getY()-x.getY(),x.getX(),x.getY());
        try{to.concatenate(from.createInverse());}catch(NoninvertibleTransformException e){throw new IllegalArgumentException("Printed registration targets are collinear.");}
        double determinant=to.getDeterminant();if(!Double.isFinite(determinant)||determinant<=.25||determinant>4)throw new IllegalArgumentException("Registration is mirrored, degenerate or far from actual size. Check target order and units.");
        return to;
    }
}
