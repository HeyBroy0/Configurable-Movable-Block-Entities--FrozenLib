package dev.heybroy0.frozenpistonprobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimal, intentionally crude UI for live testing of per-block policies. */
final class ProbeConfigScreen extends Screen {
    private final Screen parent;
    private EditBox blockId;
    private Button movementButton;
    private Button tickButton;
    private ProbeConfig.MovementMode movementMode = ProbeConfig.MovementMode.VANILLA;
    private ProbeConfig.TickMode tickMode = ProbeConfig.TickMode.TRANSFER;
    private String status = "Enter a registry ID, then Load block.";

    ProbeConfigScreen(Screen parent) {
        super(Component.literal("Frozen Piston Probe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = Math.min(240, this.width - 20);
        int left = (this.width - width) / 2;
        this.blockId = this.addRenderableWidget(new EditBox(
                this.font, left, 44, width, 20, Component.literal("Block ID")));
        this.blockId.setMaxLength(256);
        this.blockId.setValue("minecraft:observer");

        this.addRenderableWidget(Button.builder(Component.literal("Load block"), button -> loadBlock())
                .bounds(left, 68, width, 20).build());

        this.movementButton = this.addRenderableWidget(Button.builder(Component.literal("Movement: vanilla"), button -> {
            this.movementMode = this.movementMode.next();
            updateButtonLabels();
        }).bounds(left, 94, width, 20).build());

        this.tickButton = this.addRenderableWidget(Button.builder(Component.literal("Ticks: transfer"), button -> {
            this.tickMode = this.tickMode.next();
            updateButtonLabels();
        }).bounds(left, 120, width, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Apply + Save"), button -> applyPolicies())
                .bounds(left, 148, width, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left, 174, width, 20).build());

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
        this.status = "Loaded " + rawId;
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

    private void updateButtonLabels() {
        if (this.movementButton != null) {
            this.movementButton.setMessage(Component.literal("Movement: "
                    + this.movementMode.name().toLowerCase(java.util.Locale.ROOT)));
        }
        if (this.tickButton != null) {
            this.tickButton.setMessage(Component.literal("Ticks: "
                    + this.tickMode.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("Block registry ID"), this.width / 2, 33, 0xA0A0A0);
        graphics.centeredText(this.font, Component.literal(this.status), this.width / 2, 204, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("Movement overrides piston reaction; test carefully."),
                this.width / 2, 218, 0xA0A0A0);
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
