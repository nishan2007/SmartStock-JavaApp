package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class CanvasEditingTest {
 @Test void cornersPreserveRatioAndOppositeAnchorWhileEdgesStretch(){
  Rectangle2D b=new Rectangle2D.Double(10,20,40,20);
  for(int corner:new int[]{0,2,4,6}){
   Rectangle2D resized=TemplateWorkspace.resized(b,corner,15,12);
   assertEquals(2,resized.getWidth()/resized.getHeight(),.00001);
   assertEquals(corner==0||corner==6?b.getMaxX():b.getMinX(),corner==0||corner==6?resized.getMaxX():resized.getMinX(),.00001);
   assertEquals(corner<=2?b.getMaxY():b.getMinY(),corner<=2?resized.getMaxY():resized.getMinY(),.00001);
  }
  Rectangle2D edge=TemplateWorkspace.resized(b,3,20,30);assertEquals(60,edge.getWidth());assertEquals(20,edge.getHeight());
  assertTrue(TemplateWorkspace.resized(b,0,1000,1000).getWidth()>0);
 }
 @Test void inlineTextCommitsCancelsAndProtectsLayerFromDelete()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   BadgeTemplate model=new BadgeTemplate();BadgeTemplate.Element text=new BadgeTemplate.Element();text.label="Text";text.text="Before";text.width=30;text.height=10;model.elements.add(text);
   TemplateWorkspace canvas=new TemplateWorkspace();canvas.configure(()->model,Map::of,()->null,row->{},()->{});canvas.select(0);
   canvas.beginText();JTextArea input=(JTextArea)canvas.getComponent(0);input.setText("After");canvas.deleteSelection();assertEquals(1,model.elements.size());assertTrue(canvas.finishText(true));assertEquals("After",text.text);
   canvas.beginText();((JTextArea)canvas.getComponent(0)).setText("Cancelled");canvas.finishText(false);assertEquals("After",text.text);
   canvas.deleteSelection();assertTrue(model.elements.isEmpty());
   assertNull(canvas.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("DELETE")));
  });
 }
}
