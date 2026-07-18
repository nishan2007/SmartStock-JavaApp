package Receipt;

import services.LanApiClient;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Register-side payment receipt gateway. */
public final class AccountPaymentReceiptBuilder {
    private AccountPaymentReceiptBuilder(){}
    public static AccountPaymentReceiptData loadPaymentReceipt(int customerId,long transactionId)throws SQLException{
        try{LanApiClient.AccountPaymentReceiptPayload p=LanApiClient.loadAccountPaymentReceipt(customerId,transactionId);List<AccountPaymentReceiptData.AllocationLine>a=new ArrayList<>();if(p.allocations()!=null)for(LanApiClient.AccountPaymentAllocation x:p.allocations())a.add(new AccountPaymentReceiptData.AllocationLine(x.targetLabel(),x.appliedAmount(),x.chargeTotal(),x.chargePaid(),x.paymentStatus(),x.chargeDateEpochMillis()<=0?null:new Timestamp(x.chargeDateEpochMillis())));return new AccountPaymentReceiptData(p.transactionId(),p.locationId(),p.paymentId(),p.paymentTimeEpochMillis()<=0?null:new Timestamp(p.paymentTimeEpochMillis()),p.storeName(),p.userName(),p.customerName(),p.accountNumber(),p.customerEmail(),p.paymentMethod(),p.paymentReference(),p.deviceName(),p.cashDrawerName(),p.paymentAmount(),p.accountBalanceAfter(),a);}catch(Exception e){throw new SQLException("Unable to load customer payment receipt.",e);}
    }
}
