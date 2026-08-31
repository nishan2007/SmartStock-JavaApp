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
    private static final int RENDER_SCALE = 220;
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
        int width = Math.max(220, (int) Math.round(settings.widthInches() * RENDER_SCALE));
        int height = Math.max(120, (int) Math.round(settings.heightInches() * RENDER_SCALE));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, width, height); g.setColor(new Color(28,35,45)); g.drawRect(1,1,width-3,height-3);
            Map<String, Rectangle> layout = layoutRects(settings.layoutData());
            if (settings.showCompany()) drawLogo(g, CompanyCustomizationManager.loadCompanyLogo(CompanyCustomizationManager.loadReceiptSettings()), scale(layout.get("company"),width,height));
            if(settings.showName()) drawText(g, item.name(), scale(layout.get("name"),width,height), Font.BOLD, true);
            if(settings.showSize()&&!item.size().isBlank()) drawText(g, item.size(), scale(layout.get("size"),width,height), Font.PLAIN, false);
            if(settings.showDescription()&&!item.description().isBlank()) drawText(g, item.description(), scale(layout.get("description"),width,height), Font.PLAIN, true);
            if(settings.showPrice()) drawText(g, CurrencyFormatter.create(Locale.getDefault()).format(item.price()), scale(layout.get("price"),width,height), Font.BOLD, false);
            String code = !item.barcode().isBlank() ? item.barcode() : item.sku(); Rectangle barcode=scale(layout.get("barcode"),width,height);
            if(settings.showBarcode()&&!code.isBlank()){BufferedImage barcodeImage=barcodeImage(code,barcode.width,barcode.height);if(barcodeImage!=null)g.drawImage(barcodeImage,barcode.x,barcode.y,null);}
            if(settings.showSku()&&!item.sku().isBlank())drawText(g,"SKU: "+item.sku(),scale(layout.get("sku"),width,height),Font.PLAIN,false);
        } finally { g.dispose(); }
        return image;
    }

    public static LinkedHashMap<String, Rectangle> layoutRects(String value) { LinkedHashMap<String,Rectangle> r=defaultLayout(); if(value==null||value.isBlank())return r; for(String part:value.split(";")){String[] p=part.split(":");if(p.length!=2||!r.containsKey(p[0]))continue;String[] n=p[1].split(",");try{r.put(p[0],new Rectangle(Integer.parseInt(n[0]),Integer.parseInt(n[1]),Math.max(20,Integer.parseInt(n[2])),Math.max(20,Integer.parseInt(n[3]))));}catch(Exception ignored){}}return r; }
    public static String encodeLayout(Map<String,Rectangle> layout){StringBuilder out=new StringBuilder();for(var e:layout.entrySet()){if(!out.isEmpty())out.append(';');Rectangle r=e.getValue();out.append(e.getKey()).append(':').append(r.x).append(',').append(r.y).append(',').append(r.width).append(',').append(r.height);}return out.toString();}
    public static LinkedHashMap<String,Rectangle> defaultLayout(){LinkedHashMap<String,Rectangle> r=new LinkedHashMap<>();r.put("company",new Rectangle(45,25,350,65));r.put("name",new Rectangle(45,105,600,100));r.put("size",new Rectangle(45,215,260,45));r.put("description",new Rectangle(45,265,600,55));r.put("price",new Rectangle(720,80,235,130));r.put("barcode",new Rectangle(45,340,700,120));r.put("sku",new Rectangle(760,365,195,65));return r;}
    private static Rectangle scale(Rectangle r,int w,int h){return new Rectangle(r.x*w/LAYOUT_WIDTH,r.y*h/LAYOUT_HEIGHT,Math.max(1,r.width*w/LAYOUT_WIDTH),Math.max(1,r.height*h/LAYOUT_HEIGHT));}
    private static void drawText(Graphics2D g,String value,Rectangle r,int style,boolean wrap){int size=Math.max(8,r.height);Font font=new Font("SansSerif",style,size);if(!wrap){while(size>8){font=new Font("SansSerif",style,size);if(g.getFontMetrics(font).stringWidth(value)<=r.width)break;size--;}}else{size=Math.max(8,Math.min(size,r.width/Math.max(1,value.length()/2)));font=new Font("SansSerif",style,size);}g.setFont(font);g.setColor(Color.BLACK);if(wrap)drawWrapped(g,value,r.x,r.y,r.width,Math.max(1,r.height/g.getFontMetrics().getHeight()));else g.drawString(value,r.x,r.y+Math.min(r.height,g.getFontMetrics().getAscent()));}
    private static void drawLogo(Graphics2D g, BufferedImage logo, Rectangle r){if(logo==null)return;double scale=Math.min((double)r.width/logo.getWidth(),(double)r.height/logo.getHeight());int w=(int)(logo.getWidth()*scale),h=(int)(logo.getHeight()*scale);g.drawImage(logo,r.x+(r.width-w)/2,r.y+(r.height-h)/2,w,h,null);}

    public static void preview(Component parent, PriceTagItem item, CompanyCustomizationManager.PriceTagTemplateSettings settings) {
        BufferedImage rendered = render(item, settings);
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

    public static void print(Component parent, List<PriceTagItem> items, CompanyCustomizationManager.PriceTagTemplateSettings settings) throws Exception {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Add at least one price tag before printing.");
        List<BufferedImage> images = new ArrayList<>(); for (PriceTagItem item : items) images.add(render(item, settings));
        PrinterJob job = PrinterJob.getPrinterJob(); job.setJobName("SmartStock Price Tags");
        PageFormat page = pageFormat(job, settings); job.setPrintable(new TagsPrintable(images), page);
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet(); attrs.add(new MediaPrintableArea(0, 0, (float) settings.widthInches(), (float) settings.heightInches(), MediaPrintableArea.INCH));
        if (job.printDialog(attrs)) job.print(attrs);
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
        if (printer == null) throw new PrintException("No receipt printer is configured.");
        if (printer.printFormat() != HardwareSettingsManager.PrintFormat.RECEIPT_40) {
            throw new PrintException("The configured receipt printer is not a 40-column receipt printer.");
        }
        PrintService service = HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) {
            throw new PrintException("Configured receipt printer is unavailable: " + printer.displayName());
        }
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

    private static PageFormat pageFormat(PrinterJob job, CompanyCustomizationManager.PriceTagTemplateSettings s) {
        Paper paper = new Paper(); double w = s.widthInches() * 72, h = s.heightInches() * 72; paper.setSize(w, h); paper.setImageableArea(0, 0, w, h);
        PageFormat page = job.defaultPage(); page.setPaper(paper); return page;
    }
    private static BufferedImage barcodeImage(String value, int width, int height) { try { BitMatrix matrix = new Code128Writer().encode(value, BarcodeFormat.CODE_128, width, height); BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); for (int x=0;x<width;x++) for (int y=0;y<height;y++) image.setRGB(x,y,matrix.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB()); return image; } catch (Exception ignored) { return null; } }
    private static int drawWrapped(Graphics2D g, String text, int x, int y, int width, int maxLines) { FontMetrics fm=g.getFontMetrics(); String[] words=text.split("\\s+"); String line=""; int lines=0; for(String word:words){ String test=line.isEmpty()?word:line+" "+word; if(fm.stringWidth(test)>width&&!line.isEmpty()){ g.drawString(line,x,y+fm.getAscent()); y+=fm.getHeight(); line=word; if(++lines>=maxLines) break; } else line=test; } if(lines<maxLines&&!line.isEmpty()){g.drawString(line,x,y+fm.getAscent());y+=fm.getHeight();} return y; }
    private record TagsPrintable(List<BufferedImage> images) implements Printable { public int print(Graphics g, PageFormat pf, int index) { if(index<0||index>=images.size())return NO_SUCH_PAGE; Graphics2D g2=(Graphics2D)g.create(); try { g2.translate(pf.getImageableX(),pf.getImageableY()); g2.drawImage(images.get(index),0,0,(int)pf.getImageableWidth(),(int)pf.getImageableHeight(),null); } finally {g2.dispose();} return PAGE_EXISTS; } }
}
