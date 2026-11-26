package ui.screens;

import services.LanApiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Testable orchestration; every stock mutation still goes through the authenticated LAN server. */
final class StoreTransferController implements StoreTransferWorkspace.Handler {
    interface Gateway {
        Snapshot load() throws Exception;
        List<LanApiClient.TransferDetailItem> items(long id) throws Exception;
        List<LanApiClient.TransferProduct> products(String query) throws Exception;
        LanApiClient.CreateTransferResult create(LanApiClient.CreateTransferRequest request,String key) throws Exception;
        LanApiClient.CreateTransferResult change(long id,String action,LanApiClient.CreateTransferRequest request,String key) throws Exception;
        LanApiClient.ReceiveTransferResult receive(long id,String key) throws Exception;
        default LanApiClient.ReceiveTransferResult receive(long id,List<LanApiClient.TransferLine> expected,String key)throws Exception{return receive(id,key);}
        default LanApiClient.CreateTransferResult change(long id,String action,LanApiClient.CreateTransferRequest request,LanApiClient.CreateTransferRequest expected,String key)throws Exception{return change(id,action,request,key);}
    }
    record Snapshot(List<LanApiClient.TransferLocation> locations,List<StoreTransferWorkspace.Transfer> transfers,
                    String syncLabel,boolean syncHealthy) {}
    interface Tasks {<T> void run(String key,Callable<T> task,Consumer<T> success,Consumer<Throwable> failure);}
    interface Dialogs {
        boolean authorize(String action);
        boolean confirm(String title,String message);
        void information(String message);
        void close();
    }
    private final StoreTransferWorkspace view;
    private final Gateway gateway;
    private final Tasks tasks;
    private final Dialogs dialogs;
    private long selectionGeneration,searchGeneration,refreshGeneration;
    private String mutationFingerprint,mutationKey;
    private String submittedSearch;
    private LanApiClient.CreateTransferRequest baseline;
    private boolean closed;
    private TransferDraftStore drafts;
    private String draftId;

