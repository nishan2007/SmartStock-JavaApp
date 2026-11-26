package gy.deckers.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import publishing.TextAlignment;

/** Portable, self-contained badge designs. Personal photos and record values are never serialized. */
public final class BadgeTemplate {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    static String portableJson(BadgeTemplate template){
        var json=new Gson().toJsonTree(template).getAsJsonObject();
        for(var value:portableElements(json)){var element=value.getAsJsonObject();for(String name:new String[]{"italic","underline","strike","textOutline"})if(!element.get(name).getAsBoolean())element.remove(name);if(!element.has("textOutline")){element.remove("textOutlineColor");element.remove("textOutlineWidth");}}
        // Default layer settings are implicit in older store servers' portable format.
        for(var value:portableElements(json)){var element=value.getAsJsonObject();if(element.get("rotation").getAsDouble()==0)element.remove("rotation");if(!element.get("hidden").getAsBoolean())element.remove("hidden");if(element.get("transparency").getAsInt()==0)element.remove("transparency");}
        return json.toString();
    }
    private static com.google.gson.JsonArray portableElements(com.google.gson.JsonObject json) {
        var all=new com.google.gson.JsonArray();all.addAll(json.getAsJsonArray("elements"));all.addAll(json.getAsJsonArray("backElements"));return all;
    }
    public boolean savePrintRecords;
    public int formatVersion = 1;
    public String name = "New badge";
    public publishing.StudioDriveIdentity smartDrive;
    public double widthMm = 85.6, heightMm = 54;
    public List<Element> elements = new ArrayList<>();
    public List<Element> backElements = new ArrayList<>();
    transient boolean editingBack;
    List<Element> pageElements() { return editingBack ? backElements : elements; }
    List<Element> allElements() { var all=new ArrayList<Element>(elements);all.addAll(backElements);return all; }
    void mergeBack(BadgeTemplate back) {
        if(Math.abs(widthMm-back.widthMm)>.01 || Math.abs(heightMm-back.heightMm)>.01)
            throw new IllegalArgumentException("Front and back must have the same dimensions and orientation.");
        back.validate();var previous=backElements;backElements=back.snapshot().elements;
        try{validate();}catch(RuntimeException ex){backElements=previous;throw ex;}
    }
    BufferedImage renderSide(boolean back, Map<String,String> values, BufferedImage photo, int dpi)throws IOException {
        boolean previous=editingBack;try{editingBack=back;return render(values,photo,dpi);}finally{editingBack=previous;}
    }

    public static final class Element {
        public int transparency;
        public boolean hidden;
        public String photoId=UUID.randomUUID().toString();
        transient BufferedImage personalPhoto;
        transient PhotoFraming photoFraming;
        public boolean photoBackgroundEnabled,photoOutlineEnabled;
        public String photoBackgroundColor="#ffffff",photoOutlineColor="#000000";
        PhotoFraming stylePhoto(PhotoFraming frame){return frame.styled(photoBackgroundEnabled?Color.decode(photoBackgroundColor):null,photoOutlineEnabled?Color.decode(photoOutlineColor):null);}
        public String label, type = "TEXT", text = "", field = "", font = "Arial", color = "#000000", image = "";
        public double x, y, width, height, rotation, fontMm = 3;
        public boolean bold,italic,underline,strike,textOutline;
        public TextAlignment textAlignment;
        public String textOutlineColor="#000000";
        public double textOutlineWidth=.2;
        private transient String decodedSource;
        private transient BufferedImage decodedArtwork;

        BufferedImage artwork() throws IOException {
            if (decodedArtwork == null || !Objects.equals(decodedSource, image)) {
                BufferedImage decoded = decode(image);
                decodedSource = image;
                decodedArtwork = decoded;
            }
            return decodedArtwork;
        }
    }

    public static BadgeTemplate builtin() throws IOException {
        try (InputStream in = BadgeTemplate.class.getResourceAsStream("badges/skeldon-primary.sstbadge")) {
            if (in == null) throw new IOException("The Skeldon Primary template is missing.");
            return read(in);
        }
    }

