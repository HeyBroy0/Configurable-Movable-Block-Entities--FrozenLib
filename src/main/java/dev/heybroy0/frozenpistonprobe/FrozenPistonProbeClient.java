package dev.heybroy0.frozenpistonprobe;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Client-only shortcut for opening the live policy test screen. */
public final class FrozenPistonProbeClient implements ClientModInitializer {
    private static KeyMapping openConfig;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(FrozenPistonProbe.MOD_ID, "config"));
        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.frozen_piston_probe.open_config",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_P,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfig.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.gui.setScreen(new ProbeConfigScreen(null));
                }
            }
        });
    }
}
