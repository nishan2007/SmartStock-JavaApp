package publishing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Template-only transport: merge records and order associations never enter the library. */
public final class PublicationTemplates {
  private PublicationTemplates() {}
  public static Publication clean(Publication source){
    Publication p=source.copy();p.title=p.title.strip();p.template=true;p.mergeRows.clear();p.orderId=null;p.lineId=null;p.validate();return p;
  }
  public static void requireTemplate(Publication p){
    p.validate();if(!p.template||!p.mergeRows.isEmpty()||p.orderId!=null||p.lineId!=null)
      throw new IllegalArgumentException("Only reusable templates without merge records or order links may be shared.");
  }
  public static String fingerprint(Publication p)throws Exception{
    requireTemplate(p);MessageDigest digest=MessageDigest.getInstance("SHA-256");
    digest.update(Publication.JSON.toJson(p).getBytes(StandardCharsets.UTF_8));
    for(String id:new TreeSet<>(p.assetIds())){
      digest.update(("\n"+id+"\n").getBytes(StandardCharsets.UTF_8));
      MessageDigest asset=MessageDigest.getInstance("SHA-256");
      try(var input=p.openAsset(id)){byte[] buffer=new byte[1024*1024];int n;while((n=input.read(buffer))!=-1)asset.update(buffer,0,n);}
      digest.update(asset.digest());
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
