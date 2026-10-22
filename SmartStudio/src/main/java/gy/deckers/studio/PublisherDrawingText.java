package gy.deckers.studio;

import org.apache.poi.poifs.filesystem.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded reader for OfficeArt geometry-text properties omitted by libmspub. */
final class PublisherDrawingText {
    record Field(int drawing,int shape,String text,String font,double size,boolean bold,boolean italic,int color,double rotation,double x,double y,double width,double height,int shapeType,int outlineColor,double outlineWidth,Integer adjustX,Integer adjustY){
        Field(int drawing,int shape,String text,String font,double size,boolean bold,boolean italic,int color,double rotation,double x,double y,double width,double height,int shapeType,int outlineColor,double outlineWidth){this(drawing,shape,text,font,size,bold,italic,color,rotation,x,y,width,height,shapeType,outlineColor,outlineWidth,null,null);}
        Field(int drawing,int shape,String text,String font,double size,boolean bold,boolean italic,int color,double rotation,double x,double y,double width,double height){this(drawing,shape,text,font,size,bold,italic,color,rotation,x,y,width,height,136,0,0);}
    }
    private PublisherDrawingText(){}
    static List<Field> read(Path file)throws Exception{
        if(Files.size(file)>48L*1024*1024)throw new java.io.IOException("Publisher file exceeds 48 MiB.");
        try(var fs=new POIFSFileSystem(file.toFile(),true)){
            if(!fs.getRoot().hasEntry("Escher"))return List.of();
            var dir=(DirectoryEntry)fs.getRoot().getEntry("Escher");
            Map<Integer,Integer> pages;List<Integer> palette;
            try(var input=new DocumentInputStream((DocumentEntry)fs.getRoot().getEntry("Contents"))){byte[] contents=input.readNBytes(48*1024*1024);pages=pages(contents);palette=palette(contents);}
            try(var input=new DocumentInputStream((DocumentEntry)dir.getEntry("EscherStm"))){byte[] bytes=input.readNBytes(48*1024*1024+1);if(bytes.length>48*1024*1024)throw new java.io.IOException("Drawing stream too large.");return parse(bytes).stream().filter(f->pages.containsKey(f.shape())).map(f->new Field(pages.get(f.shape()),f.shape(),f.text(),f.font(),f.size(),f.bold(),f.italic(),resolveColor(f.color(),palette),f.rotation(),f.x(),f.y(),f.width(),f.height(),f.shapeType(),resolveColor(f.outlineColor(),palette),f.outlineWidth(),f.adjustX(),f.adjustY())).toList();}
        }
    }
    static List<Field> parse(byte[] bytes)throws java.io.IOException{
        List<Field> fields=new ArrayList<>();int pos=0,drawing=-1;
        while(pos+8<=bytes.length){int type=u16(bytes,pos+2),size=i32(bytes,pos+4);int end=end(bytes,pos,size);if(type==0xf002){drawing++;walk(bytes,pos+8,end,drawing,fields,0);}pos=end+((type==0xf000||type==0xf002)?4:0);}
        return List.copyOf(fields);
    }
    private static int end(byte[] bytes,int pos,int size)throws java.io.IOException{long end=(long)pos+8+size;if(size<0||end>bytes.length)throw new java.io.IOException("Invalid Publisher drawing record.");return (int)end;}
    private static void walk(byte[] b,int start,int limit,int drawing,List<Field> fields,int depth)throws java.io.IOException{
        if(depth>40)throw new java.io.IOException("Publisher groups are too deeply nested.");
        for(int pos=start;pos+8<=limit;){int type=u16(b,pos+2),end=end(b,pos,i32(b,pos+4));if(end>limit)throw new java.io.IOException("Drawing record exceeds container.");
            if(type==0xf004)shape(b,pos+8,end,drawing,fields);
            else if(type==0xf003)walk(b,pos+8,end,drawing,fields,depth+1);
            pos=end;
        }
    }
    private static void shape(byte[] b,int start,int limit,int drawing,List<Field> fields)throws java.io.IOException{
        Map<Integer,Integer> values=new HashMap<>();Map<Integer,byte[]> complex=new HashMap<>();Map<Integer,Integer> anchor=new HashMap<>();int id=0,shapeType=0;
        for(int pos=start;pos+8<=limit;){int type=u16(b,pos+2),end=end(b,pos,i32(b,pos+4));if(end>limit)throw new java.io.IOException("Shape exceeds container.");int data=pos+8;
            if(type==0xf00a)shapeType=u16(b,pos)>>>4;
            if(type==0xf00b||type==0xf122){int count=u16(b,pos)>>>4,tail=data+count*6;if(tail>end)throw new java.io.IOException("Invalid drawing property count.");
                for(int i=0;i<count;i++){int p=data+i*6,key=u16(b,p),value=i32(b,p+2);if((key&0x8000)!=0){if(value<0||(long)tail+value>end)throw new java.io.IOException("Invalid drawing property size.");complex.put(key&0x3fff,Arrays.copyOfRange(b,tail,tail+value));tail+=value;}else {int property=key&0x3fff;values.put(property,(property&63)==63?mergeBoolean(values.getOrDefault(property,0),value):value);}}
            }else if(type==0xf010||type==0xf011){for(int p=data+4;p+6<=end;p+=6){int key=u16(b,p),value=i32(b,p+2);if(type==0xf010)anchor.put(key,value);else if(key==0x6801)id=value;}}
            pos=end;
        }
        byte[] text=complex.get(0xc0);if(text==null&&shapeType!=22&&shapeType!=106)return;String wording=text==null?"":string(text);if(wording.isBlank()&&shapeType!=22&&shapeType!=106)return;
        if(!anchor.keySet().containsAll(Set.of(0x2001,0x2002,0x2003,0x2004)))return;
        double x=anchor.get(0x2001)/36000.,y=anchor.get(0x2002)/36000.,w=(anchor.get(0x2003)-anchor.get(0x2001))/36000.,h=(anchor.get(0x2004)-anchor.get(0x2002))/36000.;
        if(w<=0||h<=0)return;int flags=values.getOrDefault(0xff,0);int color=values.getOrDefault(0x181,0);
        fields.add(new Field(drawing,id,wording,string(complex.getOrDefault(0xc5,new byte[0])),values.getOrDefault(0xc3,12<<16)/65536.,(flags&0x20)!=0,(flags&0x10)!=0,color,values.getOrDefault(4,0)/65536.,x,y,w,h,shapeType,values.getOrDefault(0x1c0,0),lineEnabled(values)?values.getOrDefault(0x1cb,0)/36000.:0,values.get(0x147),values.get(0x148)));
    }
    static int mergeBoolean(int original,int update){int mask=update>>>16;return ((original|update)&0xffff0000)|((original&0xffff)&~mask)|((update&0xffff)&mask);}
    private static boolean lineEnabled(Map<Integer,Integer> values){
        int flags=values.getOrDefault(0x1ff,0),geometry=values.getOrDefault(0x17f,0);
        return values.containsKey(0x1ff)&&((flags&0x80000)==0||(flags&8)!=0)&&((geometry&0x1000)==0||(geometry&0x10000000)!=0);
    }
    private static String string(byte[] bytes){return new String(bytes,StandardCharsets.UTF_16LE).replace("\u0000","").replace("\r\n","\n").replace("\r\r","\n").replace('\r','\n').strip();}
    record Block(int id,int type,int data,int end,int value){}
    static Block block(byte[] b,int p)throws java.io.IOException{
        if(p<0||p+2>b.length)throw new java.io.IOException("Invalid Publisher content block.");int id=b[p]&255,type=b[p+1]&255,data=p+2;
        int size=switch(type){case 0,0x78,5,8,10->0;case 0x10,0x12,0x18,0x1a,7->2;case 0x20,0x22,0x58,0x68,0x70,0xb8->4;case 0x28->8;case 0x38->16;case 0x48->24;case 0xc0,0x80,0x82,0x88,0x8a,0x90,0x98,0xa0->-1;default->throw new java.io.IOException("Unsupported Publisher content block: "+type);};
        if(size<0){if(data+4>b.length)throw new java.io.IOException("Truncated content block.");size=i32(b,data);if(size<4)throw new java.io.IOException("Invalid content size.");}
        if((long)data+size>b.length)throw new java.io.IOException("Content block exceeds stream.");return new Block(id,type,data,data+size,size==4?i32(b,data):size==2?u16(b,data):0);
    }
    static Map<Integer,Integer> pages(byte[] b)throws java.io.IOException{
        Map<Integer,Integer> result=new HashMap<>();if(b.length<30)return result;
        int trailer=i32(b,26);if(trailer<0||trailer+4>b.length)throw new java.io.IOException("Invalid Publisher directory.");
        int end=trailer+i32(b,trailer),sequence=-1;
        Map<Integer,List<Integer>> shapesByPage=new LinkedHashMap<>();Set<Integer> masters=new HashSet<>();List<Integer> pageOrder=new ArrayList<>();
        for(int pos=trailer+4;pos<end;){Block part=block(b,pos);if(part.type==0x90){
            for(int q=part.data+4;q<part.end;){Block chunk=block(b,q);sequence++;
                if(chunk.type==0x88){int type=-1,offset=-1;for(int r=chunk.data+4;r<chunk.end;){Block prop=block(b,r);if(prop.id==2)type=prop.value;if(prop.id==4)offset=prop.value;r=prop.end;}
                    if((type==0x43||type==0x44)&&offset>=0){int limit=offset+i32(b,offset);List<Integer> shapes=new ArrayList<>();boolean master=false;
                        for(int r=offset+4;r<limit;){Block prop=block(b,r);
                            if(type==0x43&&prop.id==14&&prop.type==0xc0)for(int v=prop.data+4;v<prop.end;v++)if(b[v]!=0)master=true;
                            if(prop.id==2&&(prop.type==0xa0||prop.type==0x90))for(int t=prop.data+4;t<prop.end;){Block item=block(b,t);if(item.type==0x70){if(type==0x44)pageOrder.add(item.value);else shapes.add(item.value);}t=item.end;}
                            r=prop.end;
                        }
                        if(type==0x43){shapesByPage.put(sequence,shapes);if(master)masters.add(sequence);}
                    }
                }q=chunk.end;
            }
        }pos=part.end;}
        if(pageOrder.isEmpty())pageOrder.addAll(shapesByPage.keySet());
        int page=0;Set<Integer> seen=new HashSet<>();
        // Publisher reserves these four sequence IDs for dummy pages, as identified
        // by libmspub's getPageTypeBySeqNum; they can still contain old shape records.
        for(int id:pageOrder){if(!seen.add(id)||masters.contains(id)||Set.of(0x10d,0x110,0x113,0x117).contains(id))continue;var shapes=shapesByPage.get(id);if(shapes==null||shapes.isEmpty())continue;
            for(int shape:shapes)result.put(shape,page);page++;
        }
        return result;
    }
    static int resolveColor(int color,List<Integer> palette){return (color>>>24)==8?(color&0xffffff)<palette.size()?palette.get(color&0xffffff):0:color&0xffffff;}
    private static List<Integer> palette(byte[] b)throws java.io.IOException{
        List<Integer> result=new ArrayList<>();if(b.length<30)return result;
        int trailer=i32(b,26),limit=trailer+i32(b,trailer);
        for(int p=trailer+4;p<limit;){Block part=block(b,p);
            if(part.type==0x90)for(int q=part.data+4;q<part.end;){Block chunk=block(b,q);
                if(chunk.type==0x88){int type=-1,offset=-1;for(int r=chunk.data+4;r<chunk.end;){Block prop=block(b,r);if(prop.id==2)type=prop.value;if(prop.id==4)offset=prop.value;r=prop.end;}
                    if(type==0x5c&&offset>=0)for(int r=offset+4,end=offset+i32(b,offset);r<end;){Block prop=block(b,r);
                        if(prop.type==0xa0)for(int t=prop.data+4;t<prop.end;){Block entry=block(b,t);
                            if(entry.type==0x78)result.add(0);
                            else if(entry.type==0x88){int color=0;for(int v=entry.data+4;v<entry.end;){Block component=block(b,v);if(component.id==1)color=component.value;v=component.end;}result.add(color);}
                            t=entry.end;
                        }r=prop.end;
                    }
                }q=chunk.end;
            }p=part.end;
        }
        if(result.size()==7)result.add(0,0);return result;
    }
    private static int u16(byte[] b,int p){return Short.toUnsignedInt(ByteBuffer.wrap(b,p,2).order(ByteOrder.LITTLE_ENDIAN).getShort());}
    static int i32(byte[] b,int p){return ByteBuffer.wrap(b,p,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
}
