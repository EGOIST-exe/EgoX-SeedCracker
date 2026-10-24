package de.egox.seedcracker.core;

/** Copyable xoroshiro128++ state with the bit-based WorldgenRandom draw interface. */
public final class XoroshiroWorldgen {
    public record State(long low, long high) { }
    private long low, high;
    public XoroshiroWorldgen(long seed) {
        long base = seed ^ 0x6A09E667F3BCC909L;
        low = mix(base); high = mix(base + 0x9E3779B97F4A7C15L);
        if ((low | high) == 0) { low = 0x9E3779B97F4A7C15L; high = 0x6A09E667F3BCC909L; }
    }
    public XoroshiroWorldgen(State state) { low = state.low(); high = state.high(); }
    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
    public State state() { return new State(low, high); }
    public long raw() {
        long result = Long.rotateLeft(low + high, 17) + low, xor = low ^ high;
        low = Long.rotateLeft(low, 49) ^ xor ^ (xor << 21); high = Long.rotateLeft(xor, 28); return result;
    }
    public int bits(int count) { return (int)(raw() >>> (64 - count)); }
    public int nextInt(int bound) {
        if (bound < 1) throw new IllegalArgumentException("Positive bound required");
        int bits = bits(31), remainder;
        if ((bound & -bound) == bound) return (int)((bound * (long)bits) >> 31);
        while (bits - (remainder = bits % bound) + bound - 1 < 0) bits = bits(31);
        return remainder;
    }
    public float nextFloat() { return bits(24) * 0x1.0p-24f; }
    public double nextDouble() { return (((long)bits(26) << 27) + bits(27)) * 0x1.0p-53; }
}
