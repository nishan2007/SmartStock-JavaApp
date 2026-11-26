package gy.deckers.studio;

import publishing.Publication;
import java.awt.geom.Rectangle2D;
import java.util.*;

/** Transforms existing table objects in place, retaining IDs and linked print views. */
final class TableGeometry {
    static List<Publication.Element> members(Publication.Page page,Publication.Element selected) {
        String key=StudioTables.key(selected);
        if(key.isEmpty())return List.of(selected);
        var members=new ArrayList<>(page.objects.stream().filter(e->StudioTables.key(e).equals(key)).toList());
        if(!key.startsWith(StudioTables.PREFIX)) {
            Rectangle2D box=union(members);
            for(var e:page.objects)if(e.type.equals("LINE")&&(e.text.startsWith("Native Publisher table")||e.text.equals("Table bottom border"))
                    &&Math.abs(e.rotation)<.001&&e.x>=box.getX()-.01&&e.y>=box.getY()-.01
                    &&e.x+e.width<=box.getMaxX()+.02&&e.y+e.height<=box.getMaxY()+.02&&!members.contains(e))members.add(e);
        }
        return members;
    }
    private static Rectangle2D union(List<Publication.Element> members) {
        Rectangle2D result=null;
        for(var e:members){var box=new Rectangle2D.Double(e.x,e.y,e.width,e.height);if(result==null)result=box;else result.add(box);}
        return result;
    }
    static Rectangle2D bounds(Publication.Page page,Publication.Element selected) {
        var members=members(page,selected);
        var cells=members.stream().filter(e->!e.type.equals("LINE")).toList();
        return union(cells.isEmpty()?members:cells);
    }
    static boolean locked(Publication.Page page,Publication.Element selected) {
        return members(page,selected).stream().anyMatch(e->e.locked);
    }
    static void move(Publication.Page page,Publication.Element selected,double x,double y) {
        var members=members(page,selected);if(members.stream().anyMatch(e->e.locked))return;
        var old=bounds(page,selected);double dx=x-old.getX(),dy=y-old.getY();
        for(var e:members){e.x+=dx;e.y+=dy;}
    }
    static void resize(Publication.Page page,Publication.Element selected,Rectangle2D target) {
        var members=members(page,selected);if(members.stream().anyMatch(e->e.locked))return;
        var old=bounds(page,selected);double sx=target.getWidth()/old.getWidth(),sy=target.getHeight()/old.getHeight();
        if(!Double.isFinite(sx+sy)||sx<=0||sy<=0)return;
        for(var e:members){e.x=target.getX()+(e.x-old.getX())*sx;e.y=target.getY()+(e.y-old.getY())*sy;e.width*=sx;e.height*=sy;}
    }
}
