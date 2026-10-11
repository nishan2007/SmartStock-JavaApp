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

/** Portable, self-contained badge designs. Personal photos and record values are never serialized. */
public final class BadgeTemplate {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public int formatVersion = 1;
    public String name = "New badge";
    public double widthMm = 85.6, heightMm = 54;
    public List<Element> elements = new ArrayList<>();

    public static final class Element {
        public String label, type = "TEXT", text = "", field = "", font = "Arial", color = "#000000", image = "";
        public double x, y, width, height, fontMm = 3;
        public boolean bold;
    }

    public static BadgeTemplate builtin() throws IOException {
        try (InputStream in = BadgeTemplate.class.getResourceAsStream("badges/skeldon-primary.sstbadge")) {
            if (in == null) throw new IOException("The Skeldon Primary template is missing.");
            return read(in);
        }
    }

    public static BadgeTemplate load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) { return read(in); }
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
            return template;
        } catch (RuntimeException ex) { throw new IOException("Invalid badge template: " + ex.getMessage(), ex); }
    }

    public void save(Path file) throws IOException {
        validate();
        byte[] bytes = JSON.toJson(this).getBytes(StandardCharsets.UTF_8);
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
        if (formatVersion != 1) throw new IllegalArgumentException("Unsupported template version.");
        if (name == null || name.isBlank() || name.length() > 160) throw new IllegalArgumentException("Enter a template name (up to 160 characters).");
        if (!finite(widthMm, 10, 300) || !finite(heightMm, 10, 300)) throw new IllegalArgumentException("Card dimensions must be 10–300 mm.");
        if (elements == null || elements.size() > 64) throw new IllegalArgumentException("A template supports up to 64 elements.");
        for (Element e : elements) {
            if (e == null || e.label == null || e.label.isBlank() || e.label.length() > 160
                    || !Set.of("TEXT", "IMAGE", "PHOTO").contains(e.type == null ? "" : e.type)) throw new IllegalArgumentException("Invalid element.");
            if (!finite(e.x, -1000, 1000) || !finite(e.y, -1000, 1000) || !finite(e.width, .01, widthMm)
                    || !finite(e.height, .01, heightMm))
                throw new IllegalArgumentException(e.label + " has invalid workspace coordinates or dimensions.");
            if (e.text == null || e.text.length() > 1000 || e.field == null || !e.field.matches("[A-Za-z][A-Za-z0-9_]{0,39}|"))
                throw new IllegalArgumentException("Invalid text or field name.");
            if (e.font == null || e.font.length() > 100 || !finite(e.fontMm, .5, 50) || e.color == null || !e.color.matches("#[0-9a-fA-F]{6}"))
                throw new IllegalArgumentException("Invalid font or colour.");
            if (e.image == null) throw new IllegalArgumentException("Invalid image.");
            if ("IMAGE".equals(e.type)) try { decode(e.image); } catch (IOException ex) { throw new IllegalArgumentException("Invalid artwork for " + e.label, ex); }
            else if (!e.image.isEmpty()) throw new IllegalArgumentException("Only reusable artwork can be saved in a template.");
        }
    }

    private static boolean finite(double n, double min, double max) { return Double.isFinite(n) && n >= min && n <= max; }

    BadgeTemplate snapshot() { return JSON.fromJson(JSON.toJson(this), BadgeTemplate.class); }

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
            for (Element e : elements) {
                int x=(int)Math.round(e.x*scale), y=(int)Math.round(e.y*scale), ew=(int)Math.round(e.width*scale), eh=(int)Math.round(e.height*scale);
                Graphics2D layer=(Graphics2D)g.create();
                try {
                    layer.clipRect(x,y,ew,eh);
                    if ("IMAGE".equals(e.type)) layer.drawImage(decode(e.image),x,y,ew,eh,null);
                    else if ("PHOTO".equals(e.type)) {
                        if (photo == null) {
                            layer.setColor(new Color(235,237,240)); layer.fillRect(x,y,ew,eh);
                            layer.setColor(Color.GRAY); layer.setFont(new Font("SansSerif",Font.PLAIN,Math.max(10,ew/8)));
                            String label="Photo"; layer.drawString(label,x+(ew-layer.getFontMetrics().stringWidth(label))/2,y+eh/2);
                        } else {
                            double ratio=Math.max(ew/(double)photo.getWidth(),eh/(double)photo.getHeight());
                            int pw=(int)Math.ceil(photo.getWidth()*ratio),ph=(int)Math.ceil(photo.getHeight()*ratio);
                            layer.drawImage(photo,x+(ew-pw)/2,y+(eh-ph)/2,pw,ph,null);
                        }
                    } else {
                        String text=e.field.isEmpty()?e.text:values.getOrDefault(e.field,e.text);
                        Font font=new Font(e.font,e.bold?Font.BOLD:Font.PLAIN,1).deriveFont((float)(e.fontMm*scale));
                        layer.setFont(font);
                        int tw=layer.getFontMetrics().stringWidth(text);
                        if (tw > ew) layer.setFont(font.deriveFont((float)(font.getSize2D()*ew/tw)));
                        FontMetrics metrics=layer.getFontMetrics();
                        layer.setColor(Color.decode(e.color));
                        layer.drawString(text,x+(ew-metrics.stringWidth(text))/2,y+(eh-metrics.getHeight())/2+metrics.getAscent());
                    }
                } finally { layer.dispose(); }
            }
    }
}
