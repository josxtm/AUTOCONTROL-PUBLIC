package com.josxtm.autocontrol.community.sniffer;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One calling thread per command; one shared deadline supervisor, no per-command reader thread. */
final class CommandRunner {
    interface Channel extends AutoCloseable {
        InputStream stdout() throws Exception;
        void stderr(OutputStream sink) throws Exception;
        void connect(int timeoutMillis) throws Exception;
        boolean closed();
        int exitCode();
        @Override void close();
    }
    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "ssh-command-deadlines"); t.setDaemon(true); return t;
    });
    static { DEADLINES.setRemoveOnCancelPolicy(true); }
    static final int MAX_OUTPUT = 65536;
    static DiagnosticResult run(Channel channel, boolean ping, long timeoutMillis,
                                CancellationToken cancellation, SshTransport.Output output) {
        if (timeoutMillis < 1 || timeoutMillis > 60000) {
            channel.close(); throw new IllegalArgumentException("Límite de comando no válido");
        }
        long start = System.nanoTime();
        AtomicReference<DiagnosticStatus> stopped = new AtomicReference<>();
        AtomicReference<InputStream> stream = new AtomicReference<>();
        AtomicBoolean cleaned = new AtomicBoolean();
        AtomicBoolean stderr = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) return;
            // Close the local input pipe first, so a blocked read wakes before the SSH close packet.
            closeQuietly(stream.get());
            try { channel.close(); } catch (RuntimeException ignored) { }
        };
        Runnable cancel = () -> { stopped.compareAndSet(null, DiagnosticStatus.CANCELLED); cleanup.run(); };
        ScheduledFuture<?> timer = DEADLINES.schedule(() -> {
            stopped.compareAndSet(null, DiagnosticStatus.TIMEOUT); cleanup.run();
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        Integer exit = null;
        DiagnosticStatus status = DiagnosticStatus.SSH_ERROR;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CancellationToken.Registration ignored = cancellation.onCancel(cancel)) {
            if (stopped.get() == null) {
                InputStream input = channel.stdout(); stream.set(input);
                if (cleaned.get()) closeQuietly(input);
                channel.stderr(new OutputStream() {
                    @Override public void write(int b) { stderr.set(true); }
                    @Override public void write(byte[] b, int off, int len) { if (len > 0) stderr.set(true); }
                });
                if (stopped.get() == null) {
                    long remaining = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    if (remaining <= 0) stopped.compareAndSet(null, DiagnosticStatus.TIMEOUT);
                    else channel.connect((int)Math.min(10000, remaining));
                }
                byte[] buffer = new byte[2048];
                boolean eof = false;
                while (stopped.get() == null) {
                    if (System.nanoTime()-start >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis)) {
                        stopped.compareAndSet(null, DiagnosticStatus.TIMEOUT); break;
                    }
                    if (Thread.currentThread().isInterrupted()) { cancel.run(); break; }
                    // JSch supplies a local pipe. Polling also bounds the wait for EOF and exit status.
                    int available = input.available();
                    if (available > 0 && !eof) {
                        int n = input.read(buffer, 0, Math.min(buffer.length, available));
                        if (n < 0) eof = true;
                        else {
                            int allowed = Math.min(n, MAX_OUTPUT - bytes.size());
                            bytes.write(buffer, 0, allowed);
                            if (allowed != n || bytes.size() == MAX_OUTPUT) break; // Truncated is an error, never success.
                        }
                    } else if (channel.closed()) {
                        exit = channel.exitCode();
                        status = DiagnosticResult.exitStatus(ping, exit, stderr.get());
                        break;
                    } else Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); stopped.compareAndSet(null, DiagnosticStatus.CANCELLED);
        } catch (Exception ignored) {
            status = DiagnosticStatus.SSH_ERROR; // Never expose raw exception text or server secrets.
        } finally {
            timer.cancel(false); cleanup.run();
        }
        // Preserve partial output even if closing the pipe interrupted a read/available call.
        if (output != null && bytes.size() > 0) output.onText(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        DiagnosticStatus terminal = stopped.get();
        return new DiagnosticResult("", null, DiagnosticSource.MANGO_SSH,
            terminal == null ? status : terminal, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start), exit);
    }
    static void closeQuietly(Closeable resource) {
        if (resource != null) try { resource.close(); } catch (IOException | RuntimeException ignored) { }
    }
    private CommandRunner() { }
}
