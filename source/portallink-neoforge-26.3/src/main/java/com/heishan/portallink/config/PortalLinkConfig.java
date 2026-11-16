package com.heishan.portallink.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.heishan.portallink.PortalLink;
import com.heishan.portallink.client.PortalPaths;

/**
 * Persistent settings, stored as {@code config/portallink.json}.
 *
 * <p>Every field is public and serialised by name so that hand editing stays easy; a missing file
 * is created on first launch with the defaults below.</p>
 */
public final class PortalLinkConfig {

    /** {@code auto} follows the game language, anything else forces a bundled language. */
    public String language = LANGUAGE_AUTO;

    /** Master switch, toggled by the key binding. */
    public boolean enabled = true;

    /** Draw the destination rectangle each portal maps to. */
    public boolean renderRanges = true;

    /** Draw the floating labels above portals. */
    public boolean renderLabels = true;

    /**
     * Draw a line from a portal towards where its partner maps into this dimension.
     *
     * <p>Off by default: crossing dimensions the target is just empty air, so the line points at
     * nothing meaningful, and it was the last range-related element still drawn with gizmos.
     */
    public boolean renderConnectors = false;

    /** Draw a ghost outline for the partner portal, projected into the current dimension. */
    public boolean renderGhosts = false;

    /** Keep the markers visible through terrain. */
    public boolean alwaysOnTop = true;

    /** Only draw portals within this many blocks of the camera. */
    public int maxRenderDistance = 1536;

    /** Maximum number of portals drawn at once, keeps the frame cheap on big servers. */
    public int maxRenderedPortals = 32;

    /** Alpha (0-255) of the claim area floor fill. */
    public int zoneFillAlpha = 40;

    /** Alpha (0-255) of the claim area side walls. */
    public int zoneWallAlpha = 120;

    /** Alpha (0-255) of the claim area border lines. */
    public int zoneStrokeAlpha = 255;

    /** Alpha (0-255) of the corner and rim edges drawn as thin quads. */
    public int zoneEdgeAlpha = 255;

    /** Draw vertical walls, so the area reads as a volume instead of a flat decal. */
    public boolean showZoneWalls = true;

    /**
     * Draw the translucent wall and floor surfaces over the whole build height.
     *
     * <p><b>Off by default, deliberately.</b> The area's <i>wireframe</i> always spans the whole
     * column, because vanilla's search ignores Y. The <i>surfaces</i> are clipped to
     * {@link #zoneDownHeight}/{@link #zoneUpHeight} around the owning gate's own height. A full
     * height translucent cage was tried and reads wrong in game: in the nether the area is only
     * about 17 blocks wide, so a 256 block tall see-through box surrounds the player and every walk
     * makes it look like the box moves with them. Clipping the surfaces to the gate's Y anchors the
     * look to the world; the full extent is still shown as corner posts and rims.</p>
     *
     * <p>Set this to {@code true} to get the old full-extent surfaces back.</p>
     */
    public boolean fullHeightZones = false;

    /** Band height above the floor, only used when {@link #fullHeightZones} is off. */
    public int zoneUpHeight = 12;

    /** Band depth below the floor, only used when {@link #fullHeightZones} is off. */
    public int zoneDownHeight = 4;

    /** Show the compact status list in the corner of the screen. */
    public boolean showStatusPanel = true;

    /** Warn in chat when a portal's link is asymmetric ("串门" risk). */
    public boolean warnOnConflict = true;

    /** Scale of the floating label text. */
    public float labelScale = 1.0F;

    public static final String LANGUAGE_AUTO = "auto";
    public static final String LANGUAGE_ZH_CN = "zh_cn";
    public static final String LANGUAGE_EN_US = "en_us";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PortalLinkConfig instance;

    public static PortalLinkConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return PortalPaths.configDir().resolve(PortalLink.MOD_ID + ".json");
    }

    private static PortalLinkConfig load() {
        Path file = path();
        if (!Files.exists(file)) {
            PortalLinkConfig fresh = new PortalLinkConfig();
            fresh.save();
            return fresh;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PortalLinkConfig loaded = GSON.fromJson(reader, PortalLinkConfig.class);
            return loaded == null ? new PortalLinkConfig() : loaded.sanitise();
        } catch (IOException | RuntimeException exception) {
            PortalLink.LOGGER.warn("could not read {}, using defaults", file, exception);
            return new PortalLinkConfig();
        }
    }

    public void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            PortalLink.LOGGER.warn("could not write {}", file, exception);
        }
    }

    private PortalLinkConfig sanitise() {
        maxRenderDistance = clampInt(maxRenderDistance, 16, 8192, 1536);
        maxRenderedPortals = clampInt(maxRenderedPortals, 1, 512, 32);
        labelScale = clampFloat(labelScale, 0.25F, 4.0F, 1.0F);
        zoneFillAlpha = clampInt(zoneFillAlpha, 0, 255, 40);
        zoneWallAlpha = clampInt(zoneWallAlpha, 0, 255, 120);
        zoneStrokeAlpha = clampInt(zoneStrokeAlpha, 0, 255, 255);
        zoneEdgeAlpha = clampInt(zoneEdgeAlpha, 0, 255, 255);
        zoneUpHeight = clampInt(zoneUpHeight, 1, 64, 12);
        zoneDownHeight = clampInt(zoneDownHeight, 0, 64, 4);
        if (language == null || language.isBlank()) {
            language = LANGUAGE_AUTO;
        }
        return this;
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return value == 0 ? fallback : Math.max(min, Math.min(max, value));
        }
        return value;
    }

    private static float clampFloat(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || value <= 0.0F) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Replace the cached instance (used by the config screen after editing). */
    public static void replace(PortalLinkConfig replacement) {
        instance = replacement;
        replacement.save();
    }
}
