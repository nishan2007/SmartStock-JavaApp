package gy.deckers.studio;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;

/** Local, read-only Excel connection. Headers are Publisher merge field names. */
final class BusinessCardMerge {
    record Data(List<String> headers, List<Map<String,String>> rows) {}
    static Data read(Path path, String sheetName) throws IOException {
        if (Files.size(path) > 20 * 1024 * 1024) throw new IOException("Excel file exceeds 20 MB.");
        try (Workbook book = WorkbookFactory.create(path.toFile(), null, true)) {
            Sheet sheet = book.getSheet(sheetName);
            if (sheet == null) throw new IOException("Worksheet no longer exists: " + sheetName);
            if (sheet.getLastRowNum() > 10000) throw new IOException("Use at most 10000 merge rows.");
            Row heading = sheet.getRow(sheet.getFirstRowNum());
            if (heading == null || heading.getLastCellNum() < 1 || heading.getLastCellNum() > 64)
                throw new IOException("The first row must contain 1–64 field names.");
            DataFormatter formatter = new DataFormatter(Locale.getDefault());
            formatter.setUseCachedValuesForFormulaCells(true);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < heading.getLastCellNum(); c++) {
                String key = formatter.formatCellValue(heading.getCell(c)).trim();
                validateHeader(key, headers); headers.add(key);
            }
            List<Map<String,String>> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row source = sheet.getRow(r);
                if (source == null) continue;
                Map<String,String> values = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++)
                    values.put(headers.get(c), formatter.formatCellValue(source.getCell(c)));
                if (values.values().stream().anyMatch(v -> !v.isBlank())) rows.add(values);
            }
            if (rows.isEmpty()) throw new IOException("Worksheet contains no merge records.");
            return new Data(List.copyOf(headers), List.copyOf(rows));
        } catch (RuntimeException ex) { throw new IOException("Cannot read Excel file: " + ex.getMessage(), ex); }
    }
    static void validateHeader(String key, List<String> existing) throws IOException {
        if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,39}") || existing.contains(key))
            throw new IOException("Use unique field names such as name, company or phone (letters, digits, underscores; up to 40 characters).");
    }
    static List<String> sheets(Path path) throws IOException {
        if (Files.size(path) > 20 * 1024 * 1024) throw new IOException("Excel file exceeds 20 MB.");
        try (Workbook book = WorkbookFactory.create(path.toFile(), null, true)) {
            List<String> names = new ArrayList<>();
            for (Sheet sheet : book) names.add(sheet.getSheetName());
            return names;
        }
    }
}
