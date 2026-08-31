package architecture;

import org.junit.jupiter.api.Test;
import services.CustomerCardService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.*;

final class CustomerCardArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void schemaAndUpgradeCoverCustomerCards()throws Exception{
        String base=read("database/v1/local/001_schema.sql"),migration=read("database/migrations/v1_after/20260827150000_customer_card_templates.sql");
        String contract=read("src/services/SchemaContractService.java"),installer=read("src/services/LanApiSchemaInstaller.java");
        assertTrue(base.contains("customer_card_template_layout_data text DEFAULT '{\"version\":1"));
        assertTrue(base.contains("\"name\":\"Individual\""));
        assertTrue(base.contains("customer_card_template_slot integer DEFAULT 4 NOT NULL"));
        assertTrue(base.contains("customer_since integer"));
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS customer_since integer"));
        assertTrue(migration.contains("customer_types_card_template_slot_chk"));
        assertTrue(migration.contains("UPDATE public.company_customization SET customer_card_template_layout_data="));
        assertTrue(migration.contains("LOWER(BTRIM(name)) IN ('teacher','teachers')"));assertTrue(migration.contains("'general','personal / regular','personal','regular'"));
        assertTrue(contract.contains("20260827150000_customer_card_templates.sql"));
        assertTrue(contract.contains("ensureCustomerCardTemplatesUpgrade(connection)"));
        assertTrue(installer.contains("ensureCustomerCardTemplatesUpgrade(connection)"));
    }

    @Test void rendererIsLandscapeCr80AndHasFiveDefaults(){
        assertEquals(1013,CustomerCardService.WIDTH);assertEquals(638,CustomerCardService.HEIGHT);
        var templates=CustomerCardService.defaults();assertEquals(5,templates.size());
        assertTrue(templates.stream().allMatch(CustomerCardService.Template::configured));assertEquals("Government",templates.get(4).name());
        var data=new CustomerCardService.CardData(1,"Teacher Name","Teachers","ACC-100","555","a@b.com",2020,1);
        var image=CustomerCardService.render(data,templates.get(0));assertEquals(1013,image.getWidth());assertEquals(638,image.getHeight());
        assertDoesNotThrow(()->CustomerCardService.render(data,templates.get(4)));
    }

    @Test void uiApiPrintingAndAuditAreWired()throws Exception{
        String accounts=read("src/ui/screens/CustomerAccounts.java"),details=read("src/ui/screens/CustomerAccountDetails.java");
        String service=read("src/services/CustomerCardService.java"),server=read("src/services/LanApiServer.java");
        assertTrue(accounts.contains("Preview Card"));assertTrue(details.contains("Save Card PDF"));
        assertTrue(service.contains("Sides.ONE_SIDED"));assertTrue(service.contains("3.375"));assertTrue(service.contains("2.125"));
        assertTrue(server.contains("/v1/customer-accounts/card-audit"));
    }

    @Test void cardElementsHaveEditablePersistentLayout()throws Exception{
        var defaults=CustomerCardService.layoutRects("");
        assertTrue(defaults.keySet().containsAll(java.util.List.of("header","name","type","account","phone","email","since","barcode","barcodeText")));
        String changed=CustomerCardService.updateLayoutRect("","name",new Rectangle(140,180,500,80));
        assertEquals(new Rectangle(140,180,500,80),CustomerCardService.layoutRects(changed).get("name"));
        String ui=read("src/ui/screens/CompanyCustomization.java");
        assertTrue(ui.contains("class CustomerCardLayoutCanvas"));
        assertTrue(ui.contains("addMouseMotionListener(mouse)"));
        assertTrue(ui.contains("Drag the orange bottom-right handle to resize"));
        assertTrue(ui.contains("Customer Card Header"));
        assertTrue(ui.contains("Background Image"));
    }

    @Test void customerSinceIsStoredValidatedAndDefaultsOnCreate()throws Exception{
        String service=read("src/services/LanCustomerAccountService.java");
        assertTrue(service.contains("if(r.customerId()==null&&since==null)since=currentYear"));
        assertTrue(service.contains("customer_since,customer_photo_url) VALUES"));
        assertTrue(service.contains("Customer Since must be a four-digit year"));
        assertTrue(read("src/ui/screens/CustomerAccounts.java").contains("Customer Since (year):"));
        assertTrue(read("src/ui/screens/CustomerAccountDetails.java").contains("Customer Since (year):"));
    }

    @Test void customerPhotosExpiryAndGovernmentUpgradeAreWired()throws Exception{
        String migration=read("database/migrations/v1_after/20260830120000_customer_photos_card_expiry_government.sql");
        assertTrue(migration.contains("customer_photo_url"));assertTrue(migration.contains("customer_card_expires_on"));
        assertTrue(migration.contains("'Government'"));assertTrue(migration.contains("customer_card_template_slot=5"));
        assertTrue(read("src/services/OneDriveImageCloudProvider.java").contains("customer-photos"));
        assertTrue(read("src/services/LanSalesService.java").contains("This customer card has expired"));
        assertTrue(read("src/ui/screens/CustomerAccountDetails.java").contains("Choose Customer Photo or Business Logo"));
    }
}
