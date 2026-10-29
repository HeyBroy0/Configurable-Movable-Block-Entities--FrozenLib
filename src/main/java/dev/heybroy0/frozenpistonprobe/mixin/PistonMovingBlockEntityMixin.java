package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.heybroy0.frozenpistonprobe.FrozenPistonProbe;
import dev.heybroy0.frozenpistonprobe.PistonTickTransfer;
import dev.heybroy0.frozenpistonprobe.ProbeConfig;
import dev.heybroy0.frozenpistonprobe.PistonTickTransferAccess;
import net.frozenblock.lib.block.impl.piston.PistonMovingBlockEntityInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Generic compatibility experiment for scheduled state transitions during piston movement.
 * The carried payload includes only scheduled ticks matching the block/fluid at the source.
 */
@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin implements PistonTickTransferAccess {
    @Unique
    private List<PistonTickTransfer.BlockTick> frozenPistonProbe$blockTicks = List.of();

    @Unique
    private List<PistonTickTransfer.FluidTick> frozenPistonProbe$fluidTicks = List.of();

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void frozenPistonProbe$initializeTickPayload(CallbackInfo ci) {
        // Explicitly initialize our injected fields after each constructor. The null checks in
        // accessors and saveAdditional remain as a defensive fallback for preexisting instances.
        if (this.frozenPistonProbe$blockTicks == null) {
            this.frozenPistonProbe$blockTicks = List.of();
        }
        if (this.frozenPistonProbe$fluidTicks == null) {
            this.frozenPistonProbe$fluidTicks = List.of();
        }
    }

    @Unique
    @Override
    public void frozenPistonProbe$setTickPayload(PistonTickTransfer.Payload payload) {
        if (payload == null) {
            this.frozenPistonProbe$blockTicks = List.of();
            this.frozenPistonProbe$fluidTicks = List.of();
            return;
        }
        this.frozenPistonProbe$blockTicks = List.copyOf(payload.blockTicks());
        this.frozenPistonProbe$fluidTicks = List.copyOf(payload.fluidTicks());
    }

    @Unique
    @Override
    public PistonTickTransfer.Payload frozenPistonProbe$getTickPayload() {
        // Do not assume injected field initializers ran for every constructor/loading path.
        // Payload normalizes absent lists to empty immutable lists.
        return new PistonTickTransfer.Payload(this.frozenPistonProbe$blockTicks, this.frozenPistonProbe$fluidTicks);
    }

    @Unique
    @Override
    public PistonTickTransfer.Payload frozenPistonProbe$takeTickPayload() {
        PistonTickTransfer.Payload payload = this.frozenPistonProbe$getTickPayload();
        this.frozenPistonProbe$blockTicks = List.of();
        this.frozenPistonProbe$fluidTicks = List.of();
        return payload;
    }

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

        frozenPistonProbe$stagePairedLanding(level, pos, state, movingEntity);
        PistonTickTransfer.prepareLanding(level, pos, state, movingEntity);
        boolean changed = original.call(level, pos, state, effectiveFlags);
        if (changed) {
            PistonTickTransfer.consumePayload(movingEntity);
        }
        return changed;
    }

    @WrapOperation(
            method = "finalTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"
            )
    )
    private boolean frozenPistonProbe$rescheduleTicksOnFinalTick(
            Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        PistonMovingBlockEntity movingEntity = (PistonMovingBlockEntity) (Object) this;
        frozenPistonProbe$stagePairedLanding(level, pos, state, movingEntity);
        PistonTickTransfer.prepareLanding(level, pos, state, movingEntity);
        boolean changed = original.call(level, pos, state);
        if (changed) {
            PistonTickTransfer.consumePayload(movingEntity);
        }
        return changed;
    }


    /**
     * Bed and door halves finalize as two independent moving-piston block entities. Place the
     * matching companion half with shape updates suppressed immediately before this half lands;
     * this landing's normal neighbor update then sees a complete pair instead of deleting the
     * first half as an orphan. This is intentionally limited to configured movable states without
     * block entities, because this early placement does not perform FrozenLib's block-entity restore.
     */
    @Unique
    private static void frozenPistonProbe$stagePairedLanding(
            Level level, BlockPos pos, BlockState movedState, PistonMovingBlockEntity movingEntity) {
        if (movedState.hasBlockEntity()
                || (!movedState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                && !movedState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF))) {
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(movedState.getBlock()).toString();
        if (ProbeConfig.movementModeForId(blockId) != ProbeConfig.MovementMode.MOVABLE) {
            return;
        }

        BlockPos companionPos = ProbeConfig.companionPosition(pos, movedState);
        if (companionPos == null || !level.getBlockState(companionPos).is(Blocks.MOVING_PISTON)) {
            return;
        }
        BlockEntity companionBlockEntity = level.getBlockEntity(companionPos);
        if (!(companionBlockEntity instanceof PistonMovingBlockEntity companionMoving)
                || companionMoving == movingEntity) {
            return;
        }

        BlockState companionState = companionMoving.getMovedState();
        if (companionState.getBlock() != movedState.getBlock() || companionState.hasBlockEntity()) {
            return;
        }
        BlockPos reciprocal = ProbeConfig.companionPosition(companionPos, companionState);
        if (!pos.equals(reciprocal)) {
            return;
        }

        PistonTickTransfer.prepareLanding(level, companionPos, companionState, companionMoving);
        boolean changed = level.setBlock(
                companionPos,
                companionState,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (changed) {
            PistonTickTransfer.consumePayload(companionMoving);
            FrozenPistonProbe.LOGGER.info(
                    "Staged paired multi-block landing: {} at {} before {} at {}",
                    BuiltInRegistries.BLOCK.getKey(companionState.getBlock()), companionPos,
                    BuiltInRegistries.BLOCK.getKey(movedState.getBlock()), pos);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void frozenPistonProbe$loadCarriedTicks(ValueInput input, CallbackInfo ci) {
        List<PistonTickTransfer.BlockTick> blockTicks = input.read(
                PistonTickTransfer.blockTicksKey(), PistonTickTransfer.BLOCK_TICK_CODEC.listOf()).orElse(List.of());
        List<PistonTickTransfer.FluidTick> fluidTicks = input.read(
                PistonTickTransfer.fluidTicksKey(), PistonTickTransfer.FLUID_TICK_CODEC.listOf()).orElse(List.of());
        this.frozenPistonProbe$setTickPayload(new PistonTickTransfer.Payload(blockTicks, fluidTicks));
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void frozenPistonProbe$saveCarriedTicks(ValueOutput output, CallbackInfo ci) {
        if (this.frozenPistonProbe$blockTicks != null && !this.frozenPistonProbe$blockTicks.isEmpty()) {
            output.store(PistonTickTransfer.blockTicksKey(), PistonTickTransfer.BLOCK_TICK_CODEC.listOf(),
                    this.frozenPistonProbe$blockTicks);
        }
        if (this.frozenPistonProbe$fluidTicks != null && !this.frozenPistonProbe$fluidTicks.isEmpty()) {
            output.store(PistonTickTransfer.fluidTicksKey(), PistonTickTransfer.FLUID_TICK_CODEC.listOf(),
                    this.frozenPistonProbe$fluidTicks);
        }
    }
}
