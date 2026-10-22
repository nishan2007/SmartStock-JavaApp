package gy.deckers.studio;

import com.google.gson.*;
import publishing.PublicationFonts;
import services.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Verified server font snapshots, retained for offline editing. */
final class SharedFontLibrary {
    interface Transport {JsonObject call(JsonObject request)throws Exception;}
    private final Path root;private final Transport transport;
    private java.util.function.BooleanSupplier active=()->true;
    SharedFontLibrary(Path root,Transport transport){this.root=root;this.transport=transport;}
    static SharedFontLibrary current(){return new SharedFontLibrary(Path.of(System.getProperty("user.home"),"shared-font-cache",UUID.nameUUIDFromBytes(LanApiClient.baseUri().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()),LanApiClient::studioFonts);}
    static JsonObject request(String action){var r=new JsonObject();r.addProperty("action",action);return r;}
    synchronized int cached()throws Exception{return activate(JsonParser.parseString(Files.readString(root.resolve("catalog.json"))).getAsJsonObject());}
    private int activate(JsonObject catalog)throws Exception{
        var fonts=new ArrayList<Font>();for(var value:catalog.getAsJsonArray("fonts")){var row=value.getAsJsonObject();byte[] bytes=Files.readAllBytes(root.resolve(hash(row)+".font"));verify(row,bytes);fonts.addAll(Arrays.asList(StudioFontLibrary.validate(bytes)));}
        if(!active.getAsBoolean())throw new java.io.IOException("Font session changed.");
        PublicationFonts.sharedFonts(fonts);return fonts.size();
    }
    private static String hash(JsonObject row){String hash=row.get("sha256").getAsString();if(!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid shared font hash.");return hash;}
    private static void verify(JsonObject row,byte[] bytes)throws Exception{if(bytes.length!=row.get("size").getAsInt()||!StudioFontLibrary.sha256(bytes).equals(hash(row)))throw new java.io.IOException("Shared font verification failed.");}
    synchronized int sync()throws Exception{
        JsonObject catalog=transport.call(request("LIST"));if(catalog.get("version").getAsInt()!=1||catalog.getAsJsonArray("fonts").size()>2000)throw new java.io.IOException("Unsupported font catalogue.");Files.createDirectories(root);
        long total=0;for(var value:catalog.getAsJsonArray("fonts")){
            var row=value.getAsJsonObject();String sha=hash(row);int size=row.get("size").getAsInt();total+=size;if(size<1||size>StudioFontLibrary.MAX_BYTES||total>512L*1024*1024)throw new java.io.IOException("Font library exceeds limits.");
            Path target=root.resolve(sha+".font");if(Files.isRegularFile(target)){try{verify(row,Files.readAllBytes(target));continue;}catch(Exception ignored){}}
            var out=new java.io.ByteArrayOutputStream();while(out.size()<size){var r=request("READ");r.addProperty("sha256",sha);r.addProperty("offset",out.size());byte[] chunk=Base64.getDecoder().decode(transport.call(r).get("bytes").getAsString());if(chunk.length<1||chunk.length>StudioFontLibrary.CHUNK||out.size()+chunk.length>size)throw new java.io.IOException("Invalid shared font download.");out.write(chunk);}
            byte[] bytes=out.toByteArray();verify(row,bytes);StudioFontLibrary.validate(bytes);atomic(target,bytes);
        }
        // Publish only a complete, verified snapshot to renderers and the offline catalogue.
        int count=activate(catalog);atomic(root.resolve("catalog.json"),catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return count;
    }
    private static void atomic(Path target,byte[] bytes)throws Exception{Path temp=Files.createTempFile(target.getParent(),".font-",".tmp");try{Files.write(temp,bytes);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    synchronized void add(Path file)throws Exception{
        if(Files.size(file)>StudioFontLibrary.MAX_BYTES)throw new java.io.IOException("Font exceeds 16 MiB.");byte[] bytes=Files.readAllBytes(file);StudioFontLibrary.validate(bytes);var r=request("BEGIN");r.addProperty("name",file.getFileName().toString());r.addProperty("size",bytes.length);
        try(var siblings=Files.list(file.toAbsolutePath().getParent())){Path notice=siblings.filter(Files::isRegularFile).filter(p->Set.of("ofl.txt","license.txt").contains(p.getFileName().toString().toLowerCase(Locale.ROOT))).sorted().findFirst().orElse(null);if(notice!=null){if(Files.size(notice)>65536)throw new java.io.IOException("Font notice exceeds 64 KiB.");r.addProperty("license",Files.readString(notice));}}
        String id=transport.call(r).get("uploadId").getAsString();
        try{for(int offset=0;offset<bytes.length;offset+=StudioFontLibrary.CHUNK){r=request("CHUNK");r.addProperty("uploadId",id);r.addProperty("offset",offset);r.addProperty("bytes",Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+StudioFontLibrary.CHUNK))));transport.call(r);}r=request("FINISH");r.addProperty("uploadId",id);var saved=transport.call(r);if(!saved.has("sha256")||!StudioFontLibrary.sha256(bytes).equals(saved.get("sha256").getAsString())||!saved.has("size")||saved.get("size").getAsInt()!=bytes.length)throw new java.io.IOException("Server font acknowledgement differs from uploaded bytes.");}catch(Exception ex){try{r=request("ABORT");r.addProperty("uploadId",id);transport.call(r);}catch(Exception ignored){}throw ex;}
    }
    synchronized int importPending(Path pending)throws Exception{
        if(!Files.isDirectory(pending))return 0;
        Path marker=root.resolve("font-import-completed.json");JsonObject done=Files.isRegularFile(marker)?JsonParser.parseString(Files.readString(marker)).getAsJsonObject():new JsonObject();
        java.util.List<Path> files;try(var paths=Files.walk(pending)){files=paths.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().startsWith("._")).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(ttf|otf|ttc)$")).limit(2001).sorted().toList();}if(files.size()>2000)throw new java.io.IOException("Too many pending fonts.");
        int imported=0;for(Path file:files){if(!active.getAsBoolean())throw new java.io.IOException("Font session changed.");if(Files.size(file)>StudioFontLibrary.MAX_BYTES)throw new java.io.IOException("Font exceeds 16 MiB.");String sha=StudioFontLibrary.sha256(Files.readAllBytes(file));if(done.has(sha))continue;add(file);done.addProperty(sha,file.getFileName().toString());Files.createDirectories(root);atomic(marker,done.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));imported++;}return imported;
    }
    private String sharedNames(){try{var catalog=JsonParser.parseString(Files.readString(root.resolve("catalog.json"))).getAsJsonObject();var names=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);for(var row:catalog.getAsJsonArray("fonts"))for(var face:row.getAsJsonObject().getAsJsonArray("faces"))names.add(face.getAsString());return names.isEmpty()?"No shared fonts yet. Add original font files from the computer that has them.":String.join("\n",names);}catch(Exception ex){return "No verified shared font snapshot yet. Choose Sync now.";}}
    private static javax.swing.Timer timer;
    private static volatile long generation;
    static void start(boolean offline){stop();long session=generation;SharedFontLibrary library=current();library.active=()->generation==session;var busy=new java.util.concurrent.atomic.AtomicBoolean();Runnable update=()->{if(!busy.compareAndSet(false,true))return;background(null,()->{try{try{library.cached();}catch(Exception ignored){}if(!offline&&library.active.getAsBoolean()){library.sync();if(library.importPending(Path.of(System.getProperty("user.home"),"font-import-pending"))>0)library.sync();}}finally{busy.set(false);}return null;});};update.run();if(!offline){timer=new javax.swing.Timer(60000,e->update.run());timer.start();}}
    static void stop(){generation++;if(timer!=null){timer.stop();timer=null;}PublicationFonts.sharedFonts(java.util.List.of());}
    static void show(Component owner){SharedFontLibrary library=current();JPanel panel=new JPanel(new BorderLayout(8,8));JLabel status=new JLabel("Fonts are shared through this store server and cached for offline use.");panel.add(status,BorderLayout.NORTH);JTextArea names=new JTextArea(18,55);names.setEditable(false);names.setText(library.sharedNames());panel.add(new JScrollPane(names));JPanel buttons=new JPanel();JButton sync=new JButton("Sync now"),add=new JButton("Add font files…");ui.design.DeckersSwing.styleUtilityButton(sync,ui.design.DeckersPalette.PURPLE);ui.design.DeckersSwing.styleUtilityButton(add,ui.design.DeckersPalette.PURPLE);buttons.add(sync);buttons.add(add);JButton batch=new JButton("Import downloaded batch");ui.design.DeckersSwing.styleUtilityButton(batch,ui.design.DeckersPalette.PURPLE);batch.addActionListener(e->background(owner,()->{int count=library.importPending(Path.of(System.getProperty("user.home"),"font-import-pending"));library.sync();SwingUtilities.invokeLater(()->{names.setText(library.sharedNames());status.setText(count+" downloaded font files imported and verified.");owner.repaint();});return null;}));buttons.add(batch);JButton notices=new JButton("Font notices…");ui.design.DeckersSwing.styleUtilityButton(notices,ui.design.DeckersPalette.PURPLE);notices.addActionListener(e->background(owner,()->{var catalog=JsonParser.parseString(Files.readString(library.root.resolve("catalog.json")));String text=new GsonBuilder().setPrettyPrinting().create().toJson(catalog);SwingUtilities.invokeLater(()->{JTextArea details=new JTextArea(text,22,70);details.setEditable(false);details.setCaretPosition(0);JOptionPane.showMessageDialog(owner,new JScrollPane(details),"Shared font notices",JOptionPane.PLAIN_MESSAGE);});return null;}));buttons.add(notices);panel.add(buttons,BorderLayout.SOUTH);
        Runnable refresh=()->background(owner,()->{int count=library.sync();SwingUtilities.invokeLater(()->{names.setText(library.sharedNames());status.setText(count+" shared font faces verified. Administrators can add fonts.");owner.repaint();});return null;});sync.addActionListener(e->refresh.run());add.addActionListener(e->{JFileChooser chooser=new JFileChooser();chooser.setMultiSelectionEnabled(true);chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Font files (TTF, OTF, TTC)","ttf","otf","ttc"));if(chooser.showOpenDialog(owner)!=JFileChooser.APPROVE_OPTION)return;background(owner,()->{for(var file:chooser.getSelectedFiles())library.add(file.toPath());refresh.run();return null;});});JOptionPane.showMessageDialog(owner,panel,"Shared fonts",JOptionPane.PLAIN_MESSAGE);
    }
    private static void background(Component owner,java.util.concurrent.Callable<Void> action){new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{return action.call();}protected void done(){try{get();for(Window window:Window.getWindows())window.repaint();}catch(Exception ex){if(owner!=null)JOptionPane.showMessageDialog(owner,ex.getCause()==null?ex.getMessage():ex.getCause().getMessage(),"Shared fonts",JOptionPane.ERROR_MESSAGE);}}}.execute();}
}
