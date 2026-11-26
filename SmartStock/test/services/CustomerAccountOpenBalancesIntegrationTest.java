package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomerAccountOpenBalancesIntegrationTest {
    @Test void excludesUnchargedInvoicesAndInvoiceBilledOrdersButPreservesManualCharges()throws Exception{
        String url=System.getProperty("smartstock.test.statementJdbc","");
        assumeTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/statement_test"));
        try(var c=DriverManager.getConnection(url,"postgres","")){
            c.setAutoCommit(false);
            try(var s=c.createStatement()){
                s.execute("CREATE TEMP TABLE sales(sale_id int,customer_id int,receipt_number text,created_at timestamptz,total_amount numeric,returned_amount numeric,amount_paid numeric,payment_method text,payment_status text)");
                s.execute("CREATE TEMP TABLE invoices(invoice_id bigint,customer_id int,invoice_number text,created_at timestamptz,total_amount numeric,amount_paid numeric,balance_due numeric,payment_status text,status text)");
                s.execute("CREATE TEMP TABLE custom_orders(custom_order_id bigint,customer_id int,order_number text,created_at timestamptz,total_amount numeric,amount_paid numeric,balance_due numeric,payment_status text,status text,order_type text,invoice_billed boolean)");
                s.execute("CREATE TEMP TABLE customer_account_transactions(customer_id int,invoice_id bigint,transaction_type text)");
                s.execute("INSERT INTO sales VALUES(1,5,'SCHOOL-PURCHASE',now(),24160,0,0,'ACCOUNT','UNPAID'),(2,5,'PAID',now(),100,0,100,'ACCOUNT','PAID')");
                // Actual regression: an OPEN invoice with no account charge added 279,000.11.
                s.execute("INSERT INTO invoices VALUES(9,5,'UNCHARGED-279000',now(),279000.11,0,279000.11,'UNPAID','OPEN')");
                var school=LanCustomerAccountService.readOpenBalances(c,5,new BigDecimal("24160"));
                assertEquals(List.of("SCHOOL-PURCHASE"),school.stream().map(r->r.get("documentNumber")).toList());
                assertEquals(0,new BigDecimal("24160").compareTo(total(school)));
                s.execute("INSERT INTO invoices VALUES(10,5,'CHARGED-PARTIAL',now(),500,100,400,'PARTIAL','OPEN'),(11,5,'CANCELLED',now(),900,0,900,'UNPAID','CANCELLED')");
                s.execute("INSERT INTO customer_account_transactions VALUES(5,10,'INVOICE_CREDIT'),(5,10,'INVOICE_CREDIT'),(5,11,'INVOICE_CREDIT'),(999,9,'INVOICE_CREDIT')");
                s.execute("INSERT INTO custom_orders VALUES(1,5,'BILLED-ON-INVOICE',now(),500,0,500,'UNPAID','PENDING','CUSTOM',true),(2,5,'CUSTOM-PARTIAL',now(),100,25,75,'PARTIAL','PENDING','CUSTOM',false),(3,5,'CANCELLED-ORDER',now(),500,0,500,'UNPAID','CANCELLED','CUSTOM',false)");
                var rows=LanCustomerAccountService.readOpenBalances(c,5,new BigDecimal("24660"));
                assertEquals(4,rows.size());
                assertEquals(1,rows.stream().filter(r->"CHARGED-PARTIAL".equals(r.get("documentNumber"))).count());
                assertTrue(rows.stream().noneMatch(r->r.get("documentNumber").toString().contains("CANCELLED")||r.get("documentNumber").equals("BILLED-ON-INVOICE")));
                assertEquals("ACCOUNT",rows.get(0).get("documentType"));
                assertEquals(0,new BigDecimal("100").compareTo((BigDecimal)rows.get(0).get("balanceDue")));
                assertEquals(0,new BigDecimal("24735").compareTo(total(rows)));
            }finally{c.rollback();}
        }
    }
    private static BigDecimal total(List<Map<String,Object>> rows){return rows.stream().map(r->(BigDecimal)r.get("balanceDue")).reduce(BigDecimal.ZERO,BigDecimal::add);}
}
