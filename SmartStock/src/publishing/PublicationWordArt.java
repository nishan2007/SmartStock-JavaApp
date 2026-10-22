package publishing;

import java.awt.*;
import java.awt.geom.*;

/** Editable vector lettering, shared by canvas, print and PDF rendering. */
public final class PublicationWordArt {
  private PublicationWordArt() {}
  public static void draw(Graphics2D target, Publication.Element e, String text) {
    if (text.isBlank()) return;
    Graphics2D g=(Graphics2D)target.create();
    try {
      var font=PublicationFonts.create(e.font,(e.bold?Font.BOLD:0)|(e.italic?Font.ITALIC:0),100);
      Shape outline=font.createGlyphVector(g.getFontRenderContext(),text.replace('\n',' ')).getOutline();
      var bounds=outline.getBounds2D(); if(bounds.isEmpty())return;
      Path2D path=new Path2D.Double();
      var it=outline.getPathIterator(null,.15); double[] c=new double[6];
      while(!it.isDone()) {
        int kind=it.currentSegment(c);
        double x=(c[0]-bounds.getX())/bounds.getWidth();
        double y=(c[1]-bounds.getY())/bounds.getHeight();
        double bend=switch(e.wordArt){case "ARCH_UP"->-4*x*(1-x);case "ARCH_DOWN"->4*x*(1-x);case "WAVE"->Math.sin(x*Math.PI*2);default->0;};
        double px=x*e.width, py=(y+bend*e.wordArtBend)*e.height;
        switch(kind){case PathIterator.SEG_MOVETO->path.moveTo(px,py);case PathIterator.SEG_LINETO->path.lineTo(px,py);case PathIterator.SEG_CLOSE->path.closePath();default->throw new IllegalStateException("Unflattened glyph");}
        it.next();
      }
      Rectangle2D b=path.getBounds2D(); double pad=e.wordArtOutsideBounds?0:e.wordArtOutlineWidth/2+(e.wordArtHasShadow?.6:0);
      double sx=Math.max(.01,e.width-2*pad)/b.getWidth(),sy=Math.max(.01,e.height-2*pad)/b.getHeight();
      var fit=new AffineTransform();fit.translate(pad,pad);fit.scale(sx,sy);fit.translate(-b.getX(),-b.getY());
      Shape shape=fit.createTransformedShape(path);
      if(e.wordArtHasShadow){g.translate(.5,.5);g.setColor(Color.decode(e.wordArtShadow));g.fill(shape);g.translate(-.5,-.5);}
      g.setPaint(e.wordArtGradient?new GradientPaint(0,0,Color.decode(e.color),0,(float)e.height,Color.decode(e.wordArtEndColor)):Color.decode(e.color));g.fill(shape);
      if(e.wordArtOutlineWidth>0){g.setColor(Color.decode(e.wordArtOutline));g.setStroke(new BasicStroke((float)e.wordArtOutlineWidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);}
      g.setColor(Color.decode(e.color));g.setStroke(new BasicStroke((float)Math.max(.08,e.height/25)));if(e.underline)g.draw(new java.awt.geom.Line2D.Double(0,e.height*.95,e.width,e.height*.95));if(e.strike)g.draw(new java.awt.geom.Line2D.Double(0,e.height*.5,e.width,e.height*.5));
    } finally {g.dispose();}
  }
}
