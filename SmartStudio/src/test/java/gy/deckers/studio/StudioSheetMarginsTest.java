package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.PublicationFiles;
import publishing.SheetLayout;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioSheetMarginsTest {
    @Test void independentMarginsReachLayoutAndSavedDocuments()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            for(int mode=0;mode<3;mode++){
                var panel=new BusinessCardsPanel(mode==1,false,mode==2);
                try{
                    String[] fields={"leftMargin","rightMargin","topMargin","bottomMargin"};
                    double[] values={20,3,15,4};
                    for(int index=0;index<fields.length;index++){
                        var field=BusinessCardsPanel.class.getDeclaredField(fields[index]);field.setAccessible(true);
                        ((JSpinner)field.get(panel)).setValue(values[index]/25.4);
                    }
                    var output=panel.output();
                    var saved=PublicationFiles.read(PublicationFiles.archive(output));
                    assertEquals(20,saved.print.left,.000001);assertEquals(3,saved.print.right,.000001);
                    assertEquals(15,saved.print.top,.000001);assertEquals(4,saved.print.bottom,.000001);
                    var layout=SheetLayout.pack(output.width,output.height,output.print);
                    assertTrue(layout.capacity()>0);
                    for(var placement:layout.placements()){
                        assertTrue(placement.x()>=20);assertTrue(placement.y()>=15);
                        assertTrue(placement.x()+placement.width()<=output.print.width-3+.0001);
                        assertTrue(placement.y()+placement.height()<=output.print.height-4+.0001);
                    }
                    if(mode==2){
                        panel.restoreSheetSize(saved.print);
                        var restored=panel.output();
                        assertEquals(20,restored.print.left,.000001);assertEquals(3,restored.print.right,.000001);
                        assertEquals(15,restored.print.top,.000001);assertEquals(4,restored.print.bottom,.000001);
                    }
                }catch(Exception ex){throw new RuntimeException(ex);}
                finally{
                    try{var field=BusinessCardsPanel.class.getDeclaredField("refresh");field.setAccessible(true);((Timer)field.get(panel)).stop();}
                    catch(Exception ex){throw new RuntimeException(ex);}
                }
            }
        });
    }
}
