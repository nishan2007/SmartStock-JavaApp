package ui.screens;

import org.junit.jupiter.api.Test;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReportsChartRenderingTest {
    @Test void emptyExpenseChartCanPaintResizeAndSwitchTabs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CategoryChart chart = chart();
            JPanel holder = new JPanel(new BorderLayout());
            Reports.replaceChart(holder, chart);
            assertInstanceOf(JLabel.class, holder.getComponent(0));
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Expense Ledger", holder);
            JPanel other = new JPanel();other.setBackground(Color.BLUE);tabs.addTab("Other report", other);
            for(int height : new int[]{450,220,100,40}) {
                tabs.setSize(900,height);tabs.setSelectedIndex(0);layout(tabs);
                paint(tabs);
                tabs.setSelectedIndex(1);layout(tabs);
                BufferedImage image=paint(tabs);
                if(height>100)assertEquals(Color.BLUE.getRGB(),image.getRGB(450,height-15));
            }
        });
    }

    @Test void populatedZeroAndEmptyChartsCanReplaceEachOtherWithoutCorruptingGraphics() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "XChartPanel's keyboard shortcuts require a desktop toolkit");
        SwingUtilities.invokeAndWait(() -> {
            JPanel holder = new JPanel(new BorderLayout());holder.setSize(900,220);
            for(int state : new int[]{1,0,2,0}) {
                CategoryChart chart=chart();
                if(state>0)chart.addSeries("Expenses",List.of("Supplies","Donations"),state==1?List.of(0,0):List.of(200,800));
                Reports.replaceChart(holder,chart);layout(holder);
                var image=new BufferedImage(1000,300,BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=image.createGraphics();
                try {
                    graphics.translate(5,7);graphics.clipRect(0,0,900,220);
                    var before=graphics.getTransform();Rectangle clip=graphics.getClipBounds();
                    assertDoesNotThrow(() -> holder.paint(graphics));
                    assertEquals(before,graphics.getTransform());assertEquals(clip,graphics.getClipBounds());
                } finally {graphics.dispose();}
            }
        });
    }

    private static CategoryChart chart(){
        CategoryChart chart=new CategoryChartBuilder().width(700).height(300).title("Expenses by Category").yAxisTitle("Amount").build();
        chart.getStyler().setLegendVisible(false);chart.getStyler().setYAxisDecimalPattern("$#,##0.00");return chart;
    }
    private static void layout(Container container){container.doLayout();for(Component child:container.getComponents())if(child instanceof Container nested)layout(nested);}
    private static BufferedImage paint(JComponent component){
        BufferedImage image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();try{assertDoesNotThrow(() -> component.paint(graphics));}finally{graphics.dispose();}return image;
    }
}
