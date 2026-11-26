package gy.deckers.studio.pdfeditor;

import java.util.LinkedHashSet;

/** User-facing page numbers are one based; results are zero based and ordered. */
public final class PageRanges {
  private PageRanges() {}

  public static int[] parse(String value, int count) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Enter page numbers or ranges.");
    var pages = new LinkedHashSet<Integer>();
    for (String part : value.replace('–', '-').split(",", -1)) {
      String[] ends = part.trim().split("-", -1);
      try {
        if (ends.length > 2) throw new NumberFormatException();
        int first = Integer.parseInt(ends[0].trim());
        int last = ends.length == 2 ? Integer.parseInt(ends[1].trim()) : first;
        if (first < 1 || last < first || last > count) throw new NumberFormatException();
        for (int i = first; i <= last; i++) pages.add(i - 1);
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
            "Use valid pages from 1 to " + count + ", for example 1–3, 5.");
      }
    }
    return pages.stream().mapToInt(Integer::intValue).toArray();
  }
}
