package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudioArrangeTest {
    @Test void alignsToCombinedEdgesWithoutResizingOrMutatingInput(){
        double[][] input={{10,20,30,10},{60,40,10,20}};
        double[][] right=StudioArrange.positions(input,StudioArrange.Operation.RIGHT);
        assertEquals(40,right[0][0]);assertEquals(60,right[1][0]);assertEquals(10,input[0][0]);
        assertEquals(30,right[0][2]);assertEquals(20,right[0][1]);
        double[][] centre=StudioArrange.positions(input,StudioArrange.Operation.CENTRE);
        assertEquals(40,centre[0][0]+centre[0][2]/2);assertEquals(40,centre[1][0]+centre[1][2]/2);
    }
    @Test void distributesUnequalWidthsAndRetainsOriginalObjectOrder(){
        double[][] input={{80,4,20,10},{0,2,10,10},{20,3,30,10}};
        double[][] result=StudioArrange.positions(input,StudioArrange.Operation.HORIZONTAL_GAPS);
        assertEquals(80,result[0][0]);assertEquals(0,result[1][0]);assertEquals(30,result[2][0]);
        assertEquals(result[2][0]-(result[1][0]+result[1][2]),result[0][0]-(result[2][0]+result[2][2]));
        assertEquals(3,result[2][1]);assertEquals(20,input[2][0]);
    }
    @Test void rejectsInvalidSelectionAndNonFiniteGeometry(){
        assertThrows(IllegalArgumentException.class,()->StudioArrange.positions(new double[][]{{0,0,1,1}},StudioArrange.Operation.LEFT));
        assertThrows(IllegalArgumentException.class,()->StudioArrange.positions(new double[][]{{0,0,1,1},{2,0,1,1}},StudioArrange.Operation.HORIZONTAL_GAPS));
        assertThrows(IllegalArgumentException.class,()->StudioArrange.positions(new double[][]{{Double.NaN,0,1,1},{2,0,1,1}},StudioArrange.Operation.LEFT));
    }
    @Test void centersAGroupOnTheTemplateWithoutChangingItsSpacing(){
        double[][] input={{10,12,12,4},{40,26,8,10}};
        var horizontal=StudioArrange.positions(input,StudioArrange.Operation.TEMPLATE_HORIZONTAL,100,60);
        assertArrayEquals(new double[]{31,12,12,4},horizontal[0]);
        assertArrayEquals(new double[]{61,26,8,10},horizontal[1]);
        var vertical=StudioArrange.positions(input,StudioArrange.Operation.TEMPLATE_VERTICAL,100,60);
        assertArrayEquals(new double[]{10,18,12,4},vertical[0]);
        assertArrayEquals(new double[]{40,32,8,10},vertical[1]);
        var single=StudioArrange.positions(new double[][]{{5,8,20,12}},StudioArrange.Operation.TEMPLATE_VERTICAL,100,60);
        assertArrayEquals(new double[]{5,24,20,12},single[0]);
    }
    @Test void alignsTheVisibleEdgesOfRotatedObjects(){
        var rotated=StudioArrange.bounds(new java.awt.geom.Rectangle2D.Double(20,10,24,8),90);
        assertArrayEquals(new double[]{28,2,8,24},rotated,.0001);
        var result=StudioArrange.positions(new double[][]{rotated,{4,8,12,17}},StudioArrange.Operation.BOTTOM);
        assertEquals(26,result[0][1]+result[0][3],.0001);
        assertEquals(26,result[1][1]+result[1][3],.0001);
    }
}
