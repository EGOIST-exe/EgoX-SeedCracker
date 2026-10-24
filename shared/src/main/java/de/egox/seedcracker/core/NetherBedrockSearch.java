package de.egox.seedcracker.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Exact legacy positional RNG inversion. Unknown low bits are conservatively bounded before branching. */
public final class NetherBedrockSearch {
    public static final long MASK = LegacyRandom.MASK, MULTIPLIER = 0x5DEECE66DL;
    private static final long INVERSE = 246154705703781L;
    public record Sample(int x, int y, int z) {
        public Sample { if (y != 4 && y != 123) throw new IllegalArgumentException("Use rare Nether layers y=4 or y=123"); }
        public boolean roof() { return y == 123; }
    }
    public record Constraint(long xor, long low, long high) {
        public Constraint { if (low < 0 || low >= high || high > MASK+1) throw new IllegalArgumentException("Invalid RNG interval"); }
        public boolean matches(long positionalSeed) { long state = step((positionalSeed ^ xor) & MASK); return state >= low && state < high; }
        public boolean possible(long prefix, int unknown) {
            if (unknown < 0 || unknown > 13) throw new IllegalArgumentException("Unknown bits: 0..13");
            long lowMask = (1L << unknown)-1;
            long base = step((prefix ^ xor) & (MASK ^ lowMask));
            long end = base + lowMask * MULTIPLIER;
            return end <= MASK ? end >= low && base < high : base < high || (end & MASK) >= low;
        }
    }
    public record Result(SearchJob.State state, List<Long> positionalSeeds, long checked, String error) { }
    private NetherBedrockSearch() { }
    private static long step(long state) { return (state * MULTIPLIER + 11) & MASK; }
    private static long previous(long state) { return ((state-11)*INVERSE)&MASK; }
    public static long positionHash(int x, int y, int z) {
        long value = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
        return (value * value * 42317861L + value * 11L) >> 16;
    }
    public static Constraint constraint(Sample sample) {
        long cutoff = (long)Math.ceil((sample.roof() ? .8 : .2) * (1 << 24)) << 24;
        return new Constraint((positionHash(sample.x(),sample.y(),sample.z()) ^ MULTIPLIER)&MASK,
            sample.roof() ? cutoff : 0, sample.roof() ? MASK+1 : cutoff);
    }
    public static long nextLong(long externalSeed) {
        long first = step((externalSeed ^ MULTIPLIER)&MASK), second = step(first);
        return (((long)(int)(first >>> 16) << 32) + (int)(second >>> 16));
    }
    public static long positionalSeed(long structureSeed, boolean roof) {
        String name = roof ? "minecraft:bedrock_roof" : "minecraft:bedrock_floor";
        return nextLong(nextLong(structureSeed) ^ name.hashCode()) & MASK;
    }
    /** A masked nextLong fixes all 32 second-output bits and 16 first-output bits. */
    public static List<Long> invertNextLong(long output) {
        long unsignedSecond = output & 0xFFFFFFFFL;
        long firstLow16 = ((output >>> 32) + (unsignedSecond >= 0x80000000L ? 1 : 0)) & 0xFFFFL;
        var result = new ArrayList<Long>(2);
        for (long low=0; low<65536; low++) {
            long second = (unsignedSecond << 16) | low, first = previous(second);
            if (((first >>> 16) & 0xFFFFL) == firstLow16) result.add((previous(first)^MULTIPLIER)&MASK);
        }
        return result;
    }
    public static List<Long> structureSeeds(long positional, boolean roof) {
        String name = roof ? "minecraft:bedrock_roof" : "minecraft:bedrock_floor";
        var result = new TreeSet<Long>();
        for (long intermediate : invertNextLong(positional)) for (long seed : invertNextLong(intermediate ^ name.hashCode()))
            if (positionalSeed(seed,roof) == (positional&MASK)) result.add(seed);
        return List.copyOf(result);
    }
    private static void visit(List<Constraint> constraints, long prefix, int unknown, Collection<Long> found, AtomicInteger matches) {
        for (var c : constraints) if (!c.possible(prefix,unknown)) return;
        if (unknown==0) {
            if (matches.incrementAndGet()>256) throw new IllegalStateException("Zu viele Bedrock-Kandidaten; weitere unveraenderte Chunks erforderlich");
            found.add(prefix); return;
        }
        visit(constraints,prefix,unknown-1,found,matches);
        visit(constraints,prefix | (1L << (unknown-1)),unknown-1,found,matches);
    }
    /** Range is over high-36-bit prefixes, not structure seeds; full recovery uses 0..2^36. */
    public static Result search(List<Sample> samples, long firstPrefix, long endPrefix, int workers,
                                BooleanSupplier cancelled, LiftingSearch.Progress progress) {
        if (samples.size()<32 || samples.stream().map(Sample::roof).distinct().count()!=1) throw new IllegalArgumentException("At least 32 observations of one rare layer required");
        if(firstPrefix<0 || endPrefix<firstPrefix || endPrefix>(1L<<36) || workers<1 || workers>64) throw new IllegalArgumentException("Invalid search bounds");
        var constraints=samples.stream().distinct().limit(64).map(NetherBedrockSearch::constraint).toList();
        if(constraints.size()<32) throw new IllegalArgumentException("Independent positions required");
        var found=new ConcurrentLinkedQueue<Long>(); var count=new AtomicInteger();
        var next=new AtomicLong(firstPrefix); var done=new AtomicLong();
        long total=endPrefix-firstPrefix;
        var pool=Executors.newFixedThreadPool(workers,r->{var t=new Thread(r,"EgoX-nether-bedrock");t.setDaemon(true);return t;});
        var futures=new ArrayList<Future<?>>();
        try {
            for(int i=0;i<workers;i++) futures.add(pool.submit(()->{
                for(long start;(start=next.getAndAdd(2048))<endPrefix;) {
                    long end=Math.min(endPrefix,start+2048), completed=0;
                    for(long prefix=start;prefix<end;prefix++) {
                        if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) { done.addAndGet(completed); return; }
                        visit(constraints,prefix<<12,12,found,count); completed++;
                    }
                    done.addAndGet(completed);
                }
            }));
            for(var future:futures) while(true) {
                if(cancelled.getAsBoolean()) { pool.shutdownNow(); return new Result(SearchJob.State.CANCELLED,List.of(),done.get(),""); }
                progress.update("Nether-Bedrock: 48-Bit-Positions-RNG",done.get(),total);
                try { future.get(100,TimeUnit.MILLISECONDS); break; } catch(TimeoutException ignored) { }
            }
            progress.update("Nether-Bedrock: 48-Bit-Positions-RNG",done.get(),total);
            return new Result(cancelled.getAsBoolean()?SearchJob.State.CANCELLED:SearchJob.State.COMPLETE,found.stream().sorted().toList(),done.get(),"");
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt(); return new Result(SearchJob.State.CANCELLED,List.of(),done.get(),"");
        } catch(ExecutionException error) { return new Result(SearchJob.State.FAILED,List.of(),done.get(),String.valueOf(error.getCause().getMessage())); }
        finally { pool.shutdownNow(); }
    }
}
