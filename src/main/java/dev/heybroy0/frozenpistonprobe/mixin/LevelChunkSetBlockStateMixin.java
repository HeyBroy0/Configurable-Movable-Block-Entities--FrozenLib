package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.heybroy0.frozenpistonprobe.FrozenPistonProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Guards block-entity landing in both the animated tick path and the immediate finalTick path. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkSetBlockStateMixin {
    @ModifyVariable(
            method = "setBlockState",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int frozenPistonProbe$skipSideEffectsOnBlockEntityLanding(
            int updateFlags,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) BlockState newState) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        BlockState oldState = chunk.getBlockState(pos);

        // finalTick uses Level.setBlockAndUpdate, so the earlier wrapper on tick's Level.setBlock
        // never gets a chance to add this flag for very fast moves. Scope the fallback strictly to
        // moving-piston -> block-entity-state transitions; normal placements/removals are untouched.
        if (oldState.is(Blocks.MOVING_PISTON) && newState.hasBlockEntity()) {
            int skipSideEffects = PistonBaseBlock.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
            if ((updateFlags & skipSideEffects) == 0) {
                FrozenPistonProbe.LOGGER.debug(
                        "Skipping removal side effects during moving-piston landing at {} ({} -> {})",
                        pos, oldState, newState);
            }
            return updateFlags | skipSideEffects;
        }

        return updateFlags;
    }
}
