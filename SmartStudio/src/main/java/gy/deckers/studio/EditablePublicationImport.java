package gy.deckers.studio;

import publishing.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Imports individual local drawing objects rather than flattening entire pages. */
final class EditablePublicationImport {
    private EditablePublicationImport(){}
    static Publication read(Path source,Path executable)throws Exception{
        if(executable==null)throw new java.io.IOException("Choose the local LibreOffice converter first.");
        Path python=executable.resolveSibling("python.exe");
        if(!Files.isRegularFile(python))throw new java.io.IOException("Editable import requires LibreOffice's Python/UNO runtime beside soffice. The original file has not been changed.");
        if(Files.size(source)>PublicationFiles.MAX_BYTES)throw new java.io.IOException("Template exceeds 48 MiB.");
        Path temp=Files.createTempDirectory("smartstudio-editable-");
        try{
            Path script=temp.resolve("import_layers.py");try(var input=EditablePublicationImport.class.getResourceAsStream("import_layers.py")){if(input==null)throw new java.io.IOException("Editable importer is missing.");Files.copy(input,script);}
            Path log=temp.resolve("import.log");Process process=new ProcessBuilder(python.toString(),script.toString(),source.toAbsolutePath().toString(),temp.toString(),executable.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try{if(!process.waitFor(180,TimeUnit.SECONDS))throw new java.io.IOException("Editable import timed out. Try again after the current conversion finishes.");if(process.exitValue()!=0)throw new java.io.IOException("Editable import failed: "+Files.readString(log).substring(0,(int)Math.min(Files.size(log),1600)));}finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
            List<PublisherDrawingText.Field> fields=source.toString().toLowerCase(Locale.ROOT).endsWith(".pub")?PublisherDrawingText.read(source):List.of();
            Publication result=assemble(source,temp,fields);
            PublicationTableArtworkRepair.repair(result,temp.resolve("source-reference.pdf"));
            if(source.toString().toLowerCase(Locale.ROOT).endsWith(".pub")){
                PublisherNativeTables.repair(result,source);
                PublisherNativeTables.repair(result,source,PublicationTableArtworkRepair.clippedHeadings(result,temp.resolve("source-reference.pdf")));
            }
            PublicationTableArtworkRepair.refreshNativeCrops(result,temp.resolve("source-reference.pdf"));
            if(source.toString().toLowerCase(Locale.ROOT).endsWith(".pub")){PublisherNativeArtwork.repair(result,source);PublisherEmptyBoxRepair.repair(result,source);}
            String evidence=System.getProperty("smartstudio.import.evidence","");if(!evidence.isBlank()){
                Path destination=Path.of(evidence).resolve(UUID.nameUUIDFromBytes(source.toAbsolutePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());Files.createDirectories(destination);Files.copy(temp.resolve("source-reference.pdf"),destination.resolve("source-reference.pdf"),StandardCopyOption.REPLACE_EXISTING);Files.writeString(destination.resolve("source.txt"),source.toString());
                for(int page=0;page<result.pages.size();page++)javax.imageio.ImageIO.write(PublicationTemplateLibrary.preview(result,page),"png",destination.resolve("editable-page-"+(page+1)+".png").toFile());
            }
            return result;
        }finally{try(var files=Files.walk(temp)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
    }
    static Publication assemble(Path source,Path folder,List<PublisherDrawingText.Field> fields)throws Exception{
        JsonArray pages=JsonParser.parseString(Files.readString(folder.resolve("layers.json"))).getAsJsonObject().getAsJsonArray("pages");
        Publication p=new Publication();p.title=source.getFileName().toString().replaceFirst("\\.[^.]+$","");p.template=true;p.pages.clear();
        for(int index=0;index<pages.size();index++){
            JsonObject page=pages.get(index).getAsJsonObject();double width=page.get("width").getAsDouble(),height=page.get("height").getAsDouble();
            if(index==0){p.width=width;p.height=height;}else if(Math.abs(p.width-width)>.02||Math.abs(p.height-height)>.02)throw new java.io.IOException("Pages have different dimensions. Import them as separate templates.");
            Publication.Page target=new Publication.Page();target.name="Page "+(index+1);p.pages.add(target);
            List<PublisherDrawingText.Field> remaining=new ArrayList<>();for(var field:fields)if(field.drawing()==index)remaining.add(field);
            Set<Integer> recovered=new HashSet<>();
            Map<Integer,Publication.Element> nativeElements=new HashMap<>();
            for(var item:page.getAsJsonArray("objects")){
                JsonObject object=item.getAsJsonObject();double x=number(object,"x"),y=number(object,"y"),w=number(object,"width"),h=number(object,"height");
                PublisherDrawingText.Field nativeField=null;double best=.12;
                // Match original shape/group geometry, not a nearby unrelated blank box.
                if(object.has("sourceBounds"))for(var bounds:object.getAsJsonArray("sourceBounds")){
                    var geometry=bounds.getAsJsonObject();
                    for(var field:fields)if(field.drawing()==index){
                        double distance=Math.abs(field.x()+p.width/2-number(geometry,"x"))+Math.abs(field.y()+p.height/2-number(geometry,"y"))+Math.abs(field.width()-number(geometry,"width"))+Math.abs(field.height()-number(geometry,"height"));
                        if(distance<best){best=distance;nativeField=field;}
                    }
                }
                if(nativeField!=null){
                    // Every fill/outline fragment of this WordArt is a glyph placeholder.
                    // Replace the complete group once, at its original stacking position.
                    if(recovered.add(nativeField.shape())){
                        addNative(p,target,nativeField);
                        nativeElements.put(nativeField.shape(),target.objects.get(target.objects.size()-1));
                    }
                    remaining.remove(nativeField);continue;
                }
                Path image=folder.resolve(object.get("image").getAsString()).normalize();if(!image.getParent().equals(folder))throw new java.io.IOException("Unsafe object image path.");
                var decodedImage=PublicationImport.decodeImage(Files.readAllBytes(image));
                var barcode=PublicationBarcode.decode(decodedImage);
                if(barcode.isPresent()){
                    var code=new Publication.Element();code.type=barcode.get().format().equals("QR_CODE")?"QR":"BARCODE";code.text=barcode.get().text();code.barcodeFormat=barcode.get().format();code.barcodeText=false;code.x=x;code.y=y;code.width=w;code.height=h;target.objects.add(code);continue;
                }
                PublicationImport.addImage(p,target,decodedImage,x,y,w,h);
                var element=target.objects.get(target.objects.size()-1);element.text="Artwork "+target.objects.size();
                if(object.has("cells")){
                    target.objects.remove(element);String tableId=UUID.randomUUID().toString();
                    for(var value:object.getAsJsonArray("cells")){
                        var cell=value.getAsJsonObject();Publication.Element field=Publication.JSON.fromJson(Publication.JSON.toJson(element),Publication.Element.class);field.id=UUID.randomUUID().toString();field.type="TEXT";field.fitText=true;field.sourceTable=tableId;field.sourceRow=cell.get("row").getAsInt();field.sourceColumn=cell.get("column").getAsInt();field.rowSpan=cell.get("rowSpan").getAsInt();field.columnSpan=cell.get("columnSpan").getAsInt();
                        field.cropX=number(cell,"left");field.cropY=number(cell,"top");field.cropWidth=number(cell,"width");field.cropHeight=number(cell,"height");field.x=x+w*field.cropX;field.y=y+h*field.cropY;field.width=w*field.cropWidth;field.height=h*field.cropHeight;field.text=cell.get("text").getAsString();field.originalText=field.text;field.font=cell.get("font").getAsString();field.originalFont=field.font;field.fontSize=number(cell,"fontSize");field.originalFontSize=field.fontSize;field.bold=cell.get("bold").getAsBoolean();field.originalBold=field.bold;field.color=String.format("#%06x",cell.get("color").getAsInt()&0xffffff);field.originalColor=field.color;field.fill=String.format("#%06x",cell.get("fill").getAsInt()&0xffffff);target.objects.add(field);
                    }continue;
                }
                if(object.has("kind")){element.type=object.get("kind").getAsString();element.asset="";element.fill=object.get("noFill").getAsBoolean()?"none":String.format("#%06x",object.get("fill").getAsInt()&0xffffff);element.stroke=object.get("noStroke").getAsBoolean()?"none":String.format("#%06x",object.get("stroke").getAsInt()&0xffffff);element.strokeWidth=number(object,"strokeWidth");}
                if(object.has("text")&&!object.get("text").getAsString().isBlank()){
                    element.type="TEXT";element.text=object.get("text").getAsString();element.originalText=element.text;element.originalFont=object.get("font").getAsString();element.font=element.originalFont;element.originalFontSize=number(object,"fontSize");element.fontSize=element.originalFontSize;element.bold=object.get("bold").getAsBoolean();element.originalBold=element.bold;int color=object.get("color").getAsInt();element.color=String.format("#%06x",color<0?0:color&0xffffff);element.originalColor=element.color;element.fitText=true;
                    // Some Publisher merge fields export as an entirely black bitmap.
                    // Render the recovered editable wording instead of covering it with that bitmap.
                    if(solidBlack(decodedImage))element.asset="";
                }
            }
            // Missing cylinders also mask the converter's broken glyph fragments.
            // Keep that mask above those fragments, then restore native lettering above it.
            for(var field:remaining){
                addNative(p,target,field);
                var added=target.objects.get(target.objects.size()-1);
                if(field.shapeType()==22){
                    var lettering=new ArrayList<Publication.Element>();
                    for(var element:target.objects){
                        if(element.type.equals("TEXT")&&nativeElements.containsValue(element))lettering.add(element);
                        else if(element.type.equals("BARCODE")||element.type.equals("QR"))lettering.add(element);
                        else if(element.type.equals("IMAGE")&&foregroundArtwork(p,element,field,fields))lettering.add(element);
                    }
                    target.objects.removeAll(lettering);target.objects.addAll(lettering);
                }
                nativeElements.put(field.shape(),added);
            }
        }
        // Repeated logos and identical shapes on label sheets share one encoded asset.
        Map<String,String> contents=new HashMap<>(),replacements=new HashMap<>();
        for(var asset:new ArrayList<>(p.assets.entrySet())){String existing=contents.putIfAbsent(asset.getValue(),asset.getKey());if(existing!=null){replacements.put(asset.getKey(),existing);p.assets.remove(asset.getKey());}}
        Set<String> used=new HashSet<>();for(var page:p.pages)for(var e:page.objects){e.asset=replacements.getOrDefault(e.asset,e.asset);if(!e.asset.isBlank())used.add(e.asset);}p.assets.keySet().retainAll(used);
        p.validate();return p;
    }
    private static boolean foregroundArtwork(Publication p,Publication.Element e,PublisherDrawingText.Field body,List<PublisherDrawingText.Field> fields)throws Exception{
        if(e.asset.isBlank()||e.width*e.height>p.width*p.height*.7)return false;
        double x=body.x()+p.width/2,y=body.y()+p.height/2;
        if(!new java.awt.geom.Rectangle2D.Double(x,y,body.width(),body.height()).intersects(e.x,e.y,e.width,e.height))return false;
        var image=PublicationImport.decodeImage(Base64.getDecoder().decode(p.assets.get(e.asset)));
        if(rectangularFrame(image))return true;
        // Native lettering has its own editable replacement; leave its broken raster
        // fragments under the mask. Preserve actual seals, pictures and barcode bars.
        for(var f:fields)if(f.drawing()==body.drawing()&&Math.abs(e.x-(f.x()+p.width/2))<3&&Math.abs(e.y-(f.y()+p.height/2))<3&&Math.abs(e.width-f.width())<4&&Math.abs(e.height-f.height())<4)return false;
        int opaque=0,colored=0;
        for(int row=0;row<image.getHeight();row++)for(int col=0;col<image.getWidth();col++){
            int c=image.getRGB(col,row);if((c>>>24)<40)continue;opaque++;
            int r=c>>>16&255,g=c>>>8&255,b=c&255;
            if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>40)colored++;
        }
        if(opaque>0&&colored>opaque*.01)return true;
        int transitions=0;boolean previous=false;
        for(int col=0;col<image.getWidth();col++){
            int c=image.getRGB(col,image.getHeight()/2);
            boolean dark=(c>>>24)>80&&(c&255)<100&&(c>>>8&255)<100&&(c>>>16&255)<100;
            if(col>0&&dark!=previous)transitions++;previous=dark;
        }
        return transitions>=20;
    }
    static boolean rectangularFrame(java.awt.image.BufferedImage image){
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(dark(image.getRGB(x,y))){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
        if(right-left<8||bottom-top<4)return false;
        int dx=Math.max(1,(right-left)/12),dy=Math.max(1,(bottom-top)/12),horizontal=0,vertical=0,inside=0,area=0;
        for(int x=left;x<=right;x++){
            boolean a=false,b=false;for(int y=0;y<=dy;y++){a|=dark(image.getRGB(x,top+y));b|=dark(image.getRGB(x,bottom-y));}
            if(a&&b)horizontal++;
        }
        for(int y=top;y<=bottom;y++){
            boolean a=false,b=false;for(int x=0;x<=dx;x++){a|=dark(image.getRGB(left+x,y));b|=dark(image.getRGB(right-x,y));}
            if(a&&b)vertical++;
            if(y>top+dy&&y<bottom-dy)for(int x=left+dx+1;x<right-dx;x++){area++;if(dark(image.getRGB(x,y)))inside++;}
        }
        return horizontal>(right-left+1)*.85&&vertical>(bottom-top+1)*.85&&area>0&&inside<area*.02;
    }
    private static boolean dark(int pixel){return (pixel>>>24)>80&&(pixel&255)<100&&(pixel>>>8&255)<100&&(pixel>>>16&255)<100;}
    private static void addNative(Publication p,Publication.Page page,PublisherDrawingText.Field field)throws Exception{
        if(field.shapeType()==106)PublisherNativeShapes.cloud(p,page,field);
        else page.objects.add(text(field,p.width,p.height));
    }
    private static double number(JsonObject o,String key){return o.get(key).getAsDouble();}
    private static String sourceColor(int c){return (c&0xff000000)==0?String.format("#%02x%02x%02x",c&255,(c>>>8)&255,(c>>>16)&255):(c&255)==7?"#ffffff":"#000000";}
    static boolean solidBlack(java.awt.image.BufferedImage image){
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)!=0xff000000)return false;
        return true;
    }
    private static Publication.Element text(PublisherDrawingText.Field f,double width,double height){
        var e=new Publication.Element();e.text=f.text();e.font=f.font().isBlank()?"SansSerif":f.font();e.fontSize=Math.max(.1,f.size());e.bold=f.bold();e.italic=f.italic();e.x=f.x()+width/2;e.y=f.y()+height/2;e.width=f.width();e.height=f.height();e.rotation=f.rotation();
        // Publisher stores a quarter-turn's visible rectangle with its dimensions swapped.
        if(Math.abs(Math.abs(e.rotation)%180-90)<.01){double cx=e.x+e.width/2,cy=e.y+e.height/2,tmp=e.width;e.width=e.height;e.height=tmp;e.x=cx-e.width/2;e.y=cy-e.height/2;}
        int c=f.color();e.color=(c&0xff000000)==0?String.format("#%02x%02x%02x",c&255,(c>>>8)&255,(c>>>16)&255):(c&255)==7?"#ffffff":"#000000";
        if(f.shapeType()==22){e.type="CYLINDER";e.text="Cylinder";e.fill=e.color;e.stroke=sourceColor(f.outlineColor());e.strokeWidth=Math.max(.01,f.outlineWidth());}
        if(f.shapeType()==164){e.wordArt="PLAIN";e.wordArtOutline=sourceColor(f.outlineColor());e.wordArtOutlineWidth=Math.min(5,f.outlineWidth());e.wordArtOutsideBounds=true;}
        e.fitText=true;return e;
    }
}
