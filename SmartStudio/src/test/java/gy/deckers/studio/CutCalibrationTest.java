package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CutCalibrationTest {
    @Test void correctsUniformScaleFromBothMeasuredAxes(){
        assertEquals(40,CutCalibration.correctedUnits(32,10,8,8),1e-10);
        assertEquals(32,CutCalibration.correctedUnits(40,10,12.5,12.5),1e-10);
        assertEquals(40,CutCalibration.correctedUnits(40,10,9.99,10.01),1e-10);
    }
    @Test void rejectsTrackingErrorsAndInvalidMeasurements(){
        assertThrows(IllegalArgumentException.class,()->CutCalibration.correctedUnits(40,10,8,10));
        for(double n:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->CutCalibration.correctedUnits(40,10,n,n));
        for(String text:new String[]{""," ","NaN","Infinity","-1","100001","unknown"})assertThrows(IllegalArgumentException.class,()->CutCalibration.measurement(text));
        assertEquals(0,CutCalibration.measurement("0"));assertEquals(12.5,CutCalibration.measurement(" 12.5 "));
    }
}
