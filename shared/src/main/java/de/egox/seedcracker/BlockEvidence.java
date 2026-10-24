package de.egox.seedcracker;

import de.egox.seedcracker.core.BlockObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import java.time.Instant;
import java.util.*;

/** Raw ore history is unlimited on disk; only the recent working set is kept in memory. */
public final class BlockEvidence {
    private static final LinkedHashMap<String, BlockObservation> ORES = new LinkedHashMap<>(), BEDROCK = new LinkedHashMap<>();
    private static final LinkedHashMap<String, BlockObservation> FEATURES = new LinkedHashMap<>();
    private static final Set<String> SURVEYS = new HashSet<>();
    private static long mined;
    private BlockEvidence() { }
    public static boolean ore(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        return id.endsWith("_ore") || id.equals("minecraft:ancient_debris");
    }
    public static synchronized void mined(BlockPos pos, Block block) {
        if (EgoXSession.context().server().isEmpty() || !ore(block)) return;
        var observation = observation(pos, block, "CLIENT_BREAK_PREDICTED"); mined++;
        ORES.put(key(observation), observation); trim(ORES, 4096);
        SessionLibrary.event("ORE_MINED", observation);
    }
    public static synchronized void bedrock(BlockPos pos, Block block) {
        String dimension = EgoXSession.context().dimension();
        if (BEDROCK.values().stream().filter(o -> o.dimension().equals(dimension)).count() >= 1024) return;
        var observation = observation(pos, block, "OBSERVED_BEDROCK");
        if (BEDROCK.putIfAbsent(key(observation), observation) == null) SessionLibrary.event("BEDROCK", observation);
    }
    public static synchronized void scanBedrock(net.minecraft.world.level.chunk.LevelChunk chunk) {
        String dimension = EgoXSession.context().dimension();
        if (dimension.isEmpty() || BEDROCK.size() >= 2048 || BEDROCK.values().stream().filter(o -> o.dimension().equals(dimension)).count() >= 1024) return;
        int[] heights = dimension.equals("minecraft:overworld") ? new int[]{-63,-62,-61,-60}
            : dimension.equals("minecraft:the_nether") ? new int[]{1,2,3,4,123,124,125,126} : new int[0];
        int cx = Platform.chunkX(chunk.getPos()) * 16, cz = Platform.chunkZ(chunk.getPos()) * 16;
        for (int x : new int[]{2,6,10,14}) for (int z : new int[]{2,6,10,14}) for (int y : heights) {
            var pos = new BlockPos(cx + x, y, cz + z);
            if (chunk.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BEDROCK)) bedrock(pos, net.minecraft.world.level.block.Blocks.BEDROCK);
        }
    }
    public static synchronized void serverUpdate(BlockPos pos, Block block) {
        String key = EgoXSession.context().dimension() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
        var old = ORES.get(key);
        if (old == null || !old.source().equals("CLIENT_BREAK_PREDICTED")) return;
        boolean rejected = BuiltInRegistries.BLOCK.getKey(block).toString().equals(old.block());
        var observation = new BlockObservation(old.dimension(), old.block(), old.x(), old.y(), old.z(),
            rejected ? "SERVER_REJECTED" : "SERVER_BLOCK_CHANGE", old.time());
        ORES.put(key, observation); SessionLibrary.event("ORE_SERVER_RESULT", observation);
    }
    private static BlockObservation observation(BlockPos pos, Block block, String source) {
        return new BlockObservation(EgoXSession.context().dimension(), BuiltInRegistries.BLOCK.getKey(block).toString(), pos.getX(), pos.getY(), pos.getZ(), source, Instant.now().toString());
    }
    private static String key(BlockObservation observation) { return observation.dimension() + ":" + observation.x() + ":" + observation.y() + ":" + observation.z(); }
    private static void trim(Map<String, BlockObservation> values, int limit) { while (values.size() > limit) values.remove(values.keySet().iterator().next()); }
    public static synchronized List<BlockObservation> ores() { return List.copyOf(ORES.values()); }
    public static synchronized List<BlockObservation> bedrock() { return List.copyOf(BEDROCK.values()); }
    public static synchronized long minedCount() { return mined; }
    public static synchronized void clear() { ORES.clear(); BEDROCK.clear(); FEATURES.clear(); SURVEYS.clear(); mined = 0; }
    public static synchronized List<BlockObservation> features() { return List.copyOf(FEATURES.values()); }
    public static synchronized void restoreFeatures(List<BlockObservation> features) {
        if (features != null) features.forEach(o -> FEATURES.putIfAbsent(key(o), o)); trim(FEATURES, 8192);
    }
    public static synchronized void feature(BlockPos pos, Block block) {
        var observation = observation(pos, block, "OBSERVED_FEATURE_BLOCK");
        if (FEATURES.putIfAbsent(key(observation), observation) == null) { trim(FEATURES, 8192); SessionLibrary.event("FEATURE_BLOCK", observation); }
        if (block != net.minecraft.world.level.block.Blocks.SPAWNER) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        String surveyKey = observation.dimension() + ":" + pos.asLong();
        if (level == null || SURVEYS.contains(surveyKey)) return;
        var floor = new ArrayList<String>();
        for (int x=-5;x<=5;x++) for (int z=-5;z<=5;z++) {
            var point=pos.offset(x,-1,z); if (!level.hasChunkAt(point)) return;
            floor.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(point).getBlock()).toString());
        }
        SURVEYS.add(surveyKey);
        SessionLibrary.event("SPAWNER_SURVEY", Map.of("center",observation,"floorRadius",5,"floorY",pos.getY()-1,"floorBlocksXThenZ",List.copyOf(floor),"classification","UNCLASSIFIED_SPAWNER"));
    }
    public static synchronized void restore(List<BlockObservation> ores, List<BlockObservation> bedrock, long count, boolean initial) {
        ores.forEach(o -> ORES.putIfAbsent(key(o), o)); bedrock.forEach(o -> BEDROCK.putIfAbsent(key(o), o));
        trim(ORES, 4096); trim(BEDROCK, 2048); mined = initial ? mined + count : Math.max(mined, count);
    }
    public static synchronized void describe() {
        EgoXSession.say("Erz-Abbau: " + mined + " Ereignisse lokal journalisiert | " + ORES.size() + " aktuelle Positionen | Bedrock " + BEDROCK.size());
        ORES.values().stream().skip(Math.max(0, ORES.size() - 12)).forEach(o -> EgoXSession.say(o.block() + " | " + o.x() + " " + o.y() + " " + o.z()
            + " | Chunk " + o.chunkX() + " " + o.chunkZ() + " | " + o.dimension() + " | " + o.source()));
        EgoXSession.say("Abbau ist eine Client-Beobachtung; natuerliche Herkunft ist unbewiesen.");
    }
}
