package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublisherPageOrderTest {
    @Test void usesDocumentOrderInsteadOfDirectoryOrder()throws Exception{
        var result=PublisherDrawingText.pages(contents(new int[]{2,1},Map.of(1,100,2,200)));
        assertEquals(0,result.get(200));assertEquals(1,result.get(100));
    }
    @Test void reservedDummyPagesCannotShiftLiveNativeText()throws Exception{
        var result=PublisherDrawingText.pages(contents(new int[]{0x10d,1},Map.of(1,100,0x10d,200)));
        assertEquals(Map.of(100,0),result);
    }
    private byte[] contents(int[] order,Map<Integer,Integer> shapes)throws Exception{
        var body=new ByteArrayOutputStream();body.write(new byte[30]);var offsets=new HashMap<Integer,Integer>();
        offsets.put(0,body.size());var refs=new ByteArrayOutputStream();for(int id:order)refs.write(scalar(0,0x70,id));
        body.write(container(concat(containerProperty(2,0xa0,refs.toByteArray()),new byte[]{0x39,0})));
        for(var item:shapes.entrySet()){offsets.put(item.getKey(),body.size());body.write(container(containerProperty(2,0xa0,scalar(0,0x70,item.getValue()))));}
        int trailer=body.size(),last=Collections.max(shapes.keySet());var entries=new ByteArrayOutputStream();
        for(int id=0;id<=last;id++){
            if(!offsets.containsKey(id)){entries.write(new byte[]{0,0x78});continue;}
            entries.write(containerProperty(0,0x88,concat(new byte[]{2,0x18,(byte)(id==0?0x44:0x43),0},scalar(4,0x20,offsets.get(id)))));
        }
        body.write(container(containerProperty(3,0x90,entries.toByteArray())));byte[] bytes=body.toByteArray();ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(26,trailer);return bytes;
    }
    private static byte[] scalar(int id,int type,int value){return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).put((byte)id).put((byte)type).putInt(value).array();}
    private static byte[] containerProperty(int id,int type,byte[] data)throws Exception{return concat(new byte[]{(byte)id,(byte)type},container(data));}
    private static byte[] container(byte[] data)throws Exception{return concat(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length+4).array(),data);}
    private static byte[] concat(byte[]... values)throws Exception{var out=new ByteArrayOutputStream();for(var value:values)out.write(value);return out.toByteArray();}
}
