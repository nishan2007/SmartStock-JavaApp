package Receipt;

import org.junit.jupiter.api.Test;
import services.LanApiClient.CustomerOpenBalance;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class AccountStatementLetterTest {
    @Test void usesUsLetterAndPrintsEveryPageThroughTheSameRenderer(){
        var rows=IntStream.range(0,80).mapToObj(i->new CustomerOpenBalance("SALE",(long)i,
                "A long receipt reference with several words "+i,1000,BigDecimal.TEN,BigDecimal.ONE,new BigDecimal("9"))).toList();
        var letter=new AccountStatementLetter("Deckers","SKELDON LINE PATH SECONDARY SCHOOL","0005",rows,Instant.EPOCH,ZoneId.of("America/Guyana"));
        assertEquals(new BigDecimal("720"),letter.totalDue());
        assertTrue(letter.pageCount()>1);
        var page=AccountStatementLetter.pageFormat();
        assertEquals(612,page.getWidth());assertEquals(792,page.getHeight());
        var image=new BufferedImage(612,792,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{
            for(int i=0;i<letter.pageCount();i++){
                assertEquals(Printable.PAGE_EXISTS,letter.print(g,page,i));
                assertEquals(612,letter.renderPage(i,1).getWidth());
            }
            assertEquals(Printable.NO_SUCH_PAGE,letter.print(g,page,letter.pageCount()));
        }finally{g.dispose();}
    }
    @Test void emptyAccountStillHasOnePrintablePage(){
        var letter=new AccountStatementLetter("Deckers","School","0005",List.of(),Instant.EPOCH,ZoneId.of("UTC"));
        assertEquals(1,letter.pageCount());assertEquals(BigDecimal.ZERO,letter.totalDue());
    }
}
