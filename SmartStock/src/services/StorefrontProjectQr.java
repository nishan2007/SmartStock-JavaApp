package services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** QR artwork for an explicitly published, store-specific project URL. */
final class StorefrontProjectQr {
    private StorefrontProjectQr() { }

    static String url(String origin,int storeId,UUID projectId) {
        if(storeId<1)throw new IllegalArgumentException("Choose a store.");
        return origin+"/shop/projects/"+storeId+"/"+projectId;
    }

    static boolean published(JsonObject snapshot,UUID projectId){
        if(!snapshot.has("projects")||!snapshot.get("projects").isJsonArray())return false;
        for(var item:snapshot.getAsJsonArray("projects"))
            if(projectId.toString().equals(StorefrontService.text(item.getAsJsonObject(),"id")))return true;
        return false;
    }

    static byte[] svg(String url)throws Exception {
        var matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,0,0,
            Map.of(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M,EncodeHintType.MARGIN,4,
                EncodeHintType.CHARACTER_SET,StandardCharsets.UTF_8.name()));
        int width=matrix.getWidth(),height=matrix.getHeight();
        StringBuilder path=new StringBuilder();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(matrix.get(x,y))
            path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "+width+" "+height+
            "\" width=\"512\" height=\"512\" role=\"img\" aria-label=\"Scan to view this Deckers project\">"+
            "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#17111d\" d=\""+path+"\"/></svg>").getBytes(StandardCharsets.UTF_8);
    }
}
