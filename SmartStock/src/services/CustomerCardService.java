package services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Sides;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import utils.ImageCacheManager;

public final class CustomerCardService {
    public static final int WIDTH=1013,HEIGHT=638;
    private static final Gson GSON=new Gson();
    private CustomerCardService(){}
    public record Template(String name,boolean configured,ColorData background,ColorData header,String backgroundImage,String layoutData){
        public Template(String name,boolean configured,ColorData background){this(name,configured,background,new ColorData(255,112,0),"","");}
        public Template(String name,boolean configured,ColorData background,String layoutData){this(name,configured,background,new ColorData(255,112,0),"",layoutData);}
    }
    private record LayoutPayload(int version,List<Template> templates){}
    public record ColorData(int red,int green,int blue){}
    public record CardData(int customerId,String name,String type,String accountNumber,String phone,String email,Integer customerSince,String photoUrl,LocalDate expiresOn,int templateSlot){
        public CardData(int customerId,String name,String type,String accountNumber,String phone,String email,int templateSlot){this(customerId,name,type,accountNumber,phone,email,null,"",null,templateSlot);}
        public CardData(int customerId,String name,String type,String accountNumber,String phone,String email,Integer customerSince,int templateSlot){this(customerId,name,type,accountNumber,phone,email,customerSince,"",null,templateSlot);}
    }
    public static List<Template> defaults(){return List.of(
            new Template("Teachers",true,new ColorData(239,246,255)),new Template("Business",true,new ColorData(255,247,237)),
            new Template("School",true,new ColorData(240,253,244)),new Template("Individual",true,new ColorData(250,245,255)),
            new Template("Government",true,new ColorData(241,245,249),new ColorData(15,76,92),"","") );}
    public static List<Template> load(){try{String raw=CompanyCustomizationManager.loadCustomerCardTemplates();if(raw==null||raw.isBlank())return defaults();LayoutPayload payload=GSON.fromJson(raw,LayoutPayload.class);List<Template>x=payload==null?null:payload.templates();return payload.version()!=1||x==null||x.size()!=5?defaults():x;}catch(Exception e){return defaults();}}
    public static void save(List<Template> templates)throws Exception{if(templates==null||templates.size()!=5)throw new IllegalArgumentException("Five customer card templates are required.");CompanyCustomizationManager.saveCustomerCardTemplates(GSON.toJson(new LayoutPayload(1,templates)));}
    public static LinkedHashMap<String,Rectangle> layoutRects(String raw){
        LinkedHashMap<String,Rectangle> defaults=new LinkedHashMap<>();
        defaults.put("header",new Rectangle(72,62,865,55));defaults.put("name",new Rectangle(72,160,865,58));
        defaults.put("type",new Rectangle(72,225,865,36));defaults.put("account",new Rectangle(72,267,865,36));
        defaults.put("phone",new Rectangle(72,309,865,36));defaults.put("email",new Rectangle(72,351,865,36));
        defaults.put("since",new Rectangle(620,351,317,36));defaults.put("barcode",new Rectangle(72,410,865,125));defaults.put("barcodeText",new Rectangle(72,545,865,28));
        defaults.put("photo",new Rectangle(735,150,180,180));defaults.put("expiry",new Rectangle(620,385,317,32));
        if(raw==null||raw.isBlank())return defaults;
        try{Map<String,Rectangle> saved=GSON.fromJson(raw,new TypeToken<Map<String,Rectangle>>(){}.getType());if(saved!=null)for(var e:defaults.entrySet()){Rectangle r=saved.get(e.getKey());if(r!=null)e.setValue(clamp(r));}}catch(Exception ignored){}
        return defaults;
    }
    public static String updateLayoutRect(String raw,String id,Rectangle rectangle){LinkedHashMap<String,Rectangle> rects=layoutRects(raw);if(rects.containsKey(id))rects.put(id,clamp(rectangle));return GSON.toJson(rects);}
    private static Rectangle clamp(Rectangle value){int w=Math.max(35,Math.min(WIDTH,value.width)),h=Math.max(20,Math.min(HEIGHT,value.height));return new Rectangle(Math.max(0,Math.min(WIDTH-w,value.x)),Math.max(0,Math.min(HEIGHT-h,value.y)),w,h);}
    public static BufferedImage render(CardData d,Template t){if(t==null||!t.configured())throw new IllegalStateException("The selected customer card template is blank.");BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);ColorData c=t.background();g.setColor(new Color(c.red(),c.green(),c.blue()));g.fillRect(0,0,WIDTH,HEIGHT);paintBackgroundImage(g,t.backgroundImage());g.setColor(new Color(0,55,96));g.fillRoundRect(28,28,WIDTH-56,HEIGHT-56,34,34);g.setColor(new Color(255,255,255,225));g.fillRoundRect(42,42,WIDTH-84,HEIGHT-84,26,26);ColorData h=t.header()==null?new ColorData(255,112,0):t.header();g.setColor(new Color(h.red(),h.green(),h.blue()));g.fillRect(42,42,WIDTH-84,92);var r=layoutRects(t.layoutData());drawText(g,"DECKERS  •  "+t.name(),r.get("header"),Color.WHITE,Font.BOLD,38);drawText(g,clean(d.name()),r.get("name"),new Color(31,41,55),Font.BOLD,44);drawText(g,"Customer Type: "+clean(d.type()),r.get("type"),new Color(31,41,55),Font.PLAIN,25);drawText(g,"Account #: "+clean(d.accountNumber()),r.get("account"),new Color(31,41,55),Font.PLAIN,25);drawText(g,"Phone: "+clean(d.phone()),r.get("phone"),new Color(31,41,55),Font.PLAIN,25);drawText(g,"Email: "+clean(d.email()),r.get("email"),new Color(31,41,55),Font.PLAIN,25);drawText(g,"Customer Since: "+(d.customerSince()==null?"":d.customerSince()),r.get("since"),new Color(31,41,55),Font.PLAIN,25);if(d.expiresOn()!=null)drawText(g,"Expires: "+d.expiresOn().format(DateTimeFormatter.ofPattern("MMM d, uuuu")),r.get("expiry"),new Color(31,41,55),Font.BOLD,22);BufferedImage photo=ImageCacheManager.loadImage(d.photoUrl());if(photo!=null){Rectangle p=r.get("photo");g.drawImage(photo,p.x,p.y,p.width,p.height,null);}Rectangle barcode=r.get("barcode");paintBarcode(g,d.accountNumber(),barcode.x,barcode.y,barcode.width,barcode.height);drawText(g,clean(d.accountNumber()),r.get("barcodeText"),new Color(31,41,55),Font.PLAIN,18);}finally{g.dispose();}return image;}
    private static void paintBackgroundImage(Graphics2D g,String encoded){if(encoded==null||encoded.isBlank())return;try{BufferedImage source=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));if(source!=null)g.drawImage(source,0,0,WIDTH,HEIGHT,null);}catch(Exception ignored){}}
    private static void drawText(Graphics2D g,String text,Rectangle r,Color color,int style,int preferredSize){g.setClip(r);g.setColor(color);int size=Math.max(10,Math.min(preferredSize,r.height-4));g.setFont(new Font("SansSerif",style,size));g.drawString(text,r.x,r.y+Math.min(r.height-2,g.getFontMetrics().getAscent()+2));g.setClip(null);}
    private static void paintBarcode(Graphics2D g,String value,int x,int y,int w,int h){try{BitMatrix m=new MultiFormatWriter().encode(clean(value), BarcodeFormat.CODE_128,w,h);g.setColor(Color.BLACK);for(int xx=0;xx<w;xx++)for(int yy=0;yy<h;yy++)if(m.get(xx,yy))g.fillRect(x+xx,y+yy,1,1);}catch(Exception e){throw new IllegalArgumentException("A valid account number is required for the barcode.",e);}}
    public static void preview(Component parent,CardData d,Template t){JLabel label=new JLabel(new ImageIcon(render(d,t).getScaledInstance(760,479,Image.SCALE_SMOOTH)));JOptionPane.showMessageDialog(parent,new JScrollPane(label),"Customer Card Preview — "+t.name(),JOptionPane.PLAIN_MESSAGE);}
    public static void print(CardData d,Template t)throws Exception{var settings=HardwareSettingsManager.getBadgePrinterSettings();if(!settings.enabled())throw new IllegalStateException("Badge printing is disabled in Hardware Settings.");PrintService service=HardwareSettingsManager.findPrintService(settings.systemName());if(service==null)throw new IllegalStateException("The configured Magicard Windows queue is unavailable.");BufferedImage image=render(d,t);PrinterJob job=PrinterJob.getPrinterJob();job.setPrintService(service);PageFormat pf=landscape(job);job.setPrintable((g,f,index)->{if(index>0)return java.awt.print.Printable.NO_SUCH_PAGE;g.drawImage(image,0,0,(int)f.getWidth(),(int)f.getHeight(),null);return java.awt.print.Printable.PAGE_EXISTS;},pf);HashPrintRequestAttributeSet attrs=new HashPrintRequestAttributeSet();attrs.add(Sides.ONE_SIDED);job.print(attrs);}
    private static PageFormat landscape(PrinterJob job){Paper p=new Paper();double w=3.375*72,h=2.125*72;p.setSize(w,h);p.setImageableArea(0,0,w,h);PageFormat f=job.defaultPage();f.setOrientation(PageFormat.PORTRAIT);f.setPaper(p);return f;}
    public static void savePdf(Path path,CardData d,Template t)throws Exception{BufferedImage image=render(d,t);try(PDDocument doc=new PDDocument()){PDPage page=new PDPage(new PDRectangle(3.375f*72,2.125f*72));doc.addPage(page);try(PDPageContentStream out=new PDPageContentStream(doc,page)){out.drawImage(LosslessFactory.createFromImage(doc,image),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}doc.save(path.toFile());}}
    private static String clean(String x){return x==null?"":x.trim();}
}
