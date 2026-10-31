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
        MultiBlockStickiness.register();

        PistonEvents.ON_MOVING_BLOCK_SET.register((level, pos, state, movingEntity) -> {
            if (!ProbeConfig.isTracked(state.getBlock()) && !isBaseline(state.getBlock())) {
                return;
            }

            BlockEntity landedEntity = level.getBlockEntity(pos);
            String entityDescription = landedEntity == null
                    ? "none"
                    : landedEntity.getClass().getSimpleName();
            String mode = isBaseline(state.getBlock())
                    ? "vanilla-baseline (scheduled-state comparison; not a block-entity event target)"
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
        LOGGER.info("Test transient block states via the scheduled-tick transfer, then compare the redstone lamp, target, observer, button, and pressure plate baselines.");
    }

    private static boolean isBaseline(net.minecraft.world.level.block.Block block) {
        return block == Blocks.GRINDSTONE
                || block == Blocks.REDSTONE_LAMP
                || block == Blocks.TARGET
                || block == Blocks.OBSERVER
                || block == Blocks.STONE_BUTTON
                || block == Blocks.STONE_PRESSURE_PLATE
                || block == Blocks.REDSTONE_TORCH;
    }
}
