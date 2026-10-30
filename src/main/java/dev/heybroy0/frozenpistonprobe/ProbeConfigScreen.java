package dev.heybroy0.frozenpistonprobe;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Minimal, intentionally crude UI for live testing of per-block policies. */
final class ProbeConfigScreen extends Screen {
    private final Screen parent;
    private EditBox blockId;
    private Button movementButton;
    private Button tickButton;
    private ProbeConfig.MovementMode movementMode = ProbeConfig.MovementMode.VANILLA;
    private ProbeConfig.TickMode tickMode = ProbeConfig.TickMode.TRANSFER;
    private String status = "Enter a block ID; press Tab to autocomplete.";
    private int panelLeft;
    private int panelWidth;
    private List<String> completionCandidates = List.of();
    private int completionIndex = -1;
    private static String lastTypedBlockId = "";

    ProbeConfigScreen(Screen parent) {
        super(Component.literal("Frozen Piston Probe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(260, this.width - 20);
        this.panelLeft = (this.width - this.panelWidth) / 2;

        this.blockId = this.addRenderableWidget(new EditBox(
                this.font, this.panelLeft, 41, this.panelWidth, 20, Component.literal("Block ID")));
        this.blockId.setMaxLength(256);
        this.blockId.setValue(lastTypedBlockId);
        this.blockId.setResponder(value -> {
            lastTypedBlockId = value;
            this.completionCandidates = findBlockSuggestions(value);
            this.completionIndex = -1;
        });

        this.addRenderableWidget(Button.builder(Component.literal("Load settings"), button -> loadBlock())
                .bounds(this.panelLeft, 73, this.panelWidth, 18).build());

        this.movementButton = this.addRenderableWidget(Button.builder(Component.literal("Movement: vanilla"), button -> {
            this.movementMode = this.movementMode.next();
            updateButtonLabels();
        }).bounds(this.panelLeft, 95, this.panelWidth, 18).build());

        this.tickButton = this.addRenderableWidget(Button.builder(Component.literal("Ticks: transfer"), button -> {
            this.tickMode = this.tickMode.next();
            updateButtonLabels();
        }).bounds(this.panelLeft, 117, this.panelWidth, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Apply + Save"), button -> applyPolicies())
                .bounds(this.panelLeft, 139, this.panelWidth, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Open config file"), button -> openConfigFile())
                .bounds(this.panelLeft, 161, this.panelWidth, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset defaults"), button -> resetDefaults())
                .bounds(this.panelLeft, 183, this.panelWidth, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(this.panelLeft, 205, this.panelWidth, 18).build());

        loadBlock();
        this.setInitialFocus(this.blockId);
    }

    private void loadBlock() {
        String rawId = this.blockId.getValue().trim();
        if (rawId.isEmpty()) {
            this.status = "Type a block ID, then load its settings.";
            return;
        }
        if (!ProbeConfig.isValidBlockId(rawId)) {
            this.status = "Unknown block ID: " + rawId;
            return;
        }
        this.movementMode = ProbeConfig.movementModeForId(rawId);
        this.tickMode = ProbeConfig.tickModeForId(rawId);
        this.status = "Loaded settings for " + rawId;
        updateButtonLabels();
    }

    private void applyPolicies() {
        String rawId = this.blockId.getValue().trim();
        if (!ProbeConfig.isValidBlockId(rawId)) {
            this.status = "Unknown block ID: " + rawId;
            return;
        }
        boolean saved = ProbeConfig.setPolicies(rawId, this.movementMode, this.tickMode);
        this.status = saved
                ? "Applied and saved: " + rawId
                : "Applied in memory; could not save file.";
    }

    private void openConfigFile() {
        Path configPath = ProbeConfig.configPath().toAbsolutePath().normalize();
        Path configDirectory = configPath.getParent();
        try {
            if (configDirectory == null) {
                throw new IOException("The config file has no parent directory: " + configPath);
            }

            Files.createDirectories(configDirectory);
            if (!Files.isRegularFile(configPath) && !ProbeConfig.resetToDefaults()) {
                throw new IOException("Could not create the config file: " + configPath);
            }
            if (!Files.isRegularFile(configPath)) {
                throw new IOException("Config file was not created: " + configPath);
            }

            openWithDefaultApplication(configPath);
            this.status = "Requested editor to open: " + configPath;
            FrozenPistonProbe.LOGGER.info("Requested default application to open config file: {}", configPath);
        } catch (IOException | RuntimeException exception) {
            FrozenPistonProbe.LOGGER.warn("Could not open config file {}", configPath, exception);
            this.status = "Could not open config file; see latest.log. Path: " + configPath;
        }
    }

    /** Opens a file with the operating system's default application. */
    private static void openWithDefaultApplication(Path configPath) throws IOException {
        // Prefer Java's Desktop API when the runtime exposes it.
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(configPath.toFile());
                return;
            }
        } catch (IOException | RuntimeException desktopFailure) {
            // Continue with platform-specific launchers below; these often work in game runtimes
            // where AWT Desktop is unavailable or throws due to the launcher environment.
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        IOException lastFailure = null;
        if (osName.startsWith("windows")) {
            // rundll32 delegates the file URI to the Windows shell's registered file handler.
            try {
                startDefaultApplication(List.of("rundll32.exe", "url.dll,FileProtocolHandler", configPath.toUri().toString()));
                return;
            } catch (IOException exception) {
                lastFailure = exception;
            }
        } else if (osName.startsWith("mac") || osName.contains("darwin")) {
            try {
                startDefaultApplication(List.of("open", configPath.toString()));
                return;
            } catch (IOException exception) {
                lastFailure = exception;
            }
        } else {
            for (List<String> command : List.of(
                    List.of("xdg-open", configPath.toString()),
                    List.of("gio", "open", configPath.toString()))) {
                try {
                    startDefaultApplication(command);
                    return;
                } catch (IOException exception) {
                    lastFailure = exception;
                }
            }
        }

        if (lastFailure != null) throw lastFailure;
        throw new IOException("No supported default-application launcher was found for " + osName);
    }

    private static void startDefaultApplication(List<String> command) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException exception) {
            throw new IOException("Could not start default-application command: " + command.get(0), exception);
        }

        try {
            // These launchers normally hand off to the desktop application and then exit.
            if (process.waitFor(1200, TimeUnit.MILLISECONDS) && process.exitValue() != 0) {
                throw new IOException("Default-application command exited with code "
                        + process.exitValue() + ": " + command.get(0));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while launching default application: " + command.get(0), exception);
        }
    }

    private void resetDefaults() {
        boolean saved = ProbeConfig.resetToDefaults();
        this.movementMode = ProbeConfig.MovementMode.VANILLA;
        this.tickMode = ProbeConfig.TickMode.TRANSFER;
        if (this.blockId != null && ProbeConfig.isValidBlockId(this.blockId.getValue().trim())) {
            loadBlock();
        } else {
            updateButtonLabels();
        }
        this.status = saved ? "Reset defaults and saved config." : "Reset defaults in memory; save failed.";
    }

    private List<String> findBlockSuggestions(String value) {
        String typed = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (typed.isEmpty() || ProbeConfig.isValidBlockId(typed)) {
            return List.of();
        }

        return BuiltInRegistries.BLOCK.keySet().stream()
                .map(Identifier::toString)
                .filter(id -> id.startsWith(typed)
                        || (!typed.contains(":") && id.substring(id.indexOf(':') + 1).startsWith(typed)))
                .sorted()
                .limit(5)
                .toList();
    }

    private List<String> blockSuggestions() {
        String current = this.blockId == null ? "" : this.blockId.getValue().trim().toLowerCase(Locale.ROOT);
        if (!this.completionCandidates.isEmpty() && this.completionCandidates.contains(current)) {
            return this.completionCandidates;
        }
        return findBlockSuggestions(current);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.blockId != null && this.blockId.isFocused()
                && (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN
                || event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER)) {
            if (this.completionCandidates.isEmpty()) {
                this.completionCandidates = findBlockSuggestions(this.blockId.getValue());
            }
            if (!this.completionCandidates.isEmpty()) {
                if (event.key() == InputConstants.KEY_RETURN
                        || event.key() == InputConstants.KEY_NUMPADENTER) {
                    int selected = this.completionIndex < 0 ? 0 : this.completionIndex;
                    applySuggestion(this.completionCandidates.get(selected));
                } else {
                    int step = event.key() == InputConstants.KEY_DOWN ? 1 : -1;
                    int start = this.completionIndex < 0 ? (step > 0 ? -1 : 0) : this.completionIndex;
                    this.completionIndex = Math.floorMod(start + step, this.completionCandidates.size());
                }
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_TAB && this.blockId != null && this.blockId.isFocused()) {
            String current = this.blockId.getValue().trim().toLowerCase(Locale.ROOT);
            if (this.completionCandidates.isEmpty() || !this.completionCandidates.contains(current)) {
                this.completionCandidates = findBlockSuggestions(current);
                this.completionIndex = -1;
            }
            if (!this.completionCandidates.isEmpty()) {
                this.completionIndex = (this.completionIndex + 1) % this.completionCandidates.size();
                int selectedIndex = this.completionIndex;
                int candidateCount = this.completionCandidates.size();
                String suggestion = this.completionCandidates.get(selectedIndex);
                applySuggestion(suggestion);
                this.status = "Autocomplete " + (selectedIndex + 1) + "/"
                        + candidateCount + ": " + suggestion;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void applySuggestion(String suggestion) {
        this.blockId.setValue(suggestion);
        this.blockId.setCursorPosition(suggestion.length());
        this.completionCandidates = findBlockSuggestions(suggestion);
        this.completionIndex = -1;
        this.status = "Selected " + suggestion;
    }

    private void updateButtonLabels() {
        if (this.movementButton != null) {
            this.movementButton.setMessage(Component.literal("Movement: "
                    + this.movementMode.name().toLowerCase(Locale.ROOT)));
        }
        if (this.tickButton != null) {
            this.tickButton.setMessage(Component.literal("Ticks: "
                    + this.tickMode.name().toLowerCase(Locale.ROOT)));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 13, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("Block registry ID"), this.width / 2, 28, 0xA0A0A0);

        List<String> suggestions = blockSuggestions();
        if (this.blockId != null && this.blockId.isFocused() && !suggestions.isEmpty()) {
            int rowHeight = 14;
            int dropdownTop = 62;
            int rows = Math.min(5, suggestions.size());
            int dropdownBottom = dropdownTop + rows * rowHeight + 2;
            graphics.fill(this.panelLeft, dropdownTop, this.panelLeft + this.panelWidth, dropdownBottom, 0xF0101010);
            graphics.fill(this.panelLeft, dropdownTop, this.panelLeft + this.panelWidth, dropdownTop + 1, 0xFF808080);
            for (int i = 0; i < rows; i++) {
                int rowTop = dropdownTop + 2 + i * rowHeight;
                if (i == this.completionIndex) {
                    graphics.fill(this.panelLeft + 1, rowTop, this.panelLeft + this.panelWidth - 1,
                            rowTop + rowHeight, 0xFF305080);
                }
                graphics.centeredText(this.font, Component.literal(suggestions.get(i)),
                        this.width / 2, rowTop + 3, i == this.completionIndex ? 0xFFFFFFFF : 0xFFD0D0D0);
            }
        } else {
            graphics.centeredText(this.font, Component.literal("Type a block ID; Tab or arrows show suggestions"),
                    this.width / 2, 63, 0x808080);
        }

        graphics.centeredText(this.font, Component.literal(this.status), this.width / 2, 232, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("Load reads settings; Reset restores bundled defaults."),
                this.width / 2, 247, 0xA0A0A0);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
