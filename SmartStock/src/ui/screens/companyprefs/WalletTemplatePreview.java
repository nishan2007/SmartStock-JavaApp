package ui.screens.companyprefs;

import services.WalletBadgeTemplate;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** An approximation, not a promise of Apple's device-specific typesetting. */
public final class WalletTemplatePreview {
    private WalletTemplatePreview() { }
    private static final Map<String,String> SAMPLE=Map.of("NAME","Diana D. Bhudoo","USERNAME","diana.bhudoo","ROLE","Manager","LOCATION","Main Store","ISSUED","2026-08-31");
    public static BufferedImage render(WalletBadgeTemplate t,int zoom)throws Exception{
        if(zoom<60||zoom>160)throw new IllegalArgumentException("Invalid preview zoom.");
        BufferedImage image=new BufferedImage(360*zoom/100,470*zoom/100,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try{
            g.scale(zoom/100.0,zoom/100.0);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.decode(t.background()));g.fillRoundRect(0,0,360,470,24,24);
            boolean poster=t.poster().enabled();
            if(poster){
                g.drawImage(services.WalletPosterArtwork.render(t,1),0,0,358,448,null);
                g.setColor(new Color(0,0,0,170));g.fillRect(0,0,360,155);g.fillRect(0,310,360,160);
                draw(g,"Reserved QR / footer area (approximate)",14,328,335,10,Color.WHITE);
            }
            var logo=WalletBadgeTemplate.decodeImage(t.logoPng());
            if(logo!=null)g.drawImage(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(WalletBadgeTemplate.image(logo,poster?126:160,poster?30:50,t.logoScale()))),16,12,null);
            else draw(g,t.company(),16,40,325,18,Color.decode(t.foreground()));
            Map<String,Object> values=poster?services.WalletPosterArtwork.fields(t,SAMPLE):t.passFields(SAMPLE);int y=85;
            for(String section:poster?List.of("headerFields","primaryFields"):List.of("headerFields","primaryFields","secondaryFields","auxiliaryFields")){
                @SuppressWarnings("unchecked") var group=(List<Map<String,String>>)values.get(section);
                if(group.isEmpty())continue;
                int available=!poster&&section.equals("primaryFields")&&(t.employeePhoto()||!t.thumbnailPng().isEmpty())?220:328;
                int width=available/group.size();int x=16;
                for(var f:group){draw(g,f.get("label"),x,y,width-6,10,Color.decode(t.labelColor()));draw(g,f.get("value"),x,y+24,width-6,section.equals("primaryFields")?23:15,Color.decode(t.foreground()));x+=width;}
                if(!poster&&section.equals("primaryFields")){
                    var thumb=WalletBadgeTemplate.decodeImage(t.thumbnailPng());
                    if(t.employeePhoto()){g.setColor(new Color(210,220,230));g.fillRoundRect(252,y-5,80,80,8,8);draw(g,"Photo",264,y+40,65,13,new Color(0,55,96));}
                    else if(thumb!=null)g.drawImage(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(WalletBadgeTemplate.image(thumb,80,80,t.thumbnailScale()))),252,y-5,null);
                }
                y+=section.equals("primaryFields")?90:55;
            }
            var qr=new com.google.zxing.qrcode.QRCodeWriter().encode("SMARTSTOCK-PREVIEW-NOT-A-LOGIN",com.google.zxing.BarcodeFormat.QR_CODE,115,115);
            for(int x=0;x<115;x++)for(int yy=0;yy<115;yy++){g.setColor(qr.get(x,yy)?Color.BLACK:Color.WHITE);g.fillRect(122+x,335+yy,1,1);}
            draw(g,"SAMPLE — not a login badge",86,462,240,10,Color.decode(t.foreground()));
        }finally{g.dispose();}return image;
    }
    private static void draw(Graphics2D g,String value,int x,int y,int width,int size,Color color){g.setColor(color);g.setFont(new Font("SansSerif",Font.PLAIN,size));String text=value;while(text.length()>1&&g.getFontMetrics().stringWidth(text)>width)text=text.substring(0,text.length()-1);if(!text.equals(value)&&text.length()>2)text=text.substring(0,text.length()-2)+"…";g.drawString(text,x,y);}
    public static String details(WalletBadgeTemplate t){
        StringBuilder result=new StringBuilder("Wallet Details (back fields)\n");
        @SuppressWarnings("unchecked") var fields=(List<Map<String,String>>)(t.poster().enabled()?services.WalletPosterArtwork.fields(t,SAMPLE):t.passFields(SAMPLE)).get("backFields");
        for(var f:fields)result.append(f.get("label")).append(": ").append(f.get("value")).append('\n');return result.toString();
    }
}
