package ui.screens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import services.LanApiClient;
import services.LanJson;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomerTransactionHistoryDesignTest {
    static CustomerTransactionHistory fixture() {
        CustomerTransactionHistory screen=new CustomerTransactionHistory(1,"CA-000142 · Morgan Ellis",false);
        var sale=LanJson.create().fromJson("{eventId:'SALE:42',createdAtEpochMillis:1794864000000,transactionType:'SALE',documentType:'SALE',documentNumber:'0001-0002-009887',storeName:'Example Store',userName:'Staff member',deviceName:'Register 1',cashDrawerName:'Drawer 1',paymentMethod:'ACCOUNT',amount:10260,chargeTotal:10260,paymentStatus:'UNPAID',documentStatus:'COMPLETED',note:'Shirts and stationery'}",LanApiClient.CustomerTransactionRecord.class);
        var payment=LanJson.create().fromJson("{eventId:'LEDGER:43',createdAtEpochMillis:1794950400000,transactionType:'PAYMENT',documentType:'ACCOUNT',documentNumber:'PAY-000341',storeName:'Example Store',userName:'Staff member',paymentMethod:'CASH',paymentReference:'Payment receipt',amount:-4500,chargeTotal:4500,note:'Customer account payment'}",LanApiClient.CustomerTransactionRecord.class);
        screen.applyTransactions(new LanApiClient.CustomerTransactionResult(List.of(sale,payment),2,new BigDecimal("10260"),new BigDecimal("4500")));
        return screen;
    }
    @Test void readableColumnsSortAndSelectTheMatchingDetailsAfterSearch()throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            var screen=fixture();
            try {
                JTable table=(JTable)find(screen,"transactionTable");assertEquals(7,table.getColumnCount());
                assertEquals("Payment",table.getValueAt(0,1));table.setRowSelectionInterval(0,0);
                assertTrue(((JTextArea)find(screen,"selectedDetails")).getText().contains("Payment receipt"));
                ((JTextField)find(screen,"searchField")).setText("Shirts");assertEquals(1,table.getRowCount());
                table.setRowSelectionInterval(0,0);assertTrue(((JTextArea)find(screen,"selectedDetails")).getText().contains("Register 1"));
                ((JTextField)find(screen,"searchField")).setText("[");assertEquals(0,table.getRowCount());
            }finally{screen.dispose();}
        });
    }
    static Object find(CustomerTransactionHistory screen,String name){try{var f=CustomerTransactionHistory.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}catch(Exception e){throw new RuntimeException(e);}}
    public static void main(String[] args)throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(()->{var screen=fixture();screen.setSize(1440,900);screen.setVisible(true);screen.validate();
            JTable table=(JTable)find(screen,"transactionTable");table.setRowSelectionInterval(0,0);
            var image=new java.awt.image.BufferedImage(1440,900,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();screen.paintAll(g);g.dispose();
            try{javax.imageio.ImageIO.write(image,"png",new java.io.File(args[0]));}catch(Exception e){throw new RuntimeException(e);}
        });
    }
}
