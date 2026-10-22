package gy.deckers.studio;

import java.math.BigDecimal;
import java.util.*;

final class BusinessCardSeries {
    static List<String> values(String start, String step, int count, int digits) {
        if (count < 1 || count > 10000 || digits < 0 || digits > 40)
            throw new IllegalArgumentException("Use 1–10000 rows and 0–40 minimum digits.");
        if (start.length() > 80 || step.length() > 80) throw new IllegalArgumentException("Number is too long.");
        BigDecimal first = new BigDecimal(start.trim()), increment = new BigDecimal(step.trim());
        if (Math.abs((long)first.scale()) > 40 || Math.abs((long)increment.scale()) > 40
                || first.precision() > 40 || increment.precision() > 40)
            throw new IllegalArgumentException("Use numbers with up to 40 digits and decimal places.");
        List<String> result = new ArrayList<>();
        for (int i=0;i<count;i++) {
            String value=first.add(increment.multiply(BigDecimal.valueOf(i))).stripTrailingZeros().toPlainString();
            boolean negative=value.startsWith("-");String absolute=negative?value.substring(1):value;
            int point=absolute.indexOf('.'), length=point<0?absolute.length():point;
            result.add((negative?"-":"")+"0".repeat(Math.max(0,digits-length))+absolute);
        }
        return result;
    }
}
