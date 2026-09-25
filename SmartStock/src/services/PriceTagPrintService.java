package services;

import utils.CurrencyFormatter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import Receipt.NativeEscPosTransport;

import javax.print.DocFlavor;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Renders the exact same label image for the Company Preferences preview and printer output. */
public final class PriceTagPrintService {
    private static final int RENDER_SCALE = 203;
    public static final int LAYOUT_WIDTH = 1000;
    public static final int LAYOUT_HEIGHT = 500;
    private PriceTagPrintService() { }

    public record PriceTagItem(String name, String size, String description, String sku, String barcode, BigDecimal price) {
        public PriceTagItem {
            name = name == null ? "Item" : name.trim(); size = size == null ? "" : size.trim(); description = description == null ? "" : description.trim(); sku = sku == null ? "" : sku.trim(); barcode = barcode == null ? "" : barcode.trim();
            price = price == null ? BigDecimal.ZERO : price;
        }
    }

    public static BufferedImage render(PriceTagItem item, CompanyCustomizationManager.PriceTagTemplateSettings settings) {
        int width = Math.max(1, (int) Math.round(settings.widthInches() * RENDER_SCALE));
        int height = Math.max(1, (int) Math.round(settings.heightInches() * RENDER_SCALE));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, width, height);
            Map<String, Rectangle> layout = layoutRects(settings.layoutData());
            for (var entry : layout.entrySet()) {
                String id = entry.getKey();
                if (!elementVisible(settings, id)) continue;
                Rectangle box = scale(entry.getValue(), width, height);
                int angle = elementRotation(settings.layoutData(), id);
                String barcodeValue = "";
                if (id.equals("barcode")) {
                    barcodeValue = !item.barcode().isBlank() ? item.barcode() : item.sku();
                    angle = barcodeRotationThatFits(barcodeValue, box, angle);
                }
                Graphics2D element = (Graphics2D) g.create();
                try {
                    element.clip(box);
                    element.translate(box.x + box.width / 2.0, box.y + box.height / 2.0);
                    element.rotate(Math.toRadians(angle));
                    int w = angle % 180 == 0 ? box.width : box.height;
                    int h = angle % 180 == 0 ? box.height : box.width;
                    element.translate(-w / 2.0, -h / 2.0);
                    Rectangle area = new Rectangle(0, 0, w, h);
                    if (id.equals("company")) {
                        drawLogo(element, CompanyCustomizationManager.loadCompanyLogo(CompanyCustomizationManager.loadReceiptSettings()), area);
                    } else if (id.equals("barcode")) {
                        if (!barcodeValue.isBlank()) element.drawImage(barcodeImage(barcodeValue, w, h), 0, 0, null);
                    } else {
                        String value = switch (id) {
                            case "name" -> item.name(); case "size" -> item.size();
                            case "description" -> item.description();
                            case "price" -> formatPriceTagPrice(item.price(), Locale.getDefault());
                            case "sku" -> item.sku(); default -> "";
                        };
                        drawText(element, value, area, id.equals("price") || id.equals("name") ? Font.BOLD : Font.PLAIN, false);
                    }
                } finally { element.dispose(); }
            }
        } finally { g.dispose(); }
        return image;
    }

    public static LinkedHashMap<String, Rectangle> layoutRects(String value) { LinkedHashMap<String,Rectangle> r=defaultLayout(); if(value==null||value.isBlank())return r; for(String part:value.split(";")){String[] p=part.split(":");if(p.length!=2||!r.containsKey(p[0]))continue;String[] n=p[1].split(",");try{r.put(p[0],new Rectangle(Integer.parseInt(n[0]),Integer.parseInt(n[1]),Math.max(20,Integer.parseInt(n[2])),Math.max(20,Integer.parseInt(n[3]))));}catch(Exception ignored){}}return r; }
    public static String encodeLayout(Map<String,Rectangle> layout){StringBuilder out=new StringBuilder();for(var e:layout.entrySet()){if(!out.isEmpty())out.append(';');Rectangle r=e.getValue();out.append(e.getKey()).append(':').append(r.x).append(',').append(r.y).append(',').append(r.width).append(',').append(r.height);}return out.toString();}
    public static boolean elementVisible(CompanyCustomizationManager.PriceTagTemplateSettings s, String id) {
        return switch (id) {
            case "company" -> s.showCompany(); case "name" -> s.showName(); case "size" -> s.showSize();
            case "description" -> s.showDescription(); case "price" -> s.showPrice();
            case "barcode" -> s.showBarcode(); case "sku" -> s.showSku(); default -> false;
        };
    }
    public static int elementRotation(String layout, String id) {
        for (String part : layout.split(";")) if (part.startsWith(id + ":")) {
            String[] fields = part.substring(part.indexOf(':') + 1).split(",");
            try { return fields.length > 4 ? Math.floorMod(Integer.parseInt(fields[4]) / 90 * 90, 360) : 0; }
            catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }
    public static String encodeLayout(Map<String, Rectangle> layout, Map<String, Integer> rotations) {
        return layout.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue().x + "," + e.getValue().y
                + "," + e.getValue().width + "," + e.getValue().height + "," + rotations.getOrDefault(e.getKey(), 0))
                .collect(java.util.stream.Collectors.joining(";"));
    }
    public static CompanyCustomizationManager.PriceTagTemplateSettings ct221bPortraitTemplate(double rowGap) {
        return new CompanyCustomizationManager.PriceTagTemplateSettings("CT221B Portrait — two across", false, true,
                true, true, true, false, false, .5, 1,
                "name:60,20,880,55,0;price:60,80,880,60,0;barcode:80,150,840,310,90;sku:60,465,880,25,0",
                2, 0, rowGap);
    }
    public static LinkedHashMap<String,Rectangle> defaultLayout(){LinkedHashMap<String,Rectangle> r=new LinkedHashMap<>();r.put("company",new Rectangle(45,25,350,65));r.put("name",new Rectangle(45,105,600,100));r.put("size",new Rectangle(45,215,260,45));r.put("description",new Rectangle(45,265,600,55));r.put("price",new Rectangle(720,80,235,130));r.put("barcode",new Rectangle(45,340,700,120));r.put("sku",new Rectangle(760,365,195,65));return r;}
    private static Rectangle scale(Rectangle r,int w,int h){return new Rectangle(r.x*w/LAYOUT_WIDTH,r.y*h/LAYOUT_HEIGHT,Math.max(1,r.width*w/LAYOUT_WIDTH),Math.max(1,r.height*h/LAYOUT_HEIGHT));}
    private static void drawText(Graphics2D g,String value,Rectangle r,int style,boolean wrap){int size=Math.max(8,r.height);Font font=new Font("SansSerif",style,size);if(!wrap){while(size>8){font=new Font("SansSerif",style,size);if(g.getFontMetrics(font).stringWidth(value)<=r.width)break;size--;}}else{size=Math.max(8,Math.min(size,r.width/Math.max(1,value.length()/2)));font=new Font("SansSerif",style,size);}g.setFont(font);g.setColor(Color.BLACK);if(wrap)drawWrapped(g,value,r.x,r.y,r.width,Math.max(1,r.height/g.getFontMetrics().getHeight()));else g.drawString(value,r.x,r.y+Math.min(r.height,g.getFontMetrics().getAscent()));}
    private static void drawLogo(Graphics2D g, BufferedImage logo, Rectangle r){if(logo==null)return;double scale=Math.min((double)r.width/logo.getWidth(),(double)r.height/logo.getHeight());int w=(int)(logo.getWidth()*scale),h=(int)(logo.getHeight()*scale);g.drawImage(logo,r.x+(r.width-w)/2,r.y+(r.height-h)/2,w,h,null);}

    public static void preview(Component parent, PriceTagItem item, CompanyCustomizationManager.PriceTagTemplateSettings settings) {
        BufferedImage rendered = renderStockPreview(item, settings);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Price Tag Preview", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(10, 10)); dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel label = new JLabel(); label.setHorizontalAlignment(SwingConstants.CENTER);
        JScrollPane pane = new JScrollPane(label); pane.setPreferredSize(new Dimension(560, 360)); dialog.add(pane, BorderLayout.CENTER);
        int screenDpi = Toolkit.getDefaultToolkit().getScreenResolution(); double actualScale = (screenDpi / (double) RENDER_SCALE); final double[] zoom = {actualScale};
        Runnable refresh = () -> { int w=Math.max(1,(int)Math.round(rendered.getWidth()*zoom[0])); int h=Math.max(1,(int)Math.round(rendered.getHeight()*zoom[0])); Image scaled=rendered.getScaledInstance(w,h,Image.SCALE_SMOOTH); label.setIcon(new ImageIcon(scaled)); label.setPreferredSize(new Dimension(w,h)); label.revalidate(); };
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); JLabel scaleLabel = new JLabel(); JButton out = new JButton("−"); JButton actual = new JButton("Actual Size"); JButton in = new JButton("+"); JButton close = new JButton("Close"); controls.add(scaleLabel); controls.add(out); controls.add(actual); controls.add(in); controls.add(close); dialog.add(controls, BorderLayout.SOUTH);
        Runnable update = () -> { refresh.run(); scaleLabel.setText(String.format("%.0f%%", zoom[0] / actualScale * 100)); };
        out.addActionListener(e -> { zoom[0]=Math.max(actualScale*.25, zoom[0]/1.25); update.run(); }); in.addActionListener(e -> { zoom[0]=Math.min(actualScale*8, zoom[0]*1.25); update.run(); }); actual.addActionListener(e -> { zoom[0]=actualScale; update.run(); }); close.addActionListener(e -> dialog.dispose());
        update.run(); dialog.pack(); dialog.setLocationRelativeTo(parent); dialog.setVisible(true);
    }

    public static BufferedImage renderStockPreview(PriceTagItem item, CompanyCustomizationManager.PriceTagTemplateSettings settings) {
        BufferedImage label = render(item, settings);
        return arrangeRows(java.util.Collections.nCopies(settings.labelsAcross(), label), settings).get(0);
    }

    public static void print(Component parent, List<PriceTagItem> items, CompanyCustomizationManager.PriceTagTemplateSettings settings) throws Exception {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Add at least one price tag before printing.");
        List<BufferedImage> images = new ArrayList<>(); for (PriceTagItem item : items) images.add(render(item, settings));
        PrinterJob job = PrinterJob.getPrinterJob(); job.setJobName("SmartStock Price Tags");
        PageFormat page = pageFormat(job, settings); setTagPages(job, images, settings, page);
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet(); attrs.add(new MediaPrintableArea(0, 0, (float) settings.rowWidthInches(), (float) settings.rowPitchInches(), MediaPrintableArea.INCH));
        if (job.printDialog(attrs)) {
            page = pageFormat(job, settings);
            setTagPages(job, images, settings, page);
            job.print();
        }
    }

    public static String printOnConfiguredLabelOrReceiptPrinter(List<PriceTagItem> items,
                                                                 CompanyCustomizationManager.PriceTagTemplateSettings settings)
            throws Exception {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Select at least one item to print.");
        if (settings == null) throw new IllegalArgumentException("Select a price-tag template first.");
        HardwareSettingsManager.PosPrinter labelPrinter = HardwareSettingsManager.getDefaultOrderLabelPrinter();
        if (labelPrinter == null) return printOnReceiptPrinter(items, settings);
        PrintService service = HardwareSettingsManager.findPrintService(labelPrinter.systemName());
        if (service == null) throw new PrintException("Configured label printer is unavailable: " + labelPrinter.displayName());

        List<BufferedImage> images = new ArrayList<>();
        for (PriceTagItem item : items) images.add(render(item, settings));
        if (isCt221b(labelPrinter.systemName()) || isCt221b(service.getName())) {
            byte[] jobBytes = formatCt221bGapJob(arrangeRows(images, settings), settings);
            service.createPrintJob().print(new SimpleDoc(jobBytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
            return "Price tags submitted to CT221B label printer " + service.getName()
                    + " with gap-hole calibration.";
        }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("SmartStock Price Tags");
        job.setPrintService(service);
        PageFormat page = pageFormat(job, settings);
        setTagPages(job, images, settings, page);
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        attrs.add(new MediaPrintableArea(0, 0, (float) settings.rowWidthInches(),
                (float) settings.rowPitchInches(), MediaPrintableArea.INCH));
        job.print();
        return "Price tags submitted to label printer " + service.getName() + ".";
    }

    static boolean isCt221b(String name) {
        return name != null && name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT).contains("CT221B");
    }

    static String formatPriceTagPrice(BigDecimal value, Locale locale) {
        NumberFormat format = CurrencyFormatter.create(locale);
        format.setGroupingUsed(false);
        return format.format(CurrencyFormatter.normalize(value));
    }

    static byte[] formatCt221bGapJob(List<BufferedImage> rows,
                                     CompanyCustomizationManager.PriceTagTemplateSettings settings) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("At least one label row is required.");
        double widthMm = settings.rowWidthInches() * 25.4;
        double labelMm = settings.heightInches() * 25.4;
        double gapMm = settings.rowGapInches() * 25.4;
        if (gapMm <= 0) gapMm = 2.0;
        int labelDots = Math.max(1, (int) Math.round(settings.heightInches() * RENDER_SCALE));
        int gapDots = Math.max(1, (int) Math.round(gapMm / 25.4 * RENDER_SCALE));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendAscii(out, String.format(Locale.ROOT,
                "SIZE %.3f mm,%.3f mm\r\nGAP %.3f mm,0 mm\r\nGAPDETECT %d,%d\r\nDIRECTION 1\r\nREFERENCE 0,0\r\n",
                widthMm, labelMm, gapMm, labelDots, gapDots));
        for (BufferedImage row : rows) {
            appendAscii(out, "CLS\r\n");
            appendTsplBitmap(out, row);
            appendAscii(out, "\r\nPRINT 1,1\r\n");
        }
        return out.toByteArray();
    }

    private static void appendTsplBitmap(ByteArrayOutputStream out, BufferedImage image) {
        int bytesPerRow = (image.getWidth() + 7) / 8;
        appendAscii(out, "BITMAP 0,0," + bytesPerRow + "," + image.getHeight() + ",0,");
        for (int y = 0; y < image.getHeight(); y++) {
            for (int xByte = 0; xByte < bytesPerRow; xByte++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xByte * 8 + bit;
                    if (x >= image.getWidth()) continue;
                    Color color = new Color(image.getRGB(x, y));
                    double luminance = color.getRed() * .299 + color.getGreen() * .587 + color.getBlue() * .114;
                    if (luminance < 160) value |= 0x80 >> bit;
                }
                out.write(value);
            }
        }
    }

    private static void appendAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public static String printOnReceiptPrinter(List<PriceTagItem> items,
                                               CompanyCustomizationManager.PriceTagTemplateSettings settings)
            throws Exception {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one price tag before printing.");
        }
        if (settings == null) {
            throw new IllegalArgumentException("Select a price-tag template first.");
        }
        List<BufferedImage> images = new ArrayList<>();
        for (PriceTagItem item : items) images.add(fitForReceiptPrinter(render(item, settings), 384));
        byte[] jobBytes = formatReceiptPrinterJob(images);

        String endpoint = NativeEscPosTransport.sendIfEnabled(jobBytes);
        if (endpoint != null) return "Temporary price tags sent to Ethernet receipt printer " + endpoint + ".";

        HardwareSettingsManager.PosPrinter printer = HardwareSettingsManager.getDefaultReceiptPrinter();
        if (printer == null) throw new PrintException("No receipt printer is configured on the computer running SmartStock's New Item web app. Set its default receipt printer in Hardware Settings.");
        if (printer.printFormat() != HardwareSettingsManager.PrintFormat.RECEIPT_40) {
            throw new PrintException("The configured receipt printer is not a 40-column receipt printer.");
        }
        PrintService service = ReceiptPrinterDiscovery.resolve(printer.systemName());
        service.createPrintJob().print(new SimpleDoc(jobBytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
        return "Temporary price tags submitted to receipt printer " + service.getName() + ".";
    }

    static byte[] formatReceiptPrinterJob(List<BufferedImage> images) {
        if (images == null || images.isEmpty()) throw new IllegalArgumentException("At least one tag image is required.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (BufferedImage image : images) {
            if (image == null) throw new IllegalArgumentException("Tag image is required.");
            out.writeBytes(new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x01});
            appendEscPosRaster(out, image);
            out.writeBytes(new byte[]{0x0A, 0x1B, 0x64, 0x03, 0x1D, 0x56, 0x42, 0x00});
        }
        return out.toByteArray();
    }

    private static BufferedImage fitForReceiptPrinter(BufferedImage source, int maxWidth) {
        double scale = Math.min(1.0, maxWidth / (double) source.getWidth());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage fitted = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = fitted.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return fitted;
    }

    private static void appendEscPosRaster(ByteArrayOutputStream out, BufferedImage image) {
        int bytesPerRow = (image.getWidth() + 7) / 8;
        out.writeBytes(new byte[]{0x1D, 0x76, 0x30, 0x00,
                (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF),
                (byte) (image.getHeight() & 0xFF), (byte) ((image.getHeight() >> 8) & 0xFF)});
        for (int y = 0; y < image.getHeight(); y++) {
            for (int xByte = 0; xByte < bytesPerRow; xByte++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xByte * 8 + bit;
                    if (x >= image.getWidth()) continue;
                    Color color = new Color(image.getRGB(x, y));
                    double luminance = color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114;
                    if (luminance < 160) value |= 0x80 >> bit;
                }
                out.write(value);
            }
        }
    }

    static List<BufferedImage> arrangeRows(List<BufferedImage> labels, CompanyCustomizationManager.PriceTagTemplateSettings settings) {
        List<BufferedImage> rows = new ArrayList<>();
        int labelWidth = Math.max(1, (int) Math.round(settings.widthInches() * RENDER_SCALE));
        int height = Math.max(1, (int) Math.round(settings.heightInches() * RENDER_SCALE));
        int gap = (int) Math.round(settings.columnGapInches() * RENDER_SCALE);
        int rowWidth = labelWidth * settings.labelsAcross() + gap * (settings.labelsAcross() - 1);
        for (int first = 0; first < labels.size(); first += settings.labelsAcross()) {
            BufferedImage row = new BufferedImage(rowWidth, height + (int) Math.round(settings.rowGapInches() * RENDER_SCALE), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = row.createGraphics();
            try {
                g.setColor(Color.WHITE); g.fillRect(0, 0, rowWidth, row.getHeight());
                for (int column = 0; column < settings.labelsAcross() && first + column < labels.size(); column++) {
                    g.drawImage(labels.get(first + column), column * (labelWidth + gap), 0, labelWidth, height, null);
                }
            } finally { g.dispose(); }
            rows.add(row);
        }
        return rows;
    }

    private static PageFormat pageFormat(PrinterJob job, CompanyCustomizationManager.PriceTagTemplateSettings s) {
        Paper paper = new Paper(); double w = s.rowWidthInches() * 72, h = s.rowPitchInches() * 72; paper.setSize(w, h); paper.setImageableArea(0, 0, w, h);
        PageFormat page = job.defaultPage(); page.setOrientation(PageFormat.PORTRAIT); page.setPaper(paper); return page;
    }
    static BufferedImage barcodeImage(String value, int width, int height) {
        BitMatrix matrix = code128(value, width, height);
        if (matrix.getWidth() > width) throw new IllegalArgumentException(
                "Barcode " + value + " is too long for its label box. Rotate it 90 degrees, enlarge the box, or shorten the code.");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x=0;x<width;x++) for (int y=0;y<height;y++)
            image.setRGB(x,y,matrix.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB());
        return image;
    }
    static int barcodeRotationThatFits(String value, Rectangle box, int configuredAngle) {
        if (value == null || value.isBlank()) return configuredAngle;
        int minimumWidth = code128(value, 1, 1).getWidth();
        int normalized = Math.floorMod(configuredAngle / 90 * 90, 360);
        int configuredWidth = normalized % 180 == 0 ? box.width : box.height;
        int turned = Math.floorMod(normalized + 90, 360);
        int turnedWidth = turned % 180 == 0 ? box.width : box.height;
        if (turnedWidth > configuredWidth && minimumWidth <= turnedWidth) return turned;
        if (minimumWidth <= configuredWidth) return normalized;
        return minimumWidth <= turnedWidth ? turned : normalized;
    }
    private static BitMatrix code128(String value, int width, int height) {
        return new Code128Writer().encode(value, BarcodeFormat.CODE_128, width, height,
                Map.of(com.google.zxing.EncodeHintType.MARGIN, 0));
    }
    private static void setTagPages(PrinterJob job, List<BufferedImage> images,
            CompanyCustomizationManager.PriceTagTemplateSettings s, PageFormat page) {
        List<BufferedImage> rows = arrangeRows(images, s);
        java.awt.print.Book book = new java.awt.print.Book();
        book.append(new TagsPrintable(rows, s.rowWidthInches()*72, s.rowPitchInches()*72), page, rows.size());
        job.setPageable(book);
    }
    private static int drawWrapped(Graphics2D g, String text, int x, int y, int width, int maxLines) { FontMetrics fm=g.getFontMetrics(); String[] words=text.split("\\s+"); String line=""; int lines=0; for(String word:words){ String test=line.isEmpty()?word:line+" "+word; if(fm.stringWidth(test)>width&&!line.isEmpty()){ g.drawString(line,x,y+fm.getAscent()); y+=fm.getHeight(); line=word; if(++lines>=maxLines) break; } else line=test; } if(lines<maxLines&&!line.isEmpty()){g.drawString(line,x,y+fm.getAscent());y+=fm.getHeight();} return y; }
    static record TagsPrintable(List<BufferedImage> images, double widthPoints, double heightPoints) implements Printable {
        public int print(Graphics g, PageFormat pf, int index) throws java.awt.print.PrinterException {
            if(index<0||index>=images.size()) return NO_SUCH_PAGE;
            if (pf.getWidth() + .5 < widthPoints || pf.getHeight() + .5 < heightPoints)
                throw new java.awt.print.PrinterException("The printer paper is smaller than the template row. Check the selected stock.");
            Graphics2D g2=(Graphics2D)g.create();
            try {
                // Paper-origin coordinates: never stretch a label to the driver's imageable area.
                g2.drawImage(images.get(index), new java.awt.geom.AffineTransform(
                        widthPoints/images.get(index).getWidth(), 0, 0,
                        heightPoints/images.get(index).getHeight(), 0, 0), null);
            } finally {g2.dispose();}
            return PAGE_EXISTS;
        }
    }
}
