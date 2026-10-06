package ui.screens.customorders;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import managers.PermissionManager;
import services.LanApiClient;
import services.ManagerApprovalService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Staff file and proof actions for a saved custom order. */
final class CustomOrderMediaDialog extends JDialog {
    private final long orderId;
    private final JComboBox<Line> lines=new JComboBox<>();
    private final DefaultListModel<FileRow> files=new DefaultListModel<>();
    private final DefaultListModel<ProofRow> proofs=new DefaultListModel<>();
    private final List<FileRow> allFiles=new ArrayList<>();
    private final List<ProofRow> allProofs=new ArrayList<>();
    private final DefaultListModel<LinkRow> links=new DefaultListModel<>();
    private final JList<FileRow> fileList=new JList<>(files);
    private final JList<ProofRow> proofList=new JList<>(proofs);
    private final JList<LinkRow> linkList=new JList<>(links);
    private final JLabel status=new JLabel("Loading order files…");
    private final JProgressBar progress=new JProgressBar();
    private long limitBytes=104857600L;
    private boolean delivered;

    private record Line(long id,String label){public String toString(){return label;}}
    private record FileRow(UUID id,long lineId,String name,long bytes){public String toString(){return name+" · "+Math.max(1,bytes/1024)+" KB";}}
    private record ProofRow(UUID id,long lineId,int revision,String name,String state,String feedback){public String toString(){return "Rev "+revision+" · "+name+" · "+state+(feedback==null||feedback.isBlank()?"":" · "+feedback);}}
    private record LinkRow(UUID id,String created){public String toString(){return "Issued "+created;}}
    private record UploadPlan(Path path,String approvalToken,String reason){}

