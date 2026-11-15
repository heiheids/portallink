package com.heishan.portallink.gametest;

import java.util.List;
import java.util.Set;

import com.heishan.portallink.core.LinkGroups;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.core.PortalSnapshot;
import com.heishan.portallink.core.RangeMarker;
import com.heishan.portallink.core.RangePlanner;

/**
 * Pure assertions about the link grouping and claim squares, with no Minecraft client involved.
 *
 * <p>These run on the test thread directly: they only touch records and plain math, so they need no
 * {@code computeOnClient} hop. Keeping them separate from the client tests means a grouping
 * regression fails in milliseconds instead of costing a full client boot.</p>
 */
final class PortalGroupAssertions {

    private PortalGroupAssertions() {
    }

    /**
     * Two portals that link to the <b>same</b> partner are one group, and a portal linked elsewhere
     * is a different group.
     *
     * <p>This is the exact property the colour scheme depends on: same group == same colour, so a
     * different group == visibly different colour.</p>
     */
    static void assertGroupsSeparateCompetingLinks(PortalSnapshot snapshot) {
        List<PortalEntry> entries = snapshot.entries();
        PortalFixtures.require(entries.size() >= 2,
                "need at least two portals here, got " + PortalFixtures.describe(snapshot));

        LinkGroups groups = LinkGroups.of(snapshot);
        for (PortalEntry entry : entries) {
            if (!entry.hasMatch()) {
                continue;
            }
            if (!mutual(entry, snapshot)) {
                // A one-way look-up: vanilla sends this portal to the gate, but the gate leads
                // elsewhere. It must not share the gate's colour, otherwise the genuine pair would
                // be painted in two colours and look like a conflict.
                PortalFixtures.require(
                        !groups.linkedTogether(entry.key(), entry.matched().key()),
                        "a one-way portal must not share its target's group: "
                                + entry.key() + " -> " + entry.matched().key());
                continue;
            }
            // A real pairing must share one group, otherwise the two halves get two colours.
            PortalFixtures.require(groups.linkedTogether(entry.key(), entry.matched().key()),
                    "a mutual pair must share a group: "
                            + entry.key() + " vs " + entry.matched().key());
        }
    }

    /**
     * Ranges must be a property of the gate, not of the player.
     *
     * <p>This is the regression guard for the "range slides while walking" bug: the planner output
     * is compared between two scans taken with the player in different places. Any dependency on
     * the player position, however indirect, changes the centre or the size and fails here.</p>
     *
     * @param before ranges planned from a snapshot taken at the first position
     * @param after  ranges planned from a snapshot taken after moving the player
     */
    static void assertRangesStable(List<RangeMarker> before, List<RangeMarker> after) {
        PortalFixtures.require(!before.isEmpty() && !after.isEmpty(),
                "both scans must produce ranges, got " + before.size() + " and " + after.size());

        java.util.Map<String, RangeMarker> byGroup = new java.util.HashMap<>();
        for (RangeMarker marker : after) {
            byGroup.put(marker.groupKey(), marker);
        }

        int compared = 0;
        for (RangeMarker a : before) {
            RangeMarker b = byGroup.get(a.groupKey());
            if (b == null) {
                // The gate may simply have left the (player centred) scan window; that is a
                // coverage change, not the range moving.
                continue;
            }
            compared++;
            PortalFixtures.require(a.center().distanceTo(b.center()) < 1.0E-6D,
                    "range centre moved with the player for group " + a.groupKey()
                            + ": " + a.center() + " -> " + b.center());
            PortalFixtures.require(a.half() == b.half(),
                    "range size changed with the player for group " + a.groupKey()
                            + ": " + a.half() + " -> " + b.half());
            PortalFixtures.require(a.minBlockX() == b.minBlockX()
                            && a.maxBlockX() == b.maxBlockX()
                            && a.minBlockZ() == b.minBlockZ()
                            && a.maxBlockZ() == b.maxBlockZ(),
                    "range bounds moved with the player for group " + a.groupKey());
        }
        PortalFixtures.require(compared > 0,
                "no range was present in both scans, so stability could not be verified");
    }

