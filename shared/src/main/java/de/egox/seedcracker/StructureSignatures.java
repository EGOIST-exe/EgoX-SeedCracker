package de.egox.seedcracker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.util.function.Function;

/** Independent, conservative marker patterns for intact vanilla structures. */
public final class StructureSignatures {
    public record Marker(int x, int y, int z, Block block) { }
    public record Signature(String type, int width, int depth, List<Marker> markers) { }
    public record Start(String type, int chunkX, int chunkZ) { }
    public record Hit(Start start, net.minecraft.world.phys.AABB box) { }
    public static final List<Signature> SIGNATURES;
    static {
        List<Marker> pyramid = new ArrayList<>();
        pyramid.add(new Marker(10, -13, 10, Blocks.TNT));
        for (int x = 9; x <= 11; x++) for (int z = 9; z <= 11; z++)
            if (x != 10 || z != 10) pyramid.add(new Marker(x, -13, z, Blocks.TNT));
        pyramid.add(new Marker(10, -11, 10, Blocks.STONE_PRESSURE_PLATE));
        for (var direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST))
            pyramid.add(new Marker(10 + direction.getStepX() * 2, -11, 10 + direction.getStepZ() * 2, Blocks.CHEST));
        SIGNATURES = List.of(
            new Signature("minecraft:desert_pyramid", 21, 21, List.copyOf(pyramid)),
            new Signature("minecraft:swamp_hut", 7, 9, List.of(
                new Marker(4, 2, 6, Blocks.CAULDRON), new Marker(3, 2, 6, Blocks.CRAFTING_TABLE),
                new Marker(1, 3, 5, Blocks.POTTED_RED_MUSHROOM), new Marker(2, 1, 5, Blocks.SPRUCE_PLANKS),
                new Marker(1, 2, 2, Blocks.OAK_LOG), new Marker(5, 2, 2, Blocks.OAK_LOG),
                new Marker(1, 2, 7, Blocks.OAK_LOG), new Marker(5, 2, 7, Blocks.OAK_LOG))),
            new Signature("minecraft:jungle_pyramid", 12, 15, List.of(
                new Marker(3, -2, 1, Blocks.DISPENSER), new Marker(9, -2, 3, Blocks.DISPENSER),
                new Marker(8, -3, 3, Blocks.CHEST), new Marker(9, -3, 10, Blocks.CHEST)))
        );
    }
    public static BlockPos offset(Signature s, Marker m, Direction facing) {
        int x = m.x(), z = m.z();
        if (facing == Direction.NORTH) z = s.depth() - 1 - m.z();
        else if (facing == Direction.EAST) { x = m.z(); z = m.x(); }
        else if (facing == Direction.WEST) { x = s.depth() - 1 - m.z(); z = m.x(); }
        return new BlockPos(x, m.y(), z);
    }
    public static Optional<Start> detect(BlockPos anchor, Block block, Function<BlockPos, Block> view) {
        return detectHit(anchor, block, view).map(Hit::start);
    }
    public static Optional<Hit> detectHit(BlockPos anchor, Block block, Function<BlockPos, Block> view) {
        for (Signature signature : SIGNATURES) {
            if (signature.markers().getFirst().block() != block) continue;
            for (Direction facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                BlockPos relative = offset(signature, signature.markers().getFirst(), facing);
                BlockPos origin = anchor.subtract(relative);
                if ((origin.getX() & 15) != 0 || (origin.getZ() & 15) != 0) continue;
                boolean matches = true;
                for (Marker marker : signature.markers()) {
                    if (view.apply(origin.offset(offset(signature, marker, facing))) != marker.block()) { matches = false; break; }
                }
                if (matches) {
                    boolean rotated = facing == Direction.EAST || facing == Direction.WEST;
                    int width = rotated ? signature.depth() : signature.width(), depth = rotated ? signature.width() : signature.depth();
                    int minY = signature.markers().stream().mapToInt(Marker::y).min().orElse(0);
                    int maxY = signature.type().equals("minecraft:desert_pyramid") ? 15 : 9;
                    return Optional.of(new Hit(new Start(signature.type(), Math.floorDiv(origin.getX(), 16), Math.floorDiv(origin.getZ(), 16)),
                        new net.minecraft.world.phys.AABB(origin.getX(), origin.getY() + minY, origin.getZ(), origin.getX() + width, origin.getY() + maxY, origin.getZ() + depth)));
                }
            }
        }
        return Optional.empty();
    }
}
