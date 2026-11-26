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
            for(var value:area.getAsJsonArray("layers")){JsonObject l=value.getAsJsonObject();double w=StorefrontService.decimal(l,"w").doubleValue()*bw,h=StorefrontService.decimal(l,"h").doubleValue()*bh,x=bx+StorefrontService.decimal(l,"x").doubleValue()*bw,y=by+StorefrontService.decimal(l,"y").doubleValue()*bh;AffineTransform transform=g.getTransform();g.translate(x+w/2,y+h/2);g.rotate(Math.toRadians(StorefrontService.decimal(l,"rotation").doubleValue()));Shape layerClip=g.getClip();g.clip(new Rectangle2D.Double(-w/2,-h/2,w,h));boolean single=StorefrontService.text(l.has("printMode")?l:area,l.has("printMode")?"printMode":"mode").equals("SINGLE");String vinylColor=StorefrontService.text(l.has("printMode")?l:area,"vinylColor");
                if(StorefrontService.text(l,"type").equals("TEXT")){String family=StorefrontService.text(l,"font"),fontPath=family.equals("Deckers Serif")?"shirt-serif.ttf":family.equals("Deckers Mono")?"shirt-mono.ttf":"shirt-sans.ttf";Font font;try(var in=ShirtPreviewRenderer.class.getClassLoader().getResourceAsStream("storefront-web/fonts/"+fontPath)){font=family.equals("Impact")?new Font("Impact",Font.PLAIN,12):in==null?new Font(Font.SANS_SERIF,Font.PLAIN,12):Font.createFont(Font.TRUETYPE_FONT,in);}font=font.deriveFont((float)(l.has("fontSize")?l.get("fontSize").getAsDouble()/72*(bh/Math.max(.1,box.get("heightIn").getAsDouble())):h*.8));String text=StorefrontService.text(l,"text");g.setFont(font);g.setColor(Color.decode(single?vinylColor:StorefrontService.text(l,"color")));drawText(g,text,w,h,l.has("arc")?l.get("arc").getAsDouble():0,l.has("fontSize"));
                }else{BufferedImage img=artwork.get(StorefrontService.text(l,"fileId"));if(img==null)throw new IllegalArgumentException("Missing private shirt artwork.");if(single){BufferedImage mask=new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D mg=mask.createGraphics();mg.drawImage(img,0,0,null);mg.setComposite(AlphaComposite.SrcIn);mg.setColor(Color.decode(vinylColor));mg.fillRect(0,0,mask.getWidth(),mask.getHeight());mg.dispose();img=mask;}double fit=Math.min(w/img.getWidth(),h/img.getHeight())*(l.has("imageZoom")?l.get("imageZoom").getAsDouble():1),iw=img.getWidth()*fit,ih=img.getHeight()*fit;g.drawImage(img,(int)(-iw/2),(int)(-ih/2),(int)iw,(int)ih,null);}g.setClip(layerClip);g.setTransform(transform);
            }g.setClip(originalClip);
        }g.dispose();return output;
    }
    private static void drawText(Graphics2D g,String text,double w,double h,double arc,boolean naturalSize){
        FontMetrics metrics=g.getFontMetrics();double baseline=(metrics.getAscent()-metrics.getDescent())/2.0;
        if(arc==0){double measured=metrics.stringWidth(text);if(!naturalSize)g.scale(w/Math.max(1,measured),1);g.drawString(text,(float)(-measured/2),(float)baseline);return;}
        String[] letters=text.codePoints().mapToObj(code->new String(Character.toChars(code))).toArray(String[]::new);
        double[] advances=new double[letters.length];double total=0;
        for(int i=0;i<letters.length;i++){advances[i]=metrics.stringWidth(letters[i]);total+=advances[i];}
        if(total==0)return;
        double radius=total/Math.toRadians(Math.abs(arc)),sign=Math.signum(arc),cursor=0,height=g.getFont().getSize2D();
        double[][] positions=new double[letters.length][3];Rectangle2D bounds=null;
        for(int i=0;i<letters.length;i++){
            double theta=(cursor+advances[i]/2-total/2)/radius,x=radius*Math.sin(theta),y=sign*radius*(1-Math.cos(theta)),rotation=sign*theta;
            positions[i]=new double[]{x,y,rotation};cursor+=advances[i];
            AffineTransform transform=AffineTransform.getTranslateInstance(x,y);transform.rotate(rotation);
            Rectangle2D glyphBounds=transform.createTransformedShape(new Rectangle2D.Double(-advances[i]/2,-height/2,advances[i],height)).getBounds2D();
            if(bounds==null)bounds=glyphBounds;else bounds= bounds.createUnion(glyphBounds);
        }
        if(bounds==null)return;if(!naturalSize)g.scale(w/Math.max(1,bounds.getWidth()),h/Math.max(1,bounds.getHeight()));g.translate(-bounds.getCenterX(),-bounds.getCenterY());
        for(int i=0;i<letters.length;i++){AffineTransform previous=g.getTransform();g.translate(positions[i][0],positions[i][1]);g.rotate(positions[i][2]);g.drawString(letters[i],(float)(-advances[i]/2),(float)baseline);g.setTransform(previous);}
    }

}
