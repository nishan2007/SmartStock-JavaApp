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
import java.awt.BasicStroke;
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
    private static final int HEADER_HEIGHT = 112;
    private static final int FOOTER_HEIGHT = 58;
    private static final int GAP = 12;
    private static final Color PAGE_BACKGROUND = new Color(246, 247, 249);
    private static final Color TILE_BACKGROUND = Color.WHITE;
    private static final Color SUNDAY_BACKGROUND = new Color(242, 243, 246);
    private static final Color HOLIDAY_BACKGROUND = new Color(255, 239, 250);
    private static final Color TEXT = new Color(29, 31, 36);
    private static final Color MUTED = new Color(92, 98, 108);
    private static final Color BORDER = new Color(202, 206, 214);
    private static final Color HOLIDAY = new Color(174, 28, 135);
    private static final Color[] EMPLOYEE_COLORS = {
            new Color(221, 105, 36), new Color(166, 47, 146), new Color(52, 134, 67),
            new Color(42, 105, 176), new Color(141, 92, 190), new Color(194, 65, 72)
    };
    private static final DateTimeFormatter PERIOD_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DAY_DATE = DateTimeFormatter.ofPattern("MMM d");
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
        int pdfColumns = Math.min(4, Math.max(1, columns));
        int daysPerPage = pdfColumns * 2;
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
        int safeColumns = Math.max(1, columns);
        int tileWidth = compact ? 220 : 250;
        int dayCount = (int) (renderEnd.toEpochDay() - renderStart.toEpochDay()) + 1;
        int rowCount = Math.max(1, (dayCount + safeColumns - 1) / safeColumns);
        List<Integer> rowHeights = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            int busiest = 0;
            for (int column = 0; column < safeColumns; column++) {
                LocalDate date = renderStart.plusDays((long) row * safeColumns + column);
                if (!date.isAfter(renderEnd)) {
                    busiest = Math.max(busiest, assignments.getOrDefault(date, List.of()).size());
                }
            }
            int entryHeight = compact ? 32 : 76;
            rowHeights.add(Math.max(compact ? 150 : 175, 92 + (busiest * entryHeight)));
        }
        int gridWidth = safeColumns * tileWidth + (safeColumns - 1) * GAP;
        int width = MARGIN * 2 + gridWidth;
        int height = MARGIN + HEADER_HEIGHT + FOOTER_HEIGHT + rowHeights.stream().mapToInt(Integer::intValue).sum()
                + Math.max(0, rowCount - 1) * GAP;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(PAGE_BACKGROUND);
            g.fillRect(0, 0, width, height);
            drawHeader(g, storeName, fullStart, fullEnd, compact, companyLogo, pageLabel, width);

            int y = MARGIN + HEADER_HEIGHT;
            for (int row = 0; row < rowCount; row++) {
                int rowHeight = rowHeights.get(row);
                for (int column = 0; column < safeColumns; column++) {
                    LocalDate date = renderStart.plusDays((long) row * safeColumns + column);
                    if (date.isAfter(renderEnd)) break;
                    int x = MARGIN + column * (tileWidth + GAP);
                    drawDay(g, x, y, tileWidth, rowHeight, date, compact,
                            assignments.getOrDefault(date, List.of()), holidays.get(date));
                }
                y += rowHeight + GAP;
            }
            drawFooter(g, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawHeader(Graphics2D g, String storeName, LocalDate start, LocalDate end,
                                   boolean compact, BufferedImage companyLogo, String pageLabel, int width) {
        g.setColor(TEXT);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString("Employee Schedule", MARGIN, MARGIN + 32);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(storeName, MARGIN, MARGIN + 61);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(MUTED);
        String period = PERIOD_DATE.format(start) + " - " + PERIOD_DATE.format(end)
                + "  |  " + (compact ? "Compact" : "Detailed") + " view";
        g.drawString(period, MARGIN, MARGIN + 86);
        drawCenteredLogo(g, companyLogo, width);
        if (pageLabel != null) {
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            int labelWidth = g.getFontMetrics().stringWidth(pageLabel);
            g.drawString(pageLabel, width - MARGIN - labelWidth, MARGIN + 32);
        }
    }

    private static void drawCenteredLogo(Graphics2D g, BufferedImage logo, int width) {
        if (logo == null || logo.getWidth() <= 0 || logo.getHeight() <= 0) return;
        int maxWidth = 220;
        int maxHeight = 68;
        double scale = Math.min((double) maxWidth / logo.getWidth(), (double) maxHeight / logo.getHeight());
        int drawWidth = Math.max(1, (int) Math.round(logo.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.round(logo.getHeight() * scale));
        int x = (width - drawWidth) / 2;
        int y = MARGIN + 3;
        Object previousInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(logo, x, y, drawWidth, drawHeight, null);
        if (previousInterpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previousInterpolation);
        }
    }

    private static void drawDay(Graphics2D g, int x, int y, int width, int height, LocalDate date,
                                boolean compact, List<Assignment> assignments, Holiday holiday) {
        boolean sunday = date.getDayOfWeek().getValue() == 7;
        g.setColor(holiday != null ? HOLIDAY_BACKGROUND : sunday ? SUNDAY_BACKGROUND : TILE_BACKGROUND);
        g.fillRoundRect(x, y, width, height, 12, 12);
        g.setColor(holiday != null ? HOLIDAY : BORDER);
        g.setStroke(new BasicStroke(holiday != null ? 2f : 1f));
        g.drawRoundRect(x, y, width, height, 12, 12);

        int left = x + 14;
        g.setColor(TEXT);
        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.drawString(capitalize(date.getDayOfWeek().name()), left, y + 25);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(MUTED);
        g.drawString(DAY_DATE.format(date), left, y + 46);

        String marker = holiday != null ? holiday.name() : sunday ? "Manual scheduling only" : null;
        if (marker != null) {
            g.setFont(new Font("SansSerif", holiday != null ? Font.BOLD : Font.ITALIC, 11));
            g.setColor(holiday != null ? HOLIDAY : MUTED);
            g.drawString(fit(marker, g.getFontMetrics(), width - 28), left, y + 65);
        }

        int contentY = y + 78;
        if (assignments.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.ITALIC, 12));
            g.setColor(MUTED);
            g.drawString(holiday == null ? "Not scheduled" : "Closed - manual scheduling only", left, contentY + 18);
            return;
        }
        int cardHeight = compact ? 26 : 68;
        int cardGap = compact ? 6 : 8;
        for (Assignment assignment : assignments) {
            drawAssignment(g, left, contentY, width - 28, cardHeight, assignment, compact);
            contentY += cardHeight + cardGap;
        }
    }

    private static void drawAssignment(Graphics2D g, int x, int y, int width, int height,
                                       Assignment assignment, boolean compact) {
        Color accent = EMPLOYEE_COLORS[Math.floorMod(assignment.userId(), EMPLOYEE_COLORS.length)];
        g.setColor(mix(Color.WHITE, accent, 0.10));
        g.fillRoundRect(x, y, width, height, 8, 8);
        g.setColor(accent);
        g.fillRoundRect(x, y, 5, height, 5, 5);
        int textX = x + 12;
        if (compact) {
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.setColor(TEXT);
            String shift = compactShift(assignment);
            FontMetrics metrics = g.getFontMetrics();
            int shiftWidth = metrics.stringWidth(shift);
            g.drawString(fit(assignment.displayName(), metrics, width - shiftWidth - 25), textX, y + 18);
            g.setColor(accent.darker());
            g.drawString(shift, x + width - shiftWidth - 8, y + 18);
            return;
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(TEXT);
        g.drawString(fit(assignment.displayName(), g.getFontMetrics(), width - 22), textX, y + 18);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(accent.darker());
        g.drawString(fit(shiftLine(assignment), g.getFontMetrics(), width - 22), textX, y + 37);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(MUTED);
        g.drawString(fit(lunchLine(assignment), g.getFontMetrics(), width - 22), textX, y + 55);
    }

    private static void drawFooter(Graphics2D g, int width, int height) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(MUTED);
        String footer = "SmartStock  |  Generated " + GENERATED_TIME.format(LocalDateTime.now())
                + "  |  Sundays and holidays are manual scheduling only";
        g.drawString(fit(footer, g.getFontMetrics(), width - MARGIN * 2), MARGIN, height - 34);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(TEXT);
        String changeNotice = "Schedule is subject to change. Please check for updates before each shift.";
        g.drawString(fit(changeNotice, g.getFontMetrics(), width - MARGIN * 2), MARGIN, height - 16);
    }

    private static String shiftLine(Assignment assignment) {
        if (assignment.shiftStartTime() == null || assignment.shiftEndTime() == null) return "Shift not assigned";
        String name = assignment.shiftName() == null || assignment.shiftName().isBlank() ? "Shift" : assignment.shiftName();
        return name + "  " + fullTime(assignment.shiftStartTime()) + " - " + fullTime(assignment.shiftEndTime());
    }

    private static String compactShift(Assignment assignment) {
        if (assignment.shiftStartTime() == null || assignment.shiftEndTime() == null) return "No shift";
        return shortTime(assignment.shiftStartTime()) + "-" + shortTime(assignment.shiftEndTime());
    }

    private static String lunchLine(Assignment assignment) {
        if (assignment.lunchStartTime() == null) return "Lunch not set";
        return "Lunch " + fullTime(assignment.lunchStartTime()) + " - "
                + fullTime(assignment.lunchStartTime().plusMinutes(EmployeeScheduleService.LUNCH_DURATION_MINUTES));
    }

    private static String fullTime(LocalTime time) {
        return FULL_TIME.format(time);
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
