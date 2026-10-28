package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.heybroy0.frozenpistonprobe.FrozenPistonProbe;
import net.frozenblock.lib.block.impl.piston.PistonMovingBlockEntityInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Generic compatibility probe for carried block entities.
 *
 * FrozenLib saves the carried block entity's full NBT on the moving piston entity.
 * During landing, Minecraft may still invoke the old block entity's
 * preRemoveSideEffects while the state at the position is minecraft:moving_piston.
 * Some block entities assume their own block-state properties are present there
 * and crash (the reported example is SculkShriekerBlockEntity).
 *
 * When FrozenLib's saved payload exists, preserve the block-entity-side-effect skip
 * flag for the landing setBlock call as well. FrozenLib remains responsible for
 * restoring the saved block entity and dispatching its moving-block callback.
 */
@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private static boolean frozenPistonProbe$preserveBlockEntityDuringLanding(
            Level level,
            BlockPos pos,
            BlockState state,
            int updateFlags,
            Operation<Boolean> original,
            @Local(argsOnly = true) PistonMovingBlockEntity movingEntity
    ) {
        int effectiveFlags = updateFlags;
        if (movingEntity instanceof PistonMovingBlockEntityInterface frozenLibEntity
                && frozenLibEntity.frozenLib$getPushedBlockEntityTag() != null) {
            effectiveFlags |= PistonBaseBlock.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
            FrozenPistonProbe.LOGGER.debug(
                    "Preserving carried block entity during piston landing at {} (state={})",
                    pos,
                    state
            );
        }

        return original.call(level, pos, state, effectiveFlags);
    }
}
