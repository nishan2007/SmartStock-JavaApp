package publishing;

import com.google.zxing.*;
import com.google.zxing.common.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Local barcode encoding/decoding. Values remain text in the publication archive. */
public final class PublicationBarcode {
  public static final List<String> FORMATS=List.of("CODE_128","CODE_39","EAN_13","EAN_8","UPC_A","ITF","QR_CODE");
  private PublicationBarcode(){}
  public static BitMatrix encode(String value,String format)throws Exception{
    if(value==null||value.isBlank())throw new IllegalArgumentException("Enter barcode data.");
    if(!FORMATS.contains(format))throw new IllegalArgumentException("Choose a supported barcode type.");
    return new MultiFormatWriter().encode(value,BarcodeFormat.valueOf(format),1,1,Map.of(EncodeHintType.MARGIN,format.equals("QR_CODE")?4:10,EncodeHintType.CHARACTER_SET,"UTF-8"));
  }
  public record Decoded(String text,String format){}
  public static Optional<Decoded> decode(BufferedImage image){
    if((long)image.getWidth()*image.getHeight()>50000000)return Optional.empty();
    LuminanceSource source=new LuminanceSource(image.getWidth(),image.getHeight()){
      public byte[] getRow(int y,byte[] row){int width=getWidth();if(row==null||row.length<width)row=new byte[width];for(int x=0;x<width;x++){int rgb=image.getRGB(x,y);int a=(rgb>>>24)&255;int gray=(((rgb>>>16)&255)*306+((rgb>>>8)&255)*601+(rgb&255)*117)>>10;row[x]=(byte)((gray*a+255*(255-a))/255);}return row;}
      public byte[] getMatrix(){byte[] matrix=new byte[getWidth()*getHeight()];for(int y=0;y<getHeight();y++)System.arraycopy(getRow(y,null),0,matrix,y*getWidth(),getWidth());return matrix;}
    };
    try{Result result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),Map.of(DecodeHintType.TRY_HARDER,true,DecodeHintType.POSSIBLE_FORMATS,FORMATS.stream().map(BarcodeFormat::valueOf).toList()));
      float minX=Float.MAX_VALUE,maxX=0,minY=Float.MAX_VALUE,maxY=0;for(var point:result.getResultPoints()){minX=Math.min(minX,point.getX());maxX=Math.max(maxX,point.getX());minY=Math.min(minY,point.getY());maxY=Math.max(maxY,point.getY());}
      // Never replace an entire label/photo merely because it contains a small barcode.
      if(result.getBarcodeFormat()==BarcodeFormat.QR_CODE){if(maxX-minX<image.getWidth()*.4||maxY-minY<image.getHeight()*.4||image.getWidth()<image.getHeight()*.75||image.getWidth()>image.getHeight()*1.33)return Optional.empty();}
      else if(maxX-minX<image.getWidth()*.55||image.getWidth()<image.getHeight()*1.4)return Optional.empty();
      return Optional.of(new Decoded(result.getText(),result.getBarcodeFormat().name()));}catch(Exception ignored){return Optional.empty();}
  }
}
