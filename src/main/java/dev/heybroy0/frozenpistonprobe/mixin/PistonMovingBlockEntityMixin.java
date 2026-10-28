package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.heybroy0.frozenpistonprobe.FrozenPistonProbe;
import dev.heybroy0.frozenpistonprobe.PistonTickTransfer;
import dev.heybroy0.frozenpistonprobe.PistonTickTransferAccess;
import net.frozenblock.lib.block.impl.piston.PistonMovingBlockEntityInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
        PistonTickTransfer.prepareLanding(level, pos, state, movingEntity);
        boolean changed = original.call(level, pos, state);
        if (changed) {
            PistonTickTransfer.consumePayload(movingEntity);
        }
        return changed;
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
