package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;

/** Pure rules shared by online acceptance and staff fulfillment. */
public final class StorefrontPolicy {
    private StorefrontPolicy() { }
    public static BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP);}
    public static Instant deadline(Instant ready,int hours){
        if(hours<1||hours>8760)throw new IllegalArgumentException("Pickup expiry must be between 1 hour and 365 days.");
        return ready.plus(Duration.ofHours(hours));
    }
    public static void transition(String from,String to){
        Set<String> allowed=switch(from){
            case "CONFIRMED" -> Set.of("PREPARING","READY","CANCELLED","NEEDS_ATTENTION");
            case "PREPARING" -> Set.of("READY","CANCELLED","NEEDS_ATTENTION");
            case "READY" -> Set.of("COLLECTED","CANCELLED","EXPIRED","NEEDS_ATTENTION");
            case "NEEDS_ATTENTION" -> Set.of("PREPARING","CANCELLED");
            default -> Set.of();
        };
        if(!allowed.contains(to))throw new IllegalArgumentException("This order cannot move from "+from+" to "+to+".");
    }
    public static void quantity(int value){if(value<1||value>999)throw new IllegalArgumentException("Choose a quantity from 1 to 999.");}
    public static boolean reserves(String status){return Set.of("CONFIRMED","PREPARING","READY","NEEDS_ATTENTION").contains(status);}
}
