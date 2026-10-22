package ui.helpers;

import services.LanCustomOrderCatalogAdminService.Item;
import java.util.List;

/** A parent photo wins; otherwise show photographed active variants without changing stored photos. */
public final class CustomItemPictures {
    private CustomItemPictures() { }
    public static List<TableImageHoverPreview.Picture> forItem(Item item) {
        if(item==null)return List.of();
        if(item.imageUrl()!=null&&!item.imageUrl().isBlank())
            return List.of(new TableImageHoverPreview.Picture(item.imageUrl(),item.name()));
        return variants(item);
    }
    public static List<TableImageHoverPreview.Picture> variants(Item item) {
        if(item==null||item.variants()==null)return List.of();
        return item.variants().stream().filter(v->v.active()&&v.imageUrl()!=null&&!v.imageUrl().isBlank())
            .map(v->new TableImageHoverPreview.Picture(v.imageUrl(),v.name())).toList();
    }
}
