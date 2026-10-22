package ui.helpers;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** A temporary Wintab system context. Never modifies the driver's saved settings. */
final class SignatureTabletMapping implements AutoCloseable {
    interface Wintab extends StdCallLibrary {
        int WTInfoA(int category,int index,Pointer data);
        Pointer WTOpenA(Pointer window,Pointer context,boolean enabled);
        boolean WTSetA(Pointer context,Pointer settings);
        boolean WTGetA(Pointer context,Pointer settings);
        boolean WTEnable(Pointer context,boolean enabled);
        boolean WTOverlap(Pointer context,boolean onTop);
        boolean WTClose(Pointer context);
    }
    // LOGCONTEXTA: 40 ANSI name bytes followed by 33 Win32 32-bit fields.
    // Keep Win32 LONG/BOOL/FIX32 at 4 bytes, including in a 64-bit JVM.
    static final int CONTEXT_SIZE=172, OPTIONS=0, LOCKS=2, SYS_MODE=26,
            SYS_X=27,SYS_Y=28,SYS_WIDTH=29,SYS_HEIGHT=30;
    private final JComponent pad;
    private final JLabel status;
    private final Timer timer;
    private Wintab api;
    private Pointer context;
    private Memory settings;
    private Rectangle mapped;
    private boolean failed;
    private Window owner;
    private final WindowAdapter focus=new WindowAdapter(){
        @Override public void windowGainedFocus(WindowEvent e){refresh();}
        @Override public void windowLostFocus(WindowEvent e){
            try{release();}catch(LinkageError|RuntimeException ex){unavailable();}
        }
    };

    SignatureTabletMapping(JComponent pad,JLabel status){
        this.pad=pad;this.status=status;
        timer=new Timer(150,e->refresh());
    }
    void start(){
        if(!Platform.isWindows()){
            setStatus("Box mapping requires Windows and a Wintab-compatible tablet driver.");return;
        }
        try{
            api=Native.load("Wintab32",Wintab.class);
            owner=SwingUtilities.getWindowAncestor(pad);
            if(owner!=null)owner.addWindowFocusListener(focus);
            timer.start();refresh();
        }
        catch(LinkageError|RuntimeException ex){unavailable();}
    }
    static void field(Memory memory,int index,int value){memory.setInt(40L+4L*index,value);}
    static void map(Memory memory,Rectangle box){
        field(memory,SYS_MODE,0);field(memory,SYS_X,box.x);field(memory,SYS_Y,box.y);
        field(memory,SYS_WIDTH,box.width);field(memory,SYS_HEIGHT,box.height);
    }
    private void refresh(){
        if(failed)return;
        Window window=SwingUtilities.getWindowAncestor(pad);
        try{
            if(window==null||!window.isActive()||!pad.isShowing()||!pad.isEnabled()){
                release();setStatus("Pen mapping paused while the signature box is inactive.");return;
            }
            Pointer handle=Native.getComponentPointer(window);
            WinDef.RECT nativeBounds=new WinDef.RECT();
            if(!User32.INSTANCE.GetWindowRect(new WinDef.HWND(handle),nativeBounds))throw new IllegalStateException();
            // Convert Swing coordinates using this window's native bounds, including DPI scaling.
            Point point=SwingUtilities.convertPoint(pad,2,2,window);
            double sx=(nativeBounds.right-nativeBounds.left)/(double)window.getWidth();
            double sy=(nativeBounds.bottom-nativeBounds.top)/(double)window.getHeight();
            Rectangle box=new Rectangle(nativeBounds.left+(int)Math.round(point.x*sx),
                    nativeBounds.top+(int)Math.round(point.y*sy),
                    Math.max(1,(int)Math.round((pad.getWidth()-4)*sx)),
                    Math.max(1,(int)Math.round((pad.getHeight()-4)*sy)));
            if(context==null){
                settings=new Memory(CONTEXT_SIZE);settings.clear();
                if(api.WTInfoA(4,0,settings)!=CONTEXT_SIZE)throw new IllegalStateException(); // WTI_DEFSYSCTX
                // System cursor only: no WT_* messages sent to Swing's native window procedure.
                field(settings,OPTIONS,1);
                field(settings,LOCKS,settings.getInt(40+4*LOCKS)|0x10); // CXL_SYSOUT
                // Use the physical device's full X/Y range when it is available.
                int device=settings.getInt(40+4*4);
                if(device>=0){
                    try(Memory axis=new Memory(16)){
                        for(int i=0;i<2;i++){
                            if(api.WTInfoA(100+device,12+i,axis)!=16)throw new IllegalStateException();
                            int min=axis.getInt(0),max=axis.getInt(4);
                            if(max<=min)throw new IllegalStateException();
                            field(settings,11+i,min);field(settings,14+i,max-min);
                        }
                    }
                }
                map(settings,box);
                context=api.WTOpenA(handle,settings,false);
                if(context==null)throw new IllegalStateException();
                if(!api.WTEnable(context,true)||!api.WTOverlap(context,true))throw new IllegalStateException();
                verify(box);
                mapped=box;
            }else if(!box.equals(mapped)){
                map(settings,box);if(!api.WTSetA(context,settings))throw new IllegalStateException();verify(box);mapped=box;
            }
            setStatus("Driver accepted box mapping. Check that the pen stays inside; use the mouse for other controls.");
        }catch(LinkageError|RuntimeException ex){unavailable();}
    }
    private void verify(Rectangle box){
        try(Memory actual=new Memory(CONTEXT_SIZE)){
            if(!api.WTGetA(context,actual)||actual.getInt(40+4*SYS_MODE)!=0
                    ||actual.getInt(40+4*SYS_X)!=box.x||actual.getInt(40+4*SYS_Y)!=box.y
                    ||actual.getInt(40+4*SYS_WIDTH)!=box.width||actual.getInt(40+4*SYS_HEIGHT)!=box.height)
                throw new IllegalStateException("Tablet driver did not accept signature box mapping");
        }
    }
    private void setStatus(String text){status.setText("<html><div style='width:460px'>"+text+"</div></html>");}
    private void release(){
        if(context!=null){Pointer old=context;context=null;api.WTClose(old);}
        if(settings!=null){settings.close();settings=null;}mapped=null;
    }
    private void unavailable(){
        failed=true;timer.stop();
        try{release();}catch(LinkageError|RuntimeException ignored){}
        setStatus("Box mapping unavailable. Install/enable the XP-Pen Wintab driver; normal pointer input still works.");
    }
    @Override public void close(){
        timer.stop();if(owner!=null)owner.removeWindowFocusListener(focus);
        try{release();}catch(LinkageError|RuntimeException ignored){}
    }
}
