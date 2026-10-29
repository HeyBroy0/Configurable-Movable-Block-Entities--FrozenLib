package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.heybroy0.frozenpistonprobe.PistonTickTransfer;
import dev.heybroy0.frozenpistonprobe.ProbeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Captures every moved source before moveBlocks starts replacing block positions. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    @WrapOperation(
            method = "moveBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;getToPush()Ljava/util/List;",
                    ordinal = 0
            )
    )
    private List<BlockPos> frozenPistonProbe$captureTicksBeforeMutation(
            PistonStructureResolver resolver,
            Operation<List<BlockPos>> original,
            @Local(argsOnly = true) Level level) {
        List<BlockPos> sourcePositions = original.call(resolver);
        PistonTickTransfer.captureMovingSources(level, sourcePositions);
        return sourcePositions;
    }
    // The symbolic owner for this inherited method can vary with the exact mapped call site.
    // The two optional descriptors cover BlockState and its declaring BlockStateBase type; only
    // the one present in the target bytecode applies. Keeping this hook inside isPushable avoids
    // changing mining hardness globally when a test policy overrides an unbreakable block.
    @ModifyExpressionValue(
            method = "isPushable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
            require = 0
    )
    private static float frozenPistonProbe$overrideDestroySpeed(
            float original, @Local(argsOnly = true) BlockState state) {
        return ProbeConfig.applyMovementDestroySpeed(state.getBlock(), original);
    }

    @ModifyExpressionValue(
            method = "isPushable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
            require = 0
    )
    private static float frozenPistonProbe$overrideDestroySpeedFromBase(
            float original, @Local(argsOnly = true) BlockState state) {
        return ProbeConfig.applyMovementDestroySpeed(state.getBlock(), original);
    }

}
