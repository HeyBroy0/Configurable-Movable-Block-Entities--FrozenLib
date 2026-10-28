package dev.heybroy0.frozenpistonprobe;

import net.fabricmc.loader.api.FabricLoader;
import net.frozenblock.lib.block.api.piston.PistonEvents;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class ProbeConfig {
    private enum Mode {
        ALLOW,
        DENY,
        PASS;

        static Mode parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "allow" -> ALLOW;
                case "deny" -> DENY;
                case "pass" -> PASS;
                default -> throw new IllegalArgumentException("Unknown mode: " + value);
            };
        }
    }

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("frozen-piston-probe.properties");

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Map<Block, String> KEYS_BY_BLOCK = new LinkedHashMap<>();
    private static final Properties ACTIVE = new Properties();
    private static final Set<String> VALID_MODES = Set.of("allow", "deny", "pass");

    static {
        add("sculk_sensor", Blocks.SCULK_SENSOR, "allow");
        add("calibrated_sculk_sensor", Blocks.CALIBRATED_SCULK_SENSOR, "allow");
        add("sculk_shrieker", Blocks.SCULK_SHRIEKER, "allow");
        add("sculk_catalyst", Blocks.SCULK_CATALYST, "allow");
        add("lectern", Blocks.LECTERN, "allow");

        // Control block entities: useful for checking ordinary inventory/data persistence.
        add("chest", Blocks.CHEST, "allow");
        add("trapped_chest", Blocks.TRAPPED_CHEST, "allow");
        add("dispenser", Blocks.DISPENSER, "allow");
    }

    private ProbeConfig() {
    }

    private static void add(String key, Block block, String defaultMode) {
        DEFAULTS.put(key, defaultMode);
        KEYS_BY_BLOCK.put(block, key);
    }

    static Path configPath() {
        return CONFIG_PATH;
    }

    static synchronized void load() {
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
            String value = fromDisk.getProperty(key, entry.getValue()).trim().toLowerCase(Locale.ROOT);
            if (!VALID_MODES.contains(value)) {
                FrozenPistonProbe.LOGGER.warn(
                        "Invalid mode '{}' for '{}'; expected allow, deny, or pass. Using '{}'",
                        value, key, entry.getValue()
                );
                value = entry.getValue();
            }
            ACTIVE.setProperty(key, value);
        }

        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            ACTIVE.store(writer,
                    "Frozen Piston Probe modes: allow = force FrozenLib success; deny = refuse the push; pass = defer to FrozenLib tags/other callbacks. Restart Minecraft after editing.");
        } catch (IOException exception) {
            FrozenPistonProbe.LOGGER.error("Could not write config {}", CONFIG_PATH, exception);
        }
    }

    static PistonEvents.PushResult decidePush(BlockState state, Direction direction) {
        String key = KEYS_BY_BLOCK.get(state.getBlock());
        if (key == null) {
            return PistonEvents.PushResult.PASS;
        }

        Mode mode;
        try {
            mode = Mode.parse(ACTIVE.getProperty(key, DEFAULTS.get(key)));
        } catch (IllegalArgumentException exception) {
            mode = Mode.parse(DEFAULTS.get(key));
        }

        PistonEvents.PushResult result = switch (mode) {
            case ALLOW -> PistonEvents.PushResult.SUCCESS;
            case DENY -> PistonEvents.PushResult.FAIL;
            case PASS -> PistonEvents.PushResult.PASS;
        };

        FrozenPistonProbe.LOGGER.info(
                "Push decision: block={} direction={} mode={} result={}",
                state.getBlock().builtInRegistryHolder().key().location(),
                direction,
                mode.name().toLowerCase(Locale.ROOT),
                result
        );
        return result;
    }

    static boolean isTracked(Block block) {
        return KEYS_BY_BLOCK.containsKey(block);
    }

    static String modeName(Block block) {
        String key = KEYS_BY_BLOCK.get(block);
        if (key == null) {
            return "not-configured";
        }
        return ACTIVE.getProperty(key, DEFAULTS.get(key));
    }
}