    /**
     * Every column must be the full mapped size, whether or not the gate has a partner.
     *
     * <p>The two branches compute the half edge from differently directed scales: the linked branch
     * multiplies by {@code scaleBetween(other, here)}, the unlinked branch divides by
     * {@code scaleBetween(here, other)}. Both must land on the same 128 blocks in the overworld.
     * Mixing the forms once produced a 5 block column (and then, after an over-eager "fix", a 2 block
     * column), so the size is asserted for all columns here, not only the linked ones.</p>
     */
    static void assertColumnsHaveMappedSize(PortalSnapshot snapshot, int minY, int maxY) {
        LinkGroups groups = LinkGroups.of(snapshot);
        List<RangeMarker> markers = RangePlanner.plan(snapshot, groups, minY, maxY);
        PortalFixtures.require(!markers.isEmpty(), "expected claim columns, got none");
        for (RangeMarker marker : markers) {
            PortalFixtures.require(marker.half() >= 120.0D && marker.half() <= 136.0D,
                    "every overworld column must reach 128 blocks, got half=" + marker.half()
                            + " (width " + marker.blockWidth() + " blocks) for owner "
                            + marker.ownerPos().toShortString()
                            + " existing=" + marker.existing());
        }
    }

    /**
     * Pin the column size for the overworld side.
     *
     * <p>Standing in the overworld, the columns belong to nether gates. Vanilla searches 16 blocks
     * around the arrival point when the destination is the nether, and the ratio is 8:1, so the base
     * half edge is {@code 16 * 8 = 128} blocks. On top of that sits the contact margin: teleporting is
     * triggered by touching a portal block, and the portal plane is wider than its centre point, so the
     * reachable area extends by {@code width/2 + 0.3} blocks (not scaled - the player's box does not
     * shrink between dimensions). The assertion therefore pins the base value plus that margin rather
     * than a bare 128, otherwise a correct implementation would fail.</p>
     */
    static void assertOverworldColumnsAre128(PortalSnapshot snapshot, int minY, int maxY) {
        LinkGroups groups = LinkGroups.of(snapshot);
        List<RangeMarker> markers = RangePlanner.plan(snapshot, groups, minY, maxY);
        PortalFixtures.require(!markers.isEmpty(), "expected claim columns, got none");

        int checked = 0;
        for (RangeMarker marker : markers) {
            if (marker.ownerPos() == null) {
                continue;
            }
            // Only gates of the opposite (nether) dimension own columns here.
            if (!marker.ownerIsNether()) {
                continue;
            }
            checked++;
            // 128 from the radius, at most width/2 + 0.3 from the contact rule. The width is unknown
            // here, so allow the full vanilla maximum instead of trusting the owning gate's size.
            double minimum = 128.0D;
            double maximum = 128.0D + PortalRef.MAX_SIZE * 0.5D + 0.3D;
            PortalFixtures.require(marker.half() >= minimum && marker.half() <= maximum,
                    "a nether gate's overworld column must have a half edge between " + minimum
                            + " and " + maximum + ", was " + marker.half()
                            + " (width " + marker.blockWidth() + " blocks)");
            PortalFixtures.require(marker.blockWidth() == marker.blockDepth(),
                    "the column must stay square, was "
                            + marker.blockWidth() + "x" + marker.blockDepth());
        }
        PortalFixtures.require(checked > 0,
                "no nether-owned column was found, so the size could not be verified: "
                        + PortalFixtures.describe(snapshot));
    }

    /**
     * No portal frame may be a truncated fragment.
     *
     * <p>Frame extents are derived from the scanned block set, so a portal that straddles the query
     * boundary comes back shorter, with a shifted centre. Since the query window follows the player,
     * a truncated frame makes the derived claim column slide while walking — the bug this guards
     * against. A real nether portal is at least 2x3, so anything smaller is a fragment.</p>
     */
    static void assertNoTruncatedFrames(PortalSnapshot snapshot) {
        int checked = 0;
        for (PortalEntry entry : snapshot.allEntries()) {
            PortalRef ref = entry.portal().ref();
            checked++;
            PortalFixtures.require(ref.width() >= 2 && ref.height() >= 3,
                    "portal frame looks truncated (a vanilla portal is at least 2x3): "
                            + ref.key() + " is " + ref.width() + "x" + ref.height());
        }
        PortalFixtures.require(checked > 0, "no portal was scanned, so completeness could not be checked");
    }

