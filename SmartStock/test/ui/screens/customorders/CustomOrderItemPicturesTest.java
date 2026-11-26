package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.CustomOrderDataService.*;
import services.LanJson;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderItemPicturesTest {
    private final CustomItemOption item=new CustomItemOption(1L,"Shirt","","Black",null,"INVENTORY","FIXED",BigDecimal.TEN,true,null,null,null,null,null,"item.png");
    private VariantOption variant(long id,String size,String photo){return new VariantOption(id,size,size,"Black",size,"Black",null,BigDecimal.TEN,photo);}
    @Test void singleSizeUsesItsPhotoAndMissingVariantPhotoFallsBackToItem(){
        assertEquals("small.png",CustomOrderItemPictures.pictures(item,variant(2,"S","small.png"),List.of()).get(0).url());
        var fallback=CustomOrderItemPictures.pictures(item,variant(3,"M",""),List.of()).get(0);
        assertEquals("item.png",fallback.url());assertEquals("M / Black",fallback.caption());
    }
    @Test void sizeSetShowsEverySelectedSizeOnceWithQuantityAndKeepsSharedPhoto(){
        var sizes=List.of(new CustomOrderSizeSetEditor.SizeQuantity(variant(2,"S","small.png"),10),new CustomOrderSizeSetEditor.SizeQuantity(variant(3,"M",null),3));
        var pictures=CustomOrderItemPictures.pictures(item,null,sizes);
        assertEquals(2,pictures.size());assertEquals("S / Black × 10",pictures.get(0).caption());
        assertEquals("small.png",pictures.get(0).url());assertEquals("item.png",pictures.get(1).url());
        assertTrue(CustomOrderItemPictures.pictures(null,null,sizes).isEmpty());
    }
    @Test void lanContractCarriesPhotosAndAcceptsOlderServersWithoutPhotos(){
        var json=LanJson.create();
        var server=json.fromJson(json.toJson(item),services.ServerCustomOrderDataService.CustomItemOption.class);
        assertEquals("item.png",server.imageUrl());
        assertEquals("item.png",json.fromJson(json.toJson(server),CustomItemOption.class).imageUrl());
        var variant=variant(2,"S","small.png");
        var serverVariant=json.fromJson(json.toJson(variant),services.ServerCustomOrderDataService.VariantOption.class);
        assertEquals("small.png",serverVariant.imageUrl());
        assertNull(json.fromJson("{\"customItemId\":1,\"name\":\"Shirt\"}",CustomItemOption.class).imageUrl());
    }
}
