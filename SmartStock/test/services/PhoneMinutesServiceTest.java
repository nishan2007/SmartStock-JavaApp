package services;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
class PhoneMinutesServiceTest {
 @Test void validatesPhoneAndWholeGyd()throws Exception {
  assertEquals("+5926000000",PhoneMinutesService.phone("+592 (600)-0000"));
  for(String phone:new String[]{"","123","600000x","6000000\nPAYMENT", "+1234567890123456"})assertThrows(SQLException.class,()->PhoneMinutesService.phone(phone));
  for(String amount:new String[]{"0","-1","1.5","10000000000"})assertThrows(SQLException.class,()->PhoneMinutesService.amount(new BigDecimal(amount)));
  assertEquals(new BigDecimal("100.00"),PhoneMinutesService.amount(new BigDecimal("100.00")));
 }
}
