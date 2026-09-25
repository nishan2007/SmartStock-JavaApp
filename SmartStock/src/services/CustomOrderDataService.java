package services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Register-safe custom-order catalog and checkout facade. */
public final class CustomOrderDataService {
    private CustomOrderDataService() { }

    public static List<CustomItemOption> listActiveItems() throws SQLException {
        return call(LanApiClient::loadCustomOrderItems);
    }

    public static List<VariantOption> listActiveVariants(long customItemId) throws SQLException {
        return call(() -> LanApiClient.loadCustomOrderVariants(customItemId));
    }

    public static List<PrintMaterialOption> listActivePrintMaterials() throws SQLException {
        return call(LanApiClient::loadCustomOrderPrintMaterials);
    }

    public static List<PrintSizePresetOption> listActivePrintSizePresets(long printMaterialId) throws SQLException {
        return call(() -> LanApiClient.loadCustomOrderPrintSizePresets(printMaterialId));
    }

    public static List<String> listActiveDesignPlacements() throws SQLException {
        return call(LanApiClient::loadCustomOrderDesignPlacements);
    }

    public static CatalogSnapshot loadCatalogSnapshot() throws SQLException {
        return call(LanApiClient::loadCustomOrderCatalogSnapshot);
    }

    public static List<CustomerOption> searchCustomers(String search) throws SQLException {
        return call(() -> LanApiClient.searchCustomOrderCustomers(search));
    }

    public static List<EmployeeOption> listActiveEmployees() throws SQLException {
        return call(LanApiClient::loadCustomOrderEmployees);
    }

    /** Customer resolution is a server-only transaction helper. */
    public static int resolveOrderCustomerId(Connection ignored, CustomerOption selectedCustomer,
                                             String name, String phone) throws SQLException {
        throw new SQLException("Customer resolution is only available inside the SmartStock Server Service.");
    }

    public static String saveCustomOrder(OrderSaveRequest request) throws SQLException {
        return call(() -> LanApiClient.saveCustomOrder(request, UUID.randomUUID().toString()));
    }

    public static LookupResult lookupCustomItem(String search) throws SQLException {
        return call(() -> LanApiClient.lookupCustomOrderItem(search));
    }

    private static <T> T call(ThrowingSupplier<T> action) throws SQLException {
        try {
            return action.get();
        } catch (Exception ex) {
            if (ex instanceof SQLException sqlException) throw sqlException;
            throw new SQLException("The custom-order request could not be completed by the SmartStock server.", ex);
        }
    }

    private interface ThrowingSupplier<T> { T get() throws Exception; }

    public record CustomItemOption(Long customItemId, String name, String size, String color, String sku, String productType, String pricingType,
                                   BigDecimal fixedPrice, boolean hasVariants, BigDecimal areaPrice,
                                   String areaPriceUnit, String dimensionUnit, BigDecimal maxWidth,
                                   BigDecimal maxLength) {
        @Override public String toString() {
            String label = attributeLabel(name,size,color);
            if (sku != null && !sku.isBlank()) label += " [" + sku + "]";
            if (hasVariants) return label + " (variants)";
            if ("FIXED".equals(pricingType) && fixedPrice != null) return label + " ($" + fixedPrice + ")";
            if ("AREA".equals(pricingType)) return label + " (area pricing)";
            return label;
        }
    }

    public record VariantOption(Long variantId, String name, String size, String color, String effectiveSize, String effectiveColor, String sku, BigDecimal fixedPrice) {
        @Override public String toString() {
            return (sku == null || sku.isBlank() ? attributeLabel(name,effectiveSize,effectiveColor) : attributeLabel(name,effectiveSize,effectiveColor) + " [" + sku + "]")
                    + (fixedPrice == null ? "" : " ($" + fixedPrice + ")");
        }
    }

    private static String attributeLabel(String name,String size,String color){StringBuilder out=new StringBuilder(name==null?"":name);if(size!=null&&!size.isBlank())out.append(" / ").append(size);if(color!=null&&!color.isBlank())out.append(" / ").append(color);return out.toString();}

    public record LookupResult(Long customItemId, Long customVariantId) { }

    public record PrintMaterialOption(Long printMaterialId, String materialName) {
        @Override public String toString() { return materialName; }
    }

    public record PrintSizePresetOption(Long printSizePresetId, Long printMaterialId, String presetName,
                                        String pricingMode, BigDecimal fixedPrice) {
        @Override public String toString() {
            return presetName + (fixedPrice == null ? "" : " ($" + fixedPrice + ")");
        }
    }

    public record CustomerOption(Integer customerId, String name, String phone, String accountNumber, String email,boolean requireChargeAuthorization,boolean customOrderDiscountEnabled,BigDecimal customOrderDiscountPercent) {
        @Override public String toString() {
            String account=accountNumber==null||accountNumber.isBlank()?"":accountNumber+" - ";
            String contact=email!=null&&!email.isBlank()?email:phone;
            return account+name+(contact == null || contact.isBlank() ? "" : " (" + contact + ")");
        }
    }

    public record CatalogSnapshot(List<CustomItemOption> items,
                                  List<PrintMaterialOption> materials,
                                  List<String> placements,
                                  List<CustomerOption> initialCustomers) { }

    public record EmployeeOption(Integer userId, String name) {
        @Override public String toString() { return name; }
    }

