package dev.heybroy0.frozenpistonprobe.mixin;

import dev.heybroy0.frozenpistonprobe.PistonTickTransfer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkSetBlockStateMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void frozenPistonProbe$captureTicksWhenPistonVacatesSource(
            BlockPos pos, BlockState newState, int flags, CallbackInfoReturnable<BlockState> cir) {
        // Vanilla clears each origin block using UPDATE_MOVE_BY_PISTON before it places the
        // moving-piston placeholders at the destination positions. Capture while the real source
        // state and its tick queue entries are still present, not when a usually-empty destination
        // is replaced by MOVING_PISTON.
        if (!newState.isAir() || (flags & Block.UPDATE_MOVE_BY_PISTON) == 0) {
            return;
        }

        LevelChunk chunk = (LevelChunk) (Object) this;
        BlockState oldState = chunk.getBlockState(pos);
        PistonTickTransfer.captureSource(chunk, pos, oldState);
    }
}
