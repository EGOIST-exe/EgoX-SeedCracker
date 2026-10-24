package de.egox.seedcracker.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/** Bounded memory, exact search accounting, per-worker state and cooperative cancellation. */
public final class SearchJob implements AutoCloseable {
    public enum State { RUNNING, COMPLETE, CANCELLED, TRUNCATED, FAILED }
    public record Snapshot(String label, State state, long checked, long total, double seconds, List<Long> matches, String error) { }
    private final String label;
    private final long start, count;
    private final int limit;
    private final long started = System.nanoTime();
    private volatile long finished;
    private final AtomicLong cursor = new AtomicLong();
    private final LongAdder checked = new LongAdder();
    private final List<Long> matches = new ArrayList<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private volatile State state = State.RUNNING;
    private final ExecutorService executor;
    private final CompletableFuture<Void> done;

    public SearchJob(String label, long start, long count, int workers, int limit, Supplier<LongPredicate> factory) {
        if (count < 1 || count > (1L << 48) || workers < 1 || workers > 64 || limit < 1)
            throw new IllegalArgumentException("Invalid search range, worker count or result limit");
        Math.addExact(start, count - 1);
        this.label = label;
        this.start = start;
        this.count = count;
        this.limit = limit;
        executor = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "EgoX-search"); t.setDaemon(true); return t;
        });
        CompletableFuture<?>[] tasks = new CompletableFuture<?>[workers];
        for (int i = 0; i < workers; i++) tasks[i] = CompletableFuture.runAsync(() -> work(factory), executor);
        done = CompletableFuture.allOf(tasks).whenComplete((v, e) -> {
            if (failure.get() != null) state = State.FAILED;
            else if (state != State.TRUNCATED) state = cancelled.get() ? State.CANCELLED : State.COMPLETE;
            finished = System.nanoTime();
            executor.shutdown();
        });
    }

    private void work(Supplier<LongPredicate> factory) {
        try {
            LongPredicate predicate = factory.get();
            while (!cancelled.get()) {
                long from = cursor.getAndAdd(4096);
                if (from >= count) break;
                long to = Math.min(count, from + 4096);
                for (long offset = from; offset < to && !cancelled.get(); offset++) {
                    long candidate = start + offset;
                    boolean match = predicate.test(candidate);
                    checked.increment();
                    if (match) synchronized (matches) {
                        if (matches.size() < limit) matches.add(candidate);
                        else { state = State.TRUNCATED; cancelled.set(true); }
                    }
                }
            }
        } catch (CancellationException ignored) { cancelled.set(true); }
        catch (Throwable e) { failure.compareAndSet(null, e); cancelled.set(true); }
    }

    public Snapshot snapshot() {
        synchronized (matches) {
            Throwable error = failure.get();
            return new Snapshot(label, state, checked.sum(), count,
                ((finished == 0 ? System.nanoTime() : finished) - started) / 1e9,
                matches.stream().sorted().toList(), error == null ? "" : error.toString());
        }
    }
    public CompletableFuture<Void> completion() { return done; }
    public void cancel() { cancelled.set(true); }
    @Override public void close() { cancel(); }
}
