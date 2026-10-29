package dev.heybroy0.frozenpistonprobe.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.heybroy0.frozenpistonprobe.ProbeConfig;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Makes configured piston reactions consistent between pushability checks and the resolver. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStatePistonReactionMixin {
    @ModifyReturnValue(method = "getPistonPushReaction", at = @At("RETURN"))
    private PushReaction frozenPistonProbe$applyConfiguredMovementPolicy(PushReaction original) {
        BlockBehaviour.BlockStateBase state = (BlockBehaviour.BlockStateBase) (Object) this;
        return ProbeConfig.applyMovementReaction(state.getBlock(), original);
    }
}
