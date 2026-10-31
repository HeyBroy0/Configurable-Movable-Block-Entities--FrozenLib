package dev.heybroy0.frozenpistonprobe;

import net.frozenblock.lib.block.api.piston.PistonEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Integrates configured movable multiblocks with FrozenLib's piston
 * stickiness events.
 *
 * Movable bed/door-style states can stick to slime and honey blocks, and
 * matching halves can stick to each other. Other blocks retain their normal
 * FrozenLib/vanilla behavior.
 */
public final class MultiBlockStickiness {
    private MultiBlockStickiness() {
    }

    public static void register() {
        PistonEvents.DETERMINE_BLOCK_STICKINESS.register((state, direction) ->
                isConfiguredMovableMultiBlock(state)
                        ? PistonEvents.StickyResult.SUCCESS
                        : PistonEvents.StickyResult.PASS
        );

        PistonEvents.TRY_STICK_BLOCKS_TOGETHER.register((previous, next, direction) -> {
            boolean previousIsMultiBlock = isConfiguredMovableMultiBlock(previous);
            boolean nextIsMultiBlock = isConfiguredMovableMultiBlock(next);

            if (!previousIsMultiBlock && !nextIsMultiBlock) {
                return PistonEvents.StickTogetherResult.PASS;
            }

            if ((previousIsMultiBlock && isSlimeOrHoney(next))
                    || (nextIsMultiBlock && isSlimeOrHoney(previous))) {
                return PistonEvents.StickTogetherResult.SUCCESS;
            }

            return areCompanions(previous, next, direction)
                    ? PistonEvents.StickTogetherResult.SUCCESS
                    : PistonEvents.StickTogetherResult.FAIL;
        });
    }

    private static boolean isConfiguredMovableMultiBlock(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.BED_PART)
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }

        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return ProbeConfig.movementModeForId(id) == ProbeConfig.MovementMode.MOVABLE;
    }

    private static boolean isSlimeOrHoney(BlockState state) {
        return state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK);
    }

    private static boolean areCompanions(
            BlockState first,
            BlockState second,
            Direction directionFromFirstToSecond
    ) {
        if (first.getBlock() != second.getBlock()) {
            return false;
        }

        if (first.hasProperty(BlockStateProperties.BED_PART)
                && second.hasProperty(BlockStateProperties.BED_PART)) {
            if (!first.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    || !second.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return false;
            }

            if (first.getValue(BlockStateProperties.HORIZONTAL_FACING)
                    != second.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                return false;
            }

            BedPart firstPart = first.getValue(BlockStateProperties.BED_PART);
            BedPart secondPart = second.getValue(BlockStateProperties.BED_PART);
            if (firstPart == secondPart) {
                return false;
            }

            Direction facing = first.getValue(BlockStateProperties.HORIZONTAL_FACING);
            Direction expected = firstPart == BedPart.FOOT
                    ? facing
                    : facing.getOpposite();

            return directionFromFirstToSecond == expected;
        }

        if (first.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && second.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf firstHalf = first.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            DoubleBlockHalf secondHalf = second.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);

            return firstHalf != secondHalf
                    && ((firstHalf == DoubleBlockHalf.LOWER && directionFromFirstToSecond == Direction.UP)
                    || (firstHalf == DoubleBlockHalf.UPPER && directionFromFirstToSecond == Direction.DOWN));
        }

        return false;
    }
}
