package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.heybroy0.frozenpistonprobe.PistonTickTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
}
