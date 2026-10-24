package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Immutable geometry of observed pieces only. It never predicts unseen structures. */
public final class FinderMarkers {
    public record Marker(String type, int chunkX, int chunkZ, String dimension, AABB box, String source, BlockPos blockPos) { }
    private static final LinkedHashMap<String, Marker> MARKERS = new LinkedHashMap<>();
    private static volatile List<Marker> visible = List.of();
    private FinderMarkers() { }
    public static synchronized void add(String type, int chunkX, int chunkZ, AABB box, String source) {
        String dimension = EgoXSession.context().dimension();
        var marker = new Marker(type, chunkX, chunkZ, dimension, box, source, null);
        Marker previous = MARKERS.put(dimension + ':' + type + ':' + chunkX + ':' + chunkZ, marker);
        if (!marker.equals(previous)) journal(marker);
        while (MARKERS.size() > 4096) MARKERS.remove(MARKERS.keySet().iterator().next());
    }
    public static synchronized void addBlock(String type, BlockPos pos, String label) {
        String dimension = EgoXSession.context().dimension();
        var marker = new Marker(type, Math.floorDiv(pos.getX(),16), Math.floorDiv(pos.getZ(),16), dimension, new AABB(pos).inflate(.035), label, pos.immutable());
        Marker previous = MARKERS.put(dimension + ':' + type + ':' + pos.asLong(), marker);
        if (!marker.equals(previous)) journal(marker);
        while (MARKERS.size() > 4096) MARKERS.remove(MARKERS.keySet().iterator().next());
    }
    public static synchronized void addGeode(BlockPos pos) {
        String dimension = EgoXSession.context().dimension();
        String key = dimension + ":geode-zone:" + Math.floorDiv(pos.getX(),32) + ':' + Math.floorDiv(pos.getY(),32) + ':' + Math.floorDiv(pos.getZ(),32);
        Marker old = MARKERS.get(key);
        AABB box = old == null ? new AABB(pos) : old.box().minmax(new AABB(pos));
        MARKERS.put(key, new Marker("egox:geode", Math.floorDiv(pos.getX(),16), Math.floorDiv(pos.getZ(),16), dimension, box, "Geodenfund | Amethystdrusen", null));
        if (old == null || !old.box().equals(box)) journal(MARKERS.get(key));
        while (MARKERS.size() > 4096) MARKERS.remove(MARKERS.keySet().iterator().next());
    }
    public static synchronized void clear() { MARKERS.clear(); visible = List.of(); }
    public static synchronized List<Marker> observations() { return List.copyOf(MARKERS.values()); }
    public static synchronized List<LibrarySnapshot.Marker> snapshot() {
        return MARKERS.values().stream().map(FinderMarkers::portable).toList();
    }
    private static LibrarySnapshot.Marker portable(Marker m) {
        return new LibrarySnapshot.Marker(m.type(), m.chunkX(), m.chunkZ(), m.dimension(),
            m.box().minX, m.box().minY, m.box().minZ, m.box().maxX, m.box().maxY, m.box().maxZ, m.source(),
            m.blockPos() == null ? null : m.blockPos().getX(), m.blockPos() == null ? null : m.blockPos().getY(), m.blockPos() == null ? null : m.blockPos().getZ());
    }
    private static void journal(Marker marker) { SessionLibrary.event("MARKER", portable(marker)); }
    public static synchronized void restore(List<LibrarySnapshot.Marker> saved) {
        for (var m : saved) {
            if (!m.type().startsWith("egox:") && !NativeProfiles.all().containsKey(m.type())) continue;
            var pos = m.blockX() == null ? null : new BlockPos(m.blockX(), m.blockY(), m.blockZ());
            String key = m.type().equals("egox:geode") ? m.dimension()+":geode-zone:"+(int)Math.floor((m.minX()+m.maxX())/64)+":"+(int)Math.floor((m.minY()+m.maxY())/64)+":"+(int)Math.floor((m.minZ()+m.maxZ())/64)
                : m.dimension() + ':' + m.type() + ':' + (pos == null ? m.chunkX() + ":" + m.chunkZ() : pos.asLong());
            MARKERS.putIfAbsent(key, new Marker(m.type(), m.chunkX(), m.chunkZ(), m.dimension(), new AABB(m.minX(), m.minY(), m.minZ(), m.maxX(), m.maxY(), m.maxZ()), m.source(), pos));
        }
        while (MARKERS.size() > 4096) MARKERS.remove(MARKERS.keySet().iterator().next());
    }
    public static synchronized void tick(Minecraft client) {
        if (client.player == null) { visible = List.of(); return; }
        var position = client.player.position();
        double range = EgoXConfig.get().outlineRange;
        String dimension = EgoXSession.context().dimension();
        if (client.level != null) MARKERS.values().removeIf(m -> m.blockPos() != null && m.dimension().equals(dimension) && client.level.hasChunkAt(m.blockPos())
            && !SpecialBlockFinder.type(client.level.getBlockState(m.blockPos()).getBlock()).equals(m.type()));
        visible = selectVisible(MARKERS.values(), dimension, position, range, EgoXConfig.get().disabledOutlineTypes, EgoXConfig.get().specialBlocks);
    }
    static List<Marker> selectVisible(Collection<Marker> markers, String dimension, net.minecraft.world.phys.Vec3 position, double range, Set<String> disabled, boolean features) {
        return markers.stream().filter(m -> m.dimension().equals(dimension) && m.box().getCenter().distanceToSqr(position) <= range * range)
            .filter(m -> OutlineVisibility.visible(disabled, features, m.type()))
            .sorted(Comparator.comparingDouble(m -> m.box().getCenter().distanceToSqr(position))).limit(512).toList();
    }
    public static void draw() {
        var config = EgoXConfig.get();
        if (!config.outlines || OverlayPlatform.hidden()) return;
        for (var marker : visible) {
            if (!OutlineVisibility.visible(config.disabledOutlineTypes, config.specialBlocks, marker.type())) continue;
            int color = OutlineColors.color(marker.type());
            if (marker.type().startsWith("egox:")) {
                Gizmos.cuboid(marker.box(), GizmoStyle.stroke(color, 2)).setAlwaysOnTop();
                if (config.outlineLabels) Gizmos.billboardTextOverBlock(marker.source(), BlockPos.containing(marker.box().getCenter().x, marker.box().maxY, marker.box().getCenter().z), 0, color, .75f);
                continue;
            }
            var profile = NativeProfiles.get(marker.type());
            Long seed = EgoXSession.knownSeed();
            String verification = "beobachtet";
            if (seed != null) {
                boolean matches = profile.matches(seed, EgoXSession.salt(profile), marker.chunkX(), marker.chunkZ(), new LegacyRandom());
                verification = matches ? "passend" : "Salt/Start pruefen";
            }
            Gizmos.cuboid(marker.box(), GizmoStyle.stroke(color, 2)).setAlwaysOnTop();
            if (config.outlineLabels) {
                var observation = new StructureObservation(profile, marker.chunkX(), marker.chunkZ(), marker.dimension());
                String label = marker.type().replace("minecraft:", "") + String.format(Locale.ROOT, " | ~%.1f Bits | %s", ObservationBits.bits(observation), verification);
                var center = marker.box().getCenter();
                Gizmos.billboardTextOverBlock(label, BlockPos.containing(center.x, marker.box().maxY, center.z), 0, color, 0.75f);
            }
        }
    }
}
