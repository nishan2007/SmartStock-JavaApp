package gy.deckers.studio;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class BadgeSubmissionWorkflowTest {
    @TempDir Path folder;
    @Test void claimLoadsValuesWithoutReplacingDesignAndPrintHooksUseTheSameSubmission()throws Exception{
        String id=UUID.randomUUID().toString();Path file=folder.resolve(id+".sstbadge");Files.writeString(file,"{}");Files.writeString(file.resolveSibling(file.getFileName()+".meta.json"),"{\"id\":\""+id+"\",\"storageStoreId\":17}");
        var begin=new AtomicReference<JsonObject>();var sent=new AtomicReference<JsonObject>();var done=new CountDownLatch(1);var loaded=new AtomicReference<JsonObject>();
        SharedBadgeLibrary library=new SharedBadgeLibrary(folder,body->{
            assertEquals(id,body.get("id").getAsString());assertEquals(17,body.get("storageStoreId").getAsInt());
            if(body.get("action").getAsString().equals("QUEUE_BEGIN"))begin.set(body.deepCopy());else if(body.get("action").getAsString().equals("QUEUE_SENT"))sent.set(body.deepCopy());else fail("Unexpected queue mutation");return new JsonObject();
        });
        BadgeSubmissionWorkflow workflow=new BadgeSubmissionWorkflow(new JPanel(),()->library,()->file,()->"current",loaded::set,message->{if(message.startsWith("Submission job accepted"))done.countDown();});
        JsonObject payload=JsonParser.parseString("{\"values\":{\"fullName\":\"Synthetic person\"},\"photos\":{}}").getAsJsonObject();
        JsonObject entry=JsonParser.parseString("{\"submissionId\":\""+UUID.randomUUID()+"\"}").getAsJsonObject();
        JsonObject claim=JsonParser.parseString("{\"claimToken\":\""+UUID.randomUUID()+"\",\"submittedSchema\":\"schema\",\"form\":{\"schema\":\"schema\",\"templateRevision\":\"current\"}}").getAsJsonObject();claim.add("payload",payload);
        SwingUtilities.invokeAndWait(()->workflow.acceptClaim(file,entry,claim));
        assertEquals(payload,loaded.get());assertTrue(workflow.reviewing());
        assertThrows(IllegalStateException.class,()->workflow.printing(folder.resolve("another.sstbadge"),payload));
        assertThrows(IllegalStateException.class,()->workflow.printing(file,null));
        var hooks=workflow.printing(file,payload);hooks.before().run();
        assertEquals(entry.get("submissionId"),begin.get().get("submissionId"));assertEquals("current",begin.get().get("templateRevision").getAsString());assertEquals(payload,begin.get().getAsJsonObject("payload"));
        SwingUtilities.invokeAndWait(hooks.sent());assertTrue(done.await(5,TimeUnit.SECONDS));
        assertEquals(begin.get().get("recordId"),sent.get().get("recordId"));assertFalse(workflow.reviewing());
    }
}