    public record OrderSaveRequest(
            CustomerOption selectedCustomer, String customerName, String customerPhone, LocalDate dueDate,
            BigDecimal total, BigDecimal amountPaid, BigDecimal balanceDue, String paymentMethod,
            String paymentReference, String paymentStatus, Integer takenByUserId, String takenByName,
            Integer locationId, String locationName, String deviceId, String deviceName,
            BigDecimal minimumDepositRequired, String depositOverrideReason, Integer depositOverrideByUserId,
            String depositOverrideByName, String orderNotes, List<OrderLineRequest> lines,
            String depositApprovalToken,LanApiClient.ChargeAuthorization authorization,boolean applyCustomerDiscount) {
        public OrderSaveRequest(CustomerOption selectedCustomer,String customerName,String customerPhone,LocalDate dueDate,
                                BigDecimal total,BigDecimal amountPaid,BigDecimal balanceDue,String paymentMethod,
                                String paymentReference,String paymentStatus,Integer takenByUserId,String takenByName,
                                Integer locationId,String locationName,String deviceId,String deviceName,
                                BigDecimal minimumDepositRequired,String depositOverrideReason,Integer depositOverrideByUserId,
                                String depositOverrideByName,String orderNotes,List<OrderLineRequest> lines,String depositApprovalToken){
            this(selectedCustomer,customerName,customerPhone,dueDate,total,amountPaid,balanceDue,paymentMethod,
                    paymentReference,paymentStatus,takenByUserId,takenByName,locationId,locationName,deviceId,deviceName,
                    minimumDepositRequired,depositOverrideReason,depositOverrideByUserId,depositOverrideByName,orderNotes,
                    lines,depositApprovalToken,null,true);
        }
        public OrderSaveRequest(CustomerOption selectedCustomer,String customerName,String customerPhone,LocalDate dueDate,BigDecimal total,BigDecimal amountPaid,BigDecimal balanceDue,String paymentMethod,String paymentReference,String paymentStatus,Integer takenByUserId,String takenByName,Integer locationId,String locationName,String deviceId,String deviceName,BigDecimal minimumDepositRequired,String depositOverrideReason,Integer depositOverrideByUserId,String depositOverrideByName,String orderNotes,List<OrderLineRequest>lines,String depositApprovalToken,LanApiClient.ChargeAuthorization authorization){this(selectedCustomer,customerName,customerPhone,dueDate,total,amountPaid,balanceDue,paymentMethod,paymentReference,paymentStatus,takenByUserId,takenByName,locationId,locationName,deviceId,deviceName,minimumDepositRequired,depositOverrideReason,depositOverrideByUserId,depositOverrideByName,orderNotes,lines,depositApprovalToken,authorization,true);}
    }

    public record OrderLineRequest(
            Long customItemId, Long customVariantId, String itemName, String variantName, String pricingType,
            BigDecimal unitPrice, String customizationDetails, String orderInstructions, BigDecimal widthValue,
            BigDecimal lengthValue, String dimensionUnit, BigDecimal areaValue, String areaUnit, BigDecimal areaPrice,
            BigDecimal baseItemPrice, Long printMaterialId, String printMaterialName, Long printSizePresetId,
            String printSizeName, BigDecimal printCharge, int printLineCount, BigDecimal originalLineTotal,
            BigDecimal lineDiscountPercent, BigDecimal lineDiscountAmount, Integer lineDiscountByUserId,
            String lineDiscountByName, String lineDiscountReason, BigDecimal minimumDepositPercent,
            BigDecimal originalBasePrice, BigDecimal priceOverridePrice, String priceOverrideReason,
            Integer priceOverrideByUserId, String priceOverrideByName, List<PrintAddonRequest> printAddons,
            String lineDiscountApprovalToken, String priceOverrideApprovalToken, String itemSize, String itemColor) {
        public OrderLineRequest(Long customItemId,Long customVariantId,String itemName,String variantName,String pricingType,BigDecimal unitPrice,String customizationDetails,String orderInstructions,BigDecimal widthValue,BigDecimal lengthValue,String dimensionUnit,BigDecimal areaValue,String areaUnit,BigDecimal areaPrice,BigDecimal baseItemPrice,Long printMaterialId,String printMaterialName,Long printSizePresetId,String printSizeName,BigDecimal printCharge,int printLineCount,BigDecimal originalLineTotal,BigDecimal lineDiscountPercent,BigDecimal lineDiscountAmount,Integer lineDiscountByUserId,String lineDiscountByName,String lineDiscountReason,BigDecimal minimumDepositPercent,BigDecimal originalBasePrice,BigDecimal priceOverridePrice,String priceOverrideReason,Integer priceOverrideByUserId,String priceOverrideByName,List<PrintAddonRequest>printAddons,String lineDiscountApprovalToken,String priceOverrideApprovalToken){this(customItemId,customVariantId,itemName,variantName,pricingType,unitPrice,customizationDetails,orderInstructions,widthValue,lengthValue,dimensionUnit,areaValue,areaUnit,areaPrice,baseItemPrice,printMaterialId,printMaterialName,printSizePresetId,printSizeName,printCharge,printLineCount,originalLineTotal,lineDiscountPercent,lineDiscountAmount,lineDiscountByUserId,lineDiscountByName,lineDiscountReason,minimumDepositPercent,originalBasePrice,priceOverridePrice,priceOverrideReason,priceOverrideByUserId,priceOverrideByName,printAddons,lineDiscountApprovalToken,priceOverrideApprovalToken,null,null);}
    }

    public record PrintAddonRequest(Long printMaterialId, String materialName, Long printSizePresetId,
                                    String printSizeName, String pricingMode, String printDescription,
                                    int printLineCount, BigDecimal printCharge) { }
}
