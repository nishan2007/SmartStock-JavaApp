package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Capture clipboard data on the EDT, decode away from the UI, then insert. */
final class CanvasImageInsert {
    static void bindPaste(JComponent canvas,Runnable paste){
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control V"),"pasteNewImage");
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V,Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),"pasteNewImage");
        canvas.getActionMap().put("pasteNewImage",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){paste.run();}});
    }
    static Callable<StudioImageFile.Loaded> clipboard()throws Exception{return clipboard(false); }
    static Callable<StudioImageFile.Loaded> clipboard(boolean artwork)throws Exception{
        var contents=Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        if(contents==null||!StudioImageTransfer.supports(contents))throw new java.io.IOException("Copy an image or a PNG/JPEG file first, then choose Paste image.");
        return StudioImageTransfer.capture(contents,artwork);
    }
    static void load(Callable<StudioImageFile.Loaded> loader,Consumer<StudioImageFile.Loaded> done,Consumer<Exception> error){
        new SwingWorker<StudioImageFile.Loaded,Void>(){
            protected StudioImageFile.Loaded doInBackground()throws Exception{return loader.call();}
            protected void done(){try{done.accept(get());}catch(Exception ex){error.accept(ex.getCause() instanceof Exception cause?cause:ex);}}
        }.execute();
    }
    static double[] fit(int width,int height,double pageWidth,double pageHeight){
        double factor=Math.min(pageWidth*.6/width,pageHeight*.6/height);
        return new double[]{width*factor,height*factor};
    }
}
