package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Isolated component rendering, not a live server or printer acceptance test.
 * Compile with javac -cp "target/classes;../SmartStock/target/classes;target/lib/*" -d target/ribbon-proof tools/PreviewRibbon.java
 * Run with the same classpath plus target/ribbon-proof and an output directory argument.
 */
public final class PreviewRibbon {
    static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T result=find(n,type);if(result!=null)return result;}}return null;}
    static void render(JComponent panel,Path file)throws Exception{
        layout(panel);BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",file.toFile());
    }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]).toAbsolutePath();Files.createDirectories(output);
        System.setProperty("user.home",output.resolve("profile").toString());
        System.setProperty("smartstudio.badge.folder",output.resolve("profile/badges").toString());
        System.setProperty("smartstock.environment","development");System.setProperty("smartstock.db.mode","CLIENT");
        SwingUtilities.invokeAndWait(()->{
            SmartStudio studio=new SmartStudio(false);
            try{
                var show=SmartStudio.class.getDeclaredMethod("showPage",String.class);show.setAccessible(true);show.invoke(studio,"studio");
                BusinessCardsPanel editor=(BusinessCardsPanel)field(studio,"businessCards");
                var document=(publishing.Publication)field(editor,"document");
                String[] values={"DECKERS STUDIO","MAYA THOMAS","Design Consultant","Sample business card","maya@example.test"};
                for(int i=0;i<Math.min(values.length,document.pages.get(0).objects.size());i++)document.pages.get(0).objects.get(i).text=values[i];
                ((JTable)field(editor,"layerTable")).setRowSelectionInterval(1,1);
                Container workspaces=editor.getParent();((CardLayout)workspaces.getLayout()).show(workspaces,"Business Cards");
                StudioRibbon ribbon=find(editor,StudioRibbon.class);
                for(int[] size:new int[][]{{1366,768},{1920,1080},{910,469}}){
                    studio.setSize(size[0],size[1]);studio.addNotify();studio.validate();layout(studio.getContentPane());
                    for(String tab:new String[]{"Home","Insert","Text format","Layout","Print Setup"}){
                        ribbon.select(tab);render((JComponent)studio.getContentPane(),output.resolve("business-"+tab.toLowerCase()+"-"+size[0]+".png"));
                    }
                }
                studio.setSize(1366,768);studio.validate();layout(studio.getContentPane());
                ((CardLayout)workspaces.getLayout()).show(workspaces,"Choose");
                render((JComponent)studio.getContentPane(),output.resolve("workspaces-1366.png"));
                for(String name:new String[]{"Passport Pictures","PDF Editor","Image Studio","Photo Queue"}){
                    ((CardLayout)workspaces.getLayout()).show(workspaces,name);
                    render((JComponent)studio.getContentPane(),output.resolve(name.toLowerCase().replace(' ','-')+"-1366.png"));
                }
                System.out.println("Rendered Home/Insert at laptop, monitor and compact logical sizes plus workspace/specialist panels.");
            }catch(Exception e){throw new RuntimeException(e);}
            finally{studio.dispose();}
        });System.exit(0);
    }
}
