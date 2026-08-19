package ui.screens.companyprefs;

import services.ImageAssetReference;
import services.LanApiClient;
import utils.ImageCacheManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Company Preferences administrator surface for the server image manifest. */
public final class ImageStoragePanel extends JPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Asset ID", "Category", "Filename", "Size", "References", "Lifecycle", "Local", "Provider", "Cloud", "Migration", "Unused Since", "Error"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel summary = new JLabel("Loading image storage...");
    private final JLabel preview = new JLabel("Select an image", SwingConstants.CENTER);
    private final JButton keepButton = new JButton("Keep");
    private final JButton purgeButton = new JButton("Permanently Delete");

    public ImageStoragePanel() {
        super(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));
        JPanel heading = new JPanel(new BorderLayout(8, 8));
        JLabel title = new JLabel("Image Storage");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        heading.add(title, BorderLayout.NORTH);
        heading.add(summary, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refresh = new JButton("Refresh");
        JButton reconcile = new JButton("Reconcile Now");
        JButton beginMigration = new JButton("Begin OneDrive Copy");
        JButton activateOneDrive = new JButton("Activate OneDrive");
        JButton rollback = new JButton("Rollback to Supabase");
        JButton setupOneDrive = new JButton("Set Up OneDrive");
        actions.add(refresh);
        actions.add(reconcile);
        actions.add(setupOneDrive);actions.add(beginMigration);actions.add(activateOneDrive);actions.add(rollback);
        actions.add(keepButton);
        actions.add(purgeButton);
        heading.add(actions, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateSelection();
        });
        preview.setPreferredSize(new Dimension(220, 220));
        preview.setBorder(BorderFactory.createLineBorder(new Color(210, 214, 220)));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), preview);
        split.setResizeWeight(.78);
        add(split, BorderLayout.CENTER);

        refresh.addActionListener(event -> load());
        reconcile.addActionListener(event -> runAction("Reconciling images...", () -> {
            LanApiClient.reconcileImageAssets();
            return null;
        }, this::load));
        beginMigration.addActionListener(event->runAction("Starting verified OneDrive copy...",()->{LanApiClient.beginOneDriveImageMigration();return null;},this::load));
        activateOneDrive.addActionListener(event->runAction("Activating OneDrive...",()->{LanApiClient.activateOneDriveImages();return null;},this::load));
        rollback.addActionListener(event->runAction("Rolling product images back to Supabase...",()->{LanApiClient.rollbackOneDriveImages();return null;},this::load));
        setupOneDrive.addActionListener(event->setupOneDrive());
        setupOneDrive.setEnabled(data.DatabaseConfig.load().mode()==data.DatabaseMode.SERVER);
        setupOneDrive.setToolTipText(setupOneDrive.isEnabled()?"Import the server-only Microsoft certificate and test the Graph connection.":"Open Company Preferences on the store server computer to configure credentials.");
        keepButton.addActionListener(event -> {
            String id = selectedId();
            if (id != null) runAction("Keeping image...", () -> {
                LanApiClient.retainImageAsset(id);
                return null;
            }, this::load);
        });
        purgeButton.addActionListener(event -> purgeSelected());
        updateButtons();
        load();
    }

    private void load() {
        setBusy(true, "Loading image storage...");
        new SwingWorker<LanApiClient.ImageAssetState, Void>() {
            @Override protected LanApiClient.ImageAssetState doInBackground() throws Exception {
                return LanApiClient.imageAssets();
            }
            @Override protected void done() {
                try {
                    apply(get());
                } catch (Exception ex) {
                    showError(ex);
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void apply(LanApiClient.ImageAssetState state) {
        model.setRowCount(0);
        for (LanApiClient.ImageAssetRecord row : state.assets()) {
            model.addRow(new Object[]{row.assetId(), row.category(), row.filename(), formatBytes(row.byteSize()),
                    row.referenceCount(), row.lifecycleStatus(), row.localStatus(),row.cloudProvider(), row.cloudStatus(),row.migrationStatus(),
                    formatDate(row.unusedSinceEpochMillis()), row.lastError() == null ? "" : row.lastError()});
        }
        LanApiClient.ImageAssetCounts counts = state.counts();
        summary.setText("Unused: " + counts.unused() + "   Pending uploads: " + counts.pendingUploads()
                + "   Missing local: " + counts.missingLocal() + "   Missing cloud: " + counts.missingCloud()
                + "   Failed purges: " + counts.failedPurges()
                + "   OneDrive: "+counts.oneDrivePhase()+" / "+(counts.oneDriveReady()?"ready":"not verified")+" ("+counts.migrationPending()+" pending)"
                + (counts.oneDriveConfigured()?"":"   • OneDrive server credential required")
                + (counts.cloudCredentialConfigured() ? "" : "   • Server cloud credential required for private images/uploads"));
        updateButtons();
    }

    private void updateSelection() {
        updateButtons();
        String id = selectedId();
        if (id == null) {
            preview.setIcon(null);
            preview.setText("Select an image");
            return;
        }
        preview.setIcon(null);
        preview.setText("Loading preview...");
        new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() {
                return ImageCacheManager.loadImage(ImageAssetReference.PREFIX + id);
            }
            @Override protected void done() {
                try {
                    BufferedImage image = get();
                    if (image == null) {
                        preview.setText("Preview unavailable");
                        return;
                    }
                    int width = Math.max(1, preview.getWidth() - 18);
                    int height = Math.max(1, preview.getHeight() - 18);
                    double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
                    Image scaled = image.getScaledInstance(Math.max(1, (int) (image.getWidth() * scale)),
                            Math.max(1, (int) (image.getHeight() * scale)), Image.SCALE_SMOOTH);
                    preview.setText("");
                    preview.setIcon(new ImageIcon(scaled));
                } catch (Exception ex) {
                    preview.setText("Preview unavailable");
                }
            }
        }.execute();
    }

    private void purgeSelected() {
        String id = selectedId();
        if (id == null) return;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        String lifecycle = String.valueOf(model.getValueAt(row, 5));
        if (!"UNUSED".equals(lifecycle) && !"DELETE_PENDING".equals(lifecycle)) {
            JOptionPane.showMessageDialog(this, "Only images marked UNUSED can be permanently deleted.",
                    "Image Storage", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String filename = String.valueOf(model.getValueAt(row, 2));
        int answer = JOptionPane.showConfirmDialog(this,
                "Permanently delete \"" + filename + "\" from the server and its assigned cloud provider?\n\n"
                        + "SmartStock will check all references again before deletion.",
                "Permanently Delete Image", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        runAction("Deleting image...", () -> {
            LanApiClient.purgeImageAsset(id);
            return null;
        }, this::load);
    }

    private void setupOneDrive(){
        JTextField tenant=new JTextField(),client=new JTextField(),drive=new JTextField();
        JTextField certificate=new JTextField();certificate.setEditable(false);
        JTextField privateKey=new JTextField();privateKey.setEditable(false);
        JButton chooseCertificate=new JButton("Choose Certificate...");
        JButton choosePrivateKey=new JButton("Choose Private Key...");
        final Path[] certificatePath={null},privateKeyPath={null};
        chooseCertificate.addActionListener(e->{Path p=choosePem("Select the public certificate PEM");if(p!=null){certificatePath[0]=p;certificate.setText(p.getFileName().toString());}});
        choosePrivateKey.addActionListener(e->{Path p=choosePem("Select the PKCS#8 private key PEM");if(p!=null){privateKeyPath[0]=p;privateKey.setText(p.getFileName().toString());}});
        JPanel form=new JPanel(new GridLayout(0,2,8,8));
        form.add(new JLabel("Microsoft tenant ID"));form.add(tenant);
        form.add(new JLabel("Entra application/client ID"));form.add(client);
        form.add(new JLabel("OneDrive drive ID"));form.add(drive);
        form.add(chooseCertificate);form.add(certificate);
        form.add(choosePrivateKey);form.add(privateKey);
        JPanel wrapper=new JPanel(new BorderLayout(8,8));
        wrapper.add(new JLabel("<html>Configure this only on the store server. The Entra app needs the Microsoft Graph<br><b>application</b> permission Files.ReadWrite.AppFolder with administrator consent.</html>"),BorderLayout.NORTH);
        wrapper.add(form,BorderLayout.CENTER);
        if(JOptionPane.showConfirmDialog(this,wrapper,"Set Up OneDrive Image Storage",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        if(certificatePath[0]==null||privateKeyPath[0]==null){showError(new IllegalArgumentException("Select both the public certificate and PKCS#8 private key PEM files."));return;}
        runAction("Saving credentials and testing OneDrive...",()->{
            LanApiClient.configureOneDriveImages(tenant.getText().trim(),client.getText().trim(),drive.getText().trim(),
                    Files.readString(certificatePath[0]),Files.readString(privateKeyPath[0]));return null;
        },()->{JOptionPane.showMessageDialog(this,"OneDrive connection verified. You can now begin the copy.","OneDrive Image Storage",JOptionPane.INFORMATION_MESSAGE);load();});
    }

    private Path choosePem(String title){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(title);return chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION?chooser.getSelectedFile().toPath():null;}

    private <T> void runAction(String message, java.util.concurrent.Callable<T> action, Runnable success) {
        setBusy(true, message);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return action.call(); }
            @Override protected void done() {
                try {
                    get();
                    success.run();
                } catch (Exception ex) {
                    showError(ex);
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void updateButtons() {
        int row = table.getSelectedRow();
        boolean selected = row >= 0;
        String status = selected ? String.valueOf(model.getValueAt(table.convertRowIndexToModel(row), 5)) : "";
        keepButton.setEnabled(selected && ("UNUSED".equals(status) || "DELETE_PENDING".equals(status)));
        purgeButton.setEnabled(selected && ("UNUSED".equals(status) || "DELETE_PENDING".equals(status)));
    }

    private String selectedId() {
        int row = table.getSelectedRow();
        return row < 0 ? null : String.valueOf(model.getValueAt(table.convertRowIndexToModel(row), 0));
    }

    private void setBusy(boolean busy, String text) {
        table.setEnabled(!busy);
        if (text != null) summary.setText(text);
        updateButtons();
    }

    private void showError(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        summary.setText("Image storage operation failed.");
        JOptionPane.showMessageDialog(this, cause.getMessage(), "Image Storage", JOptionPane.ERROR_MESSAGE);
    }

    private static String formatDate(long epoch) {
        return epoch <= 0 ? "" : DATE.format(Instant.ofEpochMilli(epoch));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024d);
        return String.format("%.1f MB", bytes / (1024d * 1024d));
    }
}
