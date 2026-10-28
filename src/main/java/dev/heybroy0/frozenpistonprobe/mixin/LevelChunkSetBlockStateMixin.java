package dev.heybroy0.frozenpistonprobe.mixin;

import dev.heybroy0.frozenpistonprobe.PistonTickTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkSetBlockStateMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void frozenPistonProbe$captureTicksBeforeMovingPlaceholder(
            BlockPos pos, BlockState newState, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (!newState.is(Blocks.MOVING_PISTON)) {
            return;
        }

        LevelChunk chunk = (LevelChunk) (Object) this;
        BlockState oldState = chunk.getBlockState(pos);
        PistonTickTransfer.captureSource(chunk, pos, oldState);
    }
}