    static void show(Component parent,long orderId){Window window=SwingUtilities.getWindowAncestor(parent);CustomOrderMediaDialog dialog=new CustomOrderMediaDialog(window,orderId);dialog.setLocationRelativeTo(parent);dialog.setVisible(true);}
    private CustomOrderMediaDialog(Window owner,long orderId){super(owner,"Custom Order Files and Design Approval",ModalityType.APPLICATION_MODAL);this.orderId=orderId;setMinimumSize(new Dimension(760,560));setSize(920,680);setLayout(new BorderLayout(10,10));getRootPane().setBorder(new EmptyBorder(14,14,14,14));
        JPanel head=new JPanel(new BorderLayout(8,8));head.add(new JLabel("Order line:"),BorderLayout.WEST);head.add(lines,BorderLayout.CENTER);add(head,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Attachments",section(fileList,buttons(button("Add Files",this::addFiles),button("View",this::viewFile),button("Remove",this::removeFile))));tabs.addTab("Design Approval",section(proofList,buttons(button("Open Design Workspace",this::openDesigner),button("Submit Preview File",this::submitProof),button("View Preview",this::viewProof),button("Print Approved Design",this::printApproved),button("Revoke Link",this::revokeProof))));tabs.addTab("Customer Link",section(linkList,buttons(button("Create and Copy Link",this::issueLink),button("Revoke Link",this::revokeLink))));add(tabs,BorderLayout.CENTER);
        JPanel foot=new JPanel(new BorderLayout(8,8));foot.add(status,BorderLayout.NORTH);foot.add(progress,BorderLayout.CENTER);JButton close=button("Close",this::dispose);foot.add(close,BorderLayout.EAST);add(foot,BorderLayout.SOUTH);lines.addActionListener(e->filter());refresh();}
    private static JPanel section(JList<?> list,JPanel actions){JPanel p=new JPanel(new BorderLayout(8,8));p.add(new JScrollPane(list),BorderLayout.CENTER);p.add(actions,BorderLayout.SOUTH);return p;}
    private static JPanel buttons(JButton...buttons){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,4));for(var b:buttons)p.add(b);return p;}
    private static JButton button(String name,Runnable action){JButton b=new JButton(name);b.addActionListener(e->action.run());return b;}
    private Line selectedLine(){Line line=(Line)lines.getSelectedItem();if(line==null||line.id()==0)throw new IllegalArgumentException("Select an order line first.");return line;}
    private void message(String text){status.setText(text);}
    private void error(Throwable e){JOptionPane.showMessageDialog(this,e.getMessage()==null?"The action could not be completed.":e.getMessage(),"Custom Order Files",JOptionPane.ERROR_MESSAGE);message("Action failed.");}
    private void runAsync(String title,ThrowingRunnable task,Runnable done){message(title);new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{task.run();return null;}protected void done(){try{get();if(done!=null)done.run();message("Ready.");}catch(Exception e){error(e.getCause()==null?e:e.getCause());}}}.execute();}
    private interface ThrowingRunnable{void run()throws Exception;}
    private static JsonObject request(String action,long orderId){JsonObject q=new JsonObject();q.addProperty("action",action);q.addProperty("orderId",orderId);return q;}
    private void refresh(){runAsync("Loading files…",()->{
        JsonObject media=LanApiClient.customOrderMediaRead(request("LIST",orderId));JsonArray choices=media.getAsJsonArray("lines");
        SwingUtilities.invokeAndWait(()->{
            Long selected=lines.getSelectedItem() instanceof Line line?line.id():null;lines.removeAllItems();lines.addItem(new Line(0,"All order lines"));
            for(var item:choices){JsonObject o=item.getAsJsonObject();long id=o.get("lineId").getAsLong();String label=o.get("item").getAsString();if(o.has("variant")&&!o.get("variant").isJsonNull()&&!o.get("variant").getAsString().isBlank())label+=" / "+o.get("variant").getAsString();lines.addItem(new Line(id,label));}
            if(selected!=null)for(int i=0;i<lines.getItemCount();i++)if(lines.getItemAt(i).id()==selected){lines.setSelectedIndex(i);break;}
            allFiles.clear();for(var item:media.getAsJsonArray("files")){JsonObject o=item.getAsJsonObject();allFiles.add(new FileRow(UUID.fromString(o.get("id").getAsString()),o.get("lineId").getAsLong(),o.get("filename").getAsString(),o.get("bytes").getAsLong()));}
            allProofs.clear();for(var item:media.getAsJsonArray("proofs")){JsonObject o=item.getAsJsonObject();allProofs.add(new ProofRow(UUID.fromString(o.get("id").getAsString()),o.get("lineId").getAsLong(),o.get("revision").getAsInt(),o.get("filename").getAsString(),o.get("status").getAsString(),o.has("feedback")&&!o.get("feedback").isJsonNull()?o.get("feedback").getAsString():""));}
            links.clear();for(var item:media.getAsJsonArray("links")){JsonObject o=item.getAsJsonObject();links.addElement(new LinkRow(UUID.fromString(o.get("id").getAsString()),o.get("createdAt").getAsString()));}
            limitBytes=media.get("limitBytes").getAsLong();delivered="DELIVERED".equals(media.get("orderStatus").getAsString());filter();
        });
    },null);}
    private void filter(){Line line=(Line)lines.getSelectedItem();files.clear();proofs.clear();if(line==null)return;for(FileRow row:allFiles)if(line.id()==0||row.lineId()==line.id())files.addElement(row);for(ProofRow row:allProofs)if(line.id()==0||row.lineId()==line.id())proofs.addElement(row);}
    private boolean matchesSelectedLine(long lineId){Line line=(Line)lines.getSelectedItem();return line!=null&&(line.id()==0||line.id()==lineId);}
    private List<Path> pick(boolean preview){JFileChooser chooser=new JFileChooser();chooser.setMultiSelectionEnabled(!preview);int choice=chooser.showOpenDialog(this);if(choice!=JFileChooser.APPROVE_OPTION)return List.of();List<Path> result=new ArrayList<>();for(var file:preview?new java.io.File[]{chooser.getSelectedFile()}:chooser.getSelectedFiles())result.add(file.toPath());return result;}
    private List<UploadPlan> plans(List<Path> paths){List<UploadPlan> result=new ArrayList<>();for(Path path:paths){try{String token=null,reason=null;if(Files.size(path)>limitBytes){if(PermissionManager.hasPermission("CUSTOM_ORDER_FILE_SIZE_OVERRIDE")){reason=JOptionPane.showInputDialog(this,"Reason for uploading a file above the company limit:");if(reason==null||reason.isBlank())return List.of();}else{var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_FILE_SIZE_OVERRIDE","Custom Order File Size Override","Reason for this oversized file:");if(approval==null)return List.of();token=approval.lanApprovalToken();reason=approval.reason();}}result.add(new UploadPlan(path,token,reason));}catch(Exception e){error(e);return List.of();}}return result;}
    private void addFiles(){if(delivered){error(new IllegalArgumentException("Delivered orders are read only."));return;}Line line;try{line=selectedLine();}catch(Exception e){error(e);return;}List<Path> chosen=pick(false);if(chosen.isEmpty())return;List<UploadPlan> uploads=plans(chosen);if(uploads.isEmpty())return;runAsync("Uploading files…",()->{for(var plan:uploads){uploadOne(orderId,line.id(),"ATTACHMENT",plan.path(),plan.approvalToken(),plan.reason(),n->SwingUtilities.invokeLater(()->{progress.setMaximum(100);progress.setValue(n);}));}},this::refresh);}
    private void submitProof(){if(delivered){error(new IllegalArgumentException("Delivered orders are read only."));return;}Line line;try{line=selectedLine();}catch(Exception e){error(e);return;}List<Path> chosen=pick(true);if(chosen.isEmpty())return;List<UploadPlan> uploads=plans(chosen);if(uploads.isEmpty())return;runAsync("Submitting design preview…",()->{JsonObject result=uploadOne(orderId,line.id(),"PROOF",uploads.get(0).path(),uploads.get(0).approvalToken(),uploads.get(0).reason(),n->SwingUtilities.invokeLater(()->{progress.setMaximum(100);progress.setValue(n);}));String url=result.has("approvalUrl")?result.get("approvalUrl").getAsString():"";SwingUtilities.invokeLater(()->{if(!url.isBlank()){Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url),null);JOptionPane.showMessageDialog(this,"Approval link copied. If the customer has an email on file, a message was queued.\n\n"+url);}});},this::refresh);}
    private void openDesigner(){runAsync("Opening design workspace…",()->{
        var state=LanApiClient.mobileItemWebStatus();if(!state.running()||state.url()==null||state.url().isBlank())throw new IllegalStateException("Start the Mobile Item Web App from the SmartStock menu on the store server first.");
        var activation=LanApiClient.renewMobileItemWebActivation();String url=activation.activationUrl();if(url==null||url.isBlank())throw new IllegalStateException("Could not authorize this browser.");
        String separator=url.contains("?")?"&":"?";java.awt.Desktop.getDesktop().browse(java.net.URI.create(url+separator+"designOrder="+orderId));
    },null);}
    private void issueLink(){runAsync("Creating customer link…",()->{JsonObject result=LanApiClient.customOrderMediaMutation(request("ISSUE_LINK",orderId));String url=result.get("url").getAsString();SwingUtilities.invokeLater(()->{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url),null);JOptionPane.showMessageDialog(this,"Private customer link copied:\n"+url);});},this::refresh);}
    private void revokeLink(){LinkRow link=linkList.getSelectedValue();if(link==null)return;JsonObject q=request("REVOKE_LINK",orderId);q.addProperty("linkId",link.id().toString());runAsync("Revoking link…",()->LanApiClient.customOrderMediaMutation(q),this::refresh);}
    private void revokeProof(){ProofRow proof=proofList.getSelectedValue();if(proof==null||!matchesSelectedLine(proof.lineId()))return;JsonObject q=request("REVOKE_PROOF",orderId);q.addProperty("proofId",proof.id().toString());runAsync("Revoking approval link…",()->LanApiClient.customOrderMediaMutation(q),this::refresh);}
    private void removeFile(){FileRow file=fileList.getSelectedValue();if(file==null||!matchesSelectedLine(file.lineId()))return;if(!PermissionManager.hasPermission("REMOVE_CUSTOM_ORDER_FILES")){error(new IllegalArgumentException("You need the Remove Custom Order Files permission."));return;}JsonObject q=request("REMOVE",orderId);q.addProperty("fileId",file.id().toString());runAsync("Removing attachment…",()->LanApiClient.customOrderMediaMutation(q),this::refresh);}
    private void viewFile(){FileRow file=fileList.getSelectedValue();if(file==null||!matchesSelectedLine(file.lineId()))return;runAsync("Opening attachment…",()->{Path copy=download(orderId,file.id(),false,file.name());java.awt.Desktop.getDesktop().open(copy.toFile());},null);}
    private void viewProof(){ProofRow proof=proofList.getSelectedValue();if(proof==null||!matchesSelectedLine(proof.lineId()))return;runAsync("Opening preview…",()->{Path copy=download(orderId,proof.id(),true,proof.name());java.awt.Desktop.getDesktop().open(copy.toFile());},null);}
    private void printApproved(){Line line;try{line=selectedLine();}catch(Exception e){error(e);return;}runAsync("Printing approved preview…",()->{JsonObject q=request("APPROVED",orderId);q.addProperty("lineId",line.id());JsonObject media=LanApiClient.customOrderMediaRead(q);Path copy=download(orderId,UUID.fromString(media.get("id").getAsString()),true,media.get("filename").getAsString());try{printFile(copy,media.get("contentType").getAsString());}finally{Files.deleteIfExists(copy);}},null);}
    private static void printFile(Path path,String mime)throws Exception{java.awt.print.PrinterJob job=java.awt.print.PrinterJob.getPrinterJob();if("application/pdf".equals(mime)){try(var doc=org.apache.pdfbox.pdmodel.PDDocument.load(path.toFile())){job.setPageable(new org.apache.pdfbox.printing.PDFPageable(doc));if(job.printDialog())job.print();}}else{BufferedImage image=ImageIO.read(path.toFile());if(image==null)throw new IOException("Preview image could not be read.");job.setPrintable((g,format,page)->{if(page>0)return java.awt.print.Printable.NO_SUCH_PAGE;double scale=Math.min(format.getImageableWidth()/image.getWidth(),format.getImageableHeight()/image.getHeight());g.drawImage(image,(int)format.getImageableX(),(int)format.getImageableY(),(int)(image.getWidth()*scale),(int)(image.getHeight()*scale),null);return java.awt.print.Printable.PAGE_EXISTS;});if(job.printDialog())job.print();}}
    private static Path download(long orderId,UUID id,boolean proof,String filename)throws Exception{String extension=filename.contains(".")?filename.substring(filename.lastIndexOf('.')):".bin";Path copy=Files.createTempFile("smartstock-custom-order-",extension);copy.toFile().deleteOnExit();try(OutputStream out=Files.newOutputStream(copy)){long offset=0,total=-1;while(total<0||offset<total){JsonObject q=request("CHUNK",orderId);q.addProperty("fileId",id.toString());q.addProperty("proof",proof);q.addProperty("offset",offset);JsonObject part=LanApiClient.customOrderMediaRead(q);byte[] data=Base64.getDecoder().decode(part.get("bytesBase64").getAsString());total=part.get("totalBytes").getAsLong();out.write(data);offset+=data.length;}}return copy;}
    private static JsonObject reliableMutation(JsonObject request)throws Exception{
        String key=UUID.randomUUID().toString();
        try{return LanApiClient.customOrderMediaMutation(request,key);}
        catch(Exception first){if(first instanceof LanApiClient.LanApiException api&&!api.retryable())throw first;Thread.sleep(400);return LanApiClient.customOrderMediaMutation(request,key);}
    }
    static JsonObject uploadOne(long orderId,long lineId,String kind,Path path,String approvalToken,String reason,Consumer<Integer> progress)throws Exception{
        long size=Files.size(path);MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)if(n>0)digest.update(b,0,n);}
        String hash=HexFormat.of().formatHex(digest.digest());JsonObject begin=request("BEGIN",orderId);
        begin.addProperty("lineId",lineId);begin.addProperty("kind",kind);begin.addProperty("filename",path.getFileName().toString());begin.addProperty("size",size);begin.addProperty("sha256",hash);
        if(approvalToken!=null)begin.addProperty("approvalToken",approvalToken);if(reason!=null)begin.addProperty("overrideReason",reason);
        String id=reliableMutation(begin).get("uploadId").getAsString();
        try(InputStream in=Files.newInputStream(path)){byte[] buffer=new byte[768*1024];int n;long offset=0;
            while((n=in.read(buffer))>=0){if(n==0)continue;JsonObject chunk=request("CHUNK",orderId);chunk.addProperty("uploadId",id);chunk.addProperty("offset",offset);chunk.addProperty("bytesBase64",Base64.getEncoder().encodeToString(n==buffer.length?buffer:java.util.Arrays.copyOf(buffer,n)));reliableMutation(chunk);offset+=n;if(progress!=null)progress.accept((int)Math.min(100,offset*100/size));}
        }
        JsonObject finish=request("FINISH",orderId);finish.addProperty("uploadId",id);return reliableMutation(finish);
    }
}
