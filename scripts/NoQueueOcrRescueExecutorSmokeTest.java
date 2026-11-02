import com.vobichngan.kanzi.NoQueueOcrRescueExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone Java thread-safety / no-queue tests; never invokes camera or ONNX. */
public final class NoQueueOcrRescueExecutorSmokeTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static long deadlineMs(long ms) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
    }
    private static void waitForIdle(NoQueueOcrRescueExecutor executor) throws Exception {
        long ends = deadlineMs(4000);
        while (executor.isInFlight() && System.nanoTime() < ends) {
            Thread.sleep(5);
        }
        check(!executor.isInFlight(), "worker eventually returns to idle");
    }

    public static void main(String[] args) throws Exception {
        NoQueueOcrRescueExecutor off = new NoQueueOcrRescueExecutor();
        check(off.trySubmit(() -> "x", text -> {}, deadlineMs(500)) ==
                NoQueueOcrRescueExecutor.Submission.DISABLED,
                "extra worker is disabled by default");
        off.close();

        NoQueueOcrRescueExecutor normal = new NoQueueOcrRescueExecutor(
                new NoQueueOcrRescueExecutor.Config(true, 5, 300, 200));
        try {
            check(normal.trySubmit(() -> "x", text -> {}, deadlineMs(1)) ==
                    NoQueueOcrRescueExecutor.Submission.DEADLINE,
                    "deadline budget skips before queueing");
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> result = new AtomicReference<>();
            AtomicReference<String> workerName = new AtomicReference<>();
            check(normal.trySubmit(() -> {
                workerName.set(Thread.currentThread().getName());
                return "125mg";
            }, text -> {result.set(text);done.countDown();}, deadlineMs(1000)) ==
                    NoQueueOcrRescueExecutor.Submission.ACCEPTED,
                    "accept one timely call");
            check(done.await(4, TimeUnit.SECONDS), "callback completes");
            check("125mg".equals(result.get()), "valid result delivered");
            check(workerName.get().contains("selective-ocr-rescue"),
                    "never perform extra recognition on caller thread");
            waitForIdle(normal);

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger staleCallbacks = new AtomicInteger(0);
            check(normal.trySubmit(() -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "stale camera text";
            }, text -> staleCallbacks.incrementAndGet(), deadlineMs(1500)) ==
                    NoQueueOcrRescueExecutor.Submission.ACCEPTED,
                    "accept blocked first work");
            check(started.await(3, TimeUnit.SECONDS), "worker starts");
            long begin = System.nanoTime();
            check(normal.trySubmit(() -> "queued", text -> {}, deadlineMs(1000)) ==
                    NoQueueOcrRescueExecutor.Submission.BUSY,
                    "zero queued jobs even under load");
            long submitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin);
            check(submitMs < 200, "second submit never waits for running work");
            normal.invalidate();
            release.countDown();
            waitForIdle(normal);
            check(staleCallbacks.get() == 0, "invalidated frame never publishes");
        } finally {
            normal.close();
        }
        check(normal.trySubmit(() -> "x", text -> {}, deadlineMs(100)) ==
                NoQueueOcrRescueExecutor.Submission.CLOSED,
                "closed executor rejects any further work");

        NoQueueOcrRescueExecutor slow = new NoQueueOcrRescueExecutor(
                new NoQueueOcrRescueExecutor.Config(true, 5, 10, 250));
        try {
            CountDownLatch slowDone = new CountDownLatch(1);
            AtomicInteger tooSlowOutput = new AtomicInteger();
            check(slow.trySubmit(() -> {
                Thread.sleep(70);
                slowDone.countDown();
                return "too late";
            }, text -> tooSlowOutput.incrementAndGet(), deadlineMs(1000)) ==
                    NoQueueOcrRescueExecutor.Submission.ACCEPTED,
                    "slow work is allowed for isolated test");
            check(slowDone.await(4, TimeUnit.SECONDS), "slow worker finished");
            waitForIdle(slow);
            check(tooSlowOutput.get() == 0,
                    "late result cannot overwrite primary recognition");
            check(slow.trySubmit(() -> "next", text -> {}, deadlineMs(1000)) ==
                    NoQueueOcrRescueExecutor.Submission.COOLDOWN,
                    "work exceeding budget enters cooldown");
        } finally {
            slow.close();
        }

        NoQueueOcrRescueExecutor expired = new NoQueueOcrRescueExecutor(
                new NoQueueOcrRescueExecutor.Config(true, 5, 300, 50));
        try {
            AtomicInteger delivered = new AtomicInteger(0);
            check(expired.trySubmit(() -> {
                Thread.sleep(50);
                return "deadline passed";
            }, text -> delivered.incrementAndGet(), deadlineMs(15)) ==
                    NoQueueOcrRescueExecutor.Submission.ACCEPTED,
                    "work admitted when it has minimum budget");
            waitForIdle(expired);
            check(delivered.get() == 0, "expired result must not publish");
        } finally {
            expired.close();
        }
        System.out.println("NO_QUEUE_OCR_RESCUE_EXECUTOR=PASS");
    }
}
