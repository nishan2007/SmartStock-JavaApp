package services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import services.EmployeeScheduleService.Assignment;
import services.EmployeeScheduleService.Holiday;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScheduleExportService {
    private static final int MARGIN = 48;
    private static final int HEADER_HEIGHT = 180;
    private static final int FOOTER_HEIGHT = 58;
    private static final int GAP = 12;
    private static final Color PAGE_BACKGROUND = new Color(241, 245, 249);
    private static final Color SUNDAY_BACKGROUND = new Color(242, 243, 246);
    private static final Color TEXT = new Color(23, 50, 77);
    private static final Color MUTED = new Color(83, 106, 130);
    private static final Color BORDER = new Color(201, 213, 226);
    private static final Color HOLIDAY = new Color(174, 28, 135);
    private static final Color[] EMPLOYEE_COLORS = {
            new Color(221, 105, 36), new Color(166, 47, 146), new Color(52, 134, 67),
            new Color(42, 105, 176), new Color(141, 92, 190), new Color(194, 65, 72)
    };
    private static final DateTimeFormatter PERIOD_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter HOUR_TIME = DateTimeFormatter.ofPattern("h a");
    private static final DateTimeFormatter GENERATED_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private ScheduleExportService() {
    }

    public static void writePng(File output, String storeName, LocalDate periodStart, LocalDate periodEnd,
                                int columns, boolean compact,
                                Map<LocalDate, List<Assignment>> assignments,
                                Map<LocalDate, Holiday> holidays) throws IOException {
        writePng(output, storeName, periodStart, periodEnd, columns, compact, assignments, holidays, null);
    }

    public static void writePng(File output, String storeName, LocalDate periodStart, LocalDate periodEnd,
                                int columns, boolean compact,
                                Map<LocalDate, List<Assignment>> assignments,
                                Map<LocalDate, Holiday> holidays,
                                BufferedImage companyLogo) throws IOException {
        BufferedImage image = render(storeName, periodStart, periodEnd, periodStart, periodEnd,
                columns, compact, assignments, holidays, companyLogo, null);
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG export support is unavailable.");
        }
    }

    public static void writePdf(File output, String storeName, LocalDate periodStart, LocalDate periodEnd,
                                int columns, boolean compact,
                                Map<LocalDate, List<Assignment>> assignments,
                                Map<LocalDate, Holiday> holidays) throws IOException {
        writePdf(output, storeName, periodStart, periodEnd, columns, compact, assignments, holidays, null);
    }

    public static void writePdf(File output, String storeName, LocalDate periodStart, LocalDate periodEnd,
                                int columns, boolean compact,
                                Map<LocalDate, List<Assignment>> assignments,
                                Map<LocalDate, Holiday> holidays,
                                BufferedImage companyLogo) throws IOException {
        int dayCount = (int) (periodEnd.toEpochDay() - periodStart.toEpochDay()) + 1;
        int pdfColumns = columns;
        int daysPerPage = dayCount;
        int pageCount = Math.max(1, (dayCount + daysPerPage - 1) / daysPerPage);
        PDRectangle landscapeLetter = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
        try (PDDocument document = new PDDocument()) {
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                LocalDate pageStart = periodStart.plusDays((long) pageIndex * daysPerPage);
                LocalDate pageEnd = pageStart.plusDays(daysPerPage - 1L);
                if (pageEnd.isAfter(periodEnd)) pageEnd = periodEnd;
                BufferedImage image = render(storeName, periodStart, periodEnd, pageStart, pageEnd,
                        pdfColumns, compact, assignments, holidays, companyLogo,
                        pageCount == 1 ? null : "Page " + (pageIndex + 1) + " of " + pageCount);
                addPdfPage(document, landscapeLetter, image);
            }
            document.save(output);
        }
    }

    private static void addPdfPage(PDDocument document, PDRectangle pageSize, BufferedImage image) throws IOException {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
        float margin = 24f;
        float availableWidth = pageSize.getWidth() - (margin * 2f);
        float availableHeight = pageSize.getHeight() - (margin * 2f);
        float scale = Math.min(availableWidth / image.getWidth(), availableHeight / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        float x = (pageSize.getWidth() - width) / 2f;
        float y = (pageSize.getHeight() - height) / 2f;
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.drawImage(pdfImage, x, y, width, height);
        }
    }

    private static BufferedImage render(String storeName, LocalDate fullStart, LocalDate fullEnd,
                                        LocalDate renderStart, LocalDate renderEnd,
                                        int columns, boolean compact,
                                        Map<LocalDate, List<Assignment>> assignments,
                                        Map<LocalDate, Holiday> holidays, BufferedImage companyLogo,
                                        String pageLabel) {
        if (columns == 7) return renderWeekly(storeName, fullStart, fullEnd, assignments, holidays, companyLogo);
        int tileWidth = 276;
        int offset = renderStart.getDayOfWeek().getValue() - 1;
        int dayCount = (int) (renderEnd.toEpochDay() - renderStart.toEpochDay()) + 1;
        int rowCount = (dayCount + offset + 6) / 7;
        List<Integer> rowHeights = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            int tallest = 210;
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col - offset;
                if (index < 0 || index >= dayCount) continue;
                LocalDate date = renderStart.plusDays(index);
                int required = 100;
                for (Assignment entry : assignments.getOrDefault(date, List.of())) required += assignmentHeight(entry, tileWidth - 52);
                Holiday holiday = holidays.get(date);
                if (holiday != null) required += wrapped(holiday.name(), tileWidth - 28, 20, true).size() * 26;
                tallest = Math.max(tallest, required);
            }
            rowHeights.add(tallest);
        }
        int width = MARGIN * 2 + 7 * tileWidth + 6 * GAP;
        int height = MARGIN + HEADER_HEIGHT + 60 + FOOTER_HEIGHT
                + rowHeights.stream().mapToInt(Integer::intValue).sum() + rowCount * GAP;
        BufferedImage image = image(width, height);
        Graphics2D g = graphics(image);
        try {
            drawHeader(g, storeName, fullStart, fullEnd, compact, companyLogo, pageLabel, width);
            int y = MARGIN + HEADER_HEIGHT;
            for (int col = 0; col < 7; col++) {
                int x = MARGIN + col * (tileWidth + GAP);
                g.setColor(TEXT); g.fillRect(x, y, tileWidth, 48);
                g.setFont(new Font("SansSerif", Font.BOLD, 23)); g.setColor(Color.WHITE);
                g.drawString(capitalize(java.time.DayOfWeek.of(col + 1).name()), x + 14, y + 32);
            }
            y += 60;
            for (int row = 0; row < rowCount; row++) {
                for (int col = 0; col < 7; col++) {
                    int index = row * 7 + col - offset;
                    if (index < 0 || index >= dayCount) continue;
                    LocalDate date = renderStart.plusDays(index);
                    drawDay(g, MARGIN + col * (tileWidth + GAP), y, tileWidth, rowHeights.get(row), date,
                            compact, assignments.getOrDefault(date, List.of()), holidays.get(date));
                }
                y += rowHeights.get(row) + GAP;
            }
            drawFooter(g, width, height);
        } finally { g.dispose(); }
        return image;
    }

    private static BufferedImage image(int width, int height) {
        if ((long) width * height > 24_000_000L) throw new IllegalArgumentException("Schedule is too large for one image. Export a shorter period.");
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        return g;
    }

    private static List<String> wrapped(String value, int width, int size, boolean bold) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scratch.createGraphics();
        try {
            g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
            List<String> lines = new ArrayList<>(); String line = "";
            for (char ch : (value == null ? "" : value).toCharArray()) {
                if (ch == '\n') { lines.add(line); line = ""; continue; }
                if (!line.isEmpty() && g.getFontMetrics().stringWidth(line + ch) > width) { lines.add(line); line = ""; }
                line += ch;
            }
            lines.add(line); return lines;
        } finally { g.dispose(); }
    }

    private static int drawWrapped(Graphics2D g, String value, int x, int y, int width, int size, boolean bold, Color color) {
        g.setColor(color); g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        List<String> lines = wrapped(value, width, size, bold);
        for (String line : lines) { g.drawString(line, x, y); y += size + 7; }
        return y;
    }

    private static String exportShift(Assignment a) {
        return a.shiftStartTime() == null || a.shiftEndTime() == null ? "Shift not assigned"
                : shortTime(a.shiftStartTime()) + " – " + shortTime(a.shiftEndTime());
    }

    private static int assignmentHeight(Assignment a, int width) {
        return 20 + wrapped(a.displayName(), width, 24, true).size() * 31
                + wrapped(exportShift(a), width, 22, true).size() * 29
                + wrapped(lunchLine(a), width, 19, false).size() * 26 + 12;
    }

    private static BufferedImage renderWeekly(String storeName, LocalDate start, LocalDate end,
            Map<LocalDate, List<Assignment>> assignments, Map<LocalDate, Holiday> holidays, BufferedImage logo) {
        int days = (int) (end.toEpochDay() - start.toEpochDay()) + 1, cell = 248, nameWidth = 260;
        Map<Integer, Assignment> employees = new java.util.TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1))
            for (Assignment a : assignments.getOrDefault(d, List.of())) employees.put(a.userId(), a);
        List<Assignment> people = new ArrayList<>(employees.values());
        people.sort(java.util.Comparator.comparing(a -> a.displayName() == null ? "" : a.displayName(), String.CASE_INSENSITIVE_ORDER));
        List<Integer> heights = new ArrayList<>();
        for (Assignment person : people) {
            int rowHeight = Math.max(140, 40 + wrapped(person.displayName(), nameWidth - 40, 30, true).size() * 37);
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                int needed = 30;
                for (Assignment a : assignments.getOrDefault(d, List.of())) if (a.userId() == person.userId())
                    needed += wrapped(exportShift(a), cell - 28, 23, true).size() * 30
                            + wrapped(lunchLine(a), cell - 28, 20, false).size() * 27 + 14;
                rowHeight = Math.max(rowHeight, needed);
            }
            heights.add(rowHeight);
        }
        int header = 80;
        for (Holiday holiday : holidays.values()) if (!holiday.holidayDate().isBefore(start) && !holiday.holidayDate().isAfter(end))
            header = Math.max(header, 50 + wrapped(holiday.name(), cell - 28, 18, true).size() * 25);
        int width = MARGIN * 2 + nameWidth + days * cell;
        int height = MARGIN + HEADER_HEIGHT + header + Math.max(150, heights.stream().mapToInt(Integer::intValue).sum()) + FOOTER_HEIGHT;
        BufferedImage image = image(width, height); Graphics2D g = graphics(image);
        try {
            drawHeader(g, storeName, start, end, false, logo, null, width);
            int y = MARGIN + HEADER_HEIGHT;
            g.setColor(new Color(7, 136, 151)); g.fillRect(MARGIN, y, width - 2 * MARGIN, header);
            drawWrapped(g, "Employee", MARGIN + 20, y + 45, nameWidth - 40, 28, true, Color.WHITE);
            for (int i = 0; i < days; i++) {
                LocalDate date = start.plusDays(i); int x = MARGIN + nameWidth + i * cell;
                drawWrapped(g, DateTimeFormatter.ofPattern("EEE d MMM").format(date), x + 14, y + 33, cell - 28, 23, true, Color.WHITE);
                if (holidays.containsKey(date)) drawWrapped(g, holidays.get(date).name(), x + 14, y + 61, cell - 28, 18, true, Color.WHITE);
            }
            y += header;
            if (people.isEmpty()) drawWrapped(g, "No employees scheduled for this period.", MARGIN + 20, y + 65, width - MARGIN * 2 - 40, 28, false, MUTED);
            for (int row = 0; row < people.size(); row++) {
                Assignment person = people.get(row); int h = heights.get(row);
                Color accent = EMPLOYEE_COLORS[Math.floorMod(person.userId(), EMPLOYEE_COLORS.length)];
                g.setColor(accent); g.fillRect(MARGIN, y, 8, h);
                drawWrapped(g, person.displayName(), MARGIN + 22, y + 46, nameWidth - 40, 30, true, TEXT);
                for (int i = 0; i < days; i++) {
                    int x = MARGIN + nameWidth + i * cell; LocalDate date = start.plusDays(i);
                    g.setColor(mix(Color.WHITE, accent, .10)); g.fillRect(x, y, cell, h);
                    g.setColor(BORDER); g.drawLine(x, y, x, y + h);
                    int cy = y + 40; boolean found = false;
                    for (Assignment a : assignments.getOrDefault(date, List.of())) if (a.userId() == person.userId()) {
                        found = true;
                        cy = drawWrapped(g, exportShift(a), x + 14, cy, cell - 28, 23, true, TEXT);
                        cy = drawWrapped(g, lunchLine(a), x + 14, cy, cell - 28, 20, false, MUTED) + 14;
                    }
                    if (!found) drawWrapped(g, "OFF", x + 14, y + 48, cell - 28, 25, true, MUTED);
                }
                g.setColor(BORDER); g.drawLine(MARGIN, y + h, width - MARGIN, y + h); y += h;
            }
            drawFooter(g, width, height);
        } finally { g.dispose(); }
        return image;
    }

    private static void drawHeader(Graphics2D g, String storeName, LocalDate start, LocalDate end,
                                   boolean compact, BufferedImage companyLogo, String pageLabel, int width) {
        if (companyLogo == null) {
            try (var in = ScheduleExportService.class.getResourceAsStream("/Images/Deckers.png")) {
                if (in != null) companyLogo = ImageIO.read(in);
            } catch (IOException ignored) { /* The schedule remains usable without a logo. */ }
        }
        if (companyLogo != null) {
            double scale = Math.min(340d / companyLogo.getWidth(), 125d / companyLogo.getHeight());
            g.drawImage(companyLogo, MARGIN, MARGIN - 12, (int) (companyLogo.getWidth() * scale), (int) (companyLogo.getHeight() * scale), null);
        }
        drawWrapped(g, "Employee Schedule", MARGIN + 392, MARGIN + 34, width - MARGIN * 2 - 392, 50, true, TEXT);
        int y = drawWrapped(g, storeName, MARGIN + 392, MARGIN + 85, width - MARGIN * 2 - 392, 29, true, new Color(7, 136, 151));
        drawWrapped(g, PERIOD_DATE.format(start) + " – " + PERIOD_DATE.format(end), MARGIN + 392, y + 8, width - MARGIN * 2 - 392, 25, false, TEXT);
        g.setColor(new Color(7, 136, 151)); g.fillRect(MARGIN, MARGIN + HEADER_HEIGHT - 16, width - MARGIN * 2, 4);
    }

    private static void drawDay(Graphics2D g, int x, int y, int width, int height, LocalDate date,
                                boolean compact, List<Assignment> assignments, Holiday holiday) {
        g.setColor(date.getDayOfWeek().getValue() == 7 ? SUNDAY_BACKGROUND : PAGE_BACKGROUND);
        g.fillRect(x, y, width, height);
        int cy = drawWrapped(g, DateTimeFormatter.ofPattern("MMM d (EEE)").format(date), x + 14, y + 34, width - 28, 25, true, TEXT) + 8;
        if (holiday != null) cy = drawWrapped(g, holiday.name(), x + 14, cy, width - 28, 20, true, HOLIDAY) + 8;
        if (assignments.isEmpty()) { drawWrapped(g, "Not scheduled", x + 14, cy + 20, width - 28, 22, false, MUTED); return; }
        for (Assignment a : assignments) {
            Color accent = EMPLOYEE_COLORS[Math.floorMod(a.userId(), EMPLOYEE_COLORS.length)];
            int h = assignmentHeight(a, width - 52) - 12;
            g.setColor(mix(Color.WHITE, accent, .10)); g.fillRoundRect(x + 10, cy, width - 20, h, 8, 8);
            g.setColor(accent); g.fillRect(x + 10, cy, 5, h);
            int ty = drawWrapped(g, a.displayName(), x + 24, cy + 28, width - 52, 24, true, TEXT);
            ty = drawWrapped(g, exportShift(a), x + 24, ty, width - 52, 22, true, TEXT);
            drawWrapped(g, lunchLine(a), x + 24, ty, width - 52, 19, false, MUTED);
            cy += h + 12;
        }
    }

    private static void drawFooter(Graphics2D g, int width, int height) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        g.setColor(MUTED);
        String footer = "SmartStock  |  Generated " + GENERATED_TIME.format(LocalDateTime.now())
                + "  |  OFF = Not scheduled";
        g.drawString(fit(footer, g.getFontMetrics(), width - MARGIN * 2), MARGIN, height - 34);
        g.setFont(new Font("SansSerif", Font.BOLD, 19));
        g.setColor(TEXT);
        String changeNotice = "Schedule is subject to change. Please check for updates before each shift.";
        g.drawString(fit(changeNotice, g.getFontMetrics(), width - MARGIN * 2), MARGIN, height - 16);
    }

    private static String lunchLine(Assignment assignment) {
        if (assignment.lunchStartTime() == null) return "Lunch not set";
        return "Lunch " + shortTime(assignment.lunchStartTime());
    }

    private static String shortTime(LocalTime time) {
        return (time.getMinute() == 0 ? HOUR_TIME : FULL_TIME).format(time);
    }

    private static String fit(String text, FontMetrics metrics, int maxWidth) {
        if (text == null) return "";
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String suffix = "...";
        int allowed = Math.max(0, maxWidth - metrics.stringWidth(suffix));
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) > allowed) end--;
        return text.substring(0, end).stripTrailing() + suffix;
    }

    private static String capitalize(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase();
    }

    private static Color mix(Color base, Color accent, double amount) {
        double keep = 1d - amount;
        return new Color(
                (int) Math.round(base.getRed() * keep + accent.getRed() * amount),
                (int) Math.round(base.getGreen() * keep + accent.getGreen() * amount),
                (int) Math.round(base.getBlue() * keep + accent.getBlue() * amount));
    }
}
