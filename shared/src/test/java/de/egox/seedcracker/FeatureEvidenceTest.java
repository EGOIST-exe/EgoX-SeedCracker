package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureEvidenceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void copyableRngMatchesNativeWorldgenDrawsIncludingRejections() {
        for (long seed : new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,123456789L}) {
            var own=new XoroshiroWorldgen(seed); var nativeRng=new WorldgenRandom(new XoroshiroRandomSource(seed));
            for (int i=0;i<10000;i++) {
                assertEquals(nativeRng.nextInt(16),own.nextInt(16)); assertEquals(nativeRng.nextInt(1073741825),own.nextInt(1073741825));
                assertEquals(nativeRng.nextInt(3),own.nextInt(3)); assertEquals(nativeRng.nextFloat(),own.nextFloat()); assertEquals(nativeRng.nextDouble(),own.nextDouble());
            }
            var copy=new XoroshiroWorldgen(own.state()); assertEquals(own.nextDouble(),copy.nextDouble());
        }
    }
    @Test void fastBedrockFactoriesMatchNativeRandomStateInBothDimensions() {
        for (long seed : new long[]{0,-5837105212680014280L,Long.MIN_VALUE,Long.MAX_VALUE}) for (boolean nether : new boolean[]{false,true}) for (boolean roof : nether ? new boolean[]{false,true} : new boolean[]{false}) {
            var fast=NativeBedrock.factory(seed,nether,roof);
            var nativeFactory=NativeBiomes.surfaceRandom(seed,nether).getOrCreateRandomFactory(Identifier.parse(roof?"minecraft:bedrock_roof":"minecraft:bedrock_floor"));
            for (int i=0;i<1000;i++) {
                int x=i*93-50000,y=roof?123+i%4:nether?1+i%4:-63+i%4,z=i*-137+600;
                assertEquals(nativeFactory.at(x,y,z).nextFloat(),fast.at(x,y,z).nextFloat());
            }
        }
    }
    @Test void bedrockEvidenceRetainsTheGeneratingSeedAndRejectsAnother() {
        long seed=-5837105212680014280L;var samples=new ArrayList<BlockObservation>();
        for (int i=0;i<500;i++) {
            var observation=new BlockObservation("minecraft:overworld","minecraft:bedrock",i*37,-63+i%4,i*-29,"OBSERVED_BEDROCK","");
            if (NativeBedrock.expected(seed,observation)) samples.add(observation);
        }
        assertTrue(samples.size()>100);var check=NativeBedrock.predicate(samples);assertTrue(check.test(seed));assertFalse(check.test(seed+1));
        assertFalse(NativeBedrock.layer("minecraft:overworld",-64));assertFalse(NativeBedrock.layer("minecraft:the_nether",127));
        assertFalse(NativeBedrock.layer("minecraft:the_end",1));
    }
    @Test void emeraldSettingsAreReadFromTheNativeFeatureOrderAndPlacement() {
        var settings=NativeEmerald.settings();assertEquals(100,settings.attempts());assertEquals(-16,settings.minY());
        assertTrue(settings.maxY()>300);assertTrue(settings.index()>=0);assertEquals(6,settings.step());
    }
    private Object nativeFeature() throws Exception {
        net.minecraft.resources.ResourceKey registry;
        try { registry=(ResourceKey)Registries.class.getField("CONFIGURED_FEATURE").get(null); }
        catch (NoSuchFieldException ignored) { registry=(ResourceKey)Registries.class.getField("FEATURE").get(null); }
        return NativeBiomes.registries().lookupOrThrow(registry).getOrThrow(ResourceKey.create(registry,Identifier.parse("minecraft:ore_emerald"))).value();
    }
    private List<BlockPos> nativeGeometry(Object feature, WorldgenRandom random, BlockPos origin, boolean terrain) throws Exception {
        var positions=new ArrayList<BlockPos>();
        boolean[] initialPermission={feature.getClass().getSimpleName().equals("ConfiguredFeature")};
        var world=(net.minecraft.world.level.WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{net.minecraft.world.level.WorldGenLevel.class},(proxy,call,args)->switch(call.getName()) {
            case "getHeight" -> terrain?2048:-2048;
            case "getMinY" -> -64;
            case "getMaxY" -> 2031;
            case "getHeightAccessorForGeneration" -> proxy;
            case "isOutsideBuildHeight" -> false;
            case "ensureCanWrite" -> { if(initialPermission[0]) { initialPermission[0]=false; yield true; } positions.add(((BlockPos)args[0]).immutable()); yield false; }
            default -> throw new UnsupportedOperationException(call.getName());
        });
        var place=feature.getClass().getMethod("place",net.minecraft.world.level.WorldGenLevel.class,net.minecraft.world.level.chunk.ChunkGenerator.class,net.minecraft.util.RandomSource.class,BlockPos.class);
        place.invoke(feature,world,null,random,origin);return positions;
    }
    @Test void nativeEmeraldGeometryAndEarlyExitConsumeTheModelledRandomCalls() throws Exception {
        var feature=nativeFeature();
        for (boolean terrain:new boolean[]{false,true}) for(int i=0;i<24;i++) {
            long seed=54321L+i;var nativeRng=new WorldgenRandom(new XoroshiroRandomSource(seed));var own=new XoroshiroWorldgen(seed);
            var origin=new BlockPos(-16,-12,-16);var positions=nativeGeometry(feature,nativeRng,origin,terrain);
            own.nextFloat();own.nextInt(3);own.nextInt(3);if(terrain)for(int j=0;j<3;j++)own.nextDouble();
            assertEquals(nativeRng.next(31),own.bits(31),"terrain="+terrain+" seed="+seed+" native draw count="+nativeRng.getCount());
            for(var pos:positions) assertTrue(EmeraldReplay.nearOrigin(new BlockObservation("minecraft:overworld","minecraft:emerald_ore",pos.getX(),pos.getY(),pos.getZ(),"SERVER_BLOCK_CHANGE",""),origin.getX(),origin.getY(),origin.getZ()));
        }
    }
    @Test void replayRetainsNativePlacementsWithUnknownSkippedAttempts() throws Exception {
        var feature=nativeFeature();var settings=NativeEmerald.settings();long worldSeed=123456789L;int cx=-3,cz=-5;
        var random=new WorldgenRandom(new XoroshiroRandomSource(0));long population=random.setDecorationSeed(worldSeed,cx*16,cz*16);random.setFeatureSeed(population,settings.index(),settings.step());
        var height=TrapezoidHeight.of(VerticalAnchor.absolute(settings.minY()),VerticalAnchor.absolute(settings.maxY()),settings.plateau());
        BlockObservation sample=null;
        for(int i=0;i<settings.attempts();i++) {
            int x=cx*16+random.nextInt(16),z=cz*16+random.nextInt(16),y=height.sample(random,null);
            if(i%3==0)continue;
            var points=nativeGeometry(feature,random,new BlockPos(x,y,z),i%3==2);
            if(sample==null&&!points.isEmpty()&&y<300){var p=points.getFirst();sample=new BlockObservation("minecraft:overworld","minecraft:emerald_ore",p.getX(),p.getY(),p.getZ(),"SERVER_BLOCK_CHANGE","");}
        }
        assertNotNull(sample);assertTrue(NativeEmerald.predicate(List.of(sample),()->false).test(worldSeed));
        var impossible=new BlockObservation("minecraft:overworld","minecraft:deepslate_emerald_ore",cx*16,-100,cz*16,"SERVER_BLOCK_CHANGE","");
        assertFalse(NativeEmerald.predicate(List.of(impossible),()->false).test(worldSeed));
    }
    @Test void predictedOrRejectedOreIsNotUsedAsServerObservedEvidence() {
        for(String source:List.of("CLIENT_BREAK_PREDICTED","SERVER_REJECTED")) assertFalse(EmeraldReplay.supports(new BlockObservation("minecraft:overworld","minecraft:emerald_ore",0,0,0,source,"")));
        assertFalse(EmeraldReplay.supports(new BlockObservation("minecraft:overworld","minecraft:diamond_ore",0,0,0,"SERVER_BLOCK_CHANGE","")));
    }
    @Test void geodeOriginsMatchNativeRaritySquareAndHeightModifiers() throws Exception {
        var s=NativeGeode.settings();assertEquals(24,s.chance());assertEquals(-58,s.minY());assertEquals(30,s.maxY());assertEquals(-16,s.minOffset());assertEquals(16,s.maxOffset());
        var rarity=net.minecraft.world.level.levelgen.placement.RarityFilter.onAverageOnceEvery(s.chance());
        var should=rarity.getClass().getDeclaredMethod("shouldPlace",net.minecraft.world.level.levelgen.placement.PlacementContext.class,net.minecraft.util.RandomSource.class,BlockPos.class);should.setAccessible(true);
        var height=net.minecraft.world.level.levelgen.heightproviders.UniformHeight.of(VerticalAnchor.absolute(s.minY()),VerticalAnchor.absolute(s.maxY()));
        for(int i=0;i<300;i++) {
            long seed=123456789L+i;int cx=i-150,cz=-i-1;var random=new WorldgenRandom(new XoroshiroRandomSource(0));
            long p=random.setDecorationSeed(seed,cx*16,cz*16);random.setFeatureSeed(p,s.index(),s.step());var start=new BlockPos(cx*16,-64,cz*16);
            boolean present=(boolean)should.invoke(rarity,null,random,start);var result=NativeGeode.origin(seed,cx,cz);
            if(!present){assertTrue(result.isEmpty());continue;}
            var modifier=net.minecraft.world.level.levelgen.placement.InSquarePlacement.spread();
            BlockPos square;
            try {
                var modify=modifier.getClass().getMethod("modify",net.minecraft.world.level.levelgen.placement.PlacementContext.class,net.minecraft.util.RandomSource.class,BlockPos.class,java.util.function.Consumer.class);
                var positions=new ArrayList<BlockPos>();
                modify.invoke(modifier,null,random,start,(java.util.function.Consumer<BlockPos>)positions::add);
                square=positions.getFirst();
            } catch (NoSuchMethodException ignored) {
                var getPositions=modifier.getClass().getMethod("getPositions",net.minecraft.world.level.levelgen.placement.PlacementContext.class,net.minecraft.util.RandomSource.class,BlockPos.class);
                try(var positions=(java.util.stream.Stream<BlockPos>)getPositions.invoke(modifier,null,random,start)){square=positions.findFirst().orElseThrow();}
            }
            assertArrayEquals(new int[]{square.getX(),height.sample(random,null),square.getZ()},result.orElseThrow());
        }
    }
    @Test void geodeConditionsRetainMatchingPlacementsAndRejectAnotherSeed() {
        long seed=-5837105212680014280L;var observations=new ArrayList<BlockObservation>();
        for(int i=0;i<5000&&observations.size()<20;i++) {
            var origin=NativeGeode.origin(seed,i*3-1000,i*-7+200);if(origin.isEmpty())continue;var p=origin.get();
            observations.add(new BlockObservation("minecraft:overworld","minecraft:budding_amethyst",p[0]+3,p[1]-2,p[2]+1,"OBSERVED_FEATURE_BLOCK",""));
        }
        assertEquals(20,observations.size());var check=NativeGeode.predicate(observations);assertTrue(check.test(seed));assertFalse(check.test(seed+1));
    }
    @Test void oreClassificationAndServerCorrectionsRemainSeparateFromNaturalOrigin() {
        assertTrue(BlockEvidence.ore(net.minecraft.world.level.block.Blocks.DIAMOND_ORE));assertTrue(BlockEvidence.ore(net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE));
        assertTrue(BlockEvidence.ore(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS));assertFalse(BlockEvidence.ore(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK));
        BlockEvidence.clear();
        try {
            var predicted=new BlockObservation("","minecraft:emerald_ore",-1,-12,-1,"CLIENT_BREAK_PREDICTED","");
            BlockEvidence.restore(List.of(predicted),List.of(),1,false);BlockEvidence.serverUpdate(new BlockPos(-1,-12,-1),net.minecraft.world.level.block.Blocks.EMERALD_ORE);
            assertEquals("SERVER_REJECTED",BlockEvidence.ores().getFirst().source());
            BlockEvidence.clear();BlockEvidence.restore(List.of(predicted),List.of(),1,false);BlockEvidence.serverUpdate(new BlockPos(-1,-12,-1),net.minecraft.world.level.block.Blocks.AIR);
            assertEquals("SERVER_BLOCK_CHANGE",BlockEvidence.ores().getFirst().source());assertEquals(1,BlockEvidence.minedCount());
        }finally{BlockEvidence.clear();}
    }
    @Test void oreBreakAndPacketMixinsHaveNativeTargetsInBothVersions() throws Exception {
        var mode=net.minecraft.client.multiplayer.MultiPlayerGameMode.class;
        assertEquals(boolean.class,mode.getDeclaredMethod("destroyBlock",BlockPos.class).getReturnType());
        var listener=net.minecraft.client.multiplayer.ClientPacketListener.class;
        assertNotNull(listener.getDeclaredMethod("handleBlockUpdate",net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket.class));
        assertNotNull(listener.getDeclaredMethod("handleChunkBlocksUpdate",net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket.class));
        try(var input=getClass().getResourceAsStream("/egoxseedcracker.mixins.json")){assertTrue(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("OreBreakMixin"));}
    }
}
