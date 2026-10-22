package ui.helpers;

import services.LanCustomOrderCatalogAdminService;
import services.LanJson;
import org.junit.jupiter.api.Test;
import java.util.List;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomItemPicturesTest {
    private LanCustomOrderCatalogAdminService.Item item(String main){
        return LanJson.create().fromJson("""
            {"itemId":1,"name":"Shirt","imageUrl":"MAIN","variants":[
              {"variantId":1,"name":"Blue","imageUrl":"missing-blue.png","active":true},
              {"variantId":2,"name":"Red","imageUrl":"missing-red.png","active":true},
              {"variantId":3,"name":"No photo","imageUrl":"","active":true},
              {"variantId":4,"name":"Inactive","imageUrl":"missing-inactive.png","active":false}
            ]}
            """.replace("MAIN",main),LanCustomOrderCatalogAdminService.Item.class);
    }
    @Test void mainPhotoWinsAndMissingMainShowsOnlyPhotographedActiveVariants(){
        var main=CustomItemPictures.forItem(item("main.png"));assertEquals(1,main.size());assertEquals("main.png",main.get(0).url());
        var fallback=CustomItemPictures.forItem(item(""));assertEquals(List.of("Blue","Red"),fallback.stream().map(TableImageHoverPreview.Picture::label).toList());
        assertTrue(CustomItemPictures.forItem(null).isEmpty());
    }
    @Test void displayFallbackDoesNotBecomeASavedPrimaryOrGalleryPhoto()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var selector=ProductImageHelper.createImageSelector(null);
            selector.setAdditionalImageUrls(List.of("existing-gallery.png"));
            selector.setFallbackPictures(CustomItemPictures.variants(item("")));
            assertEquals("",selector.getImageUrl());
            assertEquals(List.of("existing-gallery.png"),selector.getAdditionalImageUrls());
            selector.setFallbackPictures(List.of());
            assertEquals("",selector.getImageUrl());
        });
    }
    @Test void gridIsBoundedAndClearingItRemovesPriorVariants()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var grid=new VariantPhotoGrid();
            var pictures=java.util.stream.IntStream.range(0,12).mapToObj(i->new TableImageHoverPreview.Picture("missing-"+i+".png","Variant "+i)).toList();
            grid.setPictures(pictures);assertEquals(9,grid.getComponentCount());assertTrue(grid.getToolTipText().contains("12"));
            grid.setPictures(List.of());assertEquals(0,grid.getComponentCount());
        });
    }
}
