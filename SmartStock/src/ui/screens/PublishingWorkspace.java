package ui.screens;

import com.google.gson.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import managers.SessionManager;
import publishing.*;
import services.LanApiClient;
import ui.design.*;

/** Independent publishing editor. UI edits are local until an explicit shared save. */
public final class PublishingWorkspace extends JFrame {
  private Publication document = Publication.preset("Card");
  private UUID projectId = UUID.randomUUID();
  private int revision, pageIndex, generation;
  private boolean dirty, masterMode, sheetMode, updating;
  private double zoom = 3;
  private final Deque<Publication> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
  private final Set<String> selection = new LinkedHashSet<>();
  private List<Publication.Element> clipboard = List.of();
  private final DefaultListModel<Publication.Page> pageModel = new DefaultListModel<>();
  private final DefaultListModel<Publication.Element> layerModel = new DefaultListModel<>();
  private final JList<Publication.Page> pages = new JList<>(pageModel);
  private final JList<Publication.Element> layers = new JList<>(layerModel);
  private final JPanel properties = DeckersSwing.panel();
  private final Canvas canvas = new Canvas();
  private final JLabel status = new JLabel("Ready"), metrics = new JLabel();
  private final JSpinner sheetNumber = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
  private final JCheckBox guides = new JCheckBox("Guides", true),
      snap = new JCheckBox("Snap 1 mm", true);
  private final JComboBox<String> units = new JComboBox<>(new String[] {"MM", "CM", "IN"});
  private SwingWorker<?, ?> task, previewTask;
  private BufferedImage sheetPreview, designPreview;
  private int renderedSheets, renderedGeneration = -1;
  private final javax.swing.Timer previewTimer = new javax.swing.Timer(180, e -> renderPreview());
  private final javax.swing.Timer autosave = new javax.swing.Timer(30000, e -> autosave());
  private final Path recovery =
      Path.of(
          System.getProperty("user.home"),
          ".smartstock",
          "publications",
          "recovery-"
              + SessionManager.getCurrentLocationId()
              + "-"
              + SessionManager.getCurrentUserId()
              + "-"
              + UUID.randomUUID()
              + ".sspub");

