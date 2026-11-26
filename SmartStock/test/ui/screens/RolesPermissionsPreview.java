package ui.screens;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import services.LanApiClient;

/** Native preview with an in-memory gateway; never accesses a store or real policy. */
public final class RolesPermissionsPreview {
  static Object member(Object object, String name) throws Exception {
    Field f = Roles_Permission.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(object);
  }

  static final List<LanApiClient.PermissionRecord> DEFS =
      List.of(
          p("MAKE_SALE", "Make a sale", "Sales", "Process everyday register sales."),
          p(
              "CREATE_CUSTOM_ORDER",
              "Create custom orders",
              "Custom Orders",
              "Create new custom orders for customers, including items, quantities, and details."),
          p(
              "VIEW_ASSIGNED_CUSTOM_ORDERS",
              "View assigned orders",
              "Custom Orders",
              "View custom orders assigned to the user, including details and status."),
          p(
              "MANAGE_CUSTOM_ORDERS",
              "Manage all custom orders",
              "Custom Orders",
              "View and edit any custom order, not just those assigned to the user."),
          p(
              "CUSTOM_ORDER_PRICE_OVERRIDE",
              "Override order price",
              "Custom Orders",
              "Change the total price of a custom order, including discounts and manual"
                  + " adjustments."),
          p(
              "CUSTOM_ORDER_REFUND_APPROVAL",
              "Approve custom order refunds",
              "Custom Orders",
              "Authorize and process refunds for custom orders."),
          p(
              "DELIVER_CUSTOM_ORDER_BEFORE_PRICING",
              "Deliver before pricing",
              "Custom Orders",
              "Mark a custom order as delivered before final pricing is completed."),
          p(
              "VIEW_INVENTORY",
              "View inventory",
              "Inventory",
              "Look up stock and product information."),
          p(
              "VIEW_CUSTOMERS",
              "View customers",
              "Customers",
              "Look up customers and their account details."),
          p("VIEW_EMPLOYEES", "View employees", "People", "Look up employee details."),
          p(
              "COMPANY_CUSTOMIZATION",
              "Company customization",
              "Administration",
              "Change company identity and document branding."),
          p(
              "COMPANY_PREFERENCES",
              "Company preferences",
              "Administration",
              "Change operational company settings."),
          p(
              "ROLE_MANAGEMENT",
              "Roles and permissions",
              "Administration",
              "Manage roles and their permissions."));

  static final class SampleGateway implements Roles_Permission.Gateway {
    final List<LanApiClient.RoleRecord> roles =
        new ArrayList<>(
            List.of(
                new LanApiClient.RoleRecord(1, "Admin"),
                new LanApiClient.RoleRecord(2, "Assistant Manager"),
                new LanApiClient.RoleRecord(3, "Jr. Sales Associate"),
                new LanApiClient.RoleRecord(4, "Manager"),
                new LanApiClient.RoleRecord(
                    5, "Sales Associate", "Handles everyday sales and customer orders"),
                new LanApiClient.RoleRecord(6, "Supervisor"),
                new LanApiClient.RoleRecord(7, "Trainee"),
                new LanApiClient.RoleRecord(8, "User")));
    final Map<Integer, LanApiClient.RolePermissionSelection> selections = new HashMap<>();

    public LanApiClient.RoleAdminState state() {
      return new LanApiClient.RoleAdminState(
          List.copyOf(roles),
          DEFS,
          List.of(
              p(
                  "VIEW_OVERSEAS_ORDERS",
                  "View overseas orders",
                  "Orders",
                  "Look up overseas orders from the mobile app.")),
          true);
    }

    public LanApiClient.RolePermissionSelection selection(int id) {
      return selections.getOrDefault(
          id,
          new LanApiClient.RolePermissionSelection(
              List.of("CREATE_CUSTOM_ORDER", "VIEW_ASSIGNED_CUSTOM_ORDERS"), List.of()));
    }

