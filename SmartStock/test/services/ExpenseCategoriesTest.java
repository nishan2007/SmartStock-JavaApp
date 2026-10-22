package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseCategoriesTest {
    @Test void payrollAutomaticallyUsesSalaryRegardlessOfLegacyLabel() {
        assertEquals("Salary",ExpenseCategories.forSource("Payroll","PAYROLL"));
        assertEquals("Salary",ExpenseCategories.forSource("General","payroll"));
        for(String category:new String[]{"salary","Salaries"," Wages ","Payroll"})
            assertEquals("Salary",ExpenseCategories.normalize(category));
    }

    @Test void manualExpensesCanonicalizeCategoriesAndPreserveCustomNames() {
        assertEquals("Maintenance",ExpenseCategories.normalize("maintainence"));
        assertEquals("Inventory / Supplies",ExpenseCategories.normalize("inventory/supplies"));
        assertEquals("Marketing",ExpenseCategories.normalize("marketing"));
        assertEquals("Special event costs",ExpenseCategories.normalize(" Special event costs "));
        assertEquals("",ExpenseCategories.normalize(null));
        var entry=new ServerBalanceSheetService.ExpenseEntry(LocalDate.now(),"marketing","Vendor","Poster",
                BigDecimal.TEN,"CASH",null,"PAID");
        assertEquals("Marketing",entry.category());
        assertEquals("General",ExpenseCategories.forSource("General",""),"Keep historical custom labels");
    }
}
