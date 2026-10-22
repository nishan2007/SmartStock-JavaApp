package publishing;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.*;
import java.util.List;

/** Same physical-coordinate painter for editor, imposed sheets, PDF and PrinterJob. */
public final class PublicationRenderer {
  private final Publication publication;
  private final int maximumImageSide;
  private final Map<String, BufferedImage> images = new LinkedHashMap<>(4,.75f,true){
    protected boolean removeEldestEntry(Map.Entry<String,BufferedImage> eldest){return maximumImageSide==0?size()>0:size()>2;}
  };
  private final Set<String> warnings = new LinkedHashSet<>();
  private java.util.function.BiFunction<Publication.Element,BufferedImage,BufferedImage> rasterFilter;

  public PublicationRenderer(Publication p) {
    this(p,0);
  }
  public PublicationRenderer(Publication p,int maximumImageSide) {
    publication = p;
    this.maximumImageSide=maximumImageSide;
  }

  public List<String> warnings() {
    return List.copyOf(warnings);
  }

  public void page(Graphics2D target, int index, Map<String, String> row) {
    page(target, index, row, 0);
  }
  public void page(Graphics2D target,int index,Map<String,String> row,java.util.function.BiFunction<Publication.Element,BufferedImage,BufferedImage> filter){
    if(rasterFilter!=null)throw new IllegalStateException("A raster effect is already active.");
    rasterFilter=Objects.requireNonNull(filter);try{page(target,index,row,0);}finally{rasterFilter=null;}
  }

