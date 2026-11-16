package com.heishan.portallink.core;

/**
 * How confident we are about where a portal sends the player.
 */
public enum LinkStatus {
    /** A portal in the other dimension was found and is the one vanilla would pick. */
    LINKED(0),
    /** The link is asymmetric: the partner portal leads back somewhere else (串门风险). */
    CONFLICT(1),
    /** No portal in range, so vanilla would create a new one at/around the mapped position. */
    WILL_CREATE(2),
    /** The destination area could not be inspected (not loaded / no server data). */
    UNKNOWN(3);

    private final int id;

    LinkStatus(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static LinkStatus byId(int id) {
        for (LinkStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }
        return UNKNOWN;
    }

    public boolean isProblem() {
        return this == CONFLICT;
    }
}
