package gy.deckers.studio;

import gy.deckers.studio.pdfeditor.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import publishing.TextAlignment;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.printing.PDFPageable;
import services.LanApiClient;
import ui.design.*;

/** Independent local PDF workspace. No store or cloud document writes. */
public final class PdfEditorPanel extends JPanel {
  private PdfDocument document = new PdfDocument();
  private final DefaultListModel<String> pageModel = new DefaultListModel<>();
  private final JList<String> pages = new JList<>(pageModel);
  private final DefaultListModel<PdfDocument.Overlay> objectModel = new DefaultListModel<>();
  private final JList<PdfDocument.Overlay> objects = new JList<>(objectModel);
  private final Map<Integer, ImageIcon> thumbnails = new HashMap<>();
  private final Canvas canvas = new Canvas();
  private final JScrollPane view = new JScrollPane(canvas);
  private final JLabel status = new JLabel("Open or drop PDFs to begin.");
  private final JPanel controls = DeckersSwing.panel();
  private JPanel actionGroup = controls;
  private final List<JButton> actionButtons = new ArrayList<>();
  private final JButton undo, redo;
  private final JSpinner zoom = new JSpinner(new SpinnerNumberModel(100, 10, 300, 10));
  private final JSpinner navigation = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));

  private SwingWorker<?, ?> task;
  private volatile Thread background;
  private volatile PrinterJob activePrintJob;
  private boolean dirty, refreshing;
  private int generation;
  private float scale = 1;
  private BufferedImage preview;
  private OcrEngine.Result recognized;
  private UUID recognizedPage;

  public PdfEditorPanel() {
    setBackground(DeckersPalette.background());
    setLayout(new BorderLayout(8, 8));
    JPanel toolbar = DeckersSwing.panel();
    toolbar.setLayout(new FlowLayout(FlowLayout.LEFT));
    toolbar.add(button("Open…", () -> chooseImport(true)));
    toolbar.add(button("Add Files…", () -> chooseImport(false)));
    undo =
        button(
            "Undo",
            () ->
                mutate(
                    "Undo",
                    () -> {
                      document.undo();
                      return null;
                    }));
    redo =
        button(
            "Redo",
            () ->
                mutate(
                    "Redo",
                    () -> {
                      document.redo();
                      return null;
                    }));
    toolbar.add(undo);
    toolbar.add(redo);
    toolbar.add(button("Save As…", () -> save(false)));
    toolbar.add(button("Print…", this::print));
    JButton cancel = new JButton("Cancel task");
    DeckersSwing.styleUtilityButton(cancel, DeckersPalette.PURPLE);
    cancel.addActionListener(
        e -> {
          Thread running = background;
          PrinterJob printing = activePrintJob;
          if (printing != null) printing.cancel();
          if (running != null) running.interrupt();
        });
    toolbar.add(cancel);
    add(toolbar, BorderLayout.NORTH);
    pages.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    pages.setFixedCellHeight(135);
    pages.setFixedCellWidth(180);
    pages.setCellRenderer(
        (list, value, index, selected, focus) -> {
          JLabel label = new JLabel(value, thumbnails.get(index), JLabel.CENTER);
          label.setVerticalTextPosition(JLabel.BOTTOM);
          label.setHorizontalTextPosition(JLabel.CENTER);
          label.setOpaque(true);
          label.setBackground(
              selected ? list.getSelectionBackground() : DeckersPalette.background());
          label.setForeground(selected ? list.getSelectionForeground() : DeckersPalette.text());
          return label;
        });
    pages.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && !refreshing) render();
        });
    if (!GraphicsEnvironment.isHeadless()) pages.setDragEnabled(true);
    pages.setDropMode(DropMode.INSERT);
    pages.setTransferHandler(new PageTransfer());
    JPanel left = DeckersSwing.panel();
    left.setLayout(new BorderLayout());
    JScrollPane pageScroll = new JScrollPane(pages);
    javax.swing.Timer thumbnailTimer =
        new javax.swing.Timer(
            200,
            e -> {
              if (!isDisplayable() || busy()) return;
              int first = pages.getFirstVisibleIndex(), last = pages.getLastVisibleIndex();
              for (int i = first; i >= 0 && i <= last; i++)
                if (!thumbnails.containsKey(i)) {
                  render();
                  break;
                }
            });
    thumbnailTimer.setRepeats(false);
    pageScroll.getViewport().addChangeListener(e -> thumbnailTimer.restart());
    left.add(pageScroll);
    left.add(button("Select page range…", this::selectRange), BorderLayout.SOUTH);
    controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
    action("Move Before/After…", this::move);
    action("Rotate…", this::rotate);
    action(
        "Delete pages",
        () -> {
          int[] selection = selected();
          if (confirm("Delete the selected pages?"))
            mutate(
                "Deleting pages",
                () -> {
                  document.delete(selection);
                  return null;
                });
        });
    action(
        "Duplicate pages",
        () -> {
          int[] selection = selected();
          mutate(
              "Duplicating pages",
              () -> {
                document.duplicate(selection);
                return null;
              });
        });
    action("Extract selected…", () -> save(true));
    action("Split into pages…", this::split);
    JButton blankPage=action(
        "Insert blank page",
        () -> {
          int at = insertion();
          mutate(
              "Inserting page",
              () -> {
                document.blank(at);
                return null;
              });
        });
    JButton imagePageCommand=action("Insert image page…", this::imagePage);
    action("Crop page…", this::crop);
    action("Export PNG…", this::exportPng);
    JPanel editTools = DeckersSwing.panel();
    editTools.setLayout(new BoxLayout(editTools, BoxLayout.Y_AXIS));
    actionGroup = editTools;
    JButton textCommand=action("Add text…", this::addText);
    JButton imageCommand=action("Add image…", this::addImage);
    JButton barcodeCommand=action("Add barcode…", () -> addCode(false));
    JButton qrCommand=action("Add QR code…", () -> addCode(true));
    JButton rectangleCommand=action("Highlight / rectangle…", this::rectangle);
    JButton signatureCommand=action("Draw signature…", this::signature);
    JButton formCommand=action("Fill PDF form…", this::form);
    JButton pageNumbersCommand=action("Page numbers…", this::pageNumbers);
    JPanel ocrTools = DeckersSwing.panel();
    ocrTools.setLayout(new BoxLayout(ocrTools, BoxLayout.Y_AXIS));
    actionGroup = ocrTools;
    action("OCR selected pages", this::ocr);
    action("Correct scanned region…", this::correct);
    action("Check server OCR…", this::checkOcr);
    ocrTools.add(
        new JLabel(
            "<html><br>OCR sends selected page images<br>to the store server."
                + "<br><br>Scanned corrections turn the page<br>into an image with"
                + " searchable text.</html>"));
    actionGroup = editTools;
    editTools.add(new JLabel("<html><br>Added objects<br>Drag on the page to move.</html>"));
    objects.setCellRenderer(
        (list, value, i, selected, focus) -> {
          JLabel label =
              new JLabel(value.kind() + (value.text().isEmpty() ? "" : ": " + value.text()));
          label.setOpaque(true);
          label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
          return label;
        });
    objects.setVisibleRowCount(4);
    JScrollPane objectScroll = new JScrollPane(objects);
    objectScroll.setMaximumSize(new Dimension(260, 130));
    objectScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
    editTools.add(objectScroll);
    action("Edit selected object…", this::editObject);
    action(
        "Delete selected object",
        () -> {
          var o = objects.getSelectedValue();
          if (o != null)
            mutate(
                "Deleting object",
                () -> {
                  document.removeObject(o.id());
                  return null;
                });
        });
    StudioRibbon ribbon=new StudioRibbon();ribbon.responsive();
    JPanel outputBar=DeckersSwing.panel();outputBar.setLayout(new FlowLayout(FlowLayout.RIGHT,8,0));
    for(Component control:toolbar.getComponents())if(control instanceof JButton command){
      String title=command.getText();
      if(title.startsWith("Save")||title.startsWith("Print")||title.startsWith("Cancel")){StudioVisuals.headerButton(command);outputBar.add(control);}
      else {
        if(command==undo)ribbon.add(StudioCommand.UNDO,undo);
        else if(command==redo)ribbon.add(StudioCommand.REDO,redo);
        else ribbon.add("Home","Files",control);
        if(title.startsWith("Open")){JButton action=StudioRibbon.shortcut(command,title);StudioVisuals.headerButton(action);outputBar.add(action);}
      }
    }
    JPanel top=DeckersSwing.panel();top.setLayout(new BorderLayout(8,8));outputBar.setOpaque(false);ribbon.outputActions(outputBar);top.add(ribbon);
    add(top,BorderLayout.NORTH);
    ribbon.add("Insert","Pages",StudioRibbon.shortcut(blankPage,blankPage.getText()));
    ribbon.add("Insert","Pages",StudioRibbon.shortcut(imagePageCommand,imagePageCommand.getText()));
    for(Component control:controls.getComponents())if(control instanceof JButton command&&command!=blankPage&&command!=imagePageCommand)
      ribbon.add("Layout","Pages",StudioRibbon.shortcut(command,command.getText()));
    ribbon.add(StudioCommand.TEXT,textCommand);
    ribbon.add(StudioCommand.IMAGE,imageCommand);
    ribbon.add(StudioCommand.BARCODE,barcodeCommand);
    ribbon.add(StudioCommand.QR,qrCommand);
    ribbon.add("Insert","Shapes",StudioRibbon.shortcut(rectangleCommand,rectangleCommand.getText()));
    ribbon.add("Insert","Fields",StudioRibbon.shortcut(pageNumbersCommand,pageNumbersCommand.getText()));
    ribbon.add("Insert","Annotations",StudioRibbon.shortcut(signatureCommand,signatureCommand.getText()));
    ribbon.add("Forms","Forms",StudioRibbon.shortcut(formCommand,formCommand.getText()));
    ribbon.task("Home","Edit","Edit objects",editTools);
    ribbon.task("OCR","Recognition","OCR tools",ocrTools);
    for(Component control:ocrTools.getComponents())if(control instanceof JButton command)
      ribbon.add("OCR","Recognition",StudioRibbon.shortcut(command,command.getText()));
    view.setPreferredSize(new Dimension(650,600));view.setMinimumSize(new Dimension(160,100));
    JSplitPane layout=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,view);
    layout.setResizeWeight(0);layout.setDividerLocation(190);add(layout);
    JPanel bottom = DeckersSwing.panel();
    bottom.setLayout(new BorderLayout());
    JPanel navigationBar = DeckersSwing.panel();
    navigationBar.add(new JLabel("Page"));
    navigationBar.add(navigation);
    navigationBar.add(new JLabel("Zoom %"));
    navigationBar.add(zoom);
    JButton fitWidth=button("Fit width", () -> fit(false));
    JButton fitPage=button("Fit page", () -> fit(true));
    JButton search=button("Search…", this::search);
    for(JButton command:new JButton[]{fitWidth,fitPage,search}){
      navigationBar.add(command);
      ribbon.add("View",command==search?"Search":"Zoom",StudioRibbon.shortcut(command,command.getText()));
    }
    bottom.add(navigationBar, BorderLayout.NORTH);
    bottom.add(status, BorderLayout.SOUTH);
    add(bottom, BorderLayout.SOUTH);
    zoom.addChangeListener(
        e -> {
          scale = ((Number) zoom.getValue()).floatValue() / 100;
          if (!refreshing) render();
        });
    navigation.addChangeListener(
        e -> {
          if (!refreshing && !busy() && document.size() > 0)
            pages.setSelectedIndex(((Number) navigation.getValue()).intValue() - 1);
        });
    view.setTransferHandler(new FileTransfer());
    canvas.setTransferHandler(new FileTransfer());
    registerKeyboardAction(
        e -> {
          if (!busy()) {
            try {
              save(false);
            } catch (Exception ex) {
              error(ex);
            }
          }
        },
        KeyStroke.getKeyStroke(
            KeyEvent.VK_S,
            GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    refresh();
  }

  private JButton button(String text, Runnable action) {
    JButton b = new JButton(text);
    actionButtons.add(b);
    DeckersSwing.styleUtilityButton(b, DeckersPalette.PURPLE);
    StudioWorkspaceShell.styleButton(b);
    b.addActionListener(
        e -> {
          if (!busy()) {
            try {
              action.run();
            } catch (Exception ex) {
              error(ex);
            }
          }
        });
    return b;
  }

  private JButton action(String label, Runnable action) {
    JButton b = button(label, action);
    b.setAlignmentX(Component.LEFT_ALIGNMENT);
    b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
    actionGroup.add(b);
    return b;
  }

  private JScrollPane toolScroll(JPanel panel) {
    panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    JScrollPane scroll = new JScrollPane(panel);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return scroll;
  }

  private boolean busy() {
    return task != null;
  }

  private boolean confirm(String text) {
    return JOptionPane.showConfirmDialog(this, text, "PDF Editor", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION;
  }

  private void message(String text) {
    JOptionPane.showMessageDialog(this, text, "PDF Editor", JOptionPane.INFORMATION_MESSAGE);
  }

  private void error(Throwable ex) {
    while (ex.getCause() != null) ex = ex.getCause();
    JOptionPane.showMessageDialog(
        this,
        Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()),
        "PDF Editor",
        JOptionPane.ERROR_MESSAGE);
  }

  boolean discard() {
    if (busy()) {
      message("Cancel the PDF operation and wait for it to finish before leaving SmartStudio.");
      return false;
    }
    return !dirty || confirm("Discard unsaved PDF changes?");
  }

  void reset() {
    if (busy()) throw new IllegalStateException("A PDF operation is still running.");
    shutdown();
    document = new PdfDocument();
    dirty = false;
    recognized = null;
    recognizedPage = null;
    status.setText("Open or drop PDFs to begin.");
    refresh();
  }

  void shutdown() {
    try {
      document.close();
    } catch (java.io.IOException ex) {
      throw new java.io.UncheckedIOException(ex);
    }
  }

  void printDocument() {
    if (!busy()) {
      try {
        print();
      } catch (Exception ex) {
        error(ex);
      }
    }
  }

  private int page() {
    return Math.max(0, pages.getSelectedIndex());
  }

  private int[] selected() {
    int[] s = pages.getSelectedIndices();
    if (s.length == 0) throw new IllegalArgumentException("Select at least one page.");
    return s;
  }

  private int insertion() {
    return document.size() == 0 ? 0 : page() + 1;
  }

  private void requirePage() {
    if (document.size() == 0)
      throw new IllegalArgumentException("Open a PDF or insert a blank page first.");
  }

  private <T> void work(String label, Callable<T> operation, java.util.function.Consumer<T> done) {
    if (busy()) return;
    status.setText(label + "…");
    actionButtons.forEach(b -> b.setEnabled(false));
    zoom.setEnabled(false);
    navigation.setEnabled(false);
    pages.setEnabled(false);
    controls.setEnabled(false);
    task =
        new SwingWorker<T, Void>() {
          protected T doInBackground() throws Exception {
            background = Thread.currentThread();
            try {
              return operation.call();
            } finally {
              background = null;
              Thread.interrupted();
            }
          }

          protected void done() {
            task = null;
            actionButtons.forEach(b -> b.setEnabled(true));
            zoom.setEnabled(true);
            navigation.setEnabled(true);
            pages.setEnabled(true);
            controls.setEnabled(true);
            try {
              if (isCancelled()) status.setText("Cancelled");
              else {
                T result = get();
                done.accept(result);
                status.setText(
                    "Ready — " + document.size() + " pages" + (dirty ? " • unsaved changes" : ""));
              }
            } catch (Exception ex) {
              Throwable cause = ex;
              while (cause.getCause() != null) cause = cause.getCause();
              if (cause instanceof InterruptedException
                  || cause instanceof java.util.concurrent.CancellationException)
                status.setText("Cancelled — completed edits can be undone");
              else {
                error(ex);
                status.setText("Operation failed");
              }
            }
            if (!label.equals("Rendering") && !label.equals("Preparing print")) refresh();
            else {
              undo.setEnabled(document.canUndo());
              redo.setEnabled(document.canRedo());
            }
          }
        };
    task.execute();
  }

  private <T> void mutate(String label, Callable<T> operation) {
    dirty = true;
    recognized = null;
    work(label, operation, result -> {});
  }

  private void refresh() {
    int current = page();
    refreshing = true;
    pageModel.clear();
    thumbnails.clear();
    for (int i = 0; i < document.size(); i++) pageModel.addElement("Page " + (i + 1));
    navigation.setModel(
        new SpinnerNumberModel(
            Math.min(current + 1, Math.max(1, document.size())),
            1,
            Math.max(1, document.size()),
            1));
    if (document.size() > 0) pages.setSelectedIndex(Math.min(current, document.size() - 1));
    undo.setEnabled(document.canUndo());
    redo.setEnabled(document.canRedo());
    refreshing = false;
    if (!busy()) render();
  }

  private void render() {
    if (busy()) return;
    int revision = ++generation, index = page();
    PdfDocument.Overlay selectedObject = objects.getSelectedValue();
    objectModel.clear();
    if (document.size() == 0) {
      preview = null;
      canvas.repaint();
      return;
    }
    for (var object : document.objects(index)) objectModel.addElement(object);
    if (selectedObject != null) objects.setSelectedValue(selectedObject, true);
    refreshing = true;
    navigation.setValue(index + 1);
    refreshing = false;
    float requestedScale = scale;
    int firstVisible = pages.getFirstVisibleIndex(), lastVisible = pages.getLastVisibleIndex();
    int firstThumbnail = firstVisible < 0 ? Math.max(0, index - 3) : firstVisible;
    int lastThumbnail =
        lastVisible < 0
            ? Math.min(document.size(), index + 4)
            : Math.min(document.size(), lastVisible + 1);
    // A single worker owns rendering; document access is serialized by the engine.
    work(
        "Rendering",
        () -> {
          BufferedImage image = document.render(index, requestedScale);
          Map<Integer, ImageIcon> icons = new HashMap<>();
          for (int i = firstThumbnail; i < lastThumbnail; i++) {
            if (Thread.currentThread().isInterrupted()) break;
            var box = document.bounds(i);
            float thumbScale = Math.min(150 / box.getWidth(), 100 / box.getHeight());
            icons.put(i, new ImageIcon(document.render(i, thumbScale)));
          }
          return new Object[] {image, icons};
        },
        result -> {
          if (revision != generation) return;
          preview = (BufferedImage) result[0];
          @SuppressWarnings("unchecked")
          Map<Integer, ImageIcon> icons = (Map<Integer, ImageIcon>) result[1];
          thumbnails.putAll(icons);
          if (thumbnails.size() > 128)
            thumbnails.keySet().removeIf(i -> i < firstThumbnail - 20 || i > lastThumbnail + 20);
          pages.repaint();
          canvas.setPreferredSize(new Dimension(preview.getWidth() + 32, preview.getHeight() + 32));
          canvas.revalidate();
          canvas.repaint();
        });
  }

  private JFileChooser chooser(String title, boolean multiple, String... extensions) {
    JFileChooser c = new JFileChooser();
    c.setDialogTitle(title);
    c.setMultiSelectionEnabled(multiple);
    c.setFileFilter(new FileNameExtensionFilter(String.join(", ", extensions), extensions));
    return c;
  }

  private void chooseImport(boolean replace) {
    JFileChooser c = chooser("Open PDFs (merged in the listed order)", true, "pdf");
    if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
      importFiles(Arrays.stream(c.getSelectedFiles()).map(File::toPath).toList(), replace);
  }

  private record Input(Path file, String password, boolean signed) {}

  private void importFiles(List<Path> files, boolean replace) {
    if (files.isEmpty() || (replace && !discard())) return;
    DefaultListModel<Path> order = new DefaultListModel<>();
    files.forEach(order::addElement);
    JList<Path> list = new JList<>(order);
    list.setSelectedIndex(0);
    JPanel selection = new JPanel(new BorderLayout());
    selection.add(new JScrollPane(list));
    JPanel buttons = new JPanel();
    for (int direction : new int[] {-1, 1}) {
      JButton b = new JButton(direction < 0 ? "Up" : "Down");
      StudioWorkspaceShell.styleButton(b);
    b.addActionListener(
          e -> {
            int i = list.getSelectedIndex(), to = i + direction;
            if (i >= 0 && to >= 0 && to < order.size()) {
              Path p = order.remove(i);
              order.add(to, p);
              list.setSelectedIndex(to);
            }
          });
      buttons.add(b);
    }
    selection.add(buttons, BorderLayout.SOUTH);
    JSpinner at =
        new JSpinner(
            new SpinnerNumberModel(
                replace ? 1 : insertion() + 1, 1, replace ? 1 : document.size() + 1, 1));
    JPanel prompt = new JPanel(new BorderLayout());
    prompt.add(selection);
    JPanel location = new JPanel();
    location.add(new JLabel("Insert before page (last position appends):"));
    location.add(at);
    prompt.add(location, BorderLayout.SOUTH);
    if (JOptionPane.showConfirmDialog(
            this, prompt, "PDF order and insertion", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    List<Path> ordered = Collections.list(order.elements());
    int insert = ((Number) at.getValue()).intValue() - 1;
    // Probe in the worker; password/signature prompts are marshalled back to the EDT.
    if (!replace) dirty = true;
    work(
        "Opening PDFs",
        () -> {
          List<Input> inputs = new ArrayList<>();
          for (Path file : ordered) {
            String password = "";
            while (true) {
              try (PDDocument pdf = PdfDocument.load(file, password)) {
                boolean signed = !pdf.getSignatureDictionaries().isEmpty();
                if (signed
                    && !onEdt(
                        () ->
                            confirm(
                                file.getFileName()
                                    + " is digitally signed. Editing produces an unsigned copy."
                                    + " Continue?")))
                  throw new java.util.concurrent.CancellationException("Import cancelled.");
                inputs.add(new Input(file, password, signed));
                break;
              } catch (InvalidPasswordException | PdfDocument.PermissionException ex) {
                password =
                    onEdt(
                        () -> {
                          JPasswordField field = new JPasswordField();
                          int choice =
                              JOptionPane.showConfirmDialog(
                                  this,
                                  field,
                                  "Password for " + file.getFileName(),
                                  JOptionPane.OK_CANCEL_OPTION);
                          return choice == JOptionPane.OK_OPTION
                              ? new String(field.getPassword())
                              : null;
                        });
                if (password == null)
                  throw new java.util.concurrent.CancellationException("Import cancelled.");
              }
            }
          }
          PdfDocument target = replace ? new PdfDocument() : document;
          try {
            int position = insert;
            for (Input input : inputs) {
              if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
              int before = target.size();
              target.append(input.file, input.password, position, input.signed);
              position += target.size() - before;
            }
            return target;
          } catch (Exception ex) {
            if (replace) target.close();
            throw ex;
          }
        },
        target -> {
          if (replace) {
            try {
              document.close();
            } catch (Exception ex) {
              error(ex);
            }
            document = target;
          }
          dirty = true;
          recognized = null;
        });
  }

  private <T> T onEdt(Callable<T> call) throws Exception {
    java.util.concurrent.FutureTask<T> future = new java.util.concurrent.FutureTask<>(call);
    SwingUtilities.invokeAndWait(future);
    return future.get();
  }

  private Path output(String extension) {
    JFileChooser c = chooser("Save " + extension.toUpperCase(Locale.ROOT), false, extension);
    if (c.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
    Path p = c.getSelectedFile().toPath();
    if (!p.toString().toLowerCase(Locale.ROOT).endsWith("." + extension))
      p = Path.of(p + "." + extension);
    if (Files.exists(p) && !confirm("Replace " + p.getFileName() + "?")) return null;
    return p;
  }

  private void save(boolean extract) {
    requirePage();
    int[] selection = extract ? selected() : null;
    Path path = output("pdf");
    if (path != null)
      work(
          "Saving PDF",
          () -> {
            document.save(path, selection);
            return path;
          },
          p -> {
            if (!extract) dirty = false;
            message("Saved " + p);
          });
  }

  private Path directory() {
    JFileChooser c = new JFileChooser();
    c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    return c.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
        ? c.getSelectedFile().toPath()
        : null;
  }

  private void split() {
    requirePage();
    Path directory = directory();
    if (directory == null) return;
    int count = document.size();
    if (!confirm("Create " + count + " PDFs? Existing page-N.pdf files will stop the operation."))
      return;
    work(
        "Splitting PDF",
        () -> {
          for (int i = 0; i < count; i++)
            if (Files.exists(directory.resolve("page-" + (i + 1) + ".pdf")))
              throw new IllegalArgumentException(
                  "Output file already exists: page-" + (i + 1) + ".pdf");
          for (int i = 0; i < count; i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            document.save(directory.resolve("page-" + (i + 1) + ".pdf"), new int[] {i});
          }
          return null;
        },
        v -> message("Pages saved."));
  }

  private void exportPng() {
    int[] selection = selected();
    Path directory = directory();
    if (directory == null) return;
    work(
        "Exporting PNG",
        () -> {
          for (int i : selection)
            if (Files.exists(directory.resolve("page-" + (i + 1) + ".png")))
              throw new IllegalArgumentException(
                  "An output PNG already exists. Choose an empty folder.");
          for (int i : selection) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            ImageIO.write(
                document.render(i, 2),
                "png",
                directory.resolve("page-" + (i + 1) + ".png").toFile());
          }
          return null;
        },
        v -> message("Images saved."));
  }

  private void selectRange() {
    String input =
        JOptionPane.showInputDialog(this, "Pages, for example 1–3, 5", "1-" + document.size());
    if (input != null) pages.setSelectedIndices(PageRanges.parse(input, document.size()));
  }

  private void move() {
    int[] s = selected();
    JSpinner destination = new JSpinner(new SpinnerNumberModel(1, 1, document.size(), 1));
    JComboBox<String> side = new JComboBox<>(new String[] {"Before", "After"});
    JPanel p = new JPanel();
    p.add(side);
    p.add(destination);
    if (JOptionPane.showConfirmDialog(this, p, "Move selected pages", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION) {
      int at =
          ((Number) destination.getValue()).intValue() - (side.getSelectedIndex() == 0 ? 1 : 0);
      mutate(
          "Moving pages",
          () -> {
            document.move(s, at);
            return null;
          });
    }
  }

  private void rotate() {
    int[] s = selected();
    Object angle =
        JOptionPane.showInputDialog(
            this,
            "Clockwise rotation",
            "Rotate pages",
            JOptionPane.QUESTION_MESSAGE,
            null,
            new Integer[] {90, 180, 270},
            90);
    if (angle != null)
      mutate(
          "Rotating pages",
          () -> {
            document.rotate(s, (Integer) angle);
            return null;
          });
  }

  private void fit(boolean whole) {
    requirePage();
    var b = document.bounds(page());
    boolean rotated = Math.floorMod(document.rotation(page()), 180) != 0;
    float w = rotated ? b.getHeight() : b.getWidth(), h = rotated ? b.getWidth() : b.getHeight();
    double amount = (view.getViewport().getWidth() - 40) / w;
    if (whole) amount = Math.min(amount, (view.getViewport().getHeight() - 40) / h);
    zoom.setValue(Math.max(10, Math.min(300, (int) (amount * 100))));
  }

  private void crop() {
    requirePage();
    var b = document.bounds(page());
    String input =
        JOptionPane.showInputDialog(
            this,
            "Crop: left, bottom, width, height in PDF points",
            b.getLowerLeftX()
                + ", "
                + b.getLowerLeftY()
                + ", "
                + b.getWidth()
                + ", "
                + b.getHeight());
    if (input == null) return;
    float[] n = numbers(input, 4);
    int p = page();
    mutate(
        "Cropping",
        () -> {
          document.crop(p, n[0], n[1], n[2], n[3]);
          return null;
        });
  }

  private float[] numbers(String input, int count) {
    String[] parts = input.split(",");
    if (parts.length != count)
      throw new IllegalArgumentException("Enter " + count + " comma-separated numbers.");
    float[] result = new float[count];
    for (int i = 0; i < count; i++) {
      result[i] = Float.parseFloat(parts[i].trim());
      if (!Float.isFinite(result[i])) throw new IllegalArgumentException("Use finite numbers.");
    }
    return result;
  }

  private void addText() {
    requirePage();
    JTextArea entry = new JTextArea(3, 24);
    var alignment = textAlignment(TextAlignment.LEFT);
    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.add(new JLabel("Text (Helvetica)"));
    panel.add(new JScrollPane(entry));
    panel.add(new JLabel("Text alignment"));
    panel.add(alignment);
    if (JOptionPane.showConfirmDialog(this, panel, "Add text", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION || entry.getText().isEmpty()) return;
    String text = entry.getText();
    var placement = (TextAlignment)alignment.getSelectedItem();
    int p = page();
    var b = document.bounds(p);
    mutate(
        "Adding text",
        () -> {
          document.text(p, text, b.getLowerLeftX() + 30, b.getUpperRightY() - 50,
                        Math.max(20, b.getWidth() - 60), 14, Color.BLACK, placement);
          return null;
        });
  }

  private static JComboBox<TextAlignment> textAlignment(TextAlignment current) {
    var box = new JComboBox<>(TextAlignment.values());
    box.setSelectedItem(current == null ? TextAlignment.LEFT : current);
    box.getAccessibleContext().setAccessibleName("Text alignment");
    return box;
  }

  private Path imageFile() {
    JFileChooser c = chooser("Choose image", false, "png", "jpg", "jpeg");
    return c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
        ? c.getSelectedFile().toPath()
        : null;
  }

  private void addImage() {
    requirePage();
    Path file = imageFile();
    if (file == null) return;
    int target = page();
    mutate(
        "Adding image",
        () -> {
          BufferedImage image = ImageIO.read(file.toFile());
          if (image == null) throw new IllegalArgumentException("Image could not be read.");
          placeArtwork(target, image, "Image");
          return null;
        });
  }

  private void addCode(boolean qr) {
    requirePage();
    var code = BarcodeEditor.chooseBadge(this, qr);
    if (code == null) return;
    int target = page();
    mutate("Adding code", () -> {
      var artwork = BadgeCodeArtwork.create(code);
      placeArtwork(target, artwork.image(), artwork.name());
      return null;
    });
  }

  boolean canInsertArtwork() {
    return !busy() && document.size() > 0;
  }

  void insertArtwork(BufferedImage image) {
    if (!canInsertArtwork()) throw new IllegalStateException("Open a PDF page and finish the current task first.");
    int target = page();
    mutate("Adding image", () -> {
      placeArtwork(target, image, "Image");
      return null;
    });
  }

  private void placeArtwork(int page, BufferedImage image, String label) throws Exception {
    var bounds = document.bounds(page);
    float margin = Math.min(20, Math.min(bounds.getWidth(), bounds.getHeight()) * .1f);
    float scale = Math.min(Math.min(250, bounds.getWidth() - 2 * margin) / image.getWidth(),
        (bounds.getHeight() - 2 * margin) / image.getHeight());
    document.image(page, image, bounds.getLowerLeftX() + margin, bounds.getLowerLeftY() + margin,
        image.getWidth() * scale, image.getHeight() * scale, label);
  }

  private void imagePage() {
    Path file = imageFile();
    if (file == null) return;
    int at = insertion();
    mutate(
        "Inserting image page",
        () -> {
          BufferedImage image = ImageIO.read(file.toFile());
          if (image == null) throw new IllegalArgumentException("Image could not be read.");
          document.edit(
              "Insert image page",
              pdf -> {
                document.blank(at);
                var b = document.bounds(at);
                float ratio =
                    Math.min(b.getWidth() / image.getWidth(), b.getHeight() / image.getHeight());
                document.image(
                    at, image, 0, 0, image.getWidth() * ratio, image.getHeight() * ratio);
              });
          return null;
        });
  }

  private void rectangle() {
    requirePage();
    Object kind =
        JOptionPane.showInputDialog(
            this,
            "Shape",
            "Add shape",
            JOptionPane.QUESTION_MESSAGE,
            null,
            new String[] {"Highlight", "Rectangle"},
            "Highlight");
    if (kind == null) return;
    int p = page();
    var b = document.bounds(p);
    mutate(
        "Adding shape",
        () -> {
          document.rectangle(
              p,
              b.getLowerLeftX() + 30,
              b.getLowerLeftY() + 60,
              150,
              24,
              kind.equals("Highlight") ? Color.YELLOW : Color.BLACK,
              kind.equals("Highlight"));
          return null;
        });
  }

  private void signature() {
    requirePage();
    BufferedImage ink = new BufferedImage(600, 180, BufferedImage.TYPE_INT_ARGB);
    JPanel pad =
        new JPanel() {
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(ink, 0, 0, null);
          }
        };
    pad.setBackground(Color.WHITE);
    pad.setPreferredSize(new Dimension(600, 180));
    MouseAdapter drawing =
        new MouseAdapter() {
          Point last;

          public void mousePressed(MouseEvent e) {
            last = e.getPoint();
          }

          public void mouseDragged(MouseEvent e) {
            Graphics2D g = ink.createGraphics();
            try {
              g.setColor(Color.BLACK);
              g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
              g.drawLine(last.x, last.y, e.getX(), e.getY());
            } finally {
              g.dispose();
            }
            last = e.getPoint();
            pad.repaint();
          }
        };
    pad.addMouseListener(drawing);
    pad.addMouseMotionListener(drawing);
    if (JOptionPane.showConfirmDialog(
            this, pad, "Draw signature (visual mark only)", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION) {
      int p = page();
      var b = document.bounds(p);
      mutate(
          "Adding signature",
          () -> {
            document.image(p, ink, b.getLowerLeftX() + 30, b.getLowerLeftY() + 30, 240, 72);
            return null;
          });
    }
  }

  private void editObject() {
    var o = objects.getSelectedValue();
    if (o == null) return;
    JTextField position =
        new JTextField(
            o.x() + ", " + o.y() + ", " + o.width() + ", " + o.height() + ", " + o.fontSize());
    JTextArea text = new JTextArea(o.text(), 3, 24);
    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.add(new JLabel("x, y, width, height, font size (PDF points)"));
    panel.add(position);
    panel.add(new JScrollPane(text));
    var alignment = textAlignment(o.alignment());
    if (o.kind().equals("Text")) {
      panel.add(new JLabel("Text alignment"));
      panel.add(alignment);
    }
    JButton color = new JButton("Colour");
    Color[] chosen = {o.color()};
    color.addActionListener(
        e -> {
          Color c = JColorChooser.showDialog(this, "Object colour", chosen[0]);
          if (c != null) chosen[0] = c;
        });
    panel.add(color);
    if (JOptionPane.showConfirmDialog(this, panel, "Edit object", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    float[] n = numbers(position.getText(), 5);
    if (n[2] <= 0 || n[3] <= 0 || n[4] <= 0)
      throw new IllegalArgumentException("Dimensions and font size must be positive.");
    String value = text.getText();
    Color selectedColor = chosen[0];
    var placement = o.kind().equals("Text") ? (TextAlignment)alignment.getSelectedItem() : null;
    mutate(
        "Updating object",
        () -> {
          document.updateObject(
              new PdfDocument.Overlay(
                  o.id(),
                  o.page(),
                  o.kind(),
                  value,
                  o.image(),
                  n[0],
                  n[1],
                  n[2],
                  n[3],
                  n[4],
                  selectedColor,
                  placement));
          return null;
        });
  }

  private void form() {
    requirePage();
    var fields = document.fields();
    if (fields.isEmpty()) {
      message("This document has no supported interactive form fields.");
      return;
    }
    Object selected =
        JOptionPane.showInputDialog(
            this,
            "Field",
            "Fill PDF form",
            JOptionPane.QUESTION_MESSAGE,
            null,
            fields.keySet().toArray(),
            fields.keySet().iterator().next());
    if (selected == null) return;
    var currentAlignment = document.fieldTextAlignment(selected.toString());
    String value;
    TextAlignment placement = null;
    if (currentAlignment.isPresent()) {
      var alignment = textAlignment(currentAlignment.get());
      JTextField entry = new JTextField(fields.get(selected), 24);
      JPanel panel = new JPanel(new GridLayout(0, 1));
      panel.add(new JLabel("Value for " + selected));
      panel.add(entry);
      panel.add(new JLabel("Text alignment"));
      panel.add(alignment);
      if (JOptionPane.showConfirmDialog(this, panel, "Fill PDF form", JOptionPane.OK_CANCEL_OPTION)
          != JOptionPane.OK_OPTION) return;
      value = entry.getText();
      placement = (TextAlignment)alignment.getSelectedItem();
    } else {
      value = JOptionPane.showInputDialog(this, "Value for " + selected, fields.get(selected));
    }
    var chosenAlignment = placement;
    if (value != null)
      mutate(
          "Filling form",
          () -> {
            document.field(selected.toString(), value, chosenAlignment);
            return null;
          });
  }

  private void pageNumbers() {
    requirePage();
    JSpinner start = new JSpinner(new SpinnerNumberModel(1, 0, 1000000, 1));
    JComboBox<String> position =
        new JComboBox<>(
            new String[] {"Bottom centre", "Bottom left", "Bottom right", "Top centre"});
    JPanel p = new JPanel(new GridLayout(0, 1));
    p.add(new JLabel("Starting number (all pages)"));
    p.add(start);
    p.add(position);
    if (JOptionPane.showConfirmDialog(this, p, "Page numbers", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    int first = ((Number) start.getValue()).intValue(), where = position.getSelectedIndex();
    mutate(
        "Numbering pages",
        () -> {
          document.edit(
              "Page numbers",
              pdf -> {
                for (int i = 0; i < document.size(); i++) {
                  var b = document.bounds(i);
                  String text = Integer.toString(first + i);
                  float x =
                      where == 1
                          ? b.getLowerLeftX() + 20
                          : where == 2
                              ? b.getUpperRightX() - 40
                              : b.getLowerLeftX() + b.getWidth() / 2 - text.length() * 3;
                  document.text(
                      i,
                      text,
                      x,
                      where == 3 ? b.getUpperRightY() - 25 : b.getLowerLeftY() + 20,
                      10,
                      Color.BLACK);
                }
              });
          return null;
        });
  }

  private void search() {
    requirePage();
    String query = JOptionPane.showInputDialog(this, "Find text (run OCR first for scanned pages)");
    if (query == null || query.isBlank()) return;
    int begin = page();
    work(
        "Searching",
        () -> {
          for (int offset = 1; offset <= document.size(); offset++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            int i = (begin + offset) % document.size();
            if (document.text(i).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
              return i;
          }
          return -1;
        },
        found -> {
          if (found < 0) message("Text not found.");
          else {
            refreshing = true;
            pages.setSelectedIndex(found);
            refreshing = false;
          }
        });
  }

  private void print() {
    requirePage();
    PrinterJob job = PrinterJob.getPrinterJob();
    work(
        "Preparing print",
        () -> PDDocument.load(document.bytes()),
        copy -> {
          job.setPageable(new PDFPageable(copy));
          if (!job.printDialog()) {
            try {
              copy.close();
            } catch (Exception ex) {
              error(ex);
            }
            return;
          }
          SwingUtilities.invokeLater(
              () ->
                  work(
                      "Printing",
                      () -> {
                        activePrintJob = job;
                        try (copy) {
                          job.print();
                        } finally {
                          activePrintJob = null;
                        }
                        return null;
                      },
                      v -> {}));
        });
  }

  private void checkOcr() {
    work(
        "Checking store server OCR",
        LanApiClient::checkStudioPdfOcr,
        language -> message("Store server OCR is available. Configured language: " + language));
  }

  private void ocr() {
    int[] selection = selected();
    work(
        "Recognizing selected pages on the store server",
        () -> {
          OcrEngine.Result last = null;
          for (int i : selection) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            last = OcrEngine.recognize(document.render(i, 3));
            OcrEngine.apply(document, i, last, false);
            SwingUtilities.invokeLater(() -> dirty = true);
          }
          return last;
        },
        result -> {
          dirty = true;
          recognized = result;
          recognizedPage = document.pageId(selection[selection.length - 1]);
          refreshing = true;
          pages.setSelectedIndex(selection[selection.length - 1]);
          refreshing = false;
          message(
              "OCR added searchable text. Use Correct scanned region to change recognized words.");
        });
  }

  private void correct() {
    requirePage();
    int index = page();
    if (recognized == null || !document.pageId(index).equals(recognizedPage)) {
      message(
          "Run OCR on this page first. Select all pages with the page-range control to OCR an"
              + " entire document.");
      return;
    }
    if (!document.objects(index).isEmpty()) {
      message("Save and reopen this PDF before correcting a page with added objects.");
      return;
    }
    OcrEngine.Result result = recognized;
    DefaultListModel<String> words = new DefaultListModel<>();
    for (var word : result.words())
      words.addElement(word.text() + " (confidence " + Math.round(word.confidence()) + ")");
    JList<String> list = new JList<>(words);
    list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    list.setVisibleRowCount(7);
    JTextArea replacement = new JTextArea(3, 30);
    list.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting())
            replacement.setText(
                Arrays.stream(list.getSelectedIndices())
                    .mapToObj(i -> result.words().get(i).text())
                    .collect(java.util.stream.Collectors.joining(" ")));
        });
    JSpinner size = new JSpinner(new SpinnerNumberModel(14, 4, 100, 1));
    JComboBox<String> font = new JComboBox<>(new String[] {"SansSerif", "Serif", "Monospaced"});
    Color[] colors = {Color.BLACK, Color.WHITE};
    JPanel panel = new JPanel(new GridLayout(0, 1));
    panel.add(new JLabel("Select consecutive words to replace"));
    panel.add(new JScrollPane(list));
    panel.add(new JScrollPane(replacement));
    panel.add(new JLabel("Font size in points"));
    panel.add(size);
    panel.add(font);
    for (int i = 0; i < 2; i++) {
      int slot = i;
      JButton b = new JButton(i == 0 ? "Text colour" : "Background colour");
      StudioWorkspaceShell.styleButton(b);
    b.addActionListener(
          e -> {
            Color c = JColorChooser.showDialog(this, "Correction colour", colors[slot]);
            if (c != null) colors[slot] = c;
          });
      panel.add(b);
    }
    panel.add(new JLabel("Corrected page becomes image-based. This is not secure redaction."));
    if (JOptionPane.showConfirmDialog(
                this, panel, "Correct scanned region", JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION
        || list.getSelectedIndices().length == 0) return;
    Rectangle region = null;
    for (int i : list.getSelectedIndices()) {
      var word = result.words().get(i);
      Rectangle r = new Rectangle(word.x(), word.y(), word.width(), word.height());
      region = region == null ? r : region.union(r);
    }
    Font chosen =
        new Font(
            font.getSelectedItem().toString(),
            Font.PLAIN,
            ((Number) size.getValue()).intValue() * 3);
    BufferedImage corrected =
        OcrEngine.correct(result, region, replacement.getText(), chosen, colors[0], colors[1]);
    ImageIcon icon =
        new ImageIcon(
            corrected.getScaledInstance(
                Math.min(700, corrected.getWidth()), -1, Image.SCALE_SMOOTH));
    if (!confirmPreview(icon)) return;
    work(
        "Applying correction and refreshing OCR",
        () -> {
          var newResult = OcrEngine.recognize(corrected);
          OcrEngine.apply(document, index, newResult, true);
          SwingUtilities.invokeLater(() -> {
            dirty = true;
            recognized = null;
          });
          return null;
        },
        ignored -> {});
  }

  private boolean confirmPreview(ImageIcon icon) {
    JScrollPane scroll = new JScrollPane(new JLabel(icon));
    scroll.setPreferredSize(new Dimension(730, 500));
    return JOptionPane.showConfirmDialog(
            this, scroll, "Correction preview — apply?", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION;
  }

  private final class Canvas extends JPanel {
    private Point press;
    private PdfDocument.Overlay moving;

    Canvas() {
      setBackground(DeckersPalette.background());
      MouseAdapter mouse =
          new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
              if (busy() || preview == null) return;
              Point2D.Float point = pdfPoint(e.getPoint());
              List<PdfDocument.Overlay> all = document.objects(page());
              for (int i = all.size() - 1; i >= 0; i--) {
                var o = all.get(i);
                if (new java.awt.geom.Rectangle2D.Float(
                        o.x(),
                        o.kind().equals("Text") ? o.y() - o.height() / 4 : o.y(),
                        o.width(),
                        o.height())
                    .contains(point)) {
                  moving = o;
                  press = e.getPoint();
                  objects.setSelectedValue(o, true);
                  break;
                }
              }
            }

            public void mouseReleased(MouseEvent e) {
              if (moving == null || busy()) return;
              var o = moving;
              moving = null;
              Point2D.Float a = pdfPoint(press), b = pdfPoint(e.getPoint());
              mutate(
                  "Moving object",
                  () -> {
                    document.updateObject(
                        new PdfDocument.Overlay(
                            o.id(),
                            o.page(),
                            o.kind(),
                            o.text(),
                            o.image(),
                            o.x() + b.x - a.x,
                            o.y() + b.y - a.y,
                            o.width(),
                            o.height(),
                            o.fontSize(),
                            o.color(),
                            o.alignment()));
                    return null;
                  });
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (preview != null) g.drawImage(preview, 16, 16, null);
      else g.drawString("Open a PDF or insert a blank page", 30, 40);
    }

    private Point2D.Float pdfPoint(Point point) {
      var b = document.bounds(page());
      float x = (point.x - 16) / scale, y = (point.y - 16) / scale;
      return switch (Math.floorMod(document.rotation(page()), 360)) {
        case 90 -> new Point2D.Float(b.getLowerLeftX() + y, b.getLowerLeftY() + x);
        case 180 -> new Point2D.Float(b.getUpperRightX() - x, b.getLowerLeftY() + y);
        case 270 -> new Point2D.Float(b.getUpperRightX() - y, b.getUpperRightY() - x);
        default -> new Point2D.Float(b.getLowerLeftX() + x, b.getUpperRightY() - y);
      };
    }
  }

  private class FileTransfer extends TransferHandler {
    public boolean canImport(TransferSupport support) {
      return !busy() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    public boolean importData(TransferSupport support) {
      if (!canImport(support)) return false;
      try {
        @SuppressWarnings("unchecked")
        List<File> files =
            (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        importFiles(files.stream().map(File::toPath).toList(), false);
        return true;
      } catch (Exception ex) {
        error(ex);
        return false;
      }
    }
  }

  private final class PageTransfer extends FileTransfer {
    private int[] dragging;

    public int getSourceActions(JComponent c) {
      return MOVE;
    }

    protected Transferable createTransferable(JComponent c) {
      dragging = pages.getSelectedIndices();
      return new StringSelection("smartstock-pdf-pages");
    }

    public boolean canImport(TransferSupport support) {
      return super.canImport(support)
          || (!busy()
              && dragging != null
              && support.isDrop()
              && support.isDataFlavorSupported(DataFlavor.stringFlavor));
    }

    public boolean importData(TransferSupport support) {
      if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        return super.importData(support);
      if (!canImport(support)) return false;
      int at = ((JList.DropLocation) support.getDropLocation()).getIndex();
      int[] indices = dragging;
      dragging = null;
      mutate(
          "Reordering pages",
          () -> {
            document.move(indices, at);
            return null;
          });
      return true;
    }

    protected void exportDone(JComponent c, Transferable data, int action) {
      dragging = null;
    }
  }
}
