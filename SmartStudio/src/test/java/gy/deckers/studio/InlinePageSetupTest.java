package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class InlinePageSetupTest {
    @Test void layoutAndPrintSettingsAreActualRibbonControlsAtEveryDisplayBudget()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var editor=new BusinessCardsPanel(true);StudioRibbon ribbon=find(editor,StudioRibbon.class);JTabbedPane tabs=find(ribbon,JTabbedPane.class);
                for(int screen:new int[]{1366,1920})for(double scale:new double[]{1,1.25,1.5})for(String tab:new String[]{"Layout","Print Setup"}){
                    ribbon.select(tab);ribbon.autoCompact(true);assertFalse(ribbon.isCollapsed());
                    ribbon.setSize((int)(screen/scale)-32,240);layout(ribbon);ribbon.setSize(ribbon.getWidth(),ribbon.getPreferredSize().height);layout(ribbon);
                    Container page=(Container)tabs.getSelectedComponent();
                    Container controls=(Container)page.getComponent(0);
                    for(Component control:controls.getComponents())assertTrue(control.getY()+control.getHeight()<=controls.getHeight(),tab+" must not hide actions below the ribbon");
                    String[] fields=tab.equals("Layout")?new String[]{"layoutWidth","layoutHeight","layoutUnits","paper","customWidth","customHeight","pageMode"}:new String[]{"leftMargin","rightMargin","topMargin","bottomMargin","gap","verticalGap","copies","sheet","rotate","marks"};
                    for(String name:fields){Component control=(Component)field(editor,name);assertTrue(SwingUtilities.isDescendingFrom(control,page),name);Rectangle bounds=SwingUtilities.convertRectangle(control.getParent(),control.getBounds(),page);assertTrue(bounds.x>=0&&bounds.y>=0&&bounds.x+bounds.width<=page.getWidth()&&bounds.y+bounds.height<=page.getHeight(),name+" at "+screen+" / "+scale+": "+bounds);}
                }
                ((JSpinner)field(editor,"leftMargin")).setValue(12./25.4);
                assertEquals(12,editor.output().print.left,.000001);
                assertEquals("IN",((JComboBox<?>)field(editor,"layoutUnits")).getSelectedItem());((JComboBox<?>)field(editor,"layoutUnits")).setSelectedItem("MM");((JSpinner)field(editor,"layoutWidth")).setValue(100.);((JSpinner)field(editor,"layoutHeight")).setValue(50.);
                ribbon.select("Layout");findButton(tabs.getSelectedComponent(),"Apply size").doClick();assertEquals(100,editor.output().width);assertEquals(50,editor.output().height);
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    private static JButton findButton(Component c,String label){if(c instanceof JButton button&&label.equals(button.getText()))return button;if(c instanceof Container parent)for(Component child:parent.getComponents()){var result=findButton(child,label);if(result!=null)return result;}return null;}
    private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T result=find(n,type);if(result!=null)return result;}}return null;}
}
