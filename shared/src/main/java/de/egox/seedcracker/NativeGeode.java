package de.egox.seedcracker;

import com.google.gson.*;
import de.egox.seedcracker.core.BlockObservation;
import net.minecraft.world.level.levelgen.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.LongPredicate;

/** Necessary placement/bounding-box conditions for observed budding amethyst, not a reconstruction of geode shape. */
public final class NativeGeode {
    public record Settings(int chance,int minY,int maxY,int minOffset,int maxOffset,int index,int step) { }
    private static Settings settings;
    private NativeGeode() { }
    private static JsonObject json(String path) {
        try(var input=NativeGeode.class.getResourceAsStream("/data/minecraft/worldgen/"+path)) {
            if(input==null)throw new IllegalStateException("Native resource missing: "+path);
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    public static synchronized Settings settings() {
        if(settings!=null)return settings;
        var placement=json("placed_feature/amethyst_geode.json").getAsJsonArray("placement");
        if(placement.size()!=4||!placement.get(0).getAsJsonObject().get("type").getAsString().equals("minecraft:rarity_filter")
            ||!placement.get(1).getAsJsonObject().get("type").getAsString().equals("minecraft:in_square")
            ||!placement.get(2).getAsJsonObject().get("type").getAsString().equals("minecraft:height_range")
            ||!placement.get(3).getAsJsonObject().get("type").getAsString().equals("minecraft:biome")) throw new IllegalStateException("Unsupported geode placement pipeline");
        var height=placement.get(2).getAsJsonObject().getAsJsonObject("height");
        if(!height.get("type").getAsString().equals("minecraft:uniform"))throw new IllegalStateException("Unsupported geode height distribution");
        int bottom=json("noise_settings/overworld.json").getAsJsonObject("noise").get("min_y").getAsInt();
        var min=height.getAsJsonObject("min_inclusive");int minY=min.has("absolute")?min.get("absolute").getAsInt():bottom+min.get("above_bottom").getAsInt();
        JsonObject feature;
        try{feature=json("feature/amethyst_geode.json");}catch(IllegalStateException error){feature=json("configured_feature/amethyst_geode.json").getAsJsonObject("config");}
        int[] order=NativeFeatureOrder.get("minecraft:amethyst_geode");
        settings=new Settings(placement.get(0).getAsJsonObject().get("chance").getAsInt(),minY,height.getAsJsonObject("max_inclusive").get("absolute").getAsInt(),
            feature.has("min_gen_offset")?feature.get("min_gen_offset").getAsInt():-16,feature.has("max_gen_offset")?feature.get("max_gen_offset").getAsInt():16,order[0],order[1]);
        return settings;
    }
    public static boolean supports(BlockObservation sample){return sample.dimension().equals("minecraft:overworld")&&sample.block().equals("minecraft:budding_amethyst");}
    public static Optional<int[]> origin(long inputSeed,int chunkX,int chunkZ) {
        return origin(inputSeed,chunkX,chunkZ,settings());
    }
    private static Optional<int[]> origin(long inputSeed,int chunkX,int chunkZ,Settings s) {
        var random=new WorldgenRandom(new XoroshiroRandomSource(0));long population=random.setDecorationSeed(inputSeed,chunkX*16,chunkZ*16);random.setFeatureSeed(population,s.index(),s.step());
        if(random.nextFloat()>=1.0f/s.chance())return Optional.empty();
        int x=chunkX*16+random.nextInt(16),z=chunkZ*16+random.nextInt(16),y=s.minY()+random.nextInt(s.maxY()-s.minY()+1);
        return Optional.of(new int[]{x,y,z});
    }
    public static LongPredicate predicate(List<BlockObservation> observations) {
        var all=observations.stream().filter(NativeGeode::supports).toList();
        var cells=new LinkedHashMap<String,BlockObservation>();all.forEach(o->cells.putIfAbsent(Math.floorDiv(o.x(),32)+":"+Math.floorDiv(o.y(),32)+":"+Math.floorDiv(o.z(),32),o));
        var selected=cells.values().stream().limit(32).toList();if(selected.isEmpty())throw new IllegalStateException("Noch keine Amethystdrusen beobachtet");
        var s=settings();
        var ordered=new ArrayList<>(selected);ordered.addAll(all);
        return seed->{
            var origins=new HashMap<Long,Optional<int[]>>();
            for(var o:ordered){boolean possible=false;
                for(int cx=Math.floorDiv(o.x()-s.maxOffset(),16);cx<=Math.floorDiv(o.x()-s.minOffset(),16)&&!possible;cx++)
                    for(int cz=Math.floorDiv(o.z()-s.maxOffset(),16);cz<=Math.floorDiv(o.z()-s.minOffset(),16)&&!possible;cz++){
                        long key=((long)cx<<32)|(cz&0xFFFFFFFFL);
                        var candidate=origins.computeIfAbsent(key,k->origin(seed,(int)(k>>32),(int)(long)k,s));if(candidate.isEmpty())continue;var p=candidate.get();
                        possible=o.x()>=p[0]+s.minOffset()&&o.x()<=p[0]+s.maxOffset()&&o.y()>=p[1]+s.minOffset()&&o.y()<=p[1]+s.maxOffset()&&o.z()>=p[2]+s.minOffset()&&o.z()<=p[2]+s.maxOffset();
                    }
                if(!possible)return false;
            }return true;
        };
    }
}
