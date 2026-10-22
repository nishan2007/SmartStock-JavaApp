package gy.deckers.studio;

import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;

/** Saved object memberships; no duplicate artwork, positions, styles or merge values. */
final class LinkedRiso {
    static final String MAIN="",UNASSIGNED="@unassigned",EXCLUDED="@excluded";
    static List<Publication.Element> objects(Publication p,int page){
        var main=p.pages.get(page);List<Publication.Element> result=new ArrayList<>();
        for(var master:p.masters)if(master.name.equals(main.master))result.addAll(master.objects);
        result.addAll(main.objects);return result;
    }
    static Publication.RisoPage subPage(Publication p,int page,String id){return p.pages.get(page).risoPages.stream().filter(sub->sub.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Riso sub-page no longer exists."));}
    static String assignment(Publication p,int page,String object){var main=p.pages.get(page);if(main.risoExcluded.contains(object))return EXCLUDED;for(var sub:main.risoPages)if(sub.objectIds.contains(object))return sub.id;return UNASSIGNED;}
    static List<Publication.Element> visible(Publication p,int page,String view){return objects(p,page).stream().filter(e->view.equals(MAIN)||assignment(p,page,e.id).equals(view)).toList();}
    static Publication.RisoPage add(Publication p,int page){if(p.pages.get(page).risoPages.size()>=32)throw new IllegalArgumentException("A main page supports up to 32 Riso sub-pages.");var sub=new Publication.RisoPage();sub.name="Riso "+(p.pages.get(page).risoPages.size()+1);p.pages.get(page).risoPages.add(sub);p.version=2;return sub;}
    static void assign(Publication p,int page,Collection<String> ids,String target){
        if(!target.equals(UNASSIGNED)&&!target.equals(EXCLUDED))subPage(p,page,target);
        Set<String> live=new HashSet<>();objects(p,page).forEach(e->live.add(e.id));if(!live.containsAll(ids))throw new IllegalArgumentException("An object is no longer on this main page.");
        var main=p.pages.get(page);for(var sub:main.risoPages)sub.objectIds.removeAll(ids);main.risoExcluded.removeAll(ids);
        if(target.equals(EXCLUDED))main.risoExcluded.addAll(ids);else if(!target.equals(UNASSIGNED))subPage(p,page,target).objectIds.addAll(ids);
        p.version=2;
    }
    static void remove(Publication p,int page,String id){subPage(p,page,id);p.pages.get(page).risoPages.removeIf(sub->sub.id.equals(id));}
    static void prune(Publication p){for(int page=0;page<p.pages.size();page++){Set<String> live=new HashSet<>();objects(p,page).forEach(e->live.add(e.id));var main=p.pages.get(page);main.risoExcluded.retainAll(live);for(var sub:main.risoPages)sub.objectIds.retainAll(live);}}
    static void deleteSource(Publication p,Publication.Element e){for(var page:p.allPages())page.objects.removeIf(item->item.id.equals(e.id));prune(p);}
    static BiFunction<Publication.Element,BufferedImage,BufferedImage> filter(Publication p,int page,String view,boolean colour){
        Set<String> visible=new HashSet<>();visible(p,page,view).forEach(e->visible.add(e.id));
        boolean single=!view.equals(MAIN)&&!view.equals(UNASSIGNED)&&!view.equals(EXCLUDED);
        var inks=List.of(new RisoSeparation.Ink("Sub-page",single?Color.decode(subPage(p,page,view).color):Color.BLACK,true));
        return (e,image)->!visible.contains(e.id)?new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB):single?RisoSeparation.layer(image,inks,colour?-1:0,0):image;
    }
    static BufferedImage preview(Publication p,int page,String view,int bound){
        double scale=bound/Math.max(p.width,p.height);var image=new BufferedImage(Math.max(1,(int)Math.ceil(p.width*scale)),Math.max(1,(int)Math.ceil(p.height*scale)),BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{g.scale(scale,scale);paint(g,p,page,view,true);}finally{g.dispose();}return image;
    }
    static void paint(Graphics2D g,Publication p,int page,String view,boolean colour){var renderer=new PublicationRenderer(p);var row=p.mergeRows.isEmpty()?Map.<String,String>of():p.mergeRows.get(0);if(view.equals(MAIN))renderer.page(g,page,row);else renderer.page(g,page,row,filter(p,page,view,colour));}
}
