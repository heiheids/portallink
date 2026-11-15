package com.heishan.portallink.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.heishan.portallink.PortalLink;
import com.heishan.portallink.config.PortalLinkConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;

/**
 * Translation lookups with two layers:
 *
 * <ol>
 *   <li><b>auto</b> - defers to the vanilla {@link Language}, so the text follows whatever language
 *       the player picked in the options screen;</li>
 *   <li><b>forced</b> - reads our own bundled {@code assets/portallink/lang/*.json} directly, so the
 *       mod can be shown in another language than the game without restarting.</li>
 * </ol>
 */
public final class PortalI18n {

    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> BUNDLED = new HashMap<>();
    private static String loadedLanguage = "";
    private static String lastSelected = "";

    private PortalI18n() {
    }

    public static String tr(String key) {
        return tr(key, new Object[0]);
    }

    public static String tr(String key, Object... args) {
        String pattern = lookup(key);
        if (args.length == 0) {
            return pattern;
        }
        try {
            return String.format(pattern, args);
        } catch (RuntimeException exception) {
            return pattern;
        }
    }

    private static String lookup(String key) {
        String forced = PortalLinkConfig.get().language;
        if (!PortalLinkConfig.LANGUAGE_AUTO.equals(forced)) {
            String value = bundled(forced).get(key);
            if (value != null) {
                return value;
            }
        }
        return Language.getInstance().getOrDefault(key, key);
    }

    private static Map<String, String> bundled(String language) {
        if (language.equals(loadedLanguage) && BUNDLED.containsKey(language)) {
            return BUNDLED.get(language);
        }
        Map<String, String> table = new HashMap<>();
        Identifier id = Identifier.fromNamespaceAndPath(PortalLink.MOD_ID, "lang/" + language + ".json");
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            try {
                Optional<net.minecraft.server.packs.resources.Resource> resource =
                        client.getResourceManager().getResource(id);
                if (resource.isPresent()) {
                    try (BufferedReader reader = resource.get().openAsReader()) {
                        JsonObject root = GSON.fromJson(reader, JsonObject.class);
                        if (root != null) {
                            for (String name : root.keySet()) {
                                table.put(name, root.get(name).getAsString());
                            }
                        }
                    }
                }
            } catch (IOException | RuntimeException exception) {
                PortalLink.LOGGER.warn("could not read translations for {}", language, exception);
            }
        }
        BUNDLED.put(language, table);
        loadedLanguage = language;
        return table;
    }

    /** Drop the cache so a resource-pack reload is picked up on the next lookup. */
    public static void invalidate() {
        BUNDLED.clear();
        loadedLanguage = "";
        lastSelected = "";
    }

    /** Detect a language change in the options screen and refresh the forced table. */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getLanguageManager() == null) {
            return;
        }
        String selected = client.getLanguageManager().getSelected();
        if (!selected.equals(lastSelected)) {
            lastSelected = selected;
            invalidate();
        }
    }
}
