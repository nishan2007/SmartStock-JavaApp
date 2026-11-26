package ui.screens;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import managers.PermissionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.*;

/** Focused role workspace backed exclusively by the authenticated LAN service. */
public class Roles_Permission extends JFrame {
  interface Gateway {
    LanApiClient.RoleAdminState state() throws Exception;

    LanApiClient.RolePermissionSelection selection(int id) throws Exception;

    void save(int id, Set<String> desktop, Set<String> mobile, String description, String key)
        throws Exception;

    LanApiClient.RoleRecord add(String name, Integer source, String key) throws Exception;
  }

  private static final Gateway LAN =
      new Gateway() {
        public LanApiClient.RoleAdminState state() throws Exception {
          return LanApiClient.loadRoleAdminState();
        }

        public LanApiClient.RolePermissionSelection selection(int id) throws Exception {
          return LanApiClient.loadRolePermissionSelection(id);
        }

        public void save(int id, Set<String> d, Set<String> m, String description, String key)
            throws Exception {
          LanApiClient.saveRolePermissions(id, d, m, description, key);
        }

        public LanApiClient.RoleRecord add(String name, Integer source, String key)
            throws Exception {
          return LanApiClient.addRole(name, source, key);
        }
      };
  private final Gateway gateway;
  private final DefaultListModel<LanApiClient.RoleRecord> roleModel = new DefaultListModel<>();
  private final JList<LanApiClient.RoleRecord> roleList = new JList<>(roleModel);
  private final JTextField roleSearch = field("Search roles…"),
      permissionSearch = field("Search permissions…"),
      description = field("Role description");
  private final JLabel roleTitle = label("Select a role", 34, true),
      changeStatus = label("Loading roles…", 20, true),
      categoryTitle = label("Permissions", 28, true),
      categoryDescription = label("Choose what this role can do in this area.", 18, false);
  private final JPanel categories = new WidthTrackingPanel(), rows = new WidthTrackingPanel();
  private final JCheckBox enabledOnly = new JCheckBox("Show enabled only");
  private final JTabbedPane tabs = new RoleTabs();
  private final JButton discard = button("Discard", DeckersPalette.ORANGE),
      review = button("Review changes", DeckersPalette.PURPLE),
      duplicate = button("Duplicate role", DeckersPalette.PURPLE),
      create = button("Create role", DeckersPalette.ORANGE);
  private final LoadingStatePanel loadingState = new LoadingStatePanel();
  private final Set<String> desktop = new LinkedHashSet<>(), mobile = new LinkedHashSet<>();
  private Set<String> originalDesktop = Set.of(), originalMobile = Set.of();
  private String originalDescription = "", category;
  private LanApiClient.RoleAdminState state;
  private LanApiClient.RoleRecord current;
  private boolean adjusting, ready, working;

  public Roles_Permission() {
    this(true);
  }

  public Roles_Permission(boolean navigation) {
    this(navigation, true);
  }

  /** Isolated preview constructor: load=false never contacts a store. */
  Roles_Permission(boolean navigation, boolean load) {
    this(navigation, load, LAN);
  }

