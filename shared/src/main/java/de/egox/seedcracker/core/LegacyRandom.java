package de.egox.seedcracker.core;

/** Exact java.util.Random algorithm used by Minecraft random-spread placements. */
public final class LegacyRandom {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    public static final long MASK = (1L << 48) - 1;
    private long state;

    public void setSeed(long seed) { state = (seed ^ MULTIPLIER) & MASK; }
    public int next31() {
        state = (state * MULTIPLIER + 11) & MASK;
        return (int)(state >>> 17);
    }
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        int bits = next31();
        if ((bound & -bound) == bound) return (int)((bound * (long)bits) >> 31);
        int value = bits % bound;
        while (bits - value + (bound - 1) < 0) {
            bits = next31();
            value = bits % bound;
        }
        return value;
    }
}
