package multiurlcopy.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs the part of a copy that must not happen on the event thread.
 *
 * <p>Reading a {@code TableModel} has to stay on the Swing event thread -
 * Burp's history grows while you look at it, and reading it from
 * elsewhere could tear - but everything after that need not. Formatting
 * thousands of rows and, above all, writing to the system clipboard can
 * block: X11 clipboard ownership is a negotiation with another process.
 * Doing that on the event thread freezes Burp's whole UI.
 *
 * <p>The single thread is a daemon, so it can never hold the JVM open,
 * and it is still shut down explicitly when the extension unloads.
 */
public final class CopyWorker implements AutoCloseable {

    private final Consumer<String> clipboard;

    public CopyWorker() {
        this(ClipboardWriter::write);
    }

    CopyWorker(Consumer<String> clipboard) {
        this.clipboard = clipboard;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "multi-url-copy");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Builds the outcome off the event thread, then performs and reports it.
     * {@code build} must not touch Swing: do that before calling this.
     */
    public void submit(Supplier<CopyOutcome> build, CopyReporter reporter) {
        executor.execute(() -> {
            CopyOutcome outcome;
            try {
                outcome = build.get();
            } catch (RuntimeException e) {
                outcome = CopyOutcome.error("copy failed: " + e);
            }
            CopyService.perform(outcome, reporter, clipboard);
        });
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
