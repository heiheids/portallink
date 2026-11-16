package com.heishan.portallink.client;

import com.heishan.portallink.client.gui.PortalLinkConfigScreen;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Adds the settings screen to NeoForge's mod list ("Config" button).
 *
 * <p>Kept in a separate client-only class: {@link IConfigScreenFactory} and every screen class are
 * client classes, and touching them from the common mod constructor would break a dedicated server
 * that happens to load this jar.</p>
 */
public final class PortalLinkClientSetup {

    private PortalLinkClientSetup() {
    }

    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, parent) -> new PortalLinkConfigScreen(parent));
    }
}
