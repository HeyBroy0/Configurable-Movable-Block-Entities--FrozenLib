package dev.heybroy0.frozenpistonprobe;

import net.fabricmc.loader.api.FabricLoader;
import net.frozenblock.lib.block.api.piston.PistonEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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

    // Keep the existing short keys working for the original block-entity test list.
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Map<Block, String> KEYS_BY_BLOCK = new LinkedHashMap<>();
    private static final Properties ACTIVE = new Properties();

    static {
        add("sculk_sensor", Blocks.SCULK_SENSOR, "movable");
        add("calibrated_sculk_sensor", Blocks.CALIBRATED_SCULK_SENSOR, "movable");
        add("sculk_shrieker", Blocks.SCULK_SHRIEKER, "movable");
        add("sculk_catalyst", Blocks.SCULK_CATALYST, "movable");
        add("lectern", Blocks.LECTERN, "movable");

        // Control block entities: useful for checking ordinary inventory/data persistence.
        add("chest", Blocks.CHEST, "movable");
        add("trapped_chest", Blocks.TRAPPED_CHEST, "movable");
        add("dispenser", Blocks.DISPENSER, "movable");
        add("crafter", Blocks.CRAFTER, "movable");
        add("chiseled_bookshelf", Blocks.CHISELED_BOOKSHELF, "movable");
        // Shelves are registered per wood type; there is no generic Blocks.SHELF field.
        add("shelf", Blocks.OAK_SHELF, "movable");
        add("shelf", Blocks.SPRUCE_SHELF, "movable");
        add("shelf", Blocks.BIRCH_SHELF, "movable");
        add("shelf", Blocks.JUNGLE_SHELF, "movable");
        add("shelf", Blocks.ACACIA_SHELF, "movable");
        add("shelf", Blocks.DARK_OAK_SHELF, "movable");
        add("shelf", Blocks.MANGROVE_SHELF, "movable");
        add("shelf", Blocks.CHERRY_SHELF, "movable");
        add("shelf", Blocks.PALE_OAK_SHELF, "movable");
        add("shelf", Blocks.BAMBOO_SHELF, "movable");
        add("shelf", Blocks.CRIMSON_SHELF, "movable");
        add("shelf", Blocks.WARPED_SHELF, "movable");
        add("shelf", Blocks.POPLAR_SHELF, "movable");
    }

    private ProbeConfig() {
    }

    private static void add(String key, Block block, String defaultMode) {
        DEFAULTS.put(key, defaultMode);
        KEYS_BY_BLOCK.put(block, key);
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    public static synchronized void load() {
        Properties fromDisk = new Properties();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    fromDisk.load(reader);
                }
            }
        } catch (IOException exception) {
            FrozenPistonProbe.LOGGER.error("Could not read config {}; using defaults", CONFIG_PATH, exception);
        }

        ACTIVE.clear();
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            String key = entry.getKey();
            String rawValue = fromDisk.getProperty(key, entry.getValue()).trim().toLowerCase(Locale.ROOT);
            try {
                ACTIVE.setProperty(key, MovementMode.parse(rawValue).name().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                FrozenPistonProbe.LOGGER.warn(
                        "Invalid legacy movement mode '{}' for '{}'; using '{}'",
                        rawValue, key, entry.getValue());
                ACTIVE.setProperty(key, entry.getValue());
            }
        }

        // Dynamic entries use registry IDs, e.g. movement.minecraft:observer and
        // tick.minecraft:observer. Properties.store escapes the colon when writing the file.
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
                FrozenPistonProbe.LOGGER.warn("Invalid policy '{}' for '{}'; ignoring this entry", value, property);
            }
        }
        saveToDisk();
    }

    /** FrozenLib callback for block entities; vanilla mode defers to FrozenLib's normal rules. */
    public static PistonEvents.PushResult decidePush(BlockState state, Direction direction) {
        Block block = state.getBlock();
        MovementMode mode = movementMode(block);
        PistonEvents.PushResult result = switch (mode) {
            case MOVABLE, BREAKABLE -> PistonEvents.PushResult.SUCCESS;
            case IMMOVABLE -> PistonEvents.PushResult.FAIL;
            case VANILLA -> PistonEvents.PushResult.PASS;
        };

        if (hasExplicitMovementPolicy(block)) {
            FrozenPistonProbe.LOGGER.info(
                    "Push decision: block={} direction={} mode={} result={}",
                    BuiltInRegistries.BLOCK.getKey(block), direction, mode.name().toLowerCase(Locale.ROOT), result);
        }
        return result;
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
        return KEYS_BY_BLOCK.containsKey(block)
                || ACTIVE.containsKey(movementKey(block))
                || ACTIVE.containsKey(tickKey(block));
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
        // The observer default preserves the known-good behavior reported by the user.
        return block == Blocks.OBSERVER ? TickMode.VANILLA : TickMode.TRANSFER;
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
        return KEYS_BY_BLOCK.containsKey(block) || ACTIVE.containsKey(movementKey(block));
    }

    private static MovementMode movementMode(Block block) {
        String configured = ACTIVE.getProperty(movementKey(block));
        if (configured == null) {
            String legacyKey = KEYS_BY_BLOCK.get(block);
            if (legacyKey != null) {
                configured = ACTIVE.getProperty(legacyKey, DEFAULTS.get(legacyKey));
            }
        }
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
                ACTIVE.store(writer, "Frozen Piston Probe policies. movement.<block-id>=vanilla|movable|breakable|immovable; tick.<block-id>=vanilla|transfer|duplicate. Changes apply immediately in this process.");
            }
            return true;
        } catch (IOException exception) {
            FrozenPistonProbe.LOGGER.error("Could not write config {}", CONFIG_PATH, exception);
            return false;
        }
    }
}
