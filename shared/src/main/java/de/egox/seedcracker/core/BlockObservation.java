package de.egox.seedcracker.core;

/** A client observation, never a claim that a block was generated naturally. */
public record BlockObservation(String dimension, String block, int x, int y, int z, String source, String time, int chunkX, int chunkZ) {
    public BlockObservation(String dimension,String block,int x,int y,int z,String source,String time) {
        this(dimension,block,x,y,z,source,time,Math.floorDiv(x,16),Math.floorDiv(z,16));
    }
    public BlockObservation { chunkX=Math.floorDiv(x,16);chunkZ=Math.floorDiv(z,16); }
}
