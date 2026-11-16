package com.heishan.portallink;

import com.heishan.portallink.config.PortalLinkConfig;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Mod entry point for NeoForge.
 *
 * <p>Everything this mod does is client side: it reads portal data (the integrated server's
 * point-of-interest index, or the loaded chunks on a remote server) and draws it. The only reason a
 * common entry point exists is so the mod is a well formed NeoForge mod, the configuration file is
 * created early, and the settings screen is offered in the mod list. The server half never
 * registers anything.</p>
 */
@Mod(PortalLink.MOD_ID)
public final class PortalLinkNeoForge {

    public PortalLinkNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        // Touch the config so a fresh install gets a readable file even before the first frame.
        PortalLinkConfig.get();
        if (FMLEnvironment.dist.isClient()) {
            // Client-only class, loaded reflectively by the JVM only on the client distribution.
            com.heishan.portallink.client.PortalLinkClientSetup.registerConfigScreen(modContainer);
        }
        PortalLink.LOGGER.info("{} {} (NeoForge) initialised", PortalLink.MOD_NAME,
                PortalLink.MOD_VERSION);
    }
}
