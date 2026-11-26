package gy.deckers.studio;

import publishing.Publication;
import publishing.PublicationBarcode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** Generates self-contained badge artwork using the existing portable IMAGE layer contract. */
final class BadgeCodeArtwork {
    private BadgeCodeArtwork(){}
    static StudioImageFile.Loaded create(Publication.Element code)throws Exception{
        String format="QR".equals(code.type)?"QR_CODE":code.barcodeFormat;
        var bits=PublicationBarcode.encode(code.text,format);
        boolean qr="QR_CODE".equals(format),caption=!qr&&code.barcodeText;
        int scale=qr?Math.max(4,600/bits.getWidth()):Math.max(3,1200/bits.getWidth());
        int width=bits.getWidth()*scale,height=qr?bits.getHeight()*scale:420;
        int captionHeight=caption?60:0;
        var image=new BufferedImage(width,height+captionHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();
        try{
            graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width,height+captionHeight);graphics.setColor(Color.BLACK);
            for(int y=0;y<bits.getHeight();y++)for(int x=0;x<bits.getWidth();x++)if(bits.get(x,y))graphics.fillRect(x*scale,qr?y*scale:0,scale,qr?scale:height);
            if(caption){
                Font font=new Font(Font.SANS_SERIF,Font.PLAIN,34);graphics.setFont(font);
                int textWidth=graphics.getFontMetrics().stringWidth(code.text);
                if(textWidth>width-12){font=font.deriveFont(Math.max(8f,34f*(width-12)/Math.max(1,textWidth)));graphics.setFont(font);}
                graphics.drawString(code.text,Math.max(4,(width-graphics.getFontMetrics().stringWidth(code.text))/2),height+42);
            }
        }finally{graphics.dispose();}
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        return new StudioImageFile.Loaded(bytes.toByteArray(),image,qr?"QR code":"Barcode",image.getWidth(),image.getHeight());
    }
}
