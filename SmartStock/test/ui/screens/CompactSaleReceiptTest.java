package ui.screens;

import Receipt.*;
import managers.CompanyCustomizationManager;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class CompactSaleReceiptTest {
    @Test void visibilityRoundTripsAndKeepsTotalAndBarcode()throws Exception {
        var settings=new Gson().fromJson("{\"companyName\":\"DECKERS\",\"footerLine\":\"Thank you\"}",CompanyCustomizationManager.ReceiptSettings.class);
        var receipt=new ReceiptData(1,"0001-0001-000123",null,"Main Store","Sample Cashier","","","CASH","PAID","",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"",BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,null,null,List.of());
        String original=ReceiptFormatter.formatText(receipt,settings);
        for(String label:List.of("DECKERS","Subtotal","Payment","Paid","Powered by SmartStock"))assertTrue(original.contains(label));
        Gson json=new Gson();JsonObject encoded=json.toJsonTree(settings).getAsJsonObject();
        encoded.add("saleReceiptVisibility",json.toJsonTree(new CompanyCustomizationManager.SaleReceiptVisibility(false,false,false,false,false)));
        var compact=json.fromJson(encoded,CompanyCustomizationManager.ReceiptSettings.class);
        String text=ReceiptFormatter.formatText(receipt,compact);
        for(String label:List.of("DECKERS","Subtotal","Payment","Paid","Powered by SmartStock"))assertFalse(text.contains(label));
        assertTrue(text.contains("Total"));assertTrue(text.contains("0001-0001-000123"));assertFalse(text.endsWith("\n\n"));
        assertTrue(ReceiptFormatter.formatEscPos(receipt,compact).length<ReceiptFormatter.formatEscPos(receipt,settings).length);
        SwingUtilities.invokeAndWait(()->{
            var panel=new ReceiptPreview.ReceiptPaperPanel();panel.setReceiptText(text,false,receipt.getReceiptNumber());
            BufferedImage padded=new BufferedImage(300,160,BufferedImage.TYPE_INT_ARGB);Graphics2D logo=padded.createGraphics();logo.setColor(Color.ORANGE);logo.setFont(new Font("Serif",Font.ITALIC,25));logo.drawString("Deckers",80,85);logo.dispose();
            BufferedImage cropped=ReceiptLogoLayout.trim(padded);assertTrue(cropped.getHeight()<60);panel.setLogo(padded,false);
            Dimension size=panel.getPreferredSize();panel.setSize(size);BufferedImage image=new BufferedImage(size.width,size.height,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();panel.paint(graphics);graphics.dispose();
            try{java.nio.file.Path output=java.nio.file.Path.of("target","compact-sale-receipt.png");javax.imageio.ImageIO.write(image,"png",output.toFile());}catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}
