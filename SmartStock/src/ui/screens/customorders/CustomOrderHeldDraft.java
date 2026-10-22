package ui.screens.customorders;

import services.CustomOrderDataService.*;
import java.util.List;

/** Serializable unfinished order; a hold never records payment or reserves stock. */
record CustomOrderHeldDraft(CustomerInfoPanel.Draft customer,boolean pricePending,boolean dueEnabled,
        String dueDate,String notes,String paymentMethod,String paymentReference,String deposit,
        String depositOverrideReason,List<Line> lines,List<Attachment> attachments){
    record Line(OrderLineRequest request,CustomItemOption item,VariantOption variant,List<String> files,String setId,CustomOrderShirtText.Saved personalization){
        Line(OrderLineRequest request,CustomItemOption item,VariantOption variant,List<String> files,String setId){this(request,item,variant,files,setId,null);}
        Line(OrderLineRequest request,CustomItemOption item,VariantOption variant,List<String> files){this(request,item,variant,files,null);}
    }
    record Attachment(String id,String name,long size,String sha256){}
}