    /** {@code true} when the partner's own look-up points straight back at this portal. */
    private static boolean mutual(PortalEntry entry, PortalSnapshot snapshot) {
        if (!entry.hasMatch()) {
            return false;
        }
        for (PortalEntry other : snapshot.allEntries()) {
            if (other.key().equals(entry.matched().key())) {
                return other.hasMatch() && other.matched().key().equals(entry.key());
            }
        }
        return false;
    }

    /**
     * The planner must emit one square per group, and every square must be a sane size.
     *
     * <p>A square of the wrong scale is the easiest bug to ship here (8x too big or too small), and
     * it is invisible in a screenshot unless you know the expected number.</p>
     */
    static void assertRangesPlanned(PortalSnapshot snapshot, int minY, int maxY) {
        LinkGroups groups = LinkGroups.of(snapshot);
        List<RangeMarker> markers = RangePlanner.plan(snapshot, groups, minY, maxY);
        PortalFixtures.require(!markers.isEmpty(),
                "expected at least one claim column, got none: "
                        + PortalFixtures.describe(snapshot));

        Set<String> bandKeys = new java.util.HashSet<>();
        for (RangeMarker marker : markers) {
            // Bands must live inside the build height, otherwise the engine culls them or they
            // poke into a height range the other dimension does not have.
            PortalFixtures.require(marker.bottomY() >= minY && marker.topY() <= maxY,
                    "column band outside build height [" + minY + "," + maxY + "]: "
                            + marker.bottomY() + ".." + marker.topY());
            PortalFixtures.require(marker.topY() > marker.bottomY(),
                    "column band must have positive height: "
                            + marker.bottomY() + ".." + marker.topY());
            PortalFixtures.require(marker.half() > 0.5D && marker.half() <= RangeMarker.MAX_HALF,
                    "column half edge out of range: " + marker.half());
            PortalFixtures.require(marker.blockWidth() >= 1 && marker.blockDepth() >= 1,
                    "column must cover at least one block: "
                            + marker.blockWidth() + "x" + marker.blockDepth());
            PortalFixtures.require(marker.blockCount() == marker.blockWidth() * marker.blockDepth(),
                    "column block count disagrees with its bounds");

            // A band must contain the gate height it claims, so the label sits at a real altitude.
            PortalFixtures.require(
                    marker.contains((int) Math.floor(marker.center().x),
                            (int) Math.floor(marker.center().y),
                            (int) Math.floor(marker.center().z)),
                    "column band must contain its own label point");

            String bandKey = marker.groupKey() + "@" + marker.minBlockX() + ","
                    + marker.minBlockZ() + ":" + marker.bottomY() + "-" + marker.topY();
            PortalFixtures.require(bandKeys.add(bandKey),
                    "duplicate column band emitted: " + bandKey);
        }

        // Columns that share the same horizontal rectangle must not overlap vertically: that is the
        // whole point of splitting a shared column between gates at different heights.
        for (int i = 0; i < markers.size(); i++) {
            for (int j = i + 1; j < markers.size(); j++) {
                RangeMarker a = markers.get(i);
                RangeMarker b = markers.get(j);
                boolean sameColumn = a.minBlockX() == b.minBlockX()
                        && a.maxBlockX() == b.maxBlockX()
                        && a.minBlockZ() == b.minBlockZ()
                        && a.maxBlockZ() == b.maxBlockZ();
                if (!sameColumn) {
                    continue;
                }
                boolean overlap = a.bottomY() < b.topY() && b.bottomY() < a.topY();
                PortalFixtures.require(!overlap,
                        "two gates sharing one column must not overlap in height: "
                                + a.groupKey() + " " + a.bottomY() + ".." + a.topY()
                                + " vs " + b.groupKey() + " " + b.bottomY() + ".." + b.topY());
            }
        }
    }
}
