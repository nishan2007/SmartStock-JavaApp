package ui.screens;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import services.LanApiClient;

class RolesPermissionsTest {
  static final LanApiClient.RoleRecord ROLE =
      new LanApiClient.RoleRecord(5, "Sales Associate", "Everyday sales");
  static final List<LanApiClient.PermissionRecord> DEFS =
      List.of(
          p("CREATE_CUSTOM_ORDER", "Create custom orders", "Custom Orders"),
          p("CUSTOM_ORDER_PRICE_OVERRIDE", "Override prices", "Custom Orders"),
          p("ROLE_MANAGEMENT", "Roles and permissions", "Administration"));

  static LanApiClient.PermissionRecord p(String key, String label, String group) {
    return new LanApiClient.PermissionRecord(
        key, label, group, "General", "Description for " + label);
  }

  static class Stub implements Roles_Permission.Gateway {
    volatile Set<String> desktop, mobile;
    volatile String description;
    volatile int savedRole;
    volatile boolean fail;
    final CountDownLatch saved = new CountDownLatch(1);

    public LanApiClient.RoleAdminState state() {
      return new LanApiClient.RoleAdminState(
          List.of(ROLE, new LanApiClient.RoleRecord(6, "Weekend Supervisor")),
          DEFS,
          List.of(p("MOBILE_VIEW", "View mobile orders", "Orders")),
          true);
    }

    public LanApiClient.RolePermissionSelection selection(int id) {
      return new LanApiClient.RolePermissionSelection(
          List.of("CREATE_CUSTOM_ORDER", "UNKNOWN_EXISTING_KEY"), List.of("MOBILE_VIEW"));
    }

    public void save(int id, Set<String> d, Set<String> m, String desc, String key)
        throws Exception {
      savedRole = id;
      desktop = d;
      mobile = m;
      description = desc;
      if (fail) throw new Exception("Test server failure");
      saved.countDown();
    }

    public LanApiClient.RoleRecord add(String name, Integer source, String key) {
      throw new AssertionError("Unexpected role creation");
    }
  }

  static <T> T member(Object obj, String name, Class<T> type) throws Exception {
    Field f = Roles_Permission.class.getDeclaredField(name);
    f.setAccessible(true);
    return type.cast(f.get(obj));
  }

  static void invoke(Object obj, String name) throws Exception {
    Method m = Roles_Permission.class.getDeclaredMethod(name);
    m.setAccessible(true);
    m.invoke(obj);
  }

  static Roles_Permission frame(Stub stub) throws Exception {
    Roles_Permission f = new Roles_Permission(false, false, stub);
    Field s = Roles_Permission.class.getDeclaredField("state");
    s.setAccessible(true);
    s.set(f, stub.state());
    f.applySelection(ROLE, stub.selection(5));
    f.pack();
    return f;
  }

