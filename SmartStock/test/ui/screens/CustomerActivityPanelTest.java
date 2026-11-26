package ui.screens;

import org.junit.jupiter.api.Test;
import services.LanApiClient;
import services.LanJson;
import javax.swing.*;
import java.util.List;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CustomerActivityPanelTest {
    @Test void historyIncludesPaymentsAndChargesNewestFirstAndClearsOnCustomerChange() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            CustomerActivityPanel panel=new CustomerActivityPanel(()->{},()->{},()->{});
            var charge=LanJson.create().fromJson("{transactionType:'CHARGE',createdAtEpochMillis:1000,amount:2500,documentNumber:'INV-42',note:'<private & note>'}",LanApiClient.CustomerTransactionRecord.class);
            var payment=LanJson.create().fromJson("{transactionType:'PAYMENT',createdAtEpochMillis:2000,amount:1000,paymentMethod:'CASH',paymentReference:'Receipt 12'}",LanApiClient.CustomerTransactionRecord.class);
            panel.showHistory(new LanApiClient.CustomerTransactionResult(List.of(charge,payment),2,new BigDecimal("2500"),new BigDecimal("1000")));
            JScrollPane scroll=(JScrollPane)panel.getComponent(1);
            @SuppressWarnings("unchecked") JList<LanApiClient.CustomerTransactionRecord> list=(JList<LanApiClient.CustomerTransactionRecord>)scroll.getViewport().getView();
            assertEquals(2,list.getModel().getSize());
            assertEquals("PAYMENT",list.getModel().getElementAt(0).transactionType());
            assertEquals("CHARGE",list.getModel().getElementAt(1).transactionType());
            var rendered=list.getCellRenderer().getListCellRendererComponent(list,charge,1,false,false);
            assertNotNull(rendered);
            panel.message("Select a customer to view activity.");
            assertEquals(0,list.getModel().getSize());
            panel.showHistory(new LanApiClient.CustomerTransactionResult(List.of(),0,BigDecimal.ZERO,BigDecimal.ZERO));
            assertEquals(0,list.getModel().getSize());
        });
    }
}
