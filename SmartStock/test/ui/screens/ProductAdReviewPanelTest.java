package ui.screens;

import org.junit.jupiter.api.Test;
import services.LanApiClient;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ProductAdReviewPanelTest {
    private static class Fake implements ProductAdReviewPanel.Gateway {
        final List<LanApiClient.AdReviewItem> rows=new ArrayList<>();
        final List<String> decisions=Collections.synchronizedList(new ArrayList<>());
        volatile boolean fail,stale,photoUnavailable;
        final String png;
        Fake() throws Exception {
            var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB),"png",bytes);png=Base64.getEncoder().encodeToString(bytes.toByteArray());
            for(String kind:List.of("ad_square","ad_portrait","ad_story","ad_landscape"))rows.add(new LanApiClient.AdReviewItem(1,"Test product",kind,"pending",7));
        }
        public synchronized List<LanApiClient.AdReviewItem> list(String decision){return rows.stream().filter(x->x.decision().equals(decision)).toList();}
        public LanApiClient.AdDraft load(LanApiClient.AdReviewItem item){return new LanApiClient.AdDraft(item.decision(),stale?item.revision()+1:item.revision(),png);}
        public byte[] photo(LanApiClient.AdReviewItem item,String kind) {if(photoUnavailable)throw new IllegalStateException("Unavailable");return Base64.getDecoder().decode(png);}
        public synchronized void decide(LanApiClient.AdReviewItem item,boolean approve,String reason) {
            if(fail)throw new IllegalStateException("Connection lost");
            if(!approve)assertEquals("Fix the product text",reason);assertEquals(7,item.revision());decisions.add(item.kind()+":"+approve);rows.remove(item);
        }
    }
    private static ProductAdReviewPanel panel(Fake fake) throws Exception {
        var ref=new AtomicReference<ProductAdReviewPanel>();SwingUtilities.invokeAndWait(()->ref.set(new ProductAdReviewPanel(fake,()->{})));return ref.get();
    }
    private static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<end){var result=new java.util.concurrent.atomic.AtomicBoolean();SwingUtilities.invokeAndWait(()->result.set(ready.getAsBoolean()));if(result.get())return;Thread.sleep(20);}
        fail("Timed out waiting for the review UI");
    }
    @Test void approveAndRejectAdvanceAcrossEveryFormatUntilTheQueueIsEmpty() throws Exception {
        var fake=new Fake();var panel=panel(fake);
        for(String kind:List.of("ad_square","ad_portrait","ad_story","ad_landscape")) {
            await(()->panel.approve.isEnabled()&&kind.equals(panel.list.getSelectedValue().kind()));
            SwingUtilities.invokeAndWait(()->{if("ad_portrait".equals(kind)){panel.rejectionReason.setText("Fix the product text");panel.reject.doClick();}else panel.approve.doClick();});
        }
        await(()->panel.model.isEmpty()&&!panel.isWorking());
        SwingUtilities.invokeAndWait(()->{
            assertFalse(panel.approve.isEnabled());assertFalse(panel.reject.isEnabled());
            assertTrue(panel.preview.getText().contains("All shared ads reviewed"));
        });
        assertEquals(List.of("ad_square:true","ad_portrait:false","ad_story:true","ad_landscape:true"),fake.decisions);
    }
    @Test void failedReviewDoesNotSkipTheImageOrLeaveApprovalEnabled() throws Exception {
        var fake=new Fake();fake.fail=true;var panel=panel(fake);await(()->panel.approve.isEnabled());
        SwingUtilities.invokeAndWait(()->panel.approve.doClick());
        await(()->!panel.isWorking()&&panel.status.getText().contains("Review not confirmed"));
        SwingUtilities.invokeAndWait(()->{assertEquals(4,panel.model.size());assertEquals("ad_square",panel.list.getSelectedValue().kind());assertFalse(panel.approve.isEnabled());});
        assertTrue(fake.decisions.isEmpty());fake.fail=false;
        SwingUtilities.invokeAndWait(()->panel.refresh.doClick());await(()->panel.approve.isEnabled());
        SwingUtilities.invokeAndWait(()->panel.approve.doClick());await(()->panel.model.size()==3&&panel.approve.isEnabled());
        assertEquals(List.of("ad_square:true"),fake.decisions);
    }
    @Test void rejectionRequiresAReasonAndKeepsTheCurrentImageWhenMissing() throws Exception {
        var fake=new Fake();var panel=panel(fake);await(()->panel.reject.isEnabled());
        SwingUtilities.invokeAndWait(()->panel.reject.doClick());
        SwingUtilities.invokeAndWait(()->{assertEquals(4,panel.model.size());assertTrue(panel.status.getText().contains("Enter a rejection reason"));});
        assertTrue(fake.decisions.isEmpty());
    }
    @Test void changedSharedPreviewRequiresRefreshBeforeReview() throws Exception {
        var fake=new Fake();fake.stale=true;var panel=panel(fake);
        await(()->panel.status.getText().contains("changed on another register"));
        SwingUtilities.invokeAndWait(()->{assertFalse(panel.approve.isEnabled());assertFalse(panel.reject.isEnabled());});
        assertTrue(fake.decisions.isEmpty());fake.stale=false;
        SwingUtilities.invokeAndWait(()->panel.refresh.doClick());await(()->panel.approve.isEnabled());
    }
    @Test void sourcePhotosLoadAlongsideAdAndMissingPhotosDoNotBlockReview() throws Exception {
        var fake=new Fake();var panel=panel(fake);
        await(()->panel.approve.isEnabled()&&panel.originalPhoto.getIcon()!=null&&panel.aiPhoto.getIcon()!=null);
        fake.photoUnavailable=true;
        SwingUtilities.invokeAndWait(()->panel.list.setSelectedIndex(1));
        await(()->panel.approve.isEnabled()&&panel.originalPhoto.getText().contains("unavailable")&&panel.aiPhoto.getText().contains("unavailable"));
        SwingUtilities.invokeAndWait(()->{assertNull(panel.originalPhoto.getIcon());assertNull(panel.aiPhoto.getIcon());});
    }
    @Test void lateSourcePhotoCannotReplaceTheNewSelection() throws Exception {
        var started=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var fake=new Fake(){
            @Override public byte[] photo(LanApiClient.AdReviewItem item,String kind) {
                if(!"ad_square".equals(item.kind()))return null;
                started.countDown();
                try{release.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                return Base64.getDecoder().decode(png);
            }
        };
        var panel=panel(fake);
        try {
            assertTrue(started.await(10,java.util.concurrent.TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->panel.list.setSelectedIndex(1));
            await(()->panel.originalPhoto.getText().contains("unavailable")&&panel.aiPhoto.getText().contains("unavailable"));
        }finally{release.countDown();}
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(()->{assertNull(panel.originalPhoto.getIcon());assertNull(panel.aiPhoto.getIcon());assertEquals("ad_portrait",panel.list.getSelectedValue().kind());});
    }
}
