package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MakeSaleSearchImagePreviewArchitectureTest {
    @Test
    void catalogResponsesCarryTheProductImageReference() throws Exception {
        String client = source("src/services/LanApiClient.java");
        String server = source("src/services/LanApiServer.java");

        assertTrue(client.contains("int quantityOnHand, String brandName, String imageUrl, String searchableText"));
        assertTrue(server.contains("COALESCE(p.image_url, '') AS image_url"));
        assertTrue(server.contains("row.put(\"imageUrl\", rs.getString(\"image_url\"))"));
    }

    @Test
    void saleSearchUsesDelayedCancellableOffThreadHoverPreview() throws Exception {
        String source = source("src/ui/screens/MakeASale.java");

        assertTrue(source.contains("new javax.swing.Timer(300,"));
        assertTrue(source.contains("imagePreviewWorker = new SwingWorker<>()"));
        assertTrue(source.contains("ImageCacheManager.loadImage(imageUrl)"));
        assertTrue(source.contains("generation == imagePreviewGeneration"));
        assertTrue(source.contains("imagePreviewWorker.cancel(true)"));
        assertTrue(source.contains("popupMenuWillBecomeInvisible"));
        assertTrue(source.contains("mouseExited(java.awt.event.MouseEvent e)"));
    }

    @Test
    void imageReferenceColumnRemainsHiddenAndRowsStayCompact() throws Exception {
        String source = source("src/ui/screens/MakeASale.java");

        assertTrue(source.contains("searchResultsTable.setRowHeight(24)"));
        assertTrue(source.contains("setSearchResultColumnWidth(10, 0, 0, 0)"));
        assertTrue(source.contains("\"No Image\""));
        assertTrue(source.contains("\"Image unavailable\""));
    }

    @Test
    void saleSearchHidesProductIdLeadsWithBrandAndFormatsPriceAsCurrency() throws Exception {
        String source = source("src/ui/screens/MakeASale.java");

        assertTrue(source.contains("setSearchResultColumnWidth(0, 0, 0, 0)"));
        assertTrue(source.contains("convertColumnIndexToView(9)"));
        assertTrue(source.contains("searchResultsTable.moveColumn(brandViewColumn, 0)"));
        assertTrue(source.contains("utils.CurrencyFormatter.format(number)"));
        assertTrue(source.contains("value instanceof Number number"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath));
    }
}
