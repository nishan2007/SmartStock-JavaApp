package ui.screens.customorders;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import services.ShirtDesignerService;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ShirtPrintAreaEditorTest {
    @Test void missingDimensionsGetLetterAndRaisedChestDefaultsWithoutChangingOriginal(){
        JsonObject original=ShirtDesignerService.defaults();
        JsonObject configured=ShirtPrintAreaEditor.initialConfiguration(original);
        JsonObject front=configured.getAsJsonObject("areas").getAsJsonObject("FRONT"),chest=configured.getAsJsonObject("areas").getAsJsonObject("CHEST");
        assertEquals(10,front.get("widthIn").getAsDouble());assertEquals(11,front.get("heightIn").getAsDouble());
        assertEquals(.27,chest.get("y").getAsDouble());assertEquals(3,chest.get("widthIn").getAsDouble());
        assertEquals(0,original.getAsJsonObject("areas").getAsJsonObject("FRONT").get("widthIn").getAsDouble());
    }
    @Test void configuredAreasAndPresetIdsArePreserved(){
        JsonObject original=ShirtDesignerService.defaults(),chest=original.getAsJsonObject("areas").getAsJsonObject("CHEST");
        chest.addProperty("widthIn",4);chest.addProperty("heightIn",4);chest.addProperty("y",.22);chest.addProperty("fullPresetId",27);
        assertEquals(chest,ShirtPrintAreaEditor.initialConfiguration(original).getAsJsonObject("areas").getAsJsonObject("CHEST"));
    }
    @Test void positionAndResizeControlsStayInBoundsAndSaveThroughExistingServerValidation()throws Exception{
        onEdt(()->{
            ShirtPrintAreaEditor editor=new ShirtPrintAreaEditor(ShirtDesignerService.defaults());
            editor.setValue("x",5);assertEquals(.6,editor.boundary().get("x").getAsDouble(),.000001);
            editor.setValue("x",.2);editor.setValue("w",5);assertEquals(.8,editor.boundary().get("w").getAsDouble(),.000001);
            editor.setValue("y",-1);assertEquals(0,editor.boundary().get("y").getAsDouble());
            JsonObject saved=ShirtDesignerService.configuration(editor.configuration(),true);
            assertEquals(.8,saved.getAsJsonObject("areas").getAsJsonObject("FRONT").get("w").getAsDouble());
            JsonObject copy=editor.configuration();copy.getAsJsonObject("areas").getAsJsonObject("FRONT").addProperty("x",0);
            assertEquals(.2,editor.configuration().getAsJsonObject("areas").getAsJsonObject("FRONT").get("x").getAsDouble());
        });
    }
    @Test void draggingChangesTheSelectedAreaAndCornerResizingKeepsTheOppositeCorner()throws Exception{
        onEdt(()->{
            ShirtPrintAreaEditor editor=new ShirtPrintAreaEditor(ShirtDesignerService.defaults());
            JComponent preview=(JComponent)((BorderLayout)editor.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            preview.setSize(500,500);
            drag(preview,250,220,270,195);
            assertEquals(.34,editor.boundary().get("x").getAsDouble(),.000001);
            assertEquals(.26,editor.boundary().get("y").getAsDouble(),.000001);
            double x=editor.boundary().get("x").getAsDouble(),y=editor.boundary().get("y").getAsDouble();
            drag(preview,370,350,395,375);
            assertEquals(x,editor.boundary().get("x").getAsDouble(),.000001);assertEquals(y,editor.boundary().get("y").getAsDouble(),.000001);
            assertEquals(.45,editor.boundary().get("w").getAsDouble(),.000001);assertEquals(.49,editor.boundary().get("h").getAsDouble(),.000001);
            assertEquals(.27,editor.configuration().getAsJsonObject("areas").getAsJsonObject("CHEST").get("y").getAsDouble());
            ShirtDesignerService.configuration(editor.configuration(),true);
        });
    }
    private static void drag(JComponent preview,int x,int y,int endX,int endY){
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_PRESSED,0,0,x,y,1,false));
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_DRAGGED,0,0,endX,endY,0,false));
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_RELEASED,0,0,endX,endY,1,false));
    }
    private static void onEdt(Runnable action)throws Exception{
        AtomicReference<Throwable> failure=new AtomicReference<>();SwingUtilities.invokeAndWait(()->{try{action.run();}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }
}
