package gy.deckers.studio;

import java.util.Arrays;
import java.util.Comparator;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/** Geometry shared by the badge and publication ribbons; units remain document units. */
final class StudioArrange {
    enum Operation {
        LEFT, CENTRE, RIGHT, TOP, MIDDLE, BOTTOM, HORIZONTAL_GAPS, VERTICAL_GAPS,
        TEMPLATE_HORIZONTAL, TEMPLATE_VERTICAL;

        boolean centersOnTemplate(){return this==TEMPLATE_HORIZONTAL||this==TEMPLATE_VERTICAL;}
        boolean distributes(){return this==HORIZONTAL_GAPS||this==VERTICAL_GAPS;}
        int minimumSelection(){return centersOnTemplate()?1:distributes()?3:2;}
    }

    static double[] bounds(Rectangle2D box,double rotation){
        Rectangle2D edge=rotation==0?box:AffineTransform.getRotateInstance(Math.toRadians(rotation),
                box.getCenterX(),box.getCenterY()).createTransformedShape(box).getBounds2D();
        return new double[]{edge.getX(),edge.getY(),edge.getWidth(),edge.getHeight()};
    }

    static double[][] positions(double[][] input,Operation operation){
        return positions(input,operation,Double.NaN,Double.NaN);
    }

    static double[][] positions(double[][] input,Operation operation,double templateWidth,double templateHeight){
        if(input.length<operation.minimumSelection())throw new IllegalArgumentException(switch(operation.minimumSelection()){
            case 1->"Select an object to center on the template.";
            case 2->"Select at least two objects to align.";
            default->"Select at least three objects to distribute.";
        });
        double[][] result=Arrays.stream(input).map(double[]::clone).toArray(double[][]::new);
        for(double[] b:result)if(b.length!=4||!Double.isFinite(b[0]+b[1]+b[2]+b[3])||b[2]<=0||b[3]<=0)throw new IllegalArgumentException("Invalid object bounds.");
        double left=Arrays.stream(result).mapToDouble(b->b[0]).min().orElseThrow();
        double top=Arrays.stream(result).mapToDouble(b->b[1]).min().orElseThrow();
        double right=Arrays.stream(result).mapToDouble(b->b[0]+b[2]).max().orElseThrow();
        double bottom=Arrays.stream(result).mapToDouble(b->b[1]+b[3]).max().orElseThrow();
        if(operation.centersOnTemplate()){
            boolean horizontal=operation==Operation.TEMPLATE_HORIZONTAL;
            double size=horizontal?templateWidth:templateHeight;
            if(!Double.isFinite(size)||size<=0)throw new IllegalArgumentException("Invalid template size.");
            double shift=size/2-(horizontal?(left+right)/2:(top+bottom)/2);
            for(double[] b:result)b[horizontal?0:1]+=shift;
        }else if(operation.distributes()){
            int axis=operation==Operation.HORIZONTAL_GAPS?0:1;
            double[][] ordered=result.clone();Arrays.sort(ordered,Comparator.comparingDouble(b->b[axis]));
            double end=ordered[ordered.length-1][axis]+ordered[ordered.length-1][axis+2];
            double extent=Arrays.stream(ordered).mapToDouble(b->b[axis+2]).sum();
            double gap=(end-ordered[0][axis]-extent)/(ordered.length-1);
            double cursor=ordered[0][axis];for(double[] b:ordered){b[axis]=cursor;cursor+=b[axis+2]+gap;}
        }else for(double[] b:result)switch(operation){
            case LEFT->b[0]=left;case CENTRE->b[0]=(left+right-b[2])/2;case RIGHT->b[0]=right-b[2];
            case TOP->b[1]=top;case MIDDLE->b[1]=(top+bottom-b[3])/2;case BOTTOM->b[1]=bottom-b[3];default->throw new IllegalStateException();
        }
        return result;
    }
}
