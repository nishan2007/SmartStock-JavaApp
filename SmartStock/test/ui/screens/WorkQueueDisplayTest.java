package ui.screens;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.util.List;
import services.OrdersWorkQueueService.Row;
import static org.junit.jupiter.api.Assertions.*;
class WorkQueueDisplayTest {
    @Test void headerDateAdvancesAcrossMidnight()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            JPanel header=WorkQueue.createHeader("Store","Display",java.time.LocalDate.of(2026,11,23),null);
            WorkQueue.updateHeaderDate(header,java.time.LocalDate.of(2026,11,24));
            JPanel calendar=(JPanel)header.getClientProperty("calendar");
            JPanel lines=(JPanel)((BorderLayout)calendar.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            assertEquals("Tuesday",((JLabel)lines.getComponent(0)).getText());
            assertEquals("24 Nov 2026",((JLabel)lines.getComponent(1)).getText());
        });
    }
    @Test void refreshFailuresDistinguishOutagesFromServerAndClientErrors(){
        assertTrue(WorkQueueDisplay.refreshFailure(new services.LanApiClient.LanApiException("SERVER_UNREACHABLE","offline",true)).contains("unreachable"));
        assertEquals("Store server could not load queue [INTERNAL_ERROR]",WorkQueueDisplay.refreshFailure(new services.LanApiClient.LanApiException("INTERNAL_ERROR","failed",true)));
        assertTrue(WorkQueueDisplay.refreshFailure(new services.LanApiClient.LanApiException("SERVER_NOT_VERIFIED","pair",false)).contains("Exit fullscreen to pair"));
        assertEquals("Queue request failed [SERVER_NOT_PRIMARY]",WorkQueueDisplay.refreshFailure(new services.LanApiClient.LanApiException("SERVER_NOT_PRIMARY","standby",true)));
        assertEquals("Unable to load queue [IllegalArgumentException]",WorkQueueDisplay.refreshFailure(new IllegalArgumentException("private response details")));
    }
    @Test void displayCardsCannotOpenEditorsAndHaveNoMyWorkControl() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            OrdersWorkQueuePanel panel=new OrdersWorkQueuePanel(null,row->fail("Display opened an editor"),true);
            assertFalse(panel.mine.isVisible());
            panel.setSnapshot(List.of(new Row("SALES_TASK",1,"ST-1","NEW","Customer","123","Staff","Copies",null,false,false,false,java.math.BigDecimal.ZERO,1)),ZoneId.of("America/Guyana"));
            inspect(panel);
            panel.setSnapshot(List.of(),ZoneId.of("America/Guyana"));
            assertEquals(data.DatabaseMode.DISPLAY,data.DatabaseMode.from("display"));
        });
    }
    private void inspect(Container parent){for(Component c:parent.getComponents()){
        if(c instanceof JComponent card && card.getClientProperty("row")!=null){assertFalse(card.isFocusable());assertNull(card.getActionMap().get("open"));}
        if(c instanceof Container nested)inspect(nested);
    }}
    @Test void savedDisplayModeLoadsWithoutDatabaseCredentials(){
        String previous=System.getProperty("smartstock.db.mode");
        try {
            System.setProperty("smartstock.db.mode","DISPLAY");
            var config=data.DatabaseConfig.load();
            assertEquals(data.DatabaseMode.DISPLAY,config.mode());
            assertTrue(config.jdbcUrl()==null || config.jdbcUrl().isBlank());assertEquals("",config.dbUser());assertEquals("",config.dbPassword());
            var denied=assertThrows(java.sql.SQLException.class,()->data.DB.getConnection());assertEquals("28000",denied.getSQLState());
        } finally {if(previous==null)System.clearProperty("smartstock.db.mode");else System.setProperty("smartstock.db.mode",previous);}
    }
    @Test void displayConfigurationDiscardsDatabaseCredentials(){
        var config=data.DatabaseConfig.fromForm(data.DatabaseMode.DISPLAY,"jdbc:postgresql://secret/db","server-user","server-password","POS-SERVER",8443,1,60);
        assertEquals("",config.jdbcUrl());assertEquals("",config.dbUser());assertEquals("",config.dbPassword());assertFalse(config.hasPrimaryConnection());
    }
    @Test void revocationAndExpiredCredentialsClearPrivateDataButOutagesDoNot(){
        assertTrue(WorkQueueDisplay.authenticationFailure("DEVICE_CREDENTIAL_INVALID"));
        assertTrue(WorkQueueDisplay.authenticationFailure("DEVICE_REVOKED"));
        assertTrue(WorkQueueDisplay.authenticationFailure("DEVICE_CREDENTIAL_EXPIRED"));
        assertTrue(WorkQueueDisplay.authenticationFailure("DISPLAY_DEVICE_REQUIRED"));
        assertFalse(WorkQueueDisplay.authenticationFailure("SERVER_UNREACHABLE"));
    }
}
