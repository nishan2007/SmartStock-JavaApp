package ui.helpers;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/** Runs an action-time blocking operation off the EDT while a cancellable modal status remains responsive. */
public final class ResponsiveTask {
    private ResponsiveTask() { }

    public static <T> T await(Component parent, String message, Callable<T> background) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) return background.call();

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog progress = new JDialog(owner, "SmartStock", Dialog.ModalityType.APPLICATION_MODAL);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JLabel label = new JLabel(message == null || message.isBlank() ? "Working..." : message,
                SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(18, 24, 12, 24));
        JButton cancel = new JButton("Cancel");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancel);
        progress.add(label, BorderLayout.CENTER);
        progress.add(actions, BorderLayout.SOUTH);
        progress.pack();
        progress.setLocationRelativeTo(parent);

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override protected T doInBackground() throws Exception { return background.call(); }

            @Override protected void done() {
                try { result.set(get()); }
                catch (CancellationException cancelled) { failure.set(cancelled); }
                catch (Exception ex) { failure.set(ex.getCause() == null ? ex : ex.getCause()); }
                finally { progress.dispose(); }
            }
        };
        cancel.addActionListener(event -> {
            worker.cancel(true);
            progress.dispose();
        });
        worker.execute();
        progress.setVisible(true);

        Throwable problem = failure.get();
        if (problem instanceof CancellationException) return null;
        if (problem instanceof Exception exception) throw exception;
        if (problem != null) throw new Exception(problem);
        return result.get();
    }
}
