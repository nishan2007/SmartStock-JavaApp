package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CanvasMultiSelectionTest {
    static class Model implements TemplateWorkspace.Design {
        List<Rectangle2D> boxes=new ArrayList<>(List.of(new Rectangle2D.Double(10,10,20,15),new Rectangle2D.Double(50,10,20,15),new Rectangle2D.Double(50,40,20,10)));
        boolean lockLast;
        public double width(){return 100;}public double height(){return 60;}public int size(){return boxes.size();}public Rectangle2D bounds(int i){return boxes.get(i);}public boolean canResize(){return true;}
        public void move(int i,double x,double y){var b=boxes.get(i);boxes.set(i,new Rectangle2D.Double(x,y,b.getWidth(),b.getHeight()));}
        public void remove(int i){boxes.remove(i);}public boolean locked(int i){return lockLast&&i==2;}public void paint(Graphics2D g,double scale){}
    }
    TemplateWorkspace canvas(Model model,int[] edits){var canvas=new TemplateWorkspace();canvas.configure(model,i->{},()->edits[0]++);canvas.setSize(600,400);var image=new BufferedImage(600,400,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();canvas.paint(g);g.dispose();return canvas;}
    double field(TemplateWorkspace c,String name){try{var f=TemplateWorkspace.class.getDeclaredField(name);f.setAccessible(true);return f.getDouble(c);}catch(Exception ex){throw new AssertionError(ex);}}
    void mouse(TemplateWorkspace c,int id,double x,double y,int modifiers){c.dispatchEvent(new MouseEvent(c,id,System.currentTimeMillis(),modifiers,(int)Math.round(field(c,"originX")+x*field(c,"scale")),(int)Math.round(field(c,"originY")+y*field(c,"scale")),1,false,MouseEvent.BUTTON1));}
    void click(TemplateWorkspace c,double x,double y,int modifiers){mouse(c,MouseEvent.MOUSE_PRESSED,x,y,modifiers);mouse(c,MouseEvent.MOUSE_RELEASED,x,y,modifiers);}
    @Test void modifierClicksSelectToggleAndGroupDragKeepsSpacing()throws Exception{SwingUtilities.invokeAndWait(()->{
        var model=new Model();int[] edits={0};var c=canvas(model,edits);click(c,20,18,0);click(c,60,18,InputEvent.CTRL_DOWN_MASK);assertArrayEquals(new int[]{0,1},c.selectedIndices());
        mouse(c,MouseEvent.MOUSE_PRESSED,20,18,0);mouse(c,MouseEvent.MOUSE_DRAGGED,30,23,0);mouse(c,MouseEvent.MOUSE_RELEASED,30,23,0);
        assertEquals(40,model.bounds(1).getX()-model.bounds(0).getX(),.001);assertEquals(model.bounds(0).getY(),model.bounds(1).getY(),.001);assertTrue(model.bounds(0).getX()>19);assertArrayEquals(new int[]{0,1},c.selectedIndices());
        click(c,70,23,InputEvent.SHIFT_DOWN_MASK);assertArrayEquals(new int[]{0},c.selectedIndices());
    });}
    @Test void marqueeSelectsSeveralWithoutEditingThenDeleteRemovesAll()throws Exception{SwingUtilities.invokeAndWait(()->{
        var model=new Model();int[] edits={0};var c=canvas(model,edits);mouse(c,MouseEvent.MOUSE_PRESSED,5,5,0);mouse(c,MouseEvent.MOUSE_DRAGGED,75,30,0);mouse(c,MouseEvent.MOUSE_RELEASED,75,30,0);assertArrayEquals(new int[]{0,1},c.selectedIndices());assertEquals(0,edits[0]);c.deleteSelection();assertEquals(1,model.size());assertEquals(40,model.bounds(0).getY());assertEquals(1,edits[0]);
    });}
    @Test void selectAllAndNudgeSkipLockedObjects()throws Exception{SwingUtilities.invokeAndWait(()->{
        var model=new Model();model.lockLast=true;int[] edits={0};var c=canvas(model,edits);c.getActionMap().get("selectAllLayers").actionPerformed(null);assertEquals(3,c.selectedIndices().length);c.getActionMap().get("shift RIGHT").actionPerformed(null);assertEquals(11,model.bounds(0).getX());assertEquals(51,model.bounds(1).getX());assertEquals(50,model.bounds(2).getX());assertEquals(1,edits[0]);c.deleteSelection();assertEquals(1,model.size());
    });}
}
