package com.heishan.portallink;

import net.fabricmc.api.ModInitializer;

public class PortalLinkInit implements ModInitializer {

    @Override
    public void onInitialize() {
        PortalLink.LOGGER.info("[{}] common init", PortalLink.MOD_NAME);
    }
}
