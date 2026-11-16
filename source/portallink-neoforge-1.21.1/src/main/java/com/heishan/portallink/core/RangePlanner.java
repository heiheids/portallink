package com.heishan.portallink.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.phys.Vec3;

/**
 * Turns a snapshot into the claim columns to draw in the player's dimension.
 *
 * <h2>What a column means</h2>
 * Vanilla picks a partner with {@code PoiManager#getInSquare}, which is a square in X/Z with
 * <b>no Y bound</b>: the volume a gate competes for is a full-height column. A column therefore
 * answers "if I build a portal anywhere in this vertical area, which gate will it link to?".
 *
 * <h2>Which portals own columns here</h2>
 * The <b>opposite</b> dimension's portals do. Standing in the overworld, the columns that matter
 * belong to nether gates, because those are what the player's overworld portals compete for.
 *
 * <h2>Splitting a shared column by height</h2>
 * The ranking comparator is {@code BlockPos#distSqr}, which is three dimensional, with Y only as a
 * tie breaker — so Y does affect the winner. When two gates occupy the same X/Z column at different
 * heights, a new portal belongs to whichever gate is nearer <b>in Y</b>, so the dividing line is the
 * midpoint of the two Y values. This planner cuts such a column into one band per gate, each band
 * carrying its own group id and therefore its own colour and label.
 *
 * <p>Without that split the renderer would paint one column for two competing gates, which is
 * exactly the ambiguity the mod exists to remove.</p>
 *
 * <h2>Nothing here depends on the player</h2>
 * The output is a pure function of the snapshot and the level's build height. The camera never
 * enters this class, which is what keeps ranges from sliding while walking.
 */
public final class RangePlanner {

    /** Keep bands strictly inside the build height. */
    private static final int HEIGHT_MARGIN = 1;

    private RangePlanner() {
    }

    /**
     * Plan the columns for the player's current dimension.
     *
     * @param snapshot snapshot oriented at the player's dimension
     * @param groups   link grouping used to colour the columns
     * @param minY     minimum build height of the current level
     * @param maxY     maximum build height of the current level
     * @return one marker per (column, owning gate) pair
     */
    public static List<RangeMarker> plan(PortalSnapshot snapshot, LinkGroups groups,
                                         int minY, int maxY) {
        if (snapshot == null || groups == null) {
            return List.of();
        }
        double bottom = minY + HEIGHT_MARGIN;
        double top = maxY - HEIGHT_MARGIN;
        if (top <= bottom) {
            return List.of();
        }

        // Candidate columns, keyed by their exact horizontal rectangle: only gates sharing a
        // rectangle have to fight over the same vertical space.
        Map<String, List<Draft>> byColumn = new LinkedHashMap<>();

        // 1. Gateways of the opposite dimension claim their column here.
        //
        // Only gates that are actually the partner of a portal of this dimension are drawn. Drawing
        // every gate the scan happens to find was the cause of "the range follows me": when the
        // player walked away from their portal the scan fell back to the player's own position, so it
        // kept finding *some* gate near them and painted a column around whatever they walked to.
        for (PortalEntry entry : snapshot.otherEntries()) {
            // Defence against a mixed snapshot: right after a dimension change the previous level's
            // scan can still be in flight, which put an overworld gate into "local" position and
            // painted a 257 block column at overworld coordinates inside the nether.
            if (entry.dimension().equals(snapshot.sourceDim())) {
                continue;
            }
            if (!entry.hasMatch()) {
                continue;
            }
            Draft draft = draftForOtherDimension(entry, groups);
            if (draft != null) {
                byColumn.computeIfAbsent(draft.columnKey(), key -> new ArrayList<>()).add(draft);
            }
        }

        // 2. A local portal with no partner gets a fallback column: it shows where vanilla would
        //    create the missing gate.
        //
        //    Only while the opposite dimension has no link data at all. As soon as a gate over there
        //    claims something, its own area already answers "where would a new portal go", and
        //    drawing this fallback as well paints a second box around the local gate - reported in
        //    game as "every portal renders two ranges: the nether one mapped here, and the
        //    overworld one around itself".
        boolean otherDimensionHasLinks = false;
        for (PortalEntry entry : snapshot.otherEntries()) {
            if (entry.hasMatch()) {
                otherDimensionHasLinks = true;
                break;
            }
        }
        if (!otherDimensionHasLinks) {
            for (PortalEntry entry : snapshot.entries()) {
                if (!entry.dimension().equals(snapshot.sourceDim())) {
                    continue;
                }
                if (!entry.hasMatch()) {
                    Draft draft = draftForUnlinkedHere(entry, groups);
                    if (draft != null) {
                        byColumn.computeIfAbsent(draft.columnKey(), key -> new ArrayList<>()).add(draft);
                    }
                }
            }
        }

        List<RangeMarker> out = new ArrayList<>();
        for (List<Draft> sharing : byColumn.values()) {
            out.addAll(cutIntoBands(sharing, bottom, top));
        }
        return onePerLinkGroup(out);
    }