    public void save(int id, Set<String> d, Set<String> m, String description, String key) {
      selections.put(id, new LanApiClient.RolePermissionSelection(List.copyOf(d), List.copyOf(m)));
      for (int i = 0; i < roles.size(); i++)
        if (roles.get(i).roleId() == id)
          roles.set(i, new LanApiClient.RoleRecord(id, roles.get(i).name(), description));
    }

    public LanApiClient.RoleRecord add(String name, Integer source, String key) {
      int id = roles.size() + 1;
      var role =
          new LanApiClient.RoleRecord(
              id,
              name,
              source == null
                  ? "Custom role"
                  : roles.stream()
                      .filter(r -> r.roleId() == source)
                      .findFirst()
                      .orElseThrow()
                      .description());
      roles.add(role);
      selections.put(
          id,
          source == null
              ? new LanApiClient.RolePermissionSelection(List.of(), List.of())
              : selection(source));
      return role;
    }
  }

  public static void main(String[] args) throws Exception {
    Path out = Path.of(args.length > 0 ? args[0] : "target/roles-preview");
    Files.createDirectories(out);
    boolean capture = Arrays.asList(args).contains("--capture"),
        dark = Arrays.asList(args).contains("--dark"),
        small = Arrays.asList(args).contains("--small");
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    Field theme = ui.helpers.ThemeManager.class.getDeclaredField("cachedDarkMode");
    theme.setAccessible(true);
    theme.set(null, dark);
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            SampleGateway gateway = new SampleGateway();
            Roles_Permission frame = new Roles_Permission(false, false, gateway);
            frame.setTitle("Roles & permissions · Verified option 1 preview");
            JMenuBar menu = new JMenuBar();
            for (String name :
                List.of(
                    "Point of Sale",
                    "Operations",
                    "Orders",
                    "Inventory",
                    "Employee",
                    "Admin",
                    "Status")) menu.add(new JMenu(name));
            frame.setJMenuBar(menu);
            frame.styleNavigation(menu);
            ((JLabel) member(frame, "companyName")).setText("Deckers");
            Field state = Roles_Permission.class.getDeclaredField("state");
            state.setAccessible(true);
            state.set(frame, gateway.state());
            var role = gateway.roles.get(4);
            frame.applySelection(role, gateway.selection(5));
            Field category = Roles_Permission.class.getDeclaredField("category");
            category.setAccessible(true);
            category.set(frame, "Custom Orders");
            var render = Roles_Permission.class.getDeclaredMethod("renderCategories");
            render.setAccessible(true);
            render.invoke(frame);
            var filter = Roles_Permission.class.getDeclaredMethod("filterRoles");
            filter.setAccessible(true);
            filter.invoke(frame);
            // Reproduce the reference's pending-change state without changing persisted policies.
            Field original = Roles_Permission.class.getDeclaredField("originalDesktop");
            original.setAccessible(true);
            original.set(frame, Set.of());
            var update = Roles_Permission.class.getDeclaredMethod("updateChanges");
            update.setAccessible(true);
            update.invoke(frame);
            frame
                .getRootPane()
                .setPreferredSize(new Dimension(small ? 1024 : 1488, small ? 720 : 1058));
            frame.pack();
            frame.setVisible(true);
            frame.getRootPane().requestFocusInWindow();
            javax.swing.Timer timer =
                new javax.swing.Timer(
                    700,
                    e -> {
                      try {
                        Container c = frame.getRootPane();
                        BufferedImage img =
                            new BufferedImage(
                                c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = img.createGraphics();
                        c.paint(g);
                        g.dispose();
                        javax.imageio.ImageIO.write(
                            img, "png", out.resolve("workspace.png").toFile());
                        System.out.println("Preview ready: " + out.toAbsolutePath());
                        if (capture) {
                          frame.dispose();
                          System.exit(0);
                        }
                      } catch (Exception ex) {
                        throw new RuntimeException(ex);
                      }
                    });
            timer.setRepeats(false);
            timer.start();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  static LanApiClient.PermissionRecord p(String key, String label, String group, String desc) {
    return new LanApiClient.PermissionRecord(key, label, group, "General", desc);
  }
}
