package gy.deckers.studio;

import java.awt.Graphics2D;
import java.awt.Component;
import java.awt.print.*;
import java.util.Map;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.DialogTypeSelection;
import javax.print.PrintService;
import javax.swing.JOptionPane;
import publishing.*;

/** Actual-size banner pages sent through the local operating-system driver. */
final class BannerPrinting {
    private BannerPrinting() {}

    static void configureDialog(PrintRequestAttributeSet settings) {
        if (System.getProperty("os.name", "").startsWith("Windows"))
            settings.add(DialogTypeSelection.NATIVE);
    }

    static Pageable pages(Publication p, Printable artwork, PageFormat format) {
        Book book = new Book();
        int count = (int) p.pages.stream().filter(page -> !page.reference).count();
        book.append(artwork, format, count);
        return book;
    }

    static boolean showDialog(Component owner, Publication p, PrinterJob job, Printable artwork,
                              PrintRequestAttributeSet settings) throws PrinterException {
        configureDialog(settings);
        if (!System.getProperty("os.name", "").startsWith("Windows"))
            return job.printDialog(settings);
        PrintService[] printers = PrinterJob.lookupPrintServices();
        if (printers.length == 0) throw new PrinterException("No printers are installed.");
        String[] names = java.util.Arrays.stream(printers).map(PrintService::getName).toArray(String[]::new);
        String current = job.getPrintService() == null ? names[0] : job.getPrintService().getName();
        String name = (String) JOptionPane.showInputDialog(owner, "Choose the banner printer:",
                "Print banner", JOptionPane.PLAIN_MESSAGE, null, names, current);
        if (name == null) return false;
        for (PrintService printer : printers) if (printer.getName().equals(name)) job.setPrintService(printer);
        while (true) {
            PrintService printer = job.getPrintService();
            PageFormat requested = format(p);
            // Page Setup initializes the native DEVMODE with exact temporary
            // dimensions. PrintDialog alone only initializes a nearest named size.
            PageFormat chosen = job.pageDialog(requested);
            if (chosen == requested) return false;
            requireSize(p, chosen);
            job.setPageable(pages(p, artwork, chosen));
            if (settings.get(Media.class) instanceof MediaSizeName) settings.remove(Media.class);
            settings.remove(MediaPrintableArea.class);
            settings.add(chosen.getOrientation() == PageFormat.PORTRAIT
                    ? OrientationRequested.PORTRAIT : OrientationRequested.LANDSCAPE);
            if (!job.printDialog(settings)) return false;
            // A different printer has a different DEVMODE; initialize its size
            // too, rather than silently reverting to that printer's defaults.
            if (job.getPrintService().equals(printer)) return true;
        }
    }

    static PageFormat selectedFormat(Publication p, PrinterJob job, PrintRequestAttributeSet settings)
            throws PrinterException {
        // On Windows defaultPage reads the current native print DC, including
        // temporary custom media that has no Java MediaSizeName. Do not open a
        // second dialog or change the printer: either would discard that DC.
        // Capture before getPageFormat: validating its mapped standard media
        // can reset the native DC to that media and lose the temporary size.
        PageFormat nativePage = System.getProperty("os.name", "").startsWith("Windows")
                ? normalizeNativePage(job.defaultPage()) : null;
        // Do not ask Java to validate a mapped standard size when the native
        // job already supplies the real page: that changes its driver state.
        if (nativePage != null) return selectedFormat(p, null, nativePage, settings);
        PageFormat reported = job.getPageFormat(settings);
        return selectedFormat(p, reported, null, settings);
    }

    static PageFormat selectedFormat(Publication p, PageFormat reported,
                                    PageFormat nativePage, PrintRequestAttributeSet settings)
            throws PrinterException {
        if (nativePage != null) return useNativeFormat(p, nativePage, settings);
        requireSize(p, reported);
        return fullPage(reported, settings);
    }

    private static PageFormat useNativeFormat(Publication p, PageFormat actual,
                                             PrintRequestAttributeSet settings) throws PrinterException {
        requireSize(p, actual);
        // A stale Letter MediaSizeName would overwrite the native custom size
        // again when print(settings) applies attributes. Preserve tray, quality,
        // and other driver choices; only discard the stale page geometry.
        if (settings.get(Media.class) instanceof MediaSizeName) settings.remove(Media.class);
        settings.remove(MediaPrintableArea.class);
        // The native DEVMODE already contains the user's resolution choice.
        // WPrinterJob gives a Java PrinterResolution attribute precedence over
        // the live device resolution when building its raster transform. Do not
        // replay a dialog snapshot after the custom-page DC has been refreshed.
        settings.remove(javax.print.attribute.standard.PrinterResolution.class);
        settings.add(actual.getOrientation() == PageFormat.PORTRAIT
                ? OrientationRequested.PORTRAIT : actual.getOrientation() == PageFormat.REVERSE_LANDSCAPE
                ? OrientationRequested.REVERSE_LANDSCAPE : OrientationRequested.LANDSCAPE);
        return fullPage(actual, settings);
    }

