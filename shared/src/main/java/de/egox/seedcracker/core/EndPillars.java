package de.egox.seedcracker.core;

import java.util.*;

/** The pillar permutation constrains 16 bits of the state after the world's first nextLong. */
public final class EndPillars {
    private EndPillars() { }
    public static int[] order(int pillarSeed) {
        int[] order = {0,1,2,3,4,5,6,7,8,9};
        var random = new LegacyRandom(); random.setSeed(pillarSeed);
        for (int count = 10; count > 1; count--) { int index = random.nextInt(count), value = order[count - 1]; order[count - 1] = order[index]; order[index] = value; }
        return order;
    }
    public static List<Integer> candidates(int[] heights) {
        if (heights.length != 10) throw new IllegalArgumentException("Ten pillar heights required");
        var matches = new ArrayList<Integer>();
        for (int seed = 0; seed < 65536; seed++) {
            int[] order = order(seed); boolean valid = true;
            for (int i = 0; i < 10; i++) if (heights[i] >= 0 && heights[i] != 76 + 3 * order[i]) { valid = false; break; }
            if (valid) matches.add(seed);
        }
        return List.copyOf(matches);
    }
    public static long stateAfterLong(long seed) {
        long state = (seed ^ 0x5DEECE66DL) & LegacyRandom.MASK;
        state = (state * 0x5DEECE66DL + 11) & LegacyRandom.MASK;
        return (state * 0x5DEECE66DL + 11) & LegacyRandom.MASK;
    }
    public static long seedForIndex(int pillarSeed, long index) {
        if (pillarSeed < 0 || pillarSeed > 65535 || index < 0 || index >= 1L << 32) throw new IllegalArgumentException("Pillar index outside its state family");
        long state = ((index >>> 16) << 32) | ((long)pillarSeed << 16) | (index & 65535);
        return (LiftingSearch.previous(LiftingSearch.previous(state)) ^ 0x5DEECE66DL) & LegacyRandom.MASK;
    }
}
