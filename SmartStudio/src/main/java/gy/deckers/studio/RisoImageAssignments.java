package gy.deckers.studio;

import publishing.*;
import java.util.*;
import java.awt.image.BufferedImage;

/** Object-scoped job settings; shared image assets can have different ink assignments. */
final class RisoImageAssignments {
    record Target(String key,String label,Publication.Element element){}
    static List<Target> targets(Publication p){
        List<Target> result=new ArrayList<>();
        for(int page=0;page<p.pages.size();page++)if(!p.pages.get(page).reference)add(result,"p:"+page,"Page "+(page+1)+" • "+p.pages.get(page).name,p.pages.get(page));
        for(int master=0;master<p.masters.size();master++){var m=p.masters.get(master);if(p.pages.stream().anyMatch(pg->!pg.reference&&pg.master.equals(m.name)))add(result,"m:"+master,"Master • "+m.name,m);}
        return result;
    }
    private static void add(List<Target> result,String scope,String label,Publication.Page page){
        for(int i=0;i<page.objects.size();i++){var e=page.objects.get(i);if(e.type.equals("IMAGE"))result.add(new Target(scope+":"+i,label+" • Picture "+(i+1)+" • "+String.format(Locale.ROOT,"%.1f × %.1f mm",e.width,e.height),e));}
    }
    static Map<Publication.Element,Integer> resolve(Publication p,List<RisoSeparation.Ink> inks,Map<String,Integer> assignments){
        var byKey=new HashMap<String,Target>();for(var target:targets(p))byKey.put(target.key(),target);
        Map<Publication.Element,Integer> result=new IdentityHashMap<>();
        for(var entry:assignments.entrySet()){
            Target target=byKey.get(entry.getKey());Integer ink=entry.getValue();
            if(target==null)throw new IllegalArgumentException("An assigned picture is no longer in this print job.");
            if(ink==null||ink<0||ink>=inks.size()||!inks.get(ink).enabled())throw new IllegalArgumentException(target.label()+" uses a removed or disabled ink. Enable that ink or change the picture assignment.");
            result.put(target.element(),ink);
        }
        return result;
    }
    static BufferedImage render(Publication p,int sheet,int dpi,List<RisoSeparation.Ink> inks,int plate,Map<String,Integer> assignments){
        var resolved=resolve(p,inks,assignments);
        if(resolved.isEmpty())return RisoSeparation.render(new PublicationRenderer(p).raster(sheet,dpi),inks,plate);
        var raster=new PublicationRenderer(p).raster(sheet,dpi,(element,layer)->RisoSeparation.layer(layer,inks,plate,resolved.getOrDefault(element,-1)));
        if(plate<0)return raster;
        // Preserve the actual grayscale coverage, avoiding an RGB/gray gamma conversion.
        var gray=new BufferedImage(raster.getWidth(),raster.getHeight(),BufferedImage.TYPE_BYTE_GRAY);
        for(int y=0;y<raster.getHeight();y++)for(int x=0;x<raster.getWidth();x++)gray.getRaster().setSample(x,y,0,raster.getRGB(x,y)&255);
        return gray;
    }
}
