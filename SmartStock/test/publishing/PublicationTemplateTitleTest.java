package publishing;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class PublicationTemplateTitleTest{
 @Test void legacyTrailingSpacesAreNormalizedBeforeSharingWithoutChangingTheSource(){var p=new Publication();p.title="VISHAL CASH RECEIPT ";var clean=PublicationTemplates.clean(p);assertEquals("VISHAL CASH RECEIPT",clean.title);assertEquals("VISHAL CASH RECEIPT ",p.title);PublicationTemplates.requireTemplate(clean);}
}
