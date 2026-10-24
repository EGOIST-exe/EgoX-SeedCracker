package de.egox.seedcracker.mixin;

import de.egox.seedcracker.BlockEvidence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records a successful client prediction explicitly as such, not as server-confirmed natural ore. */
@Mixin(MultiPlayerGameMode.class)
public abstract class OreBreakMixin {
    @Unique private Block egox$oldOre;
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void egox$capture(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        var level = Minecraft.getInstance().level; egox$oldOre = level == null ? null : level.getBlockState(pos).getBlock();
    }
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void egox$record(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && egox$oldOre != null) BlockEvidence.mined(pos, egox$oldOre);
        egox$oldOre = null;
    }
}