  private void page(Graphics2D target, int index, Map<String, String> row, double bleed) {
    Graphics2D g = (Graphics2D) target.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(Color.WHITE);
      g.fill(
          new Rectangle2D.Double(
              -bleed, -bleed, publication.width + 2 * bleed, publication.height + 2 * bleed));
      g.clip(
          new Rectangle2D.Double(
              -bleed, -bleed, publication.width + 2 * bleed, publication.height + 2 * bleed));
      Publication.Page page = publication.pages.get(index);
      Map<String, Integer> offsets = new HashMap<>();
      for (int i = 0; i < index; i++)
        if (!publication.pages.get(i).reference)
          flowPage(publication.pages.get(i), offsets, null, pageNumber(i), row);
      for (Publication.Page master : publication.masters)
        if (master.name.equals(page.master))
          flowPage(master, new HashMap<>(), g, pageNumber(index), row);
      flowPage(page, offsets, g, pageNumber(index), row);
      for (var story : offsets.entrySet()) {
        boolean later = false;
        for (int i = index + 1; i < publication.pages.size(); i++)
          for (var e : publication.pages.get(i).objects)
            if (e.story.equals(story.getKey())) later = true;
        if (!later
            && story.getValue()
                < merge(publication.stories.getOrDefault(story.getKey(), ""), row).length())
          warnings.add("Linked story overflow: " + story.getKey());
      }
    } finally {
      g.dispose();
    }
  }

  private void flowPage(
      Publication.Page page,
      Map<String, Integer> offsets,
      Graphics2D target,
      int number,
      Map<String, String> row) {
    BufferedImage scratch =
        target == null ? new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB) : null;
    Graphics2D base = target == null ? scratch.createGraphics() : target;
    try {
      for (Publication.Element e : page.objects) {
        // Optional job-only raster effects operate on an isolated object, preserving
        // its alpha and the renderer's normal crop, rotation, flow and stacking.
        Rectangle deviceBounds=null;
        BufferedImage layer=null;
        if(rasterFilter!=null&&target!=null){
          Shape clip=base.getClip();
          if(clip==null)throw new IllegalArgumentException("Raster effects require a clipped page.");
          deviceBounds=base.getTransform().createTransformedShape(clip).getBounds();
          if(deviceBounds.width<=0||deviceBounds.height<=0)continue;
          if((long)deviceBounds.width*deviceBounds.height>100000000)throw new IllegalArgumentException("Raster effect exceeds image limits.");
          layer=new BufferedImage(deviceBounds.width,deviceBounds.height,BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g = layer==null?(Graphics2D)base.create():layer.createGraphics();
        if(layer!=null){g.translate(-deviceBounds.x,-deviceBounds.y);g.transform(base.getTransform());g.setClip(base.getClip());g.setRenderingHints(base.getRenderingHints());}
        try {
          if (e.x < -publication.print.bleed
              || e.y < -publication.print.bleed
              || e.x + e.width > publication.width + publication.print.bleed + .001
              || e.y + e.height > publication.height + publication.print.bleed + .001)
            warnings.add("Object may clip on page " + (number + 1));
          g.translate(e.x, e.y);
          g.rotate(Math.toRadians(e.rotation), e.width / 2, e.height / 2);
          g.setColor(color(e.color));
          g.setStroke(new BasicStroke((float) Math.max(.01, e.strokeWidth)));
          String text =
              merge(e.story.isBlank() ? e.text : publication.stories.getOrDefault(e.story, ""), row)
                  .replace("{{page}}", String.valueOf(number + 1));
          switch (e.type) {
            case "TEXT" -> {
              if (!e.wordArt.equals("NONE")) { PublicationWordArt.draw(g,e,text); break; }
              if(e.fitText){
                if(!e.asset.isBlank()&&text.equals(e.originalText)&&e.font.equals(e.originalFont)&&e.fontSize==e.originalFontSize&&e.bold==e.originalBold&&e.color.equals(e.originalColor)&&!e.italic&&!e.underline&&!e.strike&&!e.textOutline)image(g,e);
                else {
                  if(!e.sourceTable.isBlank()){
                    if(!e.fill.equals("none")){g.setColor(color(e.fill));g.fill(new Rectangle2D.Double(0,0,e.width,e.height));}
                    g.setColor(color(e.stroke));g.draw(new Rectangle2D.Double(0,0,e.width,e.height));g.setColor(color(e.color));
                  }
                  if(Math.abs(e.textRotation)>0.01){
                    var rotated=(Graphics2D)g.create();try{rotated.rotate(Math.toRadians(e.textRotation),e.width/2,e.height/2);var field=Publication.JSON.fromJson(Publication.JSON.toJson(e),Publication.Element.class);if(Math.abs(Math.abs(e.textRotation)%180-90)<.01){rotated.translate((e.width-e.height)/2,(e.height-e.width)/2);field.width=e.height;field.height=e.width;}fittedText(rotated,field,text);}finally{rotated.dispose();}
                  }else fittedText(g,e,text);
                }
                break;
              }
              int offset = e.story.isBlank() ? 0 : offsets.getOrDefault(e.story, 0);
              int end = text(g, e, text, offset);
              if (!e.story.isBlank()) offsets.put(e.story, end);
              else if (end < text.length()) warnings.add("Text overflow on page " + (number + 1));
            }
            case "CYLINDER" -> {
              double cap=Math.min(e.width/4,e.height/4);
              var body=new Area(new Rectangle2D.Double(0,cap/2,e.width,e.height-cap));
              body.add(new Area(new Ellipse2D.Double(0,0,e.width,cap)));
              body.add(new Area(new Ellipse2D.Double(0,e.height-cap,e.width,cap)));
              if(!e.fill.equals("none")){g.setColor(color(e.fill));g.fill(body);}
              if(!e.stroke.equals("none")){g.setColor(color(e.stroke));g.draw(body);g.draw(new Ellipse2D.Double(0,0,e.width,cap));}
            }
            case "TABLE" -> table(g, e, text);
            case "IMAGE" -> image(g, e);
            case "QR", "BARCODE" -> code(g, e, text);
            case "LINE" -> g.draw(new Line2D.Double(0, 0, e.width, e.height));
            default -> {
              Shape shape =
                  e.type.equals("ELLIPSE")
                      ? new Ellipse2D.Double(0, 0, e.width, e.height)
                      : new Rectangle2D.Double(0, 0, e.width, e.height);
              if (!e.fill.equals("none")) {
                g.setColor(color(e.fill));
                g.fill(shape);
              }
              if (!e.stroke.equals("none")) {
                g.setColor(color(e.stroke));
                g.draw(shape);
              }
            }
          }
        } finally {
          g.dispose();
        }
        if(layer!=null){
          BufferedImage processed=rasterFilter.apply(e,layer);
          if(processed==null||processed.getWidth()!=layer.getWidth()||processed.getHeight()!=layer.getHeight())throw new IllegalArgumentException("Raster effects must preserve object raster dimensions.");
          Graphics2D output=(Graphics2D)base.create();
          try{output.setTransform(new AffineTransform());output.drawImage(processed,deviceBounds.x,deviceBounds.y,null);}finally{output.dispose();}
        }
      }
    } finally {
      if (target == null) base.dispose();
    }
  }

  private int pageNumber(int index) {
    int number = 0;
    for (int i = 0; i <= index; i++) if (!publication.pages.get(i).reference) number++;
    return Math.max(0, number - 1);
  }

  private int text(Graphics2D g, Publication.Element e, String value, int start) {
    if (start >= value.length()) return value.length();
    Font font =
        PublicationFonts.create(e.font, (e.bold ? Font.BOLD : 0) | (e.italic ? Font.ITALIC : 0), 1)
            .deriveFont((float) (e.fontSize * 25.4 / 72));
    if (!PublicationFonts.available(e.font))
      warnings.add("Font substituted: " + e.font);
    g.setFont(font);
    g.setColor(color(e.color));
    g.clip(new Rectangle2D.Double(0, 0, e.width, e.height));
    double gutter = 2, colWidth = (e.width - (e.columns - 1) * gutter) / e.columns;
    if (colWidth <= 0) return start;
    int pos = start;
    for (int col = 0; col < e.columns && pos < value.length(); col++) {
      float y = 0;
      while (pos < value.length()) {
        int newline = value.indexOf('\n', pos);
        int end = newline < 0 ? value.length() : newline;
        if (end == pos) {
          y += font.getSize2D() * 1.2f;
          if (y > e.height) break;
          pos++;
          continue;
        }
        AttributedString paragraph = new AttributedString(value.substring(pos, end));
        paragraph.addAttribute(TextAttribute.FONT, font);
        LineBreakMeasurer lines =
            new LineBreakMeasurer(paragraph.getIterator(), new FontRenderContext(null, true, true));
        int paragraphStart = pos;
        boolean full = false;
        while (lines.getPosition() < end - paragraphStart) {
          TextLayout line = lines.nextLayout((float) colWidth);
          float needed = line.getAscent() + line.getDescent() + line.getLeading();
          if (y + needed > e.height + .0001) {
            full = true;
            break;
          }
          y += line.getAscent();
          float x=(float)(col*(colWidth+gutter));
          Shape glyph=line.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x,y));
          paintText(g,e,glyph,x,y,line.getAdvance(),font.getSize2D());
          y += line.getDescent() + line.getLeading();
          pos = paragraphStart + lines.getPosition();
        }
        if (full) break;
        if (pos == end && newline >= 0) pos++;
      }
    }
    return pos;
  }

  private void paintText(Graphics2D g,Publication.Element e,Shape glyph,double x,double baseline,double advance,double size){
    g.setColor(color(e.color));g.fill(glyph);if(e.textOutline&&e.textOutlineWidth>0){g.setColor(color(e.textOutlineColor));g.setStroke(new BasicStroke((float)e.textOutlineWidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(glyph);}
    g.setColor(color(e.color));g.setStroke(new BasicStroke((float)Math.max(.08,size/18)));if(e.underline)g.draw(new java.awt.geom.Line2D.Double(x,baseline+size*.1,x+advance,baseline+size*.1));if(e.strike)g.draw(new java.awt.geom.Line2D.Double(x,baseline-size*.3,x+advance,baseline-size*.3));
  }
  private void fittedText(Graphics2D g,Publication.Element e,String value){
    if(value.isBlank())return;
    Font font=PublicationFonts.create(e.font,(e.bold?Font.BOLD:0)|(e.italic?Font.ITALIC:0),100);
    String[] lines=value.split("\n",-1);double lineHeight=e.height/lines.length;
    g.clip(new Rectangle2D.Double(0,0,e.width,e.height));
    for(int i=0;i<lines.length;i++)if(!lines[i].isBlank()){
      var glyph=font.createGlyphVector(g.getFontRenderContext(),lines[i]);var outline=glyph.getOutline();var bounds=outline.getBounds2D();if(bounds.isEmpty())continue;
      Graphics2D line=(Graphics2D)g.create();try{line.translate(0,i*lineHeight);var fit=new java.awt.geom.AffineTransform();fit.scale(e.width/bounds.getWidth(),lineHeight/bounds.getHeight());fit.translate(-bounds.getX(),-bounds.getY());paintText(line,e,fit.createTransformedShape(outline),0,lineHeight*.9,e.width,lineHeight);}finally{line.dispose();}
    }
  }

  private void table(Graphics2D g, Publication.Element e, String text) {
    String[] rows = text.split("\n", -1);
    double rh = e.height / rows.length, cw = e.width / e.tableColumns;
    for (int r = 0; r < rows.length; r++) {
      String[] cells = rows[r].split("\t", -1);
      for (int c = 0; c < e.tableColumns; c++) {
        g.draw(new Rectangle2D.Double(c * cw, r * rh, cw, rh));
        Publication.Element cell = new Publication.Element();
        cell.width = Math.max(.1, cw - 2);
        cell.height = Math.max(.1, rh - 2);
        cell.font = e.font;
        cell.fontSize = e.fontSize;
        cell.color = e.color;
        cell.bold = e.bold;
        Graphics2D cg = (Graphics2D) g.create();
        cg.translate(c * cw + 1, r * rh + 1);
        if (c < cells.length && text(cg, cell, cells[c], 0) < cells[c].length())
          warnings.add("Table cell overflow");
        cg.dispose();
      }
    }
  }

  private void image(Graphics2D g, Publication.Element e) {
    BufferedImage image = images.get(e.asset);
    if (image == null && publication.assetIds().contains(e.asset))
      try (var source=publication.openAsset(e.asset)) {
        image =
            PublicationImport.decodeImage(
                source,maximumImageSide);
        images.put(e.asset, image);
      } catch (Exception ex) {
        warnings.add("Unreadable image: " + e.asset);
      }
    if (image == null) {
      warnings.add("Missing image: " + e.asset);
      return;
    }
    int x = (int) (e.cropX * image.getWidth()), y = (int) (e.cropY * image.getHeight());
    int w = Math.max(1, Math.min(image.getWidth() - x, (int) (e.cropWidth * image.getWidth()))),
        h = Math.max(1, Math.min(image.getHeight() - y, (int) (e.cropHeight * image.getHeight())));
    if (w / (e.width / 25.4) < 150 || h / (e.height / 25.4) < 150)
      warnings.add("Image below 150 dpi: " + e.asset);
    BufferedImage crop = image.getSubimage(x, y, w, h);
    var composite=g.getComposite();
    try{g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1-e.transparency/100f));
      g.drawImage(crop, AffineTransform.getScaleInstance(e.width / w, e.height / h), null);
    }finally{g.setComposite(composite);}
  }

  private void code(Graphics2D g, Publication.Element e, String text) {
    try {
      String format=e.type.equals("QR")?"QR_CODE":e.barcodeFormat;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
      BitMatrix bits=PublicationBarcode.encode(text,format);
      double codeWidth=e.width,codeHeight=e.height;
      if(format.equals("QR_CODE")){double side=Math.min(e.width,e.height);g.translate((e.width-side)/2,(e.height-side)/2);codeWidth=codeHeight=side;}
      boolean caption=!format.equals("QR_CODE")&&e.barcodeText;
      double captionHeight=caption?Math.min(4,e.height*.22):0,barsHeight=codeHeight-captionHeight;
      g.setColor(Color.WHITE);g.fill(new Rectangle2D.Double(0,0,codeWidth,codeHeight));g.setColor(Color.BLACK);
      for(int y=0;y<bits.getHeight();y++)for(int x=0;x<bits.getWidth();){if(!bits.get(x,y)){x++;continue;}int start=x;while(x<bits.getWidth()&&bits.get(x,y))x++;g.fill(new Rectangle2D.Double(start*codeWidth/bits.getWidth(),y*barsHeight/bits.getHeight(),(x-start)*codeWidth/bits.getWidth(),barsHeight/bits.getHeight()));}
      if(caption){Publication.Element label=new Publication.Element();label.width=e.width;label.height=captionHeight*.75;Graphics2D line=(Graphics2D)g.create();try{line.translate(0,barsHeight+captionHeight*.15);fittedText(line,label,text);}finally{line.dispose();}}
    } catch (Exception ex) {
      warnings.add("Invalid " + e.type + ": " + ex.getMessage());
    }
  }

  public static String merge(String text, Map<String, String> row) {
    for (var field : row.entrySet())
      text =
          text.replace(
              "{{" + field.getKey() + "}}", field.getValue() == null ? "" : field.getValue());
    return text;
  }

  public static Color color(String value) {
    try {
      return Color.decode(value);
    } catch (Exception e) {
      return Color.BLACK;
    }
  }

  public void sheet(Graphics2D target, int sheet) {
    var s = publication.print;
    Graphics2D g = (Graphics2D) target.create();
    try {
      g.setColor(Color.WHITE);
      g.fill(new Rectangle2D.Double(0, 0, s.width, s.height));
      if (s.booklet) {
        List<Integer> output = outputPages();
        var pairs = SheetLayout.booklet(output.size());
        int[] pair = pairs.get(sheet % pairs.size());
        int instance = sheet / pairs.size();
        if (publication.width * 2 > s.width - s.left - s.right
            || publication.height > s.height - s.top - s.bottom)
          throw new IllegalArgumentException(
              "Booklet needs a sheet at least twice the publication width; sizes are not scaled.");
        for (int i = 0; i < 2; i++)
          if (pair[i] >= 0) {
            Graphics2D child = (Graphics2D) g.create();
            child.translate(
                s.left + i * publication.width + s.offsetX + (sheet % 2 == 1 ? s.backOffsetX : 0),
                s.top + s.offsetY + (sheet % 2 == 1 ? s.backOffsetY : 0));
            page(child, output.get(pair[i]), mergeRow(instance));
            child.dispose();
          }
        return;
      }
      var layout = SheetLayout.pack(publication.width, publication.height, s);
      if (layout.capacity() == 0)
        throw new IllegalArgumentException("The item does not fit this sheet.");
      boolean back = s.duplex && sheet % 2 == 1;
      int logicalSheet = s.duplex ? sheet / 2 : sheet;
      List<Integer> output = outputPages();
      int logicalPages = s.duplex ? (output.size() + 1) / 2 : output.size();
      int batches = batches();
      int batch = logicalSheet / logicalPages,
          pageIndex = logicalSheet % logicalPages * (s.duplex ? 2 : 1) + (back ? 1 : 0);
      if (!s.mixPages && (pageIndex >= output.size() || batch >= batches)) return;
      int total =
          publication.mergeRows.isEmpty()
              ? (s.quantity == 0 ? layout.capacity() - s.skip : s.quantity)
              : mergeQuantity();
      for (int slot = 0; slot < layout.capacity(); slot++) {
        int instance = SheetSequence.instance(batch,slot,layout.capacity(),batches,s.cutAndStack) - s.skip;
        int cardPage = pageIndex;
        if (s.mixPages) {
          int item = sheet * layout.capacity() + slot - s.skip;
          if (item < 0 || item >= (long) total * output.size()) continue;
          instance = item / output.size();
          cardPage = item % output.size();
        }
        if (instance < 0 || instance >= total) continue;
        var place = layout.placements().get(slot);
        if (back) place = SheetLayout.back(place, s);
        Graphics2D child = (Graphics2D) g.create();
        child.translate(
            place.x() + s.offsetX + (back ? s.backOffsetX : 0),
            place.y() + s.offsetY + (back ? s.backOffsetY : 0));
        if (place.rotated()) {
          child.translate(publication.height, 0);
          child.rotate(Math.PI / 2);
        }
        page(child, output.get(cardPage), mergeRow(instance), s.bleed);
        if (s.cropMarks) {
          child.setColor(Color.BLACK);
          child.setStroke(new BasicStroke(.1f));
          double b = s.bleed + .5;
          for (double x : new double[] {0, publication.width})
            for (double y : new double[] {0, publication.height}) {
              child.draw(new Line2D.Double(x == 0 ? -b - 2 : x + b, y, x == 0 ? -b : x + b + 2, y));
              child.draw(new Line2D.Double(x, y == 0 ? -b - 2 : y + b, x, y == 0 ? -b : y + b + 2));
            }
        }
        child.dispose();
      }
    } finally {
      g.dispose();
    }
  }

  private int mergeQuantity() {
    long n = 0;
    for (var row : publication.mergeRows) n += quantity(row);
    if (n > 100000) throw new IllegalArgumentException("Merge exceeds 100000 copies.");
    return (int) n;
  }

  private int quantity(Map<String, String> row) {
    try {
      int n = Integer.parseInt(row.getOrDefault("quantity", "1"));
      if (n < 1 || n > 100000) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Merge quantity must be a whole number from 1 to 100000.");
    }
  }

  private Map<String, String> mergeRow(int instance) {
    for (var row : publication.mergeRows) {
      int n = quantity(row);
      if (instance < n) return row;
      instance -= n;
    }
    return Map.of();
  }

  public int batches() {
    var r = SheetLayout.pack(publication.width, publication.height, publication.print);
    if (r.capacity() == 0) throw new IllegalArgumentException("The item does not fit this sheet.");
    if (publication.print.skip >= r.capacity())
      throw new IllegalArgumentException("Skipped positions must be less than the sheet capacity.");
    return r.pageCount(
        publication.mergeRows.isEmpty() ? publication.print.quantity : mergeQuantity(),
        publication.print.skip);
  }

  private List<Integer> outputPages() {
    List<Integer> indices = new ArrayList<>();
    for (int i = 0; i < publication.pages.size(); i++)
      if (!publication.pages.get(i).reference) indices.add(i);
    return indices;
  }

  public int sheets() {
    int count = outputPages().size();
    if (count == 0) throw new IllegalArgumentException("No printable pages.");
    if (publication.print.mixPages) {
      if (publication.print.duplex || publication.print.booklet)
        throw new IllegalArgumentException("Mixed pages require single-sided sheet printing.");
      var layout = SheetLayout.pack(publication.width, publication.height, publication.print);
      if (layout.capacity() == 0) throw new IllegalArgumentException("The item does not fit this sheet.");
      if (publication.print.skip >= layout.capacity()) throw new IllegalArgumentException("Skipped positions must be less than sheet capacity.");
      long copies = publication.mergeRows.isEmpty()
          ? (publication.print.quantity == 0 ? layout.capacity() - publication.print.skip : publication.print.quantity)
          : mergeQuantity();
      return (int) ((copies * count + publication.print.skip + layout.capacity() - 1) / layout.capacity());
    }
    if (publication.print.booklet) {
      var s = publication.print;
      if (publication.width * 2 > s.width - s.left - s.right + 1e-8
          || publication.height > s.height - s.top - s.bottom + 1e-8)
        throw new IllegalArgumentException(
            "Booklet sheet must fit two pages side by side at actual size.");
      if (publication.print.skip > 0)
        throw new IllegalArgumentException("Booklets cannot use skipped label positions.");
      return SheetLayout.booklet(count).size()
          * (publication.mergeRows.isEmpty()
              ? Math.max(1, publication.print.quantity)
              : mergeQuantity());
    }
    return batches() * (publication.print.duplex ? 2 * ((count + 1) / 2) : count);
  }

  public BufferedImage raster(int sheet, int dpi) {
    int w = (int) Math.ceil(publication.print.width / 25.4 * dpi),
        h = (int) Math.ceil(publication.print.height / 25.4 * dpi);
    if ((long) w * h > 100000000)
      throw new IllegalArgumentException("Sheet is too large for this image resolution.");
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
      try{g.scale(dpi / 25.4, dpi / 25.4);sheet(g, sheet);}finally{g.dispose();}
    return image;
  }
  /** Raster-only, per-object effects; ordinary rendering and the publication stay unchanged. */
  public BufferedImage raster(int sheet,int dpi,java.util.function.BiFunction<Publication.Element,BufferedImage,BufferedImage> filter){
    if(rasterFilter!=null)throw new IllegalStateException("A raster operation is already active.");
    rasterFilter=Objects.requireNonNull(filter);
    try{return raster(sheet,dpi);}finally{rasterFilter=null;}
  }
}
