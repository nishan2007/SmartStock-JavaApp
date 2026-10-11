package services;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class InventoryCatalogCacheFreshnessTest {
 @Test void sessionCatalogExpiresSoOtherRegistersItemChangesBecomeVisible() {
  Instant loaded=Instant.parse("2026-10-06T18:00:00Z");
  var snapshot=new InventoryCatalogCache.Snapshot(new InventoryCatalogCache.Scope(1,1),List.of(),loaded);
  assertTrue(InventoryCatalogCache.isFresh(snapshot,loaded.plusSeconds(59)));
  assertFalse(InventoryCatalogCache.isFresh(snapshot,loaded.plusSeconds(60)));
  assertFalse(InventoryCatalogCache.isFresh(snapshot,loaded.plusSeconds(3600)));
 }
}
