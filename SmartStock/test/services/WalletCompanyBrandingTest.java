package services;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WalletCompanyBrandingTest {
    private final WalletCompanyBranding.Info info=new WalletCompanyBranding.Info("Existing Company","logo-reference","Main Street","123456","company@example.com","Service first");
    @Test void defaultDraftUsesExistingCompanyAndNormalizedLogo() throws Exception {
        var result=WalletCompanyBranding.initial(WalletBadgeTemplate.defaults(),info,
                path->{assertEquals("logo-reference",path);return new BufferedImage(60,30,BufferedImage.TYPE_INT_RGB);});
        assertEquals("Existing Company",result.company());
        var logo=WalletBadgeTemplate.decodeImage(result.logoPng());
        assertEquals(480,logo.getWidth());assertEquals(150,logo.getHeight());
        assertEquals(4,result.fields().stream().filter(f->f.label().startsWith("COMPANY ")).count());
        assertTrue(result.fields().stream().filter(f->f.label().startsWith("COMPANY ")).noneMatch(WalletBadgeTemplate.Field::visible));
    }
    @Test void customizedSavedTemplateIsNotOverwrittenOnOpen() throws Exception {
        var t=WalletBadgeTemplate.defaults();
        var custom=new WalletBadgeTemplate("Custom Brand",t.title(),t.background(),t.foreground(),t.labelColor(),t.fields(),"","",75,50,true);
        assertSame(custom,WalletCompanyBranding.initial(custom,info,path->{fail("Must not load company images for a custom template");return null;}));
    }
    @Test void explicitPullPreservesLayoutAndDoesNotDuplicateContactFields() throws Exception {
        var t=WalletBadgeTemplate.defaults();
        var custom=new WalletBadgeTemplate("Custom Brand","My badge","#112233",t.foreground(),t.labelColor(),
                List.of(new WalletBadgeTemplate.Field(true,"backFields","TEXT","NOTICE","Keep this text")),"","",50,75,true);
        var once=WalletCompanyBranding.apply(custom,info,path->null);
        var twice=WalletCompanyBranding.apply(once,info,path->null);
        assertEquals(once,twice);assertEquals("Existing Company",twice.company());assertEquals("#112233",twice.background());
        assertEquals(custom.fields().get(0),twice.fields().get(0));assertEquals(50,twice.logoScale());assertTrue(twice.employeePhoto());
    }
    @Test void missingLogoDoesNotEraseExistingLogo() throws Exception {
        var seeded=WalletCompanyBranding.apply(WalletBadgeTemplate.defaults(),info,path->new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB));
        assertEquals(seeded.logoPng(),WalletCompanyBranding.apply(seeded,info,path->null).logoPng());
    }
}
