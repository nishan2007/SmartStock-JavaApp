package ui.screens.customorders;

import services.CustomOrderDataService.*;
import services.LanJson;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

/** Optional text belongs to an add-on on one physical shirt, never to a price. */
final class CustomOrderShirtText {
    record Slot(String key, String size, int number) {}
    record Saved(List<PrintAddonRequest> sharedAddons, List<String> texts) {}
    record Applied(OrderLineRequest request, Saved saved) {}
    static List<Slot> slots(List<CustomOrderSizeSetEditor.SizeQuantity> sizes, Long variantId, String size, int quantity) {
        List<Slot> result=new ArrayList<>();
        if(sizes.isEmpty()) append(result,variantId,size,quantity);
        else for(var selection:sizes) append(result,selection.variant().variantId(),selection.variant().effectiveSize(),selection.quantity());
        return List.copyOf(result);
    }
    private static void append(List<Slot> slots,Long variantId,String size,int quantity) {
        if(quantity<1 || quantity>5000 || slots.size()+quantity>5000)throw new IllegalArgumentException("Select between 1 and 5,000 shirts.");
        for(int i=1;i<=quantity;i++)slots.add(new Slot(key(variantId,i),size==null||size.isBlank()?"Item":size,i));
    }
    static String key(Long variantId,int number){return (variantId==null?"item":variantId.toString())+":"+number;}
    @SuppressWarnings("unchecked")
    static Map<String,String> values(Object value){return value instanceof Map<?,?>?(Map<String,String>)value:Map.of();}
    static void validate(List<Slot> slots,List<Map<String,String>> entries) {
        Set<String> allowed=new HashSet<>();slots.forEach(s->allowed.add(s.key()));
        for(var map:entries)for(var entry:map.entrySet()) {
            if(entry.getValue().length()>500)throw new IllegalArgumentException("Per-shirt text must be 500 characters or fewer.");
            if(!entry.getValue().isBlank()&&!allowed.contains(entry.getKey()))
                throw new IllegalArgumentException("Quantities or sizes changed. Open Names / per-shirt text to review the fields before adding this line.");
        }
    }
    static Applied apply(OrderLineRequest request,Slot slot,List<Map<String,String>> entries) {
        List<PrintAddonRequest> shared=request.printAddons()==null?List.of():request.printAddons();
        List<PrintAddonRequest> addons=new ArrayList<>();List<String> texts=new ArrayList<>();boolean any=false;
        for(int i=0;i<shared.size();i++) {
            var addon=shared.get(i);String text=i<entries.size()?entries.get(i).getOrDefault(slot.key(),"").trim():"";
            if(text.length()>500)throw new IllegalArgumentException("Per-shirt text must be 500 characters or fewer.");
            texts.add(text);any|=!text.isEmpty();
            String description=addon.printDescription()==null?"":addon.printDescription();
            if(!text.isEmpty())description+=(description.isBlank()?"":" | ")+"Text: "+text;
            addons.add(new PrintAddonRequest(addon.printMaterialId(),addon.materialName(),addon.printSizePresetId(),addon.printSizeName(),addon.pricingMode(),description,addon.printLineCount(),addon.printCharge()));
        }
        var json=LanJson.create().toJsonTree(request).getAsJsonObject();json.add("printAddons",LanJson.create().toJsonTree(addons));
        // Save the shared template even for blank shirts when another shirt has text.
        boolean configured=entries.stream().anyMatch(m->!m.isEmpty());
        return new Applied(LanJson.create().fromJson(json,OrderLineRequest.class),any||configured?new Saved(shared,List.copyOf(texts)):null);
    }
    record Editor(JPanel panel,Map<String,JTextField> fields) {
        Map<String,String> values(){Map<String,String> result=new LinkedHashMap<>();fields.forEach((key,field)->{if(!field.getText().isBlank())result.put(key,field.getText().trim());});return result;}
    }
    static Editor editor(List<Slot> slots,Map<String,String> previous) {
        JPanel fields=new JPanel(new GridBagLayout());fields.setBackground(ui.design.DeckersPalette.surface());
        Map<String,JTextField> inputs=new LinkedHashMap<>();int row=0;
        for(var slot:slots) {
            GridBagConstraints c=new GridBagConstraints();c.gridy=row++;c.insets=new Insets(4,6,4,6);c.anchor=GridBagConstraints.WEST;
            JLabel label=new JLabel(slot.size()+" — Shirt "+slot.number());label.setForeground(ui.design.DeckersPalette.text());fields.add(label,c);
            JTextField input=new JTextField(previous.getOrDefault(slot.key(),""),28);input.setBackground(ui.design.DeckersPalette.background());input.setForeground(ui.design.DeckersPalette.text());input.setCaretColor(ui.design.DeckersPalette.text());
            c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;fields.add(input,c);inputs.put(slot.key(),input);
        }
        return new Editor(fields,inputs);
    }
}
