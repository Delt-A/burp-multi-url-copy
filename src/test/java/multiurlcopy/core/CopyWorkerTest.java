package multiurlcopy.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyWorkerTest {

    private static final class Latched implements CopyReporter {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<CopyOutcome> seen = new AtomicReference<>();
        final AtomicReference<String> thread = new AtomicReference<>();

        @Override
        public void report(CopyOutcome outcome) {
            seen.set(outcome);
            thread.set(Thread.currentThread().getName());
            done.countDown();
        }
    }

    @Test
    void theOutcomeIsBuiltAndReportedOffTheCallingThread() throws Exception {
        Latched reporter = new Latched();
        try (CopyWorker worker = new CopyWorker()) {
            worker.submit(() -> CopyOutcome.of("https://a.com/x", 1, 1), reporter);

            assertTrue(reporter.done.await(5, TimeUnit.SECONDS), "worker never reported");
        }
        assertEquals("copied 1 URL", reporter.seen.get().statusLine());
        assertNotEquals(Thread.currentThread().getName(), reporter.thread.get());
        assertEquals("multi-url-copy", reporter.thread.get());
    }

    @Test
    void aBuilderThatThrowsIsReportedRatherThanLost() throws Exception {
        Latched reporter = new Latched();
        try (CopyWorker worker = new CopyWorker()) {
            worker.submit(() -> {
                throw new IllegalStateException("boom");
            }, reporter);

            assertTrue(reporter.done.await(5, TimeUnit.SECONDS), "worker never reported");
        }
        assertTrue(reporter.seen.get().statusLine().startsWith("nothing copied - copy failed: "));
    }
}
