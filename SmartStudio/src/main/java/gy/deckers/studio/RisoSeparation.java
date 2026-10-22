package gy.deckers.studio;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Spot-ink approximation for paper masters. White is paper, not an ink. */
final class RisoSeparation {
    record Ink(String name, Color color, boolean enabled) {}
    private RisoSeparation() {}

    static List<Ink> detect(Iterable<BufferedImage> samples, int count) {
        if(count<1||count>8)throw new IllegalArgumentException("Choose 1–8 inks.");
        Map<Integer,Long> histogram=new TreeMap<>();
        for(var image:samples)for(int y=0;y<image.getHeight();y+=2)for(int x=0;x<image.getWidth();x+=2){
            int rgb=image.getRGB(x,y);Color c=new Color(rgb);
            if(Math.min(c.getRed(),Math.min(c.getGreen(),c.getBlue()))>245)continue;
            int key=((c.getRed()>>4)<<8)|((c.getGreen()>>4)<<4)|(c.getBlue()>>4);
            histogram.merge(key,1L,Long::sum);
        }
        List<Color> colors=new ArrayList<>();
        while(colors.size()<count&&!histogram.isEmpty()){
            int best=-1;double score=-1;
            for(var entry:histogram.entrySet()){
                Color c=bucket(entry.getKey());double distance=colors.isEmpty()?1:colors.stream().mapToDouble(k->error(c,k)).min().orElse(0);
                double candidate=entry.getValue()*distance;
                if(candidate>score){score=candidate;best=entry.getKey();}
            }
            if(best<0||(!colors.isEmpty()&&score<.5))break;
            colors.add(bucket(best));histogram.remove(best);
        }
        List<Ink> result=new ArrayList<>();
        for(int i=0;i<colors.size();i++)result.add(new Ink("Ink "+(i+1),colors.get(i),true));
        if(result.isEmpty())result.add(new Ink("Black",Color.BLACK,true));
        return result;
    }
    private static Color bucket(int key){return new Color(Math.min(255,((key>>8)&15)*16+7),Math.min(255,((key>>4)&15)*16+7),Math.min(255,(key&15)*16+7));}
    static double coverage(Color source,Color ink){
        double r=255-ink.getRed(),g=255-ink.getGreen(),b=255-ink.getBlue();
        double denominator=r*r+g*g+b*b;
        return denominator==0?0:Math.max(0,Math.min(1,((255-source.getRed())*r+(255-source.getGreen())*g+(255-source.getBlue())*b)/denominator));
    }
    private static double error(Color source,Color ink){
        double t=coverage(source,ink);
        double r=source.getRed()-(255-t*(255-ink.getRed()));
        double g=source.getGreen()-(255-t*(255-ink.getGreen()));
        double b=source.getBlue()-(255-t*(255-ink.getBlue()));
        return r*r+g*g+b*b;
    }
    /** Isolated object, retaining alpha so normal object stacking and knockouts still apply. */
    static BufferedImage layer(BufferedImage source,List<Ink> inks,int plate,int forced){
        if(inks.isEmpty()||inks.size()>8||inks.stream().noneMatch(Ink::enabled)||plate < -1||plate>=inks.size()||forced < -1||forced>=inks.size()||(forced>=0&&!inks.get(forced).enabled()))throw new IllegalArgumentException("Choose valid enabled inks.");
        var output=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<source.getHeight();y++){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            for(int x=0;x<source.getWidth();x++){
                int pixel=source.getRGB(x,y),alpha=pixel>>>24;if(alpha==0)continue;
                Color c=new Color(pixel);int winner=forced;
                if(winner<0){double best=Double.POSITIVE_INFINITY;for(int i=0;i<inks.size();i++)if(inks.get(i).enabled()){double e=error(c,inks.get(i).color());if(e<best){best=e;winner=i;}}}
                Color ink=inks.get(winner).color();double tone=forced>=0?1-(.2126*c.getRed()+.7152*c.getGreen()+.0722*c.getBlue())/255.:coverage(c,ink);
                int rgb;
                if(plate<0)rgb=new Color((int)Math.round(255-tone*(255-ink.getRed())),(int)Math.round(255-tone*(255-ink.getGreen())),(int)Math.round(255-tone*(255-ink.getBlue()))).getRGB()&0xffffff;
                else {int gray=winner==plate?(int)Math.round(255*(1-tone)):255;rgb=(gray<<16)|(gray<<8)|gray;}
                output.setRGB(x,y,(alpha<<24)|rgb);
            }
        }
        return output;
    }
    /** One closest ink per pixel; shades become coverage rather than extra inks. */
    static BufferedImage render(BufferedImage source,List<Ink> inks,int plate){
        if(inks.isEmpty()||inks.size()>8)throw new IllegalArgumentException("Choose 1–8 inks.");
        if(plate < -1 || plate>=inks.size())throw new IllegalArgumentException("Invalid ink.");
        if(inks.stream().noneMatch(Ink::enabled))throw new IllegalArgumentException("Enable at least one ink.");
        BufferedImage output=new BufferedImage(source.getWidth(),source.getHeight(),plate<0?BufferedImage.TYPE_INT_RGB:BufferedImage.TYPE_BYTE_GRAY);
        for(int y=0;y<source.getHeight();y++){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            for(int x=0;x<source.getWidth();x++){
                Color c=new Color(source.getRGB(x,y));int winner=-1;double best=Double.POSITIVE_INFINITY;
                for(int i=0;i<inks.size();i++)if(inks.get(i).enabled()){
                    double e=error(c,inks.get(i).color());if(e<best){best=e;winner=i;}
                }
                Color ink=inks.get(winner).color();double t=coverage(c,ink);
                if(plate<0)output.setRGB(x,y,new Color((int)Math.round(255-t*(255-ink.getRed())),(int)Math.round(255-t*(255-ink.getGreen())),(int)Math.round(255-t*(255-ink.getBlue()))).getRGB());
                else {int gray=winner==plate?(int)Math.round(255*(1-t)):255;output.getRaster().setSample(x,y,0,gray);}
            }
        }
        return output;
    }
}
