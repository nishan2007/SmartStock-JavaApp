package services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerBalanceSheetRevisionTest {
    private static final LocalDate DAY=LocalDate.of(2026,8,6);

    @Test
    void manualExpenseReplacementPreservesNonManualSnapshotAmounts() {
        List<ServerBalanceSheetService.SheetLine> snapshot=List.of(
                new ServerBalanceSheetService.SheetLine("Payroll - Alex",new BigDecimal("100")),
                new ServerBalanceSheetService.SheetLine("General - Store",new BigDecimal("150")));
        List<ServerBalanceSheetService.EditableExpense> before=List.of(expense(1,"General","Store","50","PAID"));
        List<ServerBalanceSheetService.EditableExpense> after=List.of(expense(1,"General","Store","75","PAID"));

        List<ServerBalanceSheetService.SheetLine> revised=ServerBalanceSheetService.replaceManualExpenseLines(
                snapshot,before,after,"PAID","No expenses");

        assertEquals(new BigDecimal("100"),amount(revised,"Payroll - Alex"));
        assertEquals(new BigDecimal("175"),amount(revised,"General - Store"));
    }

    @Test
    void movingManualExpenseBetweenPaidAndUnpaidUpdatesOnlyThoseSections() {
        List<ServerBalanceSheetService.EditableExpense> before=List.of(expense(7,"Utilities","GPL","20","PAID"));
        List<ServerBalanceSheetService.EditableExpense> after=List.of(expense(7,"Utilities","GPL","20","UNPAID"));

        List<ServerBalanceSheetService.SheetLine> paid=ServerBalanceSheetService.replaceManualExpenseLines(
                List.of(new ServerBalanceSheetService.SheetLine("Utilities - GPL",new BigDecimal("20"))),before,after,"PAID","No expenses");
        List<ServerBalanceSheetService.SheetLine> unpaid=ServerBalanceSheetService.replaceManualExpenseLines(
                List.of(new ServerBalanceSheetService.SheetLine("No payables",BigDecimal.ZERO)),before,after,"UNPAID","No payables");

        assertEquals(List.of(new ServerBalanceSheetService.SheetLine("No expenses",BigDecimal.ZERO)),paid);
        assertEquals(new BigDecimal("20"),amount(unpaid,"Utilities - GPL"));
    }

    @Test
    void otherIncomeReplacementDoesNotChangeSalesOrDrawerAmounts() {
        List<ServerBalanceSheetService.SheetLine> snapshot=List.of(
                new ServerBalanceSheetService.SheetLine("SALE CASH",new BigDecimal("500")),
                new ServerBalanceSheetService.SheetLine("OTHER CASH",new BigDecimal("30")));

        List<ServerBalanceSheetService.SheetLine> revised=ServerBalanceSheetService.replaceOtherCash(snapshot,new BigDecimal("80"));

        assertEquals(new BigDecimal("500"),amount(revised,"SALE CASH"));
        assertEquals(new BigDecimal("80"),amount(revised,"OTHER CASH"));
    }

    private static ServerBalanceSheetService.EditableExpense expense(long id,String category,String payee,String amount,String status){
        return new ServerBalanceSheetService.EditableExpense(id,DAY,category,payee,"",new BigDecimal(amount),"CASH","",status);
    }
    private static BigDecimal amount(List<ServerBalanceSheetService.SheetLine> rows,String label){
        return rows.stream().filter(row->label.equals(row.label())).findFirst().orElseThrow().amount();
    }
}
