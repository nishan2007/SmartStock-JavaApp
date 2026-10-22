package publishing;

import java.util.*;

/** Deterministic, bounded mixed-orientation shelf packing, with exact physical dimensions. */
public final class SheetLayout {
  private SheetLayout() {}

  private static final double EPS = 1e-8;

  public static final class Settings {
    public double width = 210,
        height = 297,
        left = 5,
        right = 5,
        top = 5,
        bottom = 5,
        gapX = 2,
        gapY = 2,
        bleed;
    public double offsetX, offsetY, backOffsetX, backOffsetY;
    public boolean maximize = true, rotate = true, cropMarks, duplex, booklet;
    /** Place successive document pages in successive card slots. */
    public boolean mixPages;
    public String alignment = "CENTER", backMapping = "LONG_EDGE";
    public String positionOrder = "NATIVE", customPositionOrder = "";
    public boolean cutAndStack;
    public int quantity, skip;

    public void validate() {
      if(cutAndStack && (skip > 0 || mixPages || booklet))
        throw new IllegalArgumentException("Cut and stack requires separate pages with no skipped positions or booklet layout.");
      if (!Double.isFinite(
              width
                  + height
                  + left
                  + right
                  + top
                  + bottom
                  + gapX
                  + gapY
                  + bleed
                  + offsetX
                  + offsetY
                  + backOffsetX
                  + backOffsetY)
          || width <= 0
          || height <= 0
          || width > 2000
          || height > 2000
          || left < 0
          || right < 0
          || top < 0
          || bottom < 0
          || gapX < 0
          || gapY < 0
          || bleed < 0
          || left + right >= width
          || top + bottom >= height
          || quantity < 0
          || quantity > 100000
          || skip < 0
          || skip > 10000
          || !Set.of("CENTER", "TOP_LEFT").contains(alignment)
          || !Set.of("LONG_EDGE", "SHORT_EDGE", "SAME").contains(backMapping)
          || !SheetSequence.ORDERS.contains(positionOrder)
          || customPositionOrder == null)
        throw new IllegalArgumentException("Invalid sheet dimensions, margins, gaps, or quantity.");
    }
  }

  public record Placement(double x, double y, double width, double height, boolean rotated) {}

  public record Result(List<Placement> placements, double usedArea, double printableArea) {
    public int capacity() {
      return placements.size();
    }

    public int pageCount(int quantity, int skip) {
      if (capacity() == 0) return 0;
      return quantity == 0 ? 1 : (int) (((long) quantity + skip + capacity() - 1) / capacity());
    }
  }

  public static Result pack(double width, double height, Settings s) {
    s.validate();
    if (!Double.isFinite(width + height) || width <= 0 || height <= 0)
      throw new IllegalArgumentException("Invalid item size.");
    double w = width + 2 * s.bleed,
        h = height + 2 * s.bleed,
        aw = s.width - s.left - s.right,
        ah = s.height - s.top - s.bottom;
    List<Placement> best = grid(w, h, aw, ah, s.gapX, s.gapY, false);
    if (s.maximize && s.rotate) {
      best = better(best, grid(h, w, aw, ah, s.gapX, s.gapY, true));
      // Every feasible split between upright and rotated shelves, in both axes.
      int rows = count(ah, h, s.gapY);
      if (rows > 10000)
        throw new IllegalArgumentException("Too many layout rows; increase item dimensions.");
      for (int n = 0; n <= rows; n++) {
        if (Thread.currentThread().isInterrupted())
          throw new java.util.concurrent.CancellationException("Layout cancelled.");
        double used = n == 0 ? 0 : n * h + n * s.gapY;
        List<Placement> mixed = grid(w, h, aw, Math.max(0, used - s.gapY), s.gapX, s.gapY, false);
        for (Placement p : grid(h, w, aw, ah - used, s.gapX, s.gapY, true))
          mixed.add(new Placement(p.x, p.y + used, p.width, p.height, true));
        best = better(best, mixed);
      }
      int cols = count(aw, w, s.gapX);
      if (cols > 10000)
        throw new IllegalArgumentException("Too many layout columns; increase item dimensions.");
      for (int n = 0; n <= cols; n++) {
        double used = n == 0 ? 0 : n * w + n * s.gapX;
        List<Placement> mixed = grid(w, h, Math.max(0, used - s.gapX), ah, s.gapX, s.gapY, false);
        for (Placement p : grid(h, w, aw - used, ah, s.gapX, s.gapY, true))
          mixed.add(new Placement(p.x + used, p.y, p.width, p.height, true));
        best = better(best, mixed);
      }
    }
    double usedW = 0, usedH = 0;
    for (Placement p : best) {
      usedW = Math.max(usedW, p.x + p.width);
      usedH = Math.max(usedH, p.y + p.height);
    }
    double dx = s.left + (s.alignment.equals("CENTER") ? (aw - usedW) / 2 : 0),
        dy = s.top + (s.alignment.equals("CENTER") ? (ah - usedH) / 2 : 0);
    List<Placement> out = new ArrayList<>();
    for (Placement p : best)
      out.add(
          new Placement(
              p.x + dx + s.bleed,
              p.y + dy + s.bleed,
              p.width - 2 * s.bleed,
              p.height - 2 * s.bleed,
              p.rotated));
    return new Result(SheetSequence.arrange(out,s.positionOrder,s.customPositionOrder), out.size() * width * height, aw * ah);
  }

  private static int count(double space, double item, double gap) {
    return Math.max(0, (int) Math.floor((space + gap + EPS) / (item + gap)));
  }

  private static List<Placement> grid(
      double w, double h, double aw, double ah, double gx, double gy, boolean rotated) {
    int cols = count(aw, w, gx), rows = count(ah, h, gy);
    if ((long) cols * rows > 10000)
      throw new IllegalArgumentException(
          "More than 10000 copies per sheet; increase the item size.");
    List<Placement> out = new ArrayList<>();
    for (int y = 0; y < rows; y++)
      for (int x = 0; x < cols; x++)
        out.add(new Placement(x * (w + gx), y * (h + gy), w, h, rotated));
    return out;
  }

  private static List<Placement> better(List<Placement> a, List<Placement> b) {
    return b.size() > a.size() ? b : a;
  }

  public static Placement back(Placement p, Settings s) {
    return new Placement(
        s.backMapping.equals("LONG_EDGE") ? s.width - p.x - p.width : p.x,
        s.backMapping.equals("SHORT_EDGE") ? s.height - p.y - p.height : p.y,
        p.width,
        p.height,
        p.rotated);
  }

  public static List<int[]> booklet(int pages) {
    int padded = (pages + 3) / 4 * 4;
    List<int[]> out = new ArrayList<>();
    for (int i = 0; i < padded / 4; i++) {
      out.add(new int[] {valid(padded - 1 - 2 * i, pages), valid(2 * i, pages)});
      out.add(new int[] {valid(2 * i + 1, pages), valid(padded - 2 - 2 * i, pages)});
    }
    return out;
  }

  private static int valid(int n, int pages) {
    return n < pages ? n : -1;
  }
}
