package gy.deckers.studio;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.util.*;

/** Direct Windows USBPRINT transport; does not create a printer queue or change drivers. */
final class CutterUsb {
    private static final String GUID="{28d78fad-5a12-11d1-ae5b-0000f803a8c2}";
    static boolean validPath(String path){return path!=null&&path.toLowerCase(Locale.ROOT).matches("\\\\\\\\\\?\\\\usb#vid_[0-9a-f]{4}&pid_[0-9a-f]{4}#[^\\\\/]+#\\{28d78fad-5a12-11d1-ae5b-0000f803a8c2\\}");}
    static List<String> devices(){
        if(!Platform.isWindows())return List.of();
        String root="SYSTEM\\CurrentControlSet\\Control\\DeviceClasses\\"+GUID;
        if(!Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE,root))return List.of();
        List<String> paths=new ArrayList<>();
        for(String name:Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE,root)){
            if(!name.startsWith("##?#"))continue;
            String path="\\\\?\\"+name.substring(4);
            if(validPath(path))paths.add(path);
        }
        return List.copyOf(paths);
    }
    static CutterDevice.Factory factory(CutterDevice.Profile input){var p=input.copy();return ()->{
        p.requireVerified();if(!Platform.isWindows()||!validPath(p.port))throw new IOException("Select a verified Windows USBPRINT device interface.");
        var handle=Kernel32.INSTANCE.CreateFile(p.port,WinNT.GENERIC_WRITE,0,null,WinNT.OPEN_EXISTING,0,null);
        if(WinBase.INVALID_HANDLE_VALUE.equals(handle))throw new IOException("Cannot open cutter USB interface (Windows error "+Kernel32.INSTANCE.GetLastError()+"). Close SignMaster and check the connected device.");
        return new CutterDevice.Transport(){
            public void write(byte[] data)throws IOException {IntByReference written=new IntByReference();if(!Kernel32.INSTANCE.WriteFile(handle,data,data.length,written,null)||written.getValue()!=data.length)throw new IOException("USB cutter write failed (Windows error "+Kernel32.INSTANCE.GetLastError()+").");}
            public void close(){Kernel32.INSTANCE.CloseHandle(handle);}
        };
    };}
    private CutterUsb(){}
}
