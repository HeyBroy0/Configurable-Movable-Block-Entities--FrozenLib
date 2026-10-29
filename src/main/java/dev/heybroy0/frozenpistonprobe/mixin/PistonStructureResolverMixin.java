package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.heybroy0.frozenpistonprobe.FrozenPistonProbe;
import dev.heybroy0.frozenpistonprobe.ProbeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Expands movable bed/door/two-part blocks during resolver checks, not just during final movement.
 * If a companion half's destination is obstructed and is not already being moved or destroyed,
 * reject the piston operation rather than moving only one half and breaking the structure.
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @ModifyReturnValue(method = "resolve", at = @At("RETURN"))
    private boolean frozenPistonProbe$includeMultiBlockCompanions(boolean original) {
        if (!original) {
            return false;
        }

        List<BlockPos> resolvedPositions = List.copyOf(this.toPush);
        List<BlockPos> expanded = ProbeConfig.includeMovableCompanions(
                this.level, resolvedPositions, this.pushDirection);
        if (expanded.size() == resolvedPositions.size()) {
            return true;
        }
        // The vanilla piston structure resolver has a 12-block push limit. Companion halves count
        // toward that limit even though vanilla did not discover them itself.
        if (expanded.size() > 12) {
            FrozenPistonProbe.LOGGER.info("Rejecting multi-block piston move: expanded push list has {} blocks",
                    expanded.size());
            return false;
        }

        Set<BlockPos> moving = new HashSet<>(expanded);
        Set<BlockPos> destroying = new HashSet<>(this.toDestroy);
        for (BlockPos source : expanded) {
            if (resolvedPositions.contains(source)) {
                continue;
            }
            BlockPos destination = source.relative(this.pushDirection);
            if (destroying.contains(destination)) {
                continue;
            }
            // A destination occupied by another source is safe: all source positions are moved
            // back-to-front. The companion insertion preserves the required relative order.
            if (moving.contains(destination)) {
                continue;
            }
            if (!this.level.getWorldBorder().isWithinBounds(destination)
                    || destination.getY() < this.level.getMinY()
                    || destination.getY() >= this.level.getMaxY()) {
                FrozenPistonProbe.LOGGER.info(
                        "Rejecting multi-block piston move because companion destination {} is outside world bounds",
                        destination);
                return false;
            }

            BlockState destinationState = this.level.getBlockState(destination);
            if (!destinationState.canBeReplaced()) {
                FrozenPistonProbe.LOGGER.info(
                        "Rejecting multi-block piston move because companion destination {} is blocked by {}",
                        destination, destinationState);
                return false;
            }
        }

        this.toPush.clear();
        this.toPush.addAll(expanded);
        return true;
    }
}
