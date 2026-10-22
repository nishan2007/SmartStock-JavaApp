package services;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoyaltyPolicyTest {
    private static BigDecimal money(String s){return new BigDecimal(s);}
    private static LoyaltyPolicy.Settings enabled(){return new LoyaltyPolicy.Settings(1,true,1,money("100.00"),1,money("1.00"),100,money("20"));}
    @Test void earnsWholePointsOncePerDocument(){var s=enabled();assertEquals(0,LoyaltyPolicy.earn(s,money("99.99")));assertEquals(1,LoyaltyPolicy.earn(s,money("100")));assertEquals(90,LoyaltyPolicy.earn(s,money("9000")));assertEquals(0,LoyaltyPolicy.earn(LoyaltyPolicy.Settings.defaults(),money("10000")));assertEquals(0,money("1").compareTo(s.rewardPercent()));}
    @Test void preservesOriginalValuesAndConsumesOldestFirst(){UUID old=UUID.randomUUID(),newer=UUID.randomUUID();var r=LoyaltyPolicy.redeem(enabled(),150,money("2000"),List.of(new LoyaltyPolicy.Lot(old,100,money("1.00")),new LoyaltyPolicy.Lot(newer,100,money("2.00"))));assertEquals(money("200.00"),r.discount());assertEquals(100,r.portions().get(0).points());assertEquals(50,r.portions().get(1).points());}
    @Test void enforcesMinimumCapAndAvailability(){var lots=List.of(new LoyaltyPolicy.Lot(UUID.randomUUID(),500,money("1.00")));assertThrows(IllegalArgumentException.class,()->LoyaltyPolicy.redeem(enabled(),99,money("1000"),lots));assertThrows(IllegalArgumentException.class,()->LoyaltyPolicy.redeem(enabled(),201,money("1000"),lots));assertThrows(IllegalArgumentException.class,()->LoyaltyPolicy.redeem(enabled(),501,money("10000"),lots));assertEquals(money("200.00"),LoyaltyPolicy.redeem(enabled(),200,money("1000"),lots).discount());}
    @Test void rejectsInvalidSettings(){assertThrows(IllegalArgumentException.class,()->new LoyaltyPolicy.Settings(1,true,null,money("100"),1,money("1"),100,money("20")));assertThrows(IllegalArgumentException.class,()->new LoyaltyPolicy.Settings(1,true,1,money("0"),1,money("1"),100,money("20")));assertThrows(IllegalArgumentException.class,()->new LoyaltyPolicy.Settings(1,true,1,money("100"),1,money("0.001"),100,money("20")));}
    @Test void peerSignatureBindsOriginBodyAndTime(){String key="test-only-32-character-secret-value",body="{\"action\":\"SETTINGS\"}",time="10000";String signature=LanSecurity.hmacSha256(key,"2\n"+time+"\n"+body);assertTrue(LoyaltyPeer.verify(key,2,time,signature,body,10000));assertFalse(LoyaltyPeer.verify(key,3,time,signature,body,10000));assertFalse(LoyaltyPeer.verify(key,2,time,signature,"{}",10000));assertFalse(LoyaltyPeer.verify(key,2,time,signature,body,10121));assertFalse(LoyaltyPeer.verify(null,2,time,signature,body,10000));}
    @Test void allocationDoesNotLoseWholePoints(){long total=7;assertEquals(total,LoyaltyWorker.allocate(total,money("10"),money("30"))+(LoyaltyWorker.allocate(total,money("20"),money("30"))-LoyaltyWorker.allocate(total,money("10"),money("30")))+(LoyaltyWorker.allocate(total,money("30"),money("30"))-LoyaltyWorker.allocate(total,money("20"),money("30"))));}
    @Test void partialRefundsPreserveEveryCent(){BigDecimal sum=BigDecimal.ZERO;for(int returned=0;returned<3;returned++)sum=sum.add(LoyaltyCheckout.refundAmount(money("2900.00"),3,returned,1));assertEquals(money("2900.00"),sum);assertEquals(money("966.67"),LoyaltyCheckout.refundAmount(money("2900"),3,2,1));}
}
