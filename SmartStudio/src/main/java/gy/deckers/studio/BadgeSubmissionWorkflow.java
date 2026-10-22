package gy.deckers.studio;

import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

/** Network work stays off Swing's event thread. Claims belong to one loaded template/session. */
final class BadgeSubmissionWorkflow {
    private final Component owner;
    private final Supplier<SharedBadgeLibrary> library;
    private final Supplier<Path> file;
    private final Supplier<String> revision;
    private final Consumer<JsonObject> load;
    private final Consumer<String> status;
    private JsonObject claim;
    private Path claimFile;
    private long epoch;
    BadgeSubmissionWorkflow(Component owner,Supplier<SharedBadgeLibrary> library,Supplier<Path> file,Supplier<String> revision,Consumer<JsonObject> load,Consumer<String> status){
        this.owner=owner;this.library=library;this.file=file;this.revision=revision;this.load=load;this.status=status;
    }
    interface Work {JsonObject run()throws Exception;}
    private void work(Work task,Consumer<JsonObject> done){
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return task.run();}
            protected void done(){try{done.accept(get());}catch(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();JOptionPane.showMessageDialog(owner,cause.getMessage(),"Template submissions",JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }
    private JsonObject request(SharedBadgeLibrary active,Path target,String action,JsonObject body)throws Exception{
        if(active==null)throw new IllegalStateException("Connect to the shared server library first.");
        return active.record(target,action,body);
    }
    private boolean confirm(String text){return JOptionPane.showConfirmDialog(owner,text,"Organization submissions",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;}
    void link(){
        if(!confirm("Create or rotate this template's private organization link?\nAny previous link and website sessions will stop working.\nNew organizations set their first code. Rotation keeps an existing code.\nSubmissions are retained."))return;
        var active=library.get();Path target=file.get();
        work(()->request(active,target,"QUEUE_LINK",new JsonObject()),result->{
            JTextField url=new JTextField(result.get("url").getAsString(),45);url.setEditable(false);url.selectAll();
            JPanel panel=new JPanel(new BorderLayout(8,8));panel.add(new JLabel("Copy this private link. A new organization chooses its code; rotation keeps its existing code."),BorderLayout.NORTH);panel.add(url);
            JOptionPane.showMessageDialog(owner,panel,"Organization link",JOptionPane.PLAIN_MESSAGE);
        });
    }
    void copyLink(){
        var active=library.get();Path target=file.get();
        work(()->request(active,target,"QUEUE_COPY_LINK",new JsonObject()),result->{
            JTextField url=new JTextField(result.get("url").getAsString(),45);url.setEditable(false);url.selectAll();
            JOptionPane.showMessageDialog(owner,url,"Copy organization link",JOptionPane.PLAIN_MESSAGE);
        });
    }
    void resetCode(){
        if(!confirm("Reset this organization's access code?\nThe link stays the same. Current website sessions will be invalidated.\nThe next visitor chooses a new code. Submissions and records are retained."))return;
        var active=library.get();Path target=file.get();
        work(()->request(active,target,"QUEUE_RESET",new JsonObject()),r->status.accept("Access code reset. Next visitor sets a new code."));
    }
    void revoke(){
        if(!confirm("Revoke this organization's submission link and sessions?\nExisting submissions and print records are retained."))return;
        var active=library.get();Path target=file.get();
        work(()->request(active,target,"QUEUE_REVOKE",new JsonObject()),r->status.accept("Organization link revoked."));
    }
    void clear(){
        epoch++;JsonObject previous=claim;Path target=claimFile;claim=null;claimFile=null;
        var active=library.get();if(previous==null||active==null)return;
        // Best effort release of an abandoned review. PRINTING entries cannot be released.
        new SwingWorker<Void,Void>(){protected Void doInBackground(){try{request(active,target,"QUEUE_RELEASE",previous);}catch(Exception ignored){}return null;}}.execute();
    }
    void release(){
        if(claim==null){status.accept("No submission is currently being reviewed.");return;}
        var active=library.get();var current=claim;var body=claim.deepCopy();Path target=claimFile;
        work(()->request(active,target,"QUEUE_RELEASE",body),r->{if(claim!=current)return;claim=null;claimFile=null;epoch++;status.accept("Review released back to the queue.");});
    }
    void reject(){
        if(claim==null){status.accept("Open and claim a submission first.");return;}
        String reason=JOptionPane.showInputDialog(owner,"Reason for rejecting this submission:");if(reason==null)return;
        var active=library.get();var current=claim;var body=claim.deepCopy();Path target=claimFile;body.addProperty("reason",reason);
        work(()->request(active,target,"QUEUE_REJECT",body),r->{if(claim!=current)return;claim=null;claimFile=null;epoch++;status.accept("Submission rejected; audit details retained.");});
    }
    void open(int offset){
        var active=library.get();Path target=file.get();long current=epoch;JsonObject body=new JsonObject();body.addProperty("offset",offset);
        work(()->request(active,target,"QUEUE_LIST",body),result->{
            if(current!=epoch||!Objects.equals(target,file.get()))return;
            var rows=result.getAsJsonArray("submissions");
            if(rows.isEmpty()){status.accept("No more submissions for this template.");return;}
            String[] labels=new String[rows.size()];for(int i=0;i<rows.size();i++){var row=rows.get(i).getAsJsonObject();labels[i]=row.get("state").getAsString()+" · "+row.get("receivedAt").getAsString()+" · "+row.get("values");}
            JList<String> list=new JList<>(labels);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setSelectedIndex(0);JScrollPane scroll=new JScrollPane(list);scroll.setPreferredSize(new Dimension(750,350));
            int answer=JOptionPane.showOptionDialog(owner,scroll,"Template submissions · current format",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Review / claim","Admin: retry selected","Next 100","Cancel"},"Review / claim");
            if(answer==2){open(offset+100);return;}if(answer<0||answer==3)return;
            var row=rows.get(list.getSelectedIndex()).getAsJsonObject();JsonObject entry=new JsonObject();entry.add("submissionId",row.get("id"));
            if(answer==1){if(confirm("This job may already have printed. Return it to pending for another print?\nThis requires an administrator and can produce a duplicate card."))work(()->request(active,target,"QUEUE_RETRY",entry),r->open(offset));return;}
            if(claim!=null){status.accept("Release or finish your current review before opening another submission.");return;}
            work(()->request(active,target,"QUEUE_CLAIM",entry),claimed->{
                if(current!=epoch||!Objects.equals(target,file.get())){entry.add("claimToken",claimed.get("claimToken"));work(()->request(active,target,"QUEUE_RELEASE",entry),r->{});return;}
                JsonObject form=claimed.getAsJsonObject("form");
                if(!form.get("templateRevision").getAsString().equals(revision.get())){
                    entry.add("claimToken",claimed.get("claimToken"));work(()->request(active,target,"QUEUE_RELEASE",entry),r->{});
                    status.accept("Refresh and reopen the latest template before reviewing submissions.");return;
                }
                acceptClaim(target,entry,claimed);
            });
        });
    }
    void acceptClaim(Path target,JsonObject entry,JsonObject claimed){
        JsonObject form=claimed.getAsJsonObject("form");
        load.accept(claimed.getAsJsonObject("payload"));
        claim=entry.deepCopy();claim.add("claimToken",claimed.get("claimToken"));claim.add("schema",form.get("schema"));claim.add("templateRevision",form.get("templateRevision"));claimFile=target;
        boolean changed=!form.get("schema").equals(claimed.get("submittedSchema"));
        status.accept(changed?"Template fields changed since submission. Review missing/new fields and photos before printing.":"Submission claimed for 30 minutes. Review details/photos, then Print.");
    }
    record PrintHooks(Runnable before,Runnable sent){}
    boolean reviewing(){return claim!=null;}
    PrintHooks printing(Path target,JsonObject payload){
        if(claim==null)return null;
        if(payload==null)throw new IllegalStateException("Enable record saving before printing a queued submission.");
        if(!Objects.equals(target,claimFile))throw new IllegalStateException("The submission belongs to another template.");
        var active=library.get();var current=claim;var body=claim.deepCopy();body.addProperty("recordId",UUID.randomUUID().toString());body.add("payload",payload);
        return new PrintHooks(()->{
            try{request(active,target,"QUEUE_BEGIN",body);}catch(Exception ex){throw new IllegalStateException("Submission print was not sent: "+ex.getMessage(),ex);}
        },()->work(()->request(active,target,"QUEUE_SENT",body),r->{if(claim!=current)return;claim=null;claimFile=null;epoch++;status.accept("Submission job accepted by the printer driver. Verify physical output.");}));
    }
}
