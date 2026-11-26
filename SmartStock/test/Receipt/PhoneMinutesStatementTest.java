package Receipt;
import org.junit.jupiter.api.Test;
import services.LanApiClient.CustomerOpenBalance;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class PhoneMinutesStatementTest {
 @Test void statementsRetainPhoneAndPartialPaymentDetails()throws Exception {
  var rows=List.of(new CustomerOpenBalance("MINUTES",1L,"MIN-1 / +5926000000",Instant.parse("2026-11-05T14:00:00Z").toEpochMilli(),BigDecimal.valueOf(1000),BigDecimal.valueOf(200),BigDecimal.valueOf(800)));
  var zone=ZoneId.of("America/Guyana");String text=AccountStatementFormatter.format("Deckers","Example Customer","CA-1",rows,Instant.parse("2026-11-05T15:00:00Z"),zone);
  assertTrue(text.contains("+5926000000"));assertTrue(text.contains("1,000"));assertTrue(text.contains("800"));assertTrue(text.contains("2026-11-05"));
  var letter=new AccountStatementLetter("Deckers","Example Customer","CA-1",rows,Instant.parse("2026-11-05T15:00:00Z"),zone);assertEquals(1,letter.pageCount());assertEquals(BigDecimal.valueOf(800),letter.totalDue());
  Path out=Path.of("target","phone-minutes-preview");Files.createDirectories(out);javax.imageio.ImageIO.write(letter.renderPage(0,1.5),"png",out.resolve("statement.png").toFile());Files.writeString(out.resolve("statement.txt"),text);
 }
}
