package dev.heybroy0.frozenpistonprobe;

import net.frozenblock.lib.block.api.piston.PistonEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Integrates movable door/bed companion handling with FrozenLib's normal
 * sticky-block resolver hooks. This deliberately does not manually append
 * positions to PistonStructureResolver.toPush.
 *
 * Stage 1 only addresses resolver membership; landing/update-order fixes are
 * intentionally deferred to a later stage.
 */
public final class MultiBlockStickiness {
    private MultiBlockStickiness() {}

    public static void register() {
        PistonEvents.DETERMINE_BLOCK_STICKINESS.register((state, direction) -> {
            if (isConfiguredMovableCompanionType(state)) {
                return PistonEvents.StickyResult.SUCCESS;
            }
            return PistonEvents.StickyResult.PASS;
        });

        PistonEvents.TRY_STICK_BLOCKS_TOGETHER.register((previous, next, direction) -> {
            boolean previousCandidate = isConfiguredMovableCompanionType(previous);
            boolean nextCandidate = isConfiguredMovableCompanionType(next);

            // If neither state is one of our candidates, leave all other mods
            // and vanilla sticky behavior untouched.
            if (!previousCandidate && !nextCandidate) {
                return PistonEvents.StickTogetherResult.PASS;
            }

            // A candidate only sticks to its exact structural companion. This
            // prevents temporarily treating doors/beds as slime-like blocks.
            return areCompanions(previous, next, direction)
                    ? PistonEvents.StickTogetherResult.SUCCESS
                    : PistonEvents.StickTogetherResult.FAIL;
        });
    }

    private static boolean isConfiguredMovableCompanionType(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.BED_PART)
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }

        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return ProbeConfig.movementModeForId(id) == ProbeConfig.MovementMode.MOVABLE;
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
            Direction expected = firstPart == BedPart.FOOT ? facing : facing.getOpposite();
            return secondPart != firstPart && directionFromFirstToSecond == expected;
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
