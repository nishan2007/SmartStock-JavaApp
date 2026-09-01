package services;

import com.google.gson.Gson;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Presentation-only settings. No URLs, file paths, credentials, or executable placeholders. */
public record WalletBadgeTemplate(String company, String title, String background, String foreground,
                                  String labelColor, List<Field> fields, String logoPng, String thumbnailPng,
                                  int logoScale, int thumbnailScale, boolean employeePhoto, Poster poster) {
    public record Poster(String backgroundPng, String signaturePng, int signaturePercent) {
        public Poster {
            backgroundPng=Objects.requireNonNullElse(backgroundPng, "");
            signaturePng=Objects.requireNonNullElse(signaturePng, "");
            decodeArtwork(backgroundPng); decodeImage(signaturePng);
            if(signaturePercent<20||signaturePercent>100)throw new IllegalArgumentException("Signature size must be 20–100 percent.");
        }
        public boolean enabled(){return !backgroundPng.isEmpty()||!signaturePng.isEmpty();}
    }
    public WalletBadgeTemplate(String company,String title,String background,String foreground,String labelColor,
                               List<Field> fields,String logoPng,String thumbnailPng,int logoScale,int thumbnailScale,boolean employeePhoto) {
        this(company,title,background,foreground,labelColor,fields,logoPng,thumbnailPng,logoScale,thumbnailScale,employeePhoto,null);
    }
    private static final Gson JSON = new Gson();
    public static final Set<String> SOURCES = Set.of("NAME", "FIRST_NAME", "LAST_NAME", "USERNAME", "ROLE", "LOCATION", "EMAIL", "PHONE", "COMPANY", "ISSUED", "TEXT");
    public static final List<String> SECTIONS = List.of("primaryFields", "headerFields", "secondaryFields", "auxiliaryFields", "backFields");
    public record Field(boolean visible, String section, String source, String label, String text) { }

    public WalletBadgeTemplate {
        poster=poster==null?new Poster("","",100):poster;
        company = text(company, 100); title = text(title, 100);
        if (company.isBlank() || title.isBlank()) throw new IllegalArgumentException("Enter a company name and badge description.");
        for (String color : List.of(background, foreground, labelColor))
            if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Choose valid template colors.");
        fields = List.copyOf(fields);
        if (fields.size() > 24) throw new IllegalArgumentException("Use at most 24 fields.");
        Map<String, Integer> counts = new HashMap<>();
        for (Field f : fields) {
            if (!SECTIONS.contains(f.section()) || !SOURCES.contains(f.source()))
                throw new IllegalArgumentException("Unsupported Wallet field or employee information.");
            text(f.label(), 60); text(f.text(), 500);
            if (f.visible()) counts.merge(f.section(), 1, Integer::sum);
        }
        if (counts.getOrDefault("primaryFields", 0) > 1 || counts.getOrDefault("headerFields", 0) > 3
                || counts.getOrDefault("secondaryFields", 0) + counts.getOrDefault("auxiliaryFields", 0) > 4)
            throw new IllegalArgumentException("Wallet permits 1 primary, 3 header, and 4 combined secondary/auxiliary fields. Move extra fields to Details.");
        logoPng = Objects.requireNonNullElse(logoPng, ""); thumbnailPng = Objects.requireNonNullElse(thumbnailPng, "");
        decodeImage(logoPng); decodeImage(thumbnailPng);
        if (logoScale < 20 || logoScale > 100 || thumbnailScale < 20 || thumbnailScale > 100)
            throw new IllegalArgumentException("Image size must be between 20 and 100 percent of its Wallet slot.");
    }

    private static String text(String value, int max) {
        if (value == null || value.length() > max || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Template text is missing or too long.");
        return value;
    }

    public static WalletBadgeTemplate defaults() {
        return new WalletBadgeTemplate("SmartStock", "Employee Badge", "#003760", "#FFFFFF", "#FFB570",
                List.of(new Field(true, "primaryFields", "NAME", "EMPLOYEE", ""),
                        new Field(true, "secondaryFields", "ROLE", "ROLE", ""),
                        new Field(true, "secondaryFields", "LOCATION", "STORE", ""),
                        new Field(false, "auxiliaryFields", "USERNAME", "USERNAME", ""),
                        new Field(false, "backFields", "ISSUED", "ISSUED", "")), "", "", 100, 100, false);
    }
    public String json() { return JSON.toJson(this); }
    public static WalletBadgeTemplate parse(String json) {
        if (json == null || json.isBlank()) return defaults();
        if (json.length() > 2_100_000) throw new IllegalArgumentException("Wallet template is too large.");
        WalletBadgeTemplate result = JSON.fromJson(json, WalletBadgeTemplate.class);
        if (result == null) throw new IllegalArgumentException("Wallet template is missing.");
        return result;
    }
    public Map<String, Object> passFields(Map<String, String> employee) {
        Map<String, Object> result = new LinkedHashMap<>();
        int index = 0;
        for (String section : SECTIONS) {
            List<Map<String, String>> output = new ArrayList<>();
            for (Field f : fields) if (f.visible() && f.section().equals(section)) {
                String value = f.source().equals("TEXT") ? f.text() : f.source().equals("COMPANY") ? company
                        : employee.getOrDefault(f.source(), "");
                output.add(Map.of("key", "field" + index++, "label", f.label(), "value", value));
            }
            result.put(section, output);
        }
        return result;
    }
    public static String rgb(String hex) {
        Color c = Color.decode(hex); return "rgb(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + ")";
    }
    public static BufferedImage decodeArtwork(String encoded) {
        if(encoded==null||encoded.isEmpty())return null;
        if(encoded.length()>1_500_000)throw new IllegalArgumentException("Background image is too large.");
        try {
            byte[] bytes=Base64.getDecoder().decode(encoded);
            if(bytes.length<8||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71)throw new IOException("Use PNG artwork.");
            BufferedImage image=readImage(bytes,1074L*1344);
            if(image.getWidth()>1074||image.getHeight()>1344)throw new IOException("Artwork exceeds Wallet dimensions.");
            return image;
        }catch(Exception ex){throw new IllegalArgumentException("Invalid Wallet background image.",ex);}
    }
    public static BufferedImage decodeImage(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        if (encoded.length() > 250_000) throw new IllegalArgumentException("Image is too large; import a smaller image.");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 8 || bytes[0] != (byte)137 || bytes[1] != 80 || bytes[2] != 78 || bytes[3] != 71)
                throw new IOException("Template images must be PNG.");
            BufferedImage image = readImage(bytes, 480L * 480);
            if (image.getWidth() > 480 || image.getHeight() > 480) throw new IOException("Template images must be normalized before saving.");
            return image;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Use a valid PNG image within the size limit.", e);
        }
    }
    public static BufferedImage readImage(byte[] bytes) throws IOException {
        return readImage(bytes, 16_000_000);
    }
    private static BufferedImage readImage(byte[] bytes, long pixelLimit) throws IOException {
        if (bytes.length > 10_000_000) throw new IOException("Image file exceeds 10 MB.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if ((long)reader.getWidth(0) * reader.getHeight(0) > pixelLimit)
                    throw new IOException("Image exceeds the permitted pixel dimensions.");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
    public static byte[] image(BufferedImage source, int width, int height, int percent) throws IOException {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale = Math.min((double)width / source.getWidth(), (double)height / source.getHeight()) * percent / 100.0;
            int w = Math.max(1, (int)(source.getWidth() * scale)), h = Math.max(1, (int)(source.getHeight() * scale));
            g.drawImage(source, (width-w)/2, (height-h)/2, w, h, null);
        } finally { g.dispose(); }
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(result, "png", out); return out.toByteArray();
    }
}