  static void edt(Callable<Void> task) throws Exception {
    AtomicReference<Throwable> error = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            task.call();
          } catch (Throwable ex) {
            error.set(ex);
          }
        });
    if (error.get() != null) throw new AssertionError(error.get());
  }

  static List<JCheckBox> checks(Container root) {
    List<JCheckBox> out = new ArrayList<>();
    for (Component c : root.getComponents()) {
      if (c instanceof JCheckBox box) out.add(box);
      else if (c instanceof Container child) out.addAll(checks(child));
    }
    return out;
  }

  @Test
  void filtersAndTabsKeepDraftIncludingUnlistedPermissions() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    edt(
        () -> {
          var f = frame(new Stub());
          try {
            var rows = member(f, "rows", JPanel.class);
            assertEquals(2, checks(rows).size());
            checks(rows).get(1).doClick();
            member(f, "permissionSearch", JTextField.class).setText("no match");
            assertTrue(checks(rows).isEmpty());
            member(f, "permissionSearch", JTextField.class).setText("");
            assertTrue(checks(rows).get(1).isSelected());
            member(f, "tabs", JTabbedPane.class).setSelectedIndex(1);
            assertTrue(checks(rows).get(0).isSelected());
            member(f, "tabs", JTabbedPane.class).setSelectedIndex(0);
            assertTrue(checks(rows).get(1).isSelected());
            assertTrue(member(f, "desktop", Set.class).contains("UNKNOWN_EXISTING_KEY"));
            assertEquals(
                "1 unsaved change for Sales Associate",
                member(f, "changeStatus", JLabel.class).getText());
          } finally {
            f.dispose();
          }
          return null;
        });
  }

  @Test
  void discardRestoresBothScopesAndDescription() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    edt(
        () -> {
          var f = frame(new Stub());
          try {
            checks(member(f, "rows", JPanel.class)).get(1).doClick();
            member(f, "description", JTextField.class).setText("Changed");
            member(f, "tabs", JTabbedPane.class).setSelectedIndex(1);
            checks(member(f, "rows", JPanel.class)).get(0).doClick();
            member(f, "discard", JButton.class).doClick();
            assertEquals("Everyday sales", member(f, "description", JTextField.class).getText());
            assertTrue(checks(member(f, "rows", JPanel.class)).get(0).isSelected());
            assertFalse(member(f, "desktop", Set.class).contains("CUSTOM_ORDER_PRICE_OVERRIDE"));
            assertFalse(member(f, "review", JButton.class).isEnabled());
          } finally {
            f.dispose();
          }
          return null;
        });
  }

  @Test
  void savingUsesOneRoleAndBothScopesAndUpdatesDescriptionBaseline() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    var stub = new Stub();
    AtomicReference<Roles_Permission> ref = new AtomicReference<>();
    edt(
        () -> {
          var f = frame(stub);
          ref.set(f);
          checks(member(f, "rows", JPanel.class)).get(1).doClick();
          member(f, "description", JTextField.class).setText("Weekend sales");
          invoke(f, "savePermissions");
          assertFalse(member(f, "review", JButton.class).isEnabled());
          return null;
        });
    assertTrue(stub.saved.await(5, TimeUnit.SECONDS));
    // Flush success delivery without blocking Swing's event dispatch thread.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      AtomicReference<Boolean> done = new AtomicReference<>(false);
      edt(
          () -> {
            done.set(
                member(ref.get(), "changeStatus", JLabel.class)
                    .getText()
                    .equals("No unsaved changes"));
            return null;
          });
      if (done.get()) break;
      Thread.sleep(20);
    }
    edt(
        () -> {
          try {
            assertEquals(5, stub.savedRole);
            assertEquals(
                Set.of(
                    "CREATE_CUSTOM_ORDER", "UNKNOWN_EXISTING_KEY", "CUSTOM_ORDER_PRICE_OVERRIDE"),
                stub.desktop);
            assertEquals(Set.of("MOBILE_VIEW"), stub.mobile);
            assertEquals("Weekend sales", stub.description);
            assertEquals(
                "Weekend sales",
                member(ref.get(), "current", LanApiClient.RoleRecord.class).description());
            assertFalse(member(ref.get(), "review", JButton.class).isEnabled());
          } finally {
            ref.get().dispose();
          }
          return null;
        });
  }

  @Test
  void searchFindsOtherCategoriesWithoutDiscardingDraft() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    edt(
        () -> {
          var f = frame(new Stub());
          try {
            checks(member(f, "rows", JPanel.class)).get(1).doClick();
            member(f, "permissionSearch", JTextField.class).setText("Roles and permissions");
            assertEquals("Administration", member(f, "categoryTitle", JLabel.class).getText());
            assertEquals(1, checks(member(f, "rows", JPanel.class)).size());
            assertTrue(member(f, "desktop", Set.class).contains("CUSTOM_ORDER_PRICE_OVERRIDE"));
          } finally {
            f.dispose();
          }
          return null;
        });
  }

  @Test
  void lateRoleResponseCannotOverwriteMoreRecentSelection() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    Stub stub =
        new Stub() {
          @Override
          public LanApiClient.RolePermissionSelection selection(int id) {
            if (id == 6) {
              started.countDown();
              try {
                release.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException ignored) {
              }
              return new LanApiClient.RolePermissionSelection(
                  List.of("ROLE_MANAGEMENT"), List.of());
            }
            return super.selection(id);
          }
        };
    AtomicReference<Roles_Permission> ref = new AtomicReference<>();
    Method select =
        Roles_Permission.class.getDeclaredMethod("selectRole", LanApiClient.RoleRecord.class);
    select.setAccessible(true);
    edt(
        () -> {
          var f = frame(stub);
          ref.set(f);
          select.invoke(f, new LanApiClient.RoleRecord(6, "Supervisor", "Supervisor access"));
          return null;
        });
    assertTrue(started.await(5, TimeUnit.SECONDS));
    edt(
        () -> {
          select.invoke(ref.get(), new LanApiClient.RoleRecord(7, "Trainee", "Training access"));
          return null;
        });
    release.countDown();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    AtomicReference<Boolean> loaded = new AtomicReference<>(false);
    while (System.nanoTime() < deadline) {
      edt(
          () -> {
            loaded.set(
                member(ref.get(), "description", JTextField.class)
                    .getText()
                    .equals("Training access"));
            return null;
          });
      if (loaded.get()) break;
      Thread.sleep(20);
    }
    edt(
        () -> {
          try {
            assertTrue(loaded.get());
            assertEquals(7, member(ref.get(), "current", LanApiClient.RoleRecord.class).roleId());
            assertFalse(member(ref.get(), "desktop", Set.class).contains("ROLE_MANAGEMENT"));
          } finally {
            ref.get().dispose();
          }
          return null;
        });
  }

  @Test
  void olderServerStillAllowsPermissionEditingWithoutOfferingUnsupportedRoleCopy()
      throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    var legacy =
        new com.google.gson.Gson()
            .fromJson("{\"roleId\":5,\"name\":\"Sales Associate\"}", LanApiClient.RoleRecord.class);
    edt(
        () -> {
          var f = frame(new Stub());
          try {
            f.applySelection(
                legacy,
                new LanApiClient.RolePermissionSelection(
                    List.of("CREATE_CUSTOM_ORDER"), List.of()));
            assertFalse(member(f, "duplicate", JButton.class).isEnabled());
            assertFalse(member(f, "description", JTextField.class).isEnabled());
            assertTrue(checks(member(f, "rows", JPanel.class)).get(0).isEnabled());
          } finally {
            f.dispose();
          }
          return null;
        });
  }

  @Test
  void companyDefinedRoleNamesKeepTheirCase() {
    assertEquals("eCommerce Lead", Roles_Permission.displayName("eCommerce Lead"));
    assertEquals("Sales Associate", Roles_Permission.displayName("SALES_ASSOCIATE"));
  }
}
