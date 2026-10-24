package de.egox.seedcracker;

import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.function.Function;

/** Four entrance arches identify the original monument bounding box and its offset start chunk. */
public final class MonumentFinder {
    public static final StructureSignatures.Signature PATTERN;
    static {
        var markers = new ArrayList<StructureSignatures.Marker>();
        for (int arch = 0; arch < 4; arch++) {
            int z = 5 + arch * 4;
            for (int x : new int[]{26, 31}) markers.add(new StructureSignatures.Marker(x, 5, z, Blocks.SEA_LANTERN));
            for (int x : new int[]{24, 33}) for (int y = 2; y <= 4; y++) markers.add(new StructureSignatures.Marker(x, y, z, Blocks.PRISMARINE_BRICKS));
            for (int x = 27; x <= 30; x++) markers.add(new StructureSignatures.Marker(x, 6, z, Blocks.PRISMARINE));
        }
        PATTERN = new StructureSignatures.Signature("minecraft:monument", 58, 58, List.copyOf(markers));
    }
    private MonumentFinder() { }
    public static Optional<NativeTemplateFinder.Hit> detect(BlockPos anchor, Function<BlockPos, Block> view) {
        if (anchor.getY() != 44) return Optional.empty();
        for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            BlockPos origin = anchor.subtract(StructureSignatures.offset(PATTERN, PATTERN.markers().getFirst(), facing));
            if (origin.getY() != 39 || ((origin.getX() + 29) & 15) != 0 || ((origin.getZ() + 29) & 15) != 0) continue;
            boolean valid = true;
            for (var marker : PATTERN.markers()) if (view.apply(origin.offset(StructureSignatures.offset(PATTERN, marker, facing))) != marker.block()) { valid = false; break; }
            if (valid) return Optional.of(new NativeTemplateFinder.Hit("minecraft:monument", Math.floorDiv(origin.getX() + 29, 16), Math.floorDiv(origin.getZ() + 29, 16),
                new AABB(origin.getX(), 39, origin.getZ(), origin.getX() + 58, 62, origin.getZ() + 58), "Vier Eingangsboegen"));
        }
        return Optional.empty();
    }
}
