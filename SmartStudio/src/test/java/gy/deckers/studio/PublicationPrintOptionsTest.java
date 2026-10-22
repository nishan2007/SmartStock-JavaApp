package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import publishing.*;
import java.util.List;
import javax.print.attribute.standard.Sides;
import static org.junit.jupiter.api.Assertions.*;
class PublicationPrintOptionsTest {
    private Publication document(){
        Publication p=BusinessCardsPanel.builtin();p.pages.clear();
        for(int i=1;i<=4;i++){var page=new Publication.Page();page.name="Design "+i;var e=new Publication.Element();e.text="Page "+i;page.objects.add(e);p.pages.add(page);}
        p.print.quantity=1;return p;
    }
    @Test void selectsOnlySecondPageWithoutChangingTemplate(){
        var original=document();String before=Publication.JSON.toJson(original);
        var selected=PublicationPrintOptions.select(original,List.of(1),false,false);
        assertEquals(1,selected.pages.size());assertEquals("Design 2",selected.pages.get(0).name);
        assertEquals(1,new PublicationRenderer(selected).sheets());assertEquals(Sides.ONE_SIDED,PublicationPrintOptions.sides(selected));
        selected.pages.get(0).objects.get(0).text="Changed";
        assertEquals(before,Publication.JSON.toJson(original));
    }
    @Test void duplexPairsSelectedPagesAndPadsOddLastBack(){
        var source=document();source.print.mixPages=true;
        var selected=PublicationPrintOptions.select(source,List.of(0,2,3),true,true);
        assertFalse(selected.print.mixPages);assertTrue(source.print.mixPages);
        assertEquals(List.of("Design 1","Design 3","Design 4"),selected.pages.stream().map(p->p.name).toList());
        assertEquals(Sides.TWO_SIDED_SHORT_EDGE,PublicationPrintOptions.sides(selected));
        var renderer=new PublicationRenderer(selected);assertEquals(4,renderer.sheets());
        var blank=renderer.raster(3,30);assertEquals(0xffffff,blank.getRGB(blank.getWidth()/2,blank.getHeight()/2)&0xffffff);
        selected=PublicationPrintOptions.select(source,List.of(0,1),true,false);
        assertEquals(Sides.TWO_SIDED_LONG_EDGE,PublicationPrintOptions.sides(selected));
        var position=SheetLayout.pack(selected.width,selected.height,selected.print).placements().get(0);
        var back=SheetLayout.back(position,selected.print);
        assertEquals(selected.print.width-position.x()-position.width(),back.x(),.0001);
    }
    @Test void validatesSelection(){
        var p=document();assertThrows(IllegalArgumentException.class,()->PublicationPrintOptions.select(p,List.of(),false,false));
        assertThrows(IllegalArgumentException.class,()->PublicationPrintOptions.select(p,List.of(0),true,false));
        assertThrows(IllegalArgumentException.class,()->PublicationPrintOptions.select(p,List.of(1,0),false,false));
        assertThrows(IllegalArgumentException.class,()->PublicationPrintOptions.select(p,List.of(4),false,false));
        p.pages.get(1).reference=true;assertThrows(IllegalArgumentException.class,()->PublicationPrintOptions.select(p,List.of(1),false,false));
    }
    @Test void defaultsToSingleSidedAndCurrentPageButtonWorks()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var source=document();source.print.duplex=true;var options=new PublicationPrintOptions(source,1);
            assertFalse(options.duplex.isSelected());assertFalse(options.edge.isEnabled());
            assertEquals(Sides.ONE_SIDED,PublicationPrintOptions.sides(options.selection(source)));
            for(var child:((javax.swing.JPanel)options.getComponent(1)).getComponents())if(child instanceof javax.swing.JButton b&&b.getText().equals("Current page"))b.doClick();
            assertEquals("Design 2",options.selection(source).pages.get(0).name);assertEquals(1,options.selection(source).pages.size());
        });
    }
    @Test void landscapeLongEdgeFlipsBackRowsInsteadOfColumns(){
        var p=document();p.print.width=279.4;p.print.height=215.9;
        var selected=PublicationPrintOptions.select(p,List.of(0,1),true,false);
        assertEquals(Sides.TWO_SIDED_LONG_EDGE,PublicationPrintOptions.sides(selected));
        var pos=SheetLayout.pack(selected.width,selected.height,selected.print).placements().get(0);
        var back=SheetLayout.back(pos,selected.print);
        assertEquals(pos.x(),back.x(),.0001);assertEquals(selected.print.height-pos.y()-pos.height(),back.y(),.0001);
    }
}
