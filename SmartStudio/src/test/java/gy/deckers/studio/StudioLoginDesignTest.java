package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioLoginDesignTest {
    @TempDir Path profile;
    static Object field(Object owner,String name)throws Exception{var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    static JButton command(Container parent,String name){for(Component child:parent.getComponents()){if(child instanceof JButton b&&(name.equals(b.getText())||name.equals(b.getAccessibleContext().getAccessibleName())))return b;if(child instanceof Container c){var b=command(c,name);if(b!=null)return b;}}return null;}
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    @Test void loginKeepsRealInputsManualFallbackAndCompanyLogoSeparateFromAppIdentity()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            SmartStudio studio=new SmartStudio(false);
            try{
                var show=SmartStudio.class.getDeclaredMethod("showPage",String.class);show.setAccessible(true);show.invoke(studio,"login");
                JTextField identifier=(JTextField)field(studio,"identifier");JPasswordField password=(JPasswordField)field(studio,"password");
                assertEquals("Username, email or badge ID",identifier.getAccessibleContext().getAccessibleName());
                password.setText("synthetic");char echo=password.getEchoChar();command(studio.getContentPane(),"Show password").doClick();assertEquals(0,password.getEchoChar());command(studio.getContentPane(),"Hide password").doClick();assertEquals(echo,password.getEchoChar());
                command(studio.getContentPane(),"Show password").doClick();identifier.setEditable(false);identifier.setText("NFC badge detected");command(studio.getContentPane(),"Use username and password").doClick();assertTrue(identifier.isEditable());assertEquals("",identifier.getText());assertEquals(0,password.getPassword().length);assertEquals(echo,password.getEchoChar());
                assertFalse(((JLabel)field(studio,"nfcState")).getText().toLowerCase().contains("connected"));
                BufferedImage company=new BufferedImage(240,60,BufferedImage.TYPE_INT_ARGB);Graphics2D g=company.createGraphics();g.setColor(Color.BLUE);g.fillRect(0,0,240,60);g.dispose();ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(company,"png",bytes);
                JsonObject brand=new JsonObject();brand.addProperty("name","Synthetic Company");brand.addProperty("motto","Synthetic branding");brand.addProperty("logo","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()));
                var apply=SmartStudio.class.getDeclaredMethod("applyBrand",JsonObject.class);apply.setAccessible(true);apply.invoke(studio,brand);
                for(String name:java.util.List.of("logo","workspaceLogo")){JLabel logo=(JLabel)field(studio,name);assertNotNull(logo.getIcon());assertEquals(4.,logo.getIcon().getIconWidth()/(double)logo.getIcon().getIconHeight(),.01);}
                assertEquals(7,studio.getIconImages().size());
                for(int[] size:new int[][]{{1366,768},{1920,1080},{910,512}}){
                    studio.setSize(size[0],size[1]);studio.addNotify();studio.validate();layout(studio.getContentPane());
                    JButton signIn=command(studio.getContentPane(),"Sign in");assertEquals(Color.WHITE,signIn.getForeground());assertTrue(signIn.getHeight()>=44);
                    assertTrue(identifier.getWidth()>300);assertTrue(password.getWidth()>250);
                    BufferedImage image=new BufferedImage(studio.getContentPane().getWidth(),studio.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);g=image.createGraphics();studio.getContentPane().printAll(g);g.dispose();ImageIO.write(image,"png",Path.of("target","login-approved-"+size[0]+".png").toFile());
                }
            }catch(Exception ex){throw new RuntimeException(ex);}finally{studio.dispose();}
        });
    }
    @Test void iconResourcesKeepAlphaAndWindowsContainsTheExactSizeRenders()throws Exception{
        String path="/gy/deckers/studio/icons/";
        for(int size:new int[]{16,24,32,48,64,128,256,512,1024}){
            BufferedImage image=ImageIO.read(Objects.requireNonNull(getClass().getResource(path+"SmartStudio-"+size+".png")));assertEquals(size,image.getWidth());assertEquals(size,image.getHeight());assertTrue(image.getColorModel().hasAlpha());assertEquals(0,image.getRGB(0,0)>>>24);
        }
        byte[] ico;try(var in=getClass().getResourceAsStream(path+"SmartStudio.ico")){ico=Objects.requireNonNull(in).readAllBytes();}
        ByteBuffer directory=ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);assertEquals(0,directory.getShort());assertEquals(1,directory.getShort());assertEquals(7,directory.getShort());
        for(int size:new int[]{16,24,32,48,64,128,256}){assertEquals(size%256,Byte.toUnsignedInt(directory.get()));assertEquals(size%256,Byte.toUnsignedInt(directory.get()));directory.position(directory.position()+6);int length=directory.getInt(),offset=directory.getInt();try(var in=getClass().getResourceAsStream(path+"SmartStudio-"+size+".png")){assertArrayEquals(Objects.requireNonNull(in).readAllBytes(),Arrays.copyOfRange(ico,offset,offset+length));}}
        try(var in=getClass().getResourceAsStream(path+"SmartStudio.svg")){String svg=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertFalse(svg.contains("<image"));assertTrue(svg.contains("SmartStudio Creative Brush"));}
        try(var in=new DataInputStream(Objects.requireNonNull(getClass().getResourceAsStream(path+"SmartStudio.icns")))){
            assertEquals(0x69636e73,in.readInt());int total=in.readInt(),consumed=8;int[] sizes={16,32,64,128,256,512,1024};
            for(int size:sizes){in.readInt();int chunk=in.readInt();byte[] payload=in.readNBytes(chunk-8);assertEquals(chunk-8,payload.length);try(var png=getClass().getResourceAsStream(path+"SmartStudio-"+size+".png")){assertArrayEquals(Objects.requireNonNull(png).readAllBytes(),payload);}consumed+=chunk;}
            assertEquals(total,consumed);assertEquals(-1,in.read());
        }
    }
}
