package gy.deckers.studio;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.*;
import publishing.Publication;

/** OS clipboard snapshots survive page and template changes. */
final class StudioLayerClipboard {
    static final DataFlavor FLAVOR=new DataFlavor(Payload.class,"SmartStudio layers");
    record Payload(Publication publication,BadgeTemplate badge) {}
    static Transferable transfer(Payload value){return new Transferable(){
        public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{FLAVOR};}
        public boolean isDataFlavorSupported(DataFlavor f){return FLAVOR.equals(f);}
        public Object getTransferData(DataFlavor f)throws UnsupportedFlavorException{if(!isDataFlavorSupported(f))throw new UnsupportedFlavorException(f);return value;}
    };}
    static void put(Payload value){Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transfer(value),null);}
    static Transferable contents(){return Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);}
    static Payload publication(Publication source,int page,int[] rows){
        Publication copy=source.copy();var selected=new ArrayList<Publication.Element>();
        for(int row:rows)selected.add(copy.pages.get(page).objects.get(row));
        var pg=copy.pages.get(page);pg.objects=selected;copy.pages=new ArrayList<>(java.util.List.of(pg));copy.mergeRows.clear();
        copy.assets.keySet().retainAll(selected.stream().map(e->e.asset).toList());copy.assetFiles.keySet().retainAll(selected.stream().map(e->e.asset).toList());
        copy.stories.keySet().retainAll(selected.stream().map(e->e.story).toList());
        copy.masters.clear();copy.styles.clear();
        return new Payload(copy,null);
    }
    static Payload badge(BadgeTemplate source,int[] rows){
        BadgeTemplate copy=source.snapshot();var selected=new ArrayList<BadgeTemplate.Element>();
        for(int row:rows)selected.add(copy.elements.get(row));copy.elements=selected;return new Payload(null,copy);
    }
    static Publication paste(Publication target,int page,Payload payload){
        if(payload.publication()==null)throw new IllegalArgumentException("Paste badge layers into a badge template.");
        Publication next=target.copy(),source=payload.publication().copy();
        Map<String,String> ids=new HashMap<>(),groups=new HashMap<>(),stories=new HashMap<>();
        source.stories.forEach((key,value)->{String id=UUID.randomUUID().toString();stories.put(key,id);next.stories.put(id,value);});
        source.assets.forEach((key,value)->{String id=UUID.randomUUID().toString();ids.put(key,id);next.assets.put(id,value);});
        source.assetFiles.forEach((key,value)->{String id=UUID.randomUUID().toString();ids.put(key,id);next.assetFiles.put(id,value);});
        for(var e:source.pages.get(0).objects){e.id=UUID.randomUUID().toString();e.story=stories.getOrDefault(e.story,e.story);e.asset=ids.getOrDefault(e.asset,e.asset);if(!e.group.isEmpty())e.group=groups.computeIfAbsent(e.group,k->UUID.randomUUID().toString());next.pages.get(page).objects.add(e);}
        next.validate();return next;
    }
    static BadgeTemplate paste(BadgeTemplate target,Payload payload){
        if(payload.badge()==null)throw new IllegalArgumentException("Paste page layers into a page template.");
        BadgeTemplate next=target.snapshot();
        for(int i=0;i<target.elements.size();i++){next.elements.get(i).personalPhoto=target.elements.get(i).personalPhoto;next.elements.get(i).photoFraming=target.elements.get(i).photoFraming;}
        for(var e:payload.badge().snapshot().elements){e.photoId=UUID.randomUUID().toString();next.elements.add(e);}next.validate();return next;
    }
    static void bind(JComponent component,Runnable copy,Runnable paste){
        int mask=GraphicsEnvironment.isHeadless()?InputEvent.CTRL_DOWN_MASK:Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        for(int modifier:new int[]{InputEvent.CTRL_DOWN_MASK,mask}){
            component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_C,modifier),"copyStudioLayers");
            component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_V,modifier),"pasteStudioLayers");
        }
        component.getActionMap().put("copyStudioLayers",new AbstractAction(){public void actionPerformed(ActionEvent e){if(!(component instanceof JTable t)||!t.isEditing())copy.run();}});
        component.getActionMap().put("pasteStudioLayers",new AbstractAction(){public void actionPerformed(ActionEvent e){if(!(component instanceof JTable t)||!t.isEditing())paste.run();}});
    }
}
