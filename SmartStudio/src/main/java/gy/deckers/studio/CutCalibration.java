package gy.deckers.studio;

/** Physical measurements, independent of device transport and screen units. */
final class CutCalibration {
    static double correctedUnits(double previous,double requested,double measuredX,double measuredY){
        for(double n:new double[]{previous,requested,measuredX,measuredY})
            if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Enter positive, finite scale and measured dimensions.");
        double average=(measuredX+measuredY)/2;
        if(Math.abs(measuredX-measuredY)/average>.02)
            throw new IllegalArgumentException("The measured axes differ by more than 2%. Check tracking, axis mapping and measurements before applying a shared scale.");
        double corrected=previous*requested/average;
        if(!Double.isFinite(corrected)||corrected>10000)throw new IllegalArgumentException("Calculated scale is outside the supported range.");
        return corrected;
    }
    static double measurement(String text){
        try{double n=Double.parseDouble(text.trim());if(Double.isFinite(n)&&n>=0&&n<=100000)return n;}
        catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("Enter each measured target coordinate in millimetres; blank and non-finite values are not measurements.");
    }
    private CutCalibration(){}
}
