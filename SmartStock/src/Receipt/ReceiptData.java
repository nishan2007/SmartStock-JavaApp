package Receipt;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReceiptData {
    private final int saleId;
    private final String receiptNumber;
    private final Timestamp saleTime;
    private final String storeName;
    private final String cashierName;
    private final String customerName;
    private final String accountNumber;
    private final String paymentMethod;
    private final String paymentStatus;
    private final String deviceId;
    private final BigDecimal subtotalAmount;
    private final BigDecimal discountPercent;
    private final BigDecimal discountAmount;
    private final BigDecimal totalAmount;
    private final BigDecimal amountPaid;
    private final BigDecimal returnedAmount;
    private final BigDecimal cashCollected;
    private final BigDecimal changeDue;
    private final List<ReceiptItem> items;

    public ReceiptData(
            int saleId,
            String receiptNumber,
            Timestamp saleTime,
            String storeName,
            String cashierName,
            String customerName,
            String accountNumber,
            String paymentMethod,
            String paymentStatus,
            String deviceId,
            BigDecimal subtotalAmount,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal returnedAmount,
            BigDecimal cashCollected,
            BigDecimal changeDue,
            List<ReceiptItem> items
    ) {
        this.saleId = saleId;
        this.receiptNumber = receiptNumber == null ? "" : receiptNumber;
        this.saleTime = saleTime;
        this.storeName = storeName == null ? "" : storeName;
        this.cashierName = cashierName == null ? "" : cashierName;
        this.customerName = customerName == null ? "" : customerName;
        this.accountNumber = accountNumber == null ? "" : accountNumber;
        this.paymentMethod = paymentMethod == null ? "" : paymentMethod;
        this.paymentStatus = paymentStatus == null ? "" : paymentStatus;
        this.deviceId = deviceId == null ? "" : deviceId;
        this.subtotalAmount = money(subtotalAmount);
        this.discountPercent = money(discountPercent);
        this.discountAmount = money(discountAmount);
        this.totalAmount = money(totalAmount);
        this.amountPaid = money(amountPaid);
        this.returnedAmount = money(returnedAmount);
        this.cashCollected = cashCollected;
        this.changeDue = changeDue;
        this.items = Collections.unmodifiableList(new ArrayList<>(items == null ? List.of() : items));
    }

    public int getSaleId() {
        return saleId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Timestamp getSaleTime() {
        return saleTime;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getCashierName() {
        return cashierName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getReturnedAmount() {
        return returnedAmount;
    }

    public BigDecimal getCashCollected() {
        return cashCollected;
    }

    public BigDecimal getChangeDue() {
        return changeDue;
    }

    public List<ReceiptItem> getItems() {
        return items;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
