package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderCustomerSearchArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void customerSearchCoversEveryRequestedIdentityAndUiDisplaysIt()throws Exception{
        String service=read("src/services/ServerCustomOrderDataService.java");
        assertTrue(service.contains("COALESCE(phone, '') ILIKE ?"));
        assertTrue(service.contains("COALESCE(email,'') ILIKE ?"));
        assertTrue(service.contains("COALESCE(account_number,'') ILIKE ?"));
        assertTrue(service.contains("COALESCE(account_number,'') AS account_number"),
                "The computed account number must retain the ResultSet label used by the mapper");
        assertTrue(service.contains("COALESCE(email,'') AS email"),
                "The computed email must retain the ResultSet label used by the mapper");
        String panel=read("src/ui/screens/customorders/CustomerInfoPanel.java");
        assertTrue(panel.contains("Search Customer:"));
        assertTrue(panel.contains("Account #:"));
        assertTrue(panel.contains("Email:"));
        assertTrue(panel.contains("Search by customer name, email, phone number, or account number"));
    }
}