  public PublishingWorkspace() {
    super("SmartStock Publishing");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setSize(1320, 850);
    setMinimumSize(new Dimension(960, 640));
    setLocationRelativeTo(null);
    setJMenuBar(menu());
    setLayout(new BorderLayout(8, 8));
    getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));
    JPanel tools = DeckersSwing.panel();
    tools.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 4));
    for (String type : new String[] {"TEXT", "RECT", "ELLIPSE", "LINE", "TABLE", "QR", "BARCODE"})
      tools.add(button(type, () -> addObject(type)));
    tools.add(button("WordArt…", this::editWordArt));
    tools.add(button("Image…", this::importImage));
    tools.add(button("Undo", this::undo));
    tools.add(button("Redo", this::redo));
    tools.add(guides);
    tools.add(snap);
    tools.add(units);
    JSlider zoomSlider = new JSlider(25, 600, 100);
    zoomSlider.setPreferredSize(new Dimension(110, 24));
    tools.add(new JLabel("Zoom"));
    tools.add(zoomSlider);
    zoomSlider.addChangeListener(
        e -> {
          zoom = 3 * zoomSlider.getValue() / 100.;
          canvas.revalidate();
          canvas.repaint();
        });
    units.addActionListener(
        e -> {
          if (!updating) {
            checkpoint();
            document.displayUnit = (String) units.getSelectedItem();
            changed();
          }
        });
    guides.addActionListener(e -> canvas.repaint());
    add(horizontalTools(tools), BorderLayout.NORTH);
    JPanel left = DeckersSwing.panel();
    left.setLayout(new BorderLayout());
    JTabbedPane navigation = new JTabbedPane();
    navigation.addTab("Pages", new JScrollPane(pages));
    navigation.addTab("Layers", new JScrollPane(layers));
    left.add(navigation);
    JPanel pageButtons = new JPanel(new GridLayout(0, 1, 4, 4));
    pageButtons.add(button("Add page", this::addPage));
    pageButtons.add(button("Delete page", this::deletePage));
    pageButtons.add(
        button(
            "Masters",
            () -> {
              masterMode = !masterMode;
              pageIndex = 0;
              selection.clear();
              refresh();
            }));
    pageButtons.add(button("Apply master", this::applyMaster));
    left.add(pageButtons, BorderLayout.SOUTH);
    properties.setLayout(new GridBagLayout());
    JScrollPane inspector = new JScrollPane(properties);
    inspector.setPreferredSize(new Dimension(265, 600));
    JSplitPane editor =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(canvas), inspector);
    editor.setResizeWeight(1);
    editor.setDividerLocation(800);
    JSplitPane root = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editor);
    root.setDividerLocation(205);
    root.setResizeWeight(0);
    add(root, BorderLayout.CENTER);
    JPanel footer = DeckersSwing.panel();
    footer.setLayout(new BorderLayout());
    JPanel output = new JPanel(new FlowLayout(FlowLayout.LEFT));
    output.add(button("Sheet setup…", this::sheetSetup));
    output.add(
        button(
            "Design / sheet",
            () -> {
              sheetMode = !sheetMode;
              selection.clear();
              refresh();
            }));
    output.add(new JLabel("Sheet"));
    output.add(sheetNumber);
    output.add(button("Export PDF…", this::exportPdf));
    output.add(button("Print…", this::print));
    output.add(
        button(
            "Cancel task",
            () -> {
              if (task != null) task.cancel(true);
            }));
    output.add(metrics);
    footer.add(horizontalTools(output), BorderLayout.NORTH);
    footer.add(status, BorderLayout.SOUTH);
    add(footer, BorderLayout.SOUTH);
    pages.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && !updating && pages.getSelectedIndex() >= 0) {
            pageIndex = pages.getSelectedIndex();
            selection.clear();
            refresh();
          }
        });
    layers.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && !updating) {
            selection.clear();
            for (var o : layers.getSelectedValuesList()) selection.add(o.id);
            showProperties();
            canvas.repaint();
          }
        });
    sheetNumber.addChangeListener(
        e -> {
          previewTimer.restart();
        });
    previewTimer.setRepeats(false);
    autosave.start();
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            if (task != null && !task.isDone()) {
              message("Finish or cancel the active task before closing.");
              return;
            }
            if (discard()) {
              autosave.stop();
              previewTimer.stop();
              if (previewTask != null) previewTask.cancel(true);
              dispose();
            }
          }
        });
    bind("control Z", this::undo);
    bind("control Y", this::redo);
    bind("DELETE", this::deleteObjects);
    bind("control D", this::duplicate);
    bind("control C", this::copyObjects);
    bind("control V", this::pasteObjects);
    bind(
        "control X",
        () -> {
          copyObjects();
          deleteObjects();
        });
    bind("control S", () -> saveShared(false));
    refresh();
  }

  public static void open(Component parent) {
    PublishingWorkspace w = new PublishingWorkspace();
    w.setLocationRelativeTo(parent);
    w.setVisible(true);
  }

  public static void openPriceTags(
      Component parent, List<Map<String, String>> rows, double width, double height) {
    PublishingWorkspace w = new PublishingWorkspace();
    w.document = Publication.preset("Label");
    w.document.resize(width, height, false);
    w.document.mergeRows = new ArrayList<>(rows);
    Publication.Element code = new Publication.Element();
    code.type = "BARCODE";
    code.text = "{{code}}";
    code.x = Math.min(3, width / 10);
    code.y = height * .55;
    code.width = width - 2 * code.x;
    code.height = height * .3;
    w.document.pages.get(0).objects.add(code);
    w.dirty = true;
    w.refresh();
    w.setLocationRelativeTo(parent);
    w.setVisible(true);
  }

  public static void openOrder(Component parent, long orderId, Long lineId) {
    PublishingWorkspace w = new PublishingWorkspace();
    w.document.orderId = orderId;
    w.document.lineId = lineId;
    w.setLocationRelativeTo(parent);
    w.setVisible(true);
    w.job(
        "Loading order designs",
        () -> {
          JsonObject b = new JsonObject();
          b.addProperty("orderId", orderId);
          return LanApiClient.publicationRequest("ORDER_DESIGNS", b).getAsJsonArray("documents");
        },
        docs -> {
          List<JsonObject> choices = new ArrayList<>();
          for (var item : docs)
            if (lineId == null
                || item.getAsJsonObject().has("lineId")
                    && item.getAsJsonObject().get("lineId").getAsLong() == lineId)
              choices.add(item.getAsJsonObject());
          if (choices.isEmpty()) return;
          JsonObject chosen = choices.get(0);
          if (choices.size() > 1) {
            String[] names =
                choices.stream()
                    .map(x -> x.get("name").getAsString() + " · " + x.get("id").getAsString())
                    .toArray(String[]::new);
            Object selected =
                JOptionPane.showInputDialog(
                    w,
                    "Copy which existing design?",
                    "Order designs",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    names,
                    names[0]);
            if (selected == null) return;
            chosen = choices.get(Arrays.asList(names).indexOf(selected));
          }
          JsonObject design = chosen;
          SwingUtilities.invokeLater(
              () ->
                  w.job(
                      "Copying order artwork",
                      () -> PublicationClient.copyOrder(design, orderId),
                      result -> {
                        w.document = result.publication();
                        w.dirty = true;
                        w.refresh();
                        w.message(String.join("\n", result.warnings()));
                      }));
        });
  }

  private JMenuBar menu() {
    JMenuBar bar = new JMenuBar();
    JMenu file = new JMenu("Project"),
        edit = new JMenu("Arrange"),
        publication = new JMenu("Publication"),
        merge = new JMenu("Merge");
    bar.add(file);
    bar.add(edit);
    bar.add(publication);
    bar.add(merge);
    item(file, "New from template…", this::newProject);
    item(file, "Open shared…", this::openShared);
    item(file, "Edit shared template…", () -> openShared(true));
    item(file, "Save shared", () -> saveShared(false));
    item(file, "Save shared as new", () -> saveShared(true));
    item(file, "Open project file…", this::openFile);
    item(file, "Export project file…", this::exportProject);
    item(file, "Recover autosave…", this::recover);
    item(file, "Import PDF / Publisher…", this::importDocument);
    item(file, "Export sheet PNG…", this::exportImage);
    item(edit, "Duplicate", this::duplicate);
    item(edit, "Copy", this::copyObjects);
    item(edit, "Paste", this::pasteObjects);
    item(edit, "Delete", this::deleteObjects);
    item(edit, "Group", () -> group(true));
    item(edit, "Ungroup", () -> group(false));
    item(edit, "Lock / unlock", this::lock);
    item(edit, "Bring to front", () -> reorder(true));
    item(edit, "Send to back", () -> reorder(false));
    item(edit, "Align left", () -> arrange("LEFT"));
    item(edit, "Align top", () -> arrange("TOP"));
    item(edit, "Center horizontally", () -> arrange("CENTER"));
    item(edit, "Distribute horizontally", () -> arrange("HORIZONTAL"));
    item(edit, "Distribute vertically", () -> arrange("VERTICAL"));
    item(publication, "Page dimensions…", this::dimensions);
    item(publication, "Rename page…", this::renamePage);
    item(
        publication,
        "Include / exclude page from printing",
        () -> {
          if (!masterMode && page() != null) {
            checkpoint();
            page().reference = !page().reference;
            changed();
          }
        });
    item(publication, "Add guide…", this::addGuide);
    item(publication, "Save selection as style…", this::saveStyle);
    item(publication, "Apply style…", this::applyStyle);
    item(publication, "Link selected text frames…", this::linkText);
    item(publication, "Preflight…", this::preflight);
    item(
        publication,
        "Mark as shared template",
        () -> {
          checkpoint();
          document.template = !document.template;
          changed();
        });
    item(merge, "Import CSV…", this::importCsv);
    item(merge, "Load product rows…", this::products);
    item(merge, "Load customer rows", () -> businessRows("CUSTOMERS"));
    item(merge, "Load order rows", () -> businessRows("ORDERS"));
    item(merge, "Map fields / preview…", this::mergePreview);
    item(
        merge,
        "Clear merge",
        () -> {
          checkpoint();
          document.mergeRows.clear();
          changed();
        });
    return bar;
  }

  private JButton button(String text, Runnable action) {
    JButton b = new JButton(text);
    DeckersSwing.styleUtilityButton(b, DeckersPalette.PURPLE);
    b.addActionListener(e -> action.run());
    return b;
  }

  private JScrollPane horizontalTools(JPanel panel) {
    JScrollPane pane =
        new JScrollPane(
            panel,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    pane.setPreferredSize(new Dimension(700, panel.getPreferredSize().height + 22));
    pane.setBorder(BorderFactory.createEmptyBorder());
    return pane;
  }

  private void item(JMenu menu, String text, Runnable action) {
    JMenuItem i = new JMenuItem(text);
    i.addActionListener(e -> action.run());
    menu.add(i);
  }

  private void bind(String key, Runnable action) {
    getRootPane()
        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(key), key);
    getRootPane()
        .getActionMap()
        .put(
            key,
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                if (task != null && !task.isDone()
                    || KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
                        instanceof javax.swing.text.JTextComponent) return;
                action.run();
              }
            });
  }

  private Publication.Page page() {
    List<Publication.Page> list = masterMode ? document.masters : document.pages;
    if (list.isEmpty()) return null;
    return list.get(Math.min(pageIndex, list.size() - 1));
  }

  private List<Publication.Element> selected() {
    return page() == null
        ? List.of()
        : page().objects.stream().filter(e -> selection.contains(e.id)).toList();
  }

  private void checkpoint() {
    undo.push(document.copy());
    while (undo.size() > 80) undo.removeLast();
    redo.clear();
  }

  private void changed() {
    dirty = true;
    generation++;
    refresh();
  }

  private void undo() {
    if (undo.isEmpty()) return;
    redo.push(document.copy());
    document = undo.pop();
    selection.clear();
    changed();
  }

  private void redo() {
    if (redo.isEmpty()) return;
    undo.push(document.copy());
    document = redo.pop();
    selection.clear();
    dirty = true;
    generation++;
    refresh();
  }

  private void refresh() {
    updating = true;
    pageIndex =
        Math.max(
            0, Math.min(pageIndex, (masterMode ? document.masters : document.pages).size() - 1));
    pageModel.clear();
    for (var p : masterMode ? document.masters : document.pages) pageModel.addElement(p);
    pages.setSelectedIndex(pageIndex);
    layerModel.clear();
    if (page() != null) for (var e : page().objects) layerModel.addElement(e);
    units.setSelectedItem(document.displayUnit);
    updating = false;
    showProperties();
    canvas.revalidate();
    canvas.repaint();
    previewTimer.restart();
    setTitle(
        "SmartStock Publishing · "
            + document.title
            + (dirty ? " *" : "")
            + (document.template ? " · Template" : ""));
  }

  private void editWordArt() {
    if(page()==null || sheetMode)return;
    var chosen=selected();
    Publication.Element original=chosen.size()==1 && chosen.get(0).type.equals("TEXT")?chosen.get(0):null;
    if(original!=null && original.locked)return;
    var edited=WordArtEditor.choose(this,original);
    if(edited==null)return;
    checkpoint();
    if(original==null)page().objects.add(edited);else page().objects.set(page().objects.indexOf(original),edited);
    selection.clear();selection.add(edited.id);changed();
  }

  private void addObject(String type) {
    if (page() == null || sheetMode) return;
    checkpoint();
    var e = new Publication.Element();
    e.type = type;
    e.x = Math.min(5, document.width / 10);
    e.y = Math.min(5, document.height / 10);
    e.width = Math.min(60, document.width - 2 * e.x);
    e.height = Math.min(15, document.height - 2 * e.y);
    if (type.equals("TABLE")) e.text = "Header 1\tHeader 2\nValue 1\tValue 2";
    if (type.equals("QR") || type.equals("BARCODE")) e.text = "123456789";
    page().objects.add(e);
    selection.clear();
    selection.add(e.id);
    changed();
  }

  private void addPage() {
    checkpoint();
    Publication.Page p = new Publication.Page();
    p.name =
        (masterMode ? "Master " : "Page ")
            + ((masterMode ? document.masters : document.pages).size() + 1);
    (masterMode ? document.masters : document.pages).add(p);
    pageIndex = (masterMode ? document.masters : document.pages).size() - 1;
    selection.clear();
    changed();
  }

  private void deletePage() {
    if (page() == null || !masterMode && document.pages.size() == 1) return;
    checkpoint();
    String name = page().name;
    (masterMode ? document.masters : document.pages).remove(pageIndex);
    if (masterMode) for (var p : document.pages) if (p.master.equals(name)) p.master = "";
    selection.clear();
    changed();
  }

  private void renamePage() {
    if (page() == null) return;
    String name = JOptionPane.showInputDialog(this, "Page name", page().name);
    if (name != null && !name.isBlank()) {
      String old = page().name;
      checkpoint();
      page().name = name;
      if (masterMode) for (var p : document.pages) if (p.master.equals(old)) p.master = name;
      changed();
    }
  }

  private void applyMaster() {
    if (masterMode || page() == null) return;
    List<String> choices = new ArrayList<>();
    choices.add("None");
    document.masters.forEach(p -> choices.add(p.name));
    Object name =
        JOptionPane.showInputDialog(
            this,
            "Master page",
            "Apply master",
            JOptionPane.PLAIN_MESSAGE,
            null,
            choices.toArray(),
            choices.get(0));
    if (name != null) {
      checkpoint();
      page().master = name.equals("None") ? "" : name.toString();
      changed();
    }
  }

  private void deleteObjects() {
    if (selected().isEmpty()) return;
    checkpoint();
    page().objects.removeIf(e -> selection.contains(e.id) && !e.locked);
    selection.clear();
    changed();
  }

  private void duplicate() {
    if (selected().isEmpty()) return;
    checkpoint();
    List<Publication.Element> copies = new ArrayList<>();
    for (var e : selected()) {
      var copy = Publication.JSON.fromJson(Publication.JSON.toJson(e), Publication.Element.class);
      copy.id = UUID.randomUUID().toString();
      copy.group = "";
      copy.x += 2;
      copy.y += 2;
      copies.add(copy);
    }
    selection.clear();
    for (var e : copies) {
      page().objects.add(e);
      selection.add(e.id);
    }
    changed();
  }

  private void copyObjects() {
    clipboard =
        selected().stream()
            .map(
                e ->
                    Publication.JSON.fromJson(
                        Publication.JSON.toJson(e), Publication.Element.class))
            .toList();
  }

  private void pasteObjects() {
    if (clipboard.isEmpty() || page() == null || sheetMode) return;
    checkpoint();
    selection.clear();
    Map<String, String> groups = new HashMap<>();
    for (var original : clipboard) {
      var e =
          Publication.JSON.fromJson(Publication.JSON.toJson(original), Publication.Element.class);
      e.id = UUID.randomUUID().toString();
      e.x += 2;
      e.y += 2;
      e.locked = false;
      if (!e.group.isBlank())
        e.group = groups.computeIfAbsent(e.group, k -> UUID.randomUUID().toString());
      page().objects.add(e);
      selection.add(e.id);
    }
    changed();
  }

  private void group(boolean group) {
    if (selected().isEmpty()) return;
    checkpoint();
    String id = group ? UUID.randomUUID().toString() : "";
    selected().stream().filter(e -> !e.locked).forEach(e -> e.group = id);
    changed();
  }

  private void lock() {
    if (selected().isEmpty()) return;
    checkpoint();
    selected().forEach(e -> e.locked = !e.locked);
    changed();
  }

  private void reorder(boolean front) {
    List<Publication.Element> items = selected().stream().filter(e -> !e.locked).toList();
    if (items.isEmpty()) return;
    checkpoint();
    page().objects.removeAll(items);
    page().objects.addAll(front ? page().objects.size() : 0, items);
    changed();
  }

  private void arrange(String mode) {
    List<Publication.Element> items = selected().stream().filter(e -> !e.locked).toList();
    if (items.size() < 2) return;
    checkpoint();
    double minX = items.stream().mapToDouble(e -> e.x).min().orElse(0),
        minY = items.stream().mapToDouble(e -> e.y).min().orElse(0),
        maxX = items.stream().mapToDouble(e -> e.x + e.width).max().orElse(0),
        maxY = items.stream().mapToDouble(e -> e.y + e.height).max().orElse(0);
    if (mode.equals("HORIZONTAL") || mode.equals("VERTICAL")) {
      boolean horizontal = mode.equals("HORIZONTAL");
      List<Publication.Element> ordered = new ArrayList<>(items);
      ordered.sort(Comparator.comparingDouble(e -> horizontal ? e.x : e.y));
      double size = ordered.stream().mapToDouble(e -> horizontal ? e.width : e.height).sum(),
          gap = ((horizontal ? maxX - minX : maxY - minY) - size) / (ordered.size() - 1),
          position = horizontal ? minX : minY;
      for (var e : ordered) {
        if (horizontal) e.x = position;
        else e.y = position;
        position += (horizontal ? e.width : e.height) + gap;
      }
    } else
      for (var e : items)
        switch (mode) {
          case "LEFT" -> e.x = minX;
          case "TOP" -> e.y = minY;
          case "CENTER" -> e.x = (minX + maxX - e.width) / 2;
        }
    changed();
  }

  private void dimensions() {
    JTextField w = new JTextField(format(document.width)),
        h = new JTextField(format(document.height));
    JComboBox<String> behavior =
        new JComboBox<>(new String[] {"Preserve object sizes", "Scale contents"});
    if (form(
        "Publication size (" + document.displayUnit + ")",
        new String[] {"Width", "Height", "Resize behavior"},
        new JComponent[] {w, h, behavior})) {
      try {
        double width = parse(w), height = parse(h);
        checkpoint();
        document.resize(width, height, behavior.getSelectedIndex() == 1);
        changed();
      } catch (Exception ex) {
        error(ex);
      }
    }
  }

  private void addGuide() {
    if (page() == null) return;
    JTextField position = new JTextField("10");
    JComboBox<String> orientation = new JComboBox<>(new String[] {"Vertical", "Horizontal"});
    if (form(
        "Add guide (" + document.displayUnit + ")",
        new String[] {"Position", "Direction"},
        new JComponent[] {position, orientation})) {
      try {
        double value = parse(position);
        checkpoint();
        (orientation.getSelectedIndex() == 0 ? page().verticalGuides : page().horizontalGuides)
            .add(value);
        changed();
      } catch (Exception ex) {
        error(ex);
      }
    }
  }

  private void saveStyle() {
    if (selected().size() != 1) return;
    String name = JOptionPane.showInputDialog(this, "Style name");
    if (name != null && !name.isBlank()) {
      checkpoint();
      document.styles.put(
          name,
          Publication.JSON.fromJson(
              Publication.JSON.toJson(selected().get(0)), Publication.Element.class));
      changed();
    }
  }

  private void applyStyle() {
    if (document.styles.isEmpty() || selected().isEmpty()) return;
    Object choice =
        JOptionPane.showInputDialog(
            this,
            "Style",
            "Apply style",
            JOptionPane.PLAIN_MESSAGE,
            null,
            document.styles.keySet().toArray(),
            document.styles.keySet().iterator().next());
    if (choice == null) return;
    checkpoint();
    var s = document.styles.get(choice.toString());
    selected().stream()
        .filter(e -> !e.locked)
        .forEach(
            e -> {
              e.font = s.font;
              e.fontSize = s.fontSize;
              e.bold = s.bold;
              e.italic = s.italic;
              e.color = s.color;
              e.fill = s.fill;
              e.stroke = s.stroke;
              e.strokeWidth = s.strokeWidth;
            });
    changed();
  }

  private void linkText() {
    List<Publication.Element> frames =
        selected().stream().filter(e -> e.type.equals("TEXT") && !e.locked).toList();
    if (frames.isEmpty()) return;
    String existing =
        JOptionPane.showInputDialog(
            this, "Story name (reuse the name to link across pages)", "Story 1");
    if (existing == null || existing.isBlank()) return;
    checkpoint();
    document.stories.putIfAbsent(existing, frames.get(0).text);
    frames.forEach(e -> e.story = existing);
    changed();
  }

  private String format(double mm) {
    return String.format(Locale.ROOT, "%.3f", Publication.fromMm(mm, document.displayUnit));
  }

  private double parse(JTextField f) {
    return Publication.toMm(Double.parseDouble(f.getText().trim()), document.displayUnit);
  }

  private void showProperties() {
    properties.removeAll();
    List<Publication.Element> chosen = selected();
    if (chosen.size() != 1) {
      addProperty("Selection", new JLabel(chosen.size() + " objects"), 0);
      addProperty("Units", new JLabel(document.displayUnit), 1);
      propertySpacer();
      properties.revalidate();
      properties.repaint();
      return;
    }
    var e = chosen.get(0);
    int r = 0;
    JTextField x = new JTextField(format(e.x)),
        y = new JTextField(format(e.y)),
        w = new JTextField(format(e.width)),
        h = new JTextField(format(e.height)),
        rotation = new JTextField(String.valueOf(e.rotation)),
        font = new JTextField(e.font),
        size = new JTextField(String.valueOf(e.fontSize)),
        color = new JTextField(e.color),
        fill = new JTextField(e.fill),
        stroke = new JTextField(e.stroke),
        sw = new JTextField(String.valueOf(e.strokeWidth)),
        columns = new JTextField(String.valueOf(e.columns)),
        tableCols = new JTextField(String.valueOf(e.tableColumns));
    JTextArea text =
        new JTextArea(
            e.story.isBlank() ? e.text : document.stories.getOrDefault(e.story, ""), 5, 16);
    text.setLineWrap(true);
    JCheckBox bold = new JCheckBox("Bold", e.bold),
        italic = new JCheckBox("Italic", e.italic),
        locked = new JCheckBox("Locked", e.locked);
    for (var pair :
        new Object[][] {
          {"X", x},
          {"Y", y},
          {"Width", w},
          {"Height", h},
          {"Rotation °", rotation},
          {"Text / merge fields", new JScrollPane(text)},
          {"Font", font},
          {"Size (pt)", size},
          {"Text color", color},
          {"Fill", fill},
          {"Stroke", stroke},
          {"Stroke mm", sw},
          {"Text columns", columns},
          {"Table columns", tableCols},
          {"", bold},
          {"", italic},
          {"", locked}
        }) addProperty((String) pair[0], (JComponent) pair[1], r++);
    JTextField crop =
        new JTextField(e.cropX + "," + e.cropY + "," + e.cropWidth + "," + e.cropHeight);
    if (e.type.equals("IMAGE")) addProperty("Crop x,y,w,h (0–1)", crop, r++);
    JButton apply =
        button(
            "Apply properties",
            () -> {
              try {
                var candidate =
                    Publication.JSON.fromJson(
                        Publication.JSON.toJson(e), Publication.Element.class);
                candidate.x = parse(x);
                candidate.y = parse(y);
                candidate.width = parse(w);
                candidate.height = parse(h);
                candidate.rotation = Double.parseDouble(rotation.getText());
                candidate.font = font.getText();
                candidate.fontSize = Double.parseDouble(size.getText());
                candidate.color = color.getText();
                candidate.fill = fill.getText();
                candidate.stroke = stroke.getText();
                candidate.strokeWidth = Double.parseDouble(sw.getText());
                candidate.columns = Integer.parseInt(columns.getText());
                candidate.tableColumns = Integer.parseInt(tableCols.getText());
                candidate.bold = bold.isSelected();
                candidate.italic = italic.isSelected();
                candidate.locked = locked.isSelected();
                if (e.type.equals("IMAGE")) {
                  String[] values = crop.getText().split(",");
                  if (values.length != 4)
                    throw new IllegalArgumentException("Crop needs four values.");
                  candidate.cropX = Double.parseDouble(values[0]);
                  candidate.cropY = Double.parseDouble(values[1]);
                  candidate.cropWidth = Double.parseDouble(values[2]);
                  candidate.cropHeight = Double.parseDouble(values[3]);
                }
                Publication copy = document.copy();
                copy.allPages()
                    .forEach(
                        p -> {
                          for (int i = 0; i < p.objects.size(); i++)
                            if (p.objects.get(i).id.equals(e.id)) p.objects.set(i, candidate);
                        });
                if (e.story.isBlank()) candidate.text = text.getText();
                else copy.stories.put(e.story, text.getText());
                copy.validate();
                checkpoint();
                document = copy;
                changed();
              } catch (Exception ex) {
                error(ex);
              }
            });
    apply.setEnabled(!e.locked);
    addProperty("", apply, r++);
    addProperty(
        "",
        button(
            "Unlock",
            () -> {
              checkpoint();
              e.locked = false;
              changed();
            }),
        r++);
    addProperty("", new JLabel("Table cells: tabs; rows: new lines"), r);
    propertySpacer();
    properties.revalidate();
    properties.repaint();
  }

  private void propertySpacer() {
    GridBagConstraints c = new GridBagConstraints();
    c.gridy = 999;
    c.gridx = 0;
    c.gridwidth = 2;
    c.weighty = 1;
    c.fill = GridBagConstraints.VERTICAL;
    properties.add(Box.createVerticalGlue(), c);
  }

  private void addProperty(String name, JComponent value, int row) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(3, 5, 3, 5);
    properties.add(new JLabel(name), c);
    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    properties.add(value, c);
  }

  private boolean form(String title, String[] labels, JComponent[] fields) {
    JPanel panel = new JPanel(new GridLayout(0, 2, 8, 5));
    for (int i = 0; i < labels.length; i++) {
      panel.add(new JLabel(labels[i]));
      panel.add(fields[i]);
    }
    return JOptionPane.showConfirmDialog(
            this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION;
  }

  private void sheetSetup() {
    var s =
        Publication.JSON.fromJson(
            Publication.JSON.toJson(document.print), SheetLayout.Settings.class);
    String[] names = {
      "Sheet width",
      "Sheet height",
      "Left margin",
      "Right margin",
      "Top margin",
      "Bottom margin",
      "Horizontal gap",
      "Vertical gap",
      "Bleed",
      "X calibration",
      "Y calibration",
      "Back X correction",
      "Back Y correction",
      "Quantity (0 = fill)",
      "Skip starting positions"
    };
    double[] values = {
      s.width,
      s.height,
      s.left,
      s.right,
      s.top,
      s.bottom,
      s.gapX,
      s.gapY,
      s.bleed,
      s.offsetX,
      s.offsetY,
      s.backOffsetX,
      s.backOffsetY,
      s.quantity,
      s.skip
    };
    JTextField[] fields = new JTextField[names.length];
    JPanel panel = new JPanel(new GridLayout(0, 2, 8, 4));
    JComboBox<String> presets =
        new JComboBox<>(
            new String[] {
              "Custom", "A4 portrait", "A4 landscape", "Letter portrait", "Letter landscape"
            });
    panel.add(new JLabel("Preset"));
    panel.add(presets);
    for (int i = 0; i < names.length; i++) {
      fields[i] = new JTextField(i < 13 ? format(values[i]) : String.valueOf((int) values[i]));
      panel.add(new JLabel(names[i] + (i < 13 ? " (" + document.displayUnit + ")" : "")));
      panel.add(fields[i]);
    }
    presets.addActionListener(
        e -> {
          double[][] sizes = {
            {s.width, s.height}, {210, 297}, {297, 210}, {215.9, 279.4}, {279.4, 215.9}
          };
          fields[0].setText(format(sizes[presets.getSelectedIndex()][0]));
          fields[1].setText(format(sizes[presets.getSelectedIndex()][1]));
        });
    JCheckBox maximize = new JCheckBox("Maximize copies", s.maximize),
        rotate = new JCheckBox("Allow rotated copies", s.rotate),
        marks = new JCheckBox("Crop marks", s.cropMarks),
        duplex = new JCheckBox("Duplex front / back page pairs", s.duplex),
        booklet = new JCheckBox("Booklet imposition", s.booklet);
    for (var check : List.of(maximize, rotate, marks, duplex, booklet)) {
      panel.add(check);
      panel.add(new JLabel());
    }
    JComboBox<String> alignment = new JComboBox<>(new String[] {"CENTER", "TOP_LEFT"}),
        mapping = new JComboBox<>(new String[] {"LONG_EDGE", "SHORT_EDGE", "SAME"});
    alignment.setSelectedItem(s.alignment);
    mapping.setSelectedItem(s.backMapping);
    panel.add(new JLabel("Alignment"));
    panel.add(alignment);
    panel.add(new JLabel("Back mapping"));
    panel.add(mapping);
    if (JOptionPane.showConfirmDialog(
            this, new JScrollPane(panel), "Sheet setup", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION)
      try {
        double[] n = new double[13];
        for (int i = 0; i < 13; i++) n[i] = parse(fields[i]);
        s.width = n[0];
        s.height = n[1];
        s.left = n[2];
        s.right = n[3];
        s.top = n[4];
        s.bottom = n[5];
        s.gapX = n[6];
        s.gapY = n[7];
        s.bleed = n[8];
        s.offsetX = n[9];
        s.offsetY = n[10];
        s.backOffsetX = n[11];
        s.backOffsetY = n[12];
        s.quantity = Integer.parseInt(fields[13].getText());
        s.skip = Integer.parseInt(fields[14].getText());
        s.maximize = maximize.isSelected();
        s.rotate = rotate.isSelected();
        s.cropMarks = marks.isSelected();
        s.duplex = duplex.isSelected();
        s.booklet = booklet.isSelected();
        s.alignment = alignment.getSelectedItem().toString();
        s.backMapping = mapping.getSelectedItem().toString();
        s.validate();
        checkpoint();
        document.print = s;
        sheetMode = true;
        changed();
      } catch (Exception ex) {
        error(ex);
      }
  }

  private void renderPreview() {
    if (previewTask != null) previewTask.cancel(true);
    Publication copy = document.copy();
    int index = (int) sheetNumber.getValue() - 1, current = generation, designIndex = pageIndex;
    boolean sheetView = sheetMode, masterView = masterMode;
    previewTask =
        new SwingWorker<Object[], Void>() {
          protected Object[] doInBackground() {
            PublicationRenderer renderer = new PublicationRenderer(copy);
            var result = SheetLayout.pack(copy.width, copy.height, copy.print);
            int count;
            String layoutError = "";
            try {
              count = renderer.sheets();
            } catch (IllegalArgumentException ex) {
              count = 0;
              layoutError = ex.getMessage();
            }
            BufferedImage image;
            if (sheetView)
              image = count > 0 ? renderer.raster(Math.min(index, count - 1), 90) : null;
            else {
              if (masterView && !copy.masters.isEmpty())
                copy.pages =
                    new ArrayList<>(
                        List.of(copy.masters.get(Math.min(designIndex, copy.masters.size() - 1))));
              int w = (int) Math.ceil(copy.width * 150 / 25.4),
                  h = (int) Math.ceil(copy.height * 150 / 25.4);
              if ((long) w * h > 50000000)
                throw new IllegalArgumentException("Page is too large for editor preview.");
              image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
              Graphics2D g = image.createGraphics();
              g.scale(150 / 25.4, 150 / 25.4);
              renderer.page(
                  g,
                  masterView ? 0 : Math.min(designIndex, copy.pages.size() - 1),
                  copy.mergeRows.isEmpty() ? Map.of() : copy.mergeRows.get(0));
              g.dispose();
            }
            return new Object[] {image, result, count, layoutError};
          }

          protected void done() {
            if (isCancelled()
                || current != generation
                || designIndex != pageIndex
                || sheetView != sheetMode
                || masterView != masterMode) return;
            try {
              Object[] result = get();
              renderedSheets = (int) result[2];
              renderedGeneration = current;
              if (sheetView) sheetPreview = (BufferedImage) result[0];
              else designPreview = (BufferedImage) result[0];
              var layout = (SheetLayout.Result) result[1];
              metrics.setText(
                  result[3].toString().isBlank()
                      ? layout.capacity()
                          + " / sheet · "
                          + result[2]
                          + " sheets · "
                          + String.format(
                              Locale.ROOT,
                              "%.1f%% occupied",
                              100 * layout.usedArea() / layout.printableArea())
                      : result[3].toString());
              canvas.repaint();
            } catch (Exception ex) {
              metrics.setText("Layout: " + rootMessage(ex));
            }
          }
        };
    previewTask.execute();
  }

  private <T> void job(String label, Callable<T> work, Consumer<T> done) {
    if (task != null && !task.isDone()) {
      message("Another task is running; finish or cancel it first.");
      return;
    }
    boolean block = !label.startsWith("Autosaving");
    JPanel shield = new JPanel(new GridBagLayout());
    shield.setBackground(new Color(240, 240, 245, 230));
    JPanel progressPanel = new JPanel(new GridLayout(0, 1, 10, 10));
    progressPanel.setBorder(new EmptyBorder(20, 30, 20, 30));
    progressPanel.add(new JLabel(label + "…"));
    JProgressBar progress = new JProgressBar();
    progress.setIndeterminate(true);
    progressPanel.add(progress);
    JButton cancel =
        button(
            "Cancel",
            () -> {
              if (task != null) task.cancel(true);
            });
    progressPanel.add(cancel);
    shield.add(progressPanel);
    shield.addMouseListener(new MouseAdapter() {});
    shield.addMouseMotionListener(new MouseMotionAdapter() {});
    status.setText(label + "…");
    task =
        new SwingWorker<T, Void>() {
          protected T doInBackground() throws Exception {
            return work.call();
          }

          protected void done() {
            try {
              if (isCancelled()) {
                status.setText("Cancelled");
                return;
              }
              done.accept(get());
              status.setText(label + " complete");
            } catch (Exception ex) {
              status.setText(label + " failed");
              error(ex);
            } finally {
              if (block) shield.setVisible(false);
            }
          }
        };
    if (block) {
      getRootPane().setGlassPane(shield);
      shield.setVisible(true);
      cancel.requestFocusInWindow();
    }
    task.execute();
  }

  private void autosave() {
    if (!dirty || task != null && !task.isDone()) return;
    Publication copy = document.copy();
    job(
        "Autosaving recovery copy",
        () -> {
          PublicationFiles.writeAtomic(recovery, PublicationFiles.archive(copy));
          return true;
        },
        ignored -> {});
  }

  private boolean discard() {
    return !dirty
        || JOptionPane.showConfirmDialog(
                this,
                "Continue without saving current changes? A completed autosave remains"
                    + " recoverable.",
                "Unsaved publication",
                JOptionPane.YES_NO_OPTION)
            == JOptionPane.YES_OPTION;
  }

  private Path choose(boolean save, String description, String... extensions) {
    JFileChooser picker = new JFileChooser();
    picker.setFileFilter(new FileNameExtensionFilter(description, extensions));
    if ((save ? picker.showSaveDialog(this) : picker.showOpenDialog(this))
        != JFileChooser.APPROVE_OPTION) return null;
    Path file = picker.getSelectedFile().toPath();
    if (save
        && !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extensions[0]))
      file = file.resolveSibling(file.getFileName() + "." + extensions[0]);
    if (save
        && Files.exists(file)
        && JOptionPane.showConfirmDialog(
                this, "Replace " + file.getFileName() + "?", "Export", JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) return null;
    return file;
  }

  private void replace(Publication p) {
    clipboard = List.of();
    document = p;
    projectId = UUID.randomUUID();
    revision = 0;
    pageIndex = 0;
    masterMode = false;
    selection.clear();
    undo.clear();
    redo.clear();
    dirty = false;
    generation++;
    refresh();
  }

  private void newProject() {
    if (!discard()) return;
    Object preset =
        JOptionPane.showInputDialog(
            this,
            "Template",
            "New publication",
            JOptionPane.PLAIN_MESSAGE,
            null,
            new String[] {"Label", "Card", "Flyer", "Booklet"},
            "Card");
    if (preset != null) replace(Publication.preset(preset.toString()));
  }

  private void saveShared(boolean asNew) {
    String title = JOptionPane.showInputDialog(this, "Project title", document.title);
    if (title == null || title.isBlank()) return;
    document.title = title;
    Publication copy = document.copy();
    UUID id = asNew ? UUID.randomUUID() : projectId;
    int expected = asNew ? 0 : revision, captured = generation;
    job(
        "Saving shared project",
        () -> PublicationClient.save(copy, id, expected),
        saved -> {
          projectId = saved.id();
          revision = saved.revision();
          if (captured == generation) dirty = false;
          refresh();
        });
  }

  private void openShared() {
    openShared(false);
  }

  private void openShared(boolean editTemplates) {
    if (!discard()) return;
    job(
        "Loading shared projects",
        () -> LanApiClient.publicationRequest("LIST", new JsonObject()).getAsJsonArray("result"),
        rows -> {
          if (editTemplates) {
            JsonArray filtered = new JsonArray();
            for (var row : rows)
              if (row.getAsJsonObject().get("template").getAsBoolean()) filtered.add(row);
            rows = filtered;
          }
          if (rows.isEmpty()) {
            message("No shared projects yet.");
            return;
          }
          String[] names = new String[rows.size()];
          for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i).getAsJsonObject();
            names[i] =
                r.get("title").getAsString()
                    + " · "
                    + r.get("id").getAsString()
                    + (r.get("template").getAsBoolean() ? " · Template" : "");
          }
          Object choice =
              JOptionPane.showInputDialog(
                  this, "Project", "Open shared", JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
          if (choice != null) {
            var row = rows.get(Arrays.asList(names).indexOf(choice)).getAsJsonObject();
            UUID id = UUID.fromString(row.get("id").getAsString());
            SwingUtilities.invokeLater(
                () ->
                    job(
                        "Opening shared project",
                        () -> PublicationClient.load(id),
                        loaded -> {
                          replace(loaded.publication());
                          if (document.template && !editTemplates) {
                            document.template = false;
                            dirty = true;
                          } else {
                            projectId = id;
                            revision = loaded.revision();
                          }
                          refresh();
                        }));
          }
        });
  }

  private void openFile() {
    if (!discard()) return;
    Path file = choose(false, "SmartStock publication", "sspub");
    if (file != null)
      job(
          "Opening publication",
          () -> {
            if (Files.size(file) > PublicationFiles.MAX_BYTES)
              throw new java.io.IOException("Project exceeds 48 MiB.");
            return PublicationFiles.read(Files.readAllBytes(file));
          },
          this::replace);
  }

  private void exportProject() {
    Path file = choose(true, "SmartStock publication", "sspub");
    if (file != null) {
      Publication copy = document.copy();
      job(
          "Exporting project",
          () -> {
            PublicationFiles.writeAtomic(file, PublicationFiles.archive(copy));
            return file;
          },
          p -> message("Saved " + p));
    }
  }

  private void recover() {
    if (!discard()) return;
    JFileChooser chooser = new JFileChooser(recovery.getParent().toFile());
    chooser.setFileFilter(new FileNameExtensionFilter("Recovery publication", "sspub"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      Path file = chooser.getSelectedFile().toPath();
      job(
          "Recovering publication",
          () -> {
            if (Files.size(file) > PublicationFiles.MAX_BYTES)
              throw new java.io.IOException("Recovery exceeds size limit.");
            return PublicationFiles.read(Files.readAllBytes(file));
          },
          p -> {
            replace(p);
            dirty = true;
            refresh();
          });
    }
  }

  private void importImage() {
    if (page() == null) return;
    Path file = choose(false, "Images", "png", "jpg", "jpeg", "bmp", "gif");
    if (file != null) {
      Publication copy = document.copy();
      int index = pageIndex;
      boolean master = masterMode;
      job(
          "Importing image",
          () -> {
            PublicationImport.image(copy, file, (master ? copy.masters : copy.pages).get(index));
            return copy;
          },
          p -> {
            checkpoint();
            document = p;
            changed();
          });
    }
  }

  private void importDocument() {
    if (!discard()) return;
    Path file = choose(false, "PDF or Publisher", "pdf", "pub");
    if (file == null) return;
    Path exe = null;
    if (file.toString().toLowerCase(Locale.ROOT).endsWith(".pub")) {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Select LibreOffice soffice executable");
      if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
      exe = chooser.getSelectedFile().toPath();
    }
    Path executable = exe;
    job(
        "Importing document",
        () ->
            executable == null
                ? new PublicationImport.Result(
                    PublicationImport.pdf(file), List.of("PDF pages are imported as artwork."))
                : PublicationImport.publisher(file, executable),
        result -> {
          message(String.join("\n", result.warnings()));
          replace(result.publication());
          dirty = true;
          refresh();
        });
  }

  private void importCsv() {
    Path file = choose(false, "CSV merge rows", "csv");
    if (file != null)
      job(
          "Importing merge rows",
          () -> {
            if (Files.size(file) > 5000000) throw new java.io.IOException("CSV exceeds 5 MB.");
            return PublicationFiles.csv(Files.readString(file));
          },
          rows -> {
            checkpoint();
            document.mergeRows = rows;
            changed();
            mergePreview();
          });
  }

  private void products() {
    String query =
        JOptionPane.showInputDialog(this, "Find products (blank lists available products)", "");
    if (query == null) return;
    job(
        "Loading products",
        () -> LanApiClient.searchPriceTagItems(query),
        items -> {
          List<Map<String, String>> rows = new ArrayList<>();
          for (var item : items)
            rows.add(
                new LinkedHashMap<>(
                    Map.of(
                        "name",
                        item.name(),
                        "code",
                        item.code(),
                        "price",
                        String.valueOf(item.price()),
                        "quantity",
                        "1")));
          checkpoint();
          document.mergeRows = rows;
          changed();
          mergePreview();
        });
  }

  private void businessRows(String source) {
    job(
        "Loading " + source.toLowerCase(Locale.ROOT),
        () -> LanApiClient.publicationRequest(source, new JsonObject()).getAsJsonArray("rows"),
        rows -> {
          List<Map<String, String>> data = new ArrayList<>();
          for (var item : rows) {
            Map<String, String> row = new LinkedHashMap<>();
            item.getAsJsonObject()
                .entrySet()
                .forEach(e -> row.put(e.getKey(), e.getValue().getAsString()));
            data.add(row);
          }
          checkpoint();
          document.mergeRows = data;
          changed();
          mergePreview();
        });
  }

  private void mergePreview() {
    if (document.mergeRows.isEmpty()) {
      message("No merge rows loaded.");
      return;
    }
    Map<String, String> row = document.mergeRows.get(0);
    String[] headers = row.keySet().toArray(String[]::new);
    Object[][] data =
        document.mergeRows.stream()
            .limit(100)
            .map(r -> Arrays.stream(headers).map(r::get).toArray())
            .toArray(Object[][]::new);
    JTable table = new JTable(data, headers);
    table.setEnabled(false);
    DeckersSwing.styleTable(table);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JScrollPane(table));
    JPanel mapping = new JPanel(new GridLayout(0, 2, 8, 4));
    JTextField target = new JTextField("name");
    JComboBox<String> source = new JComboBox<>(headers);
    mapping.add(new JLabel("Template field (without braces)"));
    mapping.add(target);
    mapping.add(new JLabel("Source column"));
    mapping.add(source);
    panel.add(mapping, BorderLayout.SOUTH);
    if (JOptionPane.showConfirmDialog(
            this,
            panel,
            document.mergeRows.size() + " rows · first 100 shown · quantity controls copies",
            JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION) {
      String name = target.getText().trim();
      if (!name.matches("[A-Za-z_][A-Za-z0-9_ ]{0,99}")) {
        message("Use a valid field name.");
        return;
      }
      checkpoint();
      for (var r : document.mergeRows) r.put(name, r.get(source.getSelectedItem().toString()));
      changed();
    }
  }

  private int[] range() {
    if (renderedGeneration != generation || renderedSheets < 1) {
      message("Wait for a valid sheet preview before choosing an output range.");
      return null;
    }
    JTextField first = new JTextField("1"), last = new JTextField(String.valueOf(renderedSheets));
    if (!form(
        "Sheet range", new String[] {"First sheet", "Last sheet"}, new JComponent[] {first, last}))
      return null;
    try {
      int a = Integer.parseInt(first.getText()) - 1, b = Integer.parseInt(last.getText()) - 1;
      int count = renderedSheets;
      if (a < 0 || b < a || b >= count) throw new IllegalArgumentException("Invalid sheet range.");
      return new int[] {a, b};
    } catch (Exception ex) {
      error(ex);
      return null;
    }
  }

  private void exportPdf() {
    int[] range = range();
    if (range == null) return;
    Path file = choose(true, "PDF", "pdf");
    if (file != null) {
      Publication copy = document.copy();
      job(
          "Exporting PDF",
          () -> {
            PublicationFiles.pdf(copy, file, range[0], range[1]);
            return file;
          },
          p -> message("Saved " + p));
    }
  }

  private void exportImage() {
    Path file = choose(true, "PNG sheet", "png");
    if (file == null) return;
    Publication copy = document.copy();
    int index = (int) sheetNumber.getValue() - 1;
    job(
        "Exporting PNG",
        () -> {
          PublicationPreflight.requireMergeData(copy);
          PublicationRenderer r = new PublicationRenderer(copy);
          if (index >= r.sheets())
            throw new IllegalArgumentException("Selected sheet is out of range.");
          javax.imageio.ImageIO.write(r.raster(index, 300), "png", file.toFile());
          return file;
        },
        p -> message("Saved " + p));
  }

  private void preflight() {
    Publication copy = document.copy();
    job(
        "Checking publication",
        () -> {
          copy.validate();
          PublicationRenderer r = new PublicationRenderer(copy);
          BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
          Graphics2D g = image.createGraphics();
          for (int i = 0; i < copy.pages.size(); i++)
            r.page(g, i, copy.mergeRows.isEmpty() ? Map.of() : copy.mergeRows.get(0));
          g.dispose();
          List<String> warnings = new ArrayList<>(r.warnings());
          warnings.addAll(PublicationPreflight.missingMergeFields(copy));
          return warnings;
        },
        warnings ->
            message(
                warnings.isEmpty()
                    ? "No issues found."
                    : String.join("\n", new LinkedHashSet<>(warnings))));
  }

  private void print() {
    int[] range = range();
    if (range == null) return;
    Publication copy = document.copy();
    PrinterJob printer = PrinterJob.getPrinterJob();
    if (!printer.printDialog()) return;
    PageFormat format = printer.defaultPage();
    Paper paper = new Paper();
    boolean landscape = copy.print.width > copy.print.height;
    paper.setSize(
        (landscape ? copy.print.height : copy.print.width) * 72 / 25.4,
        (landscape ? copy.print.width : copy.print.height) * 72 / 25.4);
    paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
    format.setPaper(paper);
    format.setOrientation(landscape ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
    PageFormat validated = printer.validatePage(format);
    double scale = 25.4 / 72;
    var s = copy.print;
    double l = validated.getImageableX() * scale,
        t = validated.getImageableY() * scale,
        r = copy.print.width - (validated.getImageableX() + validated.getImageableWidth()) * scale,
        b =
            copy.print.height
                - (validated.getImageableY() + validated.getImageableHeight()) * scale;
    if (Math.abs(validated.getWidth() * scale - s.width) > .5
        || Math.abs(validated.getHeight() * scale - s.height) > .5) {
      message("Printer does not support the chosen sheet size. Choose a supported sheet in setup.");
      return;
    }
    if (s.left + s.offsetX < l - .01
        || s.top + s.offsetY < t - .01
        || s.right - s.offsetX < r - .01
        || s.bottom - s.offsetY < b - .01
        || (s.duplex || s.booklet)
            && (s.left + s.offsetX + s.backOffsetX < l - .01
                || s.right - s.offsetX - s.backOffsetX < r - .01
                || s.top + s.offsetY + s.backOffsetY < t - .01
                || s.bottom - s.offsetY - s.backOffsetY < b - .01)) {
      message(
          "Sheet margins or calibration exceed this printer's printable area. Adjust sheet setup;"
              + " borderless requires printer support.");
      return;
    }
    job(
        "Printing actual size",
        () -> {
          PublicationPreflight.requireMergeData(copy);
          PublicationRenderer renderer = new PublicationRenderer(copy);
          printer.setPrintable(
              (graphics, pf, index) -> {
                if (index > range[1] - range[0]) return Printable.NO_SUCH_PAGE;
                Graphics2D g = (Graphics2D) graphics.create();
                g.scale(72 / 25.4, 72 / 25.4);
                renderer.sheet(g, index + range[0]);
                g.dispose();
                return Printable.PAGE_EXISTS;
              },
              validated);
          javax.print.attribute.HashPrintRequestAttributeSet attrs =
              new javax.print.attribute.HashPrintRequestAttributeSet();
          attrs.add(
              s.duplex || s.booklet
                  ? (s.backMapping.equals("SHORT_EDGE")
                      ? javax.print.attribute.standard.Sides.TUMBLE
                      : javax.print.attribute.standard.Sides.DUPLEX)
                  : javax.print.attribute.standard.Sides.ONE_SIDED);
          printer.print(attrs);
          return renderer.warnings();
        },
        warnings -> {
          if (!warnings.isEmpty()) message(String.join("\n", warnings));
        });
  }

  private String rootMessage(Throwable ex) {
    while (ex.getCause() != null) ex = ex.getCause();
    return ex.getMessage() == null ? ex.toString() : ex.getMessage();
  }

  private void error(Throwable ex) {
    JOptionPane.showMessageDialog(this, rootMessage(ex), "Publishing", JOptionPane.ERROR_MESSAGE);
  }

  private void message(String text) {
    JTextArea area = new JTextArea(text, Math.min(18, Math.max(3, text.split("\n").length)), 65);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    JOptionPane.showMessageDialog(
        this, new JScrollPane(area), "Publishing", JOptionPane.INFORMATION_MESSAGE);
  }

  private final class Canvas extends JPanel {
    private Point2D start;
    private Map<String, Point2D> originals;
    private boolean moved;

    Canvas() {
      setFocusable(true);
      setToolTipText("Drag to move. Arrow keys: 0.1 mm; Shift + arrow: 1 mm.");
      bindMove("LEFT", -.1, 0);
      bindMove("RIGHT", .1, 0);
      bindMove("UP", 0, -.1);
      bindMove("DOWN", 0, .1);
      bindMove("shift LEFT", -1, 0);
      bindMove("shift RIGHT", 1, 0);
      bindMove("shift UP", 0, -1);
      bindMove("shift DOWN", 0, 1);
      setBackground(DeckersPalette.background());
      MouseAdapter mouse =
          new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
              if (sheetMode || page() == null) return;
              double x = (event.getX() - 30) / zoom, y = (event.getY() - 30) / zoom;
              Publication.Element hit = null;
              List<Publication.Element> objects = page().objects;
              for (int i = objects.size() - 1; i >= 0; i--) {
                var e = objects.get(i);
                AffineTransform transform = new AffineTransform();
                transform.translate(e.x, e.y);
                transform.rotate(Math.toRadians(e.rotation), e.width / 2, e.height / 2);
                if (transform
                    .createTransformedShape(new Rectangle2D.Double(0, 0, e.width, e.height))
                    .contains(x, y)) {
                  hit = e;
                  break;
                }
              }
              if (!event.isShiftDown() && (hit == null || !selection.contains(hit.id)))
                selection.clear();
              if (hit != null) {
                selection.add(hit.id);
                if (!hit.group.isBlank())
                  for (var e : objects) if (e.group.equals(hit.group)) selection.add(e.id);
              }
              start = new Point2D.Double(x, y);
              originals = new HashMap<>();
              for (var e : selected())
                if (!e.locked) originals.put(e.id, new Point2D.Double(e.x, e.y));
              moved = false;
              showProperties();
              requestFocusInWindow();
              repaint();
            }

            public void mouseDragged(MouseEvent event) {
              if (start == null || originals.isEmpty()) return;
              if (!moved) {
                checkpoint();
                moved = true;
              }
              double dx = (event.getX() - 30) / zoom - start.getX(),
                  dy = (event.getY() - 30) / zoom - start.getY();
              if (snap.isSelected()) {
                dx = Math.round(dx);
                dy = Math.round(dy);
              }
              for (var e : selected()) {
                var p = originals.get(e.id);
                if (p != null) {
                  e.x = p.getX() + dx;
                  e.y = p.getY() + dy;
                }
              }
              repaint();
            }

            public void mouseReleased(MouseEvent event) {
              if (moved) changed();
              start = null;
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    private void bindMove(String key, double dx, double dy) {
      getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
      getActionMap().put(key, new AbstractAction() {
        public void actionPerformed(ActionEvent event) {
          if (sheetMode || start != null || task != null && !task.isDone()) return;
          List<Publication.Element> movable = selected().stream().filter(e -> !e.locked).toList();
          if (movable.isEmpty()) return;
          checkpoint();
          for (var e : movable) { e.x += dx; e.y += dy; }
          changed();
          requestFocusInWindow();
        }
      });
    }

    public Dimension getPreferredSize() {
      return new Dimension(
          (int) ((sheetMode ? document.print.width : document.width) * zoom) + 70,
          (int) ((sheetMode ? document.print.height : document.height) * zoom) + 70);
    }

    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.translate(30, 30);
        g.scale(zoom, zoom);
        if (sheetMode) {
          if (sheetPreview != null)
            g.drawImage(
                sheetPreview,
                AffineTransform.getScaleInstance(
                    document.print.width / sheetPreview.getWidth(),
                    document.print.height / sheetPreview.getHeight()),
                null);
          return;
        }
        if (designPreview != null)
          g.drawImage(
              designPreview,
              AffineTransform.getScaleInstance(
                  document.width / designPreview.getWidth(),
                  document.height / designPreview.getHeight()),
              null);
        g.setStroke(new BasicStroke((float) (1 / zoom)));
        g.setColor(DeckersPalette.PURPLE);
        for (var e : selected()) {
          AffineTransform transform = new AffineTransform();
          transform.translate(e.x, e.y);
          transform.rotate(Math.toRadians(e.rotation), e.width / 2, e.height / 2);
          g.draw(transform.createTransformedShape(new Rectangle2D.Double(0, 0, e.width, e.height)));
        }
        if (guides.isSelected() && page() != null) {
          g.setColor(new Color(80, 150, 190, 160));
          for (double x : page().verticalGuides)
            g.draw(new Line2D.Double(x, 0, x, document.height));
          for (double y : page().horizontalGuides)
            g.draw(new Line2D.Double(0, y, document.width, y));
        }
        g.setColor(DeckersPalette.text());
        g.setFont(new Font("SansSerif", Font.PLAIN, 1).deriveFont((float) (10 / zoom)));
        for (int x = 0; x <= document.width; x += 10) {
          g.draw(new Line2D.Double(x, -2, x, 0));
          g.drawString(String.valueOf((int) Publication.fromMm(x, document.displayUnit)), x, -3);
        }
        for (int y = 0; y <= document.height; y += 10) {
          g.draw(new Line2D.Double(-2, y, 0, y));
          g.drawString(String.valueOf((int) Publication.fromMm(y, document.displayUnit)), -9, y);
        }
      } finally {
        g.dispose();
      }
    }
  }
}
