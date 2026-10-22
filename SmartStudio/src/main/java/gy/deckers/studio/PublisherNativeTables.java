package gy.deckers.studio;

import publishing.*;
import org.apache.poi.poifs.filesystem.*;
import org.apache.poi.hpsf.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static gy.deckers.studio.PublisherDrawingText.block;
import static gy.deckers.studio.PublisherDrawingText.i32;

/** Restores native Publisher table dimensions; only preview-confirmed borders are added. */
final class PublisherNativeTables {
    record Table(int page,int id,double x,double y,double width,double height,double[] columns,double[] rows){}
    private static int shortAt(byte[] b,int p){return Short.toUnsignedInt(ByteBuffer.wrap(b,p,2).order(ByteOrder.LITTLE_ENDIAN).getShort());}
    private static byte[] stream(DirectoryEntry d,String name)throws Exception{
        try(var in=new DocumentInputStream((DocumentEntry)d.getEntry(name))){byte[] b=in.readNBytes(48*1024*1024+1);if(b.length>48*1024*1024)throw new IOException("Publisher stream too large.");return b;}
    }
    static int repair(Publication p,Path source)throws Exception{
        return repair(p,source,Set.of());
    }
    static int repair(Publication p,Path source,Set<String> clippedHeadings)throws Exception{
        if(!source.toString().toLowerCase(Locale.ROOT).endsWith(".pub"))return 0;
        try(var fs=new POIFSFileSystem(source.toFile(),true)){
            byte[] contents=stream(fs.getRoot(),"Contents");
            var anchors=new HashMap<Integer,double[]>();
            byte[] drawing=stream((DirectoryEntry)fs.getRoot().getEntry("Escher"),"EscherStm");anchors(drawing,0,drawing.length,anchors,0);
            BufferedImage preview=null;
            if(fs.getRoot().hasEntry("\005SummaryInformation")){
                var info=PropertySetFactory.create(fs.getRoot(),"\005SummaryInformation");
                if(info instanceof SummaryInformation summary)preview=preview(summary.getThumbnail());
            }
            return restore(p,read(contents,anchors),preview,clippedHeadings);
        }
    }
    static java.util.List<Table> read(byte[] b,Map<Integer,double[]> anchors)throws Exception{
        var result=new ArrayList<Table>();var pages=PublisherDrawingText.pages(b);
        int trailer=i32(b,26),end=trailer+i32(b,trailer),sequence=-1;
        for(int pos=trailer+4;pos<end;){var part=block(b,pos);
            if(part.type()==0x90)for(int q=part.data()+4;q<part.end();){var chunk=block(b,q);sequence++;
                if(chunk.type()==0x88){int type=-1,offset=-1;
                    for(int r=chunk.data()+4;r<chunk.end();){var prop=block(b,r);if(prop.id()==2)type=prop.value();if(prop.id()==4)offset=prop.value();r=prop.end();}
                    if(type==0x10&&offset>=0&&pages.containsKey(sequence)&&anchors.containsKey(sequence)){
                        int nr=0,nc=0,array=-1;double width=0,height=0;
                        for(int r=offset+4,limit=offset+i32(b,offset);r<limit;){var prop=block(b,r);
                            switch(prop.id()){case 0x66->nr=prop.value();case 0x67->nc=prop.value();case 0x68->width=prop.value()/36000.;case 0x69->height=prop.value()/36000.;case 0x6d->array=prop.data();}r=prop.end();}
                        if(nr<1||nc<1||(long)nr*nc>4000||array<0||width<=0||height<=0)throw new IOException("Invalid native Publisher table.");
                        var dimensions=new ArrayList<Double>();
                        for(int r=array+4,limit=array+i32(b,array);r<limit;){var item=block(b,r);
                            if(item.id()==0)for(int s=item.data()+4;s<item.end();){var prop=block(b,s);if(prop.id()==2)dimensions.add(prop.value()/36000.);s=prop.end();}r=item.end();}
                        if(dimensions.size()!=nr+nc||dimensions.stream().anyMatch(v->v<=0))throw new IOException("Invalid native table row or column size.");
                        double[] xy=anchors.get(sequence);
                        result.add(new Table(pages.get(sequence),sequence,xy[0],xy[1],width,height,dimensions.subList(0,nc).stream().mapToDouble(v->v).toArray(),dimensions.subList(nc,nc+nr).stream().mapToDouble(v->v).toArray()));
                    }
                }q=chunk.end();
            }pos=part.end();
        }return result;
    }
    private static void anchors(byte[] b,int start,int limit,Map<Integer,double[]> result,int depth)throws IOException{
        if(depth>40)throw new IOException("Publisher drawing nesting exceeds limit.");
        for(int pos=start;pos+8<=limit;){int type=shortAt(b,pos+2),size=i32(b,pos+4);long end=(long)pos+8+size;
            if(size<0||end>limit)throw new IOException("Invalid Publisher drawing bounds.");
            if(type==0xf004){int id=-1;var anchor=new HashMap<Integer,Integer>();
                for(int q=pos+8;q+8<=end;){int t=shortAt(b,q+2),n=i32(b,q+4);long e=(long)q+8+n;if(n<0||e>end)throw new IOException("Invalid Publisher shape bounds.");
                    if(t==0xf010||t==0xf011)for(int r=q+12;r+6<=e;r+=6){int k=shortAt(b,r),v=i32(b,r+2);if(t==0xf010)anchor.put(k,v);else if(k==0x6801)id=v;}q=(int)e;}
                if(id>=0&&anchor.containsKey(0x2001)&&anchor.containsKey(0x2002))result.put(id,new double[]{anchor.get(0x2001)/36000.,anchor.get(0x2002)/36000.});
            }else if(type==0xf002||type==0xf003)anchors(b,pos+8,(int)end,result,depth+1);
            pos=(int)end+((type==0xf000||type==0xf002)?4:0);
        }
    }
    /** Publisher's saved CF_METAFILEPICT contains a bounded DIB, not a faithful PDF conversion. */
    static BufferedImage preview(byte[] b)throws IOException{
        if(b==null||b.length<38||b.length>48*1024*1024||i32(b,8)!=3)return null;
        for(int pos=38;pos+6<=b.length;){int words=i32(b,pos);long end=(long)pos+words*2L;if(words<3||end>b.length)return null;
            if(shortAt(b,pos+4)==0x940)for(int d=pos+6;d+40<=Math.min(end,pos+80);d++)if(i32(b,d)==40){
                int w=i32(b,d+4),h=i32(b,d+8),bits=shortAt(b,d+14),colors=i32(b,d+32);
                if(w<=0||h==0||h==Integer.MIN_VALUE||(long)w*Math.abs(h)>25000000||i32(b,d+16)!=0||!Set.of(1,4,8,24,32).contains(bits))continue;
                int palette=bits<=8?(colors>0?colors:1<<bits)*4:0;long bytes=((long)w*bits+31)/32*4*Math.abs(h),length=40L+palette+bytes;
                if(colors<0||palette<0||length>end-d||length>48*1024*1024)continue;
                byte[] bmp=new byte[(int)length+14];ByteBuffer header=ByteBuffer.wrap(bmp).order(ByteOrder.LITTLE_ENDIAN);header.put((byte)'B').put((byte)'M').putInt(bmp.length).putInt(0).putInt(14+40+palette);System.arraycopy(b,d,bmp,14,(int)length);
                return javax.imageio.ImageIO.read(new ByteArrayInputStream(bmp));
            }pos=(int)end;
        }return null;
    }
    static int restore(Publication p,java.util.List<Table> tables,BufferedImage preview){
        return restore(p,tables,preview,Set.of());
    }
    private static int restore(Publication p,java.util.List<Table> tables,BufferedImage preview,Set<String> clippedHeadings){
        int repaired=0;
        Set<String> matchedGroups=new HashSet<>();
        for(var table:tables){if(table.page()<0||table.page()>=p.pages.size())continue;
            double x=table.x()+p.width/2,y=table.y()+p.height/2;
            Publication.Page page=null;String matchedGroup=null;double closest=Double.MAX_VALUE;
            for(var candidate:p.pages)for(var e:candidate.objects)if(e.type.equals("TEXT")&&!e.sourceTable.isBlank()&&!matchedGroups.contains(e.sourceTable)&&e.sourceRow==0&&e.sourceColumn==0&&e.cropWidth>0){
                double distance=Math.abs(e.x-x)+Math.abs(e.y-y)+Math.abs(e.width/e.cropWidth-table.width());
                if(distance<closest){closest=distance;page=candidate;matchedGroup=e.sourceTable;}
            }
            if(page==null||closest>1)continue;
            var groups=new LinkedHashMap<String,java.util.List<Publication.Element>>();
            for(var e:page.objects)if(e.type.equals("TEXT")&&!e.sourceTable.isBlank())groups.computeIfAbsent(e.sourceTable,k->new ArrayList<>()).add(e);
            var cells=groups.get(matchedGroup);
            if(cells==null||cells.stream().anyMatch(e->e.sourceRow+e.rowSpan>table.rows().length||e.sourceColumn+e.columnSpan>table.columns().length))continue;
            matchedGroups.add(matchedGroup);
            String marker="Native Publisher table "+table.id();
            double[] xs=positions(table.columns(),x,table.width()),ys=positions(table.rows(),y,table.height());
            var lines=new LinkedHashMap<String,double[]>();boolean geometry=false;
            for(var e:cells){int r=e.sourceRow,c=e.sourceColumn;
                geometry|=Math.abs(e.x-xs[c])>.02||Math.abs(e.y-ys[r])>.02||Math.abs(e.width-(xs[c+e.columnSpan]-xs[c]))>.02||Math.abs(e.height-(ys[r+e.rowSpan]-ys[r]))>.02;
                for(double[] edge:new double[][]{{xs[c],ys[r],xs[c+e.columnSpan],ys[r]},{xs[c],ys[r+e.rowSpan],xs[c+e.columnSpan],ys[r+e.rowSpan]},{xs[c],ys[r],xs[c],ys[r+e.rowSpan]},{xs[c+e.columnSpan],ys[r],xs[c+e.columnSpan],ys[r+e.rowSpan]}})
                    if(p.pages.indexOf(page)==0&&preview!=null&&confirmed(preview,p.width,p.height,edge))lines.put(Arrays.toString(edge),edge);
            }
            // Tiny saved previews can make a single row edge shorter than four pixels.
            // Confirm continuous native rules across adjoining cells before splitting them.
            if(p.pages.indexOf(page)==0&&preview!=null){
                for(double xx:xs){double[] edge={xx,ys[0],xx,ys[ys.length-1]};
                    boolean unmerged=cells.stream().noneMatch(e->e.sourceColumn<Arrays.binarySearch(xs,xx)&&e.sourceColumn+e.columnSpan>Arrays.binarySearch(xs,xx));
                    if(unmerged&&confirmed(preview,p.width,p.height,edge))lines.put(Arrays.toString(edge),edge);
                }
                for(double yy:ys){double[] edge={xs[0],yy,xs[xs.length-1],yy};
                    boolean unmerged=cells.stream().noneMatch(e->e.sourceRow<Arrays.binarySearch(ys,yy)&&e.sourceRow+e.rowSpan>Arrays.binarySearch(ys,yy));
                    if(unmerged&&confirmed(preview,p.width,p.height,edge))lines.put(Arrays.toString(edge),edge);
                }
            }
            // A native grid confirms that replacing the broken converter artwork is justified.
            if(lines.size()<4&&!geometry)continue;
            var existing=page.objects.stream().filter(e->e.text.equals(marker)).toList();
            if(!geometry&&existing.size()==lines.size())continue;
            page.objects.removeAll(existing);
            for(var e:cells){int r=e.sourceRow,c=e.sourceColumn;e.x=xs[c];e.y=ys[r];e.width=xs[c+e.columnSpan]-xs[c];e.height=ys[r+e.rowSpan]-ys[r];
                if(e.text.isBlank()){e.asset="";e.fitText=false;}
            }
            for(var edge:lines.values()){var e=new Publication.Element();e.type="LINE";e.text=marker;e.group=marker;e.x=edge[0];e.y=edge[1];e.width=Math.max(.001,edge[2]-edge[0]);e.height=Math.max(.001,edge[3]-edge[1]);e.color="#000000";e.strokeWidth=.25;page.objects.add(e);}
            if(geometry||!lines.isEmpty())repaired++;
        }
        if(preview!=null&&!p.pages.isEmpty()){
            var page=p.pages.get(0);var labels=new ArrayList<Publication.Element>();
            for(var e:page.objects)if(e.type.equals("TEXT")&&!e.sourceTable.isBlank()&&(e.rowSpan>1||clippedHeadings.contains(e.id))&&!e.text.isBlank()){
                double[] bounds=letteringBounds(preview,p.width,p.height,e);
                if(bounds==null)continue;
                var label=Publication.JSON.fromJson(Publication.JSON.toJson(e),Publication.Element.class);
                label.id=UUID.randomUUID().toString();label.group=(e.rowSpan>1?"Native merged cell ":"Native table heading ")+e.id;
                label.sourceTable="";label.asset="";label.story="";label.originalText="";label.fitText=true;
                label.text=sourceLines(preview,p.width,p.height,bounds,e.text);
                label.x=bounds[0];label.y=bounds[1];label.width=bounds[2];label.height=bounds[3];labels.add(label);
                e.text="";e.asset="";e.fitText=false;repaired++;
            }
            page.objects.addAll(labels);
            for(var label:page.objects)if(label.group.startsWith("Native merged cell ")){
                String wording=sourceLines(preview,p.width,p.height,new double[]{label.x,label.y,label.width,label.height},label.text);
                if(!wording.equals(label.text)){label.text=wording;repaired++;}
            }
        }
        return repaired;
    }
    static String sourceLines(BufferedImage image,double width,double height,double[] bounds,String text){
        int x0=(int)Math.round(bounds[0]/width*image.getWidth()),y0=(int)Math.round(bounds[1]/height*image.getHeight());
        int w=(int)Math.round(bounds[2]/width*image.getWidth()),h=(int)Math.round(bounds[3]/height*image.getHeight());
        int lines=0;boolean previous=false;
        for(int y=y0;y<Math.min(image.getHeight(),y0+h);y++){boolean dark=false;for(int x=x0;x<Math.min(image.getWidth(),x0+w);x++)if(ink(image.getRGB(x,y))){dark=true;break;}if(dark&&!previous)lines++;previous=dark;}
        var words=text.strip().split("\\s+");if(lines<2||lines>4||words.length<lines)return text;
        var result=new ArrayList<String>();int start=0;
        for(int line=0;line<lines;line++){
            int remaining=lines-line;if(remaining==1){result.add(String.join(" ",Arrays.copyOfRange(words,start,words.length)));break;}
            int total=String.join(" ",Arrays.copyOfRange(words,start,words.length)).length(),end=start+1;
            while(end<words.length-(remaining-1)&&Math.abs(String.join(" ",Arrays.copyOfRange(words,start,end+1)).length()-(double)total/remaining)<Math.abs(String.join(" ",Arrays.copyOfRange(words,start,end)).length()-(double)total/remaining))end++;
            result.add(String.join(" ",Arrays.copyOfRange(words,start,end)));start=end;
        }return String.join("\n",result);
    }
    static double[] letteringBounds(BufferedImage image,double width,double height,Publication.Element e){
        double sx=image.getWidth()/width,sy=image.getHeight()/height;
        int inset=e.rowSpan>1?2:1;
        int x0=Math.max(0,(int)Math.ceil(e.x*sx)+inset),x1=Math.min(image.getWidth()-1,(int)Math.floor((e.x+e.width)*sx)-inset);
        int y0=Math.max(0,(int)Math.ceil(e.y*sy)+inset),y1=Math.min(image.getHeight()-1,(int)Math.floor((e.y+e.height)*sy)-inset);
        if(x1-x0<4||y1-y0<2)return null;
        int[] rows=new int[y1-y0+1],cols=new int[x1-x0+1];
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)if(ink(image.getRGB(x,y))){rows[y-y0]++;cols[x-x0]++;}
        int left=x1,right=x0,top=y1,bottom=y0,count=0;
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)if(rows[y-y0]<(x1-x0+1)*.85&&cols[x-x0]<(y1-y0+1)*.85&&ink(image.getRGB(x,y))){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);count++;}
        if(count<4||right-left<3||bottom-top<1)return null;
        // Border-only regions cannot establish a lettering location.
        if(bottom-top>(y1-y0)*.7)return null;
        return new double[]{left/sx,top/sy,(right-left+1)/sx,(bottom-top+1)/sy};
    }
    private static boolean ink(int rgb){return ((rgb>>16)&255)<140&&((rgb>>8)&255)<140&&(rgb&255)<140;}
    private static double[] positions(double[] sizes,double origin,double extent){double[] positions=new double[sizes.length+1];positions[0]=origin;double sum=Arrays.stream(sizes).sum();for(int i=0;i<sizes.length;i++)positions[i+1]=positions[i]+extent*sizes[i]/sum;return positions;}
    static boolean confirmed(BufferedImage image,double width,double height,double[] edge){
        boolean horizontal=Math.abs(edge[3]-edge[1])<.001;double a=(horizontal?edge[0]/width*image.getWidth():edge[1]/height*image.getHeight()),b=(horizontal?edge[2]/width*image.getWidth():edge[3]/height*image.getHeight());
        double cross=horizontal?edge[1]/height*image.getHeight():edge[0]/width*image.getWidth();if(b-a<4)return false;
        for(int shift=-3;shift<=3;shift++){int dark=0,total=0;
            for(int pos=(int)Math.ceil(a+1);pos<Math.floor(b);pos++){int x=horizontal?pos:(int)Math.round(cross)+shift,y=horizontal?(int)Math.round(cross)+shift:pos;if(x<0||x>=image.getWidth()||y<0||y>=image.getHeight())continue;total++;int rgb=image.getRGB(x,y);if(((rgb>>16)&255)<160&&((rgb>>8)&255)<160&&(rgb&255)<160)dark++;}
            if(total>=3&&dark>=total*.85)return true;
        }return false;
    }
}
