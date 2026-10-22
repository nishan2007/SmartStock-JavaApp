package gy.deckers.studio;

import publishing.*;
import org.apache.poi.poifs.filesystem.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Restores original transparent PNG bytes when object export flattened alpha to black. */
final class PublisherNativeArtwork {
    static int repair(Publication p,Path source)throws Exception{
        try(var fs=new POIFSFileSystem(source.toFile(),true)){
            if(!fs.getRoot().hasEntry("Escher"))return 0;
            var dir=(DirectoryEntry)fs.getRoot().getEntry("Escher");if(!dir.hasEntry("EscherDelayStm"))return 0;
            try(var in=new DocumentInputStream((DocumentEntry)dir.getEntry("EscherDelayStm"))){
                byte[] data=in.readNBytes(48*1024*1024+1);if(data.length>48*1024*1024)throw new IOException("Publisher image stream exceeds limit.");
                return restore(p,pngs(data));
            }
        }
    }
    static java.util.List<byte[]> pngs(byte[] data){
        var result=new ArrayList<byte[]>();byte[] signature={(byte)137,80,78,71,13,10,26,10};
        for(int pos=0;pos+8<data.length;pos++){
            boolean match=true;for(int j=0;j<8;j++)if(data[pos+j]!=signature[j]){match=false;break;}if(!match)continue;
            int end=pos+8;boolean complete=false;
            for(int chunks=0;chunks<10000&&end+12<=data.length;chunks++){
                int length=ByteBuffer.wrap(data,end,4).getInt();long next=(long)end+12+length;if(length<0||next>data.length||next-pos>48*1024*1024)break;
                boolean last=data[end+4]=='I'&&data[end+5]=='E'&&data[end+6]=='N'&&data[end+7]=='D';end=(int)next;if(last){complete=true;break;}
            }
            if(complete){result.add(Arrays.copyOfRange(data,pos,end));pos=end-1;if(result.size()>4000)break;}
        }return result;
    }
    private record Candidate(byte[] bytes,BufferedImage image,BufferedImage sample){}
    static int restore(Publication p,java.util.List<byte[]> originals)throws Exception{
        var candidates=new ArrayList<Candidate>();
        for(byte[] bytes:originals){var image=PublicationImport.decodeImage(bytes);if(image==null||!image.getColorModel().hasAlpha())continue;int transparent=0,total=0;
            for(int y=0;y<image.getHeight();y+=Math.max(1,image.getHeight()/100))for(int x=0;x<image.getWidth();x+=Math.max(1,image.getWidth()/100)){total++;if((image.getRGB(x,y)>>>24)<16)transparent++;}
            if(transparent>total*.05&&transparent<total*.95)candidates.add(new Candidate(bytes,image,sample(image)));
        }
        int repaired=0;Set<String> visited=new HashSet<>();
        for(var page:p.pages)for(var e:page.objects){if(!e.type.equals("IMAGE")||e.asset.isBlank()||!visited.add(e.asset))continue;
            var current=PublicationImport.decodeImage(p.assetBytes(e.asset));if(current==null||current.getColorModel().hasAlpha())continue;
            var sample=sample(current);Candidate best=null;double score=Double.MAX_VALUE,second=Double.MAX_VALUE;
            for(var candidate:candidates){double ratio=(double)current.getWidth()/current.getHeight()/((double)candidate.image.getWidth()/candidate.image.getHeight());if(Math.abs(ratio-1)>.02)continue;
                double distance=difference(sample,candidate.sample);if(distance<score){second=score;score=distance;best=candidate;}else second=Math.min(second,distance);
            }
            if(best!=null&&score<.05&&(second-score>.015||second==Double.MAX_VALUE)){
                p.assetFiles.remove(e.asset);p.assets.put(e.asset,Base64.getEncoder().encodeToString(best.bytes));repaired++;
            }
        }return repaired;
    }
    private static BufferedImage sample(BufferedImage image){var out=new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);var g=out.createGraphics();try{g.setColor(Color.BLACK);g.fillRect(0,0,64,64);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,64,64,null);}finally{g.dispose();}return out;}
    private static double difference(BufferedImage a,BufferedImage b){long total=0;for(int y=0;y<64;y++)for(int x=0;x<64;x++){int ca=a.getRGB(x,y),cb=b.getRGB(x,y);for(int shift:new int[]{0,8,16})total+=Math.abs(((ca>>shift)&255)-((cb>>shift)&255));}return total/(64.*64*3*255);}
}
