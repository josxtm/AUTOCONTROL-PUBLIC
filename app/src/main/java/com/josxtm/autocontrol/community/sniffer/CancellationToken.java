package com.josxtm.autocontrol.community.sniffer;

import java.util.ArrayList;
import java.util.List;

/** Register resource closure, not only Thread.interrupt(). Never call user code under the lock. */
public final class CancellationToken {
    public interface Registration extends AutoCloseable { @Override void close(); }
    private boolean cancelled;
    private final List<Runnable> callbacks = new ArrayList<>();
    public synchronized boolean isCancelled() { return cancelled; }
    public Registration onCancel(Runnable callback) {
        boolean run;
        synchronized (this) { run = cancelled; if (!run) callbacks.add(callback); }
        if (run) callback.run();
        return () -> { synchronized (this) { callbacks.remove(callback); } };
    }
    public void cancel() {
        List<Runnable> pending;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true; pending = new ArrayList<>(callbacks); callbacks.clear();
        }
        for (Runnable action : pending) {
            try { action.run(); } catch (RuntimeException ignored) { /* Continue closing others. */ }
        }
    }
}
