package ui.screens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TransferDraftStoreTest {
 @TempDir Path root;
 @Test void persistsUpdatesAndDeletes()throws Exception {
  String id=UUID.randomUUID().toString();var store=new TransferDraftStore(root);
  var draft=new TransferDraftStore.Draft(id,2,"Rosehall","Tomorrow",List.of(new TransferDraftStore.Line(1,"SKU","Blue shirt M",3)));
  store.save(draft);assertEquals(List.of(draft),new TransferDraftStore(root).load());
  var changed=new TransferDraftStore.Draft(id,2,"Rosehall","Changed",draft.lines());store.save(changed);
  assertEquals(List.of(changed),store.load());store.delete(id);assertTrue(store.load().isEmpty());
 }
 @Test void isolatesUsersAndStores()throws Exception {
  new TransferDraftStore(root.resolve("store-1/user-1")).save(new TransferDraftStore.Draft(UUID.randomUUID().toString(),0,"","",List.of()));
  assertTrue(new TransferDraftStore(root.resolve("store-1/user-2")).load().isEmpty());assertTrue(new TransferDraftStore(root.resolve("store-2/user-1")).load().isEmpty());
 }
 @Test void preservesSavedCopyOnInvalidInputAndReportsCorruption()throws Exception {
  var store=new TransferDraftStore(root);String id=UUID.randomUUID().toString();var good=new TransferDraftStore.Draft(id,2,"Rosehall","",List.of());store.save(good);
  assertThrows(IllegalArgumentException.class,()->store.save(new TransferDraftStore.Draft(id,2,"Rosehall","",List.of(new TransferDraftStore.Line(1,"SKU","Name",0)))));
  assertEquals(List.of(good),store.load());assertThrows(IllegalArgumentException.class,()->store.delete("../other"));
  Files.writeString(root.resolve(id+".json"),"broken json");assertThrows(java.io.IOException.class,store::load);assertEquals("broken json",Files.readString(root.resolve(id+".json")));
 }
}
