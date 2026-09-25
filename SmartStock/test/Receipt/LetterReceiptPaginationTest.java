package Receipt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LetterReceiptPaginationTest {
    @Test
    void secondPageStartsImmediatelyAfterLogoReducedFirstPage() {
        var first = LetterReceiptPagination.slice(65, 720, 12, 92, 96, 0);
        var second = LetterReceiptPagination.slice(65, 720, 12, 92, 96, 1);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.startLine() + first.lineCount(), second.startLine());
        assertEquals(65, second.startLine() + second.lineCount());
        assertTrue(second.lastPage());
    }

    @Test
    void intermediatePagesDoNotReserveBarcodeSpace() {
        var first = LetterReceiptPagination.slice(170, 720, 12, 92, 96, 0);
        var middle = LetterReceiptPagination.slice(170, 720, 12, 92, 96, 1);
        var secondMiddle = LetterReceiptPagination.slice(170, 720, 12, 92, 96, 2);
        var last = LetterReceiptPagination.slice(170, 720, 12, 92, 96, 3);

        assertTrue(middle.lineCount() <= 60);
        assertTrue(secondMiddle.lineCount() <= 60);
        assertTrue(last.lineCount() <= 52);
        assertEquals(first.startLine() + first.lineCount(), middle.startLine());
        assertEquals(middle.startLine() + middle.lineCount(), secondMiddle.startLine());
        assertEquals(secondMiddle.startLine() + secondMiddle.lineCount(), last.startLine());
        assertEquals(170, last.startLine() + last.lineCount());
        assertTrue(last.lastPage());
        assertNull(LetterReceiptPagination.slice(170, 720, 12, 92, 96, 4));
    }
}
