package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Register-side accounting gateway. The store server owns all accounting SQL and authorization. */
public final class BalanceSheetService {
    private static final Gson GSON = LanJson.create();
    private BalanceSheetService() { }

    public static void addManualExpense(ExpenseEntry entry) throws SQLException {
        mutate("ADD_EXPENSE", object("expense", entry));
    }

    public static List<ExpenseOption> listDeletableExpenses(LocalDate from, LocalDate to, String status) throws SQLException {
        JsonObject body = range(from,to); body.addProperty("status",status);
        return array(read("DELETABLE_EXPENSES",body),"rows",ExpenseOption[].class);
    }

    public static void deleteManualExpense(long expenseId, LocalDate from, LocalDate to, String status) throws SQLException {
        JsonObject body=range(from,to);body.addProperty("expenseId",expenseId);body.addProperty("status",status);mutate("DELETE_EXPENSE",body);
    }

    public static List<ChequeDepositOption> listPendingChequeDeposits() throws SQLException {
        return array(read("PENDING_CHEQUES",new JsonObject()),"rows",ChequeDepositOption[].class);
    }

    public static void markChequeDeposited(ChequeDepositOption cheque, String notes) throws SQLException {
        JsonObject body=object("cheque",cheque);body.addProperty("notes",notes);mutate("DEPOSIT_CHEQUE",body);
    }

    public static List<PayableOption> listUnpaidPayables(LocalDate from, LocalDate to) throws SQLException {
        return array(read("UNPAID_PAYABLES",range(from,to)),"rows",PayableOption[].class);
    }

    public static void recordPayablePayment(long expenseId, LocalDate paymentDate, BigDecimal paymentAmount,
                                            String paymentMethod, String paymentReference) throws SQLException {
        JsonObject body=new JsonObject();body.addProperty("expenseId",expenseId);body.addProperty("paymentDate",paymentDate.toString());
        body.addProperty("paymentAmount",paymentAmount);body.addProperty("paymentMethod",paymentMethod);body.addProperty("paymentReference",paymentReference);
        mutate("PAY_PAYABLE",body);
    }

    public static BalanceSheet loadBalanceSheet(LocalDate from, LocalDate to, String storeZoneId) throws SQLException {
        return loadBalanceSheet(from,to,storeZoneId,List.of());
    }

    public static BalanceSheet loadBalanceSheet(LocalDate from, LocalDate to, String storeZoneId,
                                                List<Long> cashDrawerSessionIds) throws SQLException {
        JsonObject body=range(from,to);body.addProperty("storeZoneId",storeZoneId);body.add("cashDrawerSessionIds",GSON.toJsonTree(cashDrawerSessionIds));
        return GSON.fromJson(read("LOAD",body).get("sheet"),BalanceSheet.class);
    }

    public static long submitBalanceSheet(LocalDate from, LocalDate to, String storeZoneId, String notes) throws SQLException {
        return submitBalanceSheet(from,to,storeZoneId,notes,List.of());
    }

    public static long submitBalanceSheet(LocalDate from, LocalDate to, String storeZoneId, String notes,
                                          List<Long> cashDrawerSessionIds) throws SQLException {
        JsonObject body=range(from,to);body.addProperty("storeZoneId",storeZoneId);body.addProperty("notes",notes);
        body.add("cashDrawerSessionIds",GSON.toJsonTree(cashDrawerSessionIds));return mutate("SUBMIT",body).get("submissionId").getAsLong();
    }

    public static List<SubmissionOption> listSubmissions() throws SQLException {
        return array(read("SUBMISSIONS",new JsonObject()),"rows",SubmissionOption[].class);
    }

    public static BalanceSheet loadSubmission(long submissionId) throws SQLException {
        JsonObject body=new JsonObject();body.addProperty("submissionId",submissionId);return GSON.fromJson(read("SUBMISSION",body).get("sheet"),BalanceSheet.class);
    }

    public static DrawSessionRange findDrawSessionRange(String storeZoneId, LocalDate selectedDate) throws SQLException {
        List<DrawSessionRange> ranges=findDrawSessionRanges(storeZoneId,selectedDate,selectedDate);return ranges.isEmpty()?null:ranges.get(0);
    }

