package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Pure rules shared by the server and focused workflow tests. */
public final class OverseasOrderRules {
    private OverseasOrderRules() { }
    public static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new IllegalArgumentException("Enter a valid non-negative amount.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    public record Quantities(int quantity, int purchased, int shipped, int arrived, int collected) {
        public Quantities {
            if (quantity < 1 || quantity > 100000 || collected < 0 || collected > arrived || arrived > shipped
                    || shipped > purchased || purchased > quantity)
                throw new IllegalArgumentException("Quantities must follow purchased → shipped → arrived → collected, within the ordered quantity.");
        }
    }
    public static void requireCollectionBalance(List<Quantities> lines, BigDecimal balance) {
        if (lines.isEmpty()) throw new IllegalArgumentException("An order needs at least one item.");
        if (lines.stream().allMatch(l -> l.collected() == l.quantity()) && balance.signum() > 0)
            throw new IllegalArgumentException("Collect the full remaining balance before handing over the final items.");
    }
    public static BigDecimal total(List<BigDecimal> lineTotals, BigDecimal shipping) {
        return money(lineTotals.stream().map(OverseasOrderRules::money).reduce(money(shipping), BigDecimal::add));
    }
    public static String tracking(String state,BigDecimal paid,BigDecimal total,Quantities q){
        if("CANCELLED".equals(state))return "CANCELLED";
        if("DRAFT".equals(state))return "DRAFT";
        if(q.collected()==q.quantity())return "COLLECTED";
        if(q.arrived()==q.quantity())return "ARRIVED";
        if(q.arrived()>0)return "PARTIALLY_ARRIVED";
        if(q.purchased()==q.quantity())return "ORDERED";
        if(q.purchased()>0)return "PARTIALLY_ORDERED";
        return paid.signum()>0||total.signum()==0?"PROCESSING":"WAITING_FOR_PAYMENT";
    }
}
