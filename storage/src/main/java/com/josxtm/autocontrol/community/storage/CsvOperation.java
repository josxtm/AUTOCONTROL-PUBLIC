package com.josxtm.autocontrol.community.storage;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One import's cancellation, stream ownership and throttled, immutable progress snapshots. */
public final class CsvOperation {
    public static final class Cancelled extends IOException {
        public Cancelled() { super("Operación cancelada. Datos anteriores conservados."); }
    }
    public static final class Progress {
        public final long read, selected, processed, total, bytes, size;
        public final boolean writing;
        Progress(CsvOperation op) {
            read = op.read; selected = op.selected; processed = op.processed;
            total = op.total; bytes = op.bytes; size = op.size; writing = op.writing;
        }
    }
    public interface Listener { void update(Progress progress); }
    private static final ExecutorService CLOSER = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "csv-stream-close"); thread.setDaemon(true); return thread;
    });
    private volatile boolean cancelled;
    private boolean committed;
    private Closeable stream;
    private final Listener listener;
    private long last, read, selected, processed, total, bytes, size = -1;
    private boolean writing;
    public CsvOperation() { this(null); }
    public CsvOperation(Listener listener) { this.listener = listener; }
    public boolean isCancelled() { return cancelled; }
    public synchronized void cancel() {
        if (committed || cancelled) return;
        cancelled = true;
        Closeable active = stream;
        if (active != null) CLOSER.execute(() -> { try { active.close(); } catch (IOException ignored) { } });
    }
    public synchronized void attach(Closeable value) throws IOException {
        check(); stream = value;
    }
    public synchronized void detach() { stream = null; }
    public void check() throws Cancelled { if (cancelled) throw new Cancelled(); }
    /** Linearizes cancellation against committing; cancellation accepted before here always rolls back. */
    public synchronized void commit(Runnable action) throws IOException {
        check(); action.run(); committed = true;
    }
    public void start(boolean apply, long expected, long length) throws IOException {
        check(); writing = apply; total = expected; size = length;
        read = selected = processed = bytes = 0; publish(true);
    }
    public void bytes(long value) { bytes = value; }
    public void reading(long rows, long matching) throws IOException {
        check(); read = rows; selected = matching; publish(false);
    }
    public void processed() throws IOException {
        check(); processed++;
        if (writing && total > 0 && processed > total)
            throw new IOException("El archivo cambió desde la vista previa.");
        publish(false);
    }
    public void verifyRows(long rows) throws IOException {
        check();
        if (writing && total > 0 && rows != total)
            throw new IOException("El archivo cambió desde la vista previa.");
    }
    public void publish(boolean force) {
        long now = System.nanoTime();
        if (listener != null && (force || now - last >= 150_000_000L)) {
            last = now; listener.update(new Progress(this));
        }
    }
}
