package com.heishan.portallink.client;


import com.heishan.portallink.PortalLink;
import com.heishan.portallink.client.command.PortalLinkCommand;
import com.heishan.portallink.client.render.PortalLinkHud;
import com.heishan.portallink.client.render.PortalWorldRenderer;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.i18n.PortalI18n;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;

/**
 * Client glue: wires the key binding, the tick hook, the world renderer, the HUD and the command.
 *
 * <p>Registration lives in small methods so each piece can be reasoned about, replaced or disabled
 * independently, and no method here does more than wire one feature. Everything is client only, so
 * the whole class is gated on {@link Dist#CLIENT}.</p>
 */
@EventBusSubscriber(modid = PortalLink.MOD_ID, value = Dist.CLIENT)
public final class PortalLinkClient {

    /** Category shown in the vanilla controls screen; the label key is derived from this id. */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(PortalLink.MOD_ID, "main"));

    /**
     * One key for everything the mod draws.
     *
     * <p>The panel toggle and the settings screen used to have their own keys, which made "turn the
     * mod off" mean remembering which of three keys to press. Everything visual now hangs off this
     * single master switch; the settings screen is reachable from the mod list or
     * {@code /portallink config}.</p>
     */
    private static KeyMapping toggleKey;

    private PortalLinkClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // The category must be registered with the same event as the mapping, otherwise the vanilla
        // controls screen has no label for it and prints the raw key.
        event.registerCategory(CATEGORY);
        toggleKey = new KeyMapping("key.portallink.toggle", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_P, CATEGORY);
        event.register(toggleKey);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(PortalLink.MOD_ID, "status_panel"),
                new PortalLinkHud());
    }

    @SubscribeEvent
    public static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(new PortalWorldRenderer());
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PortalLinkCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        PortalI18n.tick();
        ClientPortalLinkState.tick();
        ConflictNotifier.tick();
        handleKeys(client);
    }

    @SubscribeEvent
    public static void onClientStopping(ClientStoppingEvent event) {
        PortalLinkConfig.get().save();
        ConflictNotifier.reset();
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
}
