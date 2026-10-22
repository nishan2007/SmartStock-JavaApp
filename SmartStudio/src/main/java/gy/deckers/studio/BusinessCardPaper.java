package gy.deckers.studio;

final class BusinessCardPaper {
    static double[] size(String name,double customWidth,double customHeight){
        return switch(name){
            case "Letter"->new double[]{215.9,279.4};
            case "Legal"->new double[]{215.9,355.6};
            case "A4"->new double[]{210,297};
            case "11 × 17"->new double[]{279.4,431.8};
            case "Custom"->{
                if(!Double.isFinite(customWidth+customHeight)||customWidth<=0||customHeight<=0||customWidth>78.74||customHeight>78.74)
                    throw new IllegalArgumentException("Custom width and height must be greater than 0 and at most 78.74 inches.");
                yield new double[]{customWidth*25.4,customHeight*25.4};
            }
            default->throw new IllegalArgumentException("Choose a sheet size.");
        };
    }
}
