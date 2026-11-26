package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.CustomOrderPricingService;
import services.CustomOrderPricingService.*;
import services.CustomOrderDataService.PrintAddonRequest;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderPricingGroupsTest {
    private Snapshot unit(long id,String text,int rate) {
        return new Snapshot(id,1L,2L,"Shirt","Small","FIXED",null,null,null,
            BigDecimal.valueOf(rate),BigDecimal.valueOf(rate),null,null,false,
            List.of(new PrintAddonRequest(3L,"Vinyl",4L,"Chest","FIXED_PRESET",text,1,BigDecimal.TEN)));
    }
    @Test void tenIdenticalUnitsShareOneGroupAndStillChargeForTen() {
        List<Snapshot> lines=new ArrayList<>();for(int i=0;i<10;i++)lines.add(unit(100+i,"Logo",100));
        var groups=CustomOrderPricingDialog.groups(lines);
        assertEquals(1,groups.size());assertEquals(10,groups.get(0).size());
        BigDecimal total=BigDecimal.ZERO;Set<Long> ids=new HashSet<>();
        for(int i=0;i<lines.size();i++){
            assertEquals(0,CustomOrderPricingDialog.rowForUnit(groups,i));
            var saved=lines.get(i);ids.add(saved.lineId());
            var input=new PricingLine(saved.lineId(),null,null,BigDecimal.valueOf(100),saved.addons(),BigDecimal.ZERO,"",null,"",null);
            total=total.add(CustomOrderPricingService.calculate(saved,input,BigDecimal.ZERO,false).total());
        }
        assertEquals(10,ids.size());assertEquals(new BigDecimal("1100"),total);
    }
    @Test void distinctTextAndRatesRemainSeparateAndNonAdjacentUnitsMapCorrectly() {
        var groups=CustomOrderPricingDialog.groups(List.of(unit(1,"Logo",100),unit(2,"Alice",100),unit(3,"Logo",100),unit(4,"Logo",120)));
        assertEquals(List.of(List.of(0,2),List.of(1),List.of(3)),groups);
        assertEquals(0,CustomOrderPricingDialog.rowForUnit(groups,2));
        assertEquals(2,CustomOrderPricingDialog.rowForUnit(groups,3));
        assertThrows(IllegalArgumentException.class,()->CustomOrderPricingDialog.rowForUnit(groups,4));
    }
}
