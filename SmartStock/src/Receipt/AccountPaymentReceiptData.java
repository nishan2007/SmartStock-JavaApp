package Receipt;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccountPaymentReceiptData {
    private final long transactionId;
    private final Integer locationId;
    private final String paymentId;
    private final Timestamp paymentTime;
    private final String storeName;
    private final String userName;
    private final String customerName;
    private final String accountNumber;
    private final String customerEmail;
    private final String paymentMethod;
    private final String paymentReference;
    private final String deviceName;
    private final String cashDrawerName;
    private final BigDecimal paymentAmount;
    private final BigDecimal accountBalanceAfter;
    private final List<AllocationLine> allocations;

    public AccountPaymentReceiptData(
            long transactionId,
            Integer locationId,
            String paymentId,
            Timestamp paymentTime,
            String storeName,
            String userName,
            String customerName,
            String accountNumber,
            String customerEmail,
            String paymentMethod,
            String paymentReference,
            String deviceName,
            String cashDrawerName,
            BigDecimal paymentAmount,
            BigDecimal accountBalanceAfter,
            List<AllocationLine> allocations
    ) {
        this.transactionId = transactionId;
        this.locationId = locationId;
        this.paymentId = clean(paymentId);
        this.paymentTime = paymentTime;
        this.storeName = clean(storeName);
        this.userName = clean(userName);
        this.customerName = clean(customerName);
        this.accountNumber = clean(accountNumber);
        this.customerEmail = clean(customerEmail);
        this.paymentMethod = clean(paymentMethod);
        this.paymentReference = clean(paymentReference);
        this.deviceName = clean(deviceName);
        this.cashDrawerName = clean(cashDrawerName);
        this.paymentAmount = money(paymentAmount);
        this.accountBalanceAfter = money(accountBalanceAfter);
        this.allocations = Collections.unmodifiableList(new ArrayList<>(allocations == null ? List.of() : allocations));
    }

    public long getTransactionId() {
        return transactionId;
    }

    public Integer getLocationId() {
        return locationId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public Timestamp getPaymentTime() {
        return paymentTime;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getUserName() {
        return userName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getCashDrawerName() {
        return cashDrawerName;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public BigDecimal getAccountBalanceAfter() {
        return accountBalanceAfter;
    }

    public List<AllocationLine> getAllocations() {
        return allocations;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }

    public record AllocationLine(
            String targetLabel,
            BigDecimal appliedAmount,
            BigDecimal chargeTotal,
            BigDecimal chargePaid,
            String paymentStatus,
            Timestamp chargeDate
    ) {
        public AllocationLine {
            targetLabel = clean(targetLabel);
            appliedAmount = money(appliedAmount);
            chargeTotal = money(chargeTotal);
            chargePaid = money(chargePaid);
            paymentStatus = clean(paymentStatus);
        }
    }
}