  Roles_Permission(boolean navigation, boolean load, Gateway gateway) {
    this.gateway = gateway;
    setTitle("Role & Permission Management");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    if (navigation) setJMenuBar(AppMenuBar.create(this, "Roles_Permissions"));
    JPanel root = DeckersSwing.panel();
    root.setLayout(new BorderLayout(14, 20));
    root.setBorder(new EmptyBorder(20, 20, 16, 20));
    JPanel heading = new JPanel(new BorderLayout(0, 4));
    heading.setOpaque(false);
    heading.add(label("Roles & permissions", 40, true), BorderLayout.NORTH);
    heading.add(label("Decide what each role can do.", 20, false), BorderLayout.CENTER);
    heading.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, DeckersPalette.ORANGE),
            new EmptyBorder(0, 16, 0, 0)));
    root.add(heading, BorderLayout.NORTH);
    JPanel workspace = surface(new BorderLayout(14, 0));
    workspace.setOpaque(false);
    workspace.add(buildRoles(), BorderLayout.WEST);
    workspace.add(buildEditor(), BorderLayout.CENTER);
    root.add(workspace, BorderLayout.CENTER);
    JPanel footer = surface(new BorderLayout(12, 0));
    footer.setOpaque(false);
    changeStatus.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, DeckersPalette.PURPLE),
            new EmptyBorder(8, 14, 8, 0)));
    footer.add(changeStatus, BorderLayout.CENTER);
    JPanel actions = surface(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actions.setOpaque(false);
    actions.add(discard);
    actions.add(review);
    footer.add(actions, BorderLayout.EAST);
    JPanel south = surface(new BorderLayout());
    south.setOpaque(false);
    south.add(loadingState, BorderLayout.NORTH);
    south.add(footer, BorderLayout.SOUTH);
    root.add(south, BorderLayout.SOUTH);
    setContentPane(root);
    onChange(roleSearch, this::filterRoles);
    onChange(permissionSearch, this::searchPermissions);
    onChange(description, this::updateChanges);
    enabledOnly.addActionListener(e -> renderPermissions());
    tabs.addChangeListener(
        e -> {
          category = null;
          renderCategories();
        });
    discard.addActionListener(e -> resetDraft());
    review.addActionListener(e -> reviewChanges());
    create.addActionListener(e -> addRole(false));
    duplicate.addActionListener(e -> addRole(true));
    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            if (!working && abandonDraft()) dispose();
          }
        });
    setMinimumSize(new Dimension(960, 640));
    setSize(1440, 1024);
    setLocationRelativeTo(null);
    ThemeManager.applyToWindow(this);
    updateChanges();
    if (navigation) WindowHelper.configurePosWindow(this);
    if (navigation) styleNavigation(getJMenuBar());
    if (load) {
      loadState(null);
      loadBranding();
    }
    discard.setPreferredSize(new Dimension(175, 44));
    review.setPreferredSize(new Dimension(240, 44));
    duplicate.setPreferredSize(new Dimension(170, 40));
    create.setPreferredSize(new Dimension(280, 44));
    root.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent event) {
            JPanel sidebar = (JPanel) workspace.getComponent(0);
            sidebar.setPreferredSize(new Dimension(root.getWidth() < 1200 ? 235 : 312, 0));
            categories
                .getParent()
                .getParent()
                .setPreferredSize(new Dimension(root.getWidth() < 1200 ? 170 : 225, 0));
            resizeNavigation();
            workspace.revalidate();
            SwingUtilities.invokeLater(() -> renderPermissions());
          }
        });
  }

  private final JLabel companyName = label("Company", 26, true);

  private void loadBranding() {
    UiTaskRunner.submit(
        this,
        "roles.company-branding",
        () -> {
          var settings = managers.CompanyCustomizationManager.loadReceiptSettings();
          return new Branding(
              settings.companyName(),
              managers.CompanyCustomizationManager.loadCompanyLogo(settings));
        },
        branding -> {
          companyName.setText(branding.name());
          companyName.setToolTipText(branding.name());
          if (branding.logo() != null)
            companyName.setIcon(
                new ImageIcon(ui.design.DeckersLogoManager.scaleToFit(branding.logo(), 40, 36)));
        },
        ignored -> {});
  }

  private record Branding(String name, java.awt.image.BufferedImage logo) {}

  void styleNavigation(JMenuBar bar) {
    if (bar == null || Boolean.TRUE.equals(bar.getClientProperty("SmartStock.rolesNavigation")))
      return;
    bar.putClientProperty("SmartStock.rolesNavigation", true);
    JMenu status = null;
    for (Component c : bar.getComponents())
      if (c instanceof JMenu m && "Status".equals(m.getText())) status = m;
    if (status != null) {
      for (Component c : bar.getComponents()) {
        if (c instanceof JMenuItem item && !(item instanceof JMenu)) {
          bar.remove(c);
          status.add(item);
        } else if (c instanceof LoadingStatePanel
            || (c instanceof JLabel statusLabel
                && !"DEVELOPER / TEST".equals(statusLabel.getText()))) {
          bar.remove(c);
          status.add(c);
        } else if (c instanceof Box.Filler) bar.remove(c);
      }
    }
    JPanel brand = surface(new BorderLayout(12, 0));
    brand.setName("rolesCompanyBrand");
    brand.setBorder(new EmptyBorder(8, 20, 8, 16));
    brand.add(companyName, BorderLayout.CENTER);
    brand.add(label("│  SmartStock", 24, false), BorderLayout.EAST);
    brand.setMaximumSize(new Dimension(340, 56));
    brand.setPreferredSize(new Dimension(340, 56));
    bar.add(brand, 0);
    bar.setUI(new javax.swing.plaf.basic.BasicMenuBarUI());
    bar.setBackground(DeckersPalette.surface());
    bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.border()));
    for (Component c : bar.getComponents())
      if (c instanceof JMenu menu) {
        menu.setFont(new Font("SansSerif", Font.PLAIN, 17));
        menu.setUI(new javax.swing.plaf.basic.BasicMenuUI());
        menu.setOpaque(true);
        menu.setBackground(DeckersPalette.surface());
        menu.setForeground(DeckersPalette.text());
        menu.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    0,
                    0,
                    3,
                    0,
                    "Admin".equals(menu.getText())
                        ? DeckersPalette.ORANGE
                        : DeckersPalette.surface()),
                new EmptyBorder(12, 12, 12, 12)));
      }
  }

  private void resizeNavigation() {
    JMenuBar bar = getJMenuBar();
    if (bar == null) return;
    boolean small = getRootPane().getWidth() < 1200;
    long count = Arrays.stream(bar.getComponents()).filter(c -> c instanceof JMenu).count();
    for (Component component : bar.getComponents()) {
      if ("rolesCompanyBrand".equals(component.getName())) {
        component.setPreferredSize(new Dimension(small ? 250 : count > 7 ? 330 : 400, 56));
        component.setMaximumSize(new Dimension(small ? 250 : count > 7 ? 330 : 400, 56));
      }
      if (component instanceof JMenu menu) {
        menu.setBorderPainted(true);
        menu.setFont(
            new Font(
                "SansSerif",
                "Admin".equals(menu.getText()) ? Font.BOLD : Font.PLAIN,
                small ? 14 : count > 7 ? 18 : 20));
        menu.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    0,
                    0,
                    3,
                    0,
                    "Admin".equals(menu.getText())
                        ? DeckersPalette.ORANGE
                        : DeckersPalette.surface()),
                new EmptyBorder(
                    12, small ? 8 : count > 7 ? 12 : 30, 12, small ? 8 : count > 7 ? 12 : 30)));
      }
    }
    bar.revalidate();
  }

  private JPanel buildRoles() {
    JPanel panel = surface(new BorderLayout(0, 14));
    panel.setPreferredSize(new Dimension(312, 0));
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DeckersPalette.border()),
            new EmptyBorder(14, 12, 14, 12)));
    panel.add(roleSearch, BorderLayout.NORTH);
    roleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    roleList.setFixedCellHeight(48);
    roleList.setBackground(DeckersPalette.surface());
    roleList.setCellRenderer(
        (list, value, index, selected, focus) -> {
          JLabel item = label(displayName(value.name()), 20, selected);
          item.setOpaque(true);
          item.setBackground(
              selected ? DeckersPalette.tileFill(DeckersPalette.PURPLE) : DeckersPalette.surface());
          item.setBorder(
              BorderFactory.createCompoundBorder(
                  BorderFactory.createMatteBorder(
                      0,
                      selected ? 4 : 0,
                      1,
                      0,
                      selected ? DeckersPalette.PURPLE : DeckersPalette.border()),
                  new EmptyBorder(9, selected ? 8 : 12, 9, 6)));
          return item;
        });
    roleList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && !adjusting) selectRole(roleList.getSelectedValue());
        });
    panel.add(scroll(roleList), BorderLayout.CENTER);
    panel.add(create, BorderLayout.SOUTH);
    return panel;
  }

  private JPanel buildEditor() {
    JPanel editor = surface(new BorderLayout(0, 14));
    editor.setBorder(new EmptyBorder(0, 8, 0, 0));
    JPanel top = surface(new BorderLayout(16, 8));
    DeckersSwing.styleBand(top, DeckersPalette.PURPLE, new Insets(8, 14, 10, 14));
    JPanel title = surface(new BorderLayout());
    title.setOpaque(false);
    title.add(roleTitle, BorderLayout.CENTER);
    title.add(duplicate, BorderLayout.EAST);
    top.add(title, BorderLayout.NORTH);
    JPanel details = surface(new BorderLayout(12, 0));
    details.setOpaque(false);
    details.add(label("Description:", 18, false), BorderLayout.WEST);
    details.add(description, BorderLayout.CENTER);
    top.add(details, BorderLayout.CENTER);
    editor.add(top, BorderLayout.NORTH);
    JPanel body = surface(new BorderLayout(0, 14)), tools = surface(new BorderLayout(0, 14));
    tabs.setFont(new Font("SansSerif", Font.BOLD, 20));
    tabs.addTab("Desktop", new JPanel());
    tabs.addTab("Mobile App", new JPanel());
    tabs.setPreferredSize(new Dimension(0, 48));
    tools.add(tabs, BorderLayout.NORTH);
    JPanel search = surface(new GridBagLayout());
    GridBagConstraints searchConstraints = new GridBagConstraints();
    searchConstraints.fill = GridBagConstraints.HORIZONTAL;
    searchConstraints.weightx = 0.55;
    search.add(permissionSearch, searchConstraints);
    searchConstraints.gridx = 1;
    searchConstraints.weightx = 0.45;
    search.add(Box.createHorizontalStrut(20), searchConstraints);
    enabledOnly.setFont(new Font("SansSerif", Font.PLAIN, 18));
    enabledOnly.setOpaque(false);
    searchConstraints.gridx = 2;
    searchConstraints.weightx = 0;
    search.add(enabledOnly, searchConstraints);
    tools.add(search, BorderLayout.CENTER);
    body.add(tools, BorderLayout.NORTH);
    JPanel content = surface(new BorderLayout(22, 0));
    categories.setOpaque(false);
    categories.setLayout(new BoxLayout(categories, BoxLayout.Y_AXIS));
    JScrollPane catScroll = scroll(categories);
    catScroll.setPreferredSize(new Dimension(225, 0));
    content.add(catScroll, BorderLayout.WEST);
    JPanel permissions = surface(new BorderLayout(0, 6)), band = surface(new BorderLayout(0, 4));
    DeckersSwing.styleBand(band, DeckersPalette.PURPLE, new Insets(8, 12, 8, 12));
    band.setBackground(DeckersPalette.tileFill(DeckersPalette.PURPLE));
    band.add(categoryTitle, BorderLayout.NORTH);
    band.add(categoryDescription, BorderLayout.CENTER);
    permissions.add(band, BorderLayout.NORTH);
    rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
    rows.setBackground(DeckersPalette.surface());
    permissions.add(scroll(rows), BorderLayout.CENTER);
    content.add(permissions, BorderLayout.CENTER);
    body.add(content, BorderLayout.CENTER);
    editor.add(body, BorderLayout.CENTER);
    return editor;
  }

  public static void openWindow() {
    if (!WindowHelper.focusIfAlreadyOpen(Roles_Permission.class))
      SwingUtilities.invokeLater(() -> new Roles_Permission().setVisible(true));
  }

  private void loadState(Integer id) {
    ready = false;
    updateChanges();
    loadingState.loading(false, java.time.Instant.now());
    UiTaskRunner.submit(
        this,
        "roles.admin.state",
        gateway::state,
        loaded -> {
          state = loaded;
          tabs.setEnabledAt(1, state.mobileAvailable());
          filterRoles();
          if (roleModel.isEmpty()) {
            changeStatus.setText("No roles match your search.");
            return;
          }
          int selected = 0;
          if (id != null)
            for (int i = 0; i < roleModel.size(); i++)
              if (roleModel.get(i).roleId() == id) {
                selected = i;
                break;
              }
          roleList.setSelectedIndex(selected);
          loadingState.ready(java.time.Instant.now());
        },
        ex -> loadingState.failed(ex.getMessage(), false, () -> loadState(id)));
  }

  private void filterRoles() {
    if (state == null) return;
    adjusting = true;
    roleModel.clear();
    String q = roleSearch.getText().strip().toLowerCase(Locale.ROOT);
    for (var r : state.roles())
      if (displayName(r.name()).toLowerCase(Locale.ROOT).contains(q)) roleModel.addElement(r);
    if (current != null)
      for (int i = 0; i < roleModel.size(); i++)
        if (roleModel.get(i).roleId() == current.roleId()) roleList.setSelectedIndex(i);
    adjusting = false;
  }

  private void selectRole(LanApiClient.RoleRecord role) {
    if (role == null || (current != null && current.roleId() == role.roleId())) return;
    if (working || !abandonDraft()) {
      adjusting = true;
      roleList.setSelectedValue(current, true);
      adjusting = false;
      return;
    }
    current = role;
    ready = false;
    desktop.clear();
    mobile.clear();
    description.setText("");
    roleTitle.setText(displayName(role.name()));
    updateChanges();
    renderCategories();
    loadSelection(role);
  }

  private void loadSelection(LanApiClient.RoleRecord role) {
    loadingState.loading(false, java.time.Instant.now());
    UiTaskRunner.submit(
        this,
        "roles.selection",
        () -> gateway.selection(role.roleId()),
        selection -> {
          if (current != null && current.roleId() == role.roleId()) {
            applySelection(role, selection);
            loadingState.ready(java.time.Instant.now());
          }
        },
        ex -> loadingState.failed(ex.getMessage(), false, () -> loadSelection(role)));
  }

  void applySelection(
      LanApiClient.RoleRecord role, LanApiClient.RolePermissionSelection selection) {
    current = role;
    originalDesktop = normalise(selection.permissionKeys());
    originalMobile = normalise(selection.mobilePermissionKeys());
    originalDescription = text(role.description(), "");
    ready = true;
    roleTitle.setText(displayName(role.name()));
    resetDraft();
    renderCategories();
  }

  private void resetDraft() {
    desktop.clear();
    desktop.addAll(originalDesktop);
    mobile.clear();
    mobile.addAll(originalMobile);
    description.setText(originalDescription);
    renderPermissions();
    updateChanges();
  }

  private List<LanApiClient.PermissionRecord> definitions() {
    if (state == null) return List.of();
    var list = tabs.getSelectedIndex() == 1 ? state.mobilePermissions() : state.permissions();
    return list == null ? List.of() : list;
  }

  private Set<String> selected() {
    return tabs.getSelectedIndex() == 1 ? mobile : desktop;
  }

  private boolean matches(LanApiClient.PermissionRecord permission, String query) {
    return (text(permission.label(), "")
            + " "
            + text(permission.description(), "")
            + " "
            + permission.key()
            + " "
            + permission.group()
            + " "
            + permission.subgroup())
        .toLowerCase(Locale.ROOT)
        .contains(query);
  }

  private void searchPermissions() {
    String query = permissionSearch.getText().strip().toLowerCase(Locale.ROOT);
    if (!query.isEmpty()
        && definitions().stream()
            .noneMatch(
                p -> Objects.equals(text(p.group(), "General"), category) && matches(p, query))) {
      definitions().stream()
          .filter(p -> matches(p, query))
          .findFirst()
          .ifPresent(p -> category = text(p.group(), "General"));
    }
    renderCategories();
  }

  private void renderCategories() {
    categories.removeAll();
    Set<String> groups = new LinkedHashSet<>();
    for (var p : definitions()) groups.add(text(p.group(), "General"));
    if (category == null || !groups.contains(category))
      category = groups.stream().findFirst().orElse(null);
    for (String group : groups) {
      JButton item =
          button(
              displayName(group),
              Objects.equals(group, category) ? DeckersPalette.PURPLE : DeckersPalette.ORANGE);
      item.setHorizontalAlignment(SwingConstants.LEFT);
      item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
      item.setPreferredSize(new Dimension(215, 52));
      item.setFont(
          new Font("SansSerif", Objects.equals(group, category) ? Font.BOLD : Font.PLAIN, 20));
      item.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.border()),
              BorderFactory.createCompoundBorder(
                  BorderFactory.createMatteBorder(
                      0,
                      4,
                      0,
                      0,
                      Objects.equals(group, category)
                          ? DeckersPalette.PURPLE
                          : DeckersPalette.ORANGE),
                  new EmptyBorder(10, 10, 10, 6))));
      if (!Objects.equals(group, category)) item.setBackground(DeckersPalette.surface());
      if (Objects.equals(group, category))
        item.setBackground(DeckersPalette.tileFill(DeckersPalette.PURPLE));
      item.addActionListener(
          e -> {
            category = group;
            renderCategories();
          });
      categories.add(item);
      categories.add(Box.createVerticalStrut(6));
    }
    categories.revalidate();
    categories.repaint();
    renderPermissions();
  }

  private void renderPermissions() {
    rows.removeAll();
    categoryTitle.setText(category == null ? "Permissions" : displayName(category));
    categoryDescription.setText(
        "Custom Orders".equalsIgnoreCase(category)
            ? "Control who can create, view, manage, price, refund, and deliver custom orders."
            : "Choose what this role can do in this area.");
    String query = permissionSearch.getText().strip().toLowerCase(Locale.ROOT);
    int count = 0;
    for (var p : definitions()) {
      String key = p.key().toUpperCase(Locale.ROOT), name = text(p.label(), displayName(p.key()));
      if (!Objects.equals(text(p.group(), "General"), category)
          || (enabledOnly.isSelected() && !selected().contains(key))) continue;
      if (!matches(p, query)) continue;
      JPanel row = surface(new BorderLayout(16, 0));
      row.setAlignmentX(Component.LEFT_ALIGNMENT);
      row.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.border()),
              new EmptyBorder(8, 10, 8, 8)));
      JCheckBox check = new JCheckBox();
      Icon nativeIcon = UIManager.getIcon("CheckBox.icon");
      if (nativeIcon == null
          && check.getUI() instanceof javax.swing.plaf.basic.BasicRadioButtonUI ui)
        nativeIcon = ui.getDefaultIcon();
      if (nativeIcon != null) check.setIcon(new ScaledIcon(nativeIcon, 22));
      check.setOpaque(false);
      check.setSelected(selected().contains(key));
      check.setEnabled(ready && !working);
      check.getAccessibleContext().setAccessibleName(name);
      row.add(check, BorderLayout.WEST);
      JPanel words = surface(new BorderLayout(8, 3)),
          line = surface(new FlowLayout(FlowLayout.LEFT, 0, 0));
      line.add(label(name, 21, true));
      if (sensitive(key)) {
        line.add(Box.createHorizontalStrut(8));
        JLabel badge = label("Sensitive", 14, false);
        badge.setOpaque(true);
        badge.setBackground(DeckersPalette.sectionFill(DeckersPalette.CORAL));
        badge.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.CORAL), new EmptyBorder(1, 6, 1, 6)));
        badge.setForeground(
            DeckersPalette.dark() ? new Color(255, 170, 160) : new Color(160, 38, 27));
        line.add(badge);
      }
      words.add(line, BorderLayout.NORTH);
      words.add(
          new WrappingLabel(
              "<html>"
                  + html(
                      text(
                          p.description(),
                          "Allows access to " + name.toLowerCase(Locale.ROOT) + "."))
                  + "</html>",
              rows),
          BorderLayout.CENTER);
      row.add(words, BorderLayout.CENTER);
      row.setMaximumSize(
          new Dimension(Integer.MAX_VALUE, Math.max(70, row.getPreferredSize().height)));
      check.addActionListener(
          e -> {
            if (check.isSelected()) selected().add(key);
            else selected().remove(key);
            updateChanges();
            if (enabledOnly.isSelected()) renderPermissions();
          });
      rows.add(row);
      count++;
    }
    if (count == 0) {
      JLabel empty =
          label(
              ready
                  ? "No permissions match your filters."
                  : "Select a role to load its permissions.",
              15,
              false);
      empty.setBorder(new EmptyBorder(20, 12, 20, 12));
      rows.add(empty);
    }
    rows.revalidate();
    rows.repaint();
  }

  private int changeCount() {
    int n = 0;
    for (String k : union(desktop, originalDesktop))
      if (desktop.contains(k) != originalDesktop.contains(k)) n++;
    for (String k : union(mobile, originalMobile))
      if (mobile.contains(k) != originalMobile.contains(k)) n++;
    return n + (description.getText().equals(originalDescription) ? 0 : 1);
  }

  private void updateChanges() {
    int n = changeCount();
    changeStatus.setText(
        working
            ? "Saving changes…"
            : !ready
                ? "Select a role to manage permissions"
                : n == 0
                    ? "No unsaved changes"
                    : n
                        + " unsaved change"
                        + (n == 1 ? "" : "s")
                        + " for "
                        + displayName(current.name()));
    review.setEnabled(ready && !working && n > 0);
    discard.setEnabled(ready && !working && n > 0);
    // Older LAN servers omit descriptions and cannot atomically copy a policy.
    // Keep their existing permission editor usable without pretending new fields saved.
    boolean roleDetails = current != null && current.description() != null;
    description.setEnabled(ready && !working && roleDetails);
    duplicate.setEnabled(ready && !working && roleDetails);
    description.setToolTipText(
        roleDetails
            ? "Describe what this role does (up to 2000 characters)."
            : "Update the store server to edit role descriptions.");
    duplicate.setToolTipText(
        roleDetails
            ? "Copy this role's saved desktop and mobile permissions."
            : "Update the store server to duplicate roles.");
    create.setEnabled(!working);
  }

  private boolean abandonDraft() {
    return !ready
        || changeCount() == 0
        || choose(
                "Unsaved changes",
                label(
                    "Discard unsaved changes for " + displayName(current.name()) + "?", 18, false),
                "Discard changes",
                "Keep editing")
            == 0;
  }

  private void reviewChanges() {
    if (!ready || working || changeCount() == 0) return;
    StringBuilder s =
        new StringBuilder(
            "Changes for "
                + displayName(current.name())
                + "\nThese changes affect everyone assigned to this role.\n\n");
    appendChanges(s, "Desktop", state.permissions(), desktop, originalDesktop);
    appendChanges(s, "Mobile App", state.mobilePermissions(), mobile, originalMobile);
    if (!description.getText().equals(originalDescription))
      s.append("Description\n").append(description.getText()).append('\n');
    JTextArea area = new JTextArea(s.toString(), Math.min(14, Math.max(6, changeCount() + 5)), 55);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setFont(new Font("SansSerif", Font.PLAIN, 18));
    if (choose("Review changes", new JScrollPane(area), "Save permissions", "Keep editing") == 0)
      savePermissions();
  }

  private int choose(String title, Object message, String primary, String secondary) {
    JOptionPane pane =
        new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
    JButton proceed = button(primary, DeckersPalette.ORANGE),
        cancel = button(secondary, DeckersPalette.PURPLE);
    pane.setOptions(new Object[] {proceed, cancel});
    proceed.addActionListener(e -> pane.setValue(0));
    cancel.addActionListener(e -> pane.setValue(1));
    JDialog dialog = pane.createDialog(this, title);
    ThemeManager.applyToWindow(dialog);
    dialog.getRootPane().setDefaultButton(cancel);
    if ("Create role".equals(primary) && message instanceof JPanel form) {
      for (Component component : form.getComponents())
        if (component instanceof JTextField input) {
          Runnable validate =
              () ->
                  proceed.setEnabled(
                      !input.getText().isBlank() && input.getText().strip().length() <= 100);
          onChange(input, validate);
          validate.run();
          dialog.getRootPane().setDefaultButton(proceed);
          dialog.addWindowListener(
              new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent event) {
                  input.requestFocusInWindow();
                }
              });
        }
    }
    dialog.pack();
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    Object value = pane.getValue();
    dialog.dispose();
    return value instanceof Integer number ? number : JOptionPane.CLOSED_OPTION;
  }

  private static void appendChanges(
      StringBuilder out,
      String scope,
      List<LanApiClient.PermissionRecord> defs,
      Set<String> draft,
      Set<String> original) {
    if (defs == null) return;
    for (var p : defs) {
      String k = p.key().toUpperCase(Locale.ROOT);
      if (draft.contains(k) != original.contains(k))
        out.append(scope)
            .append(" · ")
            .append(draft.contains(k) ? "Allow: " : "Remove: ")
            .append(p.label())
            .append(sensitive(k) ? " (Sensitive)" : "")
            .append('\n');
    }
    out.append('\n');
  }

  private void savePermissions() {
    var role = current;
    Set<String> d = Set.copyOf(desktop), m = Set.copyOf(mobile);
    String desc = description.getText();
    working = true;
    updateChanges();
    renderPermissions();
    UiTaskRunner.submit(
        this,
        "roles.save-permissions",
        () -> {
          gateway.save(role.roleId(), d, m, desc, UUID.randomUUID().toString());
          return true;
        },
        ignored -> {
          working = false;
          originalDesktop = d;
          originalMobile = m;
          originalDescription = desc;
          updateRoleDescription(role, role.description() == null ? null : desc);
          SessionDataCache.invalidate("roles.");
          if (role.name().equalsIgnoreCase(PermissionManager.getCurrentRole()))
            PermissionManager.refreshOpenWindows();
          styleNavigation(getJMenuBar());
          loadingState.ready(java.time.Instant.now());
          updateChanges();
          renderPermissions();
        },
        ex -> {
          working = false;
          updateChanges();
          renderPermissions();
          showError("Permissions could not be saved", ex);
        });
  }

  private void updateRoleDescription(LanApiClient.RoleRecord role, String value) {
    var updated = new LanApiClient.RoleRecord(role.roleId(), role.name(), value);
    current = updated;
    List<LanApiClient.RoleRecord> roles = new ArrayList<>(state.roles());
    for (int i = 0; i < roles.size(); i++)
      if (roles.get(i).roleId() == role.roleId()) roles.set(i, updated);
    state =
        new LanApiClient.RoleAdminState(
            roles, state.permissions(), state.mobilePermissions(), state.mobileAvailable());
    filterRoles();
  }

  private void addRole(boolean copy) {
    if (working || !abandonDraft()) return;
    JTextField input = field("Role name");
    input.setColumns(28);
    JPanel form = surface(new BorderLayout(0, 12));
    form.add(
        label(
            copy
                ? "Name for the copy of " + displayName(current.name()) + ":"
                : "Enter new role name:",
            18,
            false),
        BorderLayout.NORTH);
    form.add(input, BorderLayout.CENTER);
    if (choose(copy ? "Duplicate role" : "Create role", form, "Create role", "Cancel") != 0) return;
    String name = input.getText().strip();
    if (name.isBlank()) return;
    Integer source = copy ? current.roleId() : null;
    working = true;
    updateChanges();
    UiTaskRunner.submit(
        this,
        "roles.add",
        () -> gateway.add(name, source, UUID.randomUUID().toString()),
        added -> {
          working = false;
          ready = false;
          current = null;
          roleSearch.setText("");
          SessionDataCache.invalidate("roles.");
          loadState(added.roleId());
        },
        ex -> {
          working = false;
          updateChanges();
          showError("The role could not be added", ex);
        });
  }

  static boolean sensitive(String key) {
    return key.contains("OVERRIDE")
        || key.contains("REFUND")
        || key.contains("RECOVERY")
        || key.equals("ROLE_MANAGEMENT")
        || key.contains("REVERSE")
        || key.contains("DELETE")
        || key.contains("BEFORE_PRICING");
  }

  private static Set<String> normalise(List<String> values) {
    Set<String> r = new LinkedHashSet<>();
    if (values != null) for (String v : values) r.add(v.toUpperCase(Locale.ROOT));
    return r;
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    Set<String> x = new LinkedHashSet<>(a);
    x.addAll(b);
    return x;
  }

  private static String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  static String displayName(String value) {
    if (value == null) return "";
    if (!value.equals(value.toUpperCase(Locale.ROOT))) return value;
    StringBuilder b = new StringBuilder();
    for (String word : value.strip().replace('_', ' ').split("\\s+")) {
      if (word.isEmpty()) continue;
      if (!b.isEmpty()) b.append(' ');
      b.append(Character.toUpperCase(word.charAt(0)))
          .append(word.substring(1).toLowerCase(Locale.ROOT));
    }
    return b.toString();
  }

  private static String html(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static final class RoleTabs extends JTabbedPane {
    @Override
    public void updateUI() {
      setUI(
          new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            protected Insets getTabInsets(int placement, int index) {
              return new Insets(10, 20, 10, 20);
            }

            protected void paintText(
                Graphics g,
                int placement,
                Font font,
                FontMetrics metrics,
                int index,
                String title,
                Rectangle rectangle,
                boolean selected) {
              Font actual = font.deriveFont(selected ? Font.BOLD : Font.PLAIN);
              super.paintText(
                  g,
                  placement,
                  actual,
                  g.getFontMetrics(actual),
                  index,
                  title,
                  rectangle,
                  selected);
            }

            protected Insets getContentBorderInsets(int placement) {
              return new Insets(0, 0, 0, 0);
            }

            protected void paintContentBorder(Graphics g, int placement, int selected) {}

            protected void paintTabBackground(
                Graphics g,
                int placement,
                int index,
                int x,
                int y,
                int w,
                int h,
                boolean selected) {
              g.setColor(DeckersPalette.surface());
              g.fillRect(x, y, w, h);
            }

            protected void paintTabBorder(
                Graphics g,
                int placement,
                int index,
                int x,
                int y,
                int w,
                int h,
                boolean selected) {
              if (selected) {
                g.setColor(DeckersPalette.ORANGE);
                g.fillRect(x, y + h - 4, w, 4);
              }
            }

            protected int calculateTabHeight(int placement, int index, int fontHeight) {
              return 44;
            }
          });
    }
  }

  private static final class ScaledIcon implements Icon {
    private final Icon delegate;
    private final int size;

    ScaledIcon(Icon delegate, int size) {
      this.delegate = delegate;
      this.size = size;
    }

    public int getIconWidth() {
      return size;
    }

    public int getIconHeight() {
      return size;
    }

    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      var nativeImage =
          new java.awt.image.BufferedImage(
              delegate.getIconWidth(),
              delegate.getIconHeight(),
              java.awt.image.BufferedImage.TYPE_INT_ARGB);
      Graphics2D nativeGraphics = nativeImage.createGraphics();
      delegate.paintIcon(component, nativeGraphics, 0, 0);
      nativeGraphics.dispose();
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(nativeImage, x, y, size, size, null);
      g.dispose();
    }
  }

  private static final class WrappingLabel extends JLabel {
    private final JPanel viewportContent;

    WrappingLabel(String text, JPanel viewportContent) {
      super(text);
      this.viewportContent = viewportContent;
      setFont(new Font("SansSerif", Font.PLAIN, 18));
      setForeground(DeckersPalette.muted());
    }

    @Override
    public Dimension getPreferredSize() {
      var view =
          (javax.swing.text.View) getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
      if (view == null) return super.getPreferredSize();
      int width =
          Math.max(160, (viewportContent.getWidth() > 0 ? viewportContent.getWidth() : 760) - 64);
      view.setSize(width, 0);
      return new Dimension(
          width, (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)));
    }
  }

  private static final class WidthTrackingPanel extends JPanel implements Scrollable {
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
      return 24;
    }

    public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
      return Math.max(24, r.height - 24);
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private static JPanel surface(LayoutManager layout) {
    JPanel p = new JPanel(layout);
    p.setBackground(DeckersPalette.surface());
    p.putClientProperty("SmartStock.preserveBackground", true);
    return p;
  }

  private static JLabel label(String value, int size, boolean bold) {
    JLabel l = new JLabel(value);
    l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
    l.setForeground(bold ? DeckersPalette.text() : DeckersPalette.muted());
    return l;
  }

  private static JTextField field(String hint) {
    boolean search = hint.startsWith("Search");
    JTextField f =
        new JTextField() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (search && SEARCH_ICON_FONT != null) {
              Graphics2D iconGraphics = (Graphics2D) g.create();
              iconGraphics.setRenderingHint(
                  RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
              iconGraphics.setFont(SEARCH_ICON_FONT.deriveFont(20f));
              iconGraphics.setColor(DeckersPalette.muted());
              FontMetrics metrics = iconGraphics.getFontMetrics();
              iconGraphics.drawString(
                  "\uf002", 12, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
              iconGraphics.dispose();
            }
            if (getText().isEmpty() && !isFocusOwner()) {
              Graphics2D copy = (Graphics2D) g.create();
              copy.setColor(DeckersPalette.muted());
              copy.setFont(getFont());
              Insets in = getInsets();
              copy.drawString(
                  hint,
                  in.left,
                  (getHeight() - getFontMetrics(getFont()).getHeight()) / 2
                      + getFontMetrics(getFont()).getAscent());
              copy.dispose();
            }
          }
        };
    DeckersSwing.styleField(f);
    if (search)
      f.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(DeckersPalette.border()),
              new EmptyBorder(2, 42, 2, 10)));
    f.setFont(new Font("SansSerif", Font.PLAIN, 18));
    f.setPreferredSize(new Dimension(160, 40));
    f.setToolTipText(hint);
    f.getAccessibleContext().setAccessibleName(hint);
    return f;
  }

  private static final Font SEARCH_ICON_FONT = loadSearchIconFont();

  private static Font loadSearchIconFont() {
    try (var input = Roles_Permission.class.getResourceAsStream("/Images/sales-task-icons.ttf")) {
      return input == null ? null : Font.createFont(Font.TRUETYPE_FONT, input);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static JButton button(String text, Color accent) {
    return new AccentButton(text, accent);
  }

  private static final class AccentButton extends JButton {
    private Color accent;

    AccentButton(String text, Color accent) {
      super(text);
      this.accent = accent;
      updateUI();
    }

    @Override
    public void updateUI() {
      setUI(new javax.swing.plaf.basic.BasicButtonUI());
      if (accent != null) {
        DeckersSwing.styleUtilityButton(this, accent);
        setFont(new Font("SansSerif", Font.BOLD, 20));
        setBorderPainted(true);
        putClientProperty("SmartStock.ownedButtonBackground", DeckersPalette.sectionFill(accent));
        putClientProperty("SmartStock.ownedButtonForeground", DeckersPalette.text());
      }
    }
  }

  private static JScrollPane scroll(Component c) {
    JScrollPane s = new JScrollPane(c);
    s.setBorder(BorderFactory.createEmptyBorder());
    s.getViewport().setBackground(DeckersPalette.surface());
    s.getVerticalScrollBar().setUnitIncrement(24);
    return s;
  }

  private static void onChange(JTextField field, Runnable action) {
    field
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                action.run();
              }

              public void removeUpdate(DocumentEvent e) {
                action.run();
              }

              public void changedUpdate(DocumentEvent e) {
                action.run();
              }
            });
  }

  private void showError(String message, Throwable ex) {
    JOptionPane.showMessageDialog(
        this, message + ".\n\n" + ex.getMessage(), "SmartStock Server", JOptionPane.ERROR_MESSAGE);
  }
}
