package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @Test void recoveryGroupsKeepOtherFamiliesSaltChangesOutOfFallback() {
        var all = observations(12345).stream().map(LiftingSearch.Constraint::observation).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        var other = new Placement("test:other", 32, 8, 98765, Placement.Spread.LINEAR);
        all.add(new StructureObservation(other, 0, 0, "minecraft:overworld"));
        var groups = RecoveryGroups.plan(all);
        assertEquals(2, groups.size()); assertEquals(7, groups.getFirst().size()); assertEquals(6, groups.getLast().size());
        assertTrue(groups.getLast().stream().allMatch(o -> o.placement().id().equals("test:temple")));
    }
    @Test void liftingReportsItsPhasesAndRejectsPowerOfTwoRemainderAssumptions() {
        var progress = new CrackProgress(); var phases = new HashSet<String>();
        var result = LiftingSearch.search(observations(12345), 0, 20000, 4, () -> false, () -> seed -> true, (phase, done, total) -> {
            synchronized(phases) { phases.add(phase); }
            assertTrue(done >= 0 && done <= total); progress.update(phase, done, total);
        });
        assertEquals(Set.of(12345L), result); assertEquals(3, phases.size());
        assertEquals(progress.snapshot().total(), progress.snapshot().checked());
        var power = new Placement("test:power", 32, 16, 0, Placement.Spread.LINEAR);
        assertThrows(IllegalArgumentException.class, () -> LiftingSearch.search(List.of(new LiftingSearch.Constraint(new StructureObservation(power,0,0,"a"),0)),0,100,1,()->false,()->x->true));
    }
    @Test void legacyRandomMatchesJdkIncludingRejection() {
        int[] bounds = {1, 2, 20, 24, 60, 100, 1024, 1073741825, Integer.MAX_VALUE};
        for (long seed : new long[]{0, -1, Long.MIN_VALUE, Long.MAX_VALUE, 1234567890}) {
            for (int bound : bounds) {
                var expected = new Random(seed); var actual = new LegacyRandom(); actual.setSeed(seed);
                for (int i = 0; i < 1000; i++) assertEquals(expected.nextInt(bound), actual.nextInt(bound));
            }
        }
    }
    private static List<LiftingSearch.Constraint> observations(long seed) {
        var placement = new Placement("test:temple", 32, 8, 14357617, Placement.Spread.LINEAR);
        List<LiftingSearch.Constraint> result = new ArrayList<>();
        for (int[] region : new int[][]{{0,0},{-1,2},{4,-7},{-100,-22},{89,42},{17,4}}) {
            var random = new Random(seed + placement.vanillaSalt() + region[0] * 341873128712L + region[1] * 132897987541L);
            int x = region[0] * 32 + random.nextInt(24), z = region[1] * 32 + random.nextInt(24);
            result.add(new LiftingSearch.Constraint(new StructureObservation(placement, x, z, "minecraft:overworld"), placement.vanillaSalt()));
        }
        return result;
    }
    @Test void liftingMatchesExhaustiveReferenceInBoundedRange() {
        var observations = observations(12345);
        Set<Long> expected = new HashSet<>();
        for (long seed = 0; seed < 20000; seed++) {
            boolean matches = true;
            for (var c : observations) {
                var o = c.observation(); int rx = Math.floorDiv(o.chunkX(), 32), rz = Math.floorDiv(o.chunkZ(), 32);
                var r = new Random(seed + c.salt() + rx * 341873128712L + rz * 132897987541L);
                if (rx * 32 + r.nextInt(24) != o.chunkX() || rz * 32 + r.nextInt(24) != o.chunkZ()) { matches = false; break; }
            }
            if (matches) expected.add(seed);
        }
        assertEquals(Set.of(12345L), expected);
        assertEquals(expected, LiftingSearch.search(observations, 0, 19999, 4, () -> false, () -> x -> true));
    }
    @Test void liftingRecoversSeedsWithRejectedFirstDraw() {
        long seed = 0;
        boolean found = false;
        for (long bottom = 0; bottom < 1000; bottom++) {
            long rejected = ((long)Integer.MAX_VALUE << 17) | bottom;
            seed = ((LiftingSearch.previous(rejected) ^ 0x5DEECE66DL) - 14357617) & LegacyRandom.MASK;
            if (!LiftingSearch.lowBitsMayMatch(seed & ((1 << 20) - 1), observations(seed), 3, new LegacyRandom())) { found = true; break; }
        }
        assertTrue(found, "Fixture must exercise the exceptional branch");
        assertEquals(Set.of(seed), LiftingSearch.search(observations(seed), seed, seed, 2, () -> false, () -> x -> true));
    }
    @Test void searchesCountEveryCandidateAndRetainSignedLongPrecision() throws Exception {
        var job = new SearchJob("range", Long.MIN_VALUE, 10000, 4, 5, () -> x -> x == Long.MIN_VALUE + 4321);
        job.completion().get(5, TimeUnit.SECONDS);
        assertEquals(SearchJob.State.COMPLETE, job.snapshot().state());
        assertEquals(10000, job.snapshot().checked());
        assertEquals(List.of(Long.MIN_VALUE + 4321), job.snapshot().matches());
    }
    @Test void resultLimitIsReportedAsTruncation() throws Exception {
        var job = new SearchJob("many", 0, 100000, 4, 2, () -> x -> true);
        job.completion().get(5, TimeUnit.SECONDS);
        assertEquals(SearchJob.State.TRUNCATED, job.snapshot().state());
        assertEquals(2, job.snapshot().matches().size());
        assertTrue(job.snapshot().checked() < 100000);
    }
    @Test void exceptionsAreReportedAndCancellationIsCooperative() throws Exception {
        var failed = new SearchJob("fail", 0, 100, 2, 5, () -> x -> { throw new IllegalArgumentException("fixture"); });
        failed.completion().get(5, TimeUnit.SECONDS);
        assertEquals(SearchJob.State.FAILED, failed.snapshot().state());
        assertTrue(failed.snapshot().error().contains("fixture"));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var cancelled = new SearchJob("cancel", 0, 10000000, 2, 5, () -> x -> {
            entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return false;
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS)); cancelled.cancel(); release.countDown();
        cancelled.completion().get(5, TimeUnit.SECONDS);
        assertEquals(SearchJob.State.CANCELLED, cancelled.snapshot().state());
        assertTrue(cancelled.snapshot().checked() < 10000000);
    }
    @Test void rangeOverflowIsRejected() {
        assertThrows(ArithmeticException.class, () -> new SearchJob("overflow", Long.MAX_VALUE, 2, 1, 1, () -> x -> true));
    }
}