    StoreTransferController(StoreTransferWorkspace view,Gateway gateway,Tasks tasks,Dialogs dialogs){
        this.view=view;this.gateway=gateway;this.tasks=tasks;this.dialogs=dialogs;view.handler(this);
    }
    void drafts(TransferDraftStore store){drafts=store;reloadDrafts();}
    private void reloadDrafts(){
        if(drafts==null)return;
        tasks.run("store-transfer.drafts",drafts::load,rows->{if(!closed)view.savedDrafts(rows);},error->{if(!closed)view.message.setText("Could not load saved drafts: "+safe(error));});
    }
    @Override public void saveDraft(){
        if(closed||view.busy()||view.selected()!=null||drafts==null||!dialogs.authorize("Save transfer draft"))return;
        final TransferDraftStore.Draft draft;
        try{draft=view.draft(draftId==null?UUID.randomUUID().toString():draftId);}catch(IllegalArgumentException error){view.message.setText(error.getMessage());return;}
        dismissSearch();view.productPopup.setVisible(false);view.busy(true);
        tasks.run("store-transfer.save-draft",()->{drafts.save(draft);return draft;},saved->{if(closed)return;draftId=saved.id();view.clean();view.busy(false);view.message.setText("Draft saved on this computer. Stock has not changed.");reloadDrafts();},error->{if(!closed){view.busy(false);view.message.setText("Could not save draft: "+safe(error));}});
    }
    @Override public void openDraft(){
        if(closed||view.busy())return;var draft=(TransferDraftStore.Draft)view.savedDrafts.getSelectedItem();
        if(draft==null||!leave())return;
        selectionGeneration++;dismissSearch();baseline=null;mutationFingerprint=null;mutationKey=null;draftId=draft.id();view.restoreDraft(draft);
    }
    @Override public void deleteDraft(){
        if(closed||view.busy()||drafts==null)return;var draft=(TransferDraftStore.Draft)view.savedDrafts.getSelectedItem();
        if(draft==null||!dialogs.authorize("Delete transfer draft")||!dialogs.confirm("Delete draft","Delete this saved draft? Stock will not change."))return;
        view.busy(true);tasks.run("store-transfer.delete-draft",()->{drafts.delete(draft.id());return draft.id();},id->{if(closed)return;view.busy(false);if(id.equals(draftId)){draftId=null;view.unsaved();view.message.setText("Saved copy deleted. Current items remain unsaved.");}reloadDrafts();},error->{if(!closed){view.busy(false);view.message.setText("Could not delete draft: "+safe(error));}});
    }
    @Override public boolean leave(){return view.commitEditor()&&(!view.dirty()||dialogs.confirm("Unsaved transfer","Discard the unsaved changes to this transfer?"));}
    @Override public void back(){if(!view.busy()&&leave())dialogs.close();}
    void closed(){closed=true;selectionGeneration++;searchGeneration++;refreshGeneration++;view.productPopup.setVisible(false);}
    @Override public void refresh(){refresh(null,false);}
    private void refresh(Long preferred,boolean outgoing){
        if(closed)return;
        long generation=++refreshGeneration;
        StoreTransferWorkspace.Transfer prior=view.selected();
        boolean wasIncoming=preferred==null&&prior!=null?prior.incoming():!outgoing&&view.incomingMode();
        Long selectedId=preferred!=null?preferred:prior==null?null:prior.id();
        view.busy(true);view.message.setText("Loading transfers…");
        tasks.run("store-transfer.queue",gateway::load,snapshot->{
            if(closed||generation!=refreshGeneration)return;
            view.busy(false);view.locations(snapshot.locations());view.transfers(snapshot.transfers());view.status(true,snapshot.syncLabel(),snapshot.syncHealthy());
            StoreTransferWorkspace.Transfer target=null;
            if(selectedId!=null)for(var row:snapshot.transfers())if(row.id()==selectedId&&row.incoming()==wasIncoming){target=row;break;}
            if(target==null&&prior==null&&preferred==null)for(var row:snapshot.transfers())if(row.incoming()==view.incomingMode()){target=row;break;}
            if(target!=null)select(target);else {selectionGeneration++;draftId=null;view.newDraft();view.message.setText("");}
        },error->{if(closed||generation!=refreshGeneration)return;view.busy(false);view.status(false,"Store sync status unavailable",false);view.detailFailed("Could not load transfers: "+safe(error)+". Use Refresh to retry.");});
    }
    @Override public void newTransfer(){selectionGeneration++;searchGeneration++;submittedSearch=null;baseline=null;draftId=null;view.newDraft();}
    @Override public void select(StoreTransferWorkspace.Transfer row){
        if(closed||view.busy())return;
        long generation=++selectionGeneration;searchGeneration++;submittedSearch=null;baseline=null;draftId=null;view.productPopup.setVisible(false);view.showTransfer(row);
        tasks.run("store-transfer.details",()->gateway.items(row.id()),lines->{
            if(closed||generation!=selectionGeneration)return;
            view.detail(lines);
            baseline=new LanApiClient.CreateTransferRequest(row.destinationId(),row.note(),lines.stream().map(line->new LanApiClient.TransferLine(line.productId(),line.quantity())).toList());
        },error->{if(!closed&&generation==selectionGeneration)view.detailFailed("Could not load this transfer: "+safe(error)+". Use Refresh to retry.");});
    }
    @Override public void dismissSearch(){searchGeneration++;submittedSearch=null;}
    @Override public void submitSearch(String query){if(query.isBlank()||!view.editable())return;submittedSearch=query;search(query);}
    @Override public void search(String query){
        if(closed||!view.editable()||query.isBlank())return;
        if(submittedSearch!=null&&!submittedSearch.equals(query))submittedSearch=null;
        long generation=++searchGeneration;long selection=selectionGeneration;
        tasks.run("store-transfer.products",()->gateway.products(query),products->{if(!closed&&generation==searchGeneration&&selection==selectionGeneration&&view.editable()&&view.productSearch.getText().trim().equals(query)){view.products(products);if(query.equals(submittedSearch)){submittedSearch=null;if(products.size()==1)view.addScannedProduct();else if(products.size()>1)view.message.setText("Multiple products match this scan. Select the correct item before adding.");}}},error->{if(!closed&&generation==searchGeneration&&selection==selectionGeneration){submittedSearch=null;view.message.setText("Could not search inventory: "+safe(error));}});
    }
    @Override public void submit(){
        if(closed||view.busy()||!view.primary.isEnabled()||!dialogs.authorize("Save or receive transfer"))return;
        var selected=view.selected();
        if(selected!=null&&selected.incoming()){
            if(!view.commitEditor())return;
            StringBuilder message=new StringBuilder("Receive transfer #").append(selected.id()).append(" into this store's inventory?\n\nVerify these items and quantities:\n");
            List<LanApiClient.TransferLine> reviewed=new ArrayList<>();
            for(int i=0;i<view.items.getRowCount();i++)reviewed.add(new LanApiClient.TransferLine(((Number)view.items.getValueAt(i,0)).intValue(),((Number)view.items.getValueAt(i,4)).intValue()));
            List<LanApiClient.TransferLine> expected=List.copyOf(reviewed);
            for(int i=0;i<view.items.getRowCount();i++)message.append("• ").append(view.items.getValueAt(i,2)).append(" | SKU: ").append(view.items.getValueAt(i,1)).append(" | Qty: ").append(view.items.getValueAt(i,4)).append('\n');
            if(!dialogs.confirm("Receive transfer",message.toString()))return;
            String key=key("RECEIVE:"+selected.id()+":"+expected);view.busy(true);
            tasks.run("store-transfer.mutation",()->gateway.receive(selected.id(),expected,key),result->{
                if(closed)return;mutationSucceeded();dialogs.information("Transfer received successfully.\nReceive ID: "+result.receiveId());refresh();
            },this::mutationFailed);
            return;
        }
        final LanApiClient.CreateTransferRequest request;
        try{request=view.request();}catch(IllegalArgumentException error){view.message.setText(error.getMessage());return;}
        LanApiClient.CreateTransferRequest expected=baseline;
        String key=key((selected==null?"CREATE":"EDIT:"+selected.id())+":"+request+":"+expected);
        view.busy(true);
        tasks.run("store-transfer.mutation",()->selected==null?gateway.create(request,key):gateway.change(selected.id(),"EDIT",request,expected,key),result->{
            if(closed)return;mutationSucceeded();dialogs.information(selected==null?"Transfer sent successfully.\nTransfer ID: "+result.transferId()+"\nThe receiving store must verify it before stock is added.":"Transfer changes requested. They complete after the receiving store synchronizes. Transfers already received cannot be changed.");refresh(result.transferId(),true);
        },this::mutationFailed);
    }
    @Override public void cancel(){
        var selected=view.selected();
        if(closed||view.busy()||selected==null||selected.incoming()||!selected.pending()||!view.cancel.isEnabled()||!dialogs.authorize("Cancel transfer"))return;
        if(!dialogs.confirm("Cancel transfer","Cancel outgoing transfer #"+selected.id()+"?\nStock returns only after the receiving store confirms it has not been received.\nUnsaved edits will be discarded."))return;
        String key=key("CANCEL:"+selected.id());view.busy(true);
        tasks.run("store-transfer.mutation",()->gateway.change(selected.id(),"CANCEL",null,key),result->{
            if(closed)return;mutationSucceeded();dialogs.information("Cancellation requested. Stock will be restored after the receiving store confirms cancellation. A transfer already received cannot be cancelled.");refresh(selected.id(),true);
        },this::mutationFailed);
    }
    @Override public void discard(){if(closed||view.busy()||!leave())return;if(view.selected()==null){draftId=null;view.newDraft();}else select(view.selected());}
    private String key(String fingerprint){if(!fingerprint.equals(mutationFingerprint)||mutationKey==null){mutationFingerprint=fingerprint;mutationKey=UUID.randomUUID().toString();}return mutationKey;}
    private void mutationSucceeded(){
        if(draftId!=null&&drafts!=null){String sent=draftId;draftId=null;tasks.run("store-transfer.sent-draft",()->{drafts.delete(sent);return sent;},id->reloadDrafts(),error->dialogs.information("Transfer sent, but its saved draft could not be removed. Delete it from Saved drafts to avoid sending it again."));}
mutationFingerprint=null;mutationKey=null;view.clean();view.busy(false);ui.helpers.SessionDataCache.invalidate("transfer-products:");ui.helpers.SessionDataCache.invalidate("incoming-transfer");ui.helpers.SessionDataCache.invalidate("outgoing-transfer");}
    private void mutationFailed(Throwable error){if(!closed){view.busy(false);view.message.setText("Transfer failed: "+safe(error)+". Retry keeps the same request key.");}}
    private static String safe(Throwable error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}

