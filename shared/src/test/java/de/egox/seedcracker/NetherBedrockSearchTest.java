package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NetherBedrockSearchTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private List<NetherBedrockSearch.Sample> observations(long seed,boolean roof,int size){
        var values=new ArrayList<NetherBedrockSearch.Sample>();var rng=new Random(seed+size);
        while(values.size()<size){int x=rng.nextInt(100000)-50000,z=rng.nextInt(100000)-50000,y=roof?123:4;
            var sample=new BlockObservation("minecraft:the_nether","minecraft:bedrock",x,y,z,"OBSERVED_BEDROCK","");
            if(NativeBedrock.expected(seed,sample))values.add(new NetherBedrockSearch.Sample(x,y,z));
        }
        return values;
    }
    @Test void mathematicalPositionalRngMatchesBothNativeNetherLayers(){
        var rng=new Random(400);
        for(int n=0;n<12;n++) {long seed=rng.nextLong();
            assertEquals(new Random(seed).nextLong(),NetherBedrockSearch.nextLong(seed));
            for(boolean roof:new boolean[]{false,true}){
                long key=NetherBedrockSearch.positionalSeed(seed,roof);
                for(int i=0;i<1000;i++){
                    int x=rng.nextInt(),z=rng.nextInt(),y=roof?123:4;
                    assertEquals(Mth.getSeed(x,y,z),NetherBedrockSearch.positionHash(x,y,z));
                    var observation=new BlockObservation("minecraft:the_nether","minecraft:bedrock",x,y,z,"","");
                    assertEquals(NativeBedrock.expected(seed,observation),NetherBedrockSearch.constraint(new NetherBedrockSearch.Sample(x,y,z)).matches(key));
                }
            }
        }
    }
    @Test void exactTwoStageNextLongInversionPreservesSignedAndBoundarySeeds(){
        for(long seed:new long[]{0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,-5837105212680014280L,123456789})for(boolean roof:new boolean[]{false,true}){
            long key=NetherBedrockSearch.positionalSeed(seed,roof);
            assertTrue(NetherBedrockSearch.structureSeeds(key,roof).contains(seed&LegacyRandom.MASK));
            for(long candidate:NetherBedrockSearch.structureSeeds(key,roof))assertEquals(key,NetherBedrockSearch.positionalSeed(candidate,roof));
        }
    }
    @Test void intervalPruningNeverDropsAValidAssignmentIncludingWrappingIntervals(){
        var rng=new Random(81);
        for(int i=0;i<3000;i++){
            int bits=rng.nextInt(14);long mask=(1L<<bits)-1,key=rng.nextLong()&LegacyRandom.MASK,prefix=key&~mask;
            var condition=NetherBedrockSearch.constraint(new NetherBedrockSearch.Sample(rng.nextInt(),rng.nextBoolean()?123:4,rng.nextInt()));
            if(condition.matches(key))assertTrue(condition.possible(prefix,bits));
            assertEquals(condition.matches(key),condition.possible(key,0));
        }
    }
    @Test void boundedRecoveryMatchesExhaustiveEnumerationAndParallelExecution(){
        long seed=-5837105212680014280L,key=NetherBedrockSearch.positionalSeed(seed,true),prefix=key>>>12;
        var samples=observations(seed,true,64);
        var constraints=samples.stream().map(NetherBedrockSearch::constraint).toList();
        var expected=new ArrayList<Long>();
        for(long candidate=(prefix-8)<<12;candidate<((prefix+9)<<12);candidate++){
            long value=candidate;if(constraints.stream().allMatch(c->c.matches(value)))expected.add(value);
        }
        assertTrue(expected.contains(key));
        for(int workers:new int[]{1,3}){
            var result=NetherBedrockSearch.search(samples,prefix-8,prefix+9,workers,()->false,(phase,done,total)->assertTrue(done<=total));
            assertEquals(SearchJob.State.COMPLETE,result.state());assertEquals(17,result.checked());assertEquals(expected,result.positionalSeeds());
        }
    }
    @Test void cancellationNeverReturnsPartialCandidatesAsSolved(){
        var result=NetherBedrockSearch.search(observations(44,true,64),0,1L<<36,2,()->true,(p,d,t)->{});
        assertEquals(SearchJob.State.CANCELLED,result.state());assertTrue(result.positionalSeeds().isEmpty());
    }
    @Test void automaticReadinessRequiresBothRareLayersAndMultipleChunks(){
        var roof=observations(99,true,128);var floor=observations(99,false,128);
        assertTrue(AutoBedrockCracker.ready(floor,roof));assertFalse(AutoBedrockCracker.ready(floor,List.of()));
        var sameChunk=new ArrayList<NetherBedrockSearch.Sample>();
        for(int x=0;x<16;x++)for(int z=0;z<8;z++)sameChunk.add(new NetherBedrockSearch.Sample(x,123,z));
        assertFalse(AutoBedrockCracker.ready(floor,sameChunk));
        assertEquals(64,AutoBedrockCracker.spread(roof).size());
    }
    @Test void excludedStructuresRemainRawButAreRemovedFromEverySolverInput(){
        var trial=NativeProfiles.get("minecraft:trial_chambers");var pyramid=NativeProfiles.get("minecraft:desert_pyramid");
        var raw=List.of(new StructureObservation(trial,0,0,"minecraft:overworld"),new StructureObservation(pyramid,0,0,"minecraft:overworld"));
        var filtered=CrackingStructures.filter(raw,CrackingStructures.defaults());
        assertEquals(2,raw.size());assertEquals(1,filtered.size());assertEquals(pyramid,filtered.getFirst().placement());
        var json=com.google.gson.JsonParser.parseString("{\"displayRevision\":3,\"dungeonChecker\":true,\"autoUpload\":true}").getAsJsonObject();
        assertTrue(ConfigMigration.update(json));assertFalse(json.get("dungeonChecker").getAsBoolean());assertTrue(json.get("autoUpload").getAsBoolean());
        assertTrue(json.getAsJsonArray("disabledCrackingStructures").toString().contains("trial_chambers"));
        json.addProperty("dungeonChecker",true);assertFalse(ConfigMigration.update(json));assertTrue(json.get("dungeonChecker").getAsBoolean());
    }
}
