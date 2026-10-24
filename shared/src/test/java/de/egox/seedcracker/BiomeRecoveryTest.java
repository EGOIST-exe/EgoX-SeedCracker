package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class BiomeRecoveryTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void fullUpper16HashLiftingFindsSignedSeedsAndReportsEveryCandidate() {
        for (long seed : new long[]{Long.MIN_VALUE, -5837105212680014280L, Long.MAX_VALUE}) {
            var progress = new CrackProgress();
            long hash = SeedHash.of(seed);
            var result = WorldSeedLifting.search(Set.of(seed & LegacyRandom.MASK), 0, 65535, 2, 32,
                () -> value -> SeedHash.of(value) == hash, () -> false, progress::update, "Hash");
            assertEquals(SearchJob.State.COMPLETE, result.state()); assertEquals(65536, result.checked());
            assertEquals(List.of(seed), result.seeds()); assertEquals(1, progress.snapshot().fraction());
        }
    }
    @Test void moreThan1000LowerCandidatesAreNotDiscarded() {
        var lower = new ArrayList<Long>(); for (long i = 0; i < 1708; i++) lower.add(i);
        long expected = (1L << 48) | 1707L;
        var result = WorldSeedLifting.search(lower, 0, 1, 2, 32, () -> seed -> seed == expected, () -> false, (p,c,t) -> {}, "Hash");
        assertEquals(SearchJob.State.COMPLETE, result.state()); assertEquals(3416, result.checked()); assertEquals(List.of(expected), result.seeds());
    }
    @Test void cancellationAndTruncationCannotLookLikeCompletedBiomeSearches() {
        var cancelled = WorldSeedLifting.search(Set.of(123L), 0, 65535, 1, 32, () -> seed -> true, () -> true, (p,c,t) -> {}, "Biomes");
        assertEquals(SearchJob.State.CANCELLED, cancelled.state()); assertEquals(0, cancelled.checked());
        var truncated = WorldSeedLifting.search(Set.of(123L), 0, 65535, 1, 2, () -> seed -> true, () -> false, (p,c,t) -> {}, "Biomes");
        assertEquals(SearchJob.State.TRUNCATED, truncated.state()); assertEquals(2, truncated.seeds().size()); assertTrue(truncated.checked() < truncated.total());
        var stop = new AtomicBoolean();
        var active = WorldSeedLifting.search(Set.of(123L), 0, 65535, 1, 32, () -> seed -> { stop.set(true); return false; }, stop::get, (p,c,t) -> {}, "Biomes");
        assertEquals(SearchJob.State.CANCELLED, active.state()); assertTrue(active.checked() < active.total());
    }
    @Test void invalidAndDuplicateLiftingInputsAreHandledExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> WorldSeedLifting.search(Set.of(-1L), 0, 65535, 1, 32, () -> s -> false, () -> false, (p,c,t) -> {}, "X"));
        assertThrows(IllegalArgumentException.class, () -> WorldSeedLifting.search(Set.of(0L), -1, 65535, 1, 32, () -> s -> false, () -> false, (p,c,t) -> {}, "X"));
        var result = WorldSeedLifting.search(List.of(0L, 0L), 65535, 65535, 1, 32, () -> s -> true, () -> false, (p,c,t) -> {}, "X");
        assertEquals(1, result.total()); assertEquals(List.of(0xFFFF000000000000L), result.seeds());
    }
    @Test void earlySamplesPreserveBiomeDiversityAndGeographicCoverage() {
        var samples = new ArrayList<BiomeObservation>();
        for (int i = 0; i < 100; i++) samples.add(new BiomeObservation(i, 16, 0, "minecraft:plains"));
        var desert = new BiomeObservation(0, 16, 0, "minecraft:desert");
        var distant = new BiomeObservation(100000, 16, -100000, "minecraft:plains");
        samples.add(desert); samples.add(distant);
        var selected = BiomeEvidence.select(samples, 4);
        assertEquals(4, selected.size()); assertTrue(selected.contains(desert)); assertTrue(selected.contains(distant));
        assertTrue(BiomeEvidence.sufficient(samples)); assertFalse(BiomeEvidence.sufficient(samples.subList(0, 8)));
        assertFalse(BiomeEvidence.sufficient(List.of(desert, distant)));
        assertEquals(102, samples.size());
    }
    @Test void fullSnapshotIsCheckedAfterTheDiversePrefilter() {
        var a = new BiomeObservation(0, 0, 0, "minecraft:plains");
        var b = new BiomeObservation(5, 5, 5, "minecraft:desert");
        assertFalse(BiomeEvidence.matches(List.of(a), List.of(a,b), sample -> "minecraft:plains"));
        assertTrue(BiomeEvidence.matches(List.of(a), List.of(a,b), BiomeObservation::biome));
        var comparison = BiomeEvidence.compare(List.of(a,b), sample -> "minecraft:plains");
        assertEquals(1, comparison.matching()); assertFalse(comparison.matches());
    }
    @Test void nativeBiomesFindSignedWorldSeedWithoutUsingTheReceivedHash() {
        long seed = -5837105212680014280L;
        var coordinates = new ArrayList<BiomeObservation>();
        for (int i = 0; i < 16; i++) coordinates.add(new BiomeObservation(i * 913 - 7000, i % 3 * 16, i * -719 + 3000, ""));
        var samples = NativeBiomes.sample(seed, coordinates, "overworld");
        assertTrue(BiomeEvidence.sufficient(samples));
        var selected = BiomeEvidence.select(samples, 8);
        int upper = (int)(seed >>> 48);
        var result = WorldSeedLifting.search(Set.of(seed & LegacyRandom.MASK, (seed + 1) & LegacyRandom.MASK), upper - 1, upper + 1, 2, 32,
            () -> candidate -> NativeBiomes.matches(candidate, selected, samples, "overworld"), () -> false, (p,c,t) -> {}, "Biomes");
        assertEquals(SearchJob.State.COMPLETE, result.state()); assertEquals(6, result.checked()); assertEquals(List.of(seed), result.seeds());
        assertTrue(NativeBiomes.compare(seed, samples, "overworld").matches());
        assertEquals("HASH_WIDERSPRUCH", AutomaticBiomes.candidate(seed, SeedHash.of(seed + 1), samples.size()).hashStatus());
        assertEquals("HASH_FEHLT", AutomaticBiomes.candidate(seed, null, samples.size()).hashStatus());
        assertEquals("HASH_PASSEND", AutomaticBiomes.candidate(seed, SeedHash.of(seed), samples.size()).hashStatus());
        assertEquals(Long.toString(seed), AutomaticBiomes.candidate(seed, null, samples.size()).worldSeed());
    }
    @Test void failedNativePredicatesAreReportedInsteadOfReturningNoMatches() {
        var result = WorldSeedLifting.search(Set.of(123L), 0, 10, 1, 32,
            () -> seed -> { throw new IllegalStateException("fixture"); }, () -> false, (p,c,t) -> {}, "Biomes");
        assertEquals(SearchJob.State.FAILED, result.state()); assertTrue(result.error().contains("fixture"));
    }
    @Test void previousServerWorkersCannotPublishIntoTheNewSessionsBiomeReport() {
        AutomaticBiomes.reset(); long previous = AutomaticBiomes.token();
        var stale = new AutomaticBiomes.Report("COMPLETE", "overworld", 8, 65536, 65536,
            List.of(new AutomaticBiomes.Candidate("-9223372036854775808", "HASH_WIDERSPRUCH", 8)), "old server");
        AutomaticBiomes.reset();
        assertFalse(AutomaticBiomes.publish(previous, stale));
        assertEquals("BEREIT", AutomaticBiomes.report().state()); assertTrue(AutomaticBiomes.report().candidates().isEmpty());
    }
}