    /**
     * Keep exactly one area per link group.
     *
     * <h2>Why this is the fix for "each portal renders two ranges"</h2>
     * A link group is one link: a nether gate and the overworld gate it really connects to
     * ({@link LinkGroups}). That link can only occupy one claim area per dimension, but two code
     * paths can describe it - the partner gate's own area, and the local fallback - and their
     * centres differ by only a few blocks, because {@code (A / 8) * 8 ≈ A}. With the sample world's
     * numbers the two boxes were {@code x=-34..225} and {@code x=-43..215}: nine blocks apart, same
     * size, same colour. Painted together they read as one portal wearing two slightly offset
     * ranges, which is the reported symptom.
     *
     * <p>Preference: the area owned by a <b>real</b> gate wins over the "a gate will be created
     * here" fallback, because the first one is the answer the mod exists to give.</p>
     */
    private static List<RangeMarker> onePerLinkGroup(List<RangeMarker> markers) {
        Map<String, RangeMarker> byGroup = new LinkedHashMap<>();
        for (RangeMarker marker : markers) {
            RangeMarker previous = byGroup.get(marker.groupKey());
            if (previous == null || (!previous.existing() && marker.existing())) {
                byGroup.put(marker.groupKey(), marker);
            }
        }
        return new ArrayList<>(byGroup.values());
    }

    /**
     * Split one shared column into vertical bands, one per competing gate.
     *
     * <p>Boundaries sit at the midpoint between neighbouring gate heights, because that is where
     * the 3D distance comparison flips. Each band keeps its own group, so overlapping claims stay
     * visually distinct.</p>
     */
    private static List<RangeMarker> cutIntoBands(List<Draft> drafts, double bottom, double top) {
        // One band per link group; a group can appear twice only if a snapshot is inconsistent.
        Map<String, Draft> byGroup = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            byGroup.putIfAbsent(draft.groupKey(), draft);
        }
        List<Draft> ordered = new ArrayList<>(byGroup.values());
        ordered.sort(Comparator.comparingDouble(draft -> draft.ownerY()));

