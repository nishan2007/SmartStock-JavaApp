package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductMergeServiceTest {
    private ProductMergeService.Item item(int id,String name,String size,String color,String brand,List<String> codes) {
        return new ProductMergeService.Item(id,name,"SKU-"+id,size,color,"",brand,"","",BigDecimal.TEN,
                BigDecimal.ONE,3,"","",codes,"","");
    }
    @Test void matchesFormattingButPreservesVariantDifferences() {
        var a=item(1,"School-Shirt","L","Red","Acme",List.of());
        assertFalse(ProductMergeService.matchReason(a,item(2," SCHOOL shirt ","l","red","ACME",List.of())).isEmpty());
        assertEquals("",ProductMergeService.matchReason(a,item(2,"School Shirt","XL","Red","Acme",List.of())));
        assertEquals("",ProductMergeService.matchReason(a,item(2,"School Shirt","L","Blue","Acme",List.of())));
        assertEquals("",ProductMergeService.matchReason(a,item(2,"School Shirt","L","Red","Other",List.of())));
        assertEquals("",ProductMergeService.matchReason(item(1,"","","","",List.of()),item(2,"","","","",List.of())));
    }
    @Test void barcodeMatchRemainsReviewableEvenWhenNamesDiffer() {
        assertTrue(ProductMergeService.matchReason(item(1,"Pencil","","","",List.of("ABC-123")),
                item(2,"Pencils","","","",List.of("abc123"))).startsWith("Matching barcode"));
    }
    @Test void combinesDescriptionsWithoutDiscardingDistinctDetails() {
        assertEquals("Stamp ink\nInk-30-RD",ProductMergeCoordinator.combineDescriptions("Stamp ink"," stamp INK \nInk-30-RD"));
        assertEquals("Original",ProductMergeCoordinator.combineDescriptions("Original",null));
    }
}