    public static BadgeTemplate load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            BadgeTemplate template=read(in);var identity=publishing.StudioDriveIdentity.fromFile(file);
            if(identity!=null){template.smartDrive=identity;template.validate();}return template;
        }
    }

    static BadgeTemplate read(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Template exceeds the 8 MB limit.");
        try {
            var object = com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!object.has("formatVersion") || !object.has("elements") || !object.has("name") || !object.has("widthMm") || !object.has("heightMm"))
                throw new IllegalArgumentException("Missing required template properties.");
            BadgeTemplate template = JSON.fromJson(object, BadgeTemplate.class);
            if (template == null) throw new IllegalArgumentException("Empty template.");
            template.validate();
            OreallaRidersPermit.upgrade(template);
            OreallaRidersPermit.layoutFront(template);
            return template;
        } catch (RuntimeException ex) { throw new IOException("Invalid badge template: " + ex.getMessage(), ex); }
    }

    public void save(Path file) throws IOException {
        validate();
        var object = JSON.toJsonTree(this).getAsJsonObject();
        ByteArrayOutputStream preview = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(renderSide(false, Map.of(), null, 72), "png", preview);
        if(preview.size()<=1024*1024)object.addProperty("previewPng",java.util.Base64.getEncoder().encodeToString(preview.toByteArray()));
        byte[] bytes = JSON.toJson(object).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Template exceeds the 8 MB limit.");
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "badge-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public void validate() {
        if(smartDrive!=null){smartDrive.validate();if(!"BADGE".equals(smartDrive.kind))throw new IllegalArgumentException("Invalid badge identity category.");}
        if (formatVersion != 1) throw new IllegalArgumentException("Unsupported template version.");
        if (name == null || name.isBlank() || name.length() > 160) throw new IllegalArgumentException("Enter a template name (up to 160 characters).");
        if (!finite(widthMm, 10, 300) || !finite(heightMm, 10, 300)) throw new IllegalArgumentException("Card dimensions must be 10–300 mm.");
        if (elements == null || backElements == null || elements.size() > 64 || backElements.size() > 64) throw new IllegalArgumentException("A template supports up to 64 elements.");
        for (Element e : allElements()) {
            if (e == null || e.label == null || e.label.isBlank() || e.label.length() > 160
                    || !Set.of("TEXT", "IMAGE", "PHOTO", "QR", "RECT", "ELLIPSE", "TRIANGLE", "LINE").contains(e.type == null ? "" : e.type)) throw new IllegalArgumentException("Invalid element.");
            if (!finite(e.x, -1000, 1000) || !finite(e.y, -1000, 1000) || !finite(e.rotation,-360,360) || !finite(e.width, .01,Math.abs(Math.sin(Math.toRadians(e.rotation)))>.001?Math.max(widthMm,heightMm):widthMm)
                    || !finite(e.height, .01,Math.abs(Math.sin(Math.toRadians(e.rotation)))>.001?Math.max(widthMm,heightMm):heightMm))
                throw new IllegalArgumentException(e.label + " has invalid workspace coordinates or dimensions.");
            if (e.text == null || e.text.length() > 1000 || e.field == null || !e.field.matches("[A-Za-z][A-Za-z0-9_]{0,39}|"))
                throw new IllegalArgumentException("Invalid text or field name.");
            if ("QR".equals(e.type) && e.field.isEmpty()) throw new IllegalArgumentException("A QR layer requires a data field.");
            if (e.font == null || e.font.length() > 100 || !finite(e.fontMm, .5, 50) || e.color == null || !e.color.matches("#[0-9a-fA-F]{6}"))
                throw new IllegalArgumentException("Invalid font or colour.");
            if(e.textOutlineColor==null||!e.textOutlineColor.matches("#[0-9a-fA-F]{6}")||!finite(e.textOutlineWidth,0,5))throw new IllegalArgumentException("Invalid text outline.");
            if(e.transparency<0||e.transparency>100)throw new IllegalArgumentException("Invalid image transparency.");
            if (e.image == null) throw new IllegalArgumentException("Invalid image.");
            if(e.photoBackgroundColor==null||!e.photoBackgroundColor.matches("#[0-9a-fA-F]{6}")||e.photoOutlineColor==null||!e.photoOutlineColor.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid photo background or outline colour.");
            if ("IMAGE".equals(e.type)) try { e.artwork(); } catch (IOException ex) { throw new IllegalArgumentException("Invalid artwork for " + e.label, ex); }
            else if (!e.image.isEmpty()) throw new IllegalArgumentException("Only reusable artwork can be saved in a template.");
        }
    }

    private static boolean finite(double n, double min, double max) { return Double.isFinite(n) && n >= min && n <= max; }

    BadgeTemplate snapshot() { var copy=JSON.fromJson(JSON.toJson(this), BadgeTemplate.class);copy.editingBack=editingBack;return copy; }

    static BufferedImage decode(String encoded) throws IOException {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null) throw new IOException("Unreadable artwork.");
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Use PNG or JPEG artwork.");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w < 1 || h < 1 || (long) w * h > 12_000_000) throw new IOException("Artwork exceeds 12 megapixels.");
                    return reader.read(0);
                } finally { reader.dispose(); }
            }
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid artwork encoding.", ex); }
    }

    public BufferedImage render(Map<String, String> values, BufferedImage photo, int dpi) throws IOException {
        if (dpi < 72 || dpi > 600) throw new IllegalArgumentException("Use 72–600 DPI.");
        int w = (int)Math.round(widthMm * dpi / 25.4), h = (int)Math.round(heightMm * dpi / 25.4);
        if ((long)w * h > 12_000_000) throw new IOException("Rendered card exceeds 12 megapixels.");
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintElements(g, values, photo, dpi / 25.4);
        } finally { g.dispose(); }
        return output;
    }

    void paintElements(Graphics2D g, Map<String, String> values, BufferedImage photo, double scale) throws IOException {
            boolean oreallaFront = !editingBack && OreallaRidersPermit.usesFrontLayout(this);
            for (Element e : pageElements()) {
                if(e.hidden)continue;
                int x=(int)Math.round(e.x*scale), y=(int)Math.round(e.y*scale), ew=(int)Math.round(e.width*scale), eh=(int)Math.round(e.height*scale);
                Graphics2D layer=(Graphics2D)g.create();
                try {
                    layer.rotate(Math.toRadians(e.rotation),x+ew/2.,y+eh/2.);
                    layer.clipRect(x,y,ew,eh);
                    if(Set.of("IMAGE","PHOTO").contains(e.type))layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1-e.transparency/100f));
                    if (Set.of("RECT","ELLIPSE","TRIANGLE","LINE").contains(e.type)) {
                        layer.setColor(Color.decode(e.color));
                        switch(e.type) {
                            case "RECT" -> layer.fillRect(x,y,ew,eh);
                            case "ELLIPSE" -> layer.fillOval(x,y,ew,eh);
                            case "TRIANGLE" -> layer.fillPolygon(new int[]{x+ew/2,x+ew,x},new int[]{y,y+eh,y+eh},3);
                            case "LINE" -> layer.fillRect(x,y,ew,Math.max(1,eh));
                        }
                    }
                    else if ("IMAGE".equals(e.type)) layer.drawImage(e.artwork(),x,y,ew,eh,null);
                    else if ("QR".equals(e.type)) {
                        String value=values.getOrDefault(e.field,"");
                        if(!value.isBlank()) paintQr(layer,value,x,y,ew,eh);
                    }
                    else if ("PHOTO".equals(e.type)) {
                        BufferedImage slotPhoto=e.personalPhoto!=null?e.personalPhoto:photo;
                        if (slotPhoto == null) {
                            layer.setColor(new Color(235,237,240)); layer.fillRect(x,y,ew,eh);
                            layer.setColor(Color.GRAY); layer.setFont(new Font("SansSerif",Font.PLAIN,Math.max(10,ew/8)));
                            String label="Photo"; layer.drawString(label,x+(ew-layer.getFontMetrics().stringWidth(label))/2,y+eh/2);
                        } else {
                            PhotoFraming framing=e.photoFraming!=null&&e.photoFraming.source()==slotPhoto?e.photoFraming:PhotoFraming.initial(slotPhoto);
                            (OreallaRidersPermit.enabled(this)?framing:e.stylePhoto(framing)).paint(layer,x,y,ew,eh);
                        }
                    } else {
                        String text=e.field.isEmpty()?e.text:values.getOrDefault(e.field,e.text);
                        Font font=publishing.PublicationFonts.create(e.font,(e.bold?Font.BOLD:0)|(e.italic?Font.ITALIC:0),(float)(e.fontMm*scale));
                        layer.setFont(font);
                        boolean permitText = oreallaFront && OreallaRidersPermit.usesTypography(e);
                        TextAlignment alignment = e.textAlignment == null
                                ? defaultTextAlignment(e) : e.textAlignment;
                        if (!permitText) {
                            int tw=layer.getFontMetrics().stringWidth(text);
                            if (tw > ew) layer.setFont(font.deriveFont((float)(font.getSize2D()*ew/tw)));
                        }
                        FontMetrics metrics=layer.getFontMetrics();
                        float lineWidth=metrics.stringWidth(text);
                        float tx=x+(float)alignment.offset(ew,lineWidth),baseline=y+(eh-metrics.getHeight())/2f+metrics.getAscent();
                        Shape glyph;
                        if (permitText) {
                            glyph = OreallaRidersPermit.fitText(layer, font, text,
                                    alignment, !e.field.isEmpty(), x, y, ew, eh);
                            var bounds=glyph.getBounds2D();
                            tx=(float)bounds.getX();
                            lineWidth=(float)bounds.getWidth();
                        } else {
                            glyph=layer.getFont().createGlyphVector(layer.getFontRenderContext(),text).getOutline(tx,baseline);
                        }
                        layer.setColor(Color.decode(e.color));
                        layer.fill(glyph);
                        if(e.textOutline&&e.textOutlineWidth>0){layer.setColor(Color.decode(e.textOutlineColor));layer.setStroke(new BasicStroke((float)(e.textOutlineWidth*scale),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));layer.draw(glyph);}
                        layer.setColor(Color.decode(e.color));layer.setStroke(new BasicStroke(Math.max(1,layer.getFont().getSize2D()/18)));if(e.underline)layer.draw(new java.awt.geom.Line2D.Float(tx,baseline+layer.getFont().getSize2D()*.1f,tx+lineWidth,baseline+layer.getFont().getSize2D()*.1f));if(e.strike)layer.draw(new java.awt.geom.Line2D.Float(tx,baseline-layer.getFont().getSize2D()*.3f,tx+lineWidth,baseline-layer.getFont().getSize2D()*.3f));
                    }
                } finally { layer.dispose(); }
            }
    }
    TextAlignment defaultTextAlignment(Element e) {
        return !editingBack && OreallaRidersPermit.usesFrontLayout(this) && OreallaRidersPermit.usesLeftAlignment(e)
                ? TextAlignment.LEFT : TextAlignment.CENTER;
    }
    private static void paintQr(Graphics2D graphics,String value,int x,int y,int width,int height)throws IOException {
        try {
            var matrix=publishing.PublicationBarcode.encode(value,"QR_CODE");
            int size=Math.min(width,height);
            int left=x+(width-size)/2,top=y+(height-size)/2,modules=matrix.getWidth();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(left,top,size,size);
            graphics.setColor(Color.BLACK);
            for(int row=0;row<modules;row++) for(int col=0;col<modules;col++) {
                if(matrix.get(col,row)) {
                    int px=col*size/modules,py=row*size/modules;
                    graphics.fillRect(left+px,top+py,(col+1)*size/modules-px,(row+1)*size/modules-py);
                }
            }
        } catch(Exception ex) {throw new IOException("Could not render badge QR code.",ex);}
    }
}