        List<RangeMarker> out = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Draft draft = ordered.get(i);
            double bandBottom = bottom;
            double bandTop = top;
            if (i > 0) {
                bandBottom = (ordered.get(i - 1).ownerY() + draft.ownerY()) * 0.5D;
            }
            if (i < ordered.size() - 1) {
                bandTop = (draft.ownerY() + ordered.get(i + 1).ownerY()) * 0.5D;
            }
            double lo = Math.max(bottom, Math.min(bandBottom, top));
            double hi = Math.max(bottom, Math.min(bandTop, top));
            if (hi - lo < 1.0D) {
                // Degenerate band, e.g. two gates at the same height: keep one to avoid clutter.
                if (!out.isEmpty()) {
                    continue;
                }
                lo = bottom;
                hi = top;
            }
            double mid = (lo + hi) * 0.5D;
            out.add(new RangeMarker(new Vec3(draft.centerXZ().x, mid, draft.centerXZ().z),
                    draft.half(), lo, hi, draft.ownerPos(), draft.ownerIsNether(), draft.existing(),
                    draft.groupKey()));
        }
        return out;
    }

    /** A column candidate before the vertical split. */
    private record Draft(Vec3 centerXZ, double half, double ownerY, String columnKey,
                         net.minecraft.core.BlockPos ownerPos, boolean ownerIsNether,
                         boolean existing, String groupKey) {
    }

    /**
     * Column owned by a gate of the other dimension.
     *
     * <p>The centre is the gate's X/Z mapped into this dimension; the half edge is the search radius
     * vanilla applies at the gate's dimension, scaled into this one.</p>
     *
     * <p>The band split uses the gate's <b>own</b> Y. That is the quantity vanilla actually ranks
     * against: teleporting maps {@code (x, y, z)} to {@code (x*k, y, z*k)}, so Y crosses dimensions
     * unscaled and heights in this dimension are directly comparable to the gate's own height. Using
     * the height of the partner portal here would shift the divide to a meaningless place.</p>
     */
    private static Draft draftForOtherDimension(PortalEntry entry, LinkGroups groups) {
        PortalRef portal = entry.portal().ref();
        // Scale points from the gate's dimension towards this one, so the radius is multiplied.
        double scale = LinkageMath.scaleBetween(entry.dimension(), entry.targetDimension());
        Vec3 mapped = LinkageMath.mapToDestination(portal.center(), scale);
        // Search radius plus the contact margin: a player triggers a teleport by touching the portal
        // block, so the reachable entry points extend past the block by the player's half width, and
        // the portal plane itself is wider than its centre point. The margin is in this dimension's
        // blocks, so it is added after scaling. The owning gate's width stands in for the width of the
        // portal that will be built here; they are usually the same size.
        double half = LinkageMath.radiusForDestination(entry.dimension()) * Math.abs(scale)
                + LinkageMath.contactMargin(portal.width());

        int minX = (int) Math.floor(mapped.x - half);
        int maxX = (int) Math.floor(mapped.x + half);
        int minZ = (int) Math.floor(mapped.z - half);
        int maxZ = (int) Math.floor(mapped.z + half);
        String columnKey = minX + "," + minZ + "," + maxX + "," + maxZ;

        return new Draft(new Vec3(mapped.x, portal.center().y, mapped.z), half,
                portal.center().y, columnKey, portal.centerPos(), portal.isNether(), true,
                groups.groupOf(portal.key()));
    }

    /**
     * Column owned by a local portal that has no partner yet.
     *
     * <p>Nothing exists on the other side, so this column describes competition rather than a link:
     * every local portal inside it maps into the same destination square, meaning they would fight
     * over the single gate vanilla is about to create.</p>
     *
     * <p>The half edge is {@code destinationRadius / |scale|} where {@code scale} maps <b>this</b>
     * dimension into the destination. Two portals here compete when their mapped anchors fall within
     * the destination radius, and mapping divides by {@code scale}, so the equivalent reach in this
     * dimension is {@code radius / scale}. In the overworld towards the nether that is
     * {@code 16 / 0.125 = 128} blocks, matching the linked case.</p>
     *
     * <p>Note the asymmetry with {@link #draftForOtherDimension}: there the scale points the other
     * way (destination to here) and is <b>multiplied</b>. Both collapse to the same 128 blocks, but
     * mixing the two forms once produced a 5 block column, so the direction of {@code scale} matters
     * and is asserted by the game tests.</p>
     */
    private static Draft draftForUnlinkedHere(PortalEntry entry, LinkGroups groups) {
        PortalRef portal = entry.portal().ref();
        double scale = LinkageMath.scaleBetween(entry.dimension(), entry.targetDimension());
        // Same contact margin as the linked case: both portals sit in this dimension, so the margin is
        // in this dimension's blocks and is added after the radius has been converted.
        double half = LinkageMath.radiusForDestination(entry.targetDimension())
                / Math.max(Math.abs(scale), 1.0E-4D)
                + LinkageMath.contactMargin(portal.width());

        Vec3 center = portal.center();
        int minX = (int) Math.floor(center.x - half);
        int maxX = (int) Math.floor(center.x + half);
        int minZ = (int) Math.floor(center.z - half);
        int maxZ = (int) Math.floor(center.z + half);
        String columnKey = minX + "," + minZ + "," + maxX + "," + maxZ;

        return new Draft(center, half, center.y, columnKey, portal.centerPos(), portal.isNether(),
                false, groups.groupOf(portal.key()));
    }
}
