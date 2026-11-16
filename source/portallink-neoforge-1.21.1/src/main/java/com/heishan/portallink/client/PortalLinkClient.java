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
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Client glue for 1.21.1: key binding, tick hook, world renderer, HUD and command.
 *
 * <p>Differences from the newer generation that are forced by this API, not by choice:</p>
 * <ul>
 *   <li>The key category is a plain {@code String} ({@code key.categories.portallink}) instead of a
 *       {@code KeyMapping.Category} record.</li>
 *   <li>The HUD is a {@code LayeredDraw.Layer} drawing immediately into {@code GuiGraphics}.</li>
 *   <li>The world overlay is a game-bus {@link RenderLevelStageEvent} listener: there is no debug
 *       renderer registration event on this NeoForge version.</li>
 * </ul>
 */
@EventBusSubscriber(modid = PortalLink.MOD_ID, value = Dist.CLIENT)
public final class PortalLinkClient {

    /** Translation key of the category shown in the vanilla controls screen. */
    private static final String CATEGORY = "key.categories.portallink";

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
        // 1.21.1 still names the keyboard input type KEYSYM; it was renamed to KEYBOARD in 26.x.
        toggleKey = new KeyMapping("key.portallink.toggle", InputConstants.Type.KEYSYM,
                InputConstants.KEY_P, CATEGORY);
        event.register(toggleKey);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(PortalLink.MOD_ID, "status_panel"),
                new PortalLinkHud());
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PortalLinkCommand.register(event.getDispatcher());
    }

    /**
     * World overlay hook.
     *
     * <p>There is no debug-renderer registration on this NeoForge version, so the overlay listens to
     * the level render stage instead. The stage choice and the camera handling live in
     * {@code PortalWorldRenderer}.</p>
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        PortalWorldRenderer.onRenderLevelStage(event);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        PortalI18n.tick();
        ClientPortalLinkState.tick();
        ConflictNotifier.tick();
        handleKeys(client);
    }

    /** Reset the one-shot warning bookkeeping when the player leaves a world. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
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
