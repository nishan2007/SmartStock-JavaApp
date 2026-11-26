package publishing;

/** Horizontal placement of text within the width of its box. */
public enum TextAlignment {
  LEFT("Left"), CENTER("Center"), RIGHT("Right");

  private final String label;

  TextAlignment(String label) {
    this.label = label;
  }

  public double offset(double width, double textWidth) {
    double remaining = Math.max(0, width - textWidth);
    return switch (this) {
      case LEFT -> 0;
      case CENTER -> remaining / 2;
      case RIGHT -> remaining;
    };
  }

  @Override
  public String toString() {
    return label;
  }
}
