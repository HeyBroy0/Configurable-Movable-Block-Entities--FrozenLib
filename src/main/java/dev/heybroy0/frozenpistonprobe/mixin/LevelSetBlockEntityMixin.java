package dev.heybroy0.frozenpistonprobe.mixin;

import dev.heybroy0.frozenpistonprobe.PistonTickTransfer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelSetBlockEntityMixin {
    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void frozenPistonProbe$attachCapturedTicks(BlockEntity blockEntity, CallbackInfo ci) {
        PistonTickTransfer.attachCaptured((Level) (Object) this, blockEntity);
    }
}
