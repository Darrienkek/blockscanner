package com.blockscanner.data;

/**
 * Persistent cursor for squared spiral traversal.
 *
 * @param waypointGridX spiral grid X coordinate (scaled to chunk space by waypoint step)
 * @param waypointGridZ spiral grid Z coordinate (scaled to chunk space by waypoint step)
 * @param directionIndex 0=+X, 1=+Z, 2=-X, 3=-Z
 * @param legLength number of waypoints to move in current leg length
 * @param stepsTakenOnLeg completed steps in current leg
 * @param legsCompletedAtLength completed legs at current leg length (0 or 1)
 * @param waypointsCompleted number of completed waypoints
 */
public record SpiralCursorState(
    int waypointGridX,
    int waypointGridZ,
    int directionIndex,
    int legLength,
    int stepsTakenOnLeg,
    int legsCompletedAtLength,
    long waypointsCompleted
) {
    public SpiralCursorState {
        if (directionIndex < 0 || directionIndex > 3) {
            throw new IllegalArgumentException("directionIndex must be between 0 and 3");
        }
        if (legLength < 1) {
            throw new IllegalArgumentException("legLength must be at least 1");
        }
        if (stepsTakenOnLeg < 0 || stepsTakenOnLeg > legLength) {
            throw new IllegalArgumentException("stepsTakenOnLeg must be between 0 and legLength");
        }
        if (legsCompletedAtLength < 0 || legsCompletedAtLength > 1) {
            throw new IllegalArgumentException("legsCompletedAtLength must be 0 or 1");
        }
        if (waypointsCompleted < 0) {
            throw new IllegalArgumentException("waypointsCompleted cannot be negative");
        }
    }

    public static SpiralCursorState initial() {
        return new SpiralCursorState(0, 0, 0, 1, 0, 0, 0);
    }
}