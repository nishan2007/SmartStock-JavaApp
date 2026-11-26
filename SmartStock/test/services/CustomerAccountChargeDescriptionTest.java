package services;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CustomerAccountChargeDescriptionTest {
 @Test void manualChargesRequireMeaningfulDescriptionWithinLimit()throws Exception{
  for(String value:new String[]{"","   ","\n\t","x".repeat(1001)})assertThrows(LanCustomerAccountService.RuleViolation.class,()->LanCustomerAccountService.chargeDescription("CHARGE",value));
  assertThrows(LanCustomerAccountService.RuleViolation.class,()->LanCustomerAccountService.chargeDescription("CHARGE",null));
  assertEquals("Printing 10 shirts",LanCustomerAccountService.chargeDescription("CHARGE","  Printing 10 shirts  "));
  assertEquals("x".repeat(1000),LanCustomerAccountService.chargeDescription("CHARGE","x".repeat(1000)));
 }
 @Test void paymentFlowDoesNotRequireChargeDescription()throws Exception{
  assertNull(LanCustomerAccountService.chargeDescription("PAYMENT",null));
 }
 @Test void adjustmentRequestSendsDescriptionAndKeepsPaymentConstructorCompatible(){
  var request=new LanApiClient.CustomerAccountAdjustmentRequest(1,BigDecimal.TEN,"CHARGE",null,null,List.of(),"Custom printing");
  assertEquals("Custom printing",LanJson.create().toJsonTree(request).getAsJsonObject().get("description").getAsString());
  assertNull(new LanApiClient.CustomerAccountAdjustmentRequest(1,BigDecimal.TEN,"PAYMENT","CASH",null,List.of()).description());
 }
}
