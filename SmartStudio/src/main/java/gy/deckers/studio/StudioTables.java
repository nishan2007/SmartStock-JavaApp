package gy.deckers.studio;

import publishing.Publication;
import java.util.*;

/** Editable tables made from portable text cells and vector borders. Geometry is in mm. */
final class StudioTables {
    static final String PREFIX="Studio table ";
    static final int MAX=100, MAX_CELLS=2000;
    static Publication.Element copy(Publication.Element e){return Publication.JSON.fromJson(Publication.JSON.toJson(e),Publication.Element.class);}
    static String key(Publication.Element e){return !e.sourceTable.isBlank()?e.sourceTable:e.group.startsWith(PREFIX)?e.group:"";}
    static boolean isTable(Publication.Element e){return e.type.equals("TABLE")||!key(e).isEmpty();}

    static final class Grid {
        String id=PREFIX+UUID.randomUUID(), border="All", color="#222222";
        double x=5,y=5,stroke=.25;
        final List<Double> widths=new ArrayList<>(),heights=new ArrayList<>();
        final List<Publication.Element> cells=new ArrayList<>();
        final Set<String> replaced=new HashSet<>();
        int rows(){return heights.size();} int columns(){return widths.size();}
        double width(){return widths.stream().mapToDouble(Double::doubleValue).sum();}
        double height(){return heights.stream().mapToDouble(Double::doubleValue).sum();}
        Publication.Element cell(int r,int c){return cells.stream().filter(e->e.sourceRow<=r&&r<e.sourceRow+e.rowSpan&&e.sourceColumn<=c&&c<e.sourceColumn+e.columnSpan).findFirst().orElse(null);}
        void text(int r,int c,String value){var e=cell(r,c);if(e!=null&&!e.text.equals(value)){e.text=value;e.asset="";e.fitText=false;}}
        void resize(double w,double h){if(!Double.isFinite(w+h)||w<=0||h<=0)throw new IllegalArgumentException("Enter positive table dimensions.");double sx=w/width(),sy=h/height();widths.replaceAll(v->v*sx);heights.replaceAll(v->v*sy);}
        void insert(boolean row,int at){
            var sizes=row?heights:widths;if(at<0||at>sizes.size()||sizes.size()>=MAX||(long)(rows()+(row?1:0))*(columns()+(row?0:1))>MAX_CELLS)throw new IllegalArgumentException("Table limit: 100 rows or columns and 2000 cells.");
            sizes.add(at,sizes.get(Math.min(at,sizes.size()-1)));
            for(var e:cells){int start=row?e.sourceRow:e.sourceColumn,span=row?e.rowSpan:e.columnSpan;if(start>=at){if(row)e.sourceRow++;else e.sourceColumn++;}else if(start+span>at){if(row)e.rowSpan++;else e.columnSpan++;}}
            fill();
        }
        void delete(boolean row,int at){
            var sizes=row?heights:widths;if(sizes.size()==1)throw new IllegalArgumentException("Keep at least one row and column.");if(at<0||at>=sizes.size())throw new IllegalArgumentException("Select a cell first.");sizes.remove(at);
            for(var it=cells.iterator();it.hasNext();){var e=it.next();int start=row?e.sourceRow:e.sourceColumn,span=row?e.rowSpan:e.columnSpan;if(start>at){if(row)e.sourceRow--;else e.sourceColumn--;}else if(start+span>at){if(span==1)it.remove();else if(row)e.rowSpan--;else e.columnSpan--;}}
        }
        void merge(int r0,int c0,int r1,int c1){
            var selected=cells.stream().filter(e->e.sourceRow<=r1&&e.sourceRow+e.rowSpan>r0&&e.sourceColumn<=c1&&e.sourceColumn+e.columnSpan>c0).toList();
            for(var e:selected)if(e.sourceRow<r0||e.sourceColumn<c0||e.sourceRow+e.rowSpan>r1+1||e.sourceColumn+e.columnSpan>c1+1)throw new IllegalArgumentException("Select the entire merged cell.");
            var first=cell(r0,c0);String text=selected.stream().map(e->e.text).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.joining(" "));
            cells.removeAll(selected);first.sourceRow=r0;first.sourceColumn=c0;first.rowSpan=r1-r0+1;first.columnSpan=c1-c0+1;first.text=text;first.asset="";first.fitText=false;cells.add(first);
        }
        void split(int r,int c){var e=cell(r,c);e.rowSpan=e.columnSpan=1;fill();}
        void fill(){for(int r=0;r<rows();r++)for(int c=0;c<columns();c++)if(cell(r,c)==null){var e=new Publication.Element();e.text="";e.fontSize=10;e.sourceRow=r;e.sourceColumn=c;cells.add(e);}}
        List<Publication.Element> elements(){
            if(rows()<1||columns()<1||(long)rows()*columns()>MAX_CELLS)throw new IllegalArgumentException("Invalid table size.");
            double[] xs=edges(widths,x),ys=edges(heights,y);var result=new ArrayList<Publication.Element>();
            for(var original:cells){var e=copy(original);e.group=id;e.sourceTable="";e.x=xs[e.sourceColumn];e.y=ys[e.sourceRow];e.width=xs[e.sourceColumn+e.columnSpan]-e.x;e.height=ys[e.sourceRow+e.rowSpan]-e.y;result.add(e);}
            // Unit segments handle merged cells without drawing through their interiors.
            if(!border.equals("None")){
                for(int r=0;r<=rows();r++)for(int c=0;c<columns();c++)if(r==0||r==rows()||border.equals("All")&&cell(r-1,c)!=cell(r,c))result.add(line(xs[c],ys[r],xs[c+1]-xs[c],.001));
                for(int c=0;c<=columns();c++)for(int r=0;r<rows();r++)if(c==0||c==columns()||border.equals("All")&&cell(r,c-1)!=cell(r,c))result.add(line(xs[c],ys[r],.001,ys[r+1]-ys[r]));
            }
            return result;
        }
        private Publication.Element line(double x,double y,double w,double h){var e=new Publication.Element();e.type="LINE";e.text="Table border";e.group=id;e.x=x;e.y=y;e.width=w;e.height=h;e.color=color;e.stroke=color;e.strokeWidth=stroke;return e;}
    }
    static double[] edges(List<Double> sizes,double start){double[] values=new double[sizes.size()+1];values[0]=start;for(int i=0;i<sizes.size();i++){double v=sizes.get(i);if(!Double.isFinite(v)||v<.1)throw new IllegalArgumentException("Rows and columns must be at least 0.1 mm.");values[i+1]=values[i]+v;}return values;}
    static Grid create(int rows,int columns,double width,double height){
        if(rows<1||columns<1||rows>MAX||columns>MAX||(long)rows*columns>MAX_CELLS)throw new IllegalArgumentException("Table limit: 100 rows or columns and 2000 cells.");
        var g=new Grid();for(int c=0;c<columns;c++)g.widths.add(width/columns);for(int r=0;r<rows;r++)g.heights.add(height/rows);g.fill();return g;
    }
    static Grid read(Publication.Page page,Publication.Element selected){
        if(selected.locked||Math.abs(selected.rotation)>.001)throw new IllegalArgumentException("Unlock and unrotate the table before editing it.");
        if(selected.type.equals("TABLE")){
            String[] rows=selected.text.split("\n",-1);var g=create(rows.length,selected.tableColumns,selected.width,selected.height);g.x=selected.x;g.y=selected.y;g.stroke=selected.strokeWidth;g.color=selected.color;g.replaced.add(selected.id);
            for(int r=0;r<rows.length;r++){String[] values=rows[r].split("\t",-1);for(int c=0;c<g.columns();c++){var e=g.cell(r,c);e.font=selected.font;e.fontSize=selected.fontSize;e.bold=selected.bold;e.color=selected.color;e.text=c<values.length?values[c]:"";}}return g;
        }
        String key=key(selected);if(key.isEmpty())throw new IllegalArgumentException("Select a table cell first.");
        var cells=page.objects.stream().filter(e->e.type.equals("TEXT")&&key(e).equals(key)).toList();if(cells.isEmpty())throw new IllegalArgumentException("Select a table cell first.");
        int rows=cells.stream().mapToInt(e->e.sourceRow+e.rowSpan).max().orElse(0),cols=cells.stream().mapToInt(e->e.sourceColumn+e.columnSpan).max().orElse(0);
        var g=create(rows,cols,cols,rows);g.cells.clear();double[] xs=new double[cols+1],ys=new double[rows+1];Arrays.fill(xs,Double.NaN);Arrays.fill(ys,Double.NaN);
        for(var e:cells){if(e.locked||Math.abs(e.rotation)>.001)throw new IllegalArgumentException("Unlock and unrotate the table before editing it.");xs[e.sourceColumn]=e.x;xs[e.sourceColumn+e.columnSpan]=e.x+e.width;ys[e.sourceRow]=e.y;ys[e.sourceRow+e.rowSpan]=e.y+e.height;g.cells.add(copy(e));g.replaced.add(e.id);}
        for(int c=0;c<cols;c++)g.widths.set(c,xs[c+1]-xs[c]);for(int r=0;r<rows;r++)g.heights.set(r,ys[r+1]-ys[r]);g.x=xs[0];g.y=ys[0];edges(g.widths,g.x);edges(g.heights,g.y);
        if(key.startsWith(PREFIX))g.id=key;
        var lines=page.objects.stream().filter(e->e.type.equals("LINE")&&(key(e).equals(key)||!key.startsWith(PREFIX)&&(e.text.startsWith("Native Publisher table")||e.text.equals("Table bottom border"))&&within(e,g))).toList();
        for(var e:lines){if(e.locked)throw new IllegalArgumentException("Unlock the table borders before editing.");g.replaced.add(e.id);g.stroke=e.strokeWidth;g.color=e.color;}
        if(lines.isEmpty()&&key.startsWith(PREFIX))g.border="None";
        else if(key.startsWith(PREFIX)&&lines.stream().noneMatch(e->e.width>e.height?e.y>g.y+.01&&e.y<g.y+g.height()-.01:e.x>g.x+.01&&e.x<g.x+g.width()-.01))g.border="Outside";
        g.fill();return g;
    }
    private static boolean within(Publication.Element e,Grid g){return Math.abs(e.rotation)<.001&&e.x>=g.x-.01&&e.y>=g.y-.01&&e.x+e.width<=g.x+g.width()+.02&&e.y+e.height<=g.y+g.height()+.02;}
    static int apply(Publication p,int pageIndex,Grid grid){
        var page=p.pages.get(pageIndex);var next=grid.elements();int index=page.objects.size();for(int i=0;i<page.objects.size();i++)if(grid.replaced.contains(page.objects.get(i).id)){index=i;break;}
        // Retain linked separation membership; never silently move borders between inks.
        Set<String> removed=new HashSet<>(grid.replaced),oldBorders=new HashSet<>();for(var e:next)removed.remove(e.id);
        for(var e:page.objects)if(grid.replaced.contains(e.id)&&(e.type.equals("LINE")||e.type.equals("TABLE")))oldBorders.add(e.id);
        var borderOwners=page.risoPages.stream().filter(s->s.objectIds.stream().anyMatch(oldBorders::contains)).toList();
        boolean excludedBorders=page.risoExcluded.stream().anyMatch(oldBorders::contains);
        if(borderOwners.size()+(excludedBorders?1:0)>1)throw new IllegalArgumentException("This table has borders in different Riso views. Move its borders into one view before editing.");
        for(var sub:page.risoPages){boolean whole=!grid.replaced.isEmpty()&&sub.objectIds.containsAll(grid.replaced);boolean borders=borderOwners.contains(sub);sub.objectIds.removeAll(removed);for(var e:next)if(whole||borders&&e.type.equals("LINE"))sub.objectIds.add(e.id);}
        boolean wholeExcluded=!grid.replaced.isEmpty()&&page.risoExcluded.containsAll(grid.replaced);page.risoExcluded.removeAll(removed);for(var e:next)if(wholeExcluded||excludedBorders&&e.type.equals("LINE"))page.risoExcluded.add(e.id);
        page.objects.removeIf(e->grid.replaced.contains(e.id));page.objects.addAll(Math.min(index,page.objects.size()),next);p.validate();return index;
    }
}
