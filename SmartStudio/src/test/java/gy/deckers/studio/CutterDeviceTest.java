package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CutterDeviceTest {
    @Test void usbPathsOnlyAcceptPrinterDeviceInterfaces(){
        assertTrue(CutterUsb.validPath("\\\\?\\USB#VID_0483&PID_5750#2017-2-25#{28d78fad-5a12-11d1-ae5b-0000f803a8c2}"));
        assertFalse(CutterUsb.validPath("C:\\output.hpgl"));assertFalse(CutterUsb.validPath("\\\\?\\C:\\output"));assertFalse(CutterUsb.validPath("COM3"));
    }
    private static CutterDevice.Profile profile(){var p=new CutterDevice.Profile();p.port="test";p.protocolVerified=true;p.unitsPerMm=40;return p;}
    @Test void unknownScaleAndUnverifiedDeviceCannotGenerateMotion(){
        var p=new CutterDevice.Profile();assertThrows(IllegalArgumentException.class,()->CutterDevice.commands(List.of(new Rectangle2D.Double(1,1,10,10)),new AffineTransform(),p));
        p.protocolVerified=true;p.port="test";assertThrows(IllegalArgumentException.class,p::requireVerified);
    }
    @Test void hpglPreservesOriginClosesContoursAndUsesPenUpTravel(){
        var commands=CutterDevice.commands(List.of(new Rectangle2D.Double(1,2,10,10)),new AffineTransform(),profile());String output=String.join("",commands);
        assertTrue(output.startsWith("PA;PU;PU40,80;"));assertTrue(output.contains("PD440,80;"));assertTrue(output.endsWith("PU;"));assertFalse(output.contains("IN;"));assertFalse(output.contains("VS"));assertFalse(output.contains("FS"));
    }
    @Test void mapsCalibratedAxesAndRejectsOutOfBoundsBeforeOpeningPort(){
        var p=profile();p.swapAxes=true;p.xSign=-1;var path=new Path2D.Double();path.moveTo(1,2);assertTrue(String.join("",CutterDevice.commands(List.of(path),new AffineTransform(),p)).contains("PU-80,40;"));
        assertThrows(IllegalArgumentException.class,()->CutterDevice.commands(List.of(new Rectangle2D.Double(760,1,10,10)),new AffineTransform(),p));
    }
    @Test void failedWriteClosesTransportAndNeverRetries(){
        int[] opens={0},writes={0},closes={0};CutterDevice.Factory factory=()->{opens[0]++;return new CutterDevice.Transport(){public void write(byte[] bytes)throws IOException{if(++writes[0]==2)throw new IOException("disconnected");}public void close(){closes[0]++;}};};
        IOException error=assertThrows(IOException.class,()->CutterDevice.send(factory,List.of("PA;PU;","PU1,1;","PD2,2;")));
        assertTrue(error.getMessage().contains("partially cut"));assertEquals(1,opens[0]);assertEquals(2,writes[0]);assertEquals(1,closes[0]);
    }
    @Test void openingFailureReportsNoCommandsSent(){IOException error=assertThrows(IOException.class,()->CutterDevice.send(()->{throw new IOException("busy");},List.of("PU;")));assertTrue(error.getMessage().contains("no job commands"));}
}
