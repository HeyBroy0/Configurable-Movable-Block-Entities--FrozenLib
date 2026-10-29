package dev.heybroy0.frozenpistonprobe;

import net.fabricmc.loader.api.FabricLoader;
import net.frozenblock.lib.block.api.piston.PistonEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Live, per-block policies used by the probe and its in-game test screen. */
public final class ProbeConfig {
    public enum MovementMode {
        VANILLA, MOVABLE, BREAKABLE, IMMOVABLE;

        public MovementMode next() {
            MovementMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static MovementMode parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "vanilla", "pass" -> VANILLA;
                case "movable", "allow" -> MOVABLE;
                case "breakable" -> BREAKABLE;
                case "immovable", "deny" -> IMMOVABLE;
                default -> throw new IllegalArgumentException("Unknown movement mode: " + value);
            };
        }
    }

    public enum TickMode {
        VANILLA, TRANSFER, DUPLICATE;

        public TickMode next() {
            TickMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static TickMode parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "vanilla" -> VANILLA;
                case "transfer" -> TRANSFER;
                case "duplicate" -> DUPLICATE;
                default -> throw new IllegalArgumentException("Unknown tick mode: " + value);
            };
        }
    }

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("frozen-piston-probe.properties");
    private static final String MOVEMENT_PREFIX = "movement.";
    private static final String TICK_PREFIX = "tick.";

    // This map now exists only to migrate older short-key properties such as
    // "chest=movable". Active movement defaults live in the bundled properties file so
    // the same values are visible and editable in the generated config.
    private static final Map<Block, String> LEGACY_KEYS_BY_BLOCK = new LinkedHashMap<>();
    private static final Properties ACTIVE = new Properties();

    static {
        addLegacyKey("sculk_sensor", Blocks.SCULK_SENSOR);
        addLegacyKey("calibrated_sculk_sensor", Blocks.CALIBRATED_SCULK_SENSOR);
        addLegacyKey("sculk_shrieker", Blocks.SCULK_SHRIEKER);
        addLegacyKey("sculk_catalyst", Blocks.SCULK_CATALYST);
        addLegacyKey("lectern", Blocks.LECTERN);

        // Control block entities: useful for checking ordinary inventory/data persistence.
        addLegacyKey("chest", Blocks.CHEST);
        addLegacyKey("trapped_chest", Blocks.TRAPPED_CHEST);
        addLegacyKey("dispenser", Blocks.DISPENSER);
        addLegacyKey("crafter", Blocks.CRAFTER);
        addLegacyKey("chiseled_bookshelf", Blocks.CHISELED_BOOKSHELF);
        // Shelves are registered per wood type; there is no generic Blocks.SHELF field.
        addLegacyKey("shelf", Blocks.OAK_SHELF);
        addLegacyKey("shelf", Blocks.SPRUCE_SHELF);
        addLegacyKey("shelf", Blocks.BIRCH_SHELF);
        addLegacyKey("shelf", Blocks.JUNGLE_SHELF);
        addLegacyKey("shelf", Blocks.ACACIA_SHELF);
        addLegacyKey("shelf", Blocks.DARK_OAK_SHELF);
        addLegacyKey("shelf", Blocks.MANGROVE_SHELF);
        addLegacyKey("shelf", Blocks.CHERRY_SHELF);
        addLegacyKey("shelf", Blocks.PALE_OAK_SHELF);
        addLegacyKey("shelf", Blocks.BAMBOO_SHELF);
        addLegacyKey("shelf", Blocks.CRIMSON_SHELF);
        addLegacyKey("shelf", Blocks.WARPED_SHELF);
        addLegacyKey("shelf", Blocks.POPLAR_SHELF);
    }

    private ProbeConfig() {
    }

    private static void addLegacyKey(String key, Block block) {
        LEGACY_KEYS_BY_BLOCK.put(block, key);
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    public static synchronized void load() {
        Properties bundledDefaults = new Properties();
        try (Reader reader = new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(
                        ProbeConfig.class.getResourceAsStream("/frozen-piston-probe-defaults.properties"),
                        "Missing bundled default config"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            bundledDefaults.load(reader);
        } catch (IOException | NullPointerException exception) {
            FrozenPistonProbe.LOGGER.error("Could not read bundled defaults; starting with vanilla movement policies", exception);
        }

        Properties fromDisk = new Properties();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    fromDisk.load(reader);
                }
            }
        } catch (IOException exception) {
            FrozenPistonProbe.LOGGER.error("Could not read config {}; using bundled defaults", CONFIG_PATH, exception);
        }

        ACTIVE.clear();
        copyValidatedDefaults(bundledDefaults);

        // Migrate the old hardcoded-list format (for example chest=movable) to explicit
        // registry-ID keys. An explicit modern key always wins over its legacy alias.
        for (Map.Entry<Block, String> entry : LEGACY_KEYS_BY_BLOCK.entrySet()) {
            String legacyValue = fromDisk.getProperty(entry.getValue());
            String currentKey = movementKey(entry.getKey());
            if (legacyValue != null && !fromDisk.containsKey(currentKey)) {
                try {
                    ACTIVE.setProperty(currentKey, MovementMode.parse(legacyValue.trim()).name().toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    FrozenPistonProbe.LOGGER.warn("Invalid legacy movement mode '{}' for '{}'; ignoring legacy value",
                            legacyValue, entry.getValue());
                }
            }
        }

        // Load all per-block settings from disk, normalizing values. Properties keeps the
        // colons in registry IDs as part of the key once Java's escaping has been decoded.
        for (String property : fromDisk.stringPropertyNames()) {
            boolean movement = property.startsWith(MOVEMENT_PREFIX);
            boolean ticks = property.startsWith(TICK_PREFIX);
            if (!movement && !ticks) {
                continue;
            }
            String value = fromDisk.getProperty(property, "").trim().toLowerCase(Locale.ROOT);
            try {
                if (movement) {
                    ACTIVE.setProperty(property, MovementMode.parse(value).name().toLowerCase(Locale.ROOT));
                } else {
                    ACTIVE.setProperty(property, TickMode.parse(value).name().toLowerCase(Locale.ROOT));
                }
            } catch (IllegalArgumentException exception) {
                FrozenPistonProbe.LOGGER.warn("Invalid policy '{}' for '{}'; using default if available", value, property);
            }
        }

        saveToDisk();
    }

    private static void copyValidatedDefaults(Properties defaults) {
        for (String property : defaults.stringPropertyNames()) {
            String value = defaults.getProperty(property, "").trim().toLowerCase(Locale.ROOT);
            try {
                if (property.startsWith(MOVEMENT_PREFIX)) {
                    ACTIVE.setProperty(property, MovementMode.parse(value).name().toLowerCase(Locale.ROOT));
                } else if (property.startsWith(TICK_PREFIX)) {
                    ACTIVE.setProperty(property, TickMode.parse(value).name().toLowerCase(Locale.ROOT));
                }
            } catch (IllegalArgumentException exception) {
                FrozenPistonProbe.LOGGER.warn("Invalid bundled default '{}' for '{}'; ignoring it", value, property);
            }
        }
    }

    /** FrozenLib callback for block entities, honoring explicit per-block policies. */
    public static PistonEvents.PushResult decidePush(BlockState state, Direction direction) {
        Block block = state.getBlock();
        MovementMode mode = movementMode(block);
        boolean explicitPolicy = hasExplicitMovementPolicy(block);
        // PASS for blocks without a probe policy preserves other mods' FrozenLib callbacks.
        // For an explicit "vanilla" policy, FAIL disables FrozenLib's tagged fallback: vanilla
        // Minecraft does not carry block entities inside moving pistons.
        if (!explicitPolicy) {
            return PistonEvents.PushResult.PASS;
        }
        PistonEvents.PushResult result = switch (mode) {
            case MOVABLE, BREAKABLE -> PistonEvents.PushResult.SUCCESS;
            case IMMOVABLE, VANILLA -> PistonEvents.PushResult.FAIL;
        };

        if (explicitPolicy) {
            FrozenPistonProbe.LOGGER.info(
                    "Push decision: block={} direction={} mode={} result={}",
                    BuiltInRegistries.BLOCK.getKey(block), direction, mode.name().toLowerCase(Locale.ROOT), result);
        }
        return result;
    }

    /**
     * Adds companion halves of configured movable multi-blocks to the source list before vanilla
     * starts replacing positions. This lets doors, beds, and other two-part blocks travel as a pair
     * instead of one half being removed by the neighbor update of the other.
     */
    public static List<BlockPos> includeMovableCompanions(
            Level level, List<BlockPos> sourcePositions, Direction pushDirection) {
        List<BlockPos> expanded = new ArrayList<>(sourcePositions);
        Set<BlockPos> included = new HashSet<>(sourcePositions);

        // Iterate over the original resolver list only. Companion positions should not trigger
        // recursive expansion, and a valid pair shares one registered Block instance.
        for (BlockPos pos : sourcePositions) {
            BlockState state = level.getBlockState(pos);
            if (movementMode(state.getBlock()) != MovementMode.MOVABLE) {
                continue;
            }

            BlockPos companion = companionPosition(pos, state);
            if (companion == null || included.contains(companion)) {
                continue;
            }

            BlockState companionState = level.getBlockState(companion);
            if (companionState.getBlock() != state.getBlock()) {
                continue;
            }

            BlockPos stableCompanion = companion.immutable();
            int sourceIndex = expanded.indexOf(pos);
            int sourceProjection = pushProjection(pos, pushDirection);
            int companionProjection = pushProjection(stableCompanion, pushDirection);
            // moveBlocks processes the resolver list backwards. Insert the farther-ahead half
            // after its partner so it lands/moves first, while retaining vanilla's ordering for
            // unrelated source positions.
            expanded.add(sourceIndex + (companionProjection > sourceProjection ? 1 : 0), stableCompanion);
            included.add(stableCompanion);
            FrozenPistonProbe.LOGGER.info(
                    "Including multi-block companion in piston move: {} at {} with {}",
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos, companion);
        }
        return expanded;
    }

    private static int pushProjection(BlockPos pos, Direction direction) {
        return pos.getX() * direction.getStepX()
                + pos.getY() * direction.getStepY()
                + pos.getZ() * direction.getStepZ();
    }

    /** Returns the other half's position for a bed or two-block-high state, if applicable. */
    public static BlockPos companionPosition(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return pos.relative(part == BedPart.FOOT ? facing : facing.getOpposite());
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            return half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        return null;
    }

    /**
     * Overrides the vanilla per-block piston reaction. World-border, build-height, destroy-speed,
     * extended-piston, and resolver limits remain vanilla checks.
     */
    public static PushReaction applyMovementReaction(Block block, PushReaction original) {
        return switch (movementMode(block)) {
            case VANILLA -> original;
            case MOVABLE -> PushReaction.PUSH_PULL;
            case BREAKABLE -> PushReaction.POPPED;
            case IMMOVABLE -> PushReaction.IMMOVEABLE;
        };
    }

    /** Lets explicit movable/breakable policies override vanilla's unbreakable-hardness veto. */
    public static float applyMovementDestroySpeed(Block block, float original) {
        if (original == -1.0F) {
            MovementMode mode = movementMode(block);
            if (mode == MovementMode.MOVABLE || mode == MovementMode.BREAKABLE) {
                return 0.0F;
            }
        }
        return original;
    }

    public static boolean isTracked(Block block) {
        return ACTIVE.containsKey(movementKey(block)) || ACTIVE.containsKey(tickKey(block));
    }

    public static String modeName(Block block) {
        if (!hasExplicitMovementPolicy(block)) {
            return "vanilla";
        }
        return movementMode(block).name().toLowerCase(Locale.ROOT);
    }

    public static TickMode tickMode(Block block) {
        String configured = ACTIVE.getProperty(tickKey(block));
        if (configured != null) {
            try {
                return TickMode.parse(configured);
            } catch (IllegalArgumentException ignored) {
                // load() validates disk values; keep this fallback defensive for runtime edits.
            }
        }
        // Per-block exceptions such as the observer live in the bundled/editable properties file.
        return TickMode.TRANSFER;
    }

    public static MovementMode movementModeForId(String rawId) {
        return movementMode(requireBlock(rawId));
    }

    public static TickMode tickModeForId(String rawId) {
        return tickMode(requireBlock(rawId));
    }

    public static boolean isValidBlockId(String rawId) {
        return findBlock(rawId) != null;
    }

    /** Applies both values immediately, then persists them. A false return means disk save failed. */
    public static synchronized boolean setPolicies(String rawId, MovementMode movementMode, TickMode tickMode) {
        Block block = requireBlock(rawId);
        ACTIVE.setProperty(movementKey(block), movementMode.name().toLowerCase(Locale.ROOT));
        ACTIVE.setProperty(tickKey(block), tickMode.name().toLowerCase(Locale.ROOT));
        FrozenPistonProbe.LOGGER.info("Updated probe policies for {}: movement={}, ticks={}",
                BuiltInRegistries.BLOCK.getKey(block), movementMode, tickMode);
        return saveToDisk();
    }

    private static boolean hasExplicitMovementPolicy(Block block) {
        // Only loaded/configured registry-ID keys define active policy. The legacy map is for
        // migration only and must not keep old hardcoded block lists active by itself.
        return ACTIVE.containsKey(movementKey(block));
    }

    private static MovementMode movementMode(Block block) {
        String configured = ACTIVE.getProperty(movementKey(block));
        if (configured == null) {
            return MovementMode.VANILLA;
        }
        try {
            return MovementMode.parse(configured);
        } catch (IllegalArgumentException ignored) {
            return MovementMode.VANILLA;
        }
    }

    private static String movementKey(Block block) {
        return MOVEMENT_PREFIX + BuiltInRegistries.BLOCK.getKey(block);
    }

    private static String tickKey(Block block) {
        return TICK_PREFIX + BuiltInRegistries.BLOCK.getKey(block);
    }

    private static Block requireBlock(String rawId) {
        Block block = findBlock(rawId);
        if (block == null) {
            throw new IllegalArgumentException("Unknown block id: " + rawId);
        }
        return block;
    }

    private static Block findBlock(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        Identifier id = Identifier.tryParse(rawId.trim());
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    private static synchronized boolean saveToDisk() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                ACTIVE.store(writer, "Frozen Piston Probe policies. movement.<block-id>=vanilla|movable|breakable|immovable; tick.<block-id>=vanilla|transfer|duplicate. Changes apply immediately in this process. Defaults originate from the bundled properties file.");
            }
            return true;
        } catch (IOException exception) {
            FrozenPistonProbe.LOGGER.error("Could not write config {}", CONFIG_PATH, exception);
            return false;
        }
    }
}
