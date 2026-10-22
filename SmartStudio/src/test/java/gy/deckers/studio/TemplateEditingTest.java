package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateEditingTest {
    @TempDir Path temp;
    @Test void canvasDeletionAndInlineLiteralTextUndoAsSingleEdits()throws Exception{
        withPanel(panel->{
            TemplateWorkspace canvas=(TemplateWorkspace)field(panel,"preview");JTable table=(JTable)field(panel,"table");
            var add=BadgeCardsPanel.class.getDeclaredMethod("addShape",String.class);add.setAccessible(true);add.invoke(panel,"Rectangle");
            int count=((BadgeTemplate)field(panel,"template")).elements.size();
            canvas.deleteSelection();assertEquals(count-1,((BadgeTemplate)field(panel,"template")).elements.size());
            var history=BadgeCardsPanel.class.getDeclaredMethod("historyAction",boolean.class);history.setAccessible(true);history.invoke(panel,false);
            assertEquals(count,((BadgeTemplate)field(panel,"template")).elements.size());
            BadgeTemplate model=(BadgeTemplate)field(panel,"template");int row=-1;for(int i=0;i<model.elements.size();i++)if(model.elements.get(i).type.equals("TEXT")&&model.elements.get(i).field.isEmpty()){row=i;break;}
            assertTrue(row>=0);String original=model.elements.get(row).text;table.setRowSelectionInterval(row,row);canvas.beginText();
            ((JTextArea)canvas.getComponent(0)).setText("Edited on canvas");assertTrue(canvas.finishText(true));
            assertEquals("Edited on canvas",((BadgeTemplate)field(panel,"template")).elements.get(row).text);
            history.invoke(panel,false);assertEquals(original,((BadgeTemplate)field(panel,"template")).elements.get(row).text);
        });
    }
    @Test void undoRedoRestoresEditsAndNewEditClearsRedo() throws Exception {
        withPanel(panel->{
            var add=BadgeCardsPanel.class.getDeclaredMethod("addShape",String.class);add.setAccessible(true);
            var history=BadgeCardsPanel.class.getDeclaredMethod("historyAction",boolean.class);history.setAccessible(true);
            BadgeTemplate initial=(BadgeTemplate)field(panel,"template");int count=initial.elements.size();
            add.invoke(panel,"Rectangle");
            assertTrue(((JButton)field(panel,"undoButton")).isEnabled());
            history.invoke(panel,false);
            assertEquals(count,((BadgeTemplate)field(panel,"template")).elements.size());assertFalse((boolean)field(panel,"dirty"));
            assertTrue(((JButton)field(panel,"redoButton")).isEnabled());
            history.invoke(panel,true);
            assertEquals(count+1,((BadgeTemplate)field(panel,"template")).elements.size());
            JTable table=(JTable)field(panel,"table");table.getModel().setValueAt("23",count,4);
            history.invoke(panel,false);assertEquals(5,((BadgeTemplate)field(panel,"template")).elements.get(count).x);
            history.invoke(panel,true);assertEquals(23,((BadgeTemplate)field(panel,"template")).elements.get(count).x);
            history.invoke(panel,false);add.invoke(panel,"Ellipse");
            assertFalse(((JButton)field(panel,"redoButton")).isEnabled());
            assertEquals("undo",panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("control Z")));
        });
    }

    @Test void actualPanelKeepsDraggingThroughLayerUpdatesAndSupportsArrowMovement() throws Exception {
        BadgeTemplate template=new BadgeTemplate();template.widthMm=template.heightMm=100;
        BadgeTemplate.Element element=new BadgeTemplate.Element();element.label="Field";element.x=element.y=10;element.width=element.height=10;
        template.elements.add(element);Path file=temp.resolve("Movement.sstbadge");template.save(file);
        withPanel(panel -> {
            var load=BadgeCardsPanel.class.getDeclaredMethod("loadTemplate",Path.class);load.setAccessible(true);load.invoke(panel,file);
            TemplateWorkspace canvas=(TemplateWorkspace)field(panel,"preview");JTable table=(JTable)field(panel,"table");
            BadgeTemplate model=(BadgeTemplate)field(panel,"template");canvas.setPageFocus(false);canvas.setSize(424,424);
            var g=new BufferedImage(424,424,BufferedImage.TYPE_INT_RGB).createGraphics();canvas.paint(g);g.dispose();
            mouse(canvas,MouseEvent.MOUSE_PRESSED,142,142);
            for(int step=1;step<=20;step++) {
                mouse(canvas,MouseEvent.MOUSE_DRAGGED,142+step*2,142+step);
                assertEquals(0,table.getSelectedRow(),"Layer refresh must retain selection throughout the gesture");
                assertEquals(10+step,model.elements.get(0).x,.001);
            }
            mouse(canvas,MouseEvent.MOUSE_RELEASED,182,162);
            var history=BadgeCardsPanel.class.getDeclaredMethod("historyAction",boolean.class);history.setAccessible(true);
            history.invoke(panel,false);
            assertEquals(10,((BadgeTemplate)field(panel,"template")).elements.get(0).x,.001,"One undo reverses the whole drag");
            history.invoke(panel,true);model=(BadgeTemplate)field(panel,"template");
            canvas.getActionMap().get("RIGHT").actionPerformed(new ActionEvent(canvas,0,""));
            canvas.getActionMap().get("shift DOWN").actionPerformed(new ActionEvent(canvas,0,""));
            assertEquals(30.1,model.elements.get(0).x,.001);assertEquals(21,model.elements.get(0).y,.001);
            assertEquals(0,table.getSelectedRow());assertTrue((boolean)field(panel,"dirty"));
        });
    }

    @Test void shapesAreAddedSelectedReorderedAndSurviveSaveAndRender() throws Exception {
        withPanel(panel -> {
            var add=BadgeCardsPanel.class.getDeclaredMethod("addShape",String.class);add.setAccessible(true);
            for(String shape:new String[]{"Rectangle","Ellipse","Triangle","Line"})add.invoke(panel,shape);
            BadgeTemplate model=(BadgeTemplate)field(panel,"template");JTable table=(JTable)field(panel,"table");
            assertEquals(model.elements.size()-1,table.getSelectedRow());
            var line=model.elements.get(table.getSelectedRow());assertEquals("LINE",line.type);
            var reorder=BadgeCardsPanel.class.getDeclaredMethod("reorderLayer",int.class);reorder.setAccessible(true);reorder.invoke(panel,-1);
            assertSame(line,model.elements.get(table.getSelectedRow()));
            Path file=temp.resolve("Shapes.sstbadge");model.save(file);
            BadgeTemplate restored=BadgeTemplate.load(file);
            assertEquals(4,restored.elements.stream().filter(e->java.util.Set.of("RECT","ELLIPSE","TRIANGLE","LINE").contains(e.type)).count());
            assertNotNull(restored.render(Map.of(),null,300));
        });
        for(String type:new String[]{"RECT","ELLIPSE","TRIANGLE","LINE"}) {
            BadgeTemplate model=new BadgeTemplate();BadgeTemplate.Element e=new BadgeTemplate.Element();e.label=type;e.type=type;e.x=e.y=5;e.width=e.height=10;e.color="#ff0000";model.elements.add(e);
            BufferedImage image=model.render(Map.of(),null,254);
            assertEquals(0xff0000,image.getRGB(100,100)&0xffffff,type+" must render in the selected colour");
            assertEquals(0xffffff,image.getRGB(10,10)&0xffffff);
        }
    }

    private Object field(Object panel,String name) throws Exception {
        var property=panel.getClass().getDeclaredField(name);property.setAccessible(true);return property.get(panel);
    }
    private void mouse(JComponent canvas,int event,int x,int y) {
        canvas.dispatchEvent(new MouseEvent(canvas,event,0,event==MouseEvent.MOUSE_DRAGGED?MouseEvent.BUTTON1_DOWN_MASK:0,x,y,1,false,event==MouseEvent.MOUSE_DRAGGED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1));
    }
    interface PanelCheck {void run(BadgeCardsPanel panel) throws Exception;}
    private void withPanel(PanelCheck check) throws Exception {
        String previous=System.getProperty("smartstudio.badge.folder");System.setProperty("smartstudio.badge.folder",temp.toString());
        try {SwingUtilities.invokeAndWait(()->{BadgeCardsPanel panel=new BadgeCardsPanel();try{check.run(panel);}catch(Exception ex){throw new RuntimeException(ex);}finally{try{((Timer)field(panel,"refresh")).stop();}catch(Exception ex){throw new RuntimeException(ex);}}});}
        finally{if(previous==null)System.clearProperty("smartstudio.badge.folder");else System.setProperty("smartstudio.badge.folder",previous);}
    }
}
