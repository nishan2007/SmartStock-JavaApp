package services;

import com.google.gson.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;

/** Private staff previews use the submitted configuration, never a later catalogue revision. */
public final class ShirtPreviewRenderer {
    private ShirtPreviewRenderer(){}
    public static BufferedImage render(JsonObject shirt,JsonObject design,String side,String shirtColor,Map<String,BufferedImage> artwork)throws Exception{
        int size=900;JsonObject config=shirt.getAsJsonObject("config");String path=StorefrontService.text(config,side.equals("BACK")?"backMockup":"frontMockup");
        BufferedImage photo;try(var input=ShirtPreviewRenderer.class.getClassLoader().getResourceAsStream("storefront-web/"+path.substring("/shop/".length()))){if(input==null)throw new IllegalArgumentException("The configured shirt mockup is not bundled.");photo=ImageIO.read(input);}
        if(photo==null)throw new IllegalArgumentException("Unreadable shirt mockup.");BufferedImage tinted=new BufferedImage(photo.getWidth(),photo.getHeight(),BufferedImage.TYPE_INT_ARGB);Color tint=Color.decode(shirtColor);
        for(int y=0;y<photo.getHeight();y++)for(int x=0;x<photo.getWidth();x++){int pixel=photo.getRGB(x,y),alpha=(pixel>>>24)&255;int r=((pixel>>>16)&255)*tint.getRed()/255,g=((pixel>>>8)&255)*tint.getGreen()/255,b=(pixel&255)*tint.getBlue()/255;tinted.setRGB(x,y,alpha<<24|r<<16|g<<8|b);}
        BufferedImage output=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=output.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.drawImage(tinted,0,0,size,size,null);
        for(String name:ShirtDesignerService.AREAS){if(side.equals("BACK")?!name.equals("BACK"):name.equals("BACK"))continue;JsonObject area=design.getAsJsonObject("areas").getAsJsonObject(name);if(area==null)continue;JsonObject box=config.getAsJsonObject("areas").getAsJsonObject(name);double bx=StorefrontService.decimal(box,"x").doubleValue()*size,by=StorefrontService.decimal(box,"y").doubleValue()*size,bw=StorefrontService.decimal(box,"w").doubleValue()*size,bh=StorefrontService.decimal(box,"h").doubleValue()*size;
            Shape originalClip=g.getClip();g.clip(new Rectangle2D.Double(bx,by,bw,bh));
            for(var value:area.getAsJsonArray("layers")){JsonObject l=value.getAsJsonObject();double w=StorefrontService.decimal(l,"w").doubleValue()*bw,h=StorefrontService.decimal(l,"h").doubleValue()*bh,x=bx+StorefrontService.decimal(l,"x").doubleValue()*bw,y=by+StorefrontService.decimal(l,"y").doubleValue()*bh;AffineTransform transform=g.getTransform();g.translate(x+w/2,y+h/2);g.rotate(Math.toRadians(StorefrontService.decimal(l,"rotation").doubleValue()));boolean single=StorefrontService.text(l.has("printMode")?l:area,l.has("printMode")?"printMode":"mode").equals("SINGLE");String vinylColor=StorefrontService.text(l.has("printMode")?l:area,"vinylColor");
                if(StorefrontService.text(l,"type").equals("TEXT")){String family=StorefrontService.text(l,"font"),fontPath=family.equals("Deckers Serif")?"shirt-serif.ttf":family.equals("Deckers Mono")?"shirt-mono.ttf":"shirt-sans.ttf";Font font;try(var in=ShirtPreviewRenderer.class.getClassLoader().getResourceAsStream("storefront-web/fonts/"+fontPath)){font=in==null?new Font(Font.SANS_SERIF,Font.PLAIN,12):Font.createFont(Font.TRUETYPE_FONT,in);}font=font.deriveFont((float)(h*.8));String text=StorefrontService.text(l,"text");double measured=g.getFontMetrics(font).stringWidth(text);if(measured>w)font=font.deriveFont((float)(font.getSize2D()*w/measured));g.setFont(font);g.setColor(Color.decode(single?vinylColor:StorefrontService.text(l,"color")));FontMetrics metrics=g.getFontMetrics();g.drawString(text,-metrics.stringWidth(text)/2f,(metrics.getAscent()-metrics.getDescent())/2f);
                }else{BufferedImage img=artwork.get(StorefrontService.text(l,"fileId"));if(img==null)throw new IllegalArgumentException("Missing private shirt artwork.");if(single){BufferedImage mask=new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D mg=mask.createGraphics();mg.drawImage(img,0,0,null);mg.setComposite(AlphaComposite.SrcIn);mg.setColor(Color.decode(vinylColor));mg.fillRect(0,0,mask.getWidth(),mask.getHeight());mg.dispose();img=mask;}g.drawImage(img,(int)(-w/2),(int)(-h/2),(int)w,(int)h,null);}g.setTransform(transform);
            }g.setClip(originalClip);
        }g.dispose();return output;
    }
}
