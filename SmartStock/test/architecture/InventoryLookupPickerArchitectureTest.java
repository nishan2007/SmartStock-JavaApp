package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryLookupPickerArchitectureTest {
    @Test
    void desktopLoadsLookupsFromComponentsThatAreActuallyDisplayed() throws Exception {
        String selector=Files.readString(Path.of("src/ui/components/ItemDetailsSelector.java"));
        String vendors=Files.readString(Path.of("src/ui/components/VendorSelector.java"));
        String departments=Files.readString(Path.of("src/ui/components/DepartmentSelector.java"));
        assertTrue(selector.contains("loadAfterDisplay(brandBox"));
        assertTrue(selector.contains("loadAfterDisplay(box,key"));
        assertFalse(selector.contains("loadAfterDisplay(this,key"));
        assertTrue(vendors.contains("loadAfterDisplay(vendorBox"));
        assertTrue(departments.contains("loadAfterDisplay(departmentBox"));
    }

    @Test
    void mobileWebOffersSearchableOrganizationLookups() throws Exception {
        String server=Files.readString(Path.of("src/services/MobileItemWebServer.java"));
        String web=Files.readString(Path.of("src/mobile-web/app.js"));
        assertTrue(server.contains("out.put(\"itemTypes\",itemTypeRows(c))"));
        assertTrue(server.contains("\"categoryId\",r.getInt(2)"));
        assertTrue(server.contains("out.put(\"brands\""));
        assertTrue(web.contains("function lookup(name,label,values,current='',required=false)"));
        assertTrue(web.contains("lookup('vendorName','Vendor'"));
        assertTrue(web.contains("lookup('itemTypeName','Item type'"));
        assertTrue(web.contains("lookup('brandName','Brand'"));
        assertTrue(web.contains("lookup('shelfName','Sales shelf'"));
        assertTrue(web.contains("lookup('storageShelfName','Storage shelf'"));
        assertTrue(web.contains("refreshItemTypes()"));
    }
}
