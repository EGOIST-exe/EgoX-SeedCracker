package de.egox.seedcracker;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class EgoX implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EgoXConfig.get();
        ResultDatabase.start();
        OverlayPlatform.register();
        NativeTemplateFinder.warmup();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            EgoXCommands.register(dispatcher);
        });
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            EgoXSession.observeChunk(world, Platform.chunkX(chunk.getPos()), Platform.chunkZ(chunk.getPos()));
            StructureScanner.enqueue(chunk.getPos());
            SpecialBlockFinder.scanChunk(chunk);
            BlockEvidence.scanBedrock(chunk);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SettingsAccess.tick();
            StructureScanner.tick(client);
            EndPillarFinder.tick(client);
            AutoCracker.tick();
            PillarCracker.tick();
            AutoSaltCracker.tick();
            FinderMarkers.tick(client);
            StatusDisplay.tick();
            SessionLibrary.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            EgoXSession.clear(); StructureScanner.clear(); FinderMarkers.clear();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SessionLibrary.shutdown());
    }
}
