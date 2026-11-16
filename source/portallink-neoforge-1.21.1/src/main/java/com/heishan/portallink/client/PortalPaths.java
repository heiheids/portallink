package com.heishan.portallink.client;

import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Where this mod is allowed to write.
 *
 * <p>The only loader specific piece of the configuration layer: Fabric asks
 * {@code FabricLoader#getConfigDir()}, NeoForge exposes {@link FMLPaths#CONFIGDIR}. Keeping the
 * lookup in one place means the config and the portal numbering stay byte identical across
 * loaders.</p>
 */
public final class PortalPaths {

    private PortalPaths() {
    }

    /** The game's {@code config} directory. */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
