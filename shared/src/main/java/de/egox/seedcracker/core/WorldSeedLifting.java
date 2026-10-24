package de.egox.seedcracker.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Enumerates complete 64-bit seeds from explicitly supplied lower-48-bit candidates. */
public final class WorldSeedLifting {
    public record Result(SearchJob.State state, long checked, long total, List<Long> seeds, String error) { }
    private WorldSeedLifting() { }
    public static long seedAt(List<Long> lower, int firstUpper, int upperCount, long index) {
        return ((long)(firstUpper + index % upperCount) << 48) | lower.get((int)(index / upperCount));
    }
    public static Result search(Collection<Long> candidates, int firstUpper, int lastUpper, int workers, int resultLimit,
                                Supplier<LongPredicate> test, BooleanSupplier cancelled, LiftingSearch.Progress progress, String phase) {
        if (firstUpper < 0 || lastUpper > 65535 || firstUpper > lastUpper) throw new IllegalArgumentException("Invalid upper-16-bit range");
        var lower = candidates.stream().distinct().sorted().toList();
        if (lower.stream().anyMatch(s -> s < 0 || s > LegacyRandom.MASK)) throw new IllegalArgumentException("Invalid lower-48-bit seed");
        int upperCount = lastUpper - firstUpper + 1;
        long total = Math.multiplyExact((long)lower.size(), upperCount);
        if (total == 0) return new Result(SearchJob.State.COMPLETE, 0, 0, List.of(), "");
        if (cancelled.getAsBoolean()) return new Result(SearchJob.State.CANCELLED, 0, total, List.of(), "");
        var job = new SearchJob(phase, 0, total, workers, resultLimit, () -> {
            var predicate = test.get();
            return index -> {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                return predicate.test(seedAt(lower, firstUpper, upperCount, index));
            };
        });
        try {
            while (!job.completion().isDone()) {
                if (cancelled.getAsBoolean()) job.cancel();
                var snapshot = job.snapshot(); progress.update(phase, snapshot.checked(), total);
                try { job.completion().get(100, TimeUnit.MILLISECONDS); }
                catch (TimeoutException ignored) { }
            }
            job.completion().join();
            var snapshot = job.snapshot(); progress.update(phase, snapshot.checked(), total);
            return new Result(snapshot.state(), snapshot.checked(), total,
                snapshot.matches().stream().map(index -> seedAt(lower, firstUpper, upperCount, index)).toList(), snapshot.error());
        } catch (InterruptedException e) {
            job.cancel(); Thread.currentThread().interrupt(); throw new IllegalStateException("World-seed search interrupted", e);
        } catch (ExecutionException e) { throw new IllegalStateException("World-seed search failed", e.getCause()); }
        finally { job.close(); }
    }
}
