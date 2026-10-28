package dev.heybroy0.frozenpistonprobe;

import net.fabricmc.api.ModInitializer;
import net.frozenblock.lib.block.api.piston.PistonEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FrozenPistonProbe implements ModInitializer {
    public static final String MOD_ID = "frozen_piston_probe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ProbeConfig.load();

        PistonEvents.DETERMINE_BLOCK_ENTITY_PUSH_RESULT.register(ProbeConfig::decidePush);
        PistonEvents.ON_MOVING_BLOCK_SET.register((level, pos, state, movingEntity) -> {
            if (!ProbeConfig.isTracked(state.getBlock()) && !state.is(Blocks.GRINDSTONE)) {
                return;
            }

            BlockEntity landedEntity = level.getBlockEntity(pos);
            String entityDescription = landedEntity == null
                    ? "none"
                    : landedEntity.getClass().getSimpleName();
            String mode = state.is(Blocks.GRINDSTONE)
                    ? "vanilla-baseline (not a block-entity event target)"
                    : ProbeConfig.modeName(state.getBlock());

            LOGGER.info(
                    "Moving-block callback: block={} pos={} blockEntity={} configMode={}",
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    pos,
                    entityDescription,
                    mode
            );
        });

        LOGGER.info("Frozen Piston Probe initialized. Config: {}", ProbeConfig.configPath());
        LOGGER.info("Test the sculk blocks and lectern against chest/dispenser controls; grindstone is the vanilla baseline.");
    }
}