    private static PageFormat fullPage(PageFormat page, PrintRequestAttributeSet settings) {
        // Dialog margins must not clip actual-size banner artwork. The device
        // still enforces its hardware limits; do not shrink or move the design.
        PageFormat result = (PageFormat) page.clone();
        Paper paper = result.getPaper();
        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        result.setPaper(paper);
        // Attributes use physical paper coordinates, before landscape rotation.
        settings.add(new MediaPrintableArea(0, 0,
                (float) (paper.getWidth() / 72), (float) (paper.getHeight() / 72),
                MediaPrintableArea.INCH));
        return result;
    }

    static PageFormat normalizeNativePage(PageFormat page) {
        Paper original = page.getPaper();
        if (original.getWidth() <= original.getHeight() || page.getOrientation() != PageFormat.PORTRAIT) return page;
        // Windows can return landscape DC dimensions as portrait Paper.
        // Keep the same visible page/margins but put the short edge across
        // the roll, otherwise a 72 x 44 banner would request a 72-inch roll.
        Paper portrait = new Paper();
        portrait.setSize(original.getHeight(), original.getWidth());
        portrait.setImageableArea(original.getImageableY(),
                original.getWidth()-original.getImageableX()-original.getImageableWidth(),
                original.getImageableHeight(), original.getImageableWidth());
        PageFormat result = (PageFormat) page.clone();
        result.setPaper(portrait);
        result.setOrientation(PageFormat.LANDSCAPE);
        return result;
    }

    private static boolean fitsEitherWay(Publication p, PageFormat page) {
        return fits(p.width, p.height, page) || fits(p.height, p.width, page);
    }

    static PageFormat format(Publication p) {
        Paper paper = new Paper();
        // Drivers expect physical paper in portrait coordinates. Express a wide
        // banner as landscape so its long edge is the roll feed, not roll width.
        paper.setSize(Math.min(p.width, p.height) * 72 / 25.4,
                Math.max(p.width, p.height) * 72 / 25.4);
        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(p.width > p.height ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        return format;
    }

    static void requireSize(Publication p, PageFormat format) throws PrinterException {
        if (!fitsEitherWay(p, format))
            throw new PrinterException(String.format(java.util.Locale.ROOT,
                    "Banner requires %.1f × %.1f mm; the printer returned %.1f × %.1f mm. "
                    + "Choose custom paper %.1f × %.1f mm with %s orientation in the driver. "
                    + "If the driver cannot support this size, export PDF for your print shop.",
                    p.width, p.height, format.getWidth() * 25.4 / 72, format.getHeight() * 25.4 / 72,
                    Math.min(p.width, p.height), Math.max(p.width, p.height),
                    p.width > p.height ? "landscape" : "portrait"));
    }

    private static boolean fits(double widthMm, double heightMm, PageFormat format) {
        return widthMm * 72 / 25.4 <= format.getWidth() + 2
                && heightMm * 72 / 25.4 <= format.getHeight() + 2;
    }

    static Printable printable(Publication p) {
        p.validate();
        PublicationPreflight.requireMergeData(p);
        if (!p.mergeRows.isEmpty()) throw new IllegalArgumentException("Clear merge records before printing a banner.");
        var pages = java.util.stream.IntStream.range(0, p.pages.size())
                .filter(i -> !p.pages.get(i).reference).toArray();
        if (pages.length == 0) throw new IllegalArgumentException("The banner has no printable pages.");
        PublicationRenderer renderer = new PublicationRenderer(p);
        return (graphics, format, index) -> {
            if (index < 0 || index >= pages.length) return Printable.NO_SUCH_PAGE;
            requireSize(p, format);
            Graphics2D draw = (Graphics2D) graphics.create();
            try {
                if (!fits(p.width, p.height, format)) {
                    draw.translate(p.height * 72 / 25.4, 0);
                    draw.rotate(Math.PI / 2);
                }
                draw.scale(72 / 25.4, 72 / 25.4);
                renderer.page(draw, pages[index], Map.of());
            } finally { draw.dispose(); }
            return Printable.PAGE_EXISTS;
        };
    }
}
