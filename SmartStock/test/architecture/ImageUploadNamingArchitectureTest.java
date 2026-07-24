package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageUploadNamingArchitectureTest {
    @Test
    void everyBusinessUploadSuppliesDescriptiveNamingContext() throws Exception {
        String newItem = source("src/ui/screens/NewItem.java");
        String editItem = source("src/ui/screens/EditItem.java");
        String customItems = source("src/ui/screens/customorders/CustomOrderItems.java");
        String employees = source("src/ui/screens/EmployeeManagement.java");
        String productHelper = source("src/ui/helpers/ProductImageHelper.java");
        String photoService = source("src/services/EmployeePhotoService.java");
        String documentService = source("src/services/EmployeeDocumentService.java");

        assertTrue(newItem.contains("new ProductImageHelper.ProductImageNaming(draft.name(),draft.brandName(),draft.itemTypeName(),draft.size(),\"\")"));
        assertTrue(editItem.contains("new ProductImageHelper.ProductImageNaming(name,brandName,itemTypeName,size,\"\")"));
        assertTrue(customItems.contains("parentItem.itemType(),\"\",variantName"));
        assertTrue(employees.contains("requestedEmployeeName=fullName"));
        assertTrue(productHelper.contains("safeNaming.variantName(), \"product-image\""));
        assertTrue(photoService.contains("employeeName, \"employee-photo\""));
        assertTrue(documentService.contains("employeeName, \"id-card-document\""));
        assertFalse(productHelper.contains("products/\" + System.currentTimeMillis()"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
