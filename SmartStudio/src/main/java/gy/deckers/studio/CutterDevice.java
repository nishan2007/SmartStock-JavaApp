package gy.deckers.studio;

import com.fazecast.jSerialComm.SerialPort;
import java.awt.Shape;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** HPGL adapter: no guessed scale, origin reset, force commands, or automatic retry. */
final class CutterDevice {
    static final class Profile {
        int version=1,baud=9600,flow;
        String port="",material="Label vinyl — kiss cut",notes="Set speed, force and blade offset on the cutter; record tested values here.";
        double unitsPerMm,feedWidth=870,cutWidth=765,feedLength=1500;
        boolean protocolVerified,swapAxes,usb;
        int xSign=1,ySign=1;
        void requireVerified(){
            if(version!=1||port==null||port.isBlank()||!protocolVerified||!Double.isFinite(unitsPerMm)||unitsPerMm<=0||unitsPerMm>10000||baud<300||baud>115200||Math.abs(xSign)!=1||Math.abs(ySign)!=1||flow<0||flow>2)
                throw new IllegalArgumentException("Verify the B-870 HPGL protocol, serial settings, axis directions and units/mm before sending motion.");
            for(double n:new double[]{feedWidth,cutWidth,feedLength})if(!Double.isFinite(n)||n<=0||n>100000)throw new IllegalArgumentException("Enter verified cutter limits.");
            if(cutWidth>feedWidth)throw new IllegalArgumentException("Cut width cannot exceed feed width.");
        }
        Profile copy(){return publishing.Publication.JSON.fromJson(publishing.Publication.JSON.toJson(this),Profile.class);}
    }
    interface Transport extends AutoCloseable {void write(byte[] bytes)throws IOException;void close();}
    interface Factory {Transport open()throws IOException;}
    static Factory connection(Profile p){return p.usb?CutterUsb.factory(p):serial(p);}
    static Factory serial(Profile input){Profile p=input.copy();return ()->{
        p.requireVerified();SerialPort port=SerialPort.getCommPort(p.port);
        port.setComPortParameters(p.baud,8,SerialPort.ONE_STOP_BIT,SerialPort.NO_PARITY);
        port.setFlowControl(switch(p.flow){case 1->SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED|SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;case 2->SerialPort.FLOW_CONTROL_RTS_ENABLED|SerialPort.FLOW_CONTROL_CTS_ENABLED;default->SerialPort.FLOW_CONTROL_DISABLED;});
        port.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING,0,5000);
        if(!port.openPort())throw new IOException("Cannot open cutter port "+p.port+". Close other cutting software and check the cable.");
        return new Transport(){
            public void write(byte[] data)throws IOException {if(!port.isOpen()||port.writeBytes(data,data.length)!=data.length)throw new IOException("Cutter write failed.");}
            public void close(){port.closePort();}
        };
    };}
    static java.util.List<String> commands(java.util.List<Shape> cuts,AffineTransform alignment,Profile p){
        p.requireVerified();java.util.List<String> lines=new ArrayList<>();lines.add("PA;PU;");int points=0;
        for(Shape cut:cuts){PathIterator it=new FlatteningPathIterator(cut.getPathIterator(alignment),.05);double[] xy=new double[6];String start=null;boolean down=false;
            while(!it.isDone()){
                int kind=it.currentSegment(xy);
                if(kind==PathIterator.SEG_CLOSE){if(down&&start!=null)lines.add("PD"+start+";");lines.add("PU;");down=false;}
                else{
                    if(!Double.isFinite(xy[0])||!Double.isFinite(xy[1])||xy[0]<-.001||xy[1]<-.001||xy[0]>p.cutWidth+.001||xy[1]>p.feedLength+.001)throw new IllegalArgumentException("Cut point is outside verified cutter bounds.");
                    String point=coordinate(xy[0],xy[1],p);
                    if(kind==PathIterator.SEG_MOVETO){lines.add("PU"+point+";");start=point;down=false;}else{lines.add("PD"+point+";");down=true;}
                    if(++points>500000)throw new IllegalArgumentException("Cut path is too complex; simplify contours.");
                }it.next();
            }lines.add("PU;");
        }return java.util.List.copyOf(lines);
    }
    private static String coordinate(double x,double y,Profile p){
        double a=(p.swapAxes?y:x)*p.xSign*p.unitsPerMm,b=(p.swapAxes?x:y)*p.ySign*p.unitsPerMm;
        if(Math.abs(a)>Integer.MAX_VALUE||Math.abs(b)>Integer.MAX_VALUE)throw new IllegalArgumentException("Cutter coordinate overflow.");
        return Math.round(a)+","+Math.round(b);
    }
    static void send(Factory factory,java.util.List<String> commands)throws IOException {
        // Validate/build all commands before opening the port. A partial write is never resumed.
        boolean attempted=false;
        try(Transport transport=factory.open()){
            for(String command:commands){
                if(Thread.currentThread().isInterrupted())throw new IOException("Sending stopped.");
                attempted=true;transport.write(command.getBytes(StandardCharsets.US_ASCII));
            }
        }catch(Exception ex){throw new IOException(attempted?"Sending stopped; this job may be partially cut or buffered. Stop the cutter at its panel, inspect material and re-establish origin. No automatic retry or resume was attempted.":"Cutter was not opened; no job commands were sent. "+ex.getMessage(),ex);}
    }
    private CutterDevice(){}
}
