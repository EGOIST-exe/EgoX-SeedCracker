package de.egox.seedcracker.mixin;

import de.egox.seedcracker.FinderMarkers;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class StructureOutlineMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void egox$collectObservedStructures(CallbackInfo ci) {
        try (var collection = ((LevelRenderer)(Object)this).collectPerFrameGizmos()) { FinderMarkers.draw(); }
    }
}
