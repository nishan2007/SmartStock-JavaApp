package ui.screens;

import services.LanApiClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Product-by-product image coverage, derived from the shared photo review record. */
public final class ProductImageCoverageDialog extends JDialog {
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Product ID", "Product", "AI product photo", "AI photo file",
                    "Square ad (1:1)", "Square ad file", "Portrait ad (4:5)", "Portrait ad file",
                    "Story ad (9:16)", "Story ad file", "Landscape ad (16:9)", "Landscape ad file",
                    "Photo count", "Main photo file", "Gallery photo files"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Long.class : String.class; }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField(22);
    private final JComboBox<String> filter = new JComboBox<>(new String[]{
            "All products", "Needs AI photo", "Awaiting review", "Ads awaiting review", "Done", "Needs redo",
            "Needs square ad", "Needs portrait ad", "Needs story ad", "Needs landscape ad"});
    private final JLabel status = new JLabel("Loading image coverage…");
    private final JButton refresh = new JButton("Refresh");
    private final JTextArea photoDetails = new JTextArea("Select a product to see every main and gallery photo.");
    private final Map<Long, LanApiClient.StudioProduct> displayedProducts = new HashMap<>();

    public ProductImageCoverageDialog(Window owner) {
        super(owner, "Product Image Coverage", ModalityType.MODELESS);
        setSize(1150, 650);
        setMinimumSize(new Dimension(800, 400));
        setLocationRelativeTo(owner);
        table.setRowSorter(sorter);
        table.setAutoCreateRowSorter(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(95);
        table.getColumnModel().getColumn(1).setPreferredWidth(330);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(330);
        for (int column = 4; column < 12; column++)
            table.getColumnModel().getColumn(column).setPreferredWidth(column % 2 == 0 ? 170 : 330);
        table.getColumnModel().getColumn(12).setPreferredWidth(95);
        table.getColumnModel().getColumn(13).setPreferredWidth(330);
        table.getColumnModel().getColumn(14).setPreferredWidth(450);
        photoDetails.setEditable(false);
        photoDetails.setLineWrap(false);
        photoDetails.setRows(5);
        photoDetails.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelectedPhotos();
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Search:")); controls.add(search);
        controls.add(new JLabel("Show:")); controls.add(filter); controls.add(refresh);
        JButton ads = new JButton("Create / review ads…");
        controls.add(ads);
        JButton reviewAds=new JButton("Review pending ads…");controls.add(reviewAds);
        reviewAds.addActionListener(e->new ProductAdReviewDialog(this,this::load).setVisible(true));
        ads.addActionListener(e -> {
            int selected=table.getSelectedRow();
            if(selected<0) {status.setText("Select a product first.");return;}
            long id=(Long)model.getValueAt(table.convertRowIndexToModel(selected),0);
            new ProductAdDialog(this,displayedProducts.get(id),this::load).setVisible(true);
        });
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(controls, BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        JScrollPane photoList = new JScrollPane(photoDetails);
        photoList.setBorder(BorderFactory.createTitledBorder("All photos for selected product"));
        content.add(photoList, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        filter.addActionListener(e -> applyFilter());
        refresh.addActionListener(e -> load());
        load();
    }

    private void applyFilter() {
        String query = search.getText().trim();
        String selection = (String) filter.getSelectedItem();
        sorter.setRowFilter(new RowFilter<>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String id = String.valueOf(entry.getValue(0));
                String name = String.valueOf(entry.getValue(1));
                String photo = String.valueOf(entry.getValue(2));
                boolean matchesSearch = query.isEmpty() || Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)
                        .matcher(id + " " + name).find();
                return matchesSearch && ("All products".equals(selection) ||
                        ("Ads awaiting review".equals(selection) && java.util.stream.IntStream.of(4,6,8,10)
                                .anyMatch(column -> String.valueOf(entry.getValue(column)).contains("Awaiting ad review"))) ||
                        ("Needs AI photo".equals(selection) && "Needs AI photo".equals(photo)) ||
                        ("Awaiting review".equals(selection) && "Awaiting review".equals(photo)) ||
                        ("Done".equals(selection) && photo.startsWith("Done")) ||
                        ("Needs redo".equals(selection) && ("Needs redo".equals(photo) ||
                                "Photo no longer in gallery".equals(photo))) ||
                        ("Needs square ad".equals(selection) && !String.valueOf(entry.getValue(4)).startsWith("Done")) ||
                        ("Needs portrait ad".equals(selection) && !String.valueOf(entry.getValue(6)).startsWith("Done")) ||
                        ("Needs story ad".equals(selection) && !String.valueOf(entry.getValue(8)).startsWith("Done")) ||
                        ("Needs landscape ad".equals(selection) && !String.valueOf(entry.getValue(10)).startsWith("Done")));
            }
        });
    }

    private void load() {
        refresh.setEnabled(false);
        status.setText("Loading image coverage from the store server…");
        new SwingWorker<Coverage, Void>() {
            @Override protected Coverage doInBackground() throws Exception {
                Map<Long, LanApiClient.StudioReview> reviews = new HashMap<>();
                for (var review : LanApiClient.studioReviews()) reviews.put(review.productId(), review);
                var snapshot = LanApiClient.imageCoverage();
                Map<Long, Map<String,LanApiClient.ImageCoverage>> ads = new HashMap<>();
                for (var ad : snapshot.images())
                    ads.computeIfAbsent(ad.productId(), ignored -> new HashMap<>()).put(ad.kind(), ad);
                Map<Long,Map<String,String>> drafts=new HashMap<>();
                for(var draft:snapshot.drafts())drafts.computeIfAbsent(draft.productId(),ignored->new HashMap<>()).put(draft.kind(),draft.decision());
                return new Coverage(snapshot.products(), reviews, ads,drafts);
            }
            @Override protected void done() {
                refresh.setEnabled(true);
                try {
                    Coverage coverage = get();
                    model.setRowCount(0);
                    displayedProducts.clear();
                    int done = 0, pending = 0, redo = 0, needs = 0;
                    for (var product : coverage.products) {
                        displayedProducts.put(product.productId(), product);
                        var review = coverage.reviews.get(product.productId());
                        String photo = photoStatus(product, review);
                        if (photo.startsWith("Done")) done++;
                        else if ("Awaiting review".equals(photo)) pending++;
                        else if ("Needs redo".equals(photo) || "Photo no longer in gallery".equals(photo)) redo++;
                        else needs++;
                        String file = review != null && review.generatedForReview() && review.reference() != null
                                ? review.reference() : "—";
                        Map<String,LanApiClient.ImageCoverage> ads = coverage.ads.getOrDefault(product.productId(), Map.of());
                        Map<String,String> drafts=coverage.drafts.getOrDefault(product.productId(),Map.of());
                        Object[] row = {product.productId(), product.name(), photo, file,
                                adReviewStatus(product, ads.get("ad_square"),drafts.get("ad_square")), adFile(ads.get("ad_square")),
                                adReviewStatus(product, ads.get("ad_portrait"),drafts.get("ad_portrait")), adFile(ads.get("ad_portrait")),
                                adReviewStatus(product, ads.get("ad_story"),drafts.get("ad_story")), adFile(ads.get("ad_story")),
                                adReviewStatus(product, ads.get("ad_landscape"),drafts.get("ad_landscape")), adFile(ads.get("ad_landscape")),
                                photoCount(product), photoReference(product.imageUrl()),
                                product.additionalImageUrls() == null || product.additionalImageUrls().isEmpty()
                                        ? "—" : String.join(" · ", product.additionalImageUrls())};
                        model.addRow(row);
                    }
                    applyFilter();
                    showSelectedPhotos();
                    status.setText(coverage.products.size() + " active products · " + done +
                            " AI photos done · " + pending + " awaiting review · " + redo +
                            " need redo · " + needs + " need AI photos · " +
                            coverage.products.stream().mapToInt(ProductImageCoverageDialog::photoCount).sum() +
                            " main/gallery photos · Select a product to see every photo");
                } catch (Exception error) {
                    status.setText("Could not load image coverage: " + error.getMessage());
                }
            }
        }.execute();
    }

    private void showSelectedPhotos() {
        int selected = table.getSelectedRow();
        if (selected < 0 || selected >= table.getRowCount()) {
            photoDetails.setText("Select a product to see every main and gallery photo.");
            return;
        }
        long id = (Long) model.getValueAt(table.convertRowIndexToModel(selected), 0);
        var product = displayedProducts.get(id);
        if (product == null) return;
        StringBuilder details = new StringBuilder("Product #").append(id).append(" · ").append(product.name()).append('\n');
        details.append("Main: ").append(photoReference(product.imageUrl())).append('\n');
        var gallery = product.additionalImageUrls();
        if (gallery == null || gallery.isEmpty()) details.append("Gallery: —");
        else for (int i = 0; i < gallery.size(); i++)
            details.append("Gallery ").append(i + 1).append(": ").append(gallery.get(i)).append('\n');
        photoDetails.setText(details.toString());
        photoDetails.setCaretPosition(0);
    }

    static int photoCount(LanApiClient.StudioProduct product) {
        return (product.imageUrl() == null || product.imageUrl().isBlank() ? 0 : 1)
                + (product.additionalImageUrls() == null ? 0 : product.additionalImageUrls().size());
    }

    private static String photoReference(String reference) {
        return reference == null || reference.isBlank() ? "—" : reference;
    }

    static String photoStatus(LanApiClient.StudioProduct product, LanApiClient.StudioReview review) {
        if (review == null) return "Needs AI photo";
        return switch (review.decision()) {
            case "imported" -> {
                if (!review.generatedForReview() || review.reference() == null) yield "Needs AI photo";
                if (review.reference().equals(product.imageUrl())) yield "Done · Main photo";
                if (product.additionalImageUrls() != null && product.additionalImageUrls().contains(review.reference()))
                    yield "Done · Additional photo";
                yield "Photo no longer in gallery";
            }
            case "approved", "import_failed" -> "Awaiting review";
            case "rejected", "failed" -> "Needs redo";
            default -> "Awaiting review";
        };
    }

    private static String adFile(LanApiClient.ImageCoverage ad) {
        return ad == null ? "—" : ad.reference() + " (" + ad.width() + "×" + ad.height() + ")";
    }

    static String adReviewStatus(LanApiClient.StudioProduct product,LanApiClient.ImageCoverage ad,String decision) {
        String approved=adStatus(product,ad);
        if("pending".equals(decision))return approved+" · Awaiting ad review";
        if("rejected".equals(decision))return approved+" · Draft rejected";
        return approved;
    }

    private static String adStatus(LanApiClient.StudioProduct product, LanApiClient.ImageCoverage ad) {
        if (ad == null) return "Needs ad";
        String reference = ad.reference();
        if (reference.equals(product.imageUrl())) return "Done · Main photo";
        if (product.additionalImageUrls() != null && product.additionalImageUrls().contains(reference))
            return "Done · Additional photo";
        return "Photo no longer in gallery";
    }

    private record Coverage(List<LanApiClient.StudioProduct> products,
                            Map<Long, LanApiClient.StudioReview> reviews,
                            Map<Long, Map<String,LanApiClient.ImageCoverage>> ads,
                            Map<Long,Map<String,String>> drafts) { }
}
