package ui.screens.customorders;

import com.google.gson.*;
import services.ShirtDesignerService;
import services.LanCustomOrderCatalogAdminService.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Explicit preset mapping: never infer production methods from material names. */
final class ShirtSetupDialog {
    private record Choice(long id,String label){public String toString(){return label;}}
    static JsonObject edit(Component owner,JsonObject saved,State state){
        JsonObject config=saved==null||saved.size()==0?ShirtDesignerService.defaults():saved.deepCopy();
        JPanel panel=new JPanel(new GridBagLayout());int row=0;
        JCheckBox enabled=new JCheckBox("Enable designer on website",config.get("websiteEnabled").getAsBoolean());row=add(panel,row,"Publication",enabled);
        JComboBox<String> shirtType=new JComboBox<>(new String[]{"T-shirt","Polo"});shirtType.setSelectedIndex(config.has("shirtType")&&config.get("shirtType").getAsString().equals("COLLARED")?1:0);
        JComboBox<String> neckline=new JComboBox<>(new String[]{"Round neck","V-neck"});neckline.setSelectedIndex(config.has("neckline")&&config.get("neckline").getAsString().equals("V")?1:0);neckline.setEnabled(shirtType.getSelectedIndex()==0);
        row=add(panel,row,"Shirt type",shirtType);
        JLabel neckLabel=new JLabel("T-shirt neckline");GridBagConstraints nc=new GridBagConstraints();nc.gridy=row++;nc.gridx=0;nc.anchor=GridBagConstraints.WEST;nc.insets=new Insets(5,8,5,8);panel.add(neckLabel,nc);nc.gridx=1;nc.fill=GridBagConstraints.HORIZONTAL;panel.add(neckline,nc);
        Runnable showNeck=()->{boolean tee=shirtType.getSelectedIndex()==0;neckline.setVisible(tee);neckLabel.setVisible(tee);neckline.getParent().revalidate();};showNeck.run();shirtType.addActionListener(e->showNeck.run());
        JComboBox<String> fabric=new JComboBox<>(new String[]{"Normal","Dry-fit"});fabric.setSelectedIndex(config.has("fabric")&&config.get("fabric").getAsString().equals("DRY_FIT")?1:0);row=add(panel,row,"Fabric",fabric);
        row=add(panel,row,"Preview",new JLabel("Automatic 3D model · front and back · product color swatches"));
        JPanel basic=panel;int basicRow=row;panel=new JPanel(new GridBagLayout());row=0;
        JTextField front=new JTextField(config.get("frontMockup").getAsString(),28),back=new JTextField(config.get("backMockup").getAsString(),28);
        Runnable updateStyle=()->{String type=shirtType.getSelectedIndex()==0?"TEE":"COLLARED",neck=type.equals("COLLARED")?"COLLAR":neckline.getSelectedIndex()==0?"ROUND":"V";neckline.setEnabled(type.equals("TEE"));
            List<String> builtins=List.of("/shop/shirt-front.png","/shop/shirt-back.png","/shop/shirt-vneck-front.png","/shop/shirt-collared-front.png","/shop/shirt-collared-back.png");
            if(builtins.contains(front.getText().trim()))front.setText(ShirtDesignerService.defaultMockup(type,neck,false));if(builtins.contains(back.getText().trim()))back.setText(ShirtDesignerService.defaultMockup(type,neck,true));};
        shirtType.addActionListener(e->updateStyle.run());neckline.addActionListener(e->updateStyle.run());
        // Bundled mockups remain available for the precise artwork editor.
        JTextArea colors=new JTextArea(5,28);for(var entry:config.getAsJsonObject("colors").entrySet())colors.append(entry.getKey()+"="+entry.getValue().getAsString()+"\n");
        row=add(panel,row,"Shirt color swatches (variant color=#RRGGBB)",new JScrollPane(colors));
        JTextField vinyl=new JTextField(String.join(",",config.getAsJsonArray("vinylColors").asList().stream().map(JsonElement::getAsString).toList()),28);row=add(panel,row,"Vinyl swatches (comma separated)",vinyl);
        List<Choice> choices=new ArrayList<>();choices.add(new Choice(0,"Choose a fixed-price preset"));
        for(Preset p:state.presets())if(p.active()&&"FIXED_PRESET".equals(p.pricingMode())){
            Material m=state.materials().stream().filter(x->x.materialId()==p.materialId()&&x.active()).findFirst().orElse(null);
            if(m!=null)choices.add(new Choice(p.presetId(),m.name()+" / "+p.name()+" — "+p.price()));
        }
        basicRow=add(basic,basicRow,"Customer add-ons",new JLabel("All active materials and presets are loaded automatically, including new ones."));
        basicRow=add(basic,basicRow,"Artwork",new JLabel("Fixed presets: text or logo. Per-line presets: one editable text line per add-on."));
        Map<String,JTextField> fields=new LinkedHashMap<>();Map<String,JComboBox<Choice>> presets=new LinkedHashMap<>();
        for(String area:ShirtDesignerService.AREAS){JsonObject a=config.getAsJsonObject("areas").getAsJsonObject(area);
            row=add(panel,row,area,new JLabel(area.equals("CHEST")?"Wearer's left chest; logo-size transfer":"Large print; full-sheet transfer"));
            for(String key:List.of("widthIn","heightIn","x","y","w","h")){JTextField field=new JTextField(a.get(key).getAsString(),10);fields.put(area+"."+key,field);row=add(panel,row,key+(key.endsWith("In")?" (inches)":" (0–1 of mockup)"),field);}
            for(String key:List.of("singlePresetId","fullPresetId")){JComboBox<Choice> combo=new JComboBox<>(choices.toArray(Choice[]::new));long id=a.get(key).getAsLong();choices.stream().filter(x->x.id()==id).findFirst().ifPresent(combo::setSelectedItem);presets.put(area+"."+key,combo);row=add(panel,row,key.startsWith("single")?"Single-color vinyl":"Full-color transfer",combo);}
        }
        JPanel advanced=panel;advanced.setVisible(false);JButton advancedToggle=new JButton("Print areas, pricing and colors…");advancedToggle.addActionListener(e->{advanced.setVisible(!advanced.isVisible());advanced.revalidate();});
        JPanel content=new JPanel();content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));content.add(basic);content.add(advancedToggle);content.add(advanced);
        JScrollPane scroll=new JScrollPane(content);scroll.setPreferredSize(new Dimension(760,600));
        while(JOptionPane.showConfirmDialog(owner,scroll,"Shirt designer setup",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){try{
            config.addProperty("fabric",fabric.getSelectedIndex()==1?"DRY_FIT":"NORMAL");config.addProperty("websiteEnabled",enabled.isSelected());config.addProperty("frontMockup",front.getText().trim());config.addProperty("backMockup",back.getText().trim());
            config.addProperty("shirtType",shirtType.getSelectedIndex()==0?"TEE":"COLLARED");config.addProperty("neckline",shirtType.getSelectedIndex()==1?"COLLAR":neckline.getSelectedIndex()==0?"ROUND":"V");
            config.addProperty("useSystemPresets",true);
            JsonObject swatches=new JsonObject();for(String line:colors.getText().split("\\R")){if(line.isBlank())continue;String[] pair=line.split("=",2);if(pair.length!=2)throw new IllegalArgumentException("Use color name=#RRGGBB, one per line.");swatches.addProperty(pair[0].trim(),pair[1].trim());}config.add("colors",swatches);
            JsonArray inks=new JsonArray();for(String ink:vinyl.getText().split(","))inks.add(ink.trim());config.add("vinylColors",inks);
            for(var entry:fields.entrySet()){String[] parts=entry.getKey().split("\\.");config.getAsJsonObject("areas").getAsJsonObject(parts[0]).addProperty(parts[1],Double.parseDouble(entry.getValue().getText().trim()));}
            for(var entry:presets.entrySet()){String[] parts=entry.getKey().split("\\.");config.getAsJsonObject("areas").getAsJsonObject(parts[0]).addProperty(parts[1],((Choice)entry.getValue().getSelectedItem()).id());}
            return ShirtDesignerService.configuration(config,enabled.isSelected());
        }catch(Exception e){advanced.setVisible(true);JOptionPane.showMessageDialog(owner,e.getMessage(),"Shirt setup",JOptionPane.ERROR_MESSAGE);}}
        return null;
    }
    private static int add(JPanel p,int row,String label,Component field){GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(5,8,5,8);p.add(new JLabel(label),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;p.add(field,c);return row+1;}
}
