package ui.helpers;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

/** Keeps either a percentage or a discounted unit price authoritative while editing. */
public final class DiscountPriceFields {
    private final Supplier<BigDecimal> original;
    private final JTextField percent, discounted;
    private boolean updating, priceMode;
    public DiscountPriceFields(Supplier<BigDecimal> original, JTextField percent, JTextField discounted) {
        this.original=original; this.percent=percent; this.discounted=discounted;
        listen(percent,()->{ if (!updating) {priceMode=false;refresh();} });
        listen(discounted,()->{ if (!updating) {priceMode=true;refresh();} });
        refresh();
    }
    public void refresh() {
        if (updating) return;
        updating=true;
        try {
            BigDecimal base=original.get();
            if (priceMode) percent.setText(percentage(base,new BigDecimal(discounted.getText().trim())).stripTrailingZeros().toPlainString());
            else discounted.setText(discountedPrice(base,new BigDecimal(percent.getText().trim())).stripTrailingZeros().toPlainString());
        } catch (RuntimeException ignored) { /* Allow incomplete input while typing; validate on save. */ }
        finally {updating=false;}
    }
    public BigDecimal validatedPercent() {
        BigDecimal base=original.get();
        BigDecimal result=priceMode?percentage(base,validatePrice(new BigDecimal(discounted.getText().trim()))):new BigDecimal(percent.getText().trim());
        discountedPrice(base,result);
        return result;
    }
    private static BigDecimal validatePrice(BigDecimal value) {
        try { return value.setScale(2,RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Discounted price can have up to two decimal places."); }
    }
    public static BigDecimal percentage(BigDecimal original,BigDecimal discounted) {
        if (original.signum()<0 || discounted.signum()<0 || discounted.compareTo(original)>0)
            throw new IllegalArgumentException("Discounted price must be between zero and the original price.");
        if (original.signum()==0) return BigDecimal.ZERO;
        return original.subtract(discounted).multiply(BigDecimal.valueOf(100)).divide(original,4,RoundingMode.HALF_UP);
    }
    public static BigDecimal discountedPrice(BigDecimal original,BigDecimal percent) {
        if (original.signum()<0 || percent.signum()<0 || percent.compareTo(BigDecimal.valueOf(100))>0)
            throw new IllegalArgumentException("Enter a non-negative original price and a discount between 0 and 100%.");
        return original.multiply(BigDecimal.ONE.subtract(percent.divide(BigDecimal.valueOf(100)))).setScale(2,RoundingMode.HALF_UP);
    }
    public static void listen(JTextField field,Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {action.run();}
            public void removeUpdate(DocumentEvent e) {action.run();}
            public void changedUpdate(DocumentEvent e) {action.run();}
        });
    }
}
