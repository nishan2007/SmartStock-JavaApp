package Receipt;

final class LetterReceiptPagination {
    private LetterReceiptPagination() {
    }

    static PageSlice slice(int lineCount, int imageableHeight, int lineHeight,
                           int firstPageReservedHeight, int lastPageReservedHeight,
                           int pageIndex) {
        int standardCapacity = capacity(imageableHeight, lineHeight);
        int firstCapacity = capacity(imageableHeight - firstPageReservedHeight, lineHeight);
        int firstAndLastCapacity = capacity(
                imageableHeight - firstPageReservedHeight - lastPageReservedHeight, lineHeight);
        int lastCapacity = capacity(imageableHeight - lastPageReservedHeight, lineHeight);

        if (lineCount <= firstAndLastCapacity) {
            return pageIndex == 0 ? new PageSlice(0, lineCount, true) : null;
        }

        int firstCount = Math.min(lineCount, firstCapacity);
        int remaining = lineCount - firstCount;
        int pagesAfterFirst = remaining <= lastCapacity
                ? 1
                : 1 + divideRoundingUp(remaining - lastCapacity, standardCapacity);
        int totalPages = 1 + pagesAfterFirst;
        if (pageIndex < 0 || pageIndex >= totalPages) {
            return null;
        }
        if (pageIndex == 0) {
            return new PageSlice(0, firstCount, false);
        }

        int start = firstCount;
        int linesRemaining = remaining;
        for (int currentPage = 1; currentPage < totalPages; currentPage++) {
            int pagesRemaining = totalPages - currentPage;
            boolean lastPage = pagesRemaining == 1;
            int count = lastPage
                    ? linesRemaining
                    : Math.min(standardCapacity, divideRoundingUp(linesRemaining, pagesRemaining));
            if (currentPage == pageIndex) {
                return new PageSlice(start, count, lastPage);
            }
            start += count;
            linesRemaining -= count;
        }
        return null;
    }

    private static int capacity(int availableHeight, int lineHeight) {
        return Math.max(availableHeight / Math.max(lineHeight, 1), 1);
    }

    private static int divideRoundingUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    record PageSlice(int startLine, int lineCount, boolean lastPage) {
    }
}
