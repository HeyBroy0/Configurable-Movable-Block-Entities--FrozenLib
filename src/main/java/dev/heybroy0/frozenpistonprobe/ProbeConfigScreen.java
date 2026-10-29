package dev.heybroy0.frozenpistonprobe;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.awt.Desktop;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

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
        this.blockId.setValue("minecraft:observer");

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
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(this.panelLeft, 183, this.panelWidth, 18).build());

        loadBlock();
        this.setInitialFocus(this.blockId);
    }

    private void loadBlock() {
        String rawId = this.blockId.getValue().trim();
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
        var configPath = ProbeConfig.configPath();
        try {
            if (!Desktop.isDesktopSupported()) {
                this.status = "Desktop opening unavailable. Config: " + configPath;
                return;
            }
            Desktop.getDesktop().open(configPath.toFile());
            this.status = "Opened config file in your default editor.";
        } catch (IOException | RuntimeException exception) {
            FrozenPistonProbe.LOGGER.warn("Could not open config file {}", configPath, exception);
            this.status = "Could not open file: " + configPath;
        }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_TAB && this.blockId != null && this.blockId.isFocused()) {
            String current = this.blockId.getValue().trim().toLowerCase(Locale.ROOT);
            if (this.completionCandidates.isEmpty() || !this.completionCandidates.contains(current)) {
                this.completionCandidates = findBlockSuggestions(current);
                this.completionIndex = -1;
            }
            if (!this.completionCandidates.isEmpty()) {
                this.completionIndex = (this.completionIndex + 1) % this.completionCandidates.size();
                String suggestion = this.completionCandidates.get(this.completionIndex);
                this.blockId.setValue(suggestion);
                this.blockId.setCursorPosition(suggestion.length());
                this.status = "Autocomplete " + (this.completionIndex + 1) + "/"
                        + this.completionCandidates.size() + ": " + suggestion;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (!suggestions.isEmpty()) {
            String current = this.blockId.getValue().trim().toLowerCase(Locale.ROOT);
            boolean cycling = this.completionCandidates.contains(current) && this.completionIndex >= 0;
            String selectedSuggestion = cycling
                    ? this.completionCandidates.get(this.completionIndex)
                    : suggestions.get(0);
            String hint = "Tab: " + selectedSuggestion;
            if (suggestions.size() > 1) {
                int currentIndex = cycling ? this.completionIndex + 1 : 1;
                hint += " (" + currentIndex + "/" + suggestions.size() + "; Tab cycles)";
            }
            graphics.centeredText(this.font, Component.literal(hint), this.width / 2, 63, 0x80D8FF);
        } else {
            graphics.centeredText(this.font, Component.literal("Press Tab to complete a partial ID"),
                    this.width / 2, 63, 0x808080);
        }

        graphics.centeredText(this.font, Component.literal(this.status), this.width / 2, 207, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("Load settings reads values; Apply + Save writes them."),
                this.width / 2, 222, 0xA0A0A0);
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
