package de.egox.seedcracker.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

/** Primitive 48-bit lifting with explicit coverage of Java nextInt rejection branches. */
public final class LiftingSearch {
    public record Constraint(StructureObservation observation, long salt) { }
    @FunctionalInterface public interface Progress { void update(String phase, long checked, long total); }
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INVERSE = 0xDFE05BCB1365L;
    private LiftingSearch() { }

    public static boolean lowBitsMayMatch(long lower, List<Constraint> constraints, int power, LegacyRandom random) {
        int mask = (1 << power) - 1;
        for (Constraint c : constraints) {
            var o = c.observation(); var p = o.placement();
            int regionX = Math.floorDiv(o.chunkX(), p.spacing()), regionZ = Math.floorDiv(o.chunkZ(), p.spacing());
            random.setSeed(lower + c.salt() + regionX * 341873128712L + regionZ * 132897987541L);
            // Deliberately use raw draws here. Actual rejection cases are covered separately below.
            if ((random.next31() & mask) != (Math.floorMod(o.chunkX(), p.spacing()) & mask)) return false;
            if ((random.next31() & mask) != (Math.floorMod(o.chunkZ(), p.spacing()) & mask)) return false;
        }
        return true;
    }
    public static long previous(long state) { return ((state - 11) * INVERSE) & LegacyRandom.MASK; }

    /** Enumerates all seeds that reject on the first raw X draw or first raw Z draw. */
    public static void exceptionalSeeds(Constraint c, BooleanSupplier cancelled, LongConsumer accept) {
        var o = c.observation(); var p = o.placement();
        int bound = p.spacing() - p.separation();
        long remainder = (1L << 31) % bound;
        long region = c.salt() + Math.floorDiv(o.chunkX(), p.spacing()) * 341873128712L
            + Math.floorDiv(o.chunkZ(), p.spacing()) * 132897987541L;
        for (long top = (1L << 31) - remainder; top < (1L << 31) && !cancelled.getAsBoolean(); top++) {
            for (long bottom = 0; bottom < (1L << 17) && !cancelled.getAsBoolean(); bottom++) {
                long state = (top << 17) | bottom;
                long beforeFirst = previous(state);
                accept.accept(((beforeFirst ^ MULTIPLIER) - region) & LegacyRandom.MASK);
                accept.accept(((previous(beforeFirst) ^ MULTIPLIER) - region) & LegacyRandom.MASK);
            }
        }
    }

    public static Set<Long> search(List<Constraint> constraints, long min, long max, int workers,
        BooleanSupplier cancelled, Supplier<LongPredicate> additionalChecks) {
        return search(constraints, min, max, workers, cancelled, additionalChecks, (p, n, t) -> { });
    }
    public static Set<Long> search(List<Constraint> constraints, long min, long max, int workers,
        BooleanSupplier cancelled, Supplier<LongPredicate> additionalChecks, Progress progress) {
        if (constraints.isEmpty() || min < 0 || max > LegacyRandom.MASK || max < min || workers < 1)
            throw new IllegalArgumentException("Invalid lifting inputs");
        int power = 3;
        for (var c : constraints) {
            var p = c.observation().placement();
            if (!ObservationBits.liftable(p)) throw new IllegalArgumentException("Lifting requires non-power-of-two even linear placements");
            power = Math.min(power, Integer.numberOfTrailingZeros(p.spacing() - p.separation()));
        }
        if (power < 1) throw new IllegalArgumentException("Lifting requires even offset bounds");
        int bits = 17 + power;
        List<Long> lower = new ArrayList<>();
        var random = new LegacyRandom();
        long lowerCount = 1L << bits;
        progress.update("1/4 Untere Bits filtern", 0, lowerCount);
        for (long value = 0; value < (1L << bits) && !cancelled.getAsBoolean(); value++) {
            if (lowBitsMayMatch(value, constraints, power, random)) lower.add(value);
            if ((value & 4095) == 4095) progress.update("1/4 Untere Bits filtern", value + 1, lowerCount);
        }
        if (cancelled.getAsBoolean()) return Set.of();
        long firstUpper = min >>> bits;
        long upperCount = (max >>> bits) - firstUpper + 1;
        long total = Math.multiplyExact(upperCount, lower.size());
        AtomicLong cursor = new AtomicLong();
        AtomicLong completed = new AtomicLong();
        progress.update("2/4 Struktur-Seeds pruefen", 0, total);
        Set<Long> result = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> { var t = new Thread(r, "EgoX-lifting"); t.setDaemon(true); return t; });
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) tasks.add(pool.submit(() -> {
                var rng = new LegacyRandom();
                var additional = additionalChecks.get();
                while (!cancelled.getAsBoolean()) {
                    long from = cursor.getAndAdd(16384);
                    if (from >= total) break;
                    long checked = 0;
                    for (long index = from; index < Math.min(total, from + 16384) && !cancelled.getAsBoolean(); index++) {
                        long seed = ((firstUpper + index / lower.size()) << bits) | lower.get((int)(index % lower.size()));
                        if (seed >= min && seed <= max && matches(seed, constraints, rng) && additional.test(seed)) result.add(seed);
                        checked++;
                    }
                    progress.update("2/4 Struktur-Seeds pruefen", completed.addAndGet(checked), total);
                }
            }));
            for (Future<?> task : tasks) task.get();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Lifting interrupted", e); }
        catch (ExecutionException e) { throw new IllegalStateException("Lifting failed", e.getCause()); }
        finally { pool.shutdownNow(); }

        // A rejection in either coordinate can break the usual low-bit congruences. Test those
        // finite exceptional states explicitly so the fast prefilter cannot lose the real seed.
        var additional = additionalChecks.get();
        long exceptionsTotal = 0;
        for (var c : constraints) exceptionsTotal += ((1L << 31) % (c.observation().placement().spacing() - c.observation().placement().separation())) * (1L << 18);
        final long exceptionCount = exceptionsTotal;
        final long[] exceptionChecked = {0};
        progress.update("3/4 RNG-Sonderfaelle pruefen", 0, exceptionCount);
        for (Constraint c : constraints) exceptionalSeeds(c, cancelled, seed -> {
            if (seed >= min && seed <= max && matches(seed, constraints, random) && additional.test(seed)) result.add(seed);
            if ((++exceptionChecked[0] & 4095) == 0) progress.update("3/4 RNG-Sonderfaelle pruefen", exceptionChecked[0], exceptionCount);
        });
        progress.update("3/4 RNG-Sonderfaelle pruefen", exceptionChecked[0], exceptionCount);
        return result;
    }
    private static boolean matches(long seed, List<Constraint> constraints, LegacyRandom random) {
        for (Constraint c : constraints) if (!c.observation().matches(seed, c.salt(), random)) return false;
        return true;
    }
}
