package ui.screens.companyprefs;

import com.google.gson.*;
import services.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Private review and import preparation; network and image work stay off Swing's event thread. */
final class ShirtQuoteReview {
    record Prepared(Map<String,BufferedImage> images,List<Path> files,Path directory){}
    record Preview(String label,BufferedImage image){}
    record ImportData(JsonObject quote,Prepared prepared){}
    static boolean isShirt(JsonObject q){return q.has("projectContext")&&q.getAsJsonObject("projectContext").has("shirtDesign");}
    static Prepared prepare(JsonObject quote)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("action","QUOTE_FILES");request.addProperty("requestId",quote.get("requestId").getAsString());
        JsonArray files=LanApiClient.storefrontAdmin(request,UUID.randomUUID().toString()).getAsJsonArray("files");Map<String,BufferedImage> images=new HashMap<>();List<Path> paths=new ArrayList<>();
        Path directory=Files.createTempDirectory("smartstock-shirt-review-");directory.toFile().deleteOnExit();
        for(var entry:files){JsonObject f=entry.getAsJsonObject(),data=LanApiClient.storefrontQuoteFile(UUID.fromString(f.get("fileId").getAsString())).getAsJsonObject("file");byte[] bytes=Base64.getDecoder().decode(data.get("bytesBase64").getAsString());String name=Path.of(data.get("filename").getAsString()).getFileName().toString();Path fileDirectory=Files.createDirectory(directory.resolve(f.get("fileId").getAsString()));fileDirectory.toFile().deleteOnExit();Path file=fileDirectory.resolve(name);Files.write(file,bytes);file.toFile().deleteOnExit();paths.add(file);if(data.get("contentType").getAsString().startsWith("image/")){BufferedImage image=ImageIO.read(new java.io.ByteArrayInputStream(bytes));if(image==null)throw new IllegalArgumentException("Unreadable shirt artwork.");images.put(f.get("fileId").getAsString(),image);}}
        return new Prepared(images,paths,directory);
    }
    static List<Preview> previews(JsonObject quote,Prepared prepared)throws Exception{
        JsonObject context=quote.getAsJsonObject("projectContext"),shirt=context.getAsJsonObject("shirtCatalog"),design=context.getAsJsonObject("shirtDesign"),estimate=context.getAsJsonObject("shirtEstimate");List<Preview> result=new ArrayList<>();Set<String> colors=new HashSet<>();
        for(var value:estimate.getAsJsonArray("lines")){String color=value.getAsJsonObject().get("color").getAsString();if(!colors.add(color))continue;String swatch=shirt.getAsJsonObject("config").getAsJsonObject("colors").get(color).getAsString();for(String side:List.of("FRONT","BACK"))result.add(new Preview(color+" · "+side,ShirtPreviewRenderer.render(shirt,design,side,swatch,prepared.images())));}return result;
    }
    static void show(Component owner,JsonObject quote){new SwingWorker<List<Preview>,Void>(){
        protected List<Preview> doInBackground()throws Exception{return previews(quote,prepare(quote));}
        protected void done(){try{JPanel panel=reviewPanel(quote,get());JOptionPane.showMessageDialog(owner,panel,"Private shirt design",JOptionPane.PLAIN_MESSAGE);}catch(Exception e){JOptionPane.showMessageDialog(owner,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Shirt review",JOptionPane.ERROR_MESSAGE);}}
    }.execute();}
    private static JPanel reviewPanel(JsonObject quote,List<Preview> previews){JPanel panel=new JPanel(new BorderLayout(12,12));JsonObject estimate=quote.getAsJsonObject("projectContext").getAsJsonObject("shirtEstimate");StringBuilder text=new StringBuilder("Estimated price: "+estimate.get("total")+" — staff confirms final quote\n"+(estimate.has("shirtStyle")?estimate.get("shirtStyle").getAsString():"Shirt")+"\n\n");
            for(var value:estimate.getAsJsonArray("lines")){var l=value.getAsJsonObject();text.append(l.get("quantity")).append(" × ").append(l.get("size").getAsString()).append(" / ").append(l.get("color").getAsString()).append(" — shirt ").append(l.get("price")).append(" + print ").append(l.get("printPrice")).append(" each\n");}
            for(var value:estimate.getAsJsonArray("charges")){var c=value.getAsJsonObject();text.append(c.get("area").getAsString()).append(": ").append(c.get("material").getAsString()).append(" / ").append(c.get("name").getAsString()).append(c.has("description")?" / "+c.get("description").getAsString():"").append(" — ").append(c.get("price")).append(" per shirt\n");}
            text.append("\nPhone: ").append(quote.get("contactPhone")).append("\nNeeded by: ").append(quote.get("desiredDate")).append("\nInstructions:\n").append(quote.get("description").getAsString());JTextArea details=new JTextArea(text.toString(),10,65);details.setEditable(false);details.setLineWrap(true);panel.add(new JScrollPane(details),BorderLayout.NORTH);
            JPanel gallery=new JPanel(new GridLayout(0,2,8,8));for(Preview item:previews){JLabel preview=new JLabel(new ImageIcon(item.image().getScaledInstance(340,340,Image.SCALE_SMOOTH)));preview.setText(item.label());preview.setHorizontalTextPosition(SwingConstants.CENTER);preview.setVerticalTextPosition(SwingConstants.BOTTOM);gallery.add(preview);}JScrollPane scroll=new JScrollPane(gallery);scroll.setPreferredSize(new Dimension(760,450));panel.add(scroll);return panel;
    }
    static void importOrder(Component owner,JsonObject quote){new SwingWorker<ImportData,Void>(){
        protected ImportData doInBackground()throws Exception{JsonObject request=new JsonObject();request.addProperty("action","QUOTE_SHIRT_IMPORT");request.addProperty("requestId",quote.get("requestId").getAsString());JsonObject current=LanApiClient.storefrontAdmin(request,UUID.randomUUID().toString()).getAsJsonObject("quote");Prepared p=prepare(current);List<Preview> images=previews(current,p);int i=0;for(Preview img:images){Path path=p.directory().resolve("shirt-preview-"+(i++)+".png");ImageIO.write(img.image(),"png",path.toFile());path.toFile().deleteOnExit();p.files().add(path);}return new ImportData(current,p);}
        protected void done(){try{var imported=get();var window=new ui.screens.customorders.CustomOrders();window.setVisible(true);window.importShirtQuote(imported.quote(),imported.prepared().files());}catch(Exception e){JOptionPane.showMessageDialog(owner,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Shirt order",JOptionPane.ERROR_MESSAGE);}}
    }.execute();}
}