    public static List<DrawSessionRange> findDrawSessionRanges(String storeZoneId, LocalDate from, LocalDate to) throws SQLException {
        JsonObject body=range(from,to);body.addProperty("storeZoneId",storeZoneId);return array(read("DRAW_RANGES",body),"rows",DrawSessionRange[].class);
    }

    private static JsonObject read(String action,JsonObject body)throws SQLException{try{return LanApiClient.balanceSheetRead(action,body);}catch(Exception e){throw sql(e);}}
    private static JsonObject mutate(String action,JsonObject body)throws SQLException{try{return LanApiClient.balanceSheetMutation(action,body,UUID.randomUUID().toString());}catch(Exception e){throw sql(e);}}
    private static JsonObject range(LocalDate from,LocalDate to){JsonObject b=new JsonObject();b.addProperty("from",from.toString());b.addProperty("to",to.toString());return b;}
    private static JsonObject object(String key,Object value){JsonObject b=new JsonObject();b.add(key,GSON.toJsonTree(value));return b;}
    private static <T>List<T>array(JsonObject value,String key,Class<T[]>type){T[]rows=GSON.fromJson(value.get(key),type);return rows==null?List.of():List.of(rows);}
    private static SQLException sql(Exception e){return e instanceof SQLException s?s:new SQLException(e.getMessage(),e);}
    private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static String text(String value){return value==null||value.isBlank()?"Unknown":value;}

    public record ExpenseEntry(LocalDate expenseDate,String category,String payee,String description,BigDecimal amount,String paymentMethod,String paymentReference,String status){}
    public record ExpenseOption(long expenseId,LocalDate expenseDate,String category,String payee,String description,BigDecimal amount,String status){public String toString(){String n=payee==null||payee.isBlank()?(description==null||description.isBlank()?category:description):payee;return expenseDate+" - "+n+" - "+zero(amount).toPlainString();}}
    public record PayableOption(long expenseId,LocalDate expenseDate,String category,String payee,String description,BigDecimal amount){public String toString(){String n=payee==null||payee.isBlank()?(description==null||description.isBlank()?category:description):payee;return expenseDate+" - "+n+" - "+zero(amount).toPlainString();}}
    public record ChequeDepositOption(String sourceType,String sourceId,LocalDateTime chequeAt,String sourceLabel,String payer,String reference,BigDecimal amount){public String toString(){String n=payer==null||payer.isBlank()?sourceLabel:sourceLabel+" - "+payer;String ref=reference==null||reference.isBlank()?"":" / "+reference;return(chequeAt==null?"":chequeAt.toLocalDate()+" - ")+n+ref+" - "+zero(amount).toPlainString();}}
    public record SheetLine(String label,BigDecimal amount){}
    public record BankTransactionLine(String transaction,String direction,BigDecimal amount){}
    public record DrawSessionRange(long sessionId,LocalDate openedDate,LocalDate closedDate,String label,String status){}
    public record SubmissionOption(long submissionId,LocalDate periodStart,LocalDate periodEnd,LocalDateTime submittedAt,String submittedByName,BigDecimal balanceCf){public String toString(){return periodStart+" to "+periodEnd+" - "+text(submittedByName)+" - CF "+zero(balanceCf).toPlainString();}}
    public record BalanceSheet(Long submissionId,LocalDate periodStart,LocalDate periodEnd,LocalDateTime submittedAt,String submittedByName,String notes,List<SheetLine>income,List<SheetLine>receivables,List<SheetLine>expenses,List<SheetLine>payables,List<SheetLine>drawerCash,List<SheetLine>deviceSales,List<SheetLine>deviceOrders,List<SheetLine>devicePayments,List<SheetLine>accountPayments,List<BankTransactionLine>bankTransactions,List<ChequeDepositOption>pendingCheques,List<SheetLine>drawerChecks,BigDecimal cashInHand,BigDecimal balanceBf,BigDecimal totalIncome,BigDecimal totalReceivables,BigDecimal totalExpenses,BigDecimal totalPayables,BigDecimal balanceCf){}
}
