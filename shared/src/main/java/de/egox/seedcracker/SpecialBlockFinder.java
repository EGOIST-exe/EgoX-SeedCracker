package de.egox.seedcracker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;

/** Visual observations only: these blocks are not treated as solved RNG traces or structure starts. */
public final class SpecialBlockFinder {
    private SpecialBlockFinder() { }
    public static String type(Block block) {
        if (block == Blocks.SPAWNER) return "egox:mob_spawner";
        if (block == Blocks.TRIAL_SPAWNER) return "egox:trial_spawner";
        if (block == Blocks.VAULT) return "egox:vault";
        if (block == Blocks.BUDDING_AMETHYST) return "egox:geode";
        return "";
    }
    public static void scanChunk(LevelChunk chunk) {
        if (!EgoXConfig.get().finder || !EgoXConfig.get().specialBlocks || EgoXSession.context().server().isEmpty()) return;
        // Block entities are available without traversing every block in the chunk.
        chunk.getBlockEntities().forEach((pos, entity) -> found(pos, chunk.getBlockState(pos).getBlock()));
    }
    public static void found(BlockPos pos, Block block) {
        if (!EgoXConfig.get().specialBlocks) return;
        String kind = type(block);
        if (kind.isEmpty()) return;
        BlockEvidence.feature(pos, block);
        if (block == Blocks.SPAWNER) DungeonEvidence.observe(pos);
        if (kind.equals("egox:geode")) {
            if (OutlineVisibility.enabled(EgoXConfig.get().disabledOutlineTypes, kind)) FinderMarkers.addGeode(pos);
            return;
        }
        String label = switch (kind) {
            case "egox:mob_spawner" -> "Mob-Spawner";
            case "egox:trial_spawner" -> "Trial-Spawner";
            default -> "Vault";
        };
        FinderMarkers.addBlock(kind, pos, label + " | " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }
}
