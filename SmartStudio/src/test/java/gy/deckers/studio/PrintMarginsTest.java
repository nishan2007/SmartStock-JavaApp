package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class PrintMarginsTest {
    private PrintService driver(double minimum,boolean reportArea){
        return (PrintService)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PrintService.class},(proxy,method,args)->{
            AttributeSet query=(AttributeSet)args[2];
            assertNull(query.get(MediaPrintableArea.class),"Inherited margins must not restrict capability discovery");
            if(method.getName().equals("getSupportedAttributeValues"))return reportArea?new MediaPrintableArea[]{new MediaPrintableArea(12.7f,12.7f,190.5f,254f,MediaPrintableArea.MM)}:null;
            if(method.getName().equals("isAttributeValueSupported"))return ((MediaPrintableArea)args[0]).getX(MediaPrintableArea.MM)>=minimum-.001;
            throw new AssertionError(method.getName());
        });
    }
    private MediaPrintableArea request(PrintService service){
        var settings=new HashPrintRequestAttributeSet();settings.add(new MediaPrintableArea(25.4f,25.4f,165.1f,228.6f,MediaPrintableArea.MM));
        return PrintMargins.requestedArea(215.9,279.4,service,settings);
    }
    @Test void selectsBorderlessEvenWhenDriverOnlyAdvertisesDefault(){
        assertEquals(0,request(driver(0,true)).getX(MediaPrintableArea.MM));
    }
    @Test void requestsPointTwoInchWhenBorderlessIsUnavailable(){
        assertEquals(.2,request(driver(5.08,true)).getX(MediaPrintableArea.INCH),.0001);
    }
    @Test void respectsHardwareMinimumWhenSmallerMarginsAreRejected(){
        assertEquals(.5,request(driver(12.7,true)).getX(MediaPrintableArea.INCH),.0001);
    }
    @Test void missingCapabilitiesRequestsFullSheetForDriverValidation(){
        assertEquals(0,request(driver(99,false)).getX(MediaPrintableArea.MM));
        assertEquals(0,request(null).getX(MediaPrintableArea.MM));
    }
}
