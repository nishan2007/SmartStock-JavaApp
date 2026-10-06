package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderDesignServiceTest {
    private static BigDecimal number(String value){return value==null?null:new BigDecimal(value);}

    @Test void variantOverridesItemAndOrderMeasurementsWhenAreaOptionIsOff()throws Exception{
        var size=CustomOrderDesignService.resolveDimensions(true,false,number("40"),number("30"),"CM",
                number("24"),number("36"),"IN",number("18"),number("24"),"IN");
        assertEquals(number("18"),size.width());assertEquals(number("24"),size.height());assertEquals("IN",size.unit());
    }

    @Test void areaOptionUsesOrderDimensions()throws Exception{
        var size=CustomOrderDesignService.resolveDimensions(true,true,number("40"),number("30"),"CM",
                number("24"),number("36"),"IN",number("18"),number("24"),"IN");
        assertEquals(number("40"),size.width());assertEquals(number("30"),size.height());assertEquals("CM",size.unit());
    }

    @Test void incompleteVariantInheritsItemAndLegacyItemGetsUsableCanvas()throws Exception{
        var inherited=CustomOrderDesignService.resolveDimensions(false,false,null,null,null,
                number("12"),number("16"),"IN",null,null,null);
        assertEquals(number("12"),inherited.width());
        var legacy=CustomOrderDesignService.resolveDimensions(false,false,null,null,null,null,null,null,null,null,null);
        assertEquals(number("8.5"),legacy.width());assertEquals(number("11"),legacy.height());
    }

    @Test void invalidMeasurementsFailBeforeOrderCommit(){
        assertThrows(SQLException.class,()->CustomOrderDesignService.resolveDimensions(true,true,number("0"),number("10"),"IN",null,null,null,null,null,null));
    }
}
