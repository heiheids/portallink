package com.heishan.portallink.client;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.client.command.PortalLinkCommand;
import com.heishan.portallink.client.gui.PortalLinkConfigScreen;
import com.heishan.portallink.client.render.PortalLinkHud;
import com.heishan.portallink.client.render.PortalLinkRenderer;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.i18n.PortalI18n;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Client entry point: wires the key bindings, the tick hook, the world renderer and the HUD.
 *
 * <p>Registration lives in small methods so each piece can be reasoned about, replaced or disabled
 * independently, and no class here does more than wire one feature.</p>
 */
public final class PortalLinkClient implements ClientModInitializer {

    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        registerKeyMappings();
        registerTick();
        registerWorldRenderer();
        registerHud();
        PortalLinkCommand.register();
        registerLifecycle();
        PortalLink.LOGGER.info("PortalLink client initialised");
    }

    private static void registerKeyMappings() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(PortalLink.MOD_ID, "main"));

        // One key for everything the mod draws, on P.
        //
        // The panel toggle and the settings screen used to have their own keys, which made "turn the
        // mod off" mean remembering which of three keys to press. Everything visual now hangs off this
        // single master switch; the settings screen is reachable from Mod Menu.
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.portallink.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_P,
                category));
    }

    private static void registerTick() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PortalI18n.tick();
            ClientPortalLinkState.tick();
            ConflictNotifier.tick();
            handleKeys(client);
        });
    }

    private static void handleKeys(Minecraft client) {
        if (client.player == null) {
            // The key is still drained so a press made in a menu does not fire later.
            drain(toggleKey);
            return;
        }
        if (drain(toggleKey)) {
            PortalLinkConfig config = PortalLinkConfig.get();
            config.enabled = !config.enabled;
            config.save();
            ClientPortalLinkState.markDirty();
        }
    }

    private static boolean drain(KeyMapping mapping) {
        return mapping != null && mapping.consumeClick();
    }

    private static void registerWorldRenderer() {
        // One hook for everything world space: the gizmo collector is still open here.
        LevelRenderEvents.BEFORE_GIZMOS.register(PortalLinkRenderer::render);
    }

    private static void registerHud() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(PortalLink.MOD_ID, "status_panel"),
                new PortalLinkHud());
    }

    private static void registerLifecycle() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            PortalLinkConfig.get().save();
            ConflictNotifier.reset();
        });
    }


}
