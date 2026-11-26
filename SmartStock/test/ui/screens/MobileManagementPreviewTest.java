package ui.screens;

import services.LanApiClient;
import services.MobileDeviceService;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MobileManagementPreviewTest {
    @Test void rendersTheDeviceWorkspaceAtNormalAndMinimumSizeWithoutStoreAccess() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        var apps=MobileDeviceService.APPS.stream().map(a->new LanApiClient.MobileWebApp(a.key(),a.name(),a.group(),a.gateway(),a.path())).toList();
        var state=new LanApiClient.MobileManagementState(List.of(
            new LanApiClient.MobileManagedDevice("phone-1","Receiving phone","2026-11-19T12:00:00Z","2026-11-19T13:00:00Z",false,1,List.of("INVENTORY","RECEIVING","ORDERS")),
            new LanApiClient.MobileManagedDevice("phone-2","Manager’s iPhone","2026-11-18T12:00:00Z","2026-11-19T12:00:00Z",false,2,List.of("SCHEDULER","EMPLOYEE_WEB")),
            new LanApiClient.MobileManagedDevice("phone-3","Old phone","2026-11-01T12:00:00Z","2026-11-02T12:00:00Z",true,3,List.of())),apps,"");
        SwingUtilities.invokeAndWait(()->{
            MobileManagement dialog=new MobileManagement(null,state);
            try {
                for(Dimension size:List.of(new Dimension(1100,760),dialog.getMinimumSize())) {
                    dialog.setSize(size);dialog.getContentPane().setSize(size.width-16,size.height-40);layout(dialog.getContentPane());
                    BufferedImage image=new BufferedImage(size.width,size.height,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();graphics.setColor(dialog.getContentPane().getBackground());graphics.fillRect(0,0,size.width,size.height);dialog.getContentPane().printAll(graphics);graphics.dispose();
                    Path output=Path.of("target/mobile-management-design/mobile-management-"+size.width+".png");
                    try{Files.createDirectories(output.getParent());ImageIO.write(image,"png",output.toFile());}catch(Exception e){throw new RuntimeException(e);}
                }
                JCheckBox policy=find(dialog.getContentPane(),JCheckBox.class).stream().filter(b->b.getText().equals("Keep employees signed in on this device")).findFirst().orElseThrow();
                assertFalse(policy.isSelected());assertTrue(policy.isEnabled());policy.doClick();
                assertTrue(find(dialog.getContentPane(),JButton.class).stream().anyMatch(b->b.getText().equals("Review changes")&&b.isEnabled()));
                policy.doClick();
                long enabled=find(dialog.getContentPane(),JCheckBox.class).stream().filter(JCheckBox::isSelected).count();assertEquals(3,enabled);
                assertTrue(find(dialog.getContentPane(),JButton.class).stream().anyMatch(b->b.getText().equals("Pair phone")&&b.isEnabled()));
                JButton repair=find(dialog.getContentPane(),JButton.class).stream().filter(b->b.getText().equals("Re-pair QR")).findFirst().orElseThrow();assertTrue(repair.isEnabled());
                assertTrue(find(dialog.getContentPane(),JButton.class).stream().anyMatch(b->b.getText().equals("Review changes")&&!b.isEnabled()));
                JTextField deviceName=find(dialog.getContentPane(),JTextField.class).stream().filter(field->"Device name".equals(field.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
                deviceName.setText("Receiving backup phone");
                assertFalse(repair.isEnabled());
                JButton review=find(dialog.getContentPane(),JButton.class).stream().filter(button->button.getText().equals("Review changes")).findFirst().orElseThrow();assertTrue(review.isEnabled());
                find(dialog.getContentPane(),JButton.class).stream().filter(button->button.getText().equals("Discard")).findFirst().orElseThrow().doClick();
                assertEquals("Receiving phone",deviceName.getText());assertFalse(review.isEnabled());
                JTextField search=find(dialog.getContentPane(),JTextField.class).stream().filter(field->"Search phones".equals(field.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();search.setText("Manager");
                assertEquals("Manager’s iPhone",deviceName.getText());
                search.setText("Old phone");
                assertEquals("Old phone",deviceName.getText());assertFalse(deviceName.isEnabled());assertFalse(repair.isEnabled());
                assertTrue(find(dialog.getContentPane(),JButton.class).stream().anyMatch(button->button.getText().equals("Restore device")&&button.isEnabled()));

            } finally {dialog.dispose();}
        });
    }
    private static void layout(Container container){container.doLayout();for(Component c:container.getComponents())if(c instanceof Container child)layout(child);}
    private static <T extends Component> java.util.List<T> find(Container container,Class<T> type){java.util.List<T> result=new java.util.ArrayList<>();for(Component c:container.getComponents()){if(type.isInstance(c))result.add(type.cast(c));if(c instanceof Container child)result.addAll(find(child,type));}return result;}
}
