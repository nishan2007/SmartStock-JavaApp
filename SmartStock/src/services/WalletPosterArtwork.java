package services;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Artwork coordinates are ours; Wallet's native QR/footer placement remains Apple's. */
public final class WalletPosterArtwork {
    public static final int WIDTH=358, HEIGHT=448;
    public static final Rectangle SIGNATURE_AREA=new Rectangle(208,244,132,56);
    private WalletPosterArtwork() { }
    public static BufferedImage render(WalletBadgeTemplate t,int scale) {
        if(scale<1||scale>3)throw new IllegalArgumentException("Invalid artwork scale.");
        BufferedImage result=new BufferedImage(WIDTH*scale,HEIGHT*scale,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=result.createGraphics();
        try {
            g.scale(scale,scale);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(Color.decode(t.background()));g.fillRect(0,0,WIDTH,HEIGHT);
            BufferedImage background=WalletBadgeTemplate.decodeArtwork(t.poster().backgroundPng());
            if(background!=null){
                double ratio=Math.max((double)WIDTH/background.getWidth(),(double)HEIGHT/background.getHeight());
                int w=(int)Math.ceil(background.getWidth()*ratio),h=(int)Math.ceil(background.getHeight()*ratio);
                g.drawImage(background,(WIDTH-w)/2,(HEIGHT-h)/2,w,h,null);
            }
            BufferedImage signature=WalletBadgeTemplate.decodeImage(t.poster().signaturePng());
            if(signature!=null){
                Rectangle r=SIGNATURE_AREA;
                g.setColor(Color.WHITE);g.fillRoundRect(r.x,r.y,r.width,r.height,8,8);
                double ratio=Math.min((r.width-12.0)/signature.getWidth(),(r.height-12.0)/signature.getHeight())*t.poster().signaturePercent()/100.0;
                int w=Math.max(1,(int)(signature.getWidth()*ratio)),h=Math.max(1,(int)(signature.getHeight()*ratio));
                g.drawImage(signature,r.x+(r.width-w)/2,r.y+(r.height-h)/2,w,h,null);
            }
        }finally{g.dispose();}return result;
    }
    public static Map<String,Object> fields(WalletBadgeTemplate t,Map<String,String> employee){
        Map<String,Object> generic=t.passFields(employee),poster=new LinkedHashMap<>();
        poster.put("headerFields",generic.get("headerFields"));poster.put("primaryFields",generic.get("primaryFields"));
        List<Map<String,String>> remaining=new ArrayList<>();
        for(String section:List.of("secondaryFields","auxiliaryFields")){
            @SuppressWarnings("unchecked") var values=(List<Map<String,String>>)generic.get(section);remaining.addAll(values);
        }
        String footer=String.join(" · ",remaining.stream().map(f->f.get("label")+": "+f.get("value")).toList());
        poster.put("footerFields",footer.isEmpty()?List.of():List.of(Map.of("key","posterSummary","value",footer)));
        List<Map<String,String>> details=new ArrayList<>(remaining);
        @SuppressWarnings("unchecked") var back=(List<Map<String,String>>)generic.get("backFields");details.addAll(back);
        poster.put("backFields",details);return poster;
    }
}
