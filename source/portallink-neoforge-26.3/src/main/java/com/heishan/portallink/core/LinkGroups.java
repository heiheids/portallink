package com.heishan.portallink.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Groups portals into link pairs so that everything belonging to one link shares one colour.
 *
 * <p>The rule this implements: <i>a nether gate and the overworld gate it really connects to are
 * one unit — one colour, one range, one label.</i> A different gate that happens to sit inside that
 * square but links elsewhere is a <b>different</b> unit, so it gets its own colour and its own range
 * instead of being merged into the first one.</p>
 *
 * <h2>Only mutual links form a group</h2>
 * A pair is created only when <b>both</b> portals pick each other. That matters in the contested
 * case: nether gate G, overworld gate A and overworld gate B where B is farther away. Vanilla sends
 * A to G and G back to A, so the real pair is (A, G); B merely points at G and will strand the
 * player. If B's one-way look-up were allowed to claim G, B would steal A's colour and the pair
 * would be painted in two colours. So a one-way link leaves the offending portal in its own
 * singleton group, and the genuine pair keeps its identity.
 *
 * <p>Group ids are the two portal keys in sorted order, which keeps them stable across rescans and
 * restarts: the same pair always receives the same colour.</p>
 */
public final class LinkGroups {

    /** Separator unlikely to appear in a portal key, so ids cannot collide. */
    private static final String JOIN = "\u0001";

    private static final String SOLO_PREFIX = "solo:";

    private final Map<String, String> groupByPortal;

    private LinkGroups(Map<String, String> groupByPortal) {
        this.groupByPortal = groupByPortal;
    }

    public static LinkGroups empty() {
        return new LinkGroups(new HashMap<>());
    }

    /** Build the grouping from every link the snapshot knows about. */
    public static LinkGroups of(PortalSnapshot snapshot) {
        if (snapshot == null) {
            return empty();
        }

        // Index every portal by key so a portal's partner entry can be inspected.
        Map<String, PortalEntry> byKey = new HashMap<>();
        for (PortalEntry entry : snapshot.allEntries()) {
            byKey.put(entry.key(), entry);
        }

        Map<String, String> groups = new HashMap<>();

        // 1. Record every mutual pair first: a genuine pairing always wins the colour.
        for (PortalEntry entry : snapshot.allEntries()) {
            if (!entry.hasMatch() || !isMutual(entry, byKey)) {
                continue;
            }
            PortalEntry partner = byKey.get(entry.matched().key());
            String id = pairKey(entry.key(), partner.key());
            groups.put(entry.key(), id);
            groups.put(partner.key(), id);
        }

        // 2. Anything left unpaired keeps its own colour. A one-way look-up lands here, which is
        //    exactly right: it must not steal the colour of the pair it is pointing at.
        for (PortalEntry entry : snapshot.allEntries()) {
            groups.putIfAbsent(entry.key(), solo(entry.key()));
        }
        return new LinkGroups(groups);
    }

    /** {@code true} when the partner's own look-up points straight back at {@code entry}. */
    private static boolean isMutual(PortalEntry entry, Map<String, PortalEntry> byKey) {
        if (!entry.hasMatch()) {
            return false;
        }
        PortalEntry partner = byKey.get(entry.matched().key());
        return partner != null
                && partner.hasMatch()
                && partner.matched().key().equals(entry.key());
    }

    /**
     * Stable id of the link a portal belongs to.
     *
     * <p>An unpaired portal is its own group: vanilla would create a new partner for it, or its link
     * is one-way, so nothing else may share its colour.</p>
     */
    public String groupOf(String portalKey) {
        String group = groupByPortal.get(portalKey);
        return group == null ? solo(portalKey) : group;
    }

    /** {@code true} when the two portals are the two halves of one mutual link. */
    public boolean linkedTogether(String keyA, String keyB) {
        String groupA = groupByPortal.get(keyA);
        String groupB = groupByPortal.get(keyB);
        return groupA != null && groupA.equals(groupB);
    }

    /** Cannonical, order independent id for a portal pair. */
    public static String pairKey(String keyA, String keyB) {
        return keyA.compareTo(keyB) <= 0 ? keyA + JOIN + keyB : keyB + JOIN + keyA;
    }

    private static String solo(String portalKey) {
        return SOLO_PREFIX + portalKey;
    }
}
