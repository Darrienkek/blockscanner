package com.blockscanner;

import net.minecraft.util.math.ChunkPos;

/**
 * Generates a spiral search pattern over a square chunk range centered on (0, 0).
 */
public class SearchPattern {
    private final int minChunk;
    private final int maxChunk;
    private final int totalCount;
    private int producedCount = 0;
    private int x = 0;
    private int z = 0;
    private int dx = 1;
    private int dz = 0;
    private int legLength = 1;
    private int legProgress = 0;
    private int legsDone = 0;
    private boolean started = false;

    public SearchPattern(int chunkRadius) {
        int radius = Math.max(0, chunkRadius);
        this.minChunk = -radius;
        this.maxChunk = radius;
        int width = maxChunk - minChunk + 1;
        this.totalCount = width * width;
    }

    public void reset() {
        producedCount = 0;
        x = 0;
        z = 0;
        dx = 1;
        dz = 0;
        legLength = 1;
        legProgress = 0;
        legsDone = 0;
        started = false;
    }

    public ChunkPos next() {
        if (producedCount >= totalCount) {
            return null;
        }

        if (!started) {
            started = true;
            if (isWithinBounds(0, 0)) {
                producedCount++;
                return new ChunkPos(0, 0);
            }
        }

        while (producedCount < totalCount) {
            step();
            if (isWithinBounds(x, z)) {
                producedCount++;
                return new ChunkPos(x, z);
            }
        }

        return null;
    }

    private void step() {
        x += dx;
        z += dz;
        legProgress++;
        if (legProgress == legLength) {
            legProgress = 0;
            rotateRight();
            legsDone++;
            if (legsDone % 2 == 0) {
                legLength++;
            }
        }
    }

    private void rotateRight() {
        int newDx = -dz;
        int newDz = dx;
        dx = newDx;
        dz = newDz;
    }

    private boolean isWithinBounds(int x, int z) {
        return x >= minChunk && x <= maxChunk && z >= minChunk && z <= maxChunk;
    }
}
