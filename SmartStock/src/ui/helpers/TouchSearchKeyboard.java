package ui.helpers;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import javax.swing.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Recognizes Windows touch-promoted mouse presses without treating ordinary clicks as touch. */
public final class TouchSearchKeyboard {
    private TouchSearchKeyboard() { }

    private static boolean installed;

    public static void install(JFrame owner, JTextField field) {
        installGlobal();
    }

    public static synchronized void installGlobal() {
        if (installed || java.awt.GraphicsEnvironment.isHeadless()
                || !System.getProperty("os.name", "").startsWith("Windows")) return;
        installed = true;
        Detector detector = new Detector();
        Thread worker = new Thread(detector, "smartstock-touch-keyboard-input");
        worker.setDaemon(true);
        worker.start();
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent mouse) || mouse.getID() != MouseEvent.MOUSE_PRESSED
                    || !SwingUtilities.isLeftMouseButton(mouse) || !detector.consumeTouch()) return;
            if (!(mouse.getComponent() instanceof javax.swing.text.JTextComponent field)
                    || !field.isEditable() || !field.isEnabled()) return;
            field.requestFocusInWindow();
            Timer delay = new Timer(150, ignored -> {
                if (field.isShowing() && field.isFocusOwner()) openKeyboard();
            });
            delay.setRepeats(false);
            delay.start();
        }, java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    private static final AtomicBoolean keyboardStarting = new AtomicBoolean();

    private static void openKeyboard() {
        if (!keyboardStarting.compareAndSet(false, true)) return;
        Thread keyboard = new Thread(() -> {
            try {
                showWindowsKeyboard();
            } catch (Exception | LinkageError failure) {
                System.err.println("SmartStock could not open the Windows touch keyboard: "
                        + failure.getClass().getSimpleName());
            } finally {
                keyboardStarting.set(false);
            }
        }, "smartstock-touch-keyboard");
        keyboard.setDaemon(true);
        keyboard.start();
    }

    static void showWindowsKeyboard() throws java.io.IOException {
        WinDef.HWND existing = User32.INSTANCE.FindWindow("OSKMainClass", null);
        if (existing != null) {
            User32.INSTANCE.ShowWindow(existing, WinUser.SW_SHOWNOACTIVATE);
            return;
        }
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null) throw new java.io.IOException("Windows system directory is unavailable.");
        Path executable = Path.of(systemRoot, "System32", "osk.exe");
        if (!Files.isRegularFile(executable)) throw new java.io.IOException("Windows On-Screen Keyboard was not found.");
        // Shell activation handles OSK's Windows UIAccess launch requirements.
        WinDef.INT_PTR result = Shell32.INSTANCE.ShellExecute(null, "open", executable.toString(),
                null, null, WinUser.SW_SHOWNOACTIVATE);
        if (result == null || result.longValue() <= 32) {
            throw new java.io.IOException("Windows could not open On-Screen Keyboard (code "
                    + (result == null ? 0 : result.longValue()) + ").");
        }
    }

    static boolean isTouch(long extra) {
        return (extra & 0xffffff00L) == 0xff515700L && (extra & 0x80L) != 0;
    }

    static final class Detector implements Runnable {
        private volatile long touchAt;
        private volatile int threadId;
        private volatile boolean closed;
        private WinUser.LowLevelMouseProc callback;

        boolean consumeTouch() {
            long stamp = touchAt;
            touchAt = 0;
            return stamp != 0 && System.nanoTime() - stamp < 2_000_000_000L;
        }

        void close() {
            closed = true;
            if (threadId != 0) User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, null, null);
        }

        public void run() {
            WinUser.HHOOK hook = null;
            try {
                threadId = Kernel32.INSTANCE.GetCurrentThreadId();
                WinUser.MSG message = new WinUser.MSG();
                User32.INSTANCE.PeekMessage(message, null, 0, 0, 0);
                if (closed) return;
                callback = (code, type, data) -> {
                    if (code >= 0 && type.intValue() == 0x0201 /* WM_LBUTTONDOWN */) {
                        touchAt = isTouch(data.dwExtraInfo.longValue()) ? System.nanoTime() : 0;
                    }
                    return User32.INSTANCE.CallNextHookEx(null, code, type,
                            new WinDef.LPARAM(Pointer.nativeValue(data.getPointer())));
                };
                hook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL, callback,
                        Kernel32.INSTANCE.GetModuleHandle(null), 0);
                if (hook == null) return;
                while (!closed && User32.INSTANCE.GetMessage(message, null, 0, 0) > 0) {
                    User32.INSTANCE.TranslateMessage(message);
                    User32.INSTANCE.DispatchMessage(message);
                }
            } catch (Throwable failure) {
                System.err.println("SmartStock touch detection unavailable: " + failure.getClass().getSimpleName());
            } finally {
                if (hook != null) User32.INSTANCE.UnhookWindowsHookEx(hook);
                callback = null;
            }
        }
    }
}
