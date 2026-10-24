package de.egox.seedcracker.core;

/** Quart coordinates from the chunk biome palette, without the hashed-seed biome zoom. */
public record BiomeObservation(int quartX, int quartY, int quartZ, String biome, String dimension) {
    public BiomeObservation(int quartX, int quartY, int quartZ, String biome) { this(quartX, quartY, quartZ, biome, "minecraft:overworld"); }
    public BiomeObservation {
        // Older journals predate dimension-aware collection and contain Overworld data only.
        if (dimension == null) dimension = "minecraft:overworld";
        if (!dimension.equals("minecraft:overworld") && !dimension.equals("minecraft:the_nether")) throw new IllegalArgumentException("Unsupported biome dimension: " + dimension);
    }
    public String key() { return dimension + ":" + quartX + ":" + quartY + ":" + quartZ; }
}
