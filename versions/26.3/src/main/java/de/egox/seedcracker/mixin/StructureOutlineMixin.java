package de.egox.seedcracker.mixin;

import de.egox.seedcracker.FinderMarkers;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class StructureOutlineMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void egox$collectObservedStructures(CallbackInfo ci) {
        try (var collection = ((LevelExtractor)(Object)this).collectPerFrameMainThreadGizmos()) { FinderMarkers.draw(); }
    }
}
