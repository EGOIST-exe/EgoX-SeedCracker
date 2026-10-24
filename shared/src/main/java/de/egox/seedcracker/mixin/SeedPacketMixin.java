package de.egox.seedcracker.mixin;

import de.egox.seedcracker.EgoXSession;
import de.egox.seedcracker.BlockEvidence;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the public biome-zoom seed hash delivered in ordinary play packets. */
@Mixin(ClientPacketListener.class)
public abstract class SeedPacketMixin {
    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void egox$login(ClientboundLoginPacket packet, CallbackInfo callback) {
        EgoXSession.connect(packet.commonPlayerSpawnInfo().seed());
    }
    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void egox$respawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        EgoXSession.connect(packet.commonPlayerSpawnInfo().seed());
    }
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void egox$oreUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo callback) {
        BlockEvidence.serverUpdate(packet.getPos(), packet.getBlockState().getBlock());
    }
    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void egox$oreUpdates(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo callback) {
        packet.runUpdates((pos, state) -> BlockEvidence.serverUpdate(pos, state.getBlock()));
    }
}
