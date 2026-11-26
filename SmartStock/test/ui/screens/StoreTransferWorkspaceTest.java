package ui.screens;

import org.junit.jupiter.api.Test;
import services.LanApiClient;
import ui.design.DeckersLogoManager;
import ui.helpers.ThemeManager;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class StoreTransferWorkspaceTest {
    private static final ZoneId ZONE=ZoneId.of("America/Guyana");
    private static final long SENT=LocalDateTime.of(2026,10,30,10,15).atZone(ZONE).toInstant().toEpochMilli();
    private static StoreTransferWorkspace.Transfer outgoing(long id,String status){return new StoreTransferWorkspace.Transfer(id,false,"Georgetown",2,SENT,"Alex","Restock weekend display.",3,18,status);}
    private static StoreTransferWorkspace.Transfer incoming(){return new StoreTransferWorkspace.Transfer(2039,true,"Georgetown",0,SENT-86400000,"Priya","Verify the quantities before receiving.",3,18,"PENDING");}
    private static List<LanApiClient.TransferDetailItem> lines(){return List.of(new LanApiClient.TransferDetailItem(101,"POLO-NV-M","Navy polo shirt (M)",6,36),new LanApiClient.TransferDetailItem(102,"TEE-WH-L","White T-shirt (L)",8,52),new LanApiClient.TransferDetailItem(103,"MUG-WH","White ceramic mug",4,24));}
    private static StoreTransferWorkspace workspace(){
        var logo=DeckersLogoManager.loadDeckersLogoIcon(StoreTransferWorkspaceTest.class);
        var scaled=logo==null?null:new ImageIcon(DeckersLogoManager.scaleToFit(logo.getImage(),190,64));
        return new StoreTransferWorkspace("SmartStock",scaled,"Skeldon","Alex",ZONE,LocalDateTime.of(2026,10,30,10,42));
    }
    private static final class Backend implements StoreTransferController.Gateway {
        List<StoreTransferWorkspace.Transfer> transfers=new ArrayList<>(List.of(outgoing(2048,"PENDING"),new StoreTransferWorkspace.Transfer(2047,false,"Georgetown",2,LocalDateTime.of(2026,10,29,15,20).atZone(ZONE).toInstant().toEpochMilli(),"Alex","Menswear replenishment.",2,12,"CHANGE_PENDING:test"),new StoreTransferWorkspace.Transfer(2046,false,"New Amsterdam",3,LocalDateTime.of(2026,10,28,11,5).atZone(ZONE).toInstant().toEpochMilli(),"Alex","Additional stock for promotion.",4,22,"PENDING"),incoming()));
        LanApiClient.CreateTransferRequest request,original;List<LanApiClient.TransferLine> reviewed;String action,key;long id;int mutations;boolean fail;
        public StoreTransferController.Snapshot load(){return new StoreTransferController.Snapshot(List.of(new LanApiClient.TransferLocation(2,"Georgetown"),new LanApiClient.TransferLocation(3,"New Amsterdam")),List.copyOf(transfers),"Store sync up to date",true);}
        public List<LanApiClient.TransferDetailItem> items(long id){return lines();}
        public List<LanApiClient.TransferProduct> products(String q){return List.of(new LanApiClient.TransferProduct(104,"PEN-BL","Blue pen",45));}
        public LanApiClient.CreateTransferResult create(LanApiClient.CreateTransferRequest r,String k)throws Exception{return change(3000,"CREATE",r,k);}
        public LanApiClient.CreateTransferResult change(long id,String action,LanApiClient.CreateTransferRequest r,String k)throws Exception{this.id=id;this.action=action;this.request=r;this.key=k;mutations++;if(fail)throw new java.io.IOException("Lost response");if(!"CREATE".equals(action))transfers.replaceAll(t->t.id()==id?outgoing(id,"CHANGE_PENDING:test"):t);else transfers.add(outgoing(id,"PENDING"));return new LanApiClient.CreateTransferResult(id,r==null?3:r.lines().size());}
        @Override public LanApiClient.CreateTransferResult change(long id,String action,LanApiClient.CreateTransferRequest r,LanApiClient.CreateTransferRequest expected,String key)throws Exception{original=expected;return change(id,action,r,key);}
        @Override public LanApiClient.ReceiveTransferResult receive(long id,List<LanApiClient.TransferLine> expected,String key){reviewed=expected;return receive(id,key);}
        public LanApiClient.ReceiveTransferResult receive(long id,String key){this.id=id;this.action="RECEIVE";this.key=key;mutations++;transfers.removeIf(t->t.id()==id&&t.incoming());return new LanApiClient.ReceiveTransferResult(id,"SK-RECEIVE-1",3);}
    }
    private static final class Dialogs implements StoreTransferController.Dialogs {
        boolean confirm=true,authorized=true,closed;String title,text,info;
        public boolean authorize(String action){return authorized;}
        public boolean confirm(String title,String text){this.title=title;this.text=text;return confirm;}
        public void information(String text){info=text;}
        public void close(){closed=true;}
    }
    private static final class Immediate implements StoreTransferController.Tasks {
        public <T> void run(String key,Callable<T> task,Consumer<T> success,Consumer<Throwable> failure){try{success.accept(task.call());}catch(Throwable error){failure.accept(error);}}
    }
    private static final class Deferred implements StoreTransferController.Tasks {
        final List<Runnable> tasks=new ArrayList<>();
        public <T> void run(String key,Callable<T> task,Consumer<T> success,Consumer<Throwable> failure){tasks.add(()->{try{success.accept(task.call());}catch(Throwable error){failure.accept(error);}});}
        void run(int index){tasks.remove(index).run();}
    }
    private record Fixture(StoreTransferWorkspace view,StoreTransferController controller,Backend backend,Dialogs dialogs){}
    private static Fixture fixture(){var v=workspace();var b=new Backend();var d=new Dialogs();var c=new StoreTransferController(v,b,new Immediate(),d);c.refresh();try{SwingUtilities.updateComponentTreeUI(v);var apply=ThemeManager.class.getDeclaredMethod("applyToComponent",Component.class);apply.setAccessible(true);apply.invoke(null,v);}catch(Exception e){throw new RuntimeException(e);}return new Fixture(v,c,b,d);}

    @Test void scannedItemClearsResultsAndEmptySearchDoesNotReloadInventory()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var v=workspace();var tasks=new Deferred();var c=new StoreTransferController(v,new Backend(),tasks,new Dialogs());
            v.newDraft();v.productSearch.setText("barcode");c.submitSearch("barcode");tasks.run(0);
            assertEquals(1,v.items.getRowCount());assertEquals("",v.productSearch.getText());assertFalse(v.productPopup.isVisible());
            c.search("");assertTrue(tasks.tasks.isEmpty());
            var product=new LanApiClient.TransferProduct(1,"SKU","Shirt",4,"Blue","Vanilla","M","Cotton");
            assertEquals("Color: Blue · Flavor: Vanilla · Size: M · Cotton",product.details());
        });
    }

    @Test void savedDraftReopensWithoutSendingAndIsRemovedOnlyAfterSuccessfulSend()throws Exception {
        Path directory=Files.createTempDirectory("transfer-draft-workflow");
        SwingUtilities.invokeAndWait(()->{
            var f=fixture();var v=f.view;var store=new TransferDraftStore(directory);f.controller.drafts(store);f.controller.newTransfer();
            v.products(List.of(new LanApiClient.TransferProduct(104,"PEN-BL","Blue pen",45)));v.addProduct();v.note.setText("Tomorrow");
            f.controller.saveDraft();assertEquals(0,f.backend.mutations);assertFalse(v.dirty());assertEquals(1,v.savedDrafts.getItemCount());
            f.controller.newTransfer();assertEquals(0,v.items.getRowCount());f.controller.openDraft();
            assertEquals("Tomorrow",v.note.getText());assertEquals(1,v.items.getRowCount());assertEquals(0,f.backend.mutations);
            v.note.setText("Updated");f.controller.saveDraft();assertEquals(1,v.savedDrafts.getItemCount());
            f.backend.fail=true;f.controller.submit();assertEquals(1,v.savedDrafts.getItemCount());
            f.backend.fail=false;f.controller.submit();assertEquals(0,v.savedDrafts.getItemCount());
            try{assertTrue(store.load().isEmpty());Files.delete(directory);}catch(Exception error){throw new RuntimeException(error);}
        });
    }

    @Test void savingFailureKeepsUnsavedChangesAndDeletingCopyDoesNotClearItems()throws Exception {
        Path directory=Files.createTempDirectory("transfer-draft-errors");Path blocked=Files.createFile(directory.resolve("blocked"));
        SwingUtilities.invokeAndWait(()->{
            var f=fixture();var v=f.view;f.controller.newTransfer();v.note.setText("Keep this");
            f.controller.drafts(new TransferDraftStore(blocked));f.controller.saveDraft();assertTrue(v.dirty());assertFalse(v.busy());assertEquals("Keep this",v.note.getText());assertEquals(0,f.backend.mutations);
            var store=new TransferDraftStore(directory.resolve("valid"));f.controller.drafts(store);f.controller.saveDraft();assertFalse(v.dirty());
            f.dialogs.confirm=false;f.controller.deleteDraft();assertEquals(1,v.savedDrafts.getItemCount());
            f.dialogs.confirm=true;f.controller.deleteDraft();assertEquals(0,v.savedDrafts.getItemCount());assertTrue(v.dirty());assertEquals("Keep this",v.note.getText());assertEquals(0,f.backend.mutations);
            try{Files.delete(directory.resolve("valid"));Files.delete(blocked);Files.delete(directory);}catch(Exception error){throw new RuntimeException(error);}
        });
    }

    @Test void broadSearchRendersAllResultsWithoutHtmlAndEscapeCancelsPendingScan()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var v=workspace();var b=new Backend();var tasks=new Deferred();var c=new StoreTransferController(v,b,tasks,new Dialogs());v.locations(b.load().locations());v.newDraft();
            List<LanApiClient.TransferProduct> matches=new ArrayList<>();for(int i=0;i<300;i++)matches.add(new LanApiClient.TransferProduct(1000+i,"SKU-"+i,"Product <html> "+i,10));
            v.products(matches);assertEquals(300,v.productModel.size());assertTrue(v.productPopup.isVisible());
            var renderer=v.products.getCellRenderer();Component first=renderer.getListCellRendererComponent(v.products,matches.get(0),0,false,false);
            Component last=renderer.getListCellRendererComponent(v.products,matches.get(299),299,false,false);assertSame(first,last);assertEquals(Boolean.TRUE,((JComponent)last).getClientProperty("html.disable"));
            v.setSize(1280,900);layout(v);assertTrue(v.products.getPreferredSize().height>=300*54);
            v.productSearch.setText("barcode");v.productSearch.postActionEvent();
            v.productSearch.getActionMap().get("dismiss-results").actionPerformed(new java.awt.event.ActionEvent(v.productSearch,0,"escape"));tasks.run(0);
            assertEquals(0,v.items.getRowCount());assertFalse(v.productPopup.isVisible());
            // Returning to an earlier query must not accept an earlier in-flight response.
            v.productSearch.setText("same");c.search("same");v.productSearch.setText("other");v.productSearch.setText("same");tasks.run(0);assertTrue(v.productModel.isEmpty());
        });
    }

    @Test void realWindowSearchDoesNotBlockOtherWindowAndRecoversFromNetworkFailure()throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1),finished=new java.util.concurrent.CountDownLatch(1);
        JFrame[] windows=new JFrame[2];StoreTransferWorkspace[] views=new StoreTransferWorkspace[1];StoreTransferController[] controls=new StoreTransferController[1];
        try {
            SwingUtilities.invokeAndWait(()->{
                var v=workspace();views[0]=v;var backend=new Backend();
                StoreTransferController.Gateway gateway=new StoreTransferController.Gateway(){
                    public StoreTransferController.Snapshot load(){return backend.load();}
                    public List<LanApiClient.TransferDetailItem> items(long id){return lines();}
                    public List<LanApiClient.TransferProduct> products(String query)throws Exception{assertFalse(SwingUtilities.isEventDispatchThread());started.countDown();assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));throw new java.io.IOException("Simulated network timeout");}
                    public LanApiClient.CreateTransferResult create(LanApiClient.CreateTransferRequest r,String k)throws Exception{return backend.create(r,k);}
                    public LanApiClient.CreateTransferResult change(long id,String a,LanApiClient.CreateTransferRequest r,String k)throws Exception{return backend.change(id,a,r,k);}
                    public LanApiClient.ReceiveTransferResult receive(long id,String k){return backend.receive(id,k);}
                };
                windows[0]=new JFrame("Transfer search regression");windows[0].setContentPane(v);windows[0].setSize(1280,900);windows[0].setVisible(true);
                windows[1]=new JFrame("Other SmartStock window regression");JButton other=new JButton("Respond");windows[1].add(other);windows[1].setSize(220,120);windows[1].setVisible(true);
                controls[0]=new StoreTransferController(v,gateway,new StoreTransferController.Tasks(){public <T> void run(String key,Callable<T> task,Consumer<T> success,Consumer<Throwable> failure){ui.helpers.UiTaskRunner.submit(windows[0],key,task,success,error->{failure.accept(error);finished.countDown();});}},new Dialogs());
                v.locations(backend.load().locations());v.newDraft();v.productSearch.setText("network barcode");v.productSearch.postActionEvent();
            });
            assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.CountDownLatch responsive=new java.util.concurrent.CountDownLatch(1);
            SwingUtilities.invokeLater(()->{assertTrue(views[0].productSearch.isEnabled());assertFalse(views[0].busy());((JButton)windows[1].getContentPane().getComponent(0)).addActionListener(e->responsive.countDown());((JButton)windows[1].getContentPane().getComponent(0)).doClick();});
            assertTrue(responsive.await(3,java.util.concurrent.TimeUnit.SECONDS),"Another window must remain responsive during transfer search");
            release.countDown();assertTrue(finished.await(5,java.util.concurrent.TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{var v=views[0];assertTrue(v.message.getText().contains("Simulated network timeout"));assertFalse(v.busy());assertTrue(v.productSearch.isEnabled());v.productSearch.setText("retry");v.products(List.of(new LanApiClient.TransferProduct(104,"PEN","Pen",5)));v.addProduct();assertEquals(1,v.items.getRowCount());assertEquals(0,MenuSelectionManager.defaultManager().getSelectedPath().length);});
        } finally {release.countDown();SwingUtilities.invokeAndWait(()->{if(controls[0]!=null)controls[0].closed();for(JFrame window:windows)if(window!=null)window.dispose();});}
    }

    @Test void scannerEnterWaitsForAsyncResultAndDoesNotAddStaleOrDuplicateItems()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var v=workspace();var b=new Backend();var tasks=new Deferred();var c=new StoreTransferController(v,b,tasks,new Dialogs());
            v.locations(b.load().locations());v.newDraft();v.productSearch.setText("0123456789012");v.productSearch.postActionEvent();
            assertEquals(0,v.items.getRowCount());assertTrue(v.productSearch.isEnabled());
            // Debounced search can arrive after the scanner's immediate Enter search.
            c.search("0123456789012");tasks.run(0);assertEquals(0,v.items.getRowCount());tasks.run(0);
            assertEquals(1,v.items.getRowCount());assertEquals(104,v.items.getValueAt(0,0));assertEquals("",v.productSearch.getText());
            v.productSearch.setText("0123456789012");v.productSearch.postActionEvent();v.productSearch.postActionEvent();tasks.run(0);tasks.run(0);
            assertEquals(2,v.items.getValueAt(0,4));
            v.productSearch.setText("old barcode");v.productSearch.postActionEvent();v.productSearch.setText("new barcode");tasks.run(0);
            assertEquals(2,v.items.getValueAt(0,4));assertEquals("new barcode",v.productSearch.getText());assertFalse(v.busy());
            v.queueSearch.setText("still responds");assertEquals("still responds",v.queueSearch.getText());
        });
    }

    @Test void inventoryResultsNeverGrabApplicationInputAndSearchRemainsUsableAfterAdding()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var f=fixture();var v=f.view;v.create.doClick();
            MenuElement[] original=MenuSelectionManager.defaultManager().getSelectedPath();
            for(int i=0;i<30;i++){
                v.productSearch.setText("pen "+i);
                v.products(f.backend.products("pen"));
                assertTrue(v.productPopup.isVisible());
                assertTrue(SwingUtilities.isDescendingFrom(v.productPopup,v));
                assertArrayEquals(original,MenuSelectionManager.defaultManager().getSelectedPath(),"Inventory search must not install a global menu input grab");
                v.add.doClick();assertFalse(v.productPopup.isVisible());
                v.queueSearch.setText("2048");assertEquals(1,v.queueModel.size());v.queueSearch.setText("");
            }
            assertEquals(1,v.items.getRowCount());assertEquals(30,v.items.getValueAt(0,4));
            v.products(f.backend.products("pen"));v.productSearch.setText("different product");
            assertTrue(v.productModel.isEmpty());assertFalse(v.productPopup.isVisible());
            v.products(f.backend.products("pen"));
            v.productSearch.getActionMap().get("dismiss-results").actionPerformed(new java.awt.event.ActionEvent(v.productSearch,0,"escape"));
            assertFalse(v.productPopup.isVisible());assertArrayEquals(original,MenuSelectionManager.defaultManager().getSelectedPath());
        });
        // A separate event must still be processed after repeated additions.
        java.util.concurrent.CountDownLatch heartbeat=new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeLater(heartbeat::countDown);assertTrue(heartbeat.await(3,java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test void editorUsesExactProductIdentityCommitsQuantityAndRetainsRetryKey()throws Exception {SwingUtilities.invokeAndWait(()->{
        var f=fixture();var v=f.view;assertEquals(2048,v.selected().id());assertEquals(3,v.items.getRowCount());assertFalse(v.destination.isEnabled());
        assertTrue(v.table.editCellAt(0,4));JSpinner spinner=(JSpinner)v.table.getEditorComponent();spinner.setValue(7);
        v.note.setText("Changed notes");f.backend.fail=true;v.primary.doClick();assertEquals("EDIT",f.backend.action);assertEquals(2048,f.backend.id);assertEquals(2,f.backend.request.destinationLocationId());assertEquals(7,f.backend.request.lines().get(0).quantity());assertEquals("Changed notes",f.backend.request.note());assertEquals(6,f.backend.original.lines().get(0).quantity());assertEquals("Restock weekend display.",f.backend.original.note());String key=f.backend.key;
        assertFalse(v.busy());assertTrue(v.dirty());v.primary.doClick();assertEquals(key,f.backend.key);assertEquals(2,f.backend.mutations);
        f.backend.fail=false;v.primary.doClick();assertEquals(key,f.backend.key);assertFalse(v.primary.isEnabled());assertEquals("Awaiting store confirmation",v.selected().badge());
    });}
    @Test void newTransferAddsMergesRemovesAndValidatesItems()throws Exception {SwingUtilities.invokeAndWait(()->{
        var f=fixture();var v=f.view;v.create.doClick();assertNull(v.selected());assertTrue(v.destination.isEnabled());
        v.destination.setSelectedIndex(1);v.primary.doClick();assertEquals(0,f.backend.mutations);assertTrue(v.message.getText().contains("at least one"));
        v.products(f.backend.products("pen"));v.quantity.setValue(3);v.addProduct();v.quantity.setValue(2);v.addProduct();assertEquals(1,v.items.getRowCount());assertEquals(5,v.items.getValueAt(0,4));assertEquals(104,v.items.getValueAt(0,0));
        v.items.setValueAt(0,0,4);v.primary.doClick();assertEquals(0,f.backend.mutations);v.items.setValueAt(5,0,4);
        v.note.setText("Send five pens");v.primary.doClick();assertEquals("CREATE",f.backend.action);assertEquals(3,f.backend.request.destinationLocationId());assertEquals(5,f.backend.request.lines().get(0).quantity());
        v.create.doClick();v.products(f.backend.products("pen"));v.addProduct();assertTrue(v.table.editCellAt(0,5));((JButton)v.table.getEditorComponent()).doClick();assertEquals(0,v.items.getRowCount());
    });}
    @Test void dirtyGuardProtectsSelectionSearchAndWindowClose()throws Exception {SwingUtilities.invokeAndWait(()->{
        var f=fixture();var v=f.view;v.note.setText("Do not lose this");f.dialogs.confirm=false;v.queue.setSelectedIndex(2);assertEquals(2048,v.selected().id());assertEquals(2048,v.queue.getSelectedValue().id());
        v.create.doClick();assertEquals(2048,v.selected().id());v.incoming.doClick();assertFalse(v.incomingMode());v.more.doClick();assertEquals("Do not lose this",v.note.getText());v.back.doClick();assertFalse(f.dialogs.closed);
        v.queueSearch.setText("#2048");assertEquals(1,v.queueModel.size());assertEquals("Do not lose this",v.note.getText());
        f.dialogs.confirm=true;v.discard.doClick();assertEquals("Restock weekend display.",v.note.getText());assertFalse(v.dirty());
    });}
    @Test void incomingConfirmsLoadedItemsAndCannotMutateOutgoingData()throws Exception {SwingUtilities.invokeAndWait(()->{
        var f=fixture();var v=f.view;v.incoming.doClick();assertTrue(v.selected().incoming());assertEquals("Georgetown",v.source.getText());assertEquals("Skeldon",((StoreTransferWorkspace.Destination)v.destination.getSelectedItem()).name());
        assertEquals("Receive transfer",v.primary.getText());assertFalse(v.items.isCellEditable(0,4));assertFalse(v.cancel.isVisible());assertFalse(v.note.isEditable());
        f.dialogs.confirm=false;v.primary.doClick();assertEquals(0,f.backend.mutations);assertTrue(f.dialogs.text.contains("Navy polo shirt (M) | SKU: POLO-NV-M | Qty: 6"));
        f.dialogs.confirm=true;v.primary.doClick();assertEquals("RECEIVE",f.backend.action);assertEquals(6,f.backend.reviewed.get(0).quantity());assertEquals(101,f.backend.reviewed.get(0).productId());assertEquals(2039,f.backend.id);assertTrue(f.dialogs.info.contains("SK-RECEIVE-1"));
        assertNull(v.selected());v.newDraft();assertTrue(((StoreTransferWorkspace.Destination)v.destination.getSelectedItem()).id()>0);
    });}
    @Test void awaitingConfirmationIsReadOnlyAndCancellationTargetsTheDisplayedTransfer()throws Exception {SwingUtilities.invokeAndWait(()->{
        var f=fixture();var v=f.view;v.queue.setSelectedIndex(1);assertFalse(v.primary.isEnabled());assertFalse(v.cancel.isEnabled());assertFalse(v.items.isCellEditable(0,4));int count=v.items.getRowCount();v.removeRow(0);assertEquals(count,v.items.getRowCount());f.controller.cancel();f.controller.submit();assertEquals(0,f.backend.mutations);
        v.queue.setSelectedIndex(0);f.dialogs.authorized=false;v.cancel.doClick();assertEquals(0,f.backend.mutations);
        f.dialogs.authorized=true;f.dialogs.confirm=false;v.cancel.doClick();assertEquals(0,f.backend.mutations);f.dialogs.confirm=true;v.cancel.doClick();assertEquals(2048,f.backend.id);assertEquals("CANCEL",f.backend.action);assertTrue(f.dialogs.text.contains("receiving store confirms"));assertFalse(v.cancel.isEnabled());
    });}
    @Test void staleDetailsAndProductSearchCannotOverwriteAnotherTransferOrNewDraft()throws Exception {SwingUtilities.invokeAndWait(()->{
        var v=workspace();var b=new Backend();var tasks=new Deferred();var c=new StoreTransferController(v,b,tasks,new Dialogs());v.locations(b.load().locations());v.transfers(b.transfers);
        c.select(outgoing(2048,"PENDING"));assertFalse(v.primary.isEnabled());v.create.doClick();tasks.run(0);assertNull(v.selected());assertEquals(0,v.items.getRowCount());c.select(outgoing(2048,"PENDING"));c.select(incoming());tasks.run(1);assertTrue(v.selected().incoming());tasks.run(0);assertTrue(v.selected().incoming());assertEquals("Verify items",v.mode.getText());
        c.select(outgoing(2048,"PENDING"));tasks.run(0);c.search("pen");v.newDraft();c.select(incoming());tasks.run(0);assertTrue(v.productModel.isEmpty());tasks.run(0);
        c.select(outgoing(2048,"PENDING"));c.closed();tasks.run(0);assertFalse(v.primary.isEnabled());
    });}
    @Test void busyAndDetailFailureDisableAllMutationsAndNavigation()throws Exception {SwingUtilities.invokeAndWait(()->{
        var f=fixture();var v=f.view;v.busy(true);v.create.doClick();v.incoming.doClick();v.cancel.doClick();v.primary.doClick();assertEquals(0,f.backend.mutations);assertEquals(2048,v.selected().id());assertFalse(v.queue.isEnabled());
        v.busy(false);v.detailFailed("Unavailable");assertEquals(0,v.items.getRowCount());assertFalse(v.primary.isEnabled());assertFalse(v.cancel.isEnabled());f.controller.submit();assertEquals(0,f.backend.mutations);
    });}
    @Test void rendersReferenceCompactIncomingNewAndDarkStates()throws Exception {SwingUtilities.invokeAndWait(()->{
        try {
            var f=fixture();var v=f.view;Path out=Path.of(System.getProperty("smartstock.transferDesignOutput","target/store-transfer-design"));Files.createDirectories(out);
            BufferedImage reference=ImageIO.read(Path.of("docs/store-transfer-design/reference.png").toFile());v.setSize(reference.getWidth(),reference.getHeight());capture(v,out.resolve("workspace.png"));
            BufferedImage actual=ImageIO.read(out.resolve("workspace.png").toFile());BufferedImage comparison=new BufferedImage(actual.getWidth()*2,actual.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=comparison.createGraphics();g.drawImage(reference,0,0,null);g.drawImage(actual,actual.getWidth(),0,null);g.dispose();ImageIO.write(comparison,"png",out.resolve("comparison.png").toFile());
            assertVisible(v,v.primary);assertVisible(v,v.table);assertTrue(v.table.getWidth()>750);
            v.queue.setSelectedIndex(1);capture(v,out.resolve("awaiting.png"));v.incoming.doClick();capture(v,out.resolve("incoming.png"));v.create.doClick();capture(v,out.resolve("new.png"));
            f.controller.select(outgoing(2048,"PENDING"));v.setSize(1280,720);capture(v,out.resolve("compact-1280.png"));assertVisible(v,v.primary);assertVisible(v,v.cancel);
            v.setSize(1024,768);capture(v,out.resolve("compact-1024.png"));assertVisible(v,v.primary);
            java.lang.reflect.Field theme=ThemeManager.class.getDeclaredField("cachedDarkMode");theme.setAccessible(true);Object old=theme.get(null);
            try {theme.set(null,Boolean.TRUE);ThemeManager.applyLookAndFeelDefaults();SwingUtilities.updateComponentTreeUI(v);var apply=ThemeManager.class.getDeclaredMethod("applyToComponent",Component.class);apply.setAccessible(true);apply.invoke(null,v);v.setSize(reference.getWidth(),reference.getHeight());capture(v,out.resolve("dark.png"));assertVisible(v,v.primary);}
            finally {theme.set(null,old);ThemeManager.applyLookAndFeelDefaults();}
        }catch(Exception e){throw new RuntimeException(e);}
    });}
    static void layout(Container c){for(int i=0;i<3;i++)layoutOnce(c);}
    private static void layoutOnce(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layoutOnce(nested);}
    private static void assertVisible(Container root,Component c){Rectangle r=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),root);assertTrue(r.x>=0&&r.y>=0&&r.x+r.width<=root.getWidth()&&r.y+r.height<=root.getHeight(),"Control is clipped: "+r);}
    static void capture(StoreTransferWorkspace view,Path path)throws Exception {layout(view);BufferedImage image=new BufferedImage(view.getWidth(),view.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);view.printAll(g);g.dispose();ImageIO.write(image,"png",path.toFile());}
    /** Isolated, fictional-data native preview; never constructs a LAN gateway or opens a store database. */
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeLater(()->{
            var f=fixture();JFrame frame=new JFrame("SmartStock Transfer Desk — fictional preview");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);frame.setContentPane(f.view);
            Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();frame.setSize(Math.min(1490,screen.width-40),Math.min(1100,screen.height-80));frame.setLocationRelativeTo(null);frame.setVisible(true);
            ThemeManager.applyToWindow(frame);
            if(args.length>0)try {Path out=Path.of(args[0]);Files.createDirectories(out);capture(f.view,out.resolve("native-preview.png"));}catch(Exception e){throw new RuntimeException(e);}
        });
    }

}
