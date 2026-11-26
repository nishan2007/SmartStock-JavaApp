package ui.screens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import services.LanApiClient;
import services.LanJson;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomerAccountsDesignTest {
    /** Standalone, read-only visual preview of the production screen with fictional records. */
    public static void main(String[] args) throws Exception {
        var theme = ui.helpers.ThemeManager.class.getDeclaredField("cachedDarkMode");
        theme.setAccessible(true); theme.set(null, args.length > 0 && args[0].equals("dark"));
        ui.helpers.ThemeManager.applyLookAndFeelDefaults();
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            try {
                CustomerAccounts screen = fixture();
                screen.setTitle("SmartStock Customers — Design Preview (fictional data)");
                JMenuBar menu = new JMenuBar();
                for(String label:new String[]{"Point of Sale","Operations","Orders","Inventory","Employee","Admin","Status","Notifications"}) menu.add(new JMenu(label));
                screen.setJMenuBar(menu);
                ((JLabel)field(screen,"companyFooter")).setText("Demo Company");
                screen.setBounds(30,10,args.length>1?Integer.parseInt(args[1]):1440,args.length>2?Integer.parseInt(args[2]):1024);
                screen.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                ui.helpers.ThemeManager.applyToWindow(screen);
                screen.setVisible(true);
                screen.validate();
                if(args.length>3) {
                    javax.swing.Timer capture = new javax.swing.Timer(600,e -> {
                        try {
                            var image = new java.awt.image.BufferedImage(screen.getWidth(),screen.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                            Graphics2D g = image.createGraphics(); screen.paintAll(g); g.dispose();
                            javax.imageio.ImageIO.write(image,"png",new java.io.File(args[3]));
                        } catch(Exception ex) { throw new RuntimeException(ex); }
                    }); capture.setRepeats(false);capture.start();
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    static final String[] NAMES = {"Morgan Ellis", "Riley James", "Casey Palmer", "Dakota Turner", "Avery Lane", "Skyler Moore", "Parker Nelson", "Reese Wallace"};
    static CustomerAccounts fixture() throws Exception {
        CustomerAccounts screen = new CustomerAccounts(false);
        List<LanApiClient.CustomerAccountRecord> rows = new java.util.ArrayList<>();
        int[] balances = {4500,12750,0,1200,8300,375,2100,6450};
        int[] orders = {1500,3000,0,0,2250,0,600,1200};
        for (int i=0;i<NAMES.length;i++) {
            String json = "{\"customerId\":"+(i+1)+",\"accountNumber\":\"CA-"+String.format("%06d",142+i)+"\",\"name\":\""+NAMES[i]+"\",\"phone\":\"+5920000000\",\"email\":\"customer"+i+"@example.com\",\"creditLimit\":20000,\"currentBalance\":"+balances[i]+",\"availableCredit\":"+(20000-balances[i])+",\"customOrderDue\":"+orders[i]+",\"totalDue\":"+(balances[i]+orders[i])+",\"business\":"+(i==1||i==4)+",\"active\":"+(i!=7)+",\"accountNotes\":\"Prefers email invoices. Regular customer for home goods.\",\"customerTypeId\":1,\"customerTypeName\":\"Individual\",\"customerSince\":2024,\"whatsappOptIn\":true}";
            var record=LanJson.create().fromJson(json,com.google.gson.JsonObject.class);
            if(i==1||i==4)record.addProperty("customerTypeName","Business");
            rows.add(LanJson.create().fromJson(record, LanApiClient.CustomerAccountRecord.class));
        }
        Class<?> snapshot = Class.forName("ui.screens.CustomerAccounts$CustomerAccountsSnapshot");
        var constructor = snapshot.getDeclaredConstructor(List.class); constructor.setAccessible(true);
        var apply = CustomerAccounts.class.getDeclaredMethod("applyCustomers", snapshot); apply.setAccessible(true);
        apply.invoke(screen, constructor.newInstance(rows));
        JTable table=(JTable)field(screen,"customerTable");
        int morganRow=table.convertRowIndexToView(0);
        table.setRowSelectionInterval(morganRow,morganRow);
        return screen;
    }
    static Object field(CustomerAccounts screen, String name) throws Exception {
        Field field = CustomerAccounts.class.getDeclaredField(name); field.setAccessible(true); return field.get(screen);
    }
    @Test void directoryFiltersNumericSortSelectionAndCancellationKeepTheCorrectAccount() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            CustomerAccounts screen = null;
            try {
                screen = fixture();
                JTable table = (JTable)field(screen,"customerTable");
                assertEquals(5,table.getColumnCount());
                assertEquals("Riley James",table.getValueAt(0,0));
                assertEquals(10,table.getRowSorter().getSortKeys().get(0).getColumn());
                assertEquals(SortOrder.DESCENDING,table.getRowSorter().getSortKeys().get(0).getSortOrder());
                int inactiveRow=table.convertRowIndexToView(7);
                Component inactive=table.prepareRenderer(table.getCellRenderer(inactiveRow,0),inactiveRow,0);
                assertEquals(ui.design.DeckersPalette.blend(ui.design.DeckersPalette.surface(),ui.design.DeckersPalette.CORAL,.15),inactive.getBackground());
                assertEquals("Morgan Ellis",((JLabel)field(screen,"customerHeading")).getText());
                JLabel[] totals = (JLabel[])field(screen,"accountTotals");
                assertEquals("G$6,000", totals[3].getText());
                assertEquals("G$15,500", totals[4].getText());
                ((JButton)field(screen,"businessFilter")).doClick();
                assertEquals(2,table.getRowCount());
                assertNull(field(screen,"selectedCustomerId"));
                assertFalse(((JButton)field(screen,"recordPaymentButton")).isEnabled());
                ((JButton)field(screen,"allFilter")).doClick();
                var header=table.getTableHeader();
                Rectangle creditHeader=header.getHeaderRect(2);
                header.dispatchEvent(new java.awt.event.MouseEvent(header,java.awt.event.MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(),0,creditHeader.x+creditHeader.width/2,10,1,false,java.awt.event.MouseEvent.BUTTON1));
                assertEquals("Casey Palmer",table.getValueAt(0,0));
                header.dispatchEvent(new java.awt.event.MouseEvent(header,java.awt.event.MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(),0,creditHeader.x+creditHeader.width/2,10,1,false,java.awt.event.MouseEvent.BUTTON1));
                assertEquals("Riley James",table.getValueAt(0,0));
                table.setRowSelectionInterval(0,0);
                assertEquals(2,field(screen,"selectedCustomerId"));
                ((JTextField)field(screen,"searchField")).setText("morgan");
                assertEquals(1,table.getRowCount());
                table.setRowSelectionInterval(0,0);
                assertEquals(1,field(screen,"selectedCustomerId"));
                ((JTextField)field(screen,"searchField")).setText("[");
                assertEquals(0,table.getRowCount());
                ((JTextField)field(screen,"searchField")).setText("");
                ((JButton)field(screen,"activeFilter")).doClick();
                assertEquals(7,table.getRowCount());
                JTabbedPane tabs=(JTabbedPane)field(screen,"accountTabs");
                assertEquals(4,tabs.getTabCount());
                assertEquals("Activity",tabs.getTitleAt(2));
                assertEquals("More",tabs.getTitleAt(3));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { if(screen!=null)screen.dispose(); }
        });
    }

    @Test void compactWorkspaceRetainsFormViewportAndThemeRefreshRetainsCustomTabs() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            CustomerAccounts screen = null;
            try {
                screen = fixture();
                screen.addNotify();
                Method density = CustomerAccounts.class.getDeclaredMethod("applyWorkspaceDensity");
                density.setAccessible(true);
                JLabel[] totals = (JLabel[])field(screen,"accountTotals");
                for (int height : new int[]{960, 720}) {
                    screen.setSize(1280, height);
                    density.invoke(screen);
                    screen.validate();
                    for (JLabel total : totals) {
                        assertTrue(total.getHeight() >= total.getPreferredSize().height,
                                total.getText() + " needs " + total.getPreferredSize().height + "px but has "
                                        + total.getHeight() + "px at window height " + height + "px");
                    }
                }
                JTabbedPane tabs = (JTabbedPane)field(screen,"accountTabs");
                assertTrue(tabs.getSelectedComponent().getHeight()>100,"The profile must retain a usable scrollable viewport at 720p");
                JButton save = (JButton)field(screen,"saveButton");
                assertTrue(save.isVisible());
                assertEquals("Save Changes",save.getText());
                ui.helpers.ThemeManager.applyToWindow(screen);
                assertTrue(tabs.getUI().getClass().getName().startsWith("ui.screens.CustomerAccounts$"),"Theme refresh must retain the orange selected-tab treatment");
                assertEquals("G$6,000",((JLabel[])field(screen,"accountTotals"))[3].getText());
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { if(screen!=null)screen.dispose(); }
        });
    }
}
