package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Independent recovery: no structure candidates or unchanged placement salts are required. */
public final class AutoBedrockCracker {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"EgoX-bedrock-auto");t.setDaemon(true);return t;});
    public static final CrackProgress PROGRESS=new CrackProgress();
    private static final AtomicLong GENERATION=new AtomicLong();
    private static volatile boolean running;
    private static String attempted="";
    private static volatile String status="Nether-Bedrock: sammelt seltene Boden- und Deckenmuster";
    private AutoBedrockCracker() { }
    public static boolean running(){return running;}
    public static String status(){return status;}
    public static void describe(){
        EgoXSession.say(status);
        if(running){var p=PROGRESS.snapshot();EgoXSession.say(p.phase());EgoXSession.say(StatusDisplay.work(p.checked(),p.total(),p.seconds()));}
    }
    public static void stop(){GENERATION.incrementAndGet();}
    public static void retry(){stop();attempted="";}
    public static void reset(){retry();PROGRESS.reset();status="Nether-Bedrock: sammelt seltene Boden- und Deckenmuster";}
    public static List<NetherBedrockSearch.Sample> samples(List<BlockObservation> observations,boolean roof){
        return observations.stream().filter(o->o.dimension().equals("minecraft:the_nether") && o.block().equals("minecraft:bedrock") && o.y()==(roof?123:4))
            .map(o->new NetherBedrockSearch.Sample(o.x(),o.y(),o.z())).distinct().toList();
    }
    public static boolean ready(List<NetherBedrockSearch.Sample> floor,List<NetherBedrockSearch.Sample> roof){
        return floor.size()>=128 && roof.size()>=128 && chunks(floor)>=4 && chunks(roof)>=4;
    }
    private static long chunks(List<NetherBedrockSearch.Sample> values){return values.stream().map(s->Math.floorDiv(s.x(),16)+":"+Math.floorDiv(s.z(),16)).distinct().count();}
    /** Round-robin chunks keeps the first search constraints spatially independent. */
    public static List<NetherBedrockSearch.Sample> spread(List<NetherBedrockSearch.Sample> samples){
        var groups=new TreeMap<String,ArrayDeque<NetherBedrockSearch.Sample>>();
        samples.forEach(s->groups.computeIfAbsent(Math.floorDiv(s.x(),16)+":"+Math.floorDiv(s.z(),16),k->new ArrayDeque<>()).add(s));
        var result=new ArrayList<NetherBedrockSearch.Sample>();
        while(result.size()<64){boolean added=false;for(var queue:groups.values())if(!queue.isEmpty()){result.add(queue.removeFirst());added=true;if(result.size()==64)break;}if(!added)break;}
        return List.copyOf(result);
    }
    public static void tick(){
        var config=EgoXConfig.get(); var context=EgoXSession.context();
        if(SessionLibrary.loading() || running || !config.autoNetherBedrock || AutoCracker.paused() || AutoCracker.running() || PillarCracker.running()
            || context.server().isEmpty() || EgoXSession.knownSeed()!=null) return;
        if(EgoXSession.job!=null && EgoXSession.job.snapshot().state()==SearchJob.State.RUNNING)return;
        var observations=BlockEvidence.bedrock();var floor=samples(observations,false);var roof=samples(observations,true);
        if(!ready(floor,roof)){status="Nether-Bedrock: Boden "+floor.size()+"/128, Decke "+roof.size()+"/128; je 4 Chunks erforderlich";return;}
        String key=context.server()+":"+context.hash();
        if(key.equals(attempted))return;
        attempted=key;running=true;PROGRESS.reset();long generation=GENERATION.get();int workers=config.workers;
        BooleanSupplier cancelled=()->generation!=GENERATION.get() || context.epoch()!=EgoXSession.context().epoch() || !EgoXConfig.get().autoNetherBedrock || AutoCracker.paused();
        WORKER.execute(()->{
            try{
                status="Nether-Bedrock: vollstaendige CPU-Suche gestartet";
                EgoXSession.say(status+". /egox cracker bedrock status; /egox stop. Diese Suche kann lange dauern.");
                var result=NetherBedrockSearch.search(spread(roof),0,1L<<36,workers,cancelled,PROGRESS::update);
                if(cancelled.getAsBoolean() || result.state()==SearchJob.State.CANCELLED){status="Nether-Bedrock: abgebrochen; Fortsetzen startet die Suche neu";return;}
                if(result.state()!=SearchJob.State.COMPLETE)throw new IllegalStateException(result.error());
                var lower=new TreeSet<Long>();
                var checks=java.util.stream.Stream.concat(floor.stream(),roof.stream()).map(s->Map.entry(s,NetherBedrockSearch.constraint(s))).toList();
                PROGRESS.update("Nether-Bedrock: RNG umkehren und beide Schichten pruefen",0,result.positionalSeeds().size());int done=0;
                for(long positional:result.positionalSeeds()){
                    if(cancelled.getAsBoolean())return;
                    for(long seed:NetherBedrockSearch.structureSeeds(positional,true)){
                        long floorSeed=NetherBedrockSearch.positionalSeed(seed,false),roofSeed=NetherBedrockSearch.positionalSeed(seed,true);
                        if(checks.stream().allMatch(e->e.getValue().matches(e.getKey().roof()?roofSeed:floorSeed)))lower.add(seed);
                    }
                    PROGRESS.update("Nether-Bedrock: RNG umkehren und beide Schichten pruefen",++done,result.positionalSeeds().size());
                }
                if(cancelled.getAsBoolean())return;
                status="Nether-Bedrock: "+lower.size()+" Struktur-Seed-Kandidaten";
                SessionLibrary.event("NETHER_BEDROCK_RESULT",Map.of("structureSeeds",lower.stream().map(Object::toString).toList(),"floorSamples",floor.size(),"roofSamples",roof.size()));
                if(lower.isEmpty()){EgoXSession.say(status+". Moeglich: veraenderter Bedrock-Generator oder veraenderte Bloecke. Kein automatischer Dauer-Neustart.");return;}
                EgoXSession.say(status);
                if(context.hash()==null){EgoXSession.say("48-Bit-Kandidaten lokal gespeichert; ohne Server-Hash noch keine bestaetigte Welt-Seed-Zuordnung.");return;}
                var lifted=WorldSeedLifting.search(lower,0,65535,workers,256,()->seed->SeedHash.of(seed)==context.hash(),cancelled,PROGRESS::update,"Nether-Bedrock: obere 16 Bits am Server-Hash pruefen");
                if(cancelled.getAsBoolean())return;
                if(lifted.state()!=SearchJob.State.COMPLETE)throw new IllegalStateException(lifted.error());
                if(lifted.seeds().size()==1){EgoXSession.acceptWorldSeed(lifted.seeds().getFirst(),"AUTOMATIC_NETHER_BEDROCK_AND_HASH");status="Nether-Bedrock: Welt-Seed am Server-Hash bestaetigt";}
                else{status="Nether-Bedrock: kein eindeutiger Server-Hash-Treffer";EgoXSession.say(status);}
            }catch(Exception error){status="Nether-Bedrock: "+error.getMessage();EgoXSession.say(status);}
            finally{running=false;}
        });
    }
}
