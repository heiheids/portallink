package com.heishan.portallink.client;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.core.PortalRef;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent portal numbers.
 *
 * <h2>Why numbers instead of coordinates</h2>
 * Reading "links to 48, 64, -87" and then finding that gate in the world means memorising coordinates.
 * A short {@code #3} is enough to tell which gate is which, and in particular to answer "which gate is
 * stealing this link" when two portals compete.
 *
 * <h2>Stability</h2>
 * Numbers are assigned on first sight and never reused or renumbered, keyed by
 * {@link PortalRef#key()} (dimension plus minimum corner). A gate therefore keeps its number across
 * rescans, dimension changes and restarts. Renumbering by sort order was rejected: adding one portal
 * would shift every later number, which defeats the purpose.
 *
 * <p>The map lives in {@code config/portallink-ids.json}. A client-side file is enough because the
 * numbers are only ever displayed; nothing about linking depends on them.</p>
 */
public final class PortalIdStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "portallink-ids.json";

    /** Numbers by portal key. */
    private final Map<String, Integer> ids = new HashMap<>();

    /** Next number to hand out. */
    private int next = 1;

    private static PortalIdStore instance;

    private PortalIdStore() {
    }

    /** The shared store, loaded on first use. */
    public static PortalIdStore get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Number for a portal, assigning one if this is the first time it is seen.
     *
     * @return a positive number, stable for the lifetime of the world
     */
    public int idOf(PortalRef portal) {
        return idOf(portal.key());
    }

    /** Number for a raw portal key; see {@link PortalRef#key()}. */
    public int idOf(String key) {
        Integer existing = ids.get(key);
        if (existing != null) {
            return existing;
        }
        int assigned = next++;
        ids.put(key, assigned);
        save();
        return assigned;
    }

    /**
     * Number for a portal described only by its anchor, used where only a {@code RangeMarker} is at
     * hand.
     *
     * <p>Rebuilds the same key shape {@link PortalRef#key()} uses, so a gate keeps one number whether
     * it is reached through a full portal reference or through the claim area it owns.</p>
     */
    public int idOf(BlockPos anchor, boolean nether) {
        String dimension = nether ? "the_nether" : "overworld";
        return idOf(dimension + "@" + anchor.getX() + "," + anchor.getY() + "," + anchor.getZ());
    }

    /** How many portals have been numbered so far. */
    public int count() {
        return ids.size();
    }

    private static Path file() {
        return PortalPaths.configDir().resolve(FILE_NAME);
    }

    private static PortalIdStore load() {
        Path path = file();
        if (!Files.exists(path)) {
            return new PortalIdStore();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            PortalIdStore loaded = GSON.fromJson(reader, PortalIdStore.class);
            if (loaded == null) {
                return new PortalIdStore();
            }
            // A corrupt or hand-edited file must not hand out duplicates.
            loaded.next = Math.max(1, loaded.next);
            for (int value : loaded.ids.values()) {
                loaded.next = Math.max(loaded.next, value + 1);
            }
            return loaded;
        } catch (IOException | JsonSyntaxException exception) {
            PortalLink.LOGGER.warn("could not read {}, starting a fresh portal numbering", path,
                    exception);
            return new PortalIdStore();
        }
    }

    private void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            PortalLink.LOGGER.warn("could not write {}", path, exception);
        }
    }
}