    static final class LanGateway implements Gateway {
        public Snapshot load() throws Exception {
            var destinations=LanApiClient.loadTransferDestinations();
            List<StoreTransferWorkspace.Transfer> transfers=new ArrayList<>();
            for(var t:LanApiClient.loadIncomingTransfers())transfers.add(new StoreTransferWorkspace.Transfer(t.transferId(),true,t.fromStore(),0,t.createdAtEpochMillis(),t.sentBy(),t.note(),t.itemCount(),t.unitCount(),"PENDING"));
            for(var t:LanApiClient.loadOutgoingTransfers())transfers.add(new StoreTransferWorkspace.Transfer(t.transferId(),false,t.toStore(),t.destinationLocationId(),t.createdAtEpochMillis(),t.sentBy(),t.note(),t.itemCount(),t.unitCount(),t.status()));
            String sync="Store sync status unavailable";boolean healthy=false;
            if(managers.SessionManager.getCurrentPermissions().contains("SYNC_NOTIFICATIONS"))try{
                var status=LanApiClient.loadSyncStatus();
                healthy=status.cloudReachable()&&status.lastSuccessEpochMillis()>0&&status.failedCount()==0&&status.pendingCount()==0&&status.conflictCount()==0;
                sync=healthy?"Store sync up to date":status.failedCount()>0?"Store sync needs attention":status.pendingCount()>0?"Store sync: "+status.pendingCount()+" pending":"Store sync: waiting for connection";
            }catch(Exception ignored){/* Transfer permission does not grant sync administration access. */}
            return new Snapshot(List.copyOf(destinations),List.copyOf(transfers),sync,healthy);
        }
        public List<LanApiClient.TransferDetailItem> items(long id)throws Exception{return LanApiClient.loadTransferItems(id);}
        public List<LanApiClient.TransferProduct> products(String query)throws Exception{return LanApiClient.searchTransferProducts(query);}
        public LanApiClient.CreateTransferResult create(LanApiClient.CreateTransferRequest request,String key)throws Exception{return LanApiClient.createTransfer(request,key);}
        public LanApiClient.CreateTransferResult change(long id,String action,LanApiClient.CreateTransferRequest request,String key)throws Exception{return LanApiClient.changeTransfer(id,action,request,key);}
        public LanApiClient.ReceiveTransferResult receive(long id,String key)throws Exception{return LanApiClient.receiveTransfer(id,key);}
        @Override public LanApiClient.ReceiveTransferResult receive(long id,List<LanApiClient.TransferLine> expected,String key)throws Exception{return LanApiClient.receiveTransfer(id,expected,key);}
        @Override public LanApiClient.CreateTransferResult change(long id,String action,LanApiClient.CreateTransferRequest request,LanApiClient.CreateTransferRequest expected,String key)throws Exception{return LanApiClient.changeTransfer(id,action,request,expected,key);}
    }
}
