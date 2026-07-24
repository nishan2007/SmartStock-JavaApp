package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceSheetExportServiceTest {
    @TempDir Path tempDir;

    @Test void exportsCompletePngAndPaginatedPdf() throws Exception {
        BalanceSheetService.SheetLine line = new BalanceSheetService.SheetLine("Cash sales", new BigDecimal("1250.50"));
        BalanceSheetService.BalanceSheet sheet = new BalanceSheetService.BalanceSheet(7L,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20), LocalDateTime.of(2026, 7, 20, 17, 30),
                "Test User", "Reviewed and approved", List.of(line), List.of(line), List.of(line), List.of(line),
                List.of(line), List.of(line), List.of(line), List.of(line), List.of(line),
                List.of(new BalanceSheetService.BankTransactionLine("Payroll", "OUT", new BigDecimal("400"))),
                List.of(new BalanceSheetService.ChequeDepositOption("SALE", "1", LocalDateTime.now(), "Sale 1", "Customer", "CHK-1", new BigDecimal("90"))),
                List.of(line), new BigDecimal("800"), new BigDecimal("100"), new BigDecimal("1250.50"),
                new BigDecimal("1250.50"), new BigDecimal("1250.50"), new BigDecimal("1250.50"), new BigDecimal("900"));
        File png = tempDir.resolve("balance-sheet.png").toFile();
        File pdf = tempDir.resolve("balance-sheet.pdf").toFile();

        BalanceSheetExportService.writePng(png, sheet);
        BalanceSheetExportService.writePdf(pdf, sheet);

        String reviewDirectory = System.getProperty("balanceSheetExport.reviewDirectory");
        if (reviewDirectory != null) {
            java.nio.file.Files.copy(png.toPath(), Path.of(reviewDirectory, "balance-sheet.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(pdf.toPath(), Path.of(reviewDirectory, "balance-sheet.pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        assertTrue(png.length() > 10_000); assertEquals(1600, ImageIO.read(png).getWidth());
        assertTrue(pdf.length() > 10_000); assertEquals('%', (char) java.nio.file.Files.readAllBytes(pdf.toPath())[0]);
    }
}
