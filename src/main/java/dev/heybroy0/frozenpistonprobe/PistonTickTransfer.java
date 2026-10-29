package dev.heybroy0.frozenpistonprobe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickContainerAccess;
import net.minecraft.world.ticks.TickPriority;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Experimental transfer of pending scheduled ticks for a block carried by a vanilla piston.
 *
 * Only ticks whose scheduled type matches the block/fluid actually present at the source
 * position are moved. Unrelated tick types at that same position are deliberately left alone.
 */
public final class PistonTickTransfer {
    private static final String BLOCK_TICKS_KEY = "frozenPistonProbe_BlockTicks";
    private static final String FLUID_TICKS_KEY = "frozenPistonProbe_FluidTicks";

    public static final Codec<BlockTick> BLOCK_TICK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("type").forGetter(BlockTick::type),
            Codec.LONG.fieldOf("trigger_tick").forGetter(BlockTick::triggerTick),
            TickPriority.CODEC.fieldOf("priority").forGetter(BlockTick::priority),
            Codec.LONG.fieldOf("sub_tick_order").forGetter(BlockTick::subTickOrder)
    ).apply(instance, BlockTick::new));

    public static final Codec<FluidTick> FLUID_TICK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("type").forGetter(FluidTick::type),
            Codec.LONG.fieldOf("trigger_tick").forGetter(FluidTick::triggerTick),
            TickPriority.CODEC.fieldOf("priority").forGetter(FluidTick::priority),
            Codec.LONG.fieldOf("sub_tick_order").forGetter(FluidTick::subTickOrder)
    ).apply(instance, FluidTick::new));

    private static final Map<Level, Map<BlockPos, Payload>> CAPTURED_BEFORE_MOVING_ENTITY = new WeakHashMap<>();

    private PistonTickTransfer() {
    }

    public record BlockTick(Block type, long triggerTick, TickPriority priority, long subTickOrder) {
    }

    public record FluidTick(Fluid type, long triggerTick, TickPriority priority, long subTickOrder) {
    }

    public record Payload(List<BlockTick> blockTicks, List<FluidTick> fluidTicks) {
        public Payload {
            // Mixin-added fields can be null on moving-piston instances that never received
            // a captured payload (for example, ordinary blocks with no pending scheduled ticks).
            // Treat an absent collection as an empty payload rather than crashing during landing
            // or while Minecraft is generating a block-entity save/crash report.
            blockTicks = blockTicks == null ? List.of() : List.copyOf(blockTicks);
            fluidTicks = fluidTicks == null ? List.of() : List.copyOf(fluidTicks);
        }

        public boolean isEmpty() {
            return blockTicks.isEmpty() && fluidTicks.isEmpty();
        }
    }

    /**
     * Capture ticks for the entire resolved push list before vanilla starts replacing any of its
     * positions. This is essential for a chain: most source positions are overwritten by another
     * moving-piston placeholder rather than being set to air, so a source-clear-only hook misses them.
     */
    public static void captureMovingSources(Level level, List<BlockPos> sourcePositions) {
        if (level.isClientSide()) {
            return;
        }

        FrozenPistonProbe.LOGGER.info(
                "Scanning {} resolved piston source positions for scheduled ticks", sourcePositions.size());
        for (BlockPos pos : sourcePositions) {
            LevelChunk chunk = level.getChunkAt(pos);
            captureSource(chunk, pos, level.getBlockState(pos));
        }
    }

    /** Called while the original block state and its per-chunk tick entries are still present. */
    public static void captureSource(LevelChunk chunk, BlockPos pos, net.minecraft.world.level.block.state.BlockState oldState) {
        Level level = chunk.getLevel();
        if (level.isClientSide() || oldState.isAir() || oldState.is(Blocks.MOVING_PISTON)) {
            return;
        }

        List<ScheduledTick<Block>> matchingBlockTicks = extractMatching(
                chunk.getBlockTicks(), pos, oldState.getBlock());

        Fluid sourceFluid = oldState.getFluidState().getType();
        List<ScheduledTick<Fluid>> matchingFluidTicks = sourceFluid == Fluids.EMPTY
                ? List.of()
                : extractMatching(chunk.getFluidTicks(), pos, sourceFluid);

        List<BlockTick> blockRecords = matchingBlockTicks.stream()
                .map(tick -> new BlockTick(tick.type(), tick.triggerTick(), tick.priority(), tick.subTickOrder()))
                .toList();
        List<FluidTick> fluidRecords = matchingFluidTicks.stream()
                .map(tick -> new FluidTick(tick.type(), tick.triggerTick(), tick.priority(), tick.subTickOrder()))
                .toList();

        Payload payload = new Payload(blockRecords, fluidRecords);
        synchronized (CAPTURED_BEFORE_MOVING_ENTITY) {
            Map<BlockPos, Payload> levelCaptures = CAPTURED_BEFORE_MOVING_ENTITY.computeIfAbsent(
                    level, ignored -> new java.util.HashMap<>());
            levelCaptures.remove(pos);
            if (!payload.isEmpty()) {
                levelCaptures.put(pos.immutable(), payload);
                FrozenPistonProbe.LOGGER.info(
                        "Captured scheduled ticks at {} for {}: block={}, fluid={}",
                        pos, BuiltInRegistries.BLOCK.getKey(oldState.getBlock()),
                        blockRecords.size(), fluidRecords.size());
            }
            if (levelCaptures.isEmpty()) {
                CAPTURED_BEFORE_MOVING_ENTITY.remove(level);
            }
        }
    }

    private static <T> List<ScheduledTick<T>> extractMatching(
            TickContainerAccess<T> tickAccess, BlockPos pos, T expectedType) {
        // Fast path: avoid scanning every tick in a chunk for the common no-pending-tick case.
        if (!tickAccess.hasScheduledTick(pos, expectedType)) {
            return List.of();
        }

        if (!(tickAccess instanceof LevelChunkTicks<?> untypedTicks)) {
            FrozenPistonProbe.LOGGER.warn(
                    "Cannot inspect per-chunk tick queue at {}; expected LevelChunkTicks, found {}",
                    pos, tickAccess.getClass().getName());
            return List.of();
        }

        @SuppressWarnings("unchecked")
        LevelChunkTicks<T> ticks = (LevelChunkTicks<T>) untypedTicks;
        List<ScheduledTick<T>> matches = ticks.getAll()
                .filter(tick -> tick.pos().equals(pos) && tick.type() == expectedType)
                .toList();

        if (!matches.isEmpty()) {
            // removeIf also removes these entries from LevelChunkTicks' deduplication set.
            // LevelTicks' cached head is corrected by its normal queue-index reconciliation.
            ticks.removeIf(tick -> tick.pos().equals(pos) && tick.type() == expectedType);
        }
        return matches;
    }

    /** Called when FrozenLib installs the moving-piston entity that carries a captured source block. */
    public static void attachCaptured(Level level, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (level.isClientSide() || !(blockEntity instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity movingEntity)
                || !(movingEntity instanceof PistonTickTransferAccess access)) {
            return;
        }

        // The piston head/base placeholder is not a carried ordinary block and must not consume
        // a neighbouring block's saved ticks.
        if (movingEntity.isSourcePiston()) {
            return;
        }

        BlockPos destinationPos = movingEntity.getBlockPos();
        BlockPos sourcePos = destinationPos.relative(movingEntity.getMovementDirection().getOpposite()).immutable();
        Payload payload;
        synchronized (CAPTURED_BEFORE_MOVING_ENTITY) {
            Map<BlockPos, Payload> levelCaptures = CAPTURED_BEFORE_MOVING_ENTITY.get(level);
            if (levelCaptures == null) {
                return;
            }
            payload = levelCaptures.remove(sourcePos);
            if (levelCaptures.isEmpty()) {
                CAPTURED_BEFORE_MOVING_ENTITY.remove(level);
            }
        }

        if (payload != null && !payload.isEmpty()) {
            access.frozenPistonProbe$setTickPayload(payload);
            FrozenPistonProbe.LOGGER.info(
                    "Attached scheduled ticks from source {} to moving piston at {}: block={}, fluid={}",
                    sourcePos, destinationPos, payload.blockTicks().size(), payload.fluidTicks().size());
        }
    }

    /**
     * Queue the captured ticks immediately before the landing setBlock call. This is deliberate:
     * landing can issue neighbor updates that try to schedule a fresh timer. By queueing the carried
     * tick first, vanilla's type+position dedup keeps the original expiry rather than replacing it
     * with a new full-delay timer.
     */
    public static void prepareLanding(Level level, BlockPos destination,
                                      net.minecraft.world.level.block.state.BlockState landingState,
                                      net.minecraft.world.level.block.piston.PistonMovingBlockEntity movingEntity) {
        if (!(movingEntity instanceof PistonTickTransferAccess access)) {
            return;
        }

        Payload payload = access.frozenPistonProbe$getTickPayload();
        if (payload == null || payload.isEmpty()) {
            return;
        }

        BlockPos target = destination.immutable();
        int queuedBlocks = 0;
        int queuedFluids = 0;

        for (BlockTick tick : payload.blockTicks()) {
            if (!landingState.is(tick.type())) {
                continue;
            }
            level.getBlockTicks().schedule(new ScheduledTick<>(
                    tick.type(), target, tick.triggerTick(), tick.priority(), tick.subTickOrder()));
            queuedBlocks++;
        }

        Fluid landingFluid = landingState.getFluidState().getType();
        for (FluidTick tick : payload.fluidTicks()) {
            if (landingFluid != tick.type()) {
                continue;
            }
            level.getFluidTicks().schedule(new ScheduledTick<>(
                    tick.type(), target, tick.triggerTick(), tick.priority(), tick.subTickOrder()));
            queuedFluids++;
        }

        FrozenPistonProbe.LOGGER.info(
                "Queued carried ticks for landing at {}: block={}/{}, fluid={}/{}",
                target, queuedBlocks, payload.blockTicks().size(), queuedFluids, payload.fluidTicks().size());
    }

    public static void consumePayload(net.minecraft.world.level.block.piston.PistonMovingBlockEntity movingEntity) {
        if (movingEntity instanceof PistonTickTransferAccess access) {
            access.frozenPistonProbe$takeTickPayload();
        }
    }

    public static String blockTicksKey() {
        return BLOCK_TICKS_KEY;
    }

    public static String fluidTicksKey() {
        return FLUID_TICKS_KEY;
    }
}
